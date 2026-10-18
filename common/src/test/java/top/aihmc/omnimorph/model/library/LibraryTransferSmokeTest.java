package top.aihmc.omnimorph.model.library;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import top.aihmc.omnimorph.avatar.ysm.YsmSourcePayload;
import top.aihmc.omnimorph.backend2.library.LibraryClient;
import top.aihmc.omnimorph.backend2.library.LibrarySession;
import top.aihmc.omnimorph.backend2.protocol.CloudEndpoint;
import top.aihmc.omnimorph.backend2.protocol.CloudHttp;
import top.aihmc.omnimorph.backend2.protocol.LibraryAccess;

import java.io.ByteArrayOutputStream;
import java.net.InetSocketAddress;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/** Real distribution streams, portable encrypted bytes and receipt calls, without model activation. */
public final class LibraryTransferSmokeTest {
    private static final UUID MODEL = new UUID(0, 11);
    private static final UUID VIEWER = new UUID(0, 1);
    private static final UUID TRANSFER = new UUID(0, 22);

    public static void main(String[] args) throws Exception {
        Path directory = Files.createTempDirectory("library-transfer-");
        try (var server = new Server()) {
            var endpoint = CloudEndpoint.parse(server.origin(), () -> true);
            var callbacks = new ConcurrentLinkedQueue<Runnable>();
            try (var access = new LibraryAccess(endpoint, HttpClient.newHttpClient(),
                    request -> request.header("Authorization", "Bearer synthetic-player"));
                 var client = new LibraryClient(access, new LibrarySession(server.origin(), VIEWER, 1, () -> true), callbacks::add)) {
                var transfers = new LibraryTransferService(client, directory);
                var first = await(transfers.preview(MODEL).request().future(), callbacks);
                require(first.nbt().contains("ysm") && first.purpose().equals("preview"), "encrypted preview uses the portable distribution");
                await(transfers.preview(MODEL).request().future(), callbacks);
                require(server.details.get() == 2 && server.files.get() == 1 && server.completed.get() == 0,
                        "a cache hit still checks backend policy and previews never complete a download receipt");
                var formal = await(transfers.download(MODEL).request().future(), callbacks);
                require(formal.purpose().equals("download") && server.files.get() == 2 && server.completed.get() == 1,
                        "formal download requests and verifies fresh bytes despite a preview cache hit");
                try (var files = Files.list(directory.resolve("model-library-downloads"))) {
                    require(files.findAny().isEmpty(), "formal temporary file is removed after verified preparation");
                }
                Path cached;
                try (var files = Files.list(directory.resolve("model-library-preview/models"))) {
                    cached = files.findFirst().orElseThrow();
                }
                require(java.util.Arrays.equals(Files.readAllBytes(cached), server.bytes), "preview disk stores the original encrypted distribution only");
                Files.setLastModifiedTime(cached, java.nio.file.attribute.FileTime.fromMillis(
                        System.currentTimeMillis() - TimeUnit.MINUTES.toMillis(90)));
                await(transfers.preview(MODEL).request().future(), callbacks);
                require(server.files.get() == 2, "the backend's two-hour lifetime permits a 90-minute-old preview");
                server.revoked.set(true);
                failure(transfers.preview(MODEL).request().future(), callbacks, "FORBIDDEN");
                require(server.files.get() == 2, "revoked access cannot use disk or request another body");
                server.revoked.set(false);
                server.corrupt.set(true);
                failure(transfers.download(MODEL).request().future(), callbacks, "DISTRIBUTION_HASH_MISMATCH");
                require(server.completed.get() == 1, "failed digest cannot complete a receipt");
                try (var files = Files.list(directory.resolve("model-library-downloads"))) {
                    require(files.findAny().isEmpty(), "failed download removes its temporary file");
                }
                server.corrupt.set(false);
                var wardrobe = new LibraryWardrobePreviewService(client, () -> "synthetic-admin", directory);
                var source = await(wardrobe.current(VIEWER, "self outfit/中文", 3, 9).future(), callbacks);
                require(source.contains("ysm"), "administrator wardrobe bytes decode as portable NBT");
                await(wardrobe.blocked(TRANSFER).future(), callbacks);
                await(new LibraryWardrobePreviewService(client, () -> "synthetic-admin", directory)
                        .current(VIEWER, "self outfit/中文", 3, 9).future(), callbacks);
                require(server.wardrobeFiles.get() == 2 && server.completed.get() == 1,
                        "reopened wardrobe preview revalidates with HEAD and reuses original cached bytes");
                require(server.wardrobeHeads.get() == 3, "every wardrobe cache hit still performs a live source check");
                server.staleWardrobe.set(true);
                failure(wardrobe.current(VIEWER, "self outfit/中文", 3, 9).future(), callbacks, "PREVIEW_UNAVAILABLE");
                server.staleWardrobe.set(false);
                server.cacheSeconds.set(0);
                server.corrupt.set(true);
                failure(wardrobe.blocked(TRANSFER).future(), callbacks, "DISTRIBUTION_HASH_MISMATCH");
                int requests = server.wardrobeHeads.get();
                failure(new LibraryWardrobePreviewService(client, () -> "", directory).blocked(TRANSFER).future(), callbacks, "FORBIDDEN");
                require(server.wardrobeHeads.get() == requests, "no administrator session means no wardrobe request");
                require(server.unexpected.get() == 0, "preview and raw download never upload or equip a model");
            }
            System.out.println("PASS library transfer: current permissions on cache hits, fresh formal stream, lengths/digests, encrypted-only disk data, receipt separation and failure cleanup");
        } finally {
            try (var files = Files.walk(directory)) {
                for (Path file : files.sorted(Comparator.reverseOrder()).toList()) {
                    Files.delete(file);
                }
            }
        }
    }

