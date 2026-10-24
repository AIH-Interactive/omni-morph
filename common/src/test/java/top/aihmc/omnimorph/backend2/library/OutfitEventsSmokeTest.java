package top.aihmc.omnimorph.backend2.library;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import top.aihmc.omnimorph.backend2.protocol.CloudEndpoint;
import top.aihmc.omnimorph.backend2.protocol.CloudProtocol;
import top.aihmc.omnimorph.backend2.protocol.CloudSocket;
import top.aihmc.omnimorph.backend2.protocol.FiguraCloudProtocol;
import top.aihmc.omnimorph.backend2.protocol.LibraryAccess;
import top.aihmc.omnimorph.backend2.protocol.ProtobufCloudProtocol;
import top.aihmc.omnimorph.model.outfit.OutfitLimits;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.WebSocket;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/** CPU wire checks, with an optional production Java adapter -> actual Rust WebSocket gate. */
public final class OutfitEventsSmokeTest {
    private static final UUID OWNER = new UUID(0, 1);
    private static final UUID VIEWER = new UUID(0, 2);

    public static void main(String[] args) throws Exception {
        framing();
        parsing();
        if (args.length != 0) {
            integration(args[0], args[1], args.length > 2 ? UUID.fromString(args[2]) : null);
        }
        System.out.println("Outfit event framing/parser checks PASS; real Rust gate runs only with listener arguments");
    }

    private static void framing() throws Exception {
        var received = new ArrayList<String>();
        var sent = new ArrayList<String>();
        var failures = new AtomicInteger();
        var aborted = new AtomicBoolean();
        var socket = (WebSocket) Proxy.newProxyInstance(WebSocket.class.getClassLoader(),
                new Class<?>[] {WebSocket.class}, (proxy, method, arguments) -> switch (method.getName()) {
                    case "request" -> null;
                    case "abort" -> {
                        aborted.set(true);
                        yield null;
                    }
                    case "sendText" -> {
                        sent.add(arguments[0].toString());
                        yield CompletableFuture.completedFuture(proxy);
                    }
                    default -> throw new UnsupportedOperationException(method.getName());
                });
        try (var transport = new CloudSocket(16, bytes -> received.add(new String(bytes, StandardCharsets.UTF_8)),
                reason -> failures.incrementAndGet(), null, true)) {
            transport.attach(socket);
            transport.onText(socket, "测", false);
            transport.onText(socket, "试", true);
            require(received.equals(List.of("测试")), "Fragmented UTF-8 text is reconstructed once");
            transport.sendText("测试");
            require(sent.equals(List.of("测试")), "Text writes stay text");
            transport.onText(socket, "测".repeat(6), true);
            require(failures.get() == 1 && aborted.get(), "UTF-8 bytes, rather than character count, bound messages");
        }
        try (var transport = new CloudSocket(16, bytes -> {}, reason -> failures.incrementAndGet(), null, true)) {
            transport.attach(socket);
            transport.onBinary(socket, ByteBuffer.wrap(new byte[] {1}), true);
            require(failures.get() == 2, "Outfit text channel rejects legacy binary frames");
        }
        var current = new AtomicBoolean(true);
        try (var http = HttpClient.newHttpClient();
             var access = new LibraryAccess(CloudEndpoint.parse("https://example.invalid", () -> false, current::get),
                     http, request -> current.set(false))) {
            for (int attempt = 0; attempt < 2; attempt++) {
                current.set(true);
                try {
                    access.openOutfitEvents(bytes -> {}, reason -> {}, () -> {});
                    throw new AssertionError("Retired handshake accepted");
                } catch (java.io.IOException expected) {
                    require(expected.getMessage().equals("backend.endpoint.retired"),
                            "Rejected opening releases its admission slot instead of leaving LIBRARY_BUSY");
                }
            }
        }
    }

    private static JsonObject frame(String type, long sequence) {
        return JsonParser.parseString("{\"schema_version\":1,\"connection_epoch\":77,"
                + "\"server_epoch\":\"18446744073709551615\",\"sequence\":" + sequence
                + ",\"type\":\"" + type + "\"}").getAsJsonObject();
    }

