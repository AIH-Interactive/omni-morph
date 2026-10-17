package top.aihmc.omnimorph.backend2.protocol;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.protobuf.ByteString;
import top.aihmc.omnimorph.backend2.protocol.generated.Cloud;
import top.aihmc.omnimorph.model.ysm.sync.YsmSyncMessage;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/** Protobuf control/state and authorized raw HTTP resources for one connection epoch. */
public final class ProtobufCloudProtocol implements CloudProtocol {
    public static final String CONTENT_TYPE = "application/x-protobuf";
    private final URI origin;
    private final CloudEndpoint endpoint;
    private volatile java.util.concurrent.CompletableFuture<java.net.http.WebSocket> opening;
    private final String token;
    private final String clientVersion;
    private final HttpClient client;
    private final CloudHttp http;
    private final CloudBudget budget = new CloudBudget();
    private final ArrayDeque<Outbound> pending = new ArrayDeque<>();
    private int pendingBytes;
    private record Outbound(byte[] bytes, long time) {}
    private final long epoch;
    private final Set<UUID> subscribed = ConcurrentHashMap.newKeySet();
    private final AtomicLong requestIds = new AtomicLong();
    private final Map<String, Cloud.AvatarDescriptor> resources = new ConcurrentHashMap<>();
    private final Map<String, Cloud.AvatarDescriptor> uploads = new ConcurrentHashMap<>();
    private final Map<UUID, Cloud.Resume> cursors = new ConcurrentHashMap<>();
    private final Map<UUID, Cloud.EquippedState> equipment = new ConcurrentHashMap<>();
    private final YsmProtobuf.Publisher publisher = new YsmProtobuf.Publisher();
    private volatile String session = "";
    private volatile UUID owner;
    private volatile Cloud.HelloAck capabilities;
    private volatile boolean ready;
    private volatile boolean closed;
    private Listener listener;
    private CloudSocket socket;
    private long heartbeatTick;

    public ProtobufCloudProtocol(URI origin, String token, String clientVersion, long epoch, HttpClient client) {
        this(CloudEndpoint.parse(origin.toString(), () -> false), token, clientVersion, epoch, client);
    }

    public ProtobufCloudProtocol(CloudEndpoint endpoint, String token, String clientVersion, long epoch, HttpClient client) {
        this.endpoint = endpoint;
        this.origin = endpoint.origin();
        this.token = token;
        this.clientVersion = clientVersion;
        this.epoch = epoch;
        this.client = client;
        this.http = new CloudHttp(client, endpoint);
    }

    @Override
    public void connect(Listener listener) throws Exception {
        endpoint.requireAllowed();
        this.listener = listener;
        capabilities = discover(http, origin);
        budget.configure(capabilities.getLimits().getMessagesPerSecond(), capabilities.getLimits().getBytesPerSecond());
        Cloud.Envelope authenticated = rpc(Cloud.Envelope.newBuilder().setAuth(Cloud.Auth.newBuilder()
                .setToken(token).setInstanceId(capabilities.getInstanceId())));
        if (!authenticated.hasAuthResult()) {
            throw new IOException("Missing scoped authentication");
        }
        Cloud.AuthResult auth = authenticated.getAuthResult();
        if (!auth.getInstanceId().equals(capabilities.getInstanceId()) || auth.getProtocolMajor() != 1
                || !auth.getSessionId().equals(authenticated.getSessionId())
                || authenticated.getConnectionEpoch() != epoch || auth.getExpiresUnixSeconds() <= System.currentTimeMillis() / 1000) {
            throw new IOException("Invalid authentication scope");
        }
        owner = UUID.fromString(auth.getOwner());
        session = auth.getSessionId();
        // Bind publication revision before a local runtime can start sending state.
        profile(owner);
        socket = new CloudSocket(Math.min(65536, capabilities.getLimits().getEnvelopeBytes()), this::receive, reason -> {
            ready = false;
            if (!closed) {
                listener.closed(reason);
            }
        }, endpoint);
        URI target = endpoint.websocket("/ws/protobuf/v1");
        endpoint.requireAllowed();
        opening = client.newWebSocketBuilder().connectTimeout(Duration.ofSeconds(15))
                .subprotocols("omnimorph.cloud.v1").header("Authorization", "Bearer " + session)
                .header("x-omnimorph-epoch", Long.toString(epoch)).buildAsync(target, socket);
        if (closed) {
            opening.cancel(true);
        }
        var connection = opening.get(15, TimeUnit.SECONDS);
        if (!connection.getSubprotocol().equals("omnimorph.cloud.v1")) {
            connection.abort();
            throw new IOException("Missing negotiated subprotocol");
        }
        socket.attach(connection);
        send(Cloud.Envelope.newBuilder().setHello(Cloud.Hello.newBuilder().addSupportedMajors(1)
                .setClientVersion(clientVersion).addCapabilities(Cloud.Capability.FIGURA)
                .addCapabilities(Cloud.Capability.YSM_PLAIN).addCapabilities(Cloud.Capability.YSM_ENCRYPTED)
                .addCapabilities(Cloud.Capability.YSM_RECOVERY)));
    }

