package org.figuramc.figura.backend2;

import com.google.gson.*;
import com.mojang.datafixers.util.Pair;
import com.neovisionaries.ws.client.WebSocket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Util;
import org.figuramc.figura.FiguraMod;
import org.figuramc.figura.avatar.Avatar;
import org.figuramc.figura.avatar.AvatarManager;
import org.figuramc.figura.avatar.Badges;
import org.figuramc.figura.avatar.UserData;
import org.figuramc.figura.avatar.local.CacheAvatarLoader;
import org.figuramc.figura.avatar.ysm.YsmSourcePayload;
import org.figuramc.figura.backend2.protocol.*;
import org.figuramc.figura.config.Configs;
import org.figuramc.figura.font.Emojis;
import org.figuramc.figura.gui.FiguraToast;
import org.figuramc.figura.model.ysm.YsmModelRuntime;
import org.figuramc.figura.model.ysm.sync.*;
import org.figuramc.figura.utils.FiguraText;
import org.figuramc.figura.utils.RefilledNumber;
import org.figuramc.figura.utils.TextUtils;
import org.figuramc.figura.utils.Version;
import org.jetbrains.annotations.Nullable;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BiConsumer;

public class NetworkStuff {
    protected static final HttpClient client = HttpClient.newHttpClient();
    protected static final Gson GSON = new GsonBuilder().disableHtmlEscaping().create();
    private static final ConcurrentLinkedQueue<Request> API_REQUESTS = new ConcurrentLinkedQueue<>();
    private static final List<UUID> SUBSCRIPTIONS = new ArrayList<>();
    private static final Map<UUID, Long> requestGenerations = new ConcurrentHashMap<>();
    private static final AtomicLong epochs = new AtomicLong();
    private static CompletableFuture<Void> tasks;
    private static final java.util.concurrent.atomic.AtomicInteger pendingTasks = new java.util.concurrent.atomic.AtomicInteger();
    private static volatile CloudConnection connection;
    private static final int RECONNECT = 6000;
    private static int authCheck = RECONNECT;
    private static final YsmSyncConnection YSM_SYNC = new YsmSyncConnection();
    private static Object ysmWorld;
    private static CloudConnection ysmConnection;
    private static volatile Object ysmEpoch;

    public static int backendStatus = 1;
    public static String disconnectedReason;
    public static boolean debug = false;
    @Nullable
    public static Component motd;
    public static int lastPing, pingsSent, pingsReceived;
    public static Version latestVersion;
    private static final RefilledNumber uploadRate = new RefilledNumber();
    private static final RefilledNumber downloadRate = new RefilledNumber();
    private static int maxAvatarSize = Integer.MAX_VALUE;

    @FunctionalInterface
    private interface Operation {
        CloudProtocol.Reply run(CloudProtocol protocol) throws Exception;
    }

    private record Request(UUID owner, long generation, Operation operation, BiConsumer<Integer, String> callback) {}

    public static long connectionEpoch() {
        return epochs.get();
    }

    public static void tick() {
        uploadRate.tick();
        downloadRate.tick();
        CloudConnection current = connection;
        if (current != null) {
            if (current.failure() != null) {
                disconnect(current.failure());
            } else {
                current.drain();
            }
        }
        if (--authCheck <= 0) {
            authCheck = RECONNECT;
            if (!isConnected()) {
                reAuth();
            } else {
                queueString(Util.NIL_UUID, CloudProtocol::checkAuth, (code, data) -> {
                    if (code != 200) {
                        reAuth();
                    }
                });
            }
        }
        tickSubscriptions();
        if (isConnected()) {
            processRequests();
        }
        tickYsmSync();
        if (lastPing > 0 && FiguraMod.ticks - lastPing >= 20) {
            lastPing = pingsSent = pingsReceived = 0;
        }
    }

