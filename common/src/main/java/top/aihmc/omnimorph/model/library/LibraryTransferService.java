package top.aihmc.omnimorph.model.library;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import top.aihmc.omnimorph.avatar.AvatarLoadProgress;
import top.aihmc.omnimorph.avatar.ysm.YsmSourcePayload;
import top.aihmc.omnimorph.backend2.library.LibraryClient;
import top.aihmc.omnimorph.backend2.library.LibraryModels;
import top.aihmc.omnimorph.backend2.protocol.CloudHttp;

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
    private final java.util.function.Supplier<String> administrator;

    public record Package(LibraryModels.Model model, CompoundTag nbt, String rawHash, String purpose) {}

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
        formalDirectory = figuraCacheDirectory.toAbsolutePath().normalize().resolve("model-library-downloads");
    }

    public Operation preview(UUID model) {
        return fetch(model, false);
    }

    public Operation download(UUID model) {
        return fetch(model, true);
    }

    private Operation fetch(UUID id, boolean formal) {
        var progress = new AvatarLoadProgress();
        long generation = progress.begin(AvatarLoadProgress.Stage.PROFILE);
        var request = client.submit(LibraryClient.Lane.TRANSFER, exchange -> {
            try {
                String token = administrator.get();
                if (token != null && !token.isBlank()) {
                    exchange.adminToken(token);
                }
                LibraryModels.Model model = exchange.json("GET", ROOT + "models/" + id, null, LibraryModels.Model.class);
                String purpose = formal ? "download" : "preview";
                if (!id.equals(model.modelId()) || !model.allows(purpose) || model.byteLength() < 1
                        || model.byteLength() > CloudHttp.FILE_BYTES) {
                    throw new LibraryClient.Failure(0, "MODEL_UNAVAILABLE");
                }
                var scope = new LibraryPreviewCache.Scope(client.session().instance(), client.session().player(),
                        id, model.revision(), model.policyRevision());
                if (!formal) {
                    // The detail immediately above is the fresh backend permission decision.
                    var cached = preview.find(scope, LibraryPreviewCache.Kind.MODEL,
                            java.time.Duration.ofSeconds(exchange.previewCacheSeconds()), exchange::requireCurrent);
                    if (cached.isPresent()) {
                        try (var lease = cached.get()) {
                            byte[] bytes = lease.read();
                            if (bytes.length == model.byteLength()) {
                                progress.stage(generation, AvatarLoadProgress.Stage.PREPARING);
                                return parse(model, bytes, CloudHttp.sha256(bytes), purpose);
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
                        || response.headers().firstValueAsLong("Content-Length").orElse(-1) != model.byteLength()) {
                    response.body().close();
                    throw new LibraryClient.Failure(response.statusCode(), "INVALID_DISTRIBUTION_RESPONSE");
                }
                progress.stage(generation, AvatarLoadProgress.Stage.DOWNLOADING);
                Package result;
                if (formal) {
                    if (Files.isSymbolicLink(formalDirectory)) {
                        response.body().close();
                        throw new IOException("UNSAFE_FORMAL_DIRECTORY");
                    }
                    Path partial = null;
                    try (InputStream input = response.body()) {
                        Files.createDirectories(formalDirectory);
                        partial = Files.createTempFile(formalDirectory, "download-", ".part");
                        try (OutputStream output = Files.newOutputStream(partial)) {
                            receive(exchange, input, output, model.byteLength(), hash, progress, generation);
                        }
                        progress.stage(generation, AvatarLoadProgress.Stage.PREPARING);
                        try (InputStream file = Files.newInputStream(partial)) {
                            result = parse(model, file, hash, purpose);
                        }
                    } finally {
                        if (partial != null) {
                            Files.deleteIfExists(partial);
                        }
                    }
                    exchange.json("POST", ROOT + "transfers/" + transfer.transferId() + "/complete",
                            Map.of("operation_id", UUID.randomUUID().toString(), "raw_sha256", hash), com.google.gson.JsonObject.class);
                } else {
                    var output = new ByteArrayOutputStream();
                    try (InputStream input = response.body()) {
                        receive(exchange, input, output, model.byteLength(), hash, progress, generation);
                    }
                    byte[] bytes = output.toByteArray();
                    progress.stage(generation, AvatarLoadProgress.Stage.PREPARING);
                    result = parse(model, bytes, hash, purpose);
                    exchange.requireCurrent();
                    preview.put(new LibraryPreviewCache.Key(scope, hash), LibraryPreviewCache.Kind.MODEL, bytes,
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

    private static Package parse(LibraryModels.Model model, byte[] bytes, String hash, String purpose) throws IOException {
        return parse(model, new ByteArrayInputStream(bytes), hash, purpose);
    }

    private static Package parse(LibraryModels.Model model, InputStream input, String hash, String purpose) throws IOException {
        CompoundTag nbt = NbtIo.readCompressed(input, NbtAccounter.create(256L * 1024 * 1024));
        boolean ysm = nbt.getCompoundOrEmpty("metadata").getStringOr("format", "").equals("ysm-native");
        boolean encrypted = YsmSourcePayload.isEncrypted(nbt.getCompoundOrEmpty("ysm"));
        if (ysm != !model.modelType().equals("FIGURA") || encrypted != model.modelType().equals("YSM_ENCRYPTED")) {
            throw new IOException("DISTRIBUTION_TYPE_MISMATCH");
        }
        if (encrypted && !YsmSourcePayload.forOutput(nbt).equals(nbt)) {
            throw new IOException("NONPORTABLE_ENCRYPTED_DISTRIBUTION");
        }
        return new Package(model, nbt, hash, purpose);
    }
}