    public static void probe(URI origin, HttpClient client) throws Exception {
        probe(CloudEndpoint.parse(origin.toString(), () -> false), client);
    }

    public static void probe(CloudEndpoint endpoint, HttpClient client) throws Exception {
        try (CloudHttp probe = new CloudHttp(client, endpoint)) {
            probe(endpoint, probe);
        }
    }

    public static void probe(CloudEndpoint endpoint, CloudHttp transport) throws Exception {
        endpoint.requireAllowed();
        discover(transport, endpoint.origin());
    }

    private static Cloud.HelloAck discover(CloudHttp http, URI origin) throws Exception {
        var response = http.send(CloudHttp.request(origin.resolve("/api/protobuf/v1/capabilities")).build());
        try (var body = response.body()) {
            if (response.statusCode() == 404 || response.statusCode() == 405 || response.statusCode() == 501
                    || response.statusCode() == 200 && !response.headers().firstValue("Content-Type").orElse("")
                            .split(";")[0].equals(CONTENT_TYPE)) {
                throw new Unsupported();
            }
            if (response.statusCode() != 200) {
                throw new IOException("Capability discovery failed: HTTP " + response.statusCode());
            }
            Cloud.Envelope discovery = CloudWire.decode(CloudHttp.readBounded(body, 65536));
            if (!discovery.hasHelloAck()) {
                throw new Unsupported();
            }
            validateCapabilities(discovery.getHelloAck());
            return discovery.getHelloAck();
        } finally {
            http.release(response.body());
        }
    }

    private static void validateCapabilities(Cloud.HelloAck hello) throws IOException {
        if (hello.getSelectedMajor() != 1 || hello.getInstanceId().isBlank()
                || !hello.getCapabilitiesList().contains(Cloud.Capability.FIGURA)
                || hello.getLimits().getEnvelopeBytes() == 0 || hello.getLimits().getEnvelopeBytes() > 65536) {
            throw new Unsupported();
        }
    }

    private Cloud.Envelope envelope(Cloud.Envelope.Builder body) throws IOException {
        http.requireOpen();
        Cloud.Envelope result = body.setMajor(1).setRequestId(requestIds.incrementAndGet())
                .setConnectionEpoch(epoch).setSessionId(session).build();
        int limit = capabilities == null ? 65536 : capabilities.getLimits().getEnvelopeBytes();
        if (result.getSerializedSize() > Math.min(limit, 65536)) {
            throw new IOException("Envelope exceeds negotiated budget");
        }
        return result;
    }

    private HttpRequest.Builder authorized(URI uri) {
        var request = CloudHttp.request(uri);
        if (!session.isEmpty()) {
            request.header("Authorization", "Bearer " + session).header("x-omnimorph-epoch", Long.toString(epoch));
        }
        return request;
    }

