package top.aihmc.omnimorph.backend2.protocol;

import com.google.gson.*;
import top.aihmc.omnimorph.model.ysm.sync.YsmSyncCodec;
import top.aihmc.omnimorph.model.ysm.sync.YsmSyncMessage;
import top.aihmc.omnimorph.model.ysm.sync.YsmSyncPackets;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/** Existing Figura HTTP and Ping/M7 wire contract, with explicit YSM capability. */
public final class FiguraCloudProtocol implements CloudProtocol {
    private final URI origin;
    private final CloudEndpoint endpoint;
    private volatile java.util.concurrent.CompletableFuture<java.net.http.WebSocket> opening;
    private final String token;
    private final HttpClient client;
    private final CloudHttp http;
    private final CloudBudget budget = new CloudBudget();
    private final Map<String, Resource> resources = new ConcurrentHashMap<>();
    private final YsmSyncPackets reassembly = new YsmSyncPackets();
    private final ArrayDeque<Packet> pending = new ArrayDeque<>();
    private CloudSocket socket;
    private Listener listener;
    private volatile boolean ready;
    private volatile boolean closed;
    private long messageId;
    private long tick;
    private int pendingBytes;

    private record Packet(byte[] bytes, long enqueuedAt) {}

    public FiguraCloudProtocol(URI origin, String token, HttpClient client) {
        this(CloudEndpoint.parse(origin.toString(), () -> false), token, client);
    }

    public FiguraCloudProtocol(CloudEndpoint endpoint, String token, HttpClient client) {
        this.endpoint = endpoint;
        this.origin = endpoint.origin();
        this.token = token;
        this.client = client;
        this.http = new CloudHttp(client, endpoint);
    }

    @Override
    public void connect(Listener listener) throws Exception {
        endpoint.requireAllowed();
        this.listener = listener;
        socket = new CloudSocket(65536, this::receive, reason -> {
            ready = false;
            if (!closed) {
                listener.closed(reason);
            }
        }, endpoint);
        URI target = endpoint.websocket("/ws");
        endpoint.requireAllowed();
        opening = client.newWebSocketBuilder().connectTimeout(Duration.ofSeconds(15))
                .header("x-omnimorph-models", "ysm").buildAsync(target, socket);
        if (closed) {
            opening.cancel(true);
        }
        var connection = opening.get(15, TimeUnit.SECONDS);
        socket.attach(connection);
        byte[] bytes = token.getBytes(StandardCharsets.UTF_8);
        socket.send(ByteBuffer.allocate(1 + bytes.length).put((byte) 0).put(bytes).array());
    }

    private void receive(byte[] bytes) {
        if (closed || bytes.length == 0) {
            return;
        }
        try {
            ByteBuffer input = ByteBuffer.wrap(bytes);
            switch (input.get()) {
                case 0 -> {
                    ready = true;
                    listener.ready();
                }
                case 1 -> {
                    if (input.remaining() < 21) {
                        throw new IOException("Truncated Ping");
                    }
                    UUID owner = new UUID(input.getLong(), input.getLong());
                    int id = input.getInt();
                    boolean echo = input.get() != 0;
                    byte[] arguments = new byte[input.remaining()];
                    input.get(arguments);
                    if (YsmSyncPackets.recognizes(id, arguments)) {
                        byte[] message;
                        synchronized (reassembly) {
                            message = reassembly.accept(owner, arguments, tick);
                        }
                        if (message != null) {
                            YsmSyncMessage decoded = YsmSyncCodec.decode(message);
                            if (!owner.equals(decoded.owner())) {
                                throw new IOException("M7 owner mismatch");
                            }
                            listener.ysm(decoded);
                        }
                    } else {
                        if (arguments.length > 1024) {
                            throw new IOException("Ping exceeds byte budget");
                        }
                        listener.ping(owner, id, echo, arguments);
                    }
                }
                case 2 -> {
                    if (input.remaining() != 16) {
                        throw new IOException("Invalid equipment event");
                    }
                    UUID owner = new UUID(input.getLong(), input.getLong());
                    resources.entrySet().removeIf(entry -> entry.getValue().owner().equals(owner));
                    listener.changed(owner);
                }
                case 3 -> {
                    if (!input.hasRemaining()) {
                        throw new IOException("Truncated toast");
                    }
                    int type = Byte.toUnsignedInt(input.get());
                    String[] text = StandardCharsets.UTF_8.decode(input).toString().split("\\x00", 2);
                    listener.notification(3, type, text[0], text.length > 1 ? text[1] : "");
                }
                case 4 -> listener.notification(4, 0, StandardCharsets.UTF_8.decode(input).toString(), "");
                case 5 -> {
                    if (input.remaining() != 1) {
                        throw new IOException("Invalid notice");
                    }
                    listener.notification(5, Byte.toUnsignedInt(input.get()), "", "");
                }
                default -> throw new IOException("Unknown Figura message");
            }
        } catch (Exception exception) {
            close();
            listener.closed("Invalid Figura Cloud message");
        }
    }

