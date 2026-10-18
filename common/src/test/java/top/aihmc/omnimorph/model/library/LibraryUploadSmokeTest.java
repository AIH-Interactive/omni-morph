package top.aihmc.omnimorph.model.library;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import top.aihmc.omnimorph.avatar.Avatar;
import top.aihmc.omnimorph.avatar.AvatarLoadExecutor;
import top.aihmc.omnimorph.backend2.library.LibraryClient;
import top.aihmc.omnimorph.backend2.library.LibraryModels;
import top.aihmc.omnimorph.backend2.library.LibrarySession;
import top.aihmc.omnimorph.backend2.protocol.CloudEndpoint;
import top.aihmc.omnimorph.backend2.protocol.CloudHttp;
import top.aihmc.omnimorph.backend2.protocol.LibraryAccess;
import top.aihmc.omnimorph.model.ysm.sync.YsmSyncModel;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/** Production upload pipeline and real HTTP; no GUI widgets, rendering, or wardrobe publication. */
public final class LibraryUploadSmokeTest {
    private static final UUID VIEWER = new UUID(0, 1);

    public static void run(Runnable drain) throws Exception {
        Path directory = Files.createTempDirectory("library-upload-test-");
        try (var server = new Server()) {
            Path source = directory.resolve("source.zip");
            try (var zip = new ZipOutputStream(Files.newOutputStream(source))) {
                zip.putNextEntry(new ZipEntry("avatar.json"));
                zip.write("{\"name\":\"原始模型\",\"authors\":[\"作者\"],\"free\":true}"
                        .getBytes(StandardCharsets.UTF_8));
                zip.closeEntry();
            }
            var prepared = LibraryPackagePreparation.prepare(source, VIEWER, CloudHttp.FILE_BYTES, () -> false);
            var draft = new LibraryUploadDraft(VIEWER);
            draft.prepared(draft.beginSource(source), prepared);
            draft.category("general");
            var snapshot = draft.snapshot(server.limits());
            try (var access = new LibraryAccess(CloudEndpoint.parse(server.origin(), () -> true), HttpClient.newHttpClient(),
                    builder -> builder.header("Authorization", "Bearer synthetic"));
                 var client = new LibraryClient(access, new LibrarySession(server.origin(), VIEWER, 1, () -> true),
                         net.minecraft.client.Minecraft.getInstance()::execute);
                 var uploads = new LibraryUploadService(client)) {
                server.dropBody.set(true);
                var bodyLost = uploads.upload(snapshot, null);
                reject(bodyLost.future(), drain);
                require(await(bodyLost.retry(), drain).published() && server.published.get() == 1,
                        "lost body acknowledgement retries same draft and commits verified bytes");

                server.dropImage.set(true);
                var image = new BufferedImage(16, 8, BufferedImage.TYPE_INT_RGB);
                var imageBytes = new ByteArrayOutputStream();
                ImageIO.write(image, "png", imageBytes);
                image.flush();
                var photo = new LibraryUploadService.Image(imageBytes.toByteArray(), "image/png");
                var imageLost = uploads.upload(snapshot, photo);
                reject(imageLost.future(), drain);
                require(await(imageLost.retry(), drain).published() && server.published.get() == 2
                        && server.images.get() == 1, "image retry reuses operation and original policy CAS");

                server.dropCommit.set(true);
                var commitLost = uploads.upload(snapshot, null);
                reject(commitLost.future(), drain);
                int reservations = server.reserves.get();
                int bodies = server.bodies.get();
                require(await(commitLost.retry(), drain).published() && server.published.get() == 3
                        && server.reserves.get() == reservations && server.bodies.get() == bodies,
                        "lost publication response replays only original commit without another draft/body");

                server.uploadLimit = 1;
                reject(uploads.upload(snapshot, null).future(), drain);
                require(server.reserves.get() == reservations, "current upload limit checked before reserve");
                server.uploadLimit = CloudHttp.FILE_BYTES;
                server.imageWidth = 8;
                reject(uploads.upload(snapshot, photo).future(), drain);
                require(server.reserves.get() == reservations, "original image dimensions obey fresh backend limits");
                server.imageWidth = 2048;

                server.blockBody.set(true);
                var cancelled = uploads.upload(snapshot, null);
                long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
                while (server.bodyEntered.getCount() != 0 && System.nanoTime() < deadline) {
                    drain.run();
                    Thread.sleep(5);
                }
                require(server.bodyEntered.getCount() == 0, "real body reached blocking handler");
                await(cancelled.cancel(), drain);
                server.bodyRelease.countDown();
                require(cancelled.future().isCancelled() && server.published.get() == 3,
                        "cancel retires upload and removes pending draft without publication");

                var invalidHeaders = client.submit(LibraryClient.Lane.CONTROL, exchange -> exchange.binaryJson("PUT",
                        "/api/library/v1/models/" + new UUID(0, 1) + "/image", photo.bytes(), photo.contentType(),
                        Map.of("Authorization", "must-not-override-player"), Map.class));
                reject(invalidHeaders.future(), drain);
                require(server.badReplay.get() == 0 && server.unexpected.get() == 0,
                        "stable mutation payloads and no unrelated upload/equip/header override path");
            }

            Path encrypted = Path.of("src/test/resources/ysm/crypto-vectors/crypto1.ysm");
            var cipher = LibraryPackagePreparation.prepare(encrypted, VIEWER, CloudHttp.FILE_BYTES, () -> false);
            var identity = await(AvatarLoadExecutor.submit(() -> {
                try {
                    return LibraryUploadIdentity.prepare(cipher, () -> false);
                } catch (Exception failure) {
                    throw new java.util.concurrent.CompletionException(failure);
                }
            }), drain);
            Avatar reference = Avatar.createLibraryPreview(UUID.randomUUID(), "InventoryReference");
            try {
                reference.load(cipher.nbt());
                var actual = new YsmSyncModel(reference.getYsmRuntime());
                require(identity.contentHash().equals(actual.contentHash())
                        && identity.schemaHash().equals(actual.schemaHash()),
                        "upload inventory matches actual Avatar YSM synchronization identity");
                require(java.util.Arrays.equals(Files.readAllBytes(encrypted), cipher.nbt()
                        .getCompoundOrEmpty("ysm").getByteArray("source_ciphertext").orElseThrow()),
                        "inventory preparation preserves original ciphertext");
            } finally {
                reference.clean();
                drain.run();
            }
            System.out.println("PASS library upload: lost body/image/commit responses, stable CAS, fresh limits, cancel and actual encrypted inventory");
        } finally {
            try (var paths = Files.walk(directory)) {
                for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
                    Files.deleteIfExists(path);
                }
            }
        }
    }

    private static <T> T await(CompletableFuture<T> future, Runnable drain) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(15);
        while (!future.isDone() && System.nanoTime() < deadline) {
            drain.run();
            Thread.sleep(5);
        }
        return future.get(1, TimeUnit.SECONDS);
    }

    private static void reject(CompletableFuture<?> future, Runnable drain) throws Exception {
        try {
            await(future, drain);
            throw new AssertionError("upload unexpectedly accepted");
        } catch (java.util.concurrent.ExecutionException expected) {
            require(expected.getCause() instanceof LibraryClient.Failure, "typed upload error");
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    private static final class Transaction {
        final UUID model = UUID.randomUUID();
        final JsonObject input;
        volatile boolean uploaded;
        volatile boolean committed;
        volatile JsonObject commit;
        volatile String imageIdentity;

        Transaction(JsonObject input) {
            this.input = input;
        }
    }

    private static final class Server implements AutoCloseable {
        final HttpServer server;
        final java.util.concurrent.ExecutorService threads = Executors.newFixedThreadPool(4);
        final Map<String, Transaction> transactions = new ConcurrentHashMap<>();
        final AtomicBoolean dropBody = new AtomicBoolean();
        final AtomicBoolean dropImage = new AtomicBoolean();
        final AtomicBoolean dropCommit = new AtomicBoolean();
        final AtomicBoolean blockBody = new AtomicBoolean();
        final CountDownLatch bodyEntered = new CountDownLatch(1);
        final CountDownLatch bodyRelease = new CountDownLatch(1);
        final AtomicInteger reserves = new AtomicInteger();
        final AtomicInteger bodies = new AtomicInteger();
        final AtomicInteger images = new AtomicInteger();
        final AtomicInteger published = new AtomicInteger();
        final AtomicInteger badReplay = new AtomicInteger();
        final AtomicInteger unexpected = new AtomicInteger();
        volatile long uploadLimit = CloudHttp.FILE_BYTES;
        volatile int imageWidth = 2048;

        Server() throws IOException {
            server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.setExecutor(threads);
            server.createContext("/api/library/v1/", this::handle);
            server.start();
        }

        String origin() {
            return "http://127.0.0.1:" + server.getAddress().getPort();
        }

        LibraryModels.Capabilities limits() {
            return new LibraryModels.Capabilities(true, 1, uploadLimit, 8, 2 * 1024 * 1024, imageWidth, 2048,
                    java.util.List.of("zip", "ysm"), java.util.List.of("FIGURA", "YSM_PLAIN", "YSM_ENCRYPTED"),
                    java.util.List.of("general"), false);
        }

        void handle(HttpExchange exchange) throws IOException {
            int status = 200;
            Object result = Map.of();
            try {
                String path = exchange.getRequestURI().getPath().substring("/api/library/v1/".length());
                String method = exchange.getRequestMethod();
                if (path.equals("capabilities")) {
                    result = new JsonObject();
                    var value = (JsonObject) result;
                    value.addProperty("enabled", true);
                    value.addProperty("version", 1);
                    value.addProperty("max_upload_bytes", uploadLimit);
                    value.addProperty("max_page_size", 8);
                    value.addProperty("max_image_bytes", 2 * 1024 * 1024);
                    value.addProperty("max_image_width", imageWidth);
                    value.addProperty("max_image_height", 2048);
                    value.add("categories", JsonParser.parseString("[\"general\"]"));
                    value.add("model_types", JsonParser.parseString("[\"FIGURA\",\"YSM_PLAIN\",\"YSM_ENCRYPTED\"]"));
                } else if (path.equals("uploads") && method.equals("POST")) {
                    reserves.incrementAndGet();
                    JsonObject input = json(exchange);
                    String operation = input.get("operation_id").getAsString();
                    Transaction transaction = transactions.computeIfAbsent(operation, ignored -> new Transaction(input));
                    if (!transaction.input.equals(input)) {
                        badReplay.incrementAndGet();
                    }
                    result = Map.of("model_id", transaction.model, "operation_id", operation,
                            "relative_path", "/api/library/v1/uploads/" + operation + "/body",
                            "expires_at", System.currentTimeMillis() / 1000 + 300, "max_bytes", uploadLimit,
                            "policy_revision", transaction.imageIdentity == null ? 1 : 2);
                } else if (path.startsWith("uploads/")) {
                    String[] segments = path.split("/");
                    Transaction transaction = transactions.get(segments[1]);
                    if (method.equals("DELETE")) {
                        if (transaction != null && !transaction.committed) {
                            transactions.remove(segments[1]);
                        }
                    } else if (transaction == null) {
                        status = 404;
                    } else if (segments[2].equals("body")) {
                        bodies.incrementAndGet();
                        byte[] bytes = exchange.getRequestBody().readNBytes(Math.toIntExact(CloudHttp.FILE_BYTES + 1));
                        require(CloudHttp.sha256(bytes).equals(transaction.input.get("raw_sha256").getAsString()),
                                "server verifies actual distribution hash");
                        if (blockBody.getAndSet(false)) {
                            bodyEntered.countDown();
                            bodyRelease.await(10, TimeUnit.SECONDS);
                        }
                        status = transaction.uploaded ? 409 : 204;
                        transaction.uploaded = true;
                        if (dropBody.getAndSet(false)) {
                            status = 503;
                        }
                    } else if (segments[2].equals("commit")) {
                        JsonObject input = json(exchange);
                        if (transaction.commit != null && !transaction.commit.equals(input)) {
                            badReplay.incrementAndGet();
                        }
                        transaction.commit = input;
                        require(transaction.uploaded && input.get("expected_revision").getAsLong()
                                == (transaction.imageIdentity == null ? 1 : 2), "commit uses current image policy CAS");
                        if (!transaction.committed) {
                            published.incrementAndGet();
                        }
                        transaction.committed = true;
                        result = Map.of("model_id", transaction.model, "published", true);
                        if (dropCommit.getAndSet(false)) {
                            status = 503;
                        }
                    }
                } else if (path.startsWith("models/") && path.endsWith("/image")) {
                    UUID model = UUID.fromString(path.split("/")[1]);
                    var transaction = transactions.values().stream().filter(value -> value.model.equals(model)).findFirst().orElseThrow();
                    String identity = exchange.getRequestHeaders().getFirst("x-omnimorph-operation") + ":"
                            + exchange.getRequestHeaders().getFirst("x-omnimorph-expected-revision") + ":"
                            + CloudHttp.sha256(exchange.getRequestBody().readNBytes(2 * 1024 * 1024 + 1));
                    require(exchange.getRequestHeaders().getFirst("x-omnimorph-expected-revision").equals("1"), "original image CAS");
                    if (transaction.imageIdentity == null) {
                        images.incrementAndGet();
                    } else if (!transaction.imageIdentity.equals(identity)) {
                        badReplay.incrementAndGet();
                    }
                    transaction.imageIdentity = identity;
                    result = Map.of("model_id", model, "image_revision", 2, "policy_revision", 2);
                    if (dropImage.getAndSet(false)) {
                        status = 503;
                    }
                } else {
                    unexpected.incrementAndGet();
                    status = 404;
                }
                if (status >= 400) {
                    result = Map.of("code", status == 503 ? "BUSY" : "CONFLICT");
                }
                byte[] response = new Gson().toJson(result).getBytes(StandardCharsets.UTF_8);
                exchange.sendResponseHeaders(status, status == 204 ? -1 : response.length);
                if (status != 204) {
                    exchange.getResponseBody().write(response);
                }
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
            } finally {
                exchange.close();
            }
        }

        private JsonObject json(HttpExchange exchange) throws IOException {
            return JsonParser.parseString(new String(exchange.getRequestBody().readNBytes(65536),
                    StandardCharsets.UTF_8)).getAsJsonObject();
        }

        @Override
        public void close() {
            bodyRelease.countDown();
            server.stop(0);
            threads.shutdownNow();
        }
    }
}
