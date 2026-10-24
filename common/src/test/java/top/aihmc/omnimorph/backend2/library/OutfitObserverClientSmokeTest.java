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
import top.aihmc.omnimorph.backend2.protocol.CloudHttp;
import top.aihmc.omnimorph.backend2.protocol.CloudProtocol;
import top.aihmc.omnimorph.backend2.protocol.FiguraCloudProtocol;
import top.aihmc.omnimorph.backend2.protocol.ProtobufCloudProtocol;
import top.aihmc.omnimorph.model.outfit.OutfitCodec;
import top.aihmc.omnimorph.model.outfit.OutfitConfig;
import top.aihmc.omnimorph.model.outfit.OutfitLimits;

import java.net.InetSocketAddress;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/** Real client HTTP/authentication and bounded render projection parsing; no native model or GUI claim. */
public final class OutfitObserverClientSmokeTest {
    private static final Gson JSON = new GsonBuilder().setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES).create();

    public static void main(String[] args) throws Exception {
        for (boolean protobuf : new boolean[]{false, true}) {
            try (var server = new Server(protobuf)) {
                run(server, protobuf);
            }
        }
        System.out.println("PASS observer outfit transport: both credentials, exact source set/path/revision, bounded envelopes, hidden/legacy distinction and body integrity; native activation pending");
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
        try (protocol; var client = new LibraryClient(protocol.libraryAccess(),
                new LibrarySession(server.origin(), new UUID(0, 1), 77, () -> live.get() && protocol.connected()), Runnable::run)) {
            var snapshot = await(client.observerOutfit(server.owner).future());
            require(snapshot.revision() == 7 && !snapshot.legacyFallback() && snapshot.resources().size() == 1,
                    "typed snapshot retains its exact configuration revision and source");
            byte[] bytes = await(client.submit(LibraryClient.Lane.TRANSFER,
                    exchange -> exchange.observerOutfitBody(snapshot, snapshot.resources().getFirst())).future());
            require(java.util.Arrays.equals(bytes, server.body), "scoped body uses the actual binary route and verifies its hash");
            server.mode = "large-valid";
            require(await(client.observerOutfit(server.owner).future()).revision() == 7,
                    "observer envelopes larger than the ordinary 64KiB control boundary have their own bounded reader");
            for (String invalid : List.of("wrong-owner", "wrong-revision", "string-revision", "missing-source",
                    "duplicate-source", "extra-source", "foreign-path", "wrong-format", "string-config-revision", "bad-media", "bad-utf8")) {
                server.mode = invalid;
                failure(client.observerOutfit(server.owner).future(), "INVALID_OUTFIT_OBSERVER_SNAPSHOT");
            }
            server.mode = "oversized";
            failure(client.observerOutfit(server.owner).future(), "LIBRARY_REQUEST_FAILED");
            server.mode = "hidden";
            var hidden = await(client.observerOutfit(server.owner).future());
            require(hidden.configuration() == null && !hidden.legacyFallback(), "hidden modern equipment must not fall back to the legacy base");
            server.mode = "legacy";
            require(await(client.observerOutfit(server.owner).future()).legacyFallback(), "revision-zero accounts keep an explicit legacy fallback");
            server.mode = "bad-body-hash";
            failure(client.submit(LibraryClient.Lane.TRANSFER,
                    exchange -> exchange.observerOutfitBody(snapshot, snapshot.resources().getFirst())).future(), "DISTRIBUTION_HASH_MISMATCH");
            server.mode = "stale-body";
            failure(client.submit(LibraryClient.Lane.TRANSFER,
                    exchange -> exchange.observerOutfitBody(snapshot, snapshot.resources().getFirst())).future(), "CONFLICT");
            server.mode = "revoked";
            failure(client.observerOutfit(server.owner).future(), "NOT_FOUND");
            server.mode = "normal";
            server.enabled = false;
            failure(client.observerOutfit(server.owner).future(), "UNSUPPORTED_FORMAT");
            require(server.badAuthentication == 0 && server.mutations == 0, "observer reads use the existing credentials and never mutate or acquire catalog entries");
            live.set(false);
            failure(client.observerOutfit(server.owner).future(), "LIBRARY_UNAVAILABLE");
        }
    }

    private static void set(Object target, String name, Object value) throws Exception {
        var field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }

    private static <T> T await(CompletableFuture<T> future) throws Exception {
        return future.get(10, TimeUnit.SECONDS);
    }

    private static void failure(CompletableFuture<?> future, String code) throws Exception {
        try {
            await(future);
            throw new AssertionError("Expected " + code);
        } catch (ExecutionException expected) {
            require(expected.getCause() instanceof LibraryClient.Failure failure && failure.code.equals(code),
                    "expected " + code + " but received " + expected.getCause());
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    private static final class Server implements AutoCloseable {
        private final HttpServer http;
        private final boolean protobuf;
        private final UUID owner = new UUID(0, 2);
        private final byte[] body = "observer-body".repeat(6000).getBytes(StandardCharsets.UTF_8);
        private volatile String mode = "normal";
        private volatile boolean enabled = true;
        private int badAuthentication;
        private int mutations;

        private Server(boolean protobuf) throws Exception {
            this.protobuf = protobuf;
            http = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            http.createContext("/", this::handle);
            http.start();
        }

        private String origin() {
            return "http://127.0.0.1:" + http.getAddress().getPort();
        }

        private JsonObject snapshot() {
            var reference = new OutfitConfig.ResourceRef(OutfitConfig.Source.LOCAL, new UUID(0, 3), null, null,
                    null, "0".repeat(64), OutfitConfig.Format.FIGURA);
            var configuration = new OutfitConfig(1, 7, reference, List.of(), List.of(), Map.of(), false);
            var snapshot = new JsonObject();
            snapshot.addProperty("schema_version", 1);
            snapshot.addProperty("owner", owner.toString());
            snapshot.addProperty("revision", 7);
            snapshot.addProperty("equipment_revision", 9);
            snapshot.add("configuration", JsonParser.parseString(OutfitCodec.encode(configuration)));
            var source = new JsonObject();
            source.addProperty("id", "1".repeat(64));
            source.add("reference", snapshot.getAsJsonObject("configuration").getAsJsonObject("base").deepCopy());
            source.addProperty("resource_kind", "AVATAR");
            source.add("accessory_metadata", JsonNull.INSTANCE);
            source.addProperty("distribution_format", 1);
            source.addProperty("byte_length", body.length);
            source.addProperty("raw_sha256", CloudHttp.sha256(body));
            source.addProperty("content_sha256", "0".repeat(64));
            source.addProperty("schema_sha256", "0".repeat(64));
            source.addProperty("relative_path", OutfitObserverSnapshot.resourcePath(owner, 7, "1".repeat(64)));
            var sources = new com.google.gson.JsonArray();
            sources.add(source);
            snapshot.add("resources", sources);
            switch (mode) {
                case "wrong-owner" -> snapshot.addProperty("owner", new UUID(0, 8).toString());
                case "wrong-revision" -> snapshot.addProperty("revision", 8);
                case "string-revision" -> snapshot.addProperty("revision", "7");
                case "string-config-revision" -> snapshot.getAsJsonObject("configuration").addProperty("revision", "7");
                case "missing-source" -> sources.remove(0);
                case "duplicate-source" -> sources.add(source.deepCopy());
                case "extra-source" -> {
                    var extra = source.deepCopy();
                    extra.addProperty("id", "2".repeat(64));
                    sources.add(extra);
                }
                case "foreign-path" -> source.addProperty("relative_path", "https://example.invalid/source");
                case "wrong-format" -> source.addProperty("distribution_format", 2);
                case "large-valid" -> snapshot.addProperty("padding", "x".repeat(70000));
                case "oversized" -> snapshot.addProperty("padding", "x".repeat(OutfitObserverSnapshot.JSON_BYTES));
                case "hidden", "legacy" -> {
                    snapshot.add("configuration", JsonNull.INSTANCE);
                    sources.remove(0);
                    if (mode.equals("legacy")) {
                        snapshot.addProperty("revision", 0);
                    }
                }
                default -> { }
            }
            return snapshot;
        }

        private void handle(HttpExchange exchange) throws java.io.IOException {
            try (exchange) {
                var headers = exchange.getRequestHeaders();
                boolean authorized = origin().equals(headers.getFirst("x-omnimorph-origin")) && (protobuf
                        ? "Bearer scoped".equals(headers.getFirst("authorization")) && "77".equals(headers.getFirst("x-omnimorph-epoch"))
                        : "Bearer original".equals(headers.getFirst("authorization")) && "figura".equals(headers.getFirst("x-omnimorph-auth-mode")));
                if (!authorized) {
                    badAuthentication++;
                    send(exchange, 401, "{\"code\":\"UNAUTHENTICATED\"}".getBytes(StandardCharsets.UTF_8), "application/json");
                    return;
                }
                if (!exchange.getRequestMethod().equals("GET")) {
                    mutations++;
                }
                String path = exchange.getRequestURI().getPath();
                if (path.endsWith("/capabilities")) {
                    var limits = OutfitLimits.defaults();
                    var outfits = new LibraryModels.OutfitCapabilities(1, true, true, enabled, false,
                            limits.maxInstances(), limits.maxVirtualAnchors(), limits.maxDepth(), limits.maxResourceNodes(),
                            limits.maxTotalNodes(), limits.maxBoneOverrides(), limits.maxJsonBytes(), limits.maxTrails(), limits.maxTrailSamples(), limits);
                    var capabilities = new LibraryModels.Capabilities(true, 1, 67108864, 8, 2097152, 2048, 2048,
                            List.of("zip", "ysm"), List.of("FIGURA", "YSM_PLAIN", "YSM_ENCRYPTED"), List.of("general"), false, outfits);
                    send(exchange, 200, JSON.toJson(capabilities).getBytes(StandardCharsets.UTF_8), "application/json");
                } else if (mode.equals("revoked") || mode.equals("stale-body") && path.contains("/resources/")) {
                    send(exchange, mode.equals("revoked") ? 404 : 409,
                            (mode.equals("revoked") ? "{\"code\":\"NOT_FOUND\"}" : "{\"code\":\"CONFLICT\"}")
                                    .getBytes(StandardCharsets.UTF_8), "application/json");
                } else if (path.contains("/resources/")) {
                    require("revision=7".equals(exchange.getRequestURI().getQuery()), "body URL retains the exact revision");
                    exchange.getResponseHeaders().add("x-omnimorph-sha256", CloudHttp.sha256(body));
                    byte[] content = body.clone();
                    if (mode.equals("bad-body-hash")) {
                        content[0] ^= 1;
                    }
                    send(exchange, 200, content, "application/octet-stream");
                } else {
                    byte[] content = snapshot().toString().getBytes(StandardCharsets.UTF_8);
                    if (mode.equals("bad-utf8")) {
                        content[0] = (byte) 0xff;
                    }
                    send(exchange, 200, content, mode.equals("bad-media") ? "text/html" : "application/json");
                }
            }
        }

        private static void send(HttpExchange exchange, int status, byte[] bytes, String media) throws java.io.IOException {
            exchange.getResponseHeaders().add("content-type", media);
            exchange.sendResponseHeaders(status, bytes.length);
            exchange.getResponseBody().write(bytes);
        }

        @Override
        public void close() {
            http.stop(0);
        }
    }
}