    private static void tickYsmSync() {
        Minecraft minecraft = Minecraft.getInstance();
        CloudConnection current = connection;
        if (!isConnected() || minecraft.level == null) {
            ysmWorld = null;
            ysmConnection = null;
            ysmEpoch = null;
        } else if (ysmWorld != minecraft.level || ysmConnection != current) {
            ysmWorld = minecraft.level;
            ysmConnection = current;
            ysmEpoch = new Object();
        }
        long tick = Integer.toUnsignedLong(FiguraMod.ticks);
        if (current != null && current.active()) {
            try {
                current.protocol.tick(tick);
            } catch (Exception exception) {
                current.fail("Cloud state transport requires reconnect");
            }
        }
        YsmModelRuntime local = minecraft.player == null || !AvatarManager.localUploaded
                ? null : loadedYsmModel(minecraft.player.getUUID());
        YSM_SYNC.tickTyped(ysmEpoch, tick, local, NetworkStuff::loadedYsmModel, message -> {
            if (current == null || !current.active()) {
                return false;
            }
            try {
                return current.protocol.publish(message);
            } catch (Exception exception) {
                current.fail("Cloud YSM publication requires reconnect");
                return false;
            }
        });
    }

    private static YsmModelRuntime loadedYsmModel(UUID owner) {
        Avatar avatar = AvatarManager.getLoadedAvatar(owner);
        return avatar == null ? null : avatar.getYsmRuntime();
    }

    /** Compatibility entry points for the retired nv-websocket listener; active adapters own callbacks. */
    public static boolean isCurrentWebSocket(WebSocket source) {
        return false;
    }

    public static boolean receiveYsmPing(WebSocket source, UUID owner, int id, byte[] data) {
        return YsmSyncPackets.recognizes(id, data);
    }

    private static void tickSubscriptions() {
        ClientPacketListener game = Minecraft.getInstance().getConnection();
        if (game == null) {
            unsubscribeAll();
            return;
        }
        List<UUID> absent = new ArrayList<>(SUBSCRIPTIONS);
        for (UUID owner : game.getOnlinePlayerIds()) {
            absent.remove(owner);
            if (!SUBSCRIPTIONS.contains(owner) && SUBSCRIPTIONS.size() < 128) {
                SUBSCRIPTIONS.add(owner);
                subscribe(owner);
            }
        }
        for (UUID owner : absent) {
            SUBSCRIPTIONS.remove(owner);
            unsubscribe(owner);
        }
    }

    private static void processRequests() {
        CloudConnection selected = connection;
        Request request;
        while ((request = API_REQUESTS.poll()) != null) {
            Request captured = request;
            async(() -> {
                if (!current(selected, captured)) {
                    return;
                }
                try {
                    CloudProtocol.Reply reply = captured.operation().run(selected.protocol);
                    selected.post(128 + reply.body().length() * 2, () -> {
                        if (current(selected, captured)) {
                            captured.callback().accept(reply.status(), reply.body());
                        }
                    });
                } catch (Exception exception) {
                    selected.post(128, () -> {
                        if (current(selected, captured)) {
                            captured.callback().accept(status(exception), "");
                        }
                    });
                }
            });
        }
    }

    private static int status(Exception exception) {
        if (exception instanceof ProtobufCloudProtocol.Failure failure) {
            return switch (failure.code) {
                case UNAUTHENTICATED -> 401;
                case FORBIDDEN -> 403;
                case NOT_FOUND -> 404;
                case CONFLICT -> 409;
                case LIMIT_EXCEEDED -> 429;
                default -> 400;
            };
        }
        return 503;
    }

    private static boolean current(CloudConnection selected, Request request) {
        return selected != null && selected == connection && selected.active()
                && request.generation() == requestGenerations.getOrDefault(request.owner(), 0L);
    }

    protected static synchronized void async(Runnable action) {
        if (pendingTasks.incrementAndGet() > 128) {
            pendingTasks.decrementAndGet();
            CloudConnection selected = connection;
            if (selected != null) {
                selected.fail("Cloud request queue requires reconnect");
            } else {
                authFail("Cloud request queue is busy", epochs.get());
            }
            return;
        }
        Runnable bounded = () -> {
            try {
                action.run();
            } finally {
                pendingTasks.decrementAndGet();
            }
        };
        if (tasks == null) {
            tasks = CompletableFuture.runAsync(bounded);
        } else {
            tasks = tasks.handle((ignored, failure) -> null).thenRunAsync(bounded);
        }
    }

    private static boolean checkUUID(UUID owner) {
        return owner.version() < 3;
    }

