package top.aihmc.omnimorph.model.library;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import top.aihmc.omnimorph.backend2.library.LibraryClient;
import top.aihmc.omnimorph.backend2.protocol.CloudHttp;

import java.io.ByteArrayInputStream;
import java.net.URLEncoder;
import java.net.http.HttpRequest;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.function.Supplier;

/** Administrator-only, uncached reads that never activate or publish wardrobe resources. */
public final class LibraryWardrobePreviewService {
    private static final String ROOT = "/api/library/v1/admin/wardrobe-preview/";
    private final LibraryClient client;
    private final Supplier<String> administrator;

    public LibraryWardrobePreviewService(LibraryClient client, Supplier<String> administrator) {
        this.client = client;
        this.administrator = administrator;
    }

    public LibraryClient.Request<CompoundTag> current(UUID owner, String asset, long revision, long equipmentRevision) {
        String query = "?asset_id=" + URLEncoder.encode(asset, StandardCharsets.UTF_8)
                + "&asset_revision=" + revision + "&equipment_revision=" + equipmentRevision;
        return fetch(ROOT + "CURRENT/" + owner + query);
    }

    public LibraryClient.Request<CompoundTag> blocked(UUID operation) {
        return fetch(ROOT + "BLOCK/" + operation);
    }

    private LibraryClient.Request<CompoundTag> fetch(String path) {
        return client.submit(LibraryClient.Lane.TRANSFER, exchange -> {
            String token = administrator.get();
            if (token == null || token.isBlank()) {
                throw new LibraryClient.Failure(403, "FORBIDDEN");
            }
            exchange.adminToken(token);
            var response = exchange.send("GET", path, HttpRequest.BodyPublishers.noBody(), null);
            try (var input = response.body()) {
                if (response.statusCode() != 200) {
                    throw new LibraryClient.Failure(response.statusCode(), "PREVIEW_UNAVAILABLE");
                }
                long length = response.headers().firstValueAsLong("content-length").orElse(-1);
                String hash = response.headers().firstValue("x-omnimorph-sha256").orElse("");
                if (length < 1 || length > CloudHttp.FILE_BYTES || !hash.matches("[a-f0-9]{64}")) {
                    throw new LibraryClient.Failure(0, "INVALID_DISTRIBUTION_RESPONSE");
                }
                byte[] bytes = CloudHttp.readBounded(input, (int) length);
                exchange.requireCurrent();
                if (bytes.length != length || !CloudHttp.sha256(bytes).equals(hash)) {
                    throw new LibraryClient.Failure(0, "DISTRIBUTION_HASH_MISMATCH");
                }
                return NbtIo.readCompressed(new ByteArrayInputStream(bytes), NbtAccounter.create(256L * 1024 * 1024));
            }
        });
    }
}
