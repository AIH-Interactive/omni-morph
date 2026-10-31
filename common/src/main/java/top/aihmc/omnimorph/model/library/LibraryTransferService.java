package top.aihmc.omnimorph.model.library;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import top.aihmc.omnimorph.avatar.AvatarLoadProgress;
import top.aihmc.omnimorph.avatar.ysm.YsmSourcePayload;
import top.aihmc.omnimorph.backend2.library.LibraryClient;
import top.aihmc.omnimorph.backend2.library.LibraryModels;
import top.aihmc.omnimorph.backend2.protocol.CloudHttp;
import top.aihmc.omnimorph.model.cache.AvatarCachePaths;
import top.aihmc.omnimorph.model.cache.ModelCacheCodec;
import top.aihmc.omnimorph.model.cache.ModelCacheLayout;
import top.aihmc.omnimorph.model.outfit.OutfitCodec;
import top.aihmc.omnimorph.model.outfit.OutfitConfig;
import top.aihmc.omnimorph.model.outfit.OutfitLimits;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.http.HttpRequest;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Map;
import java.util.UUID;

/** Fresh authorization for every operation; formal downloads never read or promote preview files. */
public final class LibraryTransferService {
    private static final String ROOT = "/api/library/v1/";
    private final LibraryClient client;
    private final LibraryPreviewCache preview;
    private final Path formalDirectory;
    private final Path cacheRoot;
    private final ModelCacheCodec codec;
    private final LibraryResourceCache resources;
    private final java.util.function.Supplier<String> administrator;

    public record Package(LibraryModels.Model model, CompoundTag nbt, String rawHash, String purpose,
                          OutfitConfig configuration, OutfitLimits outfitLimits) {
        public Package(LibraryModels.Model model, CompoundTag nbt, String rawHash, String purpose) {
            this(model, nbt, rawHash, purpose, null, null);
        }
    }

    public record Operation(LibraryClient.Request<Package> request, AvatarLoadProgress progress) implements AutoCloseable {
        @Override
        public void close() {
            request.cancel();
        }
    }

    public LibraryTransferService(LibraryClient client, Path figuraCacheDirectory) {
        this(client, figuraCacheDirectory, () -> "");
    }

    public LibraryTransferService(LibraryClient client, Path figuraCacheDirectory,
                                  java.util.function.Supplier<String> administrator) {
        this.client = client;
        this.administrator = administrator;
        preview = new LibraryPreviewCache(figuraCacheDirectory);
        codec = new ModelCacheCodec(figuraCacheDirectory);
        resources = new LibraryResourceCache(figuraCacheDirectory, client.session());
        cacheRoot = figuraCacheDirectory.toAbsolutePath().normalize();
        formalDirectory = cacheRoot.resolve("avatars/model-library-downloads");
    }

    public Operation preview(UUID model) {
        return fetch(model, false);
    }

    /** Device-only wardrobe editing. A miss is explicit; this never downloads or admits an Apply. */
    public Package cachedPreview(LibraryModels.Model model) throws Exception {
        client.session().requireCurrent();
        if (!model.allows("preview") || model.resourceKind().equals("OUTFIT")) {
            throw new IOException("LOCAL_PREVIEW_RESOURCE_UNAVAILABLE");
        }
        byte[] bytes = resources.read(model);
        if (bytes == null) {
            var scope = new LibraryPreviewCache.Scope(client.session().instance(), client.session().player(),
                    model.modelId(), model.revision(), model.policyRevision());
            var cached = preview.forResourceKind(model.resourceKind()).find(scope,
                    LibraryPreviewCache.Kind.MODEL, client.session()::requireCurrent);
            if (cached.isPresent()) {
                try (var lease = cached.get()) {
                    bytes = lease.read();
                }
            }
        }
        // Collection summaries carry no byte length; cached packages were already hash-verified on read.
        if (bytes == null || model.byteLength() > 0 && bytes.length != model.byteLength()) {
            throw new IOException("LOCAL_PREVIEW_CACHE_MISS");
        }
        client.session().requireCurrent();
        return parse(model, bytes, CloudHttp.sha256(bytes), "preview", null);
    }