    public static void auth() {
        disconnect(null);
        authCheck = RECONNECT;
        AuthHandler.auth(false);
    }

    public static void reAuth() {
        disconnect(null);
        authCheck = RECONNECT;
        AuthHandler.auth(true);
    }

    protected static void authSuccess(String token, long epoch) {
        Minecraft.getInstance().execute(() -> {
            if (epoch == epochs.get()) {
                connectAuthenticated(token, epoch);
            }
        });
    }

    protected static void authFail(String reason, long epoch) {
        Minecraft.getInstance().execute(() -> {
            if (epoch == epochs.get()) {
                disconnect(reason);
            }
        });
    }

    public static void connect(String token) {
        disconnect(null);
        connectAuthenticated(token, epochs.get());
    }

    public static void connect(String token, CloudProtocol.Kind kind) {
        disconnect(null);
        connectAuthenticated(token, epochs.get(), kind);
    }

    private static void connectAuthenticated(String token, long epoch) {
        connectAuthenticated(token, epoch, CloudProtocol.Kind.FIGURA_CLOUD);
    }

    private static void connectAuthenticated(String token, long epoch, CloudProtocol.Kind kind) {
        backendStatus = 2;
        disconnectedReason = null;
        URI api = HttpAPI.getUri("");
        URI origin = URI.create(api.getScheme() + "://" + api.getRawAuthority());
        // B6 connects this factory choice to the existing EnumConfig control.
        CloudProtocol protocol = CloudProtocol.create(kind, origin, token, FiguraMod.VERSION.toString(), epoch, client);
        CloudConnection selected = new CloudConnection(epoch, protocol);
        connection = selected;
        async(() -> {
            try {
                protocol.connect(listener(selected));
            } catch (Exception exception) {
                selected.fail(exception instanceof ProtobufCloudProtocol.Failure
                        ? exception.getMessage() : "Cloud connection failed");
            }
        });
    }

    private static CloudProtocol.Listener listener(CloudConnection selected) {
        return new CloudProtocol.Listener() {
            @Override
            public void ready() {
                selected.post(128, () -> {
                    backendStatus = 3;
                    checkVersion();
                    setLimits();
                    fetchMOTD();
                    subscribeAll();
                    for (UUID owner : List.copyOf(SUBSCRIPTIONS)) {
                        AvatarManager.reloadAvatar(owner);
                    }
                    if (Configs.CONNECTION_TOASTS.value) {
                        FiguraToast.sendToast(FiguraText.of("backend.connected"));
                    }
                });
            }

            @Override
            public void ping(UUID owner, int id, boolean echo, byte[] arguments) {
                selected.post(arguments.length + 128, () -> {
                    Avatar avatar = AvatarManager.getLoadedAvatar(owner);
                    if (avatar != null) {
                        avatar.runPing(id, arguments);
                    }
                    pingsReceived++;
                    if (lastPing == 0) {
                        lastPing = FiguraMod.ticks;
                    }
                });
            }

            @Override
            public void changed(UUID owner) {
                selected.post(128, () -> AvatarManager.reloadAvatar(owner));
            }

            @Override
            public void ysm(YsmSyncMessage message) {
                try {
                    int size = YsmSyncCodec.encode(message).length;
                    selected.post(size + 128, () -> {
                        if (ysmEpoch != null && !YSM_SYNC.enqueueTyped(ysmEpoch, message)) {
                            selected.fail("Cloud YSM receive queue requires recovery");
                        }
                    });
                } catch (Exception exception) {
                    selected.fail("Invalid cloud YSM state");
                }
            }

            @Override
            public void snapshotRequested() {
                selected.post(128, YSM_SYNC::requestSnapshot);
            }

            @Override
            public void notification(int kind, int type, String message, String detail) {
                selected.post(128 + (message.length() + detail.length()) * 2, () -> {
                    if (kind == 3 && type < FiguraToast.ToastType.values().length) {
                        FiguraToast.sendToast(message, detail, FiguraToast.ToastType.values()[type]);
                    } else if (kind == 4) {
                        FiguraMod.sendChatMessage(Component.literal("-- " + FiguraMod.MOD_NAME + " backend message --\n\n")
                                .append(TextUtils.tryParseJson(message)));
                    } else if (kind == 5) {
                        YSM_SYNC.requestSnapshot();
                        if (Configs.CONNECTION_TOASTS.value && type <= 1) {
                            FiguraToast.sendToast(FiguraText.of("backend.warning"),
                                    FiguraText.of(type == 0 ? "backend.ping_size" : "backend.ping_rate"),
                                    FiguraToast.ToastType.ERROR);
                        }
                    }
                });
            }

            @Override
            public void closed(String reason) {
                selected.fail(reason);
            }
        };
    }