    private Cloud.Envelope rpc(Cloud.Envelope.Builder body) throws Exception {
        Cloud.Envelope request = envelope(body);
        budget.await(request.getSerializedSize(), http);
        var response = http.send(authorized(origin.resolve("/api/protobuf/v1/rpc"))
                .header("Content-Type", CONTENT_TYPE)
                .POST(HttpRequest.BodyPublishers.ofByteArray(request.toByteArray())).build());
        try (var stream = response.body()) {
            if (!response.headers().firstValue("Content-Type").orElse("").split(";")[0].equals(CONTENT_TYPE)) {
                throw new Unsupported();
            }
            int limit = capabilities == null ? 65536 : Math.min(65536, capabilities.getLimits().getEnvelopeBytes());
            Cloud.Envelope message = CloudWire.decode(CloudHttp.readBounded(stream, limit));
            if (message.getRequestId() != request.getRequestId()) {
                throw new IOException("Mismatched request ID");
            }
            if (!session.isEmpty()) {
                validateScope(message);
            }
            if (message.hasError()) {
                throw new Failure(message.getError().getCode());
            }
            if (response.statusCode() != 200) {
                throw new IOException("HTTP " + response.statusCode());
            }
            return message;
        } finally {
            http.release(response.body());
        }
    }

    private void validateScope(Cloud.Envelope message) throws IOException {
        if (!session.equals(message.getSessionId()) || message.getConnectionEpoch() != epoch) {
            throw new IOException("Retired or foreign connection scope");
        }
    }

    private void receive(byte[] bytes) {
        if (closed) {
            return;
        }
        try {
            Cloud.Envelope message = CloudWire.decode(bytes);
            validateScope(message);
            switch (message.getBodyCase()) {
                case HELLO_ACK -> {
                    validateCapabilities(message.getHelloAck());
                    if (!capabilities.getInstanceId().equals(message.getHelloAck().getInstanceId())
                            || capabilities.getServerEpoch() != message.getHelloAck().getServerEpoch()) {
                        throw new IOException("Backend restarted during negotiation");
                    }
                    ready = true;
                    listener.ready();
                }
                case FIGURA_PING -> {
                    var ping = message.getFiguraPing();
                    if (ping.getArguments().size() > 1024) {
                        throw new IOException("Ping exceeds budget");
                    }
                    listener.ping(UUID.fromString(ping.getOwner()), ping.getFunctionId(), ping.getEcho(),
                            ping.getArguments().toByteArray());
                }
                case AVATAR_CHANGED -> {
                    var equipped = message.getAvatarChanged().getEquipped();
                    UUID changedOwner = UUID.fromString(equipped.getOwner());
                    rememberEquipment(equipped);
                    cursors.remove(changedOwner);
                    listener.changed(changedOwner);
                }
                case YSM_SNAPSHOT, YSM_DELTA, YSM_EVENT -> {
                    validateYsmBudget(message);
                    YsmSyncMessage decoded = YsmProtobuf.decode(message);
                    Cloud.YsmIdentity identity = switch (message.getBodyCase()) {
                        case YSM_SNAPSHOT -> message.getYsmSnapshot().getIdentity();
                        case YSM_DELTA -> message.getYsmDelta().getIdentity();
                        default -> message.getYsmEvent().getIdentity();
                    };
                    Cloud.EquippedState selected = equipment.get(decoded.owner());
                    if (selected == null || !selected.hasPrimary()) {
                        requestSnapshotAfterProfile(decoded.owner());
                        return;
                    }
                    Cloud.AvatarDescriptor descriptor = selected.getPrimary();
                    if (descriptor.getRevision() != identity.getAssetRevision()
                            || !descriptor.getContentSha256().equals(identity.getContentSha256())
                            || !descriptor.getSchemaSha256().isEmpty() && !descriptor.getSchemaSha256().equals(identity.getSchemaSha256())) {
                        throw new IOException("YSM resource identity mismatch");
                    }
                    listener.ysm(decoded);
                    if (cursors.size() >= 128 && !cursors.containsKey(decoded.owner())) {
                        throw new IOException("Cursor budget exceeded");
                    }
                    cursors.put(decoded.owner(), Cloud.Resume.newBuilder().setOwner(identity.getOwner())
                            .setServerEpoch(capabilities.getServerEpoch()).setRevision(identity.getAssetRevision())
                            .setPublisherSession(identity.getPublisherSession()).setGeneration(identity.getGeneration())
                            .setSequence(identity.getSequence()).setEventWatermark(identity.getEventWatermark()).build());
                }
                case SNAPSHOT_REQUEST -> {
                    if (message.getSnapshotRequest().getOwner().equals(owner.toString())) {
                        listener.snapshotRequested();
                    }
                }
                case HEARTBEAT -> send(Cloud.Envelope.newBuilder().setHeartbeat(message.getHeartbeat()));
                case ERROR -> {
                    Cloud.ErrorCode code = message.getError().getCode();
                    if (code == Cloud.ErrorCode.SNAPSHOT_REQUIRED || code == Cloud.ErrorCode.CURSOR_EXPIRED) {
                        listener.snapshotRequested();
                    } else {
                        throw new Failure(code);
                    }
                }
                case ACK -> { /* Control operations acknowledge without entering the model state machine. */ }
                default -> throw new IOException("Unexpected protobuf message");
            }
        } catch (Exception exception) {
            close();
            listener.closed(exception instanceof Failure ? exception.getMessage() : "Invalid Protobuf connection message");
        }
    }

