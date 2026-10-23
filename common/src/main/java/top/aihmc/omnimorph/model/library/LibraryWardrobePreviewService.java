package top.aihmc.omnimorph.model.library;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import top.aihmc.omnimorph.backend2.library.LibraryClient;
import top.aihmc.omnimorph.backend2.protocol.CloudHttp;
import top.aihmc.omnimorph.FiguraMod;
import top.aihmc.omnimorph.backend2.library.LibraryModels;
import top.aihmc.omnimorph.model.outfit.OutfitCodec;
import top.aihmc.omnimorph.model.outfit.OutfitConfig;
import top.aihmc.omnimorph.model.outfit.OutfitLimits;

import java.io.ByteArrayInputStream;
import java.net.URLEncoder;
import java.net.http.HttpRequest;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.UUID;
import java.util.function.Supplier;

/** Administrator previews revalidate the selected source before reusing original cached bytes. */
public final class LibraryWardrobePreviewService {
    public record Configuration(UUID resource, OutfitConfig configuration, OutfitLimits limits) {}

    @FunctionalInterface
    private interface Decoder<T> {
        T decode(byte[] bytes, OutfitLimits limits) throws Exception;
    }
    private static final String ROOT = "/api/library/v1/admin/wardrobe-preview/";
    private final LibraryClient client;
    private final Supplier<String> administrator;
    private final LibraryPreviewCache cache;

    public LibraryWardrobePreviewService(LibraryClient client, Supplier<String> administrator) {
        this(client, administrator, FiguraMod.getCacheDirectory());
    }

    public LibraryWardrobePreviewService(LibraryClient client, Supplier<String> administrator, Path cacheDirectory) {
        this.client = client;
        this.administrator = administrator;
        cache = new LibraryPreviewCache(cacheDirectory);
    }

    public LibraryClient.Request<CompoundTag> current(UUID owner, String asset, long revision, long equipmentRevision) {
        String query = "?asset_id=" + URLEncoder.encode(asset, StandardCharsets.UTF_8)
                + "&asset_revision=" + revision + "&equipment_revision=" + equipmentRevision;
        return fetch(ROOT + "CURRENT/" + owner + query, owner, revision, equipmentRevision);
    }

    public LibraryClient.Request<CompoundTag> blocked(UUID operation) {
        return fetch(ROOT + "BLOCK/" + operation, operation, 1, 1);
    }

    public LibraryClient.Request<CompoundTag> recycled(String kind, UUID owner, UUID id) {
        String path = switch (kind) {
            case "LIBRARY" -> ROOT + "LIBRARY_RECYCLE/" + id;
            case "WARDROBE" -> ROOT + "RECYCLE/" + id + "?owner=" + owner;
            default -> throw new IllegalArgumentException("INVALID_RECYCLE_SOURCE");
        };
        return fetch(path, id, 1, 1);
    }

    private LibraryClient.Request<CompoundTag> fetch(String path, UUID source, long revision, long policyRevision) {
        return fetch(path, source, revision, policyRevision, false, (bytes, limits) -> decode(bytes));
    }

    public LibraryClient.Request<Configuration> recycledConfiguration(UUID id) {
        return fetch(ROOT + "LIBRARY_RECYCLE/" + id, id, 1, 1, true, (bytes, limits) -> {
            String json = StandardCharsets.UTF_8.newDecoder().decode(java.nio.ByteBuffer.wrap(bytes)).toString();
            return new Configuration(id, OutfitCodec.decode(json, limits), limits);
        });
    }