    private HttpRequest.Builder request(String path) {
        return CloudHttp.request(origin.resolve("/api/" + path)).header("token", token)
                .header("x-omnimorph-models", "ysm");
    }

    private Reply reply(HttpRequest request) throws Exception {
        budget.await(128, http);
        var response = http.send(request);
        try (var body = response.body()) {
            return new Reply(response.statusCode(), new String(CloudHttp.readBounded(body, 65536), StandardCharsets.UTF_8));
        } finally {
            http.release(response.body());
        }
    }

    @Override
    public Reply profile(UUID owner) throws Exception {
        Reply response = reply(request(owner.toString()).build());
        if (response.status() != 200) {
            return response;
        }
        JsonObject json = JsonParser.parseString(response.body()).getAsJsonObject();
        JsonArray equipped = json.getAsJsonArray("equipped");
        if (equipped.size() > 128) {
            throw new IOException("Profile exceeds resource budget");
        }
        for (JsonElement item : equipped) {
            JsonObject entry = item.getAsJsonObject();
            String hash = entry.get("hash").getAsString();
            if (!hash.matches("[0-9a-f]{64}")) {
                throw new IOException("Invalid Figura resource hash");
            }
            UUID resourceOwner = UUID.fromString(entry.get("owner").getAsString());
            String id = entry.get("id").getAsString();
            requireAsset(id);
            String cacheKey = CloudHttp.sha256((instance() + "/" + resourceOwner + "/" + hash
                    + "/figura-compressed-nbt").getBytes(StandardCharsets.UTF_8));
            Resource resource = new Resource(resourceOwner, id, cacheKey, "legacy:" + hash, 0, -1, 0, 0);
            if (resources.size() >= 128) {
                resources.clear();
            }
            resources.put(resourceOwner + "/" + id, resource);
            entry.addProperty("hash", cacheKey);
        }
        return new Reply(200, json.toString());
    }

    private static void requireAsset(String id) throws IOException {
        if (!id.matches("[a-zA-Z0-9_-]{1,64}")) {
            throw new IOException("Invalid asset ID");
        }
    }

    @Override
    public Reply limits() throws Exception {
        return reply(request("limits").build());
    }

    @Override
    public Reply version() throws Exception {
        return reply(request("version").build());
    }

    @Override
    public Reply motd() throws Exception {
        return reply(request("motd").build());
    }

    @Override
    public Reply checkAuth() throws Exception {
        return reply(request("").build());
    }

    @Override
    public Reply diagnostic(String path) throws Exception {
        return reply(request(path).build());
    }

    @Override
    public Reply upload(String id, byte[] bytes, Metadata metadata) throws Exception {
        requireAsset(id);
        if (bytes.length > CloudHttp.FILE_BYTES) {
            return new Reply(413, "");
        }
        return reply(request(id).header("Content-Type", "application/octet-stream")
                .PUT(HttpRequest.BodyPublishers.ofByteArray(bytes)).build());
    }

    @Override
    public Reply delete(String id) throws Exception {
        requireAsset(id);
        return reply(request(id).DELETE().build());
    }