    private void validateYsmBudget(Cloud.Envelope message) throws IOException {
        Cloud.YsmState state;
        int size;
        switch (message.getBodyCase()) {
            case YSM_SNAPSHOT -> {
                state = message.getYsmSnapshot().getState();
                size = message.getYsmSnapshot().getSerializedSize();
            }
            case YSM_DELTA -> {
                state = message.getYsmDelta().getState();
                size = message.getYsmDelta().getSerializedSize();
            }
            case YSM_EVENT -> {
                state = Cloud.YsmState.getDefaultInstance();
                size = message.getYsmEvent().getSerializedSize();
                if (message.getYsmEvent().getEventsCount() > Math.min(64, capabilities.getLimits().getEventCount())) {
                    throw new IOException("Negotiated event budget exceeded");
                }
            }
            default -> throw new IOException("Not YSM state");
        }
        int entries = Math.min(512, capabilities.getLimits().getRepeatedEntries());
        if (size > Math.min(32768, capabilities.getLimits().getYsmStateBytes())
                || state.getControls().getValuesCount() > entries || state.getActions().getValuesCount() > entries
                || state.getControllers().getValuesCount() > entries || state.getPlaybacks().getValuesCount() > entries) {
            throw new IOException("Negotiated YSM state budget exceeded");
        }
    }

    private void requestSnapshotAfterProfile(UUID owner) {
        // The game entry point obtains profiles before subscription; a racing equipment change
        // is recovered by that entry point once the matching runtime has finished loading.
        cursors.remove(owner);
    }

    private void rememberEquipment(Cloud.EquippedState equipped) throws IOException {
        UUID account = UUID.fromString(equipped.getOwner());
        if (equipment.size() >= 128 && !equipment.containsKey(account)) {
            throw new IOException("Equipment budget exceeded");
        }
        equipment.put(account, equipped);
        resources.entrySet().removeIf(entry -> entry.getKey().startsWith(account + "/"));
        if (equipped.hasPrimary()) {
            Cloud.AvatarDescriptor descriptor = equipped.getPrimary();
            validateDescriptor(descriptor, account);
            resources.put(account + "/" + descriptor.getAssetId(), descriptor);
        }
    }