    private <T> LibraryClient.Request<T> fetch(String path, UUID source, long revision, long policyRevision,
                                              boolean configuration, Decoder<T> decoder) {
        return client.submit(LibraryClient.Lane.TRANSFER, exchange -> {
            String token = administrator.get();
            if (token == null || token.isBlank()) {
                throw new LibraryClient.Failure(403, "FORBIDDEN");
            }
            exchange.adminToken(token);
            OutfitLimits limits = null;
            if (configuration) {
                var capabilities = exchange.json("GET", "/api/library/v1/capabilities", null, LibraryModels.Capabilities.class);
                if (!capabilities.enabled() || capabilities.outfitsV1() == null || !capabilities.outfitsV1().configuration()) {
                    throw new LibraryClient.Failure(0, "OUTFIT_UNSUPPORTED");
                }
                limits = capabilities.outfitsV1().limits();
            }
            var cacheKind = configuration ? LibraryPreviewCache.Kind.CONFIGURATION : LibraryPreviewCache.Kind.WARDROBE;
            var head = exchange.send("HEAD", path, HttpRequest.BodyPublishers.noBody(), null);
            long expectedLength;
            String expectedHash;
            try (var ignored = head.body()) {
                if (head.statusCode() != 200) {
                    throw new LibraryClient.Failure(head.statusCode(), "PREVIEW_UNAVAILABLE");
                }
                expectedLength = head.headers().firstValueAsLong("content-length").orElse(-1);
                expectedHash = head.headers().firstValue("x-omnimorph-sha256").orElse("");
                if (expectedLength < 1 || expectedLength > (configuration ? limits.maxJsonBytes() : CloudHttp.FILE_BYTES)
                        || !expectedHash.matches("[a-f0-9]{64}")
                        || configuration && !jsonMedia(head.headers())) {
                    throw new LibraryClient.Failure(0, "INVALID_DISTRIBUTION_RESPONSE");
                }
            }
            String instance = CloudHttp.sha256((client.session().instance() + "\n" + path).getBytes(StandardCharsets.UTF_8));
            var scope = new LibraryPreviewCache.Scope(instance, client.session().player(), source, revision, policyRevision);
            var key = new LibraryPreviewCache.Key(scope, expectedHash);
            var existing = cache.find(key, cacheKind,
                    Duration.ofSeconds(exchange.previewCacheSeconds()), exchange::requireCurrent);
            if (existing.isPresent()) {
                try (var lease = existing.get()) {
                    byte[] bytes = lease.read();
                    if (bytes.length == expectedLength) {
                        return decoder.decode(bytes, limits);
                    }
                }
            }
            var response = exchange.send("GET", path, HttpRequest.BodyPublishers.noBody(), null);
            try (var input = response.body()) {
                if (response.statusCode() != 200) {
                    throw new LibraryClient.Failure(response.statusCode(), "PREVIEW_UNAVAILABLE");
                }
                long length = response.headers().firstValueAsLong("content-length").orElse(-1);
                String hash = response.headers().firstValue("x-omnimorph-sha256").orElse("");
                if (length != expectedLength || !hash.equals(expectedHash)
                        || configuration && !jsonMedia(response.headers())) {
                    throw new LibraryClient.Failure(0, "INVALID_DISTRIBUTION_RESPONSE");
                }
                byte[] bytes = CloudHttp.readBounded(input, (int) length);
                exchange.requireCurrent();
                if (bytes.length != length || !CloudHttp.sha256(bytes).equals(hash)) {
                    throw new LibraryClient.Failure(0, "DISTRIBUTION_HASH_MISMATCH");
                }
                T result = decoder.decode(bytes, limits);
                cache.put(key, cacheKind, bytes,
                        Duration.ofSeconds(exchange.previewCacheSeconds()));
                return result;
            }
        });
    }

    private static boolean jsonMedia(java.net.http.HttpHeaders headers) {
        return headers.firstValue("Content-Type").orElse("").split(";", 2)[0].trim()
                .equalsIgnoreCase("application/json");
    }

    private static CompoundTag decode(byte[] bytes) throws java.io.IOException {
        return NbtIo.readCompressed(new ByteArrayInputStream(bytes), NbtAccounter.create(256L * 1024 * 1024));
    }
}
