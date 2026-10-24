package top.aihmc.omnimorph.backend2.library;

import com.google.gson.FieldNamingPolicy;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import top.aihmc.omnimorph.backend2.protocol.CloudEndpoint;
import top.aihmc.omnimorph.backend2.protocol.CloudHttp;
import top.aihmc.omnimorph.backend2.protocol.CloudProtocol;
import top.aihmc.omnimorph.backend2.protocol.FiguraCloudProtocol;
import top.aihmc.omnimorph.backend2.protocol.ProtobufCloudProtocol;
import top.aihmc.omnimorph.model.outfit.CosmeticMetadata;
import top.aihmc.omnimorph.model.outfit.OutfitConfig;
import top.aihmc.omnimorph.model.outfit.OutfitLimits;

import java.net.InetSocketAddress;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/** Real HTTP with both production auth adapters; server contract fixture, not a Rust listener or GPU test. */
public final class OutfitPrivateUploadSmokeTest {
    private static final Gson JSON = new GsonBuilder()
            .setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES).create();

    public static void main(String[] args) throws Exception {
        for (boolean protobuf : new boolean[]{false, true}) {
            try (var server = new Server(protobuf)) {
                run(server, protobuf);
            }
        }
        System.out.println("PASS private outfit upload: both auth adapters, frozen retries, completed reuse, "
                + "receipt identity, limits, scoped paths and retirement");
    }

    private static void run(Server server, boolean protobuf) throws Exception {
        var endpoint = CloudEndpoint.parse(server.origin(), () -> true);
        CloudProtocol protocol = protobuf
                ? new ProtobufCloudProtocol(endpoint, "original", "test", 77, HttpClient.newHttpClient())
                : new FiguraCloudProtocol(endpoint, "original", HttpClient.newHttpClient());
        set(protocol, "ready", true);
        if (protobuf) {
            set(protocol, "session", "scoped");
        }
        var live = new AtomicBoolean(true);
        var callbacks = new ConcurrentLinkedQueue<Runnable>();
        var session = new LibrarySession(server.origin(), new UUID(0, 1), 77,
                () -> live.get() && protocol.connected());
        try (protocol; var client = new LibraryClient(protocol.libraryAccess(), session, callbacks::add)) {
            byte[] source = new byte[]{1, 2, 3, 4};
            var upload = source(source, OutfitConfig.Format.FIGURA, null);
            source[0] = 99;
            upload.bytes()[1] = 99;
            server.expected = upload.bytes();
            server.enabled = false;
            failure(client.uploadOutfitSource(upload, value -> {}).future(), callbacks, 415, "UNSUPPORTED_FORMAT");
            require(server.reservations.get() == 0, "unsupported capability cannot reserve an upload");
            server.enabled = true;
            server.maxBytes = 1;
            server.mode = "completed";
            await(client.uploadOutfitSource(upload, value -> {}).future(), callbacks);
            require(server.bodies.get() == 0, "catalog upload budget does not replace private native grant admission");
            server.maxBytes = 67108864;
            server.mode = "fail-commit";
            failure(client.uploadOutfitSource(upload, value -> {}).future(), callbacks, 503, "INTERNAL");
            server.mode = "normal";
            var binding = await(client.uploadOutfitSource(upload, value -> {}).future(), callbacks);
            require(binding.reference().localId() != null && binding.assetRevision() == 1,
                    "private native receipt is returned without applying an outfit");
            require(server.bodies.get() == 2 && server.commits.get() == 2 && server.sameInput.get(),
                    "failed commit retries use the same operation, declaration and immutable bytes");
            int bodies = server.bodies.get();
            int commits = server.commits.get();
            server.mode = "completed";
            await(client.uploadOutfitSource(upload, value -> {}).future(), callbacks);
            require(server.bodies.get() == bodies && server.commits.get() == commits,
                    "completed reservation skips both body upload and commit");
            server.mode = "completed-wrong-source";
            failure(client.uploadOutfitSource(upload, value -> {}).future(), callbacks,
                    0, "INVALID_OUTFIT_UPLOAD_RESPONSE");
            require(server.bodies.get() == bodies, "completed receipts still require exact frozen source identity");

            server.mode = "existing";
            int reservations = server.reservations.get();
            var freshOperation = new OutfitPrivateUpload(UUID.randomUUID(), upload.reference(), upload.resourceKind(),
                    null, upload.bytes(), null, null);
            var restored = await(client.uploadOutfitSource(freshOperation, value -> {}).future(), callbacks);
            require(restored.assetId().equals(binding.assetId()) && server.reservations.get() == reservations
                            && server.bodies.get() == bodies && server.commits.get() == commits,
                    "a new operation resolves the old source binding without reserving or uploading again");
            try (var restarted = new LibraryClient(protocol.libraryAccess(), session, callbacks::add)) {
                var lookup = await(restarted.resolveOutfitSource(upload.reference(), upload.resourceKind()).future(), callbacks);
                require(lookup.isPresent() && lookup.get().equals(restored),
                        "a fresh client recovers an unworn binding without local upload-operation state");
            }
            var changedBytes = new OutfitPrivateUpload(UUID.randomUUID(), upload.reference(), upload.resourceKind(),
                    null, new byte[]{9, 8, 7, 6}, null, null);
            // A changed distribution of the same source asks the server to replace the binding. This fixture only
            // accepts and acknowledges the original bytes, so the attempt must not be reported as a success.
            boolean replacedAccepted;
            try {
                await(client.uploadOutfitSource(changedBytes, value -> {}).future(), callbacks);
                replacedAccepted = true;
            } catch (ExecutionException rejected) {
                replacedAccepted = false;
            }
            require(!replacedAccepted, "a fixture that keeps the old bytes never yields an accepted replacement");
            require(server.reservations.get() == reservations + 1,
                    "a changed distribution reserves a replacement upload instead of stopping at a conflict");
            reservations = server.reservations.get();
            for (String mode : List.of("lookup-missing-field", "lookup-bad-revision", "lookup-bad-source")) {
                server.mode = mode;
                failure(client.uploadOutfitSource(upload, value -> {}).future(), callbacks,
                        0, "INVALID_OUTFIT_UPLOAD_RESPONSE");
                require(server.reservations.get() == reservations, "malformed lookup is never an unbound source");
            }
            server.mode = "lookup-deleted";
            failure(client.uploadOutfitSource(upload, value -> {}).future(), callbacks, 404, "NOT_FOUND");
            require(server.reservations.get() == reservations, "deleted fixed source must not fall back to uploading");

            for (String mode : List.of("wrong-operation", "foreign-path", "other-operation-path", "small-grant",
                    "fractional-grant", "bad-media", "bad-utf8")) {
                server.mode = mode;
                failure(client.uploadOutfitSource(upload, value -> {}).future(), callbacks,
                        0, "INVALID_OUTFIT_UPLOAD_RESPONSE");
                require(server.bodies.get() == bodies, "invalid reservation cannot start a body: " + mode);
            }
            for (String mode : List.of("changed-source", "changed-raw", "changed-kind", "changed-asset",
                    "fractional-revision", "changed-declaration")) {
                server.mode = mode;
                failure(client.uploadOutfitSource(upload, value -> {}).future(), callbacks,
                        0, "INVALID_OUTFIT_UPLOAD_RESPONSE");
            }
            server.mode = "forbidden";
            failure(client.uploadOutfitSource(upload, value -> {}).future(), callbacks, 403, "FORBIDDEN");
            server.mode = "normal";
            server.firstInput = null;
            var accessory = source(new byte[]{1, 2, 3, 4}, OutfitConfig.Format.YSM_ENCRYPTED,
                    CosmeticMetadata.read("{\"category\":\"Hair\",\"cosmetic_anchor\":\"Head\","
                            + "\"default_transform\":{\"position\":[1,0,0],\"rotation\":[0,0,0],\"scale\":[1,1,1]}}"));
            server.positionLimit = 0;
            reservations = server.reservations.get();
            failure(client.uploadOutfitSource(accessory, value -> {}).future(), callbacks,
                    0, "LIBRARY_REQUEST_FAILED");
            require(server.reservations.get() == reservations, "fresh declaration budget is checked before reserve");
            server.positionLimit = 4096;
            await(client.uploadOutfitSource(accessory, value -> {}).future(), callbacks);
            require(server.lastInput.get("content_sha256").getAsString().equals("c".repeat(64))
                            && server.lastInput.getAsJsonObject("accessory_metadata").getAsJsonObject("default_transform")
                            .get("position").isJsonArray(), "YSM digests and array transforms retain their wire contract");
            var retired = client.uploadOutfitSource(accessory, value -> {});
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
            while (callbacks.isEmpty() && System.nanoTime() < deadline) {
                Thread.sleep(1);
            }
            require(!callbacks.isEmpty(), "result queued before session retirement");
            live.set(false);
            drain(callbacks);
            require(retired.future().isCancelled(), "retired callback cannot publish a prepared private source");
            require(server.authErrors.get() == 0, "all requests use the original endpoint and scoped credentials");
        }
    }

    private static OutfitPrivateUpload source(byte[] bytes, OutfitConfig.Format format, CosmeticMetadata metadata) {
        var reference = new OutfitConfig.ResourceRef(OutfitConfig.Source.LOCAL, UUID.randomUUID(),
                null, null, null, "a".repeat(64), format);
        return new OutfitPrivateUpload(UUID.randomUUID(), reference, metadata == null
                ? OutfitConfig.ResourceKind.AVATAR : OutfitConfig.ResourceKind.ACCESSORY, metadata, bytes,
                format == OutfitConfig.Format.FIGURA ? null : "c".repeat(64),
                format == OutfitConfig.Format.FIGURA ? null : "d".repeat(64));
    }

    private static void set(Object target, String name, Object value) throws Exception {
        var field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }

    private static <T> T await(CompletableFuture<T> future, ConcurrentLinkedQueue<Runnable> callbacks) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (!future.isDone() && System.nanoTime() < deadline) {
            drain(callbacks);
            Thread.sleep(1);
        }
        return future.get(1, TimeUnit.SECONDS);
    }

    private static void failure(CompletableFuture<?> future, ConcurrentLinkedQueue<Runnable> callbacks,
                                int status, String code) throws Exception {
        try {
            await(future, callbacks);
            throw new AssertionError("Expected " + code);
        } catch (ExecutionException error) {
            require(error.getCause() instanceof LibraryClient.Failure failure
                            && failure.status == status && failure.code.equals(code),
                    "safe typed failure: " + error.getCause());
        }
    }

    private static void drain(ConcurrentLinkedQueue<Runnable> callbacks) {
        Runnable action;
        while ((action = callbacks.poll()) != null) {
            action.run();
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    private static final class Server implements AutoCloseable {
        private final HttpServer server;
        private final java.util.concurrent.ExecutorService workers = Executors.newFixedThreadPool(3);
        private final boolean protobuf;
        private final AtomicInteger reservations = new AtomicInteger();
        private final AtomicInteger bodies = new AtomicInteger();
        private final AtomicInteger commits = new AtomicInteger();
        private final AtomicInteger authErrors = new AtomicInteger();
        private final AtomicBoolean sameInput = new AtomicBoolean(true);
        private volatile JsonObject firstInput;
        private volatile JsonObject lastInput;
        private volatile byte[] expected;
        private volatile boolean enabled = true;
        private volatile long maxBytes = 67108864;
        private volatile double positionLimit = 4096;
        private volatile String mode = "normal";

        private Server(boolean protobuf) throws Exception {
            this.protobuf = protobuf;
            server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.setExecutor(workers);
            server.createContext("/", this::handle);
            server.start();
        }

        private String origin() {
            return "http://127.0.0.1:" + server.getAddress().getPort();
        }

        private void handle(HttpExchange exchange) throws java.io.IOException {
            try (exchange) {
                var headers = exchange.getRequestHeaders();
                boolean authorized = protobuf
                        ? "Bearer scoped".equals(headers.getFirst("authorization"))
                            && "77".equals(headers.getFirst("x-omnimorph-epoch"))
                        : "Bearer original".equals(headers.getFirst("authorization"))
                            && "figura".equals(headers.getFirst("x-omnimorph-auth-mode"));
                if (!authorized || !origin().equals(headers.getFirst("x-omnimorph-origin"))) {
                    authErrors.incrementAndGet();
                    send(exchange, 401, "{\"code\":\"UNAUTHENTICATED\"}");
                    return;
                }
                String path = exchange.getRequestURI().getPath();
                if (path.endsWith("/capabilities")) {
                    var configured = JSON.toJsonTree(OutfitLimits.defaults()).getAsJsonObject();
                    configured.addProperty("position_limit", positionLimit);
                    var limits = JSON.fromJson(configured, OutfitLimits.class);
                    var outfits = new LibraryModels.OutfitCapabilities(1, true, true, enabled, false,
                            limits.maxInstances(), limits.maxVirtualAnchors(), limits.maxDepth(), limits.maxResourceNodes(),
                            limits.maxTotalNodes(), limits.maxBoneOverrides(), limits.maxJsonBytes(), limits.maxTrails(),
                            limits.maxTrailSamples(), limits);
                    send(exchange, 200, JSON.toJson(new LibraryModels.Capabilities(true, 1, maxBytes, 8,
                            2097152, 2048, 2048, List.of("zip", "ysm"),
                            List.of("FIGURA", "YSM_PLAIN", "YSM_ENCRYPTED"), List.of("general"), false, outfits)));
                    return;
                }
                if (mode.equals("forbidden")) {
                    send(exchange, 403, "{\"code\":\"FORBIDDEN\",\"detail\":\"private text\"}");
                    return;
                }
                if (path.endsWith("/local/resolve")) {
                    require(exchange.getRequestMethod().equals("POST"), "owner lookup method");
                    var query = JsonParser.parseString(new String(exchange.getRequestBody().readAllBytes(),
                            StandardCharsets.UTF_8)).getAsJsonObject();
                    require(query.size() == 2 && query.has("reference") && query.has("resource_kind"),
                            "lookup identity has no caller-selected owner");
                    if (mode.equals("lookup-deleted")) {
                        send(exchange, 404, "{\"code\":\"NOT_FOUND\"}");
                        return;
                    }
                    var result = new JsonObject();
                    if (mode.equals("existing") || mode.equals("lookup-bad-revision") || mode.equals("lookup-bad-source")) {
                        result.add("resource", binding());
                        if (mode.equals("lookup-bad-revision")) {
                            result.getAsJsonObject("resource").addProperty("asset_revision", 1.5);
                        } else if (mode.equals("lookup-bad-source")) {
                            result.getAsJsonObject("resource").getAsJsonObject("reference")
                                    .addProperty("content_hash", "b".repeat(64));
                        }
                    } else if (!mode.equals("lookup-missing-field")) {
                        result.add("resource", com.google.gson.JsonNull.INSTANCE);
                    }
                    send(exchange, 200, result.toString());
                    return;
                }
                if (path.endsWith("/uploads")) {
                    require(exchange.getRequestMethod().equals("POST"), "reserve method");
                    reservations.incrementAndGet();
                    lastInput = JsonParser.parseString(new String(exchange.getRequestBody().readAllBytes(),
                            StandardCharsets.UTF_8)).getAsJsonObject();
                    if (firstInput == null) {
                        firstInput = lastInput.deepCopy();
                    } else if (!firstInput.equals(lastInput)) {
                        sameInput.set(false);
                    }
                    var result = new JsonObject();
                    result.add("operation_id", lastInput.get("operation_id"));
                    result.addProperty("completed", mode.startsWith("completed"));
                    result.addProperty("relative_path", path + "/" + lastInput.get("operation_id").getAsString() + "/body");
                    result.addProperty("expires_at", 1900000000);
                    result.addProperty("max_bytes", 67108864);
                    result.add("resource", binding());
                    switch (mode) {
                        case "completed-wrong-source" -> result.getAsJsonObject("resource")
                                .getAsJsonObject("reference").addProperty("content_hash", "b".repeat(64));
                        case "wrong-operation" -> result.addProperty("operation_id", UUID.randomUUID().toString());
                        case "foreign-path" -> result.addProperty("relative_path", "https://invalid.test/body");
                        case "other-operation-path" -> result.addProperty("relative_path", path + "/" + UUID.randomUUID() + "/body");
                        case "small-grant" -> result.addProperty("max_bytes", 1);
                        case "fractional-grant" -> result.addProperty("max_bytes", 4.5);
                        default -> { }
                    }
                    send(exchange, 200, result.toString());
                } else if (path.endsWith("/body")) {
                    require(exchange.getRequestMethod().equals("PUT"), "body method");
                    require("application/octet-stream".equals(headers.getFirst("content-type")), "native body media");
                    require(Arrays.equals(expected, exchange.getRequestBody().readAllBytes()), "frozen native bytes");
                    require(CloudHttp.sha256(expected).equals(lastInput.get("raw_sha256").getAsString()), "wire digest");
                    bodies.incrementAndGet();
                    exchange.sendResponseHeaders(204, -1);
                } else if (path.endsWith("/commit")) {
                    commits.incrementAndGet();
                    require(exchange.getRequestMethod().equals("POST"), "commit method");
                    var receipt = binding();
                    switch (mode) {
                        case "changed-source" -> receipt.getAsJsonObject("reference").addProperty("content_hash", "b".repeat(64));
                        case "changed-raw" -> receipt.addProperty("raw_sha256", "b".repeat(64));
                        case "changed-kind" -> receipt.addProperty("resource_kind", "ACCESSORY");
                        case "changed-asset" -> receipt.addProperty("asset_id", "another");
                        case "fractional-revision" -> receipt.addProperty("asset_revision", 1.5);
                        case "changed-declaration" -> receipt.add("accessory_metadata", new JsonObject());
                        default -> { }
                    }
                    send(exchange, mode.equals("fail-commit") ? 503 : 200,
                            mode.equals("fail-commit") ? "{\"code\":\"INTERNAL\"}" : receipt.toString());
                } else {
                    throw new AssertionError("Unexpected endpoint " + path);
                }
            }
        }

        private JsonObject binding() {
            var result = new JsonObject();
            for (String key : List.of("reference", "raw_sha256", "resource_kind", "accessory_metadata")) {
                result.add(key, lastInput.get(key).deepCopy());
            }
            result.addProperty("asset_id", "outfit_" + lastInput.get("operation_id").getAsString().replace("-", ""));
            result.addProperty("asset_revision", 1);
            return result;
        }

        private void send(HttpExchange exchange, int status, String body) throws java.io.IOException {
            boolean reservation = exchange.getRequestURI().getPath().endsWith("/uploads");
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            if (reservation && mode.equals("bad-utf8")) {
                bytes[2] = (byte) 0xff;
            }
            exchange.getResponseHeaders().add("content-type",
                    reservation && mode.equals("bad-media") ? "text/html" : "application/json");
            exchange.sendResponseHeaders(status, bytes.length);
            exchange.getResponseBody().write(bytes);
        }

        @Override
        public void close() {
            server.stop(0);
            workers.shutdownNow();
        }
    }
}