    /** A draft's fixed revision must not silently follow an updated catalog card. */
    public Operation preview(UUID model, long revision) {
        if (revision < 1) {
            throw new IllegalArgumentException("INVALID_RESOURCE_REVISION");
        }
        return fetch(model, false, false, revision);
    }

    /** Discard old plaintext download leftovers only within the application's dedicated cache. */
    public static void cleanupUnprotectedDownloads(Path cacheRoot) throws IOException {
        Path directory = AvatarCachePaths.prepare(cacheRoot, "model-library-downloads");
        if (Files.isSymbolicLink(directory) || !Files.isDirectory(directory)) {
            return;
        }
        try (var files = Files.list(directory)) {
            for (Path path : files.toList()) {
                if (Files.isRegularFile(path, java.nio.file.LinkOption.NOFOLLOW_LINKS)
                        && path.getFileName().toString().matches("download-.*\\.part")
                        && Files.size(path) > 32 && !ModelCacheCodec.isWrapped(path)) {
                    Files.deleteIfExists(path);
                }
            }
        }
    }

    public Operation download(UUID model) {
        return fetch(model, true);
    }

    public Operation add(UUID model) {
        return fetch(model, true, true);
    }

    private Operation fetch(UUID id, boolean formal) {
        return fetch(id, formal, false);
    }

    private Operation fetch(UUID id, boolean formal, boolean join) {
        return fetch(id, formal, join, null);
    }

