package top.aihmc.omnimorph.backend2.library;

import com.google.gson.FieldNamingPolicy;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import top.aihmc.omnimorph.backend2.protocol.CloudEndpoint;
import top.aihmc.omnimorph.backend2.protocol.CloudProtocol;
import top.aihmc.omnimorph.backend2.protocol.FiguraCloudProtocol;
import top.aihmc.omnimorph.backend2.protocol.ProtobufCloudProtocol;
import top.aihmc.omnimorph.model.outfit.OutfitCodec;
import top.aihmc.omnimorph.model.outfit.OutfitConfig;
import top.aihmc.omnimorph.model.outfit.OutfitLimits;

import java.net.InetSocketAddress;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;

/** Production HTTP adapters and typed Apply transport; no Avatar, game-window or GPU claim. */
public final class OutfitApplyClientSmokeTest {
    private static final Gson JSON = new GsonBuilder()
            .setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES).create();

    public static void main(String[] args) throws Exception {
        for (boolean protobuf : new boolean[]{false, true}) {
            try (var server = new Server(protobuf)) {
                run(server, protobuf);
            }
        }
        System.out.println("PASS outfit Apply client: both credentials, capability/limit rechecks, vector wire, "
                + "receipt/conflict isolation, stale retirement and bounded envelopes");
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
            UUID operation = UUID.randomUUID();
            OutfitConfig prepared = sample();
            server.advertise = false;
            failure(client.applyOutfit(operation, prepared).future(), callbacks, 415, "UNSUPPORTED_FORMAT");
            server.advertise = true;
            server.enabled = false;
            failure(client.applyOutfit(operation, prepared).future(), callbacks, 415, "UNSUPPORTED_FORMAT");
            require(server.applies.get() == 0, "unsupported backends receive no mutation or protocol fallback");
            server.enabled = true;
            server.maxInstances = 0;
            failure(client.applyOutfit(operation, prepared).future(), callbacks, 0, "LIBRARY_REQUEST_FAILED");
            require(server.applies.get() == 0, "a lowered negotiated instance budget is checked before PUT");
            server.maxInstances = 32;
            var result = await(client.applyOutfit(operation, prepared).future(), callbacks);
            require(result instanceof OutfitApplyResult.Accepted, "successful receipt is a distinct result");
            var accepted = (OutfitApplyResult.Accepted) result;
            require(accepted.canActivate(operation, 0) && !accepted.canActivate(UUID.randomUUID(), 0),
                    "only the prepared operation and revision may activate");
            require(server.vectorArrays.get(), "vectors use the outfit array codec on the actual wire");
            server.mode = "replay";
            var replay = (OutfitApplyResult.Accepted) await(client.applyOutfit(operation, prepared).future(), callbacks);
            require(replay.currentRevision() == 2 && !replay.canActivate(operation, 0),
                    "a superseded receipt remains observable but cannot activate");

            server.mode = "conflict";
            server.maxInstances = 0;
            var conflict = await(client.applyOutfit(operation, OutfitConfig.empty()).future(), callbacks);
            require(conflict instanceof OutfitApplyResult.Conflict current && current.currentRevision() == 9
                            && current.configuration().instances().size() == 1,
                    "stored configuration survives for inspection when it exceeds the new budget");
            server.maxInstances = 32;
            for (String mode : List.of("wrong-operation", "changed-configuration", "fractional", "fractional-ordinal", "bad-media", "bad-utf8")) {
                server.mode = mode;
                failure(client.applyOutfit(operation, prepared).future(), callbacks, 0, "INVALID_OUTFIT_APPLY_RESPONSE");
            }
            server.mode = "changed-source-revision";
            var preciseBase = new OutfitConfig.ResourceRef(OutfitConfig.Source.CLOUD, null, server.origin(),
                    UUID.randomUUID(), 1L << 53, "c".repeat(64), OutfitConfig.Format.FIGURA);
            var precise = new OutfitConfig(1, 0, preciseBase, List.of(), List.of(), Map.of(), true);
            failure(client.applyOutfit(operation, precise).future(), callbacks, 0, "INVALID_OUTFIT_APPLY_RESPONSE");
            server.mode = "oversized";
            failure(client.applyOutfit(operation, prepared).future(), callbacks, 0, "LIBRARY_REQUEST_FAILED");
            server.mode = "forbidden";
            failure(client.applyOutfit(operation, prepared).future(), callbacks, 403, "FORBIDDEN");

            server.mode = "normal";
            var large = large();
            var largeResult = await(client.applyOutfit(operation, large).future(), callbacks);
            require(largeResult instanceof OutfitApplyResult.Accepted && server.requestBytes > 65536
                            && server.responseBytes > 65536 && server.responseBytes <= OutfitApplyResult.JSON_BYTES,
                    "Apply envelopes may exceed ordinary control JSON without raising that global limit");
            var retired = client.applyOutfit(UUID.randomUUID(), prepared);
            waitFor(() -> !callbacks.isEmpty());
            live.set(false);
            drain(callbacks);
            require(retired.future().isCancelled(), "queued Apply data cannot publish into a retired account/session");
            live.set(true);
            require(server.authErrors.get() == 0, "endpoint origin and each adapter's credentials stay bound");
        }
    }

    private static OutfitConfig sample() {
        var reference = new OutfitConfig.ResourceRef(OutfitConfig.Source.LOCAL, UUID.randomUUID(),
                null, null, null, "a".repeat(64), OutfitConfig.Format.FIGURA);
        var instance = new OutfitConfig.Instance(UUID.randomUUID(), reference, 1, true,
                new OutfitConfig.AnchorRef(OutfitConfig.AnchorType.AUTO_TRAIL, null, null, null, null, null),
                OutfitConfig.Transform.identity(), List.of(), Map.of("speed", 1, "name", "世界"), Map.of());
        var parent = new OutfitConfig.BoneRef(null, List.of(new OutfitConfig.BoneSegment("Root", 0)), "b".repeat(64));
        var anchor = new OutfitConfig.VirtualAnchor(UUID.randomUUID(), "Virtual", parent, OutfitConfig.Transform.identity());
        return new OutfitConfig(1, 0, null, List.of(instance), List.of(anchor), Map.of(reference.identity(), 1), true);
    }

    private static OutfitConfig large() {
        OutfitConfig result = null;
        for (int width = 1; width <= 507; width++) {
            var watermarks = new LinkedHashMap<String, Integer>();
            for (int index = 0; index < 128; index++) {
                watermarks.put(String.format("%03d-", index) + "x".repeat(width), 1);
            }
            var candidate = new OutfitConfig(1, 0, null, List.of(), List.of(), watermarks, false);
            try {
                if (OutfitCodec.encode(candidate).getBytes(StandardCharsets.UTF_8).length > OutfitConfig.MAX_JSON_BYTES - 32) {
                    break;
                }
                result = candidate;
            } catch (IllegalArgumentException overBudget) {
                break;
            }
        }
        require(result != null, "large configuration exists");
        var watermarks = new LinkedHashMap<>(result.displayOrdinals());
        for (String key : new ArrayList<>(watermarks.keySet())) {
            if (key.length() == 512) {
                continue;
            }
            watermarks.remove(key);
            watermarks.put(key + "x", 1);
            var candidate = new OutfitConfig(1, 0, null, List.of(), List.of(), watermarks, false);
            try {
                if (OutfitCodec.encode(candidate).getBytes(StandardCharsets.UTF_8).length <= OutfitConfig.MAX_JSON_BYTES - 32) {
                    result = candidate;
                } else {
                    watermarks.remove(key + "x");
                    watermarks.put(key, 1);
                }
            } catch (IllegalArgumentException overBudget) {
                watermarks.remove(key + "x");
                watermarks.put(key, 1);
            }
        }
        require(OutfitCodec.encode(result).getBytes(StandardCharsets.UTF_8).length > 65500,
                "configuration approaches the actual UTF-8 budget");
        return result;
    }

    private static void set(Object target, String name, Object value) throws Exception {
        var field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }

    private static <T> T await(CompletableFuture<T> future, ConcurrentLinkedQueue<Runnable> callbacks) throws Exception {
        waitFor(() -> {
            drain(callbacks);
            return future.isDone();
        });
        return future.get(1, TimeUnit.SECONDS);
    }

    private static void failure(CompletableFuture<?> future, ConcurrentLinkedQueue<Runnable> callbacks,
                                int status, String code) throws Exception {
        try {
            await(future, callbacks);
            throw new AssertionError("Expected " + code);
        } catch (ExecutionException error) {
            require(error.getCause() instanceof LibraryClient.Failure failure
                            && failure.status == status && failure.code.equals(code) && failure.getMessage().equals(code),
                    "typed failure contains no raw server data: " + error.getCause());
        }
    }

    private static void drain(ConcurrentLinkedQueue<Runnable> callbacks) {
        Runnable action;
        while ((action = callbacks.poll()) != null) {
            action.run();
        }
    }

    private static void waitFor(BooleanSupplier condition) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (!condition.getAsBoolean() && System.nanoTime() < deadline) {
            Thread.sleep(1);
        }
        require(condition.getAsBoolean(), "bounded completion");
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    private static final class Server implements AutoCloseable {
        private final HttpServer server;
        private final java.util.concurrent.ExecutorService workers = Executors.newFixedThreadPool(4);
        private final boolean protobuf;
        private final AtomicInteger applies = new AtomicInteger();
        private final AtomicInteger authErrors = new AtomicInteger();
        private final AtomicBoolean vectorArrays = new AtomicBoolean();
        private final OutfitConfig stored = sample();
        private volatile boolean advertise = true;
        private volatile boolean enabled = true;
        private volatile int maxInstances = 32;
        private volatile String mode = "normal";
        private volatile int requestBytes;
        private volatile int responseBytes;

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
                authorized &= origin().equals(headers.getFirst("x-omnimorph-origin"));
                if (!authorized) {
                    authErrors.incrementAndGet();
                    send(exchange, 401, "{\"code\":\"UNAUTHENTICATED\"}", false);
                    return;
                }
                if (exchange.getRequestURI().getPath().equals("/api/library/v1/capabilities")) {
                    send(exchange, 200, capabilities().toString(), false);
                    return;
                }
                applies.incrementAndGet();
                byte[] input = exchange.getRequestBody().readNBytes(OutfitApplyResult.JSON_BYTES + 1);
                requestBytes = input.length;
                var request = JsonParser.parseString(new String(input, StandardCharsets.UTF_8)).getAsJsonObject();
                var configuration = request.getAsJsonObject("configuration").deepCopy();
                if (!configuration.getAsJsonArray("virtual_anchors").isEmpty()) {
                    require(configuration.get("base").isJsonNull(), "vanilla base has the server's explicit null");
                    require(configuration.getAsJsonArray("virtual_anchors").get(0).getAsJsonObject()
                            .getAsJsonObject("parent").get("instance_id").isJsonNull(), "base bone owner is explicit null");
                    require(configuration.getAsJsonArray("instances").get(0).getAsJsonObject()
                            .getAsJsonObject("anchor").get("preferred_instance_id").isJsonNull(), "automatic fallback is explicit null");
                }
                if (!configuration.getAsJsonArray("instances").isEmpty()) {
                    vectorArrays.set(configuration.getAsJsonArray("instances").get(0).getAsJsonObject()
                            .getAsJsonObject("transform").get("position").isJsonArray());
                }
                long revision = configuration.get("revision").getAsLong() + 1;
                configuration.addProperty("revision", revision);
                configuration.add("base", configuration.has("base") ? configuration.get("base") : JsonNull.INSTANCE);
                var result = new JsonObject();
                result.addProperty("schema_version", 1);
                result.add("operation_id", request.get("operation_id"));
                var applied = new JsonObject();
                applied.add("configuration", configuration);
                applied.addProperty("equipment_revision", 0);
                result.add("applied", applied);
                result.addProperty("current_revision", revision);
                int status = 200;
                switch (mode) {
                    case "replay" -> result.addProperty("current_revision", revision + 1);
                    case "conflict" -> {
                        status = 409;
                        result = new JsonObject();
                        var current = JsonParser.parseString(OutfitCodec.encode(stored)).getAsJsonObject();
                        current.addProperty("revision", 9);
                        result.add("configuration", current);
                        result.addProperty("current_revision", 9);
                        result.addProperty("code", "CONFLICT");
                        result.addProperty("request_id", UUID.randomUUID().toString());
                        result.addProperty("retryable", false);
                    }
                    case "wrong-operation" -> result.addProperty("operation_id", UUID.randomUUID().toString());
                    case "changed-configuration" -> configuration.addProperty("show_equipment", false);
                    case "fractional" -> result.addProperty("current_revision", 1.5);
                    case "fractional-ordinal" -> configuration.getAsJsonArray("instances").get(0).getAsJsonObject()
                            .addProperty("display_ordinal", 1.5);
                    case "changed-source-revision" -> {
                        var base = configuration.getAsJsonObject("base");
                        base.addProperty("revision", base.get("revision").getAsLong() + 1);
                    }
                    case "oversized" -> result.addProperty("padding", "x".repeat(OutfitApplyResult.JSON_BYTES));
                    case "forbidden" -> {
                        status = 403;
                        result = new JsonObject();
                        result.addProperty("code", "FORBIDDEN");
                        result.addProperty("private_detail", "do not publish this server text");
                    }
                    default -> {
                    }
                }
                if (mode.equals("bad-utf8")) {
                    result.addProperty("padding", "bad");
                }
                send(exchange, status, result.toString(), mode.equals("bad-media"));
            }
        }

        private JsonObject capabilities() {
            var limits = JSON.toJsonTree(OutfitLimits.defaults()).getAsJsonObject();
            limits.addProperty("max_instances", maxInstances);
            var configured = JSON.fromJson(limits, OutfitLimits.class);
            var outfits = new LibraryModels.OutfitCapabilities(1, true, true, enabled, false,
                    configured.maxInstances(), configured.maxVirtualAnchors(), configured.maxDepth(),
                    configured.maxResourceNodes(), configured.maxTotalNodes(), configured.maxBoneOverrides(),
                    configured.maxJsonBytes(), configured.maxTrails(), configured.maxTrailSamples(), configured);
            var value = new LibraryModels.Capabilities(true, 1, 67108864, 8, 2097152, 2048, 2048,
                    List.of("zip", "ysm"), List.of("FIGURA", "YSM_PLAIN", "YSM_ENCRYPTED"), List.of("general"),
                    false, advertise ? outfits : null);
            return JSON.toJsonTree(value).getAsJsonObject();
        }

        private void send(HttpExchange exchange, int status, String body, boolean badMedia) throws java.io.IOException {
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            if (mode.equals("bad-utf8") && exchange.getRequestURI().getPath().endsWith("/current")) {
                int offset = body.substring(0, body.indexOf("bad")).getBytes(StandardCharsets.UTF_8).length;
                bytes[offset] = (byte) 0xff;
            }
            responseBytes = bytes.length;
            exchange.getResponseHeaders().add("content-type", badMedia ? "text/html" : "application/json");
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
