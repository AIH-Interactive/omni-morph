package org.figuramc.figura.backend2.protocol;

import com.google.gson.JsonParser;
import org.figuramc.figura.model.ysm.sync.YsmSyncMessage;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;

/** Invoked by the real Rust loopback server harness with isolated synthetic accounts/resources. */
public final class CloudAdapterIntegrationTest {
    private static final byte[] PING = {0, -1, -128, 127};

    public static void main(String[] args) throws Exception {
        if (args[0].equals("--legacy")) {
            legacyBackend(args);
            return;
        }
        URI origin = URI.create(args[0]);
        Path fixtures = Path.of(args[1]);
        UUID owner = UUID.fromString(args[2]);
        UUID observer = UUID.fromString(args[3]);
        HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
        for (int direction = 0; direction < 4; direction++) {
            boolean sourceProtobuf = (direction & 1) != 0;
            boolean targetProtobuf = (direction & 2) != 0;
            try (CloudProtocol source = adapter(sourceProtobuf, origin, "java-source", 100 + direction, http);
                 CloudProtocol target = adapter(targetProtobuf, origin, "java-observer", 200 + direction, http)) {
                Probe sent = new Probe();
                Probe received = new Probe();
                source.connect(sent);
                target.connect(received);
                try (Pump pump = new Pump(source, target)) {
                    sent.awaitReady();
                    received.awaitReady();
                    require(source.checkAuth().status() == 200 && target.checkAuth().status() == 200, "Shared authenticated scope");
                    // Consume the existing legacy self-profile temporary-slot probe, without changing its semantics.
                    source.profile(owner);
                    source.profile(owner);
                    require(source.version().status() == 200 && source.limits().status() == 200 && source.motd().status() == 200,
                            "Version/limits/MOTD operations");
                    target.subscribe(owner);
                    source.subscribe(observer);
                    for (int model = 1; model <= 3; model++) {
                        String name = model == 1 ? "figura" : model == 2 ? "plain" : "encrypted";
                        byte[] bytes = Files.readAllBytes(fixtures.resolve(name + ".bin"));
                        String content = model == 1 ? "" : Files.readString(fixtures.resolve(name + ".sha256")).trim();
                        String schema = "22".repeat(32);
                        var metadata = model == 1 ? CloudProtocol.Metadata.figura() : new CloudProtocol.Metadata(model, content, schema);
                        require(source.upload("avatar", bytes, metadata).status() == 200, "Upload " + name);
                        require(source.equip(List.of(owner), List.of("avatar")).status() == 200, "Equip " + name);
                        CloudProtocol.Reply profile = target.profile(owner);
                        var selected = JsonParser.parseString(profile.body()).getAsJsonObject().getAsJsonArray("equipped");
                        require(selected.size() == 1, "Extended profile selects primary " + name);
                        String key = selected.get(0).getAsJsonObject().get("hash").getAsString();
                        try (var download = target.download(owner, "avatar")) {
                            require(download.resource().cacheKey().equals(key), "Profile/cache namespace matches download");
                            require(Arrays.equals(bytes, CloudHttp.readBounded(download.stream(), 1048576)), "Raw HTTP bytes " + name);
                        }
                        source.ping(0x01020304, true, PING);
                        target.ping(0x10203040, false, PING);
                        received.awaitPing(owner, 0x01020304);
                        sent.awaitPing(owner, 0x01020304);
                        sent.awaitPing(observer, 0x10203040);
                        if (model != 1) {
                            received.states.clear();
                            UUID publisher = UUID.randomUUID();
                            var state = new YsmSyncMessage.State(0, List.of(), List.of(), List.of(), List.of());
                            var baseline = message(owner, publisher, content, schema, 0, -1, true, 0, state, List.of());
                            require(source.publish(baseline), "Publish snapshot");
                            YsmSyncMessage snapshot = received.awaitState(true, false);
                            require(snapshot.owner().equals(owner) && snapshot.contentHash().equals(content)
                                    && snapshot.schemaHash().equals(schema) && snapshot.state().equals(state), "Mixed typed snapshot fields");
                            var event = new YsmSyncMessage.Event(1, YsmSyncMessage.EventKind.SYNC, List.of(0.5f, -1.25f));
                            var delta = message(owner, publisher, content, schema, 1, 0, false, 1,
                                    new YsmSyncMessage.State(1, List.of(), null, null, null), List.of(event));
                            require(source.publish(delta), "Publish state plus short event");
                            YsmSyncMessage changed = received.awaitState(false, false);
                            require(changed.state().texture() == 1 && changed.state().controls().isEmpty()
                                    && changed.state().actions() == null, "Delta presence across adapters");
                            require(received.awaitState(false, true).events().equals(List.of(event)), "One-shot arrives once");
                            require(source.publish(delta), "Exact source retry admitted");
                            require(received.states.poll(250, TimeUnit.MILLISECONDS) == null, "Duplicate source does not replay state/event");
                            // A periodic full snapshot with a fresh action must not lose that live action.
                            var secondEvent = new YsmSyncMessage.Event(2, YsmSyncMessage.EventKind.ACTION, List.of(0f));
                            require(source.publish(message(owner, publisher, content, schema, 2, -1, true, 2,
                                    state, List.of(secondEvent))), "Periodic snapshot publication");
                            require(received.awaitState(true, false).eventWatermark() == 1, "Periodic baseline retains prior watermark");
                            require(received.awaitState(false, true).events().equals(List.of(secondEvent)), "Periodic snapshot live event preserved");
                            // Re-subscription obtains a materialized baseline without historical instantaneous effects.
                            target.unsubscribe(owner);
                            target.subscribe(owner);
                            YsmSyncMessage recovered = received.awaitState(true, false);
                            require(recovered.eventWatermark() == 2 && recovered.events().isEmpty(), "Late baseline skips historical events");
                        }
                        sent.requireHealthy();
                        received.requireHealthy();
                        require(source.delete("avatar").status() == 200, "Delete " + name);
                        require(JsonParser.parseString(target.profile(owner).body()).getAsJsonObject()
                                .getAsJsonArray("equipped").isEmpty(), "Deleted primary absent " + name);
                        System.out.println("PASS production client " + (sourceProtobuf ? "Protobuf" : "Figura Cloud")
                                + " -> " + (targetProtobuf ? "Protobuf" : "Figura Cloud")
                                + ": " + name + " raw HTTP/profile/equip/delete/Ping" + (model == 1 ? "" : "/typed recovery/events"));
                    }
                }
            }
        }
    }

