package top.aihmc.omnimorph.model.library;

import com.google.gson.FieldNamingPolicy;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParser;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import top.aihmc.omnimorph.backend2.library.LibraryClient;
import top.aihmc.omnimorph.backend2.library.LibraryModels;
import top.aihmc.omnimorph.backend2.library.LibrarySession;
import top.aihmc.omnimorph.backend2.protocol.CloudEndpoint;
import top.aihmc.omnimorph.backend2.protocol.CloudHttp;
import top.aihmc.omnimorph.backend2.protocol.LibraryAccess;
import top.aihmc.omnimorph.model.outfit.OutfitConfig;

import java.io.ByteArrayOutputStream;
import java.net.InetSocketAddress;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/** Real HTTP authorization, wrapped cache and metadata/reference checks; no model or GUI activation. */
public final class CloudCosmeticSourceSmokeTest {
    public static final String MANIFEST = "{\"name\":\"星翼\",\"category\":\"Wings\",\"cosmetic_anchor\":\"wing_anchor\","
            + "\"default_transform\":{\"position\":[1,2,3],\"rotation\":[0,90,0],\"scale\":[1,1,1]}}";

    public static void main(String[] args) throws Exception {
        var header = new CompoundTag();
        header.putString("resource_kind", "ACCESSORY");
        header.putString("cosmetic_json", MANIFEST);
        var nbt = new CompoundTag();
        nbt.put("metadata", header);
        var root = Files.createTempDirectory("outfit-cloud-source-");
        var callbacks = new ConcurrentLinkedQueue<Runnable>();
        var live = new AtomicBoolean(true);
        try (var server = new Server(nbt, MANIFEST);
             var access = new LibraryAccess(CloudEndpoint.parse(server.origin(), () -> true), HttpClient.newHttpClient(),
                     request -> request.header("Authorization", "Bearer synthetic-player"));
             var client = new LibraryClient(access, new LibrarySession(server.origin(), UUID.randomUUID(), 1, live::get), callbacks::add)) {
            var source = new CloudCosmeticSource(client, new LibraryTransferService(client, root), callbacks::add);
            var first = await(source.select(server.model()).future(), callbacks);
            var reference = first.descriptor().reference();
            require(reference.source() == OutfitConfig.Source.CLOUD && reference.origin().equals(server.origin())
                            && reference.revision() == 1 && reference.contentHash().equals(CloudHttp.sha256(server.bytes))
                            && first.descriptor().metadata().defaultTransform().position().x() == 1,
                    "accepted data retains the exact authorized reference and author defaults");
            try (var batch = source.readAll(List.of(reference, reference))) {
                require(await(batch.future(), callbacks).size() == 1 && server.files.get() == 1 && server.details.get() == 2,
                        "shared resources are read once per batch and cache hits still authorize with fresh detail");
            }
            var badHash = new OutfitConfig.ResourceRef(OutfitConfig.Source.CLOUD, null, server.origin(), reference.resourceId(),
                    reference.revision(), "f".repeat(64), reference.format());
            failure(source.read(badHash).future(), callbacks, "COSMETIC_RESOURCE_REFERENCE_MISMATCH");
            server.revision.set(2);
            failure(source.read(reference).future(), callbacks, "RESOURCE_REVISION_UNAVAILABLE");
            require(server.files.get() == 1, "fixed references never silently download an updated revision");
            server.revision.set(1);
            server.mismatched.set(true);
            failure(source.read(reference).future(), callbacks, "COSMETIC_METADATA_MISMATCH");
            server.mismatched.set(false);
            server.revoked.set(true);
            failure(source.read(reference).future(), callbacks, "FORBIDDEN");
            require(server.files.get() == 1, "previous preview bytes do not bypass a revocation");
            server.revoked.set(false);
            var wrongOrigin = new OutfitConfig.ResourceRef(OutfitConfig.Source.CLOUD, null, "https://example.invalid",
                    reference.resourceId(), reference.revision(), reference.contentHash(), reference.format());
            int details = server.details.get();
            try (var wrong = source.readAll(List.of(wrongOrigin))) {
                failure(wrong.future(), callbacks, "COSMETIC_RESOURCE_SCOPE_MISMATCH");
            }
            require(server.details.get() == details, "another origin cannot borrow this connection's authorization");
            server.gate.set(true);
            try (var cancelled = source.readAll(List.of(reference))) {
                require(server.entered.await(5, TimeUnit.SECONDS), "the request reaches the controlled server gate");
                cancelled.close();
                server.release.countDown();
                require(cancelled.future().isCancelled(), "closing a generation cancels the real in-flight request");
            }
            live.set(false);
            try {
                first.nbt();
                throw new AssertionError("retired session exposed a prepared source");
            } catch (java.util.concurrent.CancellationException expected) {
                // Session scope remains authoritative after preparation as well.
            }
            require(server.unexpected.get() == 0, "private preview never equips, uploads or completes a download receipt");
            System.out.println("PASS cloud cosmetic source: actual HTTP/cache, exact references, metadata agreement, shared reads, revocation, cancellation and session retirement; GUI separate");
        } finally {
            try (var files = Files.walk(root)) {
                for (var path : files.sorted(java.util.Comparator.reverseOrder()).toList()) {
                    Files.deleteIfExists(path);
                }
            }
        }
    }