    private void validateDescriptor(Cloud.AvatarDescriptor descriptor, UUID expectedOwner) throws IOException {
        if (!descriptor.getOwner().equals(expectedOwner.toString()) || descriptor.getRevision() <= 0
                || !descriptor.getAssetId().matches("[a-zA-Z0-9_-]{1,64}") || descriptor.getRawSha256().size() != 32
                || descriptor.getRawLength() < 0 || descriptor.getRawLength() > CloudHttp.FILE_BYTES
                || descriptor.getModelTypeValue() < 1 || descriptor.getModelTypeValue() > 3
                || descriptor.getDistributionFormatValue() != (descriptor.getModelTypeValue() == 1 ? 1 : 2)) {
            throw new IOException("Invalid resource descriptor");
        }
        if (descriptor.getModelTypeValue() != 1 && (descriptor.getContentSha256().size() != 32
                || !descriptor.getSchemaSha256().isEmpty() && descriptor.getSchemaSha256().size() != 32)) {
            throw new IOException("Invalid YSM resource identity");
        }
        for (var capability : descriptor.getRequiredCapabilitiesList()) {
            if (!capabilities.getCapabilitiesList().contains(capability)) {
                throw new IOException("UNSUPPORTED_CAPABILITY");
            }
        }
    }

    private Resource resource(Cloud.AvatarDescriptor descriptor) {
        String raw = HexFormat.of().formatHex(descriptor.getRawSha256().toByteArray());
        String key = CloudHttp.sha256((instance() + "/" + descriptor.getOwner() + "/" + descriptor.getAssetId()
                + "/" + descriptor.getRevision() + "/" + raw + "/" + descriptor.getModelTypeValue()
                + "/" + descriptor.getDistributionFormatValue()).getBytes(StandardCharsets.UTF_8));
        return new Resource(UUID.fromString(descriptor.getOwner()), descriptor.getAssetId(), key, raw,
                descriptor.getRevision(), descriptor.getRawLength(), descriptor.getModelTypeValue(), descriptor.getDistributionFormatValue());
    }

    @Override
    public Reply profile(UUID owner) throws Exception {
        Cloud.Profile profile = rpc(Cloud.Envelope.newBuilder().setProfileRequest(Cloud.ProfileRequest.newBuilder()
                .setOwner(owner.toString()))).getProfile();
        if (!profile.getOwner().equals(owner.toString())) {
            throw new IOException("Profile owner mismatch");
        }
        rememberEquipment(profile.getEquipped());
        JsonObject json = new JsonObject();
        json.addProperty("uuid", profile.getOwner());
        json.addProperty("rank", profile.getRank());
        json.addProperty("version", profile.getClientVersion());
        json.addProperty("lastUsed", profile.getLastUsed());
        json.addProperty("banned", profile.getBanned());
        json.addProperty("canUpload", profile.getCanUpload());
        JsonArray equipped = new JsonArray();
        if (profile.getEquipped().hasPrimary()) {
            var primary = resource(profile.getEquipped().getPrimary());
            JsonObject entry = new JsonObject();
            entry.addProperty("owner", primary.owner().toString());
            entry.addProperty("id", primary.id());
            entry.addProperty("hash", primary.cacheKey());
            equipped.add(entry);
        }
        json.add("equipped", equipped);
        JsonObject badges = new JsonObject();
        JsonArray special = new JsonArray();
        profile.getBadges().getSpecialList().forEach(special::add);
        JsonArray pride = new JsonArray();
        profile.getBadges().getPrideList().forEach(pride::add);
        badges.add("special", special);
        badges.add("pride", pride);
        json.add("equippedBadges", badges);
        return new Reply(200, json.toString());
    }

    private Cloud.ServerInfo serverInfo() throws Exception {
        var result = rpc(Cloud.Envelope.newBuilder().setServerInfo(Cloud.ServerInfo.getDefaultInstance()));
        if (!result.hasServerInfo()) {
            throw new IOException("Server information missing");
        }
        return result.getServerInfo();
    }