    private Operation fetch(UUID id, boolean formal, boolean join, Long expectedRevision) {
        var progress = new AvatarLoadProgress();
        long generation = progress.begin(AvatarLoadProgress.Stage.PROFILE);
        var request = client.submit(LibraryClient.Lane.TRANSFER, exchange -> {
            try {
                String token = administrator.get();
                if (token != null && !token.isBlank()) {
                    exchange.adminToken(token);
                }
                LibraryModels.Model model = exchange.json("GET", ROOT + "models/" + id, null, LibraryModels.Model.class);
                if (expectedRevision != null && model.revision() != expectedRevision) {
                    throw new LibraryClient.Failure(0, "RESOURCE_REVISION_UNAVAILABLE");
                }
                LibraryPreviewCache modelPreview = preview.forResourceKind(model.resourceKind());
                boolean outfit = "OUTFIT".equals(model.resourceKind());
                OutfitLimits limits = null;
                if (outfit) {
                    var capabilities = exchange.json("GET", ROOT + "capabilities", null, LibraryModels.Capabilities.class);
                    var supported = capabilities.outfitsV1();
                    if (!capabilities.enabled() || supported == null || !supported.configuration()) {
                        throw new LibraryClient.Failure(0, "OUTFIT_UNSUPPORTED");
                    }
                    limits = supported.limits();
                }
                LibraryPreviewCache.Kind cacheKind = outfit ? LibraryPreviewCache.Kind.CONFIGURATION
                        : LibraryPreviewCache.Kind.MODEL;
                String purpose = formal ? "download" : "preview";
                if (!id.equals(model.modelId()) || !model.allows(purpose) || model.byteLength() < 1
                        || model.byteLength() > CloudHttp.FILE_BYTES) {
                    throw new LibraryClient.Failure(0, "MODEL_UNAVAILABLE");
                }
                if (outfit && model.byteLength() > limits.maxJsonBytes()) {
                    throw new LibraryClient.Failure(0, "OUTFIT_LIMIT_EXCEEDED");
                }
                var scope = new LibraryPreviewCache.Scope(client.session().instance(), client.session().player(),
                        id, model.revision(), model.policyRevision());
                if (formal) {
                    byte[] cached = resources.read(model);
                    if (cached != null && (!join || isMember(exchange, id))) {
                        return parse(model, cached, CloudHttp.sha256(cached), purpose, limits);
                    }
                    var pending = resources.pending(model);
                    if (pending != null) {
                        var result = parse(model, pending.bytes(), pending.hash(), purpose, limits);
                        finishPending(exchange, model, pending, join);
                        return result;
                    }
                }
                if (!formal) {
                    // A formally downloaded source may serve a freshly authorized preview, never the reverse.
                    byte[] formalBytes = resources.read(model);
                    if (formalBytes != null) {
                        return parse(model, formalBytes, CloudHttp.sha256(formalBytes), purpose, limits);
                    }
                    // The detail immediately above is the fresh backend permission decision.
                    var cached = modelPreview.find(scope, cacheKind,
                            java.time.Duration.ofSeconds(exchange.previewCacheSeconds()), exchange::requireCurrent);
                    if (cached.isPresent()) {
                        try (var lease = cached.get()) {
                            byte[] bytes = lease.read();
                            if (bytes.length == model.byteLength()) {
                                progress.stage(generation, AvatarLoadProgress.Stage.PREPARING);
                                return parse(model, bytes, CloudHttp.sha256(bytes), purpose, limits);
                            }
                        }
                    }
                }
                LibraryModels.Transfer transfer = exchange.json("POST", ROOT + "models/" + id + "/transfers",
                        Map.of("operation_id", UUID.randomUUID().toString(), "purpose", purpose,
                                "model_revision", model.revision()), LibraryModels.Transfer.class);
                if (!purpose.equals(transfer.purpose()) || transfer.modelRevision() != model.revision()
                        || transfer.policyRevision() != model.policyRevision()) {
                    throw new LibraryClient.Failure(0, "STALE_TRANSFER");
                }
                var response = exchange.send("GET", transfer.relativePath(), HttpRequest.BodyPublishers.noBody(), null);
                String hash = response.headers().firstValue("x-omnimorph-sha256").orElse("");
                if (response.statusCode() != 200 || !hash.matches("[a-f0-9]{64}")
                        || response.headers().firstValueAsLong("Content-Length").orElse(-1) != model.byteLength()
                        || outfit && !response.headers().firstValue("Content-Type").orElse("")
                                .split(";", 2)[0].trim().equalsIgnoreCase("application/json")) {
                    response.body().close();
                    throw new LibraryClient.Failure(response.statusCode(), "INVALID_DISTRIBUTION_RESPONSE");
                }
                progress.stage(generation, AvatarLoadProgress.Stage.DOWNLOADING);
                Package result;
                if (formal) {
                    Path stagingDirectory = outfit ? ModelCacheLayout.configurationDirectory(cacheRoot)
                            : "ACCESSORY".equals(model.resourceKind())
                            ? ModelCacheLayout.cosmeticDirectory(cacheRoot) : formalDirectory;
                    if (Files.isSymbolicLink(stagingDirectory)) {
                        response.body().close();
                        throw new IOException("UNSAFE_FORMAL_DIRECTORY");
                    }
                    Path partial = null;
                    try (InputStream input = response.body()) {
                        if (!outfit && !"ACCESSORY".equals(model.resourceKind())) {
                            AvatarCachePaths.prepare(cacheRoot, "model-library-downloads");
                        }
                        partial = Files.createTempFile(stagingDirectory, "download-", ".part");
                        try (OutputStream output = codec.output(partial)) {
                            receive(exchange, input, output, model.byteLength(), hash, progress, generation);
                        }
                        progress.stage(generation, AvatarLoadProgress.Stage.PREPARING);
                        byte[] bytes = codec.read(partial, CloudHttp.FILE_BYTES);
                        result = parse(model, bytes, hash, purpose, limits);
                        var pending = resources.stage(model, partial, bytes, hash, transfer);
                        finishPending(exchange, model, pending, join);
                    } finally {
                        if (partial != null) {
                            Files.deleteIfExists(partial);
                        }
                    }
                } else {
                    var output = new ByteArrayOutputStream();
                    try (InputStream input = response.body()) {
                        receive(exchange, input, output, model.byteLength(), hash, progress, generation);
                    }
                    byte[] bytes = output.toByteArray();
                    progress.stage(generation, AvatarLoadProgress.Stage.PREPARING);
                    result = parse(model, bytes, hash, purpose, limits);
                    exchange.requireCurrent();
                    modelPreview.put(new LibraryPreviewCache.Key(scope, hash), cacheKind, bytes,
                            java.time.Duration.ofSeconds(exchange.previewCacheSeconds()));
                }
                return result;
            } finally {
                progress.finish(generation);
            }
        });
        request.future().whenComplete((value, failure) -> progress.finish(generation));
        return new Operation(request, progress);
    }