    public static void disconnect(String reason) {
        epochs.incrementAndGet();
        CloudConnection previous = connection;
        connection = null;
        if (previous != null) {
            previous.close();
        }
        API_REQUESTS.clear();
        requestGenerations.clear();
        ysmEpoch = null;
        backendStatus = 1;
        disconnectedReason = reason;
    }

    private static void queueString(UUID owner, Operation operation, BiConsumer<Integer, String> callback) {
        if (API_REQUESTS.size() >= 128) {
            return;
        }
        API_REQUESTS.add(new Request(owner, requestGenerations.getOrDefault(owner, 0L), operation, callback));
    }

    public static void diagnostic(String path, BiConsumer<Integer, String> callback) {
        queueString(Util.NIL_UUID, protocol -> protocol.diagnostic(path), callback);
    }

    public static void clear(UUID owner) {
        requestGenerations.merge(owner, 1L, Long::sum);
        API_REQUESTS.removeIf(request -> request.owner().equals(owner));
    }

    private static void responseDebug(String operation, int status, String body) {
        if (debug) {
            FiguraMod.debug("Cloud response: operation={}, status={}, characters={}", operation, status,
                    body == null ? 0 : body.length());
        }
    }

    private static void fetchMOTD() {
        queueString(Util.NIL_UUID, CloudProtocol::motd, (code, data) -> {
            if (code == 200) {
                motd = Emojis.applyEmojis(TextUtils.tryParseJson(data));
            }
        });
    }

    public static void checkVersion() {
        queueString(Util.NIL_UUID, CloudProtocol::version, (code, data) -> {
            if (code != 200) {
                return;
            }
            JsonObject json = JsonParser.parseString(data).getAsJsonObject();
            int channel = Configs.UPDATE_CHANNEL.value;
            latestVersion = new Version(json.get(channel <= 1 ? "release" : "prerelease").getAsString());
            if (channel != 0 && latestVersion.compareTo(FiguraMod.VERSION) > 0) {
                FiguraToast.sendToast(FiguraText.of("toast.new_version"), latestVersion);
            }
        });
    }

    public static void setLimits() {
        queueString(Util.NIL_UUID, CloudProtocol::limits, (code, data) -> {
            if (code != 200) {
                return;
            }
            JsonObject json = JsonParser.parseString(data).getAsJsonObject();
            JsonObject rate = json.getAsJsonObject("rate");
            uploadRate.set(rate.get("upload").getAsDouble() * 0.95);
            downloadRate.set(rate.get("download").getAsDouble() * 0.95);
            maxAvatarSize = Math.min((int) CloudHttp.FILE_BYTES,
                    json.getAsJsonObject("limits").get("maxAvatarSize").getAsInt());
        });
    }

