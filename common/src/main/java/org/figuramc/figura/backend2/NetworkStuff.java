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
import top.aihmc.omnimorph.avatar.AvatarLoadProgress;
import org.figuramc.figura.avatar.AvatarManager;
import org.figuramc.figura.avatar.Badges;
import org.figuramc.figura.avatar.UserData;
import org.figuramc.figura.avatar.local.CacheAvatarLoader;
import top.aihmc.omnimorph.avatar.ysm.YsmSourcePayload;
import top.aihmc.omnimorph.backend2.protocol.CloudConnection;
import top.aihmc.omnimorph.backend2.protocol.CloudEndpoint;
import top.aihmc.omnimorph.backend2.protocol.CloudHttp;
import top.aihmc.omnimorph.backend2.protocol.CloudProtocol;
import top.aihmc.omnimorph.backend2.protocol.CloudReconnect;
import top.aihmc.omnimorph.backend2.protocol.FiguraCloudProtocol;
import top.aihmc.omnimorph.backend2.protocol.ProtobufCloudProtocol;
import top.aihmc.omnimorph.backend2.library.LibraryClient;
import top.aihmc.omnimorph.backend2.library.LibraryModels;
import top.aihmc.omnimorph.backend2.library.LibrarySession;
import top.aihmc.omnimorph.backend2.library.OutfitEvents;
import org.figuramc.figura.config.Configs;
import org.figuramc.figura.font.Emojis;
import org.figuramc.figura.gui.FiguraToast;
import top.aihmc.omnimorph.model.library.LibraryCollectionService;
import top.aihmc.omnimorph.model.library.OutfitObserverService;
import top.aihmc.omnimorph.model.library.OutfitApplyService;
import top.aihmc.omnimorph.model.library.LibraryTransferService;
import top.aihmc.omnimorph.model.library.OutfitInstanceSync;
import top.aihmc.omnimorph.model.ysm.YsmModelRuntime;
import top.aihmc.omnimorph.model.ysm.sync.YsmSyncCodec;
import top.aihmc.omnimorph.model.ysm.sync.YsmSyncConnection;
import top.aihmc.omnimorph.model.ysm.sync.YsmSyncMessage;
import top.aihmc.omnimorph.model.ysm.sync.YsmSyncModel;
import top.aihmc.omnimorph.model.ysm.sync.YsmSyncPackets;
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
    private static final AtomicLong localSelections = new AtomicLong();
    private static volatile Avatar localBroadcastAvatar;
    private static volatile boolean localBroadcastPending;
    private static volatile LibrarySelection activeLibrarySelection;
    private static final AvatarLoadProgress uploadProgress = new AvatarLoadProgress();
    private static CompletableFuture<Void> tasks;
    private static final java.util.concurrent.atomic.AtomicInteger pendingTasks = new java.util.concurrent.atomic.AtomicInteger();
    private static volatile CloudConnection connection;
    private static LibraryClient libraryClient;
    private static LibraryCollectionService wardrobeCollection;
    private static OutfitEventCoordinator outfitEvents;
    private static final Map<UUID, ObserverLoad> outfitObservers = new ConcurrentHashMap<>();
    private static final Map<UUID, Long> outfitObserverRetries = new ConcurrentHashMap<>();
    private static volatile CloudHttp authenticationWork;
    private static volatile CloudHttp resourceWork;
    private static volatile CloudProtocol.Kind connectedKind;
    private static volatile String connectedAddress;
    private static final int RECONNECT = 6000;
    private static int authCheck = RECONNECT;
    private static final top.aihmc.omnimorph.backend2.protocol.CloudReconnect reconnect =
            new top.aihmc.omnimorph.backend2.protocol.CloudReconnect();
    private static volatile boolean authenticationPending;
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

    private record ObserverLoad(UserData user, net.minecraft.world.entity.LivingEntity wearer, Object world,
                                CloudConnection connection, long generation, OutfitObserverService service) {
        private boolean current() {
            return outfitObservers.get(user.id) == this && NetworkStuff.connection == connection && connection.active()
                    && SUBSCRIPTIONS.contains(user.id) && Minecraft.getInstance().level == world
                    && AvatarManager.getLoadedUser(user.id) == user && !wearer.isRemoved()
                    && generation == requestGenerations.getOrDefault(user.id, 0L);
        }
    }

    private static boolean observeOutfit(UserData user, boolean refresh) {
        if (!isConnected() || FiguraMod.isLocal(user.id) || !SUBSCRIPTIONS.contains(user.id)) {
            return false;
        }
        var entity = org.figuramc.figura.utils.EntityUtils.getEntityByUUID(user.id);
        if (!(entity instanceof net.minecraft.world.entity.LivingEntity wearer) || wearer.isRemoved()) {
            return false;
        }
        ObserverLoad previous = outfitObservers.get(user.id);
        if (!refresh && previous != null && previous.current() && previous.wearer() == wearer) {
            return true;
        }
        LibraryClient library;
        try {
            library = libraryClient();
        } catch (java.io.IOException unavailable) {
            return false;
        }
        // Retire any earlier profile/body callbacks before this preparation begins its native generation.
        clear(user.id);
        var service = new OutfitObserverService(library, FiguraMod.getCacheDirectory());
        var selected = new ObserverLoad(user, wearer, Minecraft.getInstance().level, connection,
                requestGenerations.getOrDefault(user.id, 0L), service);
        outfitObservers.put(user.id, selected);
        service.refresh(user, wearer, selected::current).future().whenComplete((result, failure) -> {
            if (!selected.current()) {
                return;
            }
            if (failure == null && result == OutfitObserverService.Result.LEGACY) {
                getProfileUser(user, true);
            } else if (failure != null && !(failure instanceof java.util.concurrent.CancellationException)) {
                if (retryObserver(failure)) {
                    outfitObserverRetries.put(user.id, Integer.toUnsignedLong(FiguraMod.ticks) + 20);
                }
                FiguraMod.debug("Observer outfit preparation failed for {}", user.id);
            }
        });
        getProfileUser(user, false);
        return true;
    }

    private static void retireObserver(UUID owner, boolean clearResident) {
        outfitObserverRetries.remove(owner);
        ObserverLoad previous = outfitObservers.remove(owner);
        if (previous != null) {
            previous.service().close();
            if (clearResident) {
                requestGenerations.merge(owner, 1L, Long::sum);
                API_REQUESTS.removeIf(request -> request.owner().equals(owner));
            }
            if (clearResident && previous.user().getOutfit() != null) {
                previous.user().clear();
            }
        }
    }

    private static void retireObservers() {
        for (UUID owner : List.copyOf(outfitObservers.keySet())) {
            retireObserver(owner, true);
        }
    }

    private static void tickOutfitObservers() {
        for (ObserverLoad selected : List.copyOf(outfitObservers.values())) {
            if (!selected.current() || org.figuramc.figura.utils.EntityUtils.getEntityByUUID(selected.user().id) != selected.wearer()) {
                retireObserver(selected.user().id, true);
            } else {
                Long retry = outfitObserverRetries.get(selected.user().id);
                if (retry != null && Integer.toUnsignedLong(FiguraMod.ticks) >= retry) {
                    retireObserver(selected.user().id, false);
                }
            }
        }
        if (isConnected()) {
            int started = 0;
            for (UUID owner : List.copyOf(SUBSCRIPTIONS)) {
                UserData user = AvatarManager.getLoadedUser(owner);
                if (user != null && !outfitObservers.containsKey(owner)) {
                    if (observeOutfit(user, false) && ++started == 2) {
                        break;
                    }
                }
            }
        }
    }

    private static boolean retryObserver(Throwable failure) {
        while (failure instanceof java.util.concurrent.CompletionException && failure.getCause() != null) {
            failure = failure.getCause();
        }
        return failure instanceof LibraryClient.Failure request && (request.status == 429 || request.status >= 500
                || Set.of("LIBRARY_BUSY", "LIBRARY_UNAVAILABLE", "LIBRARY_REQUEST_FAILED").contains(request.code));
    }

    /** The event stream carries invalidations; existing observer operations load authoritative snapshots. */
    private static final class OutfitEventCoordinator implements OutfitEvents.Listener, AutoCloseable {
        private final LibraryClient client;
        private final CloudConnection selected = connection;
        private final OutfitEvents events;
        private final OutfitInstanceSync instanceSync;
        private LibraryClient.Request<LibraryModels.Capabilities> pending;
        private LibraryModels.Capabilities capabilities;
        private long capabilityGeneration;
        private long retryAt;
        private Object world = Minecraft.getInstance().level;
        private Object ownerWorld;
        private boolean ownerDirty = true;
        private boolean ownerLoading;
        private final Set<String> ownerTraced = new java.util.HashSet<>();
        private long ownerRetryAt;
        private long ownerGeneration;
        private LibraryClient.Request<?> ownerRead;
        private OutfitApplyService ownerRestore;

        private OutfitEventCoordinator(LibraryClient client) {
            this.client = client;
            FiguraMod.LOGGER.info("Outfit owner restore coordinator started");
            events = client.outfitEvents(this);
            instanceSync = new OutfitInstanceSync(client, owner -> {
                if (FiguraMod.isLocal(owner)) {
                    UserData user = AvatarManager.getLoadedUser(owner);
                    if (user != null && user.getOutfit() != null) {
                        user.clear();
                    }
                    ownerWorld = null;
                }
                changed(owner);
            });
        }

        private boolean current() {
            return outfitEvents == this && libraryClient == client && client.current()
                    && connection == selected && isConnected();
        }

        private void tick() {
            if (world != Minecraft.getInstance().level) {
                world = Minecraft.getInstance().level;
                instanceSync.close();
                retireObservers();
                retireOwner();
                ownerDirty = true;
            }
            long tick = Integer.toUnsignedLong(FiguraMod.ticks);
            if (capabilities == null && pending == null && tick >= retryAt) {
                long generation = ++capabilityGeneration;
                pending = client.capabilities();
                pending.future().whenComplete((value, failure) -> {
                    if (!current() || generation != capabilityGeneration) {
                        return;
                    }
                    pending = null;
                    if (failure == null) {
                        capabilities = value;
                        var outfits = value.outfitsV1();
                        if (!value.enabled() || outfits == null || !outfits.configuration() || !outfits.apply()) {
                            retireObservers();
                        }
                    } else {
                        retryAt = Integer.toUnsignedLong(FiguraMod.ticks) + 20;
                        if (!retryObserver(failure)) {
                            capabilities = LibraryModels.Capabilities.unsupported();
                            retireObservers();
                        }
                    }
                });
            }
            Set<UUID> owners = world == null ? Set.of() : Set.copyOf(SUBSCRIPTIONS);
            events.tick(Util.getMillis(), capabilities, owners);
            restoreOwner(tick);
            var configured = capabilities == null ? null : capabilities.outfitsV1();
            if (world != null && configured != null && configured.apply() && configured.events()) {
                instanceSync.tick(tick, owners);
            }
        }

        private void restoreOwner(long tick) {
            var outfits = capabilities == null ? null : capabilities.outfitsV1();
            if (!ownerDirty || ownerLoading || tick < ownerRetryAt || world == null) {
                return;
            }
            if (outfits == null || !outfits.apply() || !outfits.configuration()) {
                ownerTrace("server outfit capabilities unavailable");
                return;
            }
            if (activeLibrarySelection != null && librarySelectionCurrent(activeLibrarySelection)) {
                ownerTrace("a library selection is still active");
                return;
            }
            if (org.figuramc.figura.avatar.local.LocalAvatarLoader.getLastLoadedPath() != null) {
                ownerTrace("a local avatar file is selected");
                return;
            }
            var wearer = Minecraft.getInstance().player;
            if (wearer == null || !wearer.getUUID().equals(client.session().player())) {
                ownerTrace("the local player is not the authenticated account");
                return;
            }
            ownerTraced.clear();
            long generation = ++ownerGeneration;
            long selection = localSelections.get();
            Object selectedWorld = world;
            ownerDirty = false;
            ownerLoading = true;
            ownerRead = client.currentOutfit();
            @SuppressWarnings("unchecked")
            var read = (LibraryClient.Request<top.aihmc.omnimorph.model.outfit.OutfitConfig>) ownerRead;
            read.future().whenComplete((configuration, failure) -> {
                if (!current() || generation != ownerGeneration || selectedWorld != Minecraft.getInstance().level) {
                    return;
                }
                ownerRead = null;
                if (failure != null) {
                    ownerFinished(generation, failure);
                    return;
                }
                if (selection != localSelections.get() || configuration.revision() == 0) {
                    FiguraMod.LOGGER.warn("Outfit owner restore skipped: {}", configuration.revision() == 0
                            ? "the server has no committed outfit" : "the local selection changed during the read");
                    ownerLoading = false;
                    return;
                }
                UserData user = AvatarManager.getLoadedUser(wearer.getUUID());
                if (ownerWorld == selectedWorld && user != null && user.getOutfit() != null && user.getOutfit().configuration().equals(configuration)
                        && user.getOutfit().composition().limits().equals(outfits.limits())) {
                    FiguraMod.LOGGER.info("Outfit owner restore skipped: the committed outfit is already loaded");
                    ownerLoading = false;
                    return;
                }
                FiguraMod.LOGGER.info("Outfit owner restore started: revision {}, {} instances",
                        configuration.revision(), configuration.instances().size());
                if (ownerRestore != null) {
                    ownerRestore.close();
                }
                ownerRestore = new OutfitApplyService(client,
                        new LibraryTransferService(client, FiguraMod.getCacheDirectory()),
                        FiguraMod.getFiguraDirectory(), FiguraMod.getCacheDirectory());
                ownerRestore.restore(configuration, wearer,
                        () -> current() && generation == ownerGeneration && selectedWorld == Minecraft.getInstance().level)
                        .whenComplete((restored, error) -> {
                            if (current() && generation == ownerGeneration && error == null) {
                                ownerWorld = selectedWorld;
                                FiguraMod.LOGGER.info("Outfit owner restore completed");
                            }
                            ownerFinished(generation, error);
                        });
            });
        }

        /** Diagnostic only: each pending-restore reason is logged once until a restore read starts. */
        private void ownerTrace(String reason) {
            if (ownerTraced.add(reason)) {
                FiguraMod.LOGGER.warn("Outfit owner restore waiting: {}", reason);
            }
        }

        private void ownerFinished(long generation, Throwable failure) {
            if (!current() || generation != ownerGeneration) {
                return;
            }
            ownerLoading = false;
            while (failure instanceof java.util.concurrent.CompletionException && failure.getCause() != null) {
                failure = failure.getCause();
            }
            if (failure != null) {
                FiguraMod.LOGGER.warn("Outfit owner restore failed: {}", failure instanceof LibraryClient.Failure typed
                        ? typed.code : failure.getClass().getSimpleName() + ": " + failure.getMessage());
            }
            if (failure != null && (retryObserver(failure)
                    || failure instanceof LibraryClient.Failure changed
                    && Set.of("OUTFIT_CHANGED", "OUTFIT_LIMITS_CHANGED").contains(changed.code))) {
                ownerDirty = true;
                ownerRetryAt = Integer.toUnsignedLong(FiguraMod.ticks) + 20;
            }
        }

        private void retireOwner() {
            ++ownerGeneration;
            if (ownerRead != null) {
                ownerRead.cancel();
                ownerRead = null;
            }
            if (ownerRestore != null) {
                ownerRestore.close();
                ownerRestore = null;
            }
            ownerLoading = false;
        }

        @Override
        public void changed(UUID owner) {
            changed(owner, -1, -1);
        }

        @Override
        public void changed(UUID owner, long revision, long equipmentRevision) {
            if (!current()) {
                return;
            }
            if (FiguraMod.isLocal(owner)) {
                ownerDirty = true;
                return;
            }
            if (!SUBSCRIPTIONS.contains(owner)) {
                return;
            }
            UserData user = AvatarManager.getLoadedUser(owner);
            var resident = user == null ? null : user.getOutfit();
            if (resident != null && revision >= 0 && revision < resident.configuration().revision()) {
                return;
            }
            // Same-revision notifications can revoke policy without changing configuration.
            boolean invalidate = resident != null && revision <= resident.configuration().revision();
            retireObserver(owner, invalidate);
            if (invalidate && user.getOutfit() != null) {
                user.clear();
            }
            // The existing tick starts at most two missing observers, including late entities.
        }

        @Override
        public void reset() {
            if (current()) {
                instanceSync.close();
                retireObservers();
                retireOwner();
                ownerDirty = true;
            }
        }

        @Override
        public void limitsChanged() {
            if (!current()) {
                return;
            }
            ++capabilityGeneration;
            instanceSync.close();
            if (pending != null) {
                pending.cancel();
                pending = null;
            }
            capabilities = null;
            retryAt = 0;
            retireObservers();
            retireOwner();
            ownerDirty = true;
        }

        @Override
        public void close() {
            instanceSync.close();
            retireOwner();
            ++capabilityGeneration;
            if (pending != null) {
                pending.cancel();
                pending = null;
            }
            events.close();
        }

        @Override
        public void stateChanged(UUID owner, UUID instance, long outfitRevision, long stateRevision) {
            if (current()) {
                instanceSync.changed(owner, instance, outfitRevision, stateRevision);
            }
        }
    }

    private static void retireOutfitEvents() {
        OutfitEventCoordinator previous = outfitEvents;
        outfitEvents = null;
        if (previous != null) {
            previous.close();
        }
    }

    private static void tickOutfitEvents() {
        if (!isConnected()) {
            retireOutfitEvents();
            retireObservers();
            return;
        }
        try {
            LibraryClient selected = libraryClient();
            if (outfitEvents == null || !outfitEvents.current()) {
                retireOutfitEvents();
                outfitEvents = new OutfitEventCoordinator(selected);
            }
            outfitEvents.tick();
        } catch (java.io.IOException unavailable) {
            // The selected adapter's existing connection lifecycle owns authentication and reconnect.
        }
    }

    public static long connectionEpoch() {
        return epochs.get();
    }

    /** Called on the client thread; one library service belongs to the current cloud connection. */
    public static synchronized LibraryClient libraryClient() throws java.io.IOException {
        if (!isConnected()) {
            throw new java.io.IOException("LIBRARY_OFFLINE");
        }
        if (libraryClient == null || !libraryClient.current()) {
            retireObservers();
            retireOutfitEvents();
            if (wardrobeCollection != null) {
                wardrobeCollection.close();
                wardrobeCollection = null;
            }
            if (libraryClient != null) {
                libraryClient.close();
            }
            CloudConnection selected = connection;
            UUID player = FiguraMod.getLocalPlayerUUID();
            var scope = new LibrarySession(
                    selected.protocol.instance(), player, selected.epoch,
                    () -> connection == selected && selected.active() && selected.protocol.connected()
                            && player.equals(FiguraMod.getLocalPlayerUUID()));
            libraryClient = new LibraryClient(
                    selected.protocol.libraryAccess(), scope, Minecraft.getInstance()::execute);
        }
        return libraryClient;
    }

    public static synchronized LibraryCollectionService wardrobeCollection()
            throws java.io.IOException {
        LibraryClient client = libraryClient();
        if (wardrobeCollection == null) {
            wardrobeCollection = new LibraryCollectionService(
                    client, FiguraMod.getCacheDirectory());
            wardrobeCollection.refresh();
        }
        return wardrobeCollection;
    }

    public static void tick() {
        if (wardrobeCollection != null && (libraryClient == null || !libraryClient.current())) {
            wardrobeCollection.close();
            wardrobeCollection = null;
        }
        if (libraryClient != null && !libraryClient.current()) {
            retireObservers();
            retireOutfitEvents();
            libraryClient.close();
            libraryClient = null;
        }
        uploadRate.tick();
        if (Configs.WARDROBE_AUTO_CACHE.value && isConnected() && wardrobeCollection == null) {
            try {
                wardrobeCollection();
            } catch (java.io.IOException unavailable) {
                // The existing connection lifecycle controls reconnect and retries.
            }
        }
        if (wardrobeCollection != null) {
            wardrobeCollection.tick();
        }
        downloadRate.tick();
        if (!endpointPermitted()) {
            authCheck = RECONNECT;
            if (connection != null || authenticationWork != null || resourceWork != null) {
                disconnect(disconnectedReason);
            }
            tickYsmSync();
            return;
        }
        CloudConnection current = connection;
        if (current != null && (connectedKind != Configs.CLOUD_PROTOCOL.kind()
                || !Objects.equals(connectedAddress, Configs.SERVER_IP.value))) {
            reAuth();
            return;
        }
        if (current != null) {
            if (current.failure() != null) {
                String reason = current.failure();
                connectionLost(reason);
                if (Configs.CONNECTION_TOASTS.value) {
                    FiguraToast.sendToast(FiguraText.of("backend.disconnected"), Component.literal(reason), FiguraToast.ToastType.ERROR);
                }
            } else {
                current.drain();
            }
        }
        if (--authCheck <= 0) {
            authCheck = RECONNECT;
            if (isConnected()) {
                queueString(Util.NIL_UUID, CloudProtocol::checkAuth, (code, data) -> {
                    if (code != 200) {
                        reAuth();
                    }
                });
            }
        }
        if (reconnect.tick(authenticationPending || authenticationWork != null || connection != null)) {
            retire(null, false);
            authenticationPending = true;
            AuthHandler.auth(true);
        }
        tickSubscriptions();
        tickOutfitEvents();
        tickOutfitObservers();
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
        // D025: share the local player's display values; the protocol drops them where unsupported.
        YSM_SYNC.setDisplaySource(() -> minecraft.player == null || !Configs.SHARE_YSM_DISPLAY.value ? null
                : top.aihmc.omnimorph.model.ysm.YsmDisplayQuery.of(minecraft.player));
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
        reconnect.start();
        authCheck = RECONNECT;
        if (endpointPermitted()) {
            authenticationPending = true;
            AuthHandler.auth(false);
        }
    }

    public static void reAuth() {
        disconnect(null);
        reconnect.start();
        authCheck = RECONNECT;
        if (endpointPermitted()) {
            authenticationPending = true;
            AuthHandler.auth(true);
        }
    }

    private static boolean endpointPermitted() {
        try {
            HttpAPI.endpoint().requireAllowed();
            return true;
        } catch (Exception rejected) {
            String key = "backend.endpoint.debug_required".equals(rejected.getMessage())
                    ? "backend.endpoint.debug_required" : "backend.endpoint.invalid";
            disconnectedReason = FiguraText.of(key).getString();
            backendStatus = 1;
            return false;
        }
    }

    public static void debugPermissionChanged() {
        try {
            if (HttpAPI.endpoint().plaintext()) {
                if (Configs.DEBUG_MODE.value) {
                    reAuth();
                } else {
                    disconnect(FiguraText.of("backend.endpoint.debug_required").getString());
                    authCheck = RECONNECT;
                }
            }
        } catch (Exception rejected) {
            disconnect(FiguraText.of("backend.endpoint.invalid").getString());
        }
    }

    static synchronized boolean registerAuthentication(long epoch, CloudHttp work) {
        if (epoch != epochs.get()) {
            work.close();
            return false;
        }
        if (authenticationWork != null) {
            authenticationWork.close();
        }
        authenticationWork = work;
        return true;
    }

    static synchronized void finishAuthentication(CloudHttp work) {
        if (authenticationWork == work) {
            authenticationWork = null;
        }
        work.close();
    }

    protected static void authSuccess(String token, long epoch, CloudEndpoint endpoint) {
        Minecraft.getInstance().execute(() -> {
            try {
                endpoint.requireAllowed();
                if (epoch == epochs.get()) {
                    connectAuthenticated(token, epoch);
                }
            } catch (java.io.IOException retired) {
                // A late token belongs exclusively to the endpoint and policy that requested it.
            }
        });
    }

    protected static void authFail(String reason, long epoch) {
        Minecraft.getInstance().execute(() -> {
            if (epoch == epochs.get()) {
                String message = reason != null && (reason.startsWith("backend.protobuf.")
                        || reason.startsWith("backend.endpoint.") || reason.equals("backend.auth.failed"))
                        ? FiguraText.of(reason).getString() : reason;
                connectionLost(message);
                if (Configs.CONNECTION_TOASTS.value && message != null) {
                    FiguraToast.sendToast(FiguraText.of("backend.disconnected"), Component.literal(message), FiguraToast.ToastType.ERROR);
                }
            }
        });
    }

    public static void connect(String token) {
        disconnect(null);
        reconnect.start();
        connectAuthenticated(token, epochs.get());
    }

    public static void connect(String token, CloudProtocol.Kind kind) {
        disconnect(null);
        reconnect.start();
        connectAuthenticated(token, epochs.get(), kind);
    }

    private static void connectAuthenticated(String token, long epoch) {
        connectAuthenticated(token, epoch, Configs.CLOUD_PROTOCOL.kind());
    }

    private static void connectAuthenticated(String token, long epoch, CloudProtocol.Kind kind) {
        authenticationPending = false;
        backendStatus = 2;
        disconnectedReason = null;
        if (!endpointPermitted()) {
            return;
        }
        CloudEndpoint endpoint = HttpAPI.endpoint(epoch);
        CloudProtocol protocol = CloudProtocol.create(kind, endpoint, token, FiguraMod.VERSION.toString(), epoch, client);
        CloudConnection selected = new CloudConnection(epoch, protocol);
        connectedKind = kind;
        connectedAddress = Configs.SERVER_IP.value;
        connection = selected;
        async(() -> {
            try {
                protocol.connect(listener(selected));
            } catch (Exception exception) {
                if (kind == CloudProtocol.Kind.PROTOBUF) {
                    String key = exception instanceof ProtobufCloudProtocol.Unsupported
                            || exception instanceof ProtobufCloudProtocol.Failure failure
                                    && failure.code == top.aihmc.omnimorph.backend2.protocol.generated.Cloud.ErrorCode.UNSUPPORTED_CAPABILITY
                            ? "backend.protobuf.unsupported" : "backend.protobuf.failed";
                    selected.fail(FiguraText.of(key).getString());
                } else {
                    selected.fail("Cloud connection failed");
                }
            }
        });
    }

    private static CloudProtocol.Listener listener(CloudConnection selected) {
        return new CloudProtocol.Listener() {
            @Override
            public void ready() {
                selected.post(128, () -> {
                    reconnect.connected();
                    backendStatus = 3;
                    checkVersion();
                    setLimits();
                    fetchMOTD();
                    subscribeAll();
                    broadcastLocalAvatar();
                    for (UUID owner : List.copyOf(SUBSCRIPTIONS)) {
                        if (!FiguraMod.isLocal(owner) || org.figuramc.figura.avatar.local.LocalAvatarLoader.getLastLoadedPath() == null) {
                            AvatarManager.reloadAvatar(owner);
                        }
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
                selected.post(128, () -> {
                    boolean formalPublication = FiguraMod.isLocal(owner)
                            && librarySelectionCurrent(activeLibrarySelection);
                    if (!formalPublication && (!FiguraMod.isLocal(owner)
                            || org.figuramc.figura.avatar.local.LocalAvatarLoader.getLastLoadedPath() == null)) {
                        AvatarManager.reloadAvatar(owner);
                    }
                });
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

    public static synchronized void disconnect(String reason) {
        retire(reason, true);
    }

    public static synchronized void connectionLost(String reason) {
        retire(reason, false);
        reconnect.failed();
    }

    private static synchronized void retire(String reason, boolean stopRetry) {
        retireObservers();
        retireOutfitEvents();
        if (wardrobeCollection != null) {
            wardrobeCollection.close();
            wardrobeCollection = null;
        }
        if (stopRetry) {
            reconnect.stop();
        }
        authenticationPending = false;
        if (libraryClient != null) {
            libraryClient.close();
            libraryClient = null;
        }
        uploadProgress.clear();
        AvatarManager.clearNetworkLoadingProgress();
        epochs.incrementAndGet();
        if (authenticationWork != null) {
            authenticationWork.close();
            authenticationWork = null;
        }
        if (resourceWork != null) {
            resourceWork.close();
            resourceWork = null;
        }
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

    private static boolean queueString(UUID owner, Operation operation, BiConsumer<Integer, String> callback) {
        if (API_REQUESTS.size() >= 128) {
            return false;
        }
        API_REQUESTS.add(new Request(owner, requestGenerations.getOrDefault(owner, 0L), operation, callback));
        return true;
    }

    public static void diagnostic(String path, BiConsumer<Integer, String> callback) {
        queueString(Util.NIL_UUID, protocol -> protocol.diagnostic(path), callback);
    }

    public static void clear(UUID owner) {
        retireObserver(owner, false);
        AvatarManager.clearLoadingProgress(owner);
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

        if (!observeOutfit(user, true)) {
            getProfileUser(user, true);
        }
    }

    private static void getProfileUser(UserData user, boolean equipment) {

        long progress = user.loading.begin(top.aihmc.omnimorph.avatar.AvatarLoadProgress.Stage.PROFILE);
        boolean queued = queueString(user.id, api -> api.profile(user.id), (code, data) -> {
            try {
                //debug
                responseDebug("getUser", code, data);

                //error
                if (code != 200) {
                    if (code == 404) {
                        // A player without a cloud profile has no equipped model.
                        if (equipment) {
                            user.clear();
                        }
                        user.loadBadges(Badges.emptyBadges());
                        if (equipment && FiguraMod.isLocal(user.id)) {
                            AvatarManager.localUploaded = false;
                        }
                    }
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
                if (equipment) {
                    user.loadData(avatars, badgesPair, () -> selected == connection && selected.active()
                            && generation == requestGenerations.getOrDefault(user.id, 0L));
                } else {
                    user.loadBadges(badgesPair);
                }
                if (equipment && FiguraMod.isLocal(user.id)
                        && org.figuramc.figura.avatar.local.LocalAvatarLoader.getLastLoadedPath() == null) {
                    AvatarManager.localUploaded = !avatars.isEmpty();
                }
            } finally {
                user.loading.finish(progress);
            }
        });
        if (!queued) {
            user.loading.finish(progress);
        }
    }

    public static void uploadAvatar(Avatar avatar) {
        if (avatar != null && avatar.nbt != null) {
            localAvatarReady(avatar);
            uploadRate.use();
        }
    }

    public static void localSelectionChanged() {
        CloudConnection current = connection;
        if (current != null && current.active()) {
            current.protocol.localSelectionChanged();
        }
        uploadProgress.clear();
        localSelections.incrementAndGet();
        localBroadcastAvatar = null;
        localBroadcastPending = false;
        activeLibrarySelection = null;
    }

    /** Opaque guard for a formal library equip request within one connection and local selection. */
    public static final class LibrarySelection {
        private final CloudConnection connection;
        private final UUID player;
        private final long selection;

        private LibrarySelection(CloudConnection connection, UUID player, long selection) {
            this.connection = connection;
            this.player = player;
            this.selection = selection;
        }
    }

    /**
     * Called on the client thread before submitting a library equip request. Retire pending local
     * broadcasts and preparations while keeping the current visible model if the request fails.
     */
    public static LibrarySelection beginLibrarySelection() {
        if (!isConnected()) {
            throw new IllegalStateException("Cloud connection is unavailable");
        }
        localSelectionChanged();
        UUID player = FiguraMod.getLocalPlayerUUID();
        AvatarManager.cancelPendingLocalLoad();
        clear(player);
        LibrarySelection selection = new LibrarySelection(connection, player, localSelections.get());
        activeLibrarySelection = selection;
        return selection;
    }

    /** May also be checked by the background request before committing equipment. */
    public static boolean librarySelectionCurrent(LibrarySelection selection) {
        return selection != null && selection == activeLibrarySelection && selection.connection == connection
                && selection.connection.active() && selection.connection.epoch == epochs.get()
                && selection.selection == localSelections.get()
                && selection.player.equals(FiguraMod.getLocalPlayerUUID());
    }

    /**
     * Called on the client thread only after the library endpoint confirms equip success. Load the
     * server's source alias through the normal profile/download path; never upload it as "avatar".
     */
    public static boolean publishLibraryEquipment(LibrarySelection selection) {
        if (!librarySelectionCurrent(selection)) {
            return false;
        }
        AvatarManager.reloadCloudAvatar();
        YSM_SYNC.requestSnapshot();
        return true;
    }

    /** Transfer ownership of an already prepared formal model; failure preserves the resident model. */
    public static CompletableFuture<Boolean> publishLibraryEquipment(LibrarySelection selection, Avatar prepared) {
        if (!librarySelectionCurrent(selection) || prepared == null || !prepared.loaded || prepared.nbt == null
                || !selection.player.equals(prepared.owner)) {
            if (prepared != null) {
                prepared.clean();
            }
            return CompletableFuture.completedFuture(false);
        }
        clear(selection.player);
        return AvatarManager.publishLibraryAvatar(prepared, () -> librarySelectionCurrent(selection))
                .thenApply(published -> {
                    if (published) {
                        localSelectionChanged();
                        YSM_SYNC.requestSnapshot();
                    }
                    return published;
                });
    }

    public static void cancelLibrarySelection(LibrarySelection selection) {
        if (librarySelectionCurrent(selection)) {
            localSelectionChanged();
        }
    }

    /** Only an acknowledged, still-current outfit may replace the resident base/composition pair. */
    public static CompletableFuture<Boolean> publishOutfitEquipment(LibrarySelection selection,
            AvatarManager.OutfitLoad load, top.aihmc.omnimorph.model.outfit.OutfitConfig configuration,
            Avatar base, top.aihmc.omnimorph.model.outfit.OutfitComposition composition,
            java.util.function.BooleanSupplier current) {
        return load.publish(configuration, base, composition,
                () -> librarySelectionCurrent(selection) && current.getAsBoolean()).thenApply(published -> {
            if (published) {
                clear(selection.player);
                localSelectionChanged();
                YSM_SYNC.requestSnapshot();
            }
            return published;
        });
    }

    /** Worker-only profile refresh updates adapter identity and returns the official source-alias cache key. */
    public static String libraryEquipmentCacheKey(
            LibrarySelection selection, LibraryModels.Equipment equipment) throws Exception {
        if (!librarySelectionCurrent(selection) || !selection.player.equals(equipment.owner())) {
            throw new java.io.IOException("LIBRARY_RETIRED");
        }
        CloudProtocol.Reply reply = selection.connection.protocol.profile(selection.player);
        if (reply.status() != 200 || !librarySelectionCurrent(selection)) {
            throw new java.io.IOException("LIBRARY_PROFILE_UNAVAILABLE");
        }
        JsonArray selected = JsonParser.parseString(reply.body()).getAsJsonObject().getAsJsonArray("equipped");
        if (selected == null || selected.size() != 1) {
            throw new java.io.IOException("LIBRARY_EQUIPMENT_CHANGED");
        }
        JsonObject entry = selected.get(0).getAsJsonObject();
        String expectedAsset = selection.connection.protocol instanceof FiguraCloudProtocol ? "avatar" : equipment.assetId();
        String hash = entry.get("hash").getAsString();
        if (!selection.player.toString().equals(entry.get("owner").getAsString())
                || !expectedAsset.equals(entry.get("id").getAsString()) || !hash.matches("[a-f0-9]{64}")) {
            throw new java.io.IOException("LIBRARY_EQUIPMENT_CHANGED");
        }
        return hash;
    }

    /** Called only after successful local publication, or when the local model is explicitly cleared. */
    public static void localAvatarReady(Avatar avatar) {
        localBroadcastAvatar = avatar;
        localBroadcastPending = true;
        broadcastLocalAvatar();
    }

    private static void broadcastLocalAvatar() {
        if (!localBroadcastPending || !isConnected()) {
            return;
        }
        Avatar avatar = localBroadcastAvatar;
        CompoundTag source = avatar == null ? null : avatar.nbt;
        YsmModelRuntime runtime = avatar == null ? null : avatar.getYsmRuntime();
        long selection = localSelections.get();
        long progress = source == null ? 0 : uploadProgress.begin(AvatarLoadProgress.Stage.COMPRESSING);
        localBroadcastPending = false;
        boolean queued = queueString(Util.NIL_UUID, protocol -> {
            if (selection != localSelections.get()) {
                return new CloudProtocol.Reply(204, "");
            }
            if (source != null) {
                ByteArrayOutputStream output = new ByteArrayOutputStream();
                NbtIo.writeCompressed(YsmSourcePayload.forOutput(source), output);
                CloudProtocol.Metadata metadata = CloudProtocol.Metadata.figura();
                if (runtime != null) {
                    YsmSyncModel model = new YsmSyncModel(runtime);
                    metadata = new CloudProtocol.Metadata(runtime.encryptedSource() ? 3 : 2,
                            model.contentHash(), model.schemaHash());
                }
                if (selection != localSelections.get()) {
                    return new CloudProtocol.Reply(204, "");
                }
                byte[] bytes = output.toByteArray();
                uploadProgress.uploaded(progress, 0, bytes.length);
                var uploaded = protocol.upload("avatar", bytes, metadata,
                        sent -> uploadProgress.uploaded(progress, sent, bytes.length));
                if (uploaded.status() != 200) {
                    return uploaded;
                }
            }
            if (selection != localSelections.get()) {
                return new CloudProtocol.Reply(204, "");
            }
            uploadProgress.stage(progress, AvatarLoadProgress.Stage.EQUIPPING);
            return source == null ? protocol.equip(List.of(), List.of())
                    : protocol.equip(List.of(avatar.owner), List.of("avatar"));
        }, (code, body) -> {
            uploadProgress.finish(progress);
            if (selection != localSelections.get()) {
                return;
            }
            if (code == 200) {
                AvatarManager.localUploaded = source != null;
                YSM_SYNC.requestSnapshot();
            } else if (code != 204) {
                localBroadcastPending = true;
                FiguraMod.LOGGER.warn("Local model broadcast failed: HTTP {}", code);
                FiguraToast.sendToast(FiguraText.of("backend.upload_error"), Component.literal("HTTP " + code),
                        FiguraToast.ToastType.ERROR);
            }
        });
        if (!queued) {
            uploadProgress.finish(progress);
            localBroadcastPending = true;
        }
    }

    public static AvatarLoadProgress.Snapshot getUploadProgress() {
        return uploadProgress.snapshot();
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
        long progress = target.loading.begin(top.aihmc.omnimorph.avatar.AvatarLoadProgress.Stage.DOWNLOADING);
        boolean queued = queueString(target.id, protocol -> {
            try {
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
                    byte[] bytes = CloudHttp.readBounded(download.stream(), (int) CloudHttp.FILE_BYTES,
                            received -> target.loading.downloaded(progress, received, resource.length()));
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
                            target.loadAvatar(nbt, () -> selected == connection && selected.active()
                                    && requestGeneration == requestGenerations.getOrDefault(target.id, 0L));
                        }
                    });
                }
                return new CloudProtocol.Reply(200, "");
            } finally {
                target.loading.finish(progress);
            }
        }, (code, data) -> responseDebug("download", code, data));
        if (!queued) {
            target.loading.finish(progress);
        }
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
        retireObserver(owner, true);
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
        CloudHttp transport;
        synchronized (NetworkStuff.class) {
            if (resourceWork == null) {
                resourceWork = new CloudHttp(client, HttpAPI.endpoint());
            }
            transport = resourceWork;
        }
        var response = transport.send(request);
        if (response.statusCode() != 200) {
            transport.release(response.body());
            throw new java.io.IOException("Cloud asset HTTP " + response.statusCode());
        }
        return transport.managed(response.body());
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
        return isConnected() && uploadRate.check() && uploadProgress.snapshot() == null;
    }

    public static int getSizeLimit() {
        return isConnected() ? maxAvatarSize : Integer.MAX_VALUE;
    }
}