    private void finishPending(LibraryClient.Exchange exchange, LibraryModels.Model model,
                               LibraryResourceCache.Pending pending, boolean join) throws Exception {
        exchange.json("POST", ROOT + "transfers/" + pending.transfer() + "/complete",
                Map.of("operation_id", pending.completion().toString(), "raw_sha256", pending.hash()),
                com.google.gson.JsonObject.class);
        if (join) {
            exchange.json("PUT", ROOT + "collection/" + model.modelId(),
                    Map.of("transfer_id", pending.transfer().toString()), com.google.gson.JsonObject.class);
        }
        exchange.requireCurrent();
        resources.publish(model, pending);
    }

    private boolean isMember(LibraryClient.Exchange exchange, UUID id) throws Exception {
        int page = 1;
        while (true) {
            var result = exchange.json("GET", ROOT + "collection?page_size=64&page=" + page,
                    null, LibraryModels.CollectionPage.class);
            if (result.items().stream().anyMatch(entry -> id.equals(entry.modelId()))) {
                return true;
            }
            if (!result.hasNext()) {
                return false;
            }
            page++;
        }
    }

    private static void receive(LibraryClient.Exchange exchange, InputStream input, OutputStream output,
                                long length, String hash, AvatarLoadProgress progress, long generation) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] buffer = new byte[32 * 1024];
        long total = 0;
        int count;
        while ((count = input.read(buffer)) != -1) {
            exchange.requireCurrent();
            total += count;
            if (total > length || total > CloudHttp.FILE_BYTES) {
                throw new LibraryClient.Failure(0, "DISTRIBUTION_LENGTH_MISMATCH");
            }
            digest.update(buffer, 0, count);
            output.write(buffer, 0, count);
            progress.downloaded(generation, total, length);
        }
        exchange.requireCurrent();
        if (total != length || !HexFormat.of().formatHex(digest.digest()).equals(hash)) {
            throw new LibraryClient.Failure(0, "DISTRIBUTION_HASH_MISMATCH");
        }
    }

    private static Package parse(LibraryModels.Model model, byte[] bytes, String hash, String purpose,
                                 OutfitLimits limits) throws IOException {
        if ("OUTFIT".equals(model.resourceKind())) {
            try {
                if (model.modelType() != null || limits == null) {
                    throw new IllegalArgumentException("DISTRIBUTION_TYPE_MISMATCH");
                }
                String json = java.nio.charset.StandardCharsets.UTF_8.newDecoder()
                        .decode(java.nio.ByteBuffer.wrap(bytes)).toString();
                return new Package(model, null, hash, purpose, OutfitCodec.decode(json, limits), limits);
            } catch (IllegalArgumentException | com.google.gson.JsonParseException malformed) {
                throw new IOException("INVALID_OUTFIT_DISTRIBUTION", malformed);
            }
        }
        return parse(model, new ByteArrayInputStream(bytes), hash, purpose);
    }

    private static Package parse(LibraryModels.Model model, InputStream input, String hash, String purpose) throws IOException {
        CompoundTag nbt = NbtIo.readCompressed(input, NbtAccounter.create(256L * 1024 * 1024));
        boolean ysm = nbt.getCompound("metadata").getString("format").equals("ysm-native");
        boolean encrypted = YsmSourcePayload.isEncrypted(nbt.getCompound("ysm"));
        if (ysm != !model.modelType().equals("FIGURA") || encrypted != model.modelType().equals("YSM_ENCRYPTED")) {
            throw new IOException("DISTRIBUTION_TYPE_MISMATCH");
        }
        if (encrypted && !YsmSourcePayload.forOutput(nbt).equals(nbt)) {
            throw new IOException("NONPORTABLE_ENCRYPTED_DISTRIBUTION");
        }
        return new Package(model, nbt, hash, purpose);
    }
}
