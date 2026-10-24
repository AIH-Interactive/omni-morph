package top.aihmc.omnimorph.backend2.library;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import top.aihmc.omnimorph.backend2.protocol.LibraryAccess;

import java.io.IOException;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/** Client-thread revision invalidations; authoritative data always comes from HTTP snapshots. */
public final class OutfitEvents implements AutoCloseable {
    public interface Listener {
        void changed(UUID owner);
        default void changed(UUID owner, long revision, long equipmentRevision) {
            changed(owner);
        }
        void reset();
        void limitsChanged();
        default void stateChanged(UUID owner, UUID instance, long outfitRevision, long stateRevision) { }
    }

    private final LibraryAccess access;
    private final LibrarySession session;
    private final Listener listener;
    private final ArrayDeque<byte[]> incoming = new ArrayDeque<>();
    private final Set<UUID> subscribed = new HashSet<>();
    private LibraryAccess.OutfitConnection connection;
    private boolean transportReady;
    private boolean transportFailed;
    private boolean helloSent;
    private boolean ready;
    private boolean closed;
    private int incomingBytes;
    private int failures;
    private long attempt;
    private long retryAt;
    private long lastReceived;
    private long lastHeartbeat;
    private long sequence;
    private String serverEpoch;

    OutfitEvents(LibraryAccess access, LibrarySession session, Listener listener) {
        this.access = access;
        this.session = session;
        this.listener = listener;
    }

    public synchronized boolean ready() {
        return !closed && ready && session.current();
    }

    /** Called from the client tick with its current negotiated capabilities and visible-player set. */
    public synchronized void tick(long nowMillis, LibraryModels.Capabilities capabilities, Set<UUID> owners) {
        if (closed) {
            return;
        }
        if (!session.current()) {
            close();
            return;
        }
        var outfits = capabilities == null ? null : capabilities.outfitsV1();
        if (outfits == null || !capabilities.enabled() || !outfits.configuration() || !outfits.events()) {
            retire();
            return;
        }
        if (owners.size() > 128 || session.epoch() <= 0) {
            throw new IllegalArgumentException("INVALID_OUTFIT_SUBSCRIPTIONS");
        }
        try {
            if (transportFailed) {
                recover(nowMillis);
            }
            if (connection == null) {
                if (nowMillis < retryAt) {
                    return;
                }
                open(nowMillis);
            }
            if (transportReady && !helloSent) {
                send("HELLO", Set.of());
                helloSent = true;
            }
            int delivered = 0;
            while (!incoming.isEmpty() && delivered++ < 32) {
                byte[] bytes = incoming.removeFirst();
                incomingBytes -= bytes.length;
                receive(bytes, owners, nowMillis);
            }
            if (nowMillis - lastReceived >= (ready ? 60_000 : 15_000)) {
                throw new IOException("OUTFIT_EVENTS_TIMEOUT");
            }
            if (ready) {
                Set<UUID> removed = new HashSet<>(subscribed);
                removed.removeAll(owners);
                if (!removed.isEmpty()) {
                    send("UNSUBSCRIBE", removed);
                    subscribed.removeAll(removed);
                }
                Set<UUID> added = new HashSet<>(owners);
                added.removeAll(subscribed);
                if (!added.isEmpty()) {
                    send("SUBSCRIBE", added);
                    subscribed.addAll(added);
                }
                if (nowMillis - lastHeartbeat >= 20_000) {
                    send("HEARTBEAT", Set.of());
                    lastHeartbeat = nowMillis;
                }
            }
        } catch (Exception invalid) {
            recover(nowMillis);
        }
    }

    private void open(long nowMillis) throws IOException {
        long selected = ++attempt;
        lastReceived = nowMillis;
        lastHeartbeat = nowMillis;
        connection = access.openOutfitEvents(bytes -> enqueue(selected, bytes),
                reason -> failed(selected), () -> opened(selected));
    }

    private synchronized void opened(long selected) {
        if (!closed && selected == attempt) {
            transportReady = true;
        }
    }

    private synchronized void failed(long selected) {
        if (!closed && selected == attempt) {
            transportFailed = true;
        }
    }

    private synchronized void enqueue(long selected, byte[] bytes) {
        if (closed || selected != attempt) {
            return;
        }
        // One ACK + a complete 128-owner initial baseline can arrive between client ticks.
        if (bytes.length > 16_384 || incoming.size() >= 132 || bytes.length > 262_144 - incomingBytes) {
            transportFailed = true;
            return;
        }
        incoming.addLast(bytes.clone());
        incomingBytes += bytes.length;
    }