    private static <T> T await(CompletableFuture<T> future, ConcurrentLinkedQueue<Runnable> callbacks) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (!future.isDone() && System.nanoTime() < deadline) {
            Runnable callback;
            while ((callback = callbacks.poll()) != null) {
                callback.run();
            }
            Thread.sleep(1);
        }
        return future.get(1, TimeUnit.SECONDS);
    }

    private static void failure(CompletableFuture<?> future, ConcurrentLinkedQueue<Runnable> callbacks, String code) throws Exception {
        try {
            await(future, callbacks);
            throw new AssertionError("Expected transfer failure");
        } catch (ExecutionException error) {
            require(error.getCause() instanceof LibraryClient.Failure failure && failure.code.equals(code),
                    "controlled transfer failure: " + error.getCause());
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    private static final class Server implements AutoCloseable {
        private final HttpServer server;
        private final AtomicInteger details = new AtomicInteger();
        private final AtomicInteger files = new AtomicInteger();
        private final AtomicInteger completed = new AtomicInteger();
        private final AtomicInteger unexpected = new AtomicInteger();
        private final AtomicBoolean revoked = new AtomicBoolean();
        private final AtomicBoolean corrupt = new AtomicBoolean();
        private final AtomicInteger wardrobeFiles = new AtomicInteger();
        private final AtomicInteger wardrobeHeads = new AtomicInteger();
        private final AtomicInteger cacheSeconds = new AtomicInteger(7200);
        private final AtomicBoolean staleWardrobe = new AtomicBoolean();
        private final byte[] bytes;

        private Server() throws Exception {
            byte[] ciphertext;
            try (var input = LibraryTransferSmokeTest.class.getResourceAsStream("/ysm/crypto-vectors/crypto1.ysm")) {
                ciphertext = input.readAllBytes();
            }
            var nbt = new CompoundTag();
            var metadata = new CompoundTag();
            metadata.putString("format", "ysm-native");
            metadata.putString("name", "synthetic");
            var source = new CompoundTag();
            source.putByteArray("source_ciphertext", ciphertext);
            source.putString("content_sha256", CloudHttp.sha256(ciphertext));
            source.putInt("crypto_version", 1);
            source.putInt("engine_schema_version", 1);
            nbt.put("metadata", metadata);
            nbt.put("ysm", source);
            var output = new ByteArrayOutputStream();
            NbtIo.writeCompressed(YsmSourcePayload.forOutput(nbt), output);
            bytes = output.toByteArray();
            server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.createContext("/", this::handle);
            server.start();
        }

        private String origin() {
            return "http://127.0.0.1:" + server.getAddress().getPort();
        }

        private void handle(HttpExchange request) throws java.io.IOException {
            try (request) {
                request.getResponseHeaders().add("x-omnimorph-preview-cache-seconds", Integer.toString(cacheSeconds.get()));
                String path = request.getRequestURI().getPath();
                String body;
                int status = 200;
                if (path.startsWith("/api/library/v1/admin/wardrobe-preview/")) {
                    boolean head = request.getRequestMethod().equals("HEAD");
                    if (head) {
                        wardrobeHeads.incrementAndGet();
                    } else {
                        wardrobeFiles.incrementAndGet();
                    }
                    boolean current = path.equals("/api/library/v1/admin/wardrobe-preview/CURRENT/" + VIEWER);
                    String expectedQuery = "asset_id=" + java.net.URLEncoder.encode("self outfit/中文", StandardCharsets.UTF_8)
                            + "&asset_revision=3&equipment_revision=9";
                    boolean valid = (head || request.getRequestMethod().equals("GET"))
                            && "synthetic-admin".equals(request.getRequestHeaders().getFirst("x-omnimorph-admin"))
                            && (current && expectedQuery.equals(request.getRequestURI().getRawQuery())
                                || path.equals("/api/library/v1/admin/wardrobe-preview/BLOCK/" + TRANSFER));
                    if (!valid || current && staleWardrobe.get()) {
                        status = valid ? 409 : 403;
                        body = "{\"code\":\"PREVIEW_UNAVAILABLE\"}";
                    } else {
                        request.getResponseHeaders().add("x-omnimorph-sha256", CloudHttp.sha256(bytes));
                        if (head) {
                            request.getResponseHeaders().add("content-length", Integer.toString(bytes.length));
                            request.sendResponseHeaders(200, -1);
                            return;
                        }
                        request.sendResponseHeaders(200, bytes.length);
                        byte[] sent = bytes.clone();
                        if (corrupt.get()) {
                            sent[sent.length - 1] ^= 1;
                        }
                        request.getResponseBody().write(sent);
                        return;
                    }
                } else if (path.equals("/api/library/v1/models/" + MODEL)) {
                    details.incrementAndGet();
                    status = revoked.get() ? 403 : 200;
                    body = revoked.get() ? "{\"code\":\"FORBIDDEN\"}" : """
                            {"model_id":"%s","revision":1,"policy_revision":1,"display_name":"synthetic",
                             "internal_name":"synthetic","authors":[],"uploader":{"uuid":"%s","name":"Synthetic"},
                             "model_type":"YSM_ENCRYPTED","visibility":"PRIVATE","requires_authorization":true,
                             "allowed_actions":["preview","download","equip"],"byte_length":%d}
                            """.formatted(MODEL, VIEWER, bytes.length);
                } else if (path.equals("/api/library/v1/models/" + MODEL + "/transfers")) {
                    String input = new String(request.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
                    String purpose = input.contains("download") ? "download" : "preview";
                    body = """
                            {"transfer_id":"%s","purpose":"%s","model_revision":1,"policy_revision":1,
                             "relative_path":"/api/library/v1/transfers/%s","expires_at":2000000000}
                            """.formatted(TRANSFER, purpose, TRANSFER);
                } else if (path.equals("/api/library/v1/transfers/" + TRANSFER)) {
                    files.incrementAndGet();
                    request.getResponseHeaders().add("x-omnimorph-sha256", CloudHttp.sha256(bytes));
                    request.sendResponseHeaders(200, bytes.length);
                    byte[] sent = bytes.clone();
                    if (corrupt.get()) {
                        sent[sent.length - 1] ^= 1;
                    }
                    request.getResponseBody().write(sent);
                    return;
                } else if (path.equals("/api/library/v1/transfers/" + TRANSFER + "/complete")) {
                    completed.incrementAndGet();
                    body = "{}";
                } else {
                    unexpected.incrementAndGet();
                    status = 404;
                    body = "{\"code\":\"NOT_FOUND\"}";
                }
                byte[] response = body.getBytes(StandardCharsets.UTF_8);
                request.sendResponseHeaders(status, response.length);
                request.getResponseBody().write(response);
            }
        }

        @Override
        public void close() {
            server.stop(0);
        }
    }
}