    @Override
    public Reply limits() throws Exception {
        Cloud.Limits limits = serverInfo().getLimits();
        JsonObject json = new JsonObject();
        JsonObject rate = new JsonObject();
        rate.addProperty("upload", 1);
        rate.addProperty("download", 1);
        json.add("rate", rate);
        JsonObject size = new JsonObject();
        size.addProperty("maxAvatarSize", Math.min(CloudHttp.FILE_BYTES, limits.getUploadBytes()));
        json.add("limits", size);
        return new Reply(200, json.toString());
    }

    @Override
    public Reply version() throws Exception {
        var info = serverInfo();
        JsonObject json = new JsonObject();
        json.addProperty("release", info.getRelease());
        json.addProperty("prerelease", info.getPrerelease());
        return new Reply(200, json.toString());
    }

    @Override
    public Reply motd() throws Exception {
        return new Reply(200, serverInfo().getMotdJson());
    }

    @Override
    public Reply checkAuth() throws Exception {
        rpc(Cloud.Envelope.newBuilder().setHeartbeat(Cloud.Heartbeat.getDefaultInstance()));
        return new Reply(200, "");
    }

    @Override
    public Reply upload(String id, byte[] bytes, Metadata metadata) throws Exception {
        return upload(id, bytes, metadata, sent -> {});
    }

    @Override
    public Reply upload(String id, byte[] bytes, Metadata metadata,
                        java.util.function.LongConsumer progress) throws Exception {
        if (!id.matches("[a-zA-Z0-9_-]{1,64}") || bytes.length > Math.min(CloudHttp.FILE_BYTES, capabilities.getLimits().getUploadBytes())) {
            return new Reply(413, "");
        }
        var descriptor = Cloud.AvatarDescriptor.newBuilder().setOwner(owner.toString()).setAssetId(id)
                .setModelTypeValue(metadata.modelType()).setDistributionFormatValue(metadata.modelType() == 1 ? 1 : 2)
                .setRawLength(bytes.length).setRawSha256(ByteString.copyFrom(HexFormat.of().parseHex(CloudHttp.sha256(bytes))))
                .addRequiredCapabilitiesValue(metadata.modelType());
        if (metadata.modelType() != 1) {
            if (metadata.modelType() < 2 || metadata.modelType() > 3
                    || !capabilities.getCapabilitiesValueList().contains(metadata.modelType())) {
                throw new Failure(Cloud.ErrorCode.UNSUPPORTED_CAPABILITY);
            }
            descriptor.setContentSha256(ByteString.copyFrom(HexFormat.of().parseHex(metadata.contentHash())))
                    .setSchemaSha256(ByteString.copyFrom(HexFormat.of().parseHex(metadata.schemaHash())));
        }
        String operation = UUID.randomUUID().toString();
        var grantResponse = rpc(Cloud.Envelope.newBuilder().setUploadRequest(Cloud.UploadRequest.newBuilder()
                .setOperationId(operation).setResource(descriptor)));
        if (!grantResponse.hasTransferGrant()) {
            throw new IOException("Upload grant missing");
        }
        var grant = grantResponse.getTransferGrant();
        validateGrant(grant, bytes.length);
        var response = http.send(authorized(CloudHttp.relative(origin, grant.getRelativePath()))
                .header("Content-Type", "application/octet-stream").PUT(CloudHttp.uploadBody(bytes, progress)).build());
        try {
            if (response.statusCode() != 204) {
                throw new IOException("Raw upload failed: HTTP " + response.statusCode());
            }
        } finally {
            http.release(response.body());
        }
        var committed = rpc(Cloud.Envelope.newBuilder().setCommitUpload(Cloud.CommitUpload.newBuilder()
                .setOperationId(operation).setRawSha256(descriptor.getRawSha256()).setRawLength(bytes.length)));
        if (!committed.hasAvatar()) {
            throw new IOException("Resource commit missing");
        }
        validateDescriptor(committed.getAvatar(), owner);
        if (!committed.getAvatar().getRawSha256().equals(descriptor.getRawSha256())
                || committed.getAvatar().getRawLength() != bytes.length) {
            throw new IOException("Committed resource identity mismatch");
        }
        uploads.put(id, committed.getAvatar());
        return new Reply(200, "");
    }