    private void receive(byte[] bytes, Set<UUID> owners, long nowMillis) throws Exception {
        JsonObject body = JsonParser.parseString(StandardCharsets.UTF_8.newDecoder()
                .decode(ByteBuffer.wrap(bytes)).toString()).getAsJsonObject();
        String type = string(body, "type");
        Set<String> fields = new HashSet<>(Set.of("schema_version", "connection_epoch", "server_epoch",
                "sequence", "type"));
        fields.addAll(switch (type) {
            case "HELLO_ACK" -> Set.of("instance_id");
            case "ACK" -> Set.of("command");
            case "CHANGED" -> Set.of("owner", "revision", "equipment_revision");
            case "STATE_CHANGED" -> Set.of("owner", "instance_id", "outfit_revision", "state_revision");
            case "ERROR", "RESYNC_REQUIRED" -> Set.of("code");
            case "HEARTBEAT", "LIMITS_CHANGED" -> Set.of();
            default -> throw new IOException("INVALID_OUTFIT_EVENT");
        });
        if (!body.keySet().equals(fields) || number(body, "schema_version") != 1
                || number(body, "connection_epoch") != session.epoch()
                || number(body, "sequence") != sequence + 1) {
            throw new IOException("OUTFIT_EVENTS_GAP");
        }
        String epoch = string(body, "server_epoch");
        if (!epoch.matches("0|[1-9][0-9]{0,19}") || new BigInteger(epoch).bitLength() > 64
                || serverEpoch != null && !serverEpoch.equals(epoch)) {
            throw new IOException("OUTFIT_EVENTS_EPOCH");
        }
        if (!ready) {
            String instance = type.equals("HELLO_ACK") ? string(body, "instance_id") : "";
            boolean figuraNamespace = session.instance().equals("figura:" + access.origin());
            boolean protobufNamespace = session.instance().equals(access.origin() + "/" + instance);
            if (!type.equals("HELLO_ACK") || !helloSent
                    || instance.isBlank() || instance.length() > 128
                    || !figuraNamespace && !protobufNamespace && !instance.equals(session.instance())) {
                throw new IOException("INVALID_OUTFIT_HELLO");
            }
            ready = true;
            serverEpoch = epoch;
            failures = 0;
            listener.reset();
        } else {
            switch (type) {
                case "CHANGED" -> {
                    String ownerText = string(body, "owner");
                    UUID owner = UUID.fromString(ownerText);
                    if (!owner.toString().equals(ownerText)) {
                        throw new IOException("INVALID_OUTFIT_OWNER");
                    }
                    long revision = number(body, "revision");
                    long equipmentRevision = number(body, "equipment_revision");
                    if (owners.contains(owner) || session.player().equals(owner)) {
                        listener.changed(owner, revision, equipmentRevision);
                    }
                }
                case "ACK" -> {
                    if (!Set.of("SUBSCRIBE", "UNSUBSCRIBE").contains(string(body, "command"))) {
                        throw new IOException("INVALID_OUTFIT_ACK");
                    }
                }
                case "STATE_CHANGED" -> {
                    String ownerText = string(body, "owner");
                    String instanceText = string(body, "instance_id");
                    UUID owner = UUID.fromString(ownerText);
                    UUID instance = UUID.fromString(instanceText);
                    if (!owner.toString().equals(ownerText) || !instance.toString().equals(instanceText)) {
                        throw new IOException("INVALID_OUTFIT_INSTANCE");
                    }
                    long outfitRevision = number(body, "outfit_revision");
                    long stateRevision = number(body, "state_revision");
                    if (owners.contains(owner) || session.player().equals(owner)) {
                        listener.stateChanged(owner, instance, outfitRevision, stateRevision);
                    }
                }
                case "HEARTBEAT" -> { }
                case "LIMITS_CHANGED" -> listener.limitsChanged();
                default -> throw new IOException("OUTFIT_EVENTS_RECOVERY");
            }
        }
        sequence++;
        lastReceived = nowMillis;
    }

    private static String string(JsonObject body, String key) throws IOException {
        var value = body.get(key);
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()) {
            throw new IOException("INVALID_OUTFIT_EVENT");
        }
        return value.getAsString();
    }

    private static long number(JsonObject body, String key) throws IOException {
        var value = body.get(key);
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber()
                || !value.getAsString().matches("0|[1-9][0-9]{0,18}")) {
            throw new IOException("INVALID_OUTFIT_EVENT");
        }
        try {
            return Long.parseLong(value.getAsString());
        } catch (NumberFormatException invalid) {
            throw new IOException("INVALID_OUTFIT_EVENT");
        }
    }

    private void send(String type, Set<UUID> owners) throws IOException {
        session.requireCurrent();
        var body = new JsonObject();
        body.addProperty("schema_version", 1);
        body.addProperty("connection_epoch", session.epoch());
        body.addProperty("type", type);
        var values = new com.google.gson.JsonArray();
        owners.stream().sorted().forEach(owner -> values.add(owner.toString()));
        body.add("owners", values);
        connection.send(body.toString());
    }

    private void recover(long nowMillis) {
        boolean hadConnection = connection != null;
        retire();
        retryAt = nowMillis + Math.min(30_000, 1_000L << Math.min(failures++, 5));
        if (hadConnection) {
            listener.reset();
        }
    }

    private void retire() {
        ++attempt;
        if (connection != null) {
            connection.close();
            connection = null;
        }
        ready = false;
        helloSent = false;
        transportReady = false;
        transportFailed = false;
        subscribed.clear();
        incoming.clear();
        incomingBytes = 0;
        sequence = 0;
        serverEpoch = null;
    }

    @Override
    public synchronized void close() {
        closed = true;
        retire();
    }
}