    public static void getUser(UserData user) {
        if (checkUUID(user.id))
            return;

        queueString(user.id, api -> api.profile(user.id), (code, data) -> {
            //debug
            responseDebug("getUser", code, data);

            //error
            if (code != 200) {
                if (code == 404 && Configs.CONNECTION_TOASTS.value)
                    FiguraToast.sendToast(FiguraText.of("backend.user_not_found"), FiguraToast.ToastType.ERROR);
                return;
            }

            //success

            JsonObject json = JsonParser.parseString(data).getAsJsonObject();

            //avatars
            ArrayList<Pair<String, Pair<String, UUID>>> avatars = new ArrayList<>();

            JsonArray equippedAvatars = json.getAsJsonArray("equipped");
            for (JsonElement element : equippedAvatars) {
                JsonObject entry = element.getAsJsonObject();
                UUID owner = UUID.fromString(entry.get("owner").getAsString());
                avatars.add(Pair.of(entry.get("hash").getAsString(), Pair.of(entry.get("id").getAsString(), owner)));
            }

            //badges
            JsonObject badges = json.getAsJsonObject("equippedBadges");
            Pair<BitSet, BitSet> badgesPair = Badges.emptyBadges();

            JsonArray pride = badges.getAsJsonArray("pride");
            BitSet prideSet = badgesPair.getFirst();
            for (int i = 0; i < pride.size(); i++)
                prideSet.set(i, pride.get(i).getAsInt() >= 1);

            JsonArray special = badges.getAsJsonArray("special");
            BitSet specialSet = badgesPair.getSecond();
            for (int i = 0; i < special.size(); i++)
                specialSet.set(i, special.get(i).getAsInt() >= 1);

            CloudConnection selected = connection;
            long generation = requestGenerations.getOrDefault(user.id, 0L);
            user.loadData(avatars, badgesPair, action -> {
                try {
                    selected.publishModel(() -> {
                        if (selected == connection && selected.active()
                                && generation == requestGenerations.getOrDefault(user.id, 0L)) {
                            action.run();
                        }
                    });
                } catch (Exception cancelled) {
                    if (selected.active()) {
                        selected.fail("Cached model publication failed");
                    }
                }
            });
        });
    }

    public static void uploadAvatar(Avatar avatar) {
        if (avatar == null || avatar.nbt == null) {
            return;
        }
        try {
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            NbtIo.writeCompressed(YsmSourcePayload.forOutput(avatar.nbt), output);
            CloudProtocol.Metadata metadata = CloudProtocol.Metadata.figura();
            YsmModelRuntime runtime = avatar.getYsmRuntime();
            if (runtime != null) {
                YsmSyncModel model = new YsmSyncModel(runtime);
                metadata = new CloudProtocol.Metadata(runtime.encryptedSource() ? 3 : 2,
                        model.contentHash(), model.schemaHash());
            }
            byte[] bytes = output.toByteArray();
            CloudProtocol.Metadata captured = metadata;
            queueString(Util.NIL_UUID, api -> api.upload("avatar", bytes, captured), (code, data) -> {
                if (code == 200) {
                    equipAvatar(List.of(Pair.of(avatar.owner, "avatar")));
                }
                String key = switch (code) {
                    case 200 -> "backend.upload_success";
                    case 413 -> "backend.upload_too_big";
                    case 507 -> "backend.upload_too_many";
                    default -> "backend.upload_error";
                };
                FiguraToast.sendToast(FiguraText.of(key), code == 200
                        ? FiguraToast.ToastType.DEFAULT : FiguraToast.ToastType.ERROR);
            });
            uploadRate.use();
        } catch (Exception exception) {
            FiguraMod.LOGGER.error("Failed to prepare cloud model upload");
        }
    }

    public static void deleteAvatar(String avatar) {
        queueString(Util.NIL_UUID, api -> api.delete("avatar"), (code, data) -> {
            String key = code == 200 ? "backend.delete_success"
                    : code == 404 ? "backend.avatar_not_found" : "backend.delete_error";
            FiguraToast.sendToast(FiguraText.of(key), code == 200
                    ? FiguraToast.ToastType.DEFAULT : FiguraToast.ToastType.ERROR);
        });
    }

    public static void equipAvatar(List<Pair<UUID, String>> avatars) {
        List<UUID> owners = avatars.stream().map(Pair::getFirst).toList();
        List<String> ids = avatars.stream().map(Pair::getSecond).toList();
        queueString(Util.NIL_UUID, api -> api.equip(owners, ids), (code, data) -> {
            if (code == 200) {
                AvatarManager.localUploaded = !avatars.isEmpty();
                YSM_SYNC.requestSnapshot();
            } else if (Configs.CONNECTION_TOASTS.value) {
                FiguraToast.sendToast(FiguraText.of("backend.equip_error"), FiguraToast.ToastType.ERROR);
            }
        });
    }