    private void validateGrant(Cloud.TransferGrant grant, long length) throws IOException {
        if (grant.getExpiresUnixSeconds() <= System.currentTimeMillis() / 1000 || grant.getMaxBytes() != length
                || !grant.getRelativePath().startsWith("/api/protobuf/v1/resources/")) {
            throw new IOException("Invalid or expired transfer grant");
        }
    }

    @Override
    public Reply equip(List<UUID> owners, List<String> assets) throws Exception {
        if (owners.size() != assets.size() || owners.size() > 1 || !owners.isEmpty() && !owners.getFirst().equals(owner)) {
            throw new IOException("Invalid equipment authority");
        }
        profile(owner);
        var request = Cloud.Equip.newBuilder().setOperationId(UUID.randomUUID().toString())
                .setExpectedRevision(equipment.get(owner).getRevision()).setClear(owners.isEmpty());
        if (!owners.isEmpty()) {
            String id = assets.getFirst();
            var resource = uploads.getOrDefault(id, resources.get(owner + "/" + id));
            if (resource == null) {
                throw new Failure(Cloud.ErrorCode.NOT_FOUND);
            }
            request.setAssetId(id).setAssetRevision(resource.getRevision());
        }
        var response = rpc(Cloud.Envelope.newBuilder().setEquip(request));
        if (!response.hasEquipped()) {
            throw new IOException("Equipment commit missing");
        }
        rememberEquipment(response.getEquipped());
        return new Reply(200, "");
    }

    @Override
    public Reply delete(String id) throws Exception {
        profile(owner);
        var descriptor = uploads.getOrDefault(id, resources.get(owner + "/" + id));
        if (descriptor == null) {
            return new Reply(404, "");
        }
        rpc(Cloud.Envelope.newBuilder().setDelete(Cloud.Delete.newBuilder().setOperationId(UUID.randomUUID().toString())
                .setAssetId(id).setExpectedRevision(descriptor.getRevision())));
        uploads.remove(id);
        resources.remove(owner + "/" + id);
        return new Reply(200, "");
    }

    @Override
    public Download download(UUID owner, String id) throws Exception {
        var descriptor = resources.get(owner + "/" + id);
        if (descriptor == null) {
            throw new IOException("Resource profile required");
        }
        var response = rpc(Cloud.Envelope.newBuilder().setDownloadRequest(Cloud.DownloadRequest.newBuilder()
                .setOwner(owner.toString()).setAssetId(id).setRevision(descriptor.getRevision())));
        if (!response.hasTransferGrant()) {
            throw new IOException("Download grant missing");
        }
        var grant = response.getTransferGrant();
        validateGrant(grant, descriptor.getRawLength());
        var raw = http.send(authorized(CloudHttp.relative(origin, grant.getRelativePath())).build());
        if (raw.statusCode() != 200 || !raw.headers().firstValue("Content-Type").orElse("").equals("application/octet-stream")) {
            http.release(raw.body());
            throw new IOException("Raw download failed: HTTP " + raw.statusCode());
        }
        return new Download(resource(descriptor), http.managed(raw.body()));
    }

    private synchronized boolean send(Cloud.Envelope.Builder body) throws IOException {
        byte[] bytes = envelope(body).toByteArray();
        if (body.hasHello()) {
            return socket.send(bytes);
        }
        if (pending.size() >= 32 || pendingBytes + bytes.length > Math.min(262144, capabilities.getLimits().getQueueBytes())) {
            throw new IOException("Socket queue requires recovery");
        }
        pending.addLast(new Outbound(bytes, System.nanoTime()));
        pendingBytes += bytes.length;
        return true;
    }