    private static void parsing() throws Exception {
        try (var http = HttpClient.newHttpClient();
             var access = new LibraryAccess(CloudEndpoint.parse("https://example.invalid", () -> false), http,
                     request -> {})) {
            var probe = new EventProbe();
            var session = new LibrarySession("fixture-instance", VIEWER, 77, () -> true);
            var service = new OutfitEvents(access, session, probe);
            var helloSent = OutfitEvents.class.getDeclaredField("helloSent");
            helloSent.setAccessible(true);
            helloSent.set(service, true);
            var receive = OutfitEvents.class.getDeclaredMethod("receive", byte[].class, Set.class, long.class);
            receive.setAccessible(true);
            JsonObject hello = frame("HELLO_ACK", 1);
            hello.addProperty("instance_id", "fixture-instance");
            receive.invoke(service, hello.toString().getBytes(StandardCharsets.UTF_8), Set.of(OWNER), 1000L);
            require(service.ready() && probe.resets == 1, "Matching HELLO resets snapshot baseline, including unsigned server epoch");
            JsonObject changed = frame("CHANGED", 2);
            changed.addProperty("owner", OWNER.toString());
            changed.addProperty("revision", 3);
            changed.addProperty("equipment_revision", 2);
            receive.invoke(service, changed.toString().getBytes(StandardCharsets.UTF_8), Set.of(OWNER), 1001L);
            changed.addProperty("sequence", 3);
            receive.invoke(service, changed.toString().getBytes(StandardCharsets.UTF_8), Set.of(OWNER), 1002L);
            require(probe.owners.size() == 2, "Same revision policy invalidations are never suppressed");
            for (String mutation : List.of("gap", "epoch", "schema", "private", "number", "instance")) {
                JsonObject invalid = changed.deepCopy();
                invalid.addProperty("sequence", 4);
                switch (mutation) {
                    case "gap" -> invalid.addProperty("sequence", 5);
                    case "epoch" -> invalid.addProperty("server_epoch", "1");
                    case "schema" -> invalid.addProperty("schema_version", 2);
                    case "private" -> invalid.addProperty("resource_name", "private");
                    case "number" -> invalid.addProperty("revision", 1.5);
                    case "instance" -> {
                        invalid = frame("HELLO_ACK", 4);
                        invalid.addProperty("instance_id", "another-instance");
                    }
                    default -> throw new AssertionError();
                }
                try {
                    receive.invoke(service, invalid.toString().getBytes(StandardCharsets.UTF_8), Set.of(OWNER), 1003L);
                    throw new AssertionError("Accepted invalid event: " + mutation);
                } catch (InvocationTargetException expected) {
                    require(expected.getCause() instanceof java.io.IOException, "Typed invalidation failure");
                }
            }
            service.close();
            require(!service.ready(), "Closed event scope is not ready");
        }
    }