    public static void getAvatar(UserData target, UUID owner, String id, String cacheKey) {
        if (checkUUID(target.id)) {
            return;
        }
        queueString(target.id, protocol -> {
            CloudConnection selected = connection;
            if (selected == null || selected.protocol != protocol || !selected.active()) {
                throw new java.io.IOException("Retired cloud download");
            }
            long requestGeneration = requestGenerations.getOrDefault(target.id, 0L);
            try (CloudProtocol.Download download = protocol.download(owner, id)) {
                CloudProtocol.Resource resource = download.resource();
                if (!resource.cacheKey().equals(cacheKey)) {
                    throw new java.io.IOException("Stale resource profile");
                }
                byte[] bytes = CloudHttp.readBounded(download.stream(), (int) CloudHttp.FILE_BYTES);
                if (resource.length() >= 0 && resource.length() != bytes.length) {
                    throw new java.io.IOException("Resource length mismatch");
                }
                String expected = resource.rawHash();
                String actual = expected.startsWith("legacy:")
                        ? CloudHttp.sha256(Base64.getEncoder().encode(bytes)) : CloudHttp.sha256(bytes);
                if (!actual.equals(expected.replaceFirst("^legacy:", ""))) {
                    throw new java.io.IOException("Resource hash mismatch");
                }
                CompoundTag nbt = NbtIo.readCompressed(new ByteArrayInputStream(bytes), NbtAccounter.create(256L * 1024 * 1024));
                selected.publishModel(() -> {
                    if (selected == connection && selected.active()
                            && requestGeneration == requestGenerations.getOrDefault(target.id, 0L)) {
                        CacheAvatarLoader.save(cacheKey, nbt);
                        target.loadAvatar(nbt);
                    }
                });
            }
            return new CloudProtocol.Reply(200, "");
        }, (code, data) -> responseDebug("download", code, data));
        downloadRate.use();
    }

    public static void sendPing(int id, boolean echo, byte[] data) {
        CloudConnection selected = connection;
        if (!AvatarManager.localUploaded || !isConnected()) {
            return;
        }
        try {
            selected.protocol.ping(id, echo, data);
            pingsSent++;
            if (lastPing == 0) {
                lastPing = FiguraMod.ticks;
            }
        } catch (Exception exception) {
            selected.fail("Cloud Ping failed");
        }
    }

    private static void subscribe(UUID owner) {
        if (!checkUUID(owner) && isConnected()) {
            queueString(Util.NIL_UUID, protocol -> {
                protocol.subscribe(owner);
                return new CloudProtocol.Reply(200, "");
            }, (code, data) -> responseDebug("subscribe", code, data));
        }
    }

    private static void unsubscribe(UUID owner) {
        if (!checkUUID(owner) && isConnected()) {
            queueString(Util.NIL_UUID, protocol -> {
                protocol.unsubscribe(owner);
                return new CloudProtocol.Reply(200, "");
            }, (code, data) -> responseDebug("unsubscribe", code, data));
        }
    }

    public static void subscribeAll() {
        SUBSCRIPTIONS.forEach(NetworkStuff::subscribe);
    }

    public static void unsubscribeAll() {
        SUBSCRIPTIONS.forEach(NetworkStuff::unsubscribe);
        SUBSCRIPTIONS.clear();
    }

    // -- resources stuff -- //


    private static InputStream request(HttpRequest request) throws Exception {
        HttpResponse<InputStream> response = NetworkStuff.client.send(request, HttpResponse.BodyHandlers.ofInputStream());
        return response.body();
    }

    public static InputStream getResourcesHashes(String version) throws Exception {
        return request(HttpRequest.newBuilder(HttpAPI.getUri("/assets/" + version)).timeout(Duration.ofSeconds(15)).build());
    }

    public static InputStream getResource(String version, String resource) throws Exception {
        return request(HttpRequest.newBuilder(HttpAPI.getUri("/assets/" + version + "/" + resource)).build());
    }


    public static boolean isConnected() {
        CloudConnection current = connection;
        return current != null && current.active() && current.protocol.connected() && backendStatus == 3;
    }

    public static boolean canUpload() {
        return isConnected() && uploadRate.check();
    }

    public static int getSizeLimit() {
        return isConnected() ? maxAvatarSize : Integer.MAX_VALUE;
    }
}