    @Override
    public void ping(int id, boolean echo, byte[] arguments) throws Exception {
        if (arguments.length > 1024) {
            throw new IOException("Ping exceeds byte budget");
        }
        send(Cloud.Envelope.newBuilder().setFiguraPing(Cloud.FiguraPing.newBuilder().setFunctionId(id)
                .setEcho(echo).setArguments(ByteString.copyFrom(arguments))));
    }

    @Override
    public void subscribe(UUID owner) throws Exception {
        if (!subscribed.contains(owner) && subscribed.size() >= Math.min(128, capabilities.getLimits().getSubscriptions())) {
            throw new IOException("Subscription budget exceeded");
        }
        profile(owner);
        var request = Cloud.Subscribe.newBuilder().addOwners(owner.toString());
        Cloud.Resume cursor = cursors.get(owner);
        if (cursor != null) {
            request.addCursors(cursor);
        }
        send(Cloud.Envelope.newBuilder().setSubscribe(request));
        subscribed.add(owner);
    }

    @Override
    public void unsubscribe(UUID owner) throws Exception {
        send(Cloud.Envelope.newBuilder().setUnsubscribe(Cloud.Unsubscribe.newBuilder().addOwners(owner.toString())));
        subscribed.remove(owner);
        cursors.remove(owner);
        equipment.remove(owner);
        resources.entrySet().removeIf(entry -> entry.getKey().startsWith(owner + "/"));
    }

    @Override
    public synchronized boolean publish(YsmSyncMessage message) throws Exception {
        var equipped = equipment.get(owner);
        if (equipped == null || !equipped.hasPrimary()) {
            return false;
        }
        var descriptor = equipped.getPrimary();
        if (descriptor.getModelTypeValue() == 1 || !descriptor.getContentSha256().equals(
                ByteString.copyFrom(HexFormat.of().parseHex(message.contentHash())))) {
            return false;
        }
        for (var body : publisher.encode(message, descriptor.getRevision())) {
            validateYsmBudget(body.build());
            if (!send(body)) {
                throw new IOException("YSM publication requires recovery");
            }
        }
        return true;
    }

    @Override
    public synchronized void tick(long tick) throws Exception {
        http.requireOpen();
        while (ready && !pending.isEmpty()) {
            Outbound outgoing = pending.getFirst();
            if (System.nanoTime() - outgoing.time() >= Duration.ofSeconds(30).toNanos()) {
                throw new IOException("Socket event queue expired; reconnect required");
            }
            if (!budget.take(outgoing.bytes().length)) {
                break;
            }
            pending.removeFirst();
            pendingBytes -= outgoing.bytes().length;
            if (!socket.send(outgoing.bytes())) {
                throw new IOException("Socket queue requires recovery");
            }
        }
        if (ready && tick - heartbeatTick >= 400) {
            heartbeatTick = tick;
            send(Cloud.Envelope.newBuilder().setHeartbeat(Cloud.Heartbeat.newBuilder().setNonce(tick)));
        }
    }

    @Override
    public boolean connected() {
        return ready && !closed;
    }

    @Override
    public String instance() {
        return capabilities == null ? origin.toString() : origin + "/" + capabilities.getInstanceId();
    }

    @Override
    public synchronized void close() {
        pending.clear();
        pendingBytes = 0;
        closed = true;
        ready = false;
        http.close();
        if (opening != null) {
            opening.cancel(true);
        }
        if (socket != null) {
            socket.close();
        }
        subscribed.clear();
        resources.clear();
        uploads.clear();
        cursors.clear();
        equipment.clear();
    }

    public static final class Unsupported extends IOException {
        public Unsupported() {
            super("backend.protobuf.unsupported");
        }
    }

    public static final class Failure extends IOException {
        public final Cloud.ErrorCode code;

        public Failure(Cloud.ErrorCode code) {
            super("backend.protobuf.error." + code.name().toLowerCase(Locale.ROOT));
            this.code = code;
        }
    }
}