    private static <T> T await(CompletableFuture<T> future, ConcurrentLinkedQueue<Runnable> callbacks) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
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
            throw new AssertionError("expected " + code);
        } catch (ExecutionException invalid) {
            Throwable cause = invalid.getCause();
            while (cause instanceof CompletionException && cause.getCause() != null) {
                cause = cause.getCause();
            }
            require(cause instanceof LibraryClient.Failure failure && failure.code.equals(code), "controlled failure: " + cause);
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    /** Shared transport fixture for the production screen's native preparation regression. */
    static final class Server implements AutoCloseable {
        static final UUID MODEL = new UUID(0, 751);
        private static final UUID TRANSFER = new UUID(0, 752);
        private static final Gson JSON = new GsonBuilder().setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES).create();
        private final HttpServer server;
        private final byte[] bytes;
        private final String manifest;
        private final String format;
        private final String kind;
        final AtomicLong revision = new AtomicLong(1);
        final AtomicBoolean revoked = new AtomicBoolean();
        final AtomicBoolean mismatched = new AtomicBoolean();
        final AtomicBoolean gate = new AtomicBoolean();
        final CountDownLatch entered = new CountDownLatch(1);
        final CountDownLatch release = new CountDownLatch(1);
        final AtomicInteger details = new AtomicInteger();
        final AtomicInteger files = new AtomicInteger();
        final AtomicInteger unexpected = new AtomicInteger();

        Server(CompoundTag nbt, String manifest) throws Exception {
            this.manifest = manifest;
            kind = manifest == null ? "AVATAR" : "ACCESSORY";
            boolean ysm = nbt.getCompoundOrEmpty("metadata").getStringOr("format", "").equals("ysm-native");
            format = !ysm ? "FIGURA" : top.aihmc.omnimorph.avatar.ysm.YsmSourcePayload.isEncrypted(nbt.getCompoundOrEmpty("ysm"))
                    ? "YSM_ENCRYPTED" : "YSM_PLAIN";
            var output = new ByteArrayOutputStream();
            NbtIo.writeCompressed(top.aihmc.omnimorph.avatar.ysm.YsmSourcePayload.forOutput(nbt), output);
            bytes = output.toByteArray();
            server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.createContext("/api/library/v1/", this::handle);
            server.start();
        }

        String origin() {
            return "http://127.0.0.1:" + server.getAddress().getPort();
        }

        LibraryModels.Model model() {
            var metadata = manifest == null ? null : JsonParser.parseString(manifest).getAsJsonObject();
            if (metadata != null && mismatched.get()) {
                metadata.addProperty("category", "Back");
            }
            return new LibraryModels.Model(MODEL, revision.get(), 1, "Cloud 星翼", "星翼", List.of("Author"),
                    new LibraryModels.Uploader(new UUID(0, 19), "Author"), format, 0, false, 0, 0, false,
                    "PUBLIC", false, List.of("preview", "download"), "", "Wings", List.of(), "ACTIVE",
                    bytes.length, 1, "AUTHOR", "CLIENT_EXTRACTED", kind, metadata);
        }

        String rawHash() {
            return CloudHttp.sha256(bytes);
        }

        private void handle(HttpExchange exchange) throws java.io.IOException {
            try (exchange) {
                String path = exchange.getRequestURI().getPath();
                if (path.equals("/api/library/v1/capabilities")) {
                    var capabilities = new LibraryModels.Capabilities(true, 1, CloudHttp.FILE_BYTES, 32,
                            1024 * 1024, 1024, 1024, List.of("nbt"), List.of("FIGURA", "YSM_PLAIN", "YSM_ENCRYPTED"), List.of(), false);
                    respond(exchange, 200, JSON.toJson(capabilities));
                } else if (path.equals("/api/library/v1/models/" + MODEL) && exchange.getRequestMethod().equals("GET")) {
                    details.incrementAndGet();
                    if (gate.get()) {
                        entered.countDown();
                        try {
                            release.await(5, TimeUnit.SECONDS);
                        } catch (InterruptedException interrupted) {
                            Thread.currentThread().interrupt();
                        }
                    }
                    respond(exchange, revoked.get() ? 403 : 200, revoked.get() ? "{\"code\":\"FORBIDDEN\"}" : JSON.toJson(model()));
                } else if (path.equals("/api/library/v1/models/" + MODEL + "/transfers") && exchange.getRequestMethod().equals("POST")) {
                    var input = JsonParser.parseString(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8)).getAsJsonObject();
                    if (!input.get("purpose").getAsString().equals("preview") || input.get("model_revision").getAsLong() != revision.get()) {
                        respond(exchange, 409, "{\"code\":\"CONFLICT\"}");
                        return;
                    }
                    respond(exchange, 200, "{\"transfer_id\":\"" + TRANSFER + "\",\"purpose\":\"preview\",\"model_revision\":"
                            + revision.get() + ",\"policy_revision\":1,\"relative_path\":\"/api/library/v1/transfers/" + TRANSFER
                            + "\",\"expires_at\":" + (System.currentTimeMillis() / 1000 + 60) + "}");
                } else if (path.equals("/api/library/v1/transfers/" + TRANSFER) && exchange.getRequestMethod().equals("GET")) {
                    files.incrementAndGet();
                    exchange.getResponseHeaders().set("x-omnimorph-sha256", CloudHttp.sha256(bytes));
                    exchange.sendResponseHeaders(200, bytes.length);
                    exchange.getResponseBody().write(bytes);
                } else {
                    unexpected.incrementAndGet();
                    respond(exchange, 404, "{\"code\":\"NOT_FOUND\"}");
                }
            }
        }

        private static void respond(HttpExchange exchange, int status, String value) throws java.io.IOException {
            byte[] body = value.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(status, body.length);
            exchange.getResponseBody().write(body);
        }

        @Override
        public void close() {
            release.countDown();
            server.stop(0);
        }
    }
}