    private static void integration(String origin, String instance, UUID stateInstance) throws Exception {
        try (var http = HttpClient.newHttpClient()) {
            for (boolean protobuf : new boolean[] {false, true}) {
                var endpoint = CloudEndpoint.parse(origin, () -> true);
                try (CloudProtocol protocol = protobuf
                        ? new ProtobufCloudProtocol(endpoint, "synthetic-token-2", "outfit-event-test", 77, http)
                        : new FiguraCloudProtocol(endpoint, "synthetic-token-2", http)) {
                    var callbacks = new ConcurrentLinkedQueue<Runnable>();
                    protocol.connect(new ProtocolProbe());
                    long deadline = System.nanoTime() + 10_000_000_000L;
                    while (!protocol.connected() && System.nanoTime() < deadline) {
                        protocol.tick(System.nanoTime() / 50_000_000L);
                        Thread.sleep(5);
                    }
                    require(protocol.connected(), "Original adapter handshake completes");
                    require(protocol.instance().equals(protobuf ? origin + "/" + instance : "figura:" + origin),
                            "Actual protocol identity keeps its existing origin namespace");
                    try (var client = new LibraryClient(protocol.libraryAccess(),
                            new LibrarySession(protocol.instance(), VIEWER, 77, protocol::connected), callbacks::add)) {
                        var probe = new EventProbe();
                        OutfitEvents events = client.outfitEvents(probe);
                        // Internal channel gate: production events remains false until all stage-five requirements pass.
                        var limits = OutfitLimits.defaults();
                        var capabilities = new LibraryModels.Capabilities(true, 1, 1_048_576, 64, 1_048_576, 1024, 1024,
                                List.of("FIGURA"), List.of(), List.of(), false,
                                new LibraryModels.OutfitCapabilities(1, true, true, false, true,
                                        limits.maxInstances(), limits.maxVirtualAnchors(), limits.maxDepth(),
                                        limits.maxResourceNodes(), limits.maxTotalNodes(), limits.maxBoneOverrides(),
                                        limits.maxJsonBytes(), limits.maxTrails(), limits.maxTrailSamples(), limits));
                        var pump = new Pump(protocol, events, capabilities, callbacks);
                        pump.until(() -> events.ready() && new java.util.HashSet<>(probe.owners).size() == 128);
                        if (stateInstance != null) {
                            stateRoundtrip(origin, http, client, callbacks, probe, pump, stateInstance);
                        }
                        probe.owners.clear();
                        var input = HttpRequest.newBuilder(java.net.URI.create(origin + "/api/library/v1/outfits/current"))
                                .header("Authorization", "Bearer synthetic-token-1")
                                .header("x-omnimorph-auth-mode", "figura")
                                .header("x-omnimorph-origin", origin).GET().build();
                        String configuration = http.send(input, HttpResponse.BodyHandlers.ofString()).body();
                        var current = JsonParser.parseString(configuration).getAsJsonObject();
                        var body = new JsonObject();
                        body.addProperty("operation_id", UUID.randomUUID().toString());
                        body.addProperty("expected_revision", current.get("revision").getAsLong());
                        body.add("configuration", current);
                        var applied = http.send(HttpRequest.newBuilder(input.uri())
                                .headers("Authorization", "Bearer synthetic-token-1", "x-omnimorph-auth-mode", "figura",
                                        "x-omnimorph-origin", origin, "Content-Type", "application/json")
                                .PUT(HttpRequest.BodyPublishers.ofString(body.toString())).build(), HttpResponse.BodyHandlers.ofString());
                        require(applied.statusCode() == 200, "Real HTTP commit succeeds: " + applied.body());
                        pump.until(() -> probe.owners.contains(OWNER));
                        authenticated(protocol);
                        // Abort the actual event socket, then drive the real timeout/backoff/reconnect path.
                        var connection = OutfitEvents.class.getDeclaredField("connection");
                        connection.setAccessible(true);
                        ((LibraryAccess.OutfitConnection) connection.get(events)).close();
                        pump.offset += 60_001;
                        pump.once();
                        require(!events.ready(), "Silent transport loss retires old baseline");
                        probe.owners.clear();
                        pump.offset += 30_001;
                        pump.until(() -> events.ready() && new java.util.HashSet<>(probe.owners).size() == 128);
                        require(probe.resets >= 3, "Reconnect requires fresh baseline instead of replaying old events");
                        client.close();
                        require(!events.ready() && protocol.connected(), "Library retirement closes event channel only");
                        authenticated(protocol);
                    }
                }
            }
        }
        System.out.println("Production Figura/Protobuf -> Rust outfit event handshake/commit/reconnect PASS");
    }

    private static void authenticated(CloudProtocol protocol) throws Exception {
        try (var call = protocol.libraryAccess().open()) {
            var response = call.send("GET", "/api/library/v1/session", HttpRequest.BodyPublishers.noBody(), null, null);
            try (var body = response.body()) {
                require(response.statusCode() == 200, "Independent event channel preserves original authenticated access");
            }
        }
    }