    private static void legacyBackend(String[] args) throws Exception {
        URI origin = URI.create(args[1]);
        UUID owner = UUID.fromString(args[3]);
        byte[] expected = Files.readAllBytes(Path.of(args[4]));
        try (HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
             CloudProtocol protocol = adapter(false, origin, args[2], 1, http)) {
            Probe probe = new Probe();
            protocol.connect(probe);
            try (Pump pump = new Pump(protocol)) {
                probe.awaitReady();
                require(protocol.checkAuth().status() == 200, "Current Figura Cloud authenticates to baseline backend");
                protocol.profile(owner);
                require(protocol.profile(owner).status() == 200 && protocol.limits().status() == 200,
                        "Current adapter reads legacy profile/limits");
                try (var download = protocol.download(owner, "avatar")) {
                    require(Arrays.equals(CloudHttp.readBounded(download.stream(), 1048576), expected),
                            "Baseline backend raw Figura bytes remain compatible");
                }
                protocol.ping(0x01020304, true, PING);
                probe.awaitPing(owner, 0x01020304);
                probe.requireHealthy();
                System.out.println("PASS current Figura Cloud adapter -> baseline 28fb81e backend: auth/profile/limits/raw download/Ping");
            }
        }
    }

    private static CloudProtocol adapter(boolean protobuf, URI origin, String token, long epoch, HttpClient http) {
        return protobuf ? new ProtobufCloudProtocol(CloudEndpoint.parse(origin.toString(), () -> true), token, "integration", epoch, http)
                : new FiguraCloudProtocol(CloudEndpoint.parse(origin.toString(), () -> true), token, http);
    }

    private static YsmSyncMessage message(UUID owner, UUID publisher, String content, String schema,
                                          long sequence, long base, boolean snapshot, long watermark,
                                          YsmSyncMessage.State state, List<YsmSyncMessage.Event> events) {
        return new YsmSyncMessage(owner, publisher, content, schema, 0, sequence, base,
                sequence + 100, snapshot, watermark, state, events);
    }

    private static final class Probe implements CloudProtocol.Listener {
        private final CountDownLatch ready = new CountDownLatch(1);
        private final BlockingQueue<YsmSyncMessage> states = new LinkedBlockingQueue<>();
        private final BlockingQueue<String> pings = new LinkedBlockingQueue<>();
        private volatile String failure;

        @Override
        public void ready() {
            ready.countDown();
        }

        @Override
        public void ping(UUID owner, int id, boolean echo, byte[] arguments) {
            require(Arrays.equals(arguments, PING), "Opaque Ping arguments unchanged");
            pings.add(owner + "/" + id);
        }

        @Override
        public void changed(UUID owner) {}

        @Override
        public void ysm(YsmSyncMessage message) {
            states.add(message);
        }

        @Override
        public void snapshotRequested() {}

        @Override
        public void closed(String reason) {
            failure = reason;
            ready.countDown();
        }

        private void awaitReady() throws Exception {
            require(ready.await(10, TimeUnit.SECONDS), "Socket negotiation timed out");
            requireHealthy();
        }

        private void awaitPing(UUID owner, int id) throws Exception {
            String expected = owner + "/" + id;
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
            List<String> deferred = new ArrayList<>();
            try {
                while (System.nanoTime() < deadline) {
                    String actual = pings.poll(Math.max(1, deadline - System.nanoTime()), TimeUnit.NANOSECONDS);
                    requireHealthy();
                    if (expected.equals(actual)) {
                        return;
                    }
                    if (actual != null) {
                        deferred.add(actual);
                    }
                }
                throw new AssertionError("Expected authenticated Ping: " + expected);
            } finally {
                // Independent producers have no shared arrival order; retain their other messages.
                pings.addAll(deferred);
            }
        }

        private YsmSyncMessage awaitState(boolean snapshot, boolean event) throws Exception {
            YsmSyncMessage value = states.poll(10, TimeUnit.SECONDS);
            requireHealthy();
            require(value != null && value.snapshot() == snapshot && !value.events().isEmpty() == event,
                    "Expected " + (snapshot ? "snapshot" : event ? "event" : "delta") + ": " + value);
            return value;
        }

        private void requireHealthy() {
            require(failure == null, "Adapter failed: " + failure);
        }
    }

    private static final class Pump implements AutoCloseable {
        private final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();

        private Pump(CloudProtocol... adapters) {
            AtomicLong tick = new AtomicLong();
            executor.scheduleAtFixedRate(() -> {
                long now = tick.incrementAndGet();
                for (CloudProtocol adapter : adapters) {
                    try {
                        adapter.tick(now);
                    } catch (Exception exception) {
                        throw new RuntimeException(exception);
                    }
                }
            }, 0, 20, TimeUnit.MILLISECONDS);
        }

        @Override
        public void close() throws InterruptedException {
            executor.shutdownNow();
            require(executor.awaitTermination(5, TimeUnit.SECONDS), "Adapter pump stopped");
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