    @Override
    public Reply equip(List<UUID> owners, List<String> assets) throws Exception {
        if (owners.size() != assets.size()) {
            throw new IOException("Invalid equipment selection");
        }
        JsonArray selected = new JsonArray();
        for (int index = 0; index < owners.size(); index++) {
            requireAsset(assets.get(index));
            JsonObject entry = new JsonObject();
            entry.addProperty("owner", owners.get(index).toString());
            entry.addProperty("id", assets.get(index));
            selected.add(entry);
        }
        return reply(request("equip").header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(selected.toString())).build());
    }

    @Override
    public Download download(UUID owner, String id) throws Exception {
        requireAsset(id);
        Resource resource = resources.get(owner + "/" + id);
        if (resource == null) {
            throw new IOException("Resource profile required");
        }
        var response = http.send(request(owner + "/" + id).build());
        if (response.statusCode() != 200) {
            http.release(response.body());
            throw new IOException("HTTP " + response.statusCode());
        }
        return new Download(resource, http.managed(response.body()));
    }

    @Override
    public void ping(int id, boolean echo, byte[] arguments) throws Exception {
        if (arguments.length > 1024) {
            throw new IOException("Ping exceeds byte budget");
        }
        enqueue(ByteBuffer.allocate(6 + arguments.length).put((byte) 1).putInt(id)
                .put((byte) (echo ? 1 : 0)).put(arguments).array());
    }

    private void subscription(byte kind, UUID owner) throws Exception {
        enqueue(ByteBuffer.allocate(17).put(kind).putLong(owner.getMostSignificantBits())
                .putLong(owner.getLeastSignificantBits()).array());
    }

    @Override
    public void subscribe(UUID owner) throws Exception {
        subscription((byte) 2, owner);
    }

    @Override
    public void unsubscribe(UUID owner) throws Exception {
        subscription((byte) 3, owner);
    }

    @Override
    public synchronized boolean publish(YsmSyncMessage message) throws Exception {
        endpoint.requireAllowed();
        List<byte[]> packets = YsmSyncPackets.fragment(++messageId, YsmSyncCodec.encode(message));
        int size = packets.stream().mapToInt(packet -> packet.length + 6).sum();
        if (pending.size() + packets.size() > 1024 || pendingBytes + size > 262144) {
            throw new IOException("M7 queue requires recovery");
        }
        long now = System.nanoTime();
        for (byte[] packet : packets) {
            byte[] bytes = ByteBuffer.allocate(6 + packet.length).put((byte) 1)
                    .putInt(YsmSyncPackets.PING_ID).put((byte) 0).put(packet).array();
            pending.addLast(new Packet(bytes, now));
            pendingBytes += bytes.length;
        }
        return true;
    }

    private synchronized void enqueue(byte[] bytes) throws IOException {
        endpoint.requireAllowed();
        if (pending.size() >= 1024 || pendingBytes + bytes.length > 262144) {
            throw new IOException("Figura socket queue requires recovery");
        }
        pending.addLast(new Packet(bytes, System.nanoTime()));
        pendingBytes += bytes.length;
    }

    @Override
    public synchronized void tick(long tick) throws Exception {
        http.requireOpen();
        this.tick = tick;
        synchronized (reassembly) {
            reassembly.expire(tick);
        }
        while (ready && !pending.isEmpty()) {
            if (!budget.take(pending.getFirst().bytes().length)) {
                break;
            }
            Packet packet = pending.removeFirst();
            pendingBytes -= packet.bytes().length;
            if (System.nanoTime() - packet.enqueuedAt() >= Duration.ofSeconds(30).toNanos()) {
                throw new IOException("M7 event queue expired; reconnect required");
            }
            if (!socket.send(packet.bytes())) {
                throw new IOException("M7 socket requires recovery");
            }
        }
    }

    @Override
    public boolean connected() {
        return ready && !closed;
    }

    @Override
    public String instance() {
        return "figura:" + origin;
    }

    @Override
    public synchronized void close() {
        closed = true;
        ready = false;
        pending.clear();
        pendingBytes = 0;
        http.close();
        if (opening != null) {
            opening.cancel(true);
        }
        if (socket != null) {
            socket.close();
        }
        synchronized (reassembly) {
            reassembly.clear();
        }
    }
}