    private static void stateRoundtrip(String origin, HttpClient http, LibraryClient observer,
                                       ConcurrentLinkedQueue<Runnable> callbacks, EventProbe probe,
                                       Pump pump, UUID instance) throws Exception {
        var endpoint = CloudEndpoint.parse(origin, () -> true);
        try (var access = new LibraryAccess(endpoint, http, request -> request
                .header("authorization", "Bearer synthetic-token-1").header("x-omnimorph-auth-mode", "figura"));
             var owner = new LibraryClient(access, new LibrarySession("figura:" + origin, OWNER, 77, () -> true), callbacks::add)) {
            var read = observer.instanceState(OWNER, instance);
            pump.until(read.future()::isDone);
            var baseline = read.future().join();
            var value = new OutfitInstanceState(baseline.outfitRevision(), instance, baseline.resource(), baseline.revision(),
                    new OutfitInstanceState.State(UUID.randomUUID(), 1, java.util.Map.of("enabled", true),
                            java.util.Map.of("mode", "ribbon"), null, "", ""));
            int before = probe.states;
            var write = owner.publishInstanceState(value);
            pump.until(() -> write.future().isDone() && probe.states > before);
            var accepted = write.future().join();
            require(accepted.revision() == baseline.revision() + 1 && accepted.state().equals(value.state()),
                    "Java owner state CAS crosses the real Rust JSON/store boundary");
            var observed = observer.instanceState(OWNER, instance);
            pump.until(observed.future()::isDone);
            require(observed.future().join().equals(accepted), "Original adapter observer reads the committed baseline after STATE_CHANGED");
            var denied = observer.publishInstanceState(accepted);
            pump.until(denied.future()::isDone);
            try {
                denied.future().join();
                throw new AssertionError("Observer published owner state");
            } catch (java.util.concurrent.CompletionException expected) {
                require(expected.getCause() instanceof LibraryClient.Failure failure
                                && (failure.status == 403 || failure.status == 404),
                        "Real backend rejects observer writes");
            }
        }
    }

    private static final class EventProbe implements OutfitEvents.Listener {
        private final List<UUID> owners = new ArrayList<>();
        private int resets;
        private int states;

        @Override public void changed(UUID owner) { owners.add(owner); }
        @Override public void reset() { resets++; }
        @Override public void limitsChanged() { }
        @Override public void stateChanged(UUID owner, UUID instance, long outfitRevision, long stateRevision) { states++; }
    }

    private static final class ProtocolProbe implements CloudProtocol.Listener {
        @Override public void ready() { }
        @Override public void ping(UUID owner, int functionId, boolean echo, byte[] arguments) { }
        @Override public void changed(UUID owner) { }
        @Override public void ysm(top.aihmc.omnimorph.model.ysm.sync.YsmSyncMessage message) { }
        @Override public void snapshotRequested() { }
        @Override public void closed(String reason) { }
    }

    private static final class Pump {
        private final CloudProtocol protocol;
        private final OutfitEvents events;
        private final LibraryModels.Capabilities capabilities;
        private final ConcurrentLinkedQueue<Runnable> callbacks;
        private final Set<UUID> owners = IntStream.rangeClosed(1, 128)
                .mapToObj(value -> new UUID(0, value)).collect(Collectors.toUnmodifiableSet());
        private long offset;

        private Pump(CloudProtocol protocol, OutfitEvents events, LibraryModels.Capabilities capabilities,
                     ConcurrentLinkedQueue<Runnable> callbacks) {
            this.protocol = protocol;
            this.events = events;
            this.capabilities = capabilities;
            this.callbacks = callbacks;
        }

        private void once() throws Exception {
            protocol.tick(System.nanoTime() / 50_000_000L);
            Runnable callback;
            while ((callback = callbacks.poll()) != null) {
                callback.run();
            }
            events.tick(System.nanoTime() / 1_000_000L + offset, capabilities, owners);
        }

        private void until(BooleanSupplier condition) throws Exception {
            long deadline = System.nanoTime() + 10_000_000_000L;
            while (!condition.getAsBoolean() && System.nanoTime() < deadline) {
                once();
                Thread.sleep(5);
            }
            require(condition.getAsBoolean(), "Timed out waiting for actual outfit event flow");
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
