package top.aihmc.omnimorph.model.library;

import com.google.gson.FieldNamingPolicy;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.world.entity.LivingEntity;
import top.aihmc.omnimorph.FiguraMod;
import top.aihmc.omnimorph.avatar.Avatar;
import top.aihmc.omnimorph.avatar.AvatarManager;
import top.aihmc.omnimorph.avatar.UserData;
import top.aihmc.omnimorph.backend2.NetworkStuff;
import top.aihmc.omnimorph.backend2.library.LibraryClient;
import top.aihmc.omnimorph.backend2.library.LibraryModels;
import top.aihmc.omnimorph.backend2.library.LibrarySession;
import top.aihmc.omnimorph.backend2.library.OutfitApplyResult;
import top.aihmc.omnimorph.backend2.protocol.CloudConnection;
import top.aihmc.omnimorph.backend2.protocol.CloudEndpoint;
import top.aihmc.omnimorph.backend2.protocol.CloudHttp;
import top.aihmc.omnimorph.backend2.protocol.CloudProtocol;
import top.aihmc.omnimorph.backend2.protocol.LibraryAccess;
import top.aihmc.omnimorph.model.outfit.CosmeticMetadata;
import top.aihmc.omnimorph.model.outfit.OutfitCodec;
import top.aihmc.omnimorph.model.outfit.OutfitConfig;
import top.aihmc.omnimorph.model.outfit.OutfitLimits;

import java.io.ByteArrayOutputStream;
import java.lang.reflect.Proxy;
import java.net.InetSocketAddress;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

/** Actual Apply coordinator and resident native runtimes over an HTTP contract fixture, without world/GPU claims. */
public final class OutfitApplyServiceSmokeTest {
    private static final Gson JSON = new GsonBuilder()
            .setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES).create();

    @SuppressWarnings("unchecked")
    public static void run(CompoundTag accessory, LivingEntity wearer, Runnable drain) throws Exception {
        UUID player = FiguraMod.getLocalPlayerUUID();
        var connection = field(NetworkStuff.class, "connection");
        Object previousConnection = connection.get(null);
        int previousStatus = NetworkStuff.backendStatus;
        boolean previousUploaded = AvatarManager.localUploaded;
        var users = (Map<UUID, UserData>) field(AvatarManager.class, "LOADED_USERS").get(null);
        UserData previousUser = users.get(player);
        var user = new UserData(player);
        CompoundTag baseData = accessory.copy();
        baseData.remove("scripts");
        baseData.getCompoundOrEmpty("metadata").putString("resource_kind", "AVATAR");
        try (var server = new Server(player, baseData, accessory, user)) {
            var protocol = (CloudProtocol) Proxy.newProxyInstance(CloudProtocol.class.getClassLoader(),
                    new Class<?>[]{CloudProtocol.class}, (proxy, method, arguments) -> switch (method.getName()) {
                        case "connected" -> true;
                        case "close", "localSelectionChanged" -> null;
                        default -> throw new AssertionError("Apply must not use legacy equipment: " + method.getName());
                    });
            var scope = new CloudConnection(NetworkStuff.connectionEpoch(), protocol);
            connection.set(null, scope);
            NetworkStuff.backendStatus = 3;
            users.put(player, user);
            Avatar original = new Avatar(player);
            original.load(baseData.copy());
            await(user.publishAvatar(original, user.ysmLoads.begin(), () -> true), drain);
            var endpoint = CloudEndpoint.parse(server.origin(), () -> true);
            try (var access = new LibraryAccess(endpoint, HttpClient.newHttpClient(), request -> request
                    .header("authorization", "Bearer apply-test"));
                 var client = new LibraryClient(access,
                         new LibrarySession(server.origin(), player, scope.epoch, scope::active), Minecraft.getInstance()::execute);
                 var service = new OutfitApplyService(client, null, FiguraMod.getFiguraDirectory(), FiguraMod.getCacheDirectory())) {
                var first = service.apply(server.configuration(0), wearer, () -> true);
                OutfitApplyResult accepted;
                try {
                    accepted = await(first.future(), drain);
                } catch (Exception failure) {
                    throw new AssertionError("Apply fixture issues: " + first.issues(), failure);
                }
                require(accepted instanceof OutfitApplyResult.Accepted && server.commits == 1
                                && user.getOutfit().configuration().revision() == 1 && user.getMainAvatar() != original
                                && !original.accelerationOwner().isCurrent(),
                        "native preparation, actual CAS and current authority recheck publish one real resident pair");
                Avatar retained = user.getMainAvatar();
                var runtime = retained.getYsmRuntime();
                var retainedComposition = user.getOutfit().composition();
                var second = await(service.apply(server.configuration(1), wearer, () -> true).future(), drain);
                require(second instanceof OutfitApplyResult.Accepted && user.getMainAvatar() == retained
                                && retained.getYsmRuntime() == runtime && user.getOutfit().composition() != retainedComposition,
                        "accessory-only Apply retains the actual base and its native runtime");
                int beforeRestore = server.commits;
                require(await(service.restore(server.committed, wearer, () -> true), drain)
                                && server.commits == beforeRestore && user.getMainAvatar() == retained
                                && retained.getYsmRuntime() == runtime
                                && user.getOutfit().configuration().equals(server.committed),
                        "owner reconnect restores the committed native outfit without CAS or resetting its base");
                retainedComposition = user.getOutfit().composition();
                server.loseResponse = true;
                var retry = service.apply(server.configuration(2), wearer, () -> true);
                failure(retry.future(), drain, "BUSY");
                require(server.commits == 3 && user.getOutfit().composition() == retainedComposition,
                        "lost committed response preserves the previous local pair");
                UUID operation = retry.operationId();
                await(retry.retry(), drain);
                require(retry.operationId().equals(operation) && server.commits == 3 && server.replays == 1
                                && user.getMainAvatar() == retained && user.getOutfit().configuration().revision() == 3,
                        "recovery re-prepares exact fixed dependencies and replays frozen CAS without a duplicate commit");
                retainedComposition = user.getOutfit().composition();
                var conflict = await(service.apply(server.configuration(1), wearer, () -> true).future(), drain);
                require(conflict instanceof OutfitApplyResult.Conflict && server.commits == 3
                                && user.getOutfit().composition() == retainedComposition,
                        "CAS conflict is inspectable and never auto-merges or activates");
                server.advanceAfterReceipt = true;
                failure(service.apply(server.configuration(3), wearer, () -> true).future(), drain, "OUTFIT_CHANGED");
                require(user.getOutfit().composition() == retainedComposition && server.commits == 4,
                        "a superseding server configuration after the receipt prevents stale local activation");
                server.advanceAfterReceipt = false;
                server.wrongEquipmentRevision = true;
                failure(service.apply(server.configuration(server.revision), wearer, () -> true).future(), drain, "OUTFIT_CHANGED");
                require(user.getOutfit().composition() == retainedComposition,
                        "a changed equipment revision cannot activate an otherwise matching outfit receipt");
                server.wrongEquipmentRevision = false;
                server.tightenAfterReceipt = true;
                failure(service.apply(server.configuration(server.revision), wearer, () -> true).future(), drain, "OUTFIT_LIMITS_CHANGED");
                require(user.getOutfit().composition() == retainedComposition,
                        "changed budgets after CAS require fresh native preparation instead of publishing old allowances");
                server.tightenAfterReceipt = false;
                server.tightened = false;
                int commits = server.commits;
                server.denySources = true;
                var unavailable = service.apply(server.configuration(server.revision), wearer, () -> true);
                failure(unavailable.future(), drain, "OUTFIT_DEPENDENCY_UNAVAILABLE");
                require(!unavailable.issues().isEmpty() && server.commits == commits
                                && user.getOutfit().composition() == retainedComposition,
                        "revoked dependencies fail before CAS and expose affected source/instance issues");
                server.denySources = false;
                var active = new java.util.concurrent.atomic.AtomicBoolean(true);
                var retired = service.apply(server.configuration(server.revision), wearer, active::get);
                active.set(false);
                try {
                    await(retired.future(), drain);
                    throw new AssertionError("retired Apply accepted");
                } catch (ExecutionException | java.util.concurrent.CancellationException expected) {
                    require(user.getOutfit().composition() == retainedComposition && server.commits == commits,
                            "retired draft never commits or replaces the resident pair");
                }
                require(server.prematurePublication == 0, "all requests observe the old resident configuration before acknowledgement");
                testInstanceState(client, server, user, wearer, drain);
                testWardrobe(client, server, user, drain);
                testFormalHudWheel(user, wearer, drain);
                testFormalControls(user, wearer, drain);
                testWorldFrames(user, wearer, drain);
                testObserverPublication(baseData, accessory, drain);
                System.out.println("PASS formal outfit Apply " + server.base.reference().format()
                        + ": native-before-CAS, resident pair, same-base reuse, frozen replay, conflict, post-receipt authority and retirement; world/GUI pending");
            } finally {
                scope.close();
            }
        } finally {
            NetworkStuff.localSelectionChanged();
            NetworkStuff.clear(player);
            user.clear();
            drain.run();
            if (previousUser == null) {
                users.remove(player);
            } else {
                users.put(player, previousUser);
            }
            connection.set(null, previousConnection);
            NetworkStuff.backendStatus = previousStatus;
            AvatarManager.localUploaded = previousUploaded;
        }
    }

    @SuppressWarnings("unchecked")
    private static void testObserverPublication(CompoundTag baseData, CompoundTag accessory, Runnable drain) throws Exception {
        var wearer = new ObserverWearer();
        wearer.setUUID(UUID.randomUUID());
        var user = new UserData(wearer.getUUID());
        try (var server = new Server(user.id, baseData, accessory, user)) {
            var configured = server.configuration(1);
            server.committed = new OutfitConfig(1, 1, configured.base(), configured.instances(), List.of(), configured.displayOrdinals(), true);
            server.revision = 1;
            var endpoint = CloudEndpoint.parse(server.origin(), () -> true);
            try (var access = new LibraryAccess(endpoint, HttpClient.newHttpClient(), request -> request.header("authorization", "Bearer apply-test"));
                 var client = new LibraryClient(access, new LibrarySession(server.origin(), FiguraMod.getLocalPlayerUUID(), 1, () -> true),
                         Minecraft.getInstance()::execute);
                 var service = new OutfitObserverService(client, FiguraMod.getCacheDirectory())) {
                require(await(service.refresh(user, wearer, () -> true).future(), drain) == OutfitObserverService.Result.PUBLISHED
                                && user.getOutfit() != null && !user.getMainAvatar().isHost,
                        "actual remote preparation publishes a non-host native observer base and composition");
                Avatar base = user.getMainAvatar();
                var runtime = base.getYsmRuntime();
                var previous = user.getOutfit().composition();
                var copies = (Map<UUID, Avatar>) field(previous.getClass(), "instances").get(previous);
                require(copies.size() == 1 && copies.values().stream().allMatch(copy -> copy.cosmeticContext.scope()
                                == top.aihmc.omnimorph.model.outfit.CosmeticInstanceContext.Scope.OBSERVER && copy.luaRuntime == null),
                        "observer copies retain their scope and do not run author Lua during preparation");
                server.committed = new OutfitConfig(1, 2, configured.base(), configured.instances(), List.of(), configured.displayOrdinals(), true);
                server.revision = 2;
                await(service.refresh(user, wearer, () -> true).future(), drain);
                require(user.getMainAvatar() == base && base.getYsmRuntime() == runtime && user.getOutfit().composition() != previous,
                        "observer configuration-only changes preserve the original native base runtime");
                var stable = user.getOutfit();
                server.observerTighten = true;
                server.observerSnapshots.set(0);
                failure(service.refresh(user, wearer, () -> true).future(), drain, "OUTFIT_LIMITS_CHANGED");
                require(user.getOutfit() == stable, "budgets changed after the final snapshot cannot activate old allowances");
                server.observerTighten = false;
                server.tightened = false;
                server.committed = new OutfitConfig(1, 1, configured.base(), configured.instances(), List.of(), configured.displayOrdinals(), true);
                server.revision = 1;
                try {
                    await(service.refresh(user, wearer, () -> true).future(), drain);
                    throw new AssertionError("older observer revision published");
                } catch (ExecutionException | java.util.concurrent.CancellationException expected) {
                    require(user.getOutfit() == stable && base.accelerationOwner().isCurrent(),
                            "an older authorized snapshot cannot replace a newer resident revision or clean its borrowed base");
                }
                server.denySources = true;
                failure(service.refresh(user, wearer, () -> true).future(), drain, "OUTFIT_STALE");
                require(user.getOutfit() == stable, "an older hidden projection cannot clear a newer resident pair");
                server.denySources = false;
                server.committed = stable.configuration();
                server.revision = stable.configuration().revision();
                try (var failing = new OutfitObserverService(client, FiguraMod.getCacheDirectory().resolve("observer-failure-" + UUID.randomUUID()))) {
                    server.observerBadBody = true;
                    failure(failing.refresh(user, wearer, () -> true).future(), drain, "DISTRIBUTION_HASH_MISMATCH");
                    require(user.getOutfit() == stable && base.accelerationOwner().isCurrent(),
                            "ordinary observer source failure preserves the preceding resident pair");
                    server.observerBadBody = false;
                }
                var subscribed = new java.util.concurrent.atomic.AtomicBoolean(true);
                var retired = service.refresh(user, wearer, subscribed::get);
                subscribed.set(false);
                try {
                    await(retired.future(), drain);
                    throw new AssertionError("retired observer published");
                } catch (ExecutionException | java.util.concurrent.CancellationException expected) {
                    require(user.getOutfit() == stable, "retired subscription cannot replace the resident pair");
                }
                server.denySources = true;
                require(await(service.refresh(user, wearer, () -> true).future(), drain) == OutfitObserverService.Result.HIDDEN
                                && user.getMainAvatar() == null && user.getOutfit() == null && !base.accelerationOwner().isCurrent(),
                        "an authoritative hidden projection retires both old base and instances");
                server.denySources = false;
                server.revision = 0;
                server.committed = OutfitConfig.empty();
                require(await(service.refresh(user, wearer, () -> true).future(), drain) == OutfitObserverService.Result.LEGACY,
                        "legacy accounts return to the existing profile path instead of activating a synthetic clear");
                require(server.commits == 0, "observer preparation never submits Apply or other equipment mutations");
                testObserverDispatch(client, server, user, wearer, drain);
            } finally {
                user.clear();
            }
        }
        System.out.println("PASS observer native publication: remote identity/scope, fixed native preparation, same-base reuse, source failure, subscription retirement and hidden/legacy handling; live backend/GPU pending");
    }

    private static final class ObserverWearer extends net.minecraft.world.entity.decoration.ArmorStand
            implements top.aihmc.omnimorph.mixin.EntityAccessor {
        private ObserverWearer() {
            super(net.minecraft.world.entity.EntityType.ARMOR_STAND, null);
        }

        @Override
        public net.minecraft.world.level.Level getLevel() {
            return Minecraft.getInstance().level;
        }
    }

    @SuppressWarnings("unchecked")
    private static void testObserverDispatch(LibraryClient client, Server server, UserData user,
                                             LivingEntity wearer, Runnable drain) throws Exception {
        var minecraft = Minecraft.getInstance();
        var previousWorld = minecraft.level;
        int previousTick = FiguraMod.ticks;
        var libraryField = field(NetworkStuff.class, "libraryClient");
        var previousLibrary = libraryField.get(null);
        var eventsField = field(NetworkStuff.class, "outfitEvents");
        var previousEvents = eventsField.get(null);
        var users = (Map<UUID, UserData>) field(AvatarManager.class, "LOADED_USERS").get(null);
        var subscriptions = (List<UUID>) field(NetworkStuff.class, "SUBSCRIPTIONS").get(null);
        var observers = (Map<UUID, ?>) field(NetworkStuff.class, "outfitObservers").get(null);
        var unsafeField = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
        unsafeField.setAccessible(true);
        var unsafe = (sun.misc.Unsafe) unsafeField.get(null);
        var world = (ObserverLevel) unsafe.allocateInstance(ObserverLevel.class);
        world.visible = wearer;
        minecraft.level = world;
        libraryField.set(null, client);
        eventsField.set(null, null);
        users.put(user.id, user);
        subscriptions.add(user.id);
        var tick = NetworkStuff.class.getDeclaredMethod("tickOutfitObservers");
        tick.setAccessible(true);
        var eventTick = NetworkStuff.class.getDeclaredMethod("tickOutfitEvents");
        eventTick.setAccessible(true);
        try {
            var configuration = server.configuration(3);
            server.revision = 3;
            server.committed = new OutfitConfig(1, 3, configuration.base(), configuration.instances(), List.of(), configuration.displayOrdinals(), true);
            NetworkStuff.getUser(user);
            require(await(dispatchedObserver(observers.get(user.id)).future(), drain) == OutfitObserverService.Result.PUBLISHED,
                    "the actual NetworkStuff getUser entry activates an authorized remote outfit");
            var resident = user.getOutfit();
            eventTick.invoke(null);
            Object coordinator = eventsField.get(null);
            require(coordinator != null, "the production event tick installs its connection-owned coordinator");
            var capabilityRead = (LibraryClient.Request<?>) field(coordinator.getClass(), "pending").get(coordinator);
            if (capabilityRead != null) {
                await(capabilityRead.future(), drain);
            }
            var events = (top.aihmc.omnimorph.backend2.library.OutfitEvents.Listener) coordinator;
            Object originalObserver = observers.get(user.id);
            events.changed(user.id, 2, 0);
            require(user.getOutfit() == resident && observers.get(user.id) == originalObserver,
                    "a stale revision notification cannot retire a newer resident or pending identity");
            var queued = (java.util.Queue<?>) field(NetworkStuff.class, "API_REQUESTS").get(null);
            Object badgeRequest = queued.stream().filter(request -> {
                try {
                    return user.id.equals(field(request.getClass(), "owner").get(request));
                } catch (Exception failure) {
                    throw new AssertionError(failure);
                }
            }).findFirst().orElseThrow();
            var badgeCallback = (java.util.function.BiConsumer<Integer, String>) field(badgeRequest.getClass(), "callback").get(badgeRequest);
            badgeCallback.accept(200, "{\"equipped\":[],\"equippedBadges\":{\"pride\":[],\"special\":[]}}");
            require(user.getOutfit() == resident, "a badge-only legacy profile response cannot clear the modern outfit");
            server.revision = 4;
            server.committed = new OutfitConfig(1, 4, configuration.base(), configuration.instances(), List.of(), configuration.displayOrdinals(), true);
            events.changed(user.id, 4, 0);
            require(user.getOutfit() == resident && !observers.containsKey(user.id),
                    "a newer revision invalidates the old request while retaining its reusable base");
            tick.invoke(null);
            await(dispatchedObserver(observers.get(user.id)).future(), drain);
            require(user.getMainAvatar() == resident.base() && user.getOutfit().configuration().revision() == 4,
                    "the existing changed/reload path refreshes the composition while preserving its base");
            AvatarManager.reloadAvatar(user.id);
            await(dispatchedObserver(observers.get(user.id)).future(), drain);
            server.observerBusy = true;
            NetworkStuff.getUser(user);
            var busy = dispatchedObserver(observers.get(user.id));
            failure(busy.future(), drain, "BUSY");
            server.observerBusy = false;
            FiguraMod.ticks += 20;
            tick.invoke(null);
            var retried = dispatchedObserver(observers.get(user.id));
            require(retried != busy, "a temporary observer failure is retried on a later tick");
            await(retried.future(), drain);
            server.denySources = true;
            events.changed(user.id, 4, 0);
            require(user.getOutfit() == null && !observers.containsKey(user.id),
                    "same-revision policy invalidation immediately retires the old authorized projection");
            tick.invoke(null);
            require(await(dispatchedObserver(observers.get(user.id)).future(), drain) == OutfitObserverService.Result.HIDDEN,
                    "the event callback obtains the current hidden snapshot instead of restoring cached permissions");
            server.denySources = false;
            events.changed(user.id, 4, 0);
            tick.invoke(null);
            await(dispatchedObserver(observers.get(user.id)).future(), drain);
            events.reset();
            require(user.getOutfit() == null, "event reconnect/gap recovery retires its preceding remote baseline");
            tick.invoke(null);
            await(dispatchedObserver(observers.get(user.id)).future(), drain);
            server.tightened = true;
            events.limitsChanged();
            require(user.getOutfit() == null && field(coordinator.getClass(), "capabilities").get(coordinator) == null,
                    "limit invalidation clears old resident allowances and negotiated capabilities");
            eventTick.invoke(null);
            capabilityRead = (LibraryClient.Request<?>) field(coordinator.getClass(), "pending").get(coordinator);
            if (capabilityRead != null) {
                await(capabilityRead.future(), drain);
            }
            var refreshed = (LibraryModels.Capabilities) field(coordinator.getClass(), "capabilities").get(coordinator);
            require(refreshed.outfitsV1().limits().maxTotalNodes() == OutfitLimits.defaults().maxTotalNodes() - 1,
                    "the production coordinator rereads actual backend budgets after invalidation");
            tick.invoke(null);
            await(dispatchedObserver(observers.get(user.id)).future(), drain);
            server.tightened = false;
            world.visible = null;
            tick.invoke(null);
            require(!observers.containsKey(user.id) && user.getOutfit() == null,
                    "entity departure retires observer callbacks and formal resident resources");
            world.visible = wearer;
            tick.invoke(null);
            await(dispatchedObserver(observers.get(user.id)).future(), drain);
            require(user.getOutfit() != null, "a late/reappearing entity starts its observer preparation on the existing tick");
            subscriptions.remove(user.id);
            tick.invoke(null);
            require(!observers.containsKey(user.id) && user.getOutfit() == null,
                    "subscription retirement removes the modern outfit without an equipment write");
        } finally {
            Object coordinator = eventsField.get(null);
            if (coordinator instanceof AutoCloseable closeable) {
                closeable.close();
            }
            eventsField.set(null, previousEvents);
            NetworkStuff.clear(user.id);
            subscriptions.remove(user.id);
            users.remove(user.id);
            user.clear();
            libraryField.set(null, previousLibrary);
            minecraft.level = previousWorld;
            FiguraMod.ticks = previousTick;
        }
        System.out.println("PASS observer network dispatch: actual event coordinator/getUser/reload/tick, revision/policy/limits recovery, badge isolation, base reuse, transient retry and entity/subscription retirement; real connection/GPU pending");
    }

    private static OutfitObserverService.Operation dispatchedObserver(Object entry) throws Exception {
        require(entry != null, "observer dispatch installed a current loading identity");
        var service = (OutfitObserverService) field(entry.getClass(), "service").get(entry);
        return (OutfitObserverService.Operation) field(OutfitObserverService.class, "active").get(service);
    }

    private static void testInstanceState(LibraryClient client, Server server, UserData user,
                                          LivingEntity wearer, Runnable drain) throws Exception {
        if (!user.getOutfit().configuration().equals(server.committed)) {
            try (var restore = new OutfitApplyService(client, null, FiguraMod.getFiguraDirectory(), FiguraMod.getCacheDirectory())) {
                await(restore.restore(server.committed, wearer, () -> true), drain);
            }
        }
        var copies = user.getOutfit().composition().runtimeInstances();
        var selected = copies.entrySet().iterator().next();
        var context = selected.getValue().cosmeticContext;
        try (var sync = new OutfitInstanceSync(client, owner -> {
            throw new AssertionError("Unexpected instance invalidation: " + owner);
        })) {
            long tick = 0;
            for (; tick < 100 && !server.instanceStates.containsKey(selected.getKey()); tick++) {
                sync.tick(tick, java.util.Set.of());
                drain.run();
                Thread.sleep(5);
            }
            var stateApi = new top.aihmc.omnimorph.lua.api.CosmeticStateAPI(context);
            stateApi.set("sync_probe", org.luaj.vm2.LuaValue.valueOf("owner"));
            stateApi.set("numeric_string", org.luaj.vm2.LuaValue.valueOf("001"));
            context.saveControl("sync_toggle", true);
            if (selected.getValue().getYsmRuntime() != null) {
                selected.getValue().controls.setValue(selected.getValue(), "ysm.width_scale", 0.8);
            }
            boolean published = false;
            for (int attempt = 0; attempt < 300 && !published; attempt++, tick++) {
                sync.tick(tick, java.util.Set.of());
                drain.run();
                Thread.sleep(5);
                var state = server.instanceStates.get(selected.getKey());
                published = state != null && state.getAsJsonObject("state").getAsJsonObject("lua_state").has("sync_probe");
            }
            require(published, "actual instance context controls/Lua state publish through the authenticated HTTP client");
            sync.close();
            context.restoreState(Map.of(), Map.of());
        }
        try (var restored = new OutfitInstanceSync(client, owner -> {
            throw new AssertionError("Unexpected restored instance invalidation: " + owner);
        })) {
            for (int tick = 0; tick < 100 && context.luaState().isEmpty(); tick++) {
                restored.tick(tick, java.util.Set.of());
                drain.run();
                Thread.sleep(5);
            }
            require("owner".equals(context.luaState().get("sync_probe")) && Boolean.TRUE.equals(context.control("sync_toggle")),
                    "a fresh sync session restores persistent instance values without another Apply");
            require("001".equals(context.luaState().get("numeric_string")), "numeric Lua strings keep their type");
        }
        Avatar base = user.getMainAvatar();
        var configuration = user.getOutfit().configuration();
        var observer = top.aihmc.omnimorph.model.outfit.OutfitPreparation.prepare(configuration, OutfitLimits.defaults(),
                client.session().player(), UUID.randomUUID(), wearer, base.permissions,
                new top.aihmc.omnimorph.model.outfit.SkeletonIndex.Cache(),
                (reference, cancelled) -> server.accessory.data().copy(), () -> false,
                top.aihmc.omnimorph.model.outfit.CosmeticInstanceContext.Scope.OBSERVER, base, reference -> true, reference -> true);
        await(user.publishOutfit(configuration, base, observer, user.ysmLoads.begin(), () -> true), drain);
        var remote = observer.runtimeInstances().get(selected.getKey());
        long savedRevision = server.instanceStates.get(selected.getKey()).get("revision").getAsLong();
        try (var sync = new OutfitInstanceSync(client, owner -> {
            throw new AssertionError("Unexpected observer instance invalidation: " + owner);
        })) {
            for (int tick = 0; tick < 150 && !remote.cosmeticContext.luaState().containsKey("sync_probe"); tick++) {
                sync.tick(tick, java.util.Set.of());
                drain.run();
                Thread.sleep(5);
            }
            require("owner".equals(remote.cosmeticContext.luaState().get("sync_probe"))
                            && Boolean.TRUE.equals(remote.cosmeticContext.control("sync_toggle")),
                    "native OBSERVER consumes the stored instance baseline");
            if (remote.getYsmRuntime() != null) {
                require(Math.abs(remote.getYsmRuntime().widthScale() - 0.8f) < 0.0001,
                        "native observer applies the existing YSM snapshot to its own runtime");
                require(remote.getYsmRuntime().functions().drainSyncEvents().isEmpty(),
                        "restored observer state does not recapture outgoing events");
            }
            remote.cosmeticContext.saveLuaState("sync_probe", "observer");
            for (int tick = 150; tick < 160; tick++) {
                sync.tick(tick, java.util.Set.of());
                drain.run();
            }
            require(server.instanceStates.get(selected.getKey()).get("revision").getAsLong() == savedRevision,
                    "observer hydration and local input never publish owner state");
            int reads = server.instanceStateReads.get();
            sync.changed(user.id, selected.getKey(), configuration.revision(), savedRevision + 1);
            for (int tick = 160; tick < 200 && server.instanceStateReads.get() < reads + 2; tick++) {
                sync.tick(tick, java.util.Set.of());
                drain.run();
                Thread.sleep(5);
            }
            require(server.instanceStateReads.get() >= reads + 2,
                    "an older HTTP response cannot clear a newer state notification");
        }
        try (var restore = new OutfitApplyService(client, null, FiguraMod.getFiguraDirectory(), FiguraMod.getCacheDirectory())) {
            await(restore.restore(server.committed, wearer, () -> true), drain);
        }
        System.out.println("PASS instance state: native OWNER save/reload, typed Lua scalars, OBSERVER hydration/YSM snapshot and no echo; real clients pending");
    }

    private static final class ObserverLevel extends net.minecraft.client.multiplayer.ClientLevel
            implements top.aihmc.omnimorph.mixin.ClientLevelInvoker {
        private LivingEntity visible;

        private ObserverLevel() {
            super(null, null, null, null, 0, 0, null, false, 0L, 0);
        }

        @Override
        public net.minecraft.core.RegistryAccess registryAccess() {
            return net.minecraft.core.RegistryAccess.fromRegistryOfRegistries(net.minecraft.core.registries.BuiltInRegistries.REGISTRY);
        }

        @Override
        public net.minecraft.world.flag.FeatureFlagSet enabledFeatures() {
            return net.minecraft.world.flag.FeatureFlags.DEFAULT_FLAGS;
        }

        @Override
        @SuppressWarnings("unchecked")
        public net.minecraft.world.level.entity.LevelEntityGetter<net.minecraft.world.entity.Entity> getEntityGetter() {
            return (net.minecraft.world.level.entity.LevelEntityGetter<net.minecraft.world.entity.Entity>) Proxy.newProxyInstance(
                    getClass().getClassLoader(), new Class<?>[]{net.minecraft.world.level.entity.LevelEntityGetter.class},
                    (proxy, method, arguments) -> {
                        if (method.getName().equals("get")) {
                            return visible != null && visible.getUUID().equals(arguments[0]) ? visible : null;
                        }
                        throw new UnsupportedOperationException("No traversal in the observer fixture");
                    });
        }
    }

    private static void testFormalHudWheel(UserData user, LivingEntity wearer, Runnable drain) throws Exception {
        Avatar base = user.getMainAvatar();
        base.initializePreviewPlayer(wearer);
        var previousPage = base.luaRuntime.action_wheel.getCurrentPage();
        base.luaRuntime.setGlobal("hud_budget", new org.luaj.vm2.lib.ZeroArgFunction() {
            @Override
            public org.luaj.vm2.LuaValue call() {
                require(top.aihmc.omnimorph.model.outfit.OutfitInstructionBudget.current(base) != null,
                        "the actual HUD scopes base action callbacks in the outfit ledger");
                return org.luaj.vm2.LuaValue.TRUE;
            }
        });
        require(base.luaRuntime.run(base.luaRuntime.load("HUD base page", """
                hud_clicks = 0
                local page = action_wheel:newPage('HUD base fixture')
                page:newAction(1):onLeftClick(function() hud_budget(); hud_clicks = hud_clicks + 1 end)
                action_wheel:setPage(page)
                """), new Avatar.Instructions(4096)) != null && !base.scriptError, "base HUD fixture initializes");
        boolean initialEnabled = top.aihmc.omnimorph.gui.ActionWheel.isEnabled();
        try {
            top.aihmc.omnimorph.gui.ActionWheel.setEnabled(true);
            top.aihmc.omnimorph.gui.ActionWheel.hotbarKeyPressed(0);
            require(!base.scriptError, "formal outfits without active animation entries still budget base HUD callbacks");
        } finally {
            top.aihmc.omnimorph.gui.ActionWheel.setEnabled(initialEnabled);
        }
        var reference = new OutfitConfig.ResourceRef(OutfitConfig.Source.LOCAL, UUID.randomUUID(), null, null,
                null, "0".repeat(64), OutfitConfig.Format.FIGURA);
        var declaration = new OutfitConfig.Instance(UUID.randomUUID(), reference, 1, true,
                OutfitConfig.AnchorRef.authorDefault("unused"), OutfitConfig.Transform.identity(), List.of(), Map.of(), Map.of());
        var context = new top.aihmc.omnimorph.model.outfit.CosmeticInstanceContext(wearer.getUUID(), UUID.randomUUID(),
                wearer.getUUID(), declaration, OutfitLimits.defaults(),
                top.aihmc.omnimorph.model.outfit.CosmeticInstanceContext.Scope.OWNER);
        Avatar instance = Avatar.createCosmetic(context, base.permissions, "HUD instance fixture");
        var data = new CompoundTag();
        var scripts = new CompoundTag();
        scripts.putByteArray("wheel", """
                clicks, scrolls = 0, 0
                local page = action_wheel:newPage('HUD instance fixture')
                page:newAction(1):onLeftClick(function() clicks = clicks + 1 end)
                    :onScroll(function(delta) scrolls = scrolls + delta end)
                action_wheel:setPage(page)
                """.getBytes(StandardCharsets.UTF_8));
        data.put("scripts", scripts);
        instance.load(data);
        instance.initializePreviewPlayer(wearer);
        require(!instance.scriptError && instance.luaRuntime != null, "instance HUD fixture initializes");
        var configuration = new OutfitConfig(1, user.getOutfit().configuration().revision() + 1, base.outfitSource(),
                List.of(declaration), List.of(), Map.of(reference.identity(), 1), false);
        var composition = new top.aihmc.omnimorph.model.outfit.OutfitComposition(configuration, OutfitLimits.defaults(),
                wearer, Map.of(declaration.instanceId(), instance), new top.aihmc.omnimorph.model.outfit.SkeletonIndex.Cache());
        await(user.publishOutfit(configuration, base, composition, user.ysmLoads.begin(), () -> true), drain);
        var wheel = composition.actionWheel();
        boolean wasEnabled = top.aihmc.omnimorph.gui.ActionWheel.isEnabled();
        var selected = field(top.aihmc.omnimorph.gui.ActionWheel.class, "selected");
        int previousSelected = selected.getInt(null);
        try {
            top.aihmc.omnimorph.gui.ActionWheel.setEnabled(true);
            top.aihmc.omnimorph.gui.ActionWheel.hotbarKeyPressed(0);
            require(base.luaRuntime.run(base.luaRuntime.load("HUD base count", "assert(hud_clicks == 2)"),
                    new Avatar.Instructions(4096)) != null && !base.scriptError, "actual HUD retains base actions");
            var page = wheel.page(base);
            int navigation = page.getGreatestSlot();
            page.setSlotsShift(navigation / 8 + 1);
            top.aihmc.omnimorph.gui.ActionWheel.hotbarKeyPressed(navigation % 8);
            top.aihmc.omnimorph.gui.ActionWheel.hotbarKeyPressed(1);
            require(wheel.selectedOwner(base) == instance, "actual HUD enters the formal instance page");
            top.aihmc.omnimorph.gui.ActionWheel.hotbarKeyPressed(0);
            selected.setInt(null, 0);
            top.aihmc.omnimorph.gui.ActionWheel.scroll(2);
            require(instance.luaRuntime.run(instance.luaRuntime.load("HUD instance counts", "assert(clicks == 1 and scrolls == 2)"),
                    new Avatar.Instructions(4096)) != null && !instance.scriptError,
                    "actual HUD click and scroll reach the selected instance VM");
        } finally {
            top.aihmc.omnimorph.gui.ActionWheel.setEnabled(false);
            top.aihmc.omnimorph.gui.ActionWheel.setEnabled(wasEnabled);
            selected.setInt(null, previousSelected);
            base.luaRuntime.action_wheel.setPage(previousPage);
        }
        System.out.println("PASS formal HUD wheel handlers: resident lookup, base budget, instance navigation, click and scroll; real input/GPU pending");
    }

    private static void testFormalControls(UserData user, LivingEntity wearer, Runnable drain) throws Exception {
        Avatar base = user.getMainAvatar();
        var reference = new OutfitConfig.ResourceRef(OutfitConfig.Source.LOCAL, UUID.randomUUID(), null, null,
                null, "0".repeat(64), OutfitConfig.Format.FIGURA);
        var declarations = new java.util.ArrayList<OutfitConfig.Instance>();
        var instances = new java.util.LinkedHashMap<UUID, Avatar>();
        UUID identity = UUID.randomUUID();
        for (int index = 1; index <= 3; index++) {
            var declaration = new OutfitConfig.Instance(UUID.randomUUID(), reference, index, index < 3,
                    OutfitConfig.AnchorRef.authorDefault("unused"), OutfitConfig.Transform.identity(), List.of(), Map.of(), Map.of());
            declarations.add(declaration);
            var context = new top.aihmc.omnimorph.model.outfit.CosmeticInstanceContext(wearer.getUUID(), identity,
                    wearer.getUUID(), declaration, OutfitLimits.defaults(),
                    top.aihmc.omnimorph.model.outfit.CosmeticInstanceContext.Scope.OWNER);
            Avatar instance = Avatar.createCosmetic(context, base.permissions, "formal controls fixture");
            var data = new CompoundTag();
            var scripts = new CompoundTag();
            scripts.putByteArray("controls", "local fixture = true".getBytes(StandardCharsets.UTF_8));
            data.put("scripts", scripts);
            instance.load(data);
            instance.initializePreviewPlayer(wearer);
            instances.put(declaration.instanceId(), instance);
        }
        var configuration = new OutfitConfig(1, user.getOutfit().configuration().revision() + 1, base.outfitSource(),
                declarations, List.of(), Map.of(reference.identity(), 3), false);
        var composition = new top.aihmc.omnimorph.model.outfit.OutfitComposition(configuration, OutfitLimits.defaults(),
                wearer, instances, new top.aihmc.omnimorph.model.outfit.SkeletonIndex.Cache());
        await(user.publishOutfit(configuration, base, composition, user.ysmLoads.begin(), () -> true), drain);
        var controlsType = top.aihmc.omnimorph.avatar.control.AvatarControlType.TOGGLE;
        var first = instances.get(declarations.getFirst().instanceId());
        var second = instances.get(declarations.get(1).instanceId());
        var calls = new java.util.IdentityHashMap<Avatar, Integer>();
        var presses = new java.util.IdentityHashMap<Avatar, Integer>();
        for (Avatar owner : List.of(base, first, second, instances.get(declarations.get(2).instanceId()))) {
            var control = new top.aihmc.omnimorph.avatar.control.AvatarControlDefinition("formal_shared", controlsType)
                    .setDefault(false).setPersistent(owner.cosmeticContext != null);
            control.onChange(new org.luaj.vm2.lib.ZeroArgFunction() {
                @Override
                public org.luaj.vm2.LuaValue call() {
                    require(top.aihmc.omnimorph.model.outfit.OutfitInstructionBudget.current(base) != null,
                            "formal controls scope both base and instance callbacks in the resident budget");
                    calls.merge(owner, 1, Integer::sum);
                    return org.luaj.vm2.LuaValue.NIL;
                }
            });
            owner.controls.register(owner, control);
            owner.controls.register(owner, new top.aihmc.omnimorph.avatar.control.AvatarControlDefinition("formal_button",
                    top.aihmc.omnimorph.avatar.control.AvatarControlType.BUTTON).onPress(new org.luaj.vm2.lib.ZeroArgFunction() {
                @Override
                public org.luaj.vm2.LuaValue call() {
                    require(top.aihmc.omnimorph.model.outfit.OutfitInstructionBudget.current(base) != null,
                            "formal button callbacks share the same resident budget");
                    presses.merge(owner, 1, Integer::sum);
                    return org.luaj.vm2.LuaValue.NIL;
                }
            }));
        }
        var screenType = top.aihmc.omnimorph.gui.screens.OutfitControlsScreen.class;
        var baseScreen = top.aihmc.omnimorph.gui.screens.OutfitControlsScreen.create(null, base, "root");
        var firstScreen = top.aihmc.omnimorph.gui.screens.OutfitControlsScreen.create(null, first, "root");
        var secondScreen = top.aihmc.omnimorph.gui.screens.OutfitControlsScreen.create(null, second, "root");
        require(baseScreen != null && firstScreen != null && secondScreen != null
                        && top.aihmc.omnimorph.gui.screens.OutfitControlsScreen.create(null,
                        instances.get(declarations.get(2).instanceId()), "root") == null,
                "formal controls admit the actual base and enabled copies, rejecting a disabled copy");
        var change = top.aihmc.omnimorph.gui.screens.AvatarControlsScreen.class.getDeclaredMethod("setControlValue",
                top.aihmc.omnimorph.avatar.control.AvatarControlDefinition.class, Object.class);
        change.setAccessible(true);
        require((boolean) change.invoke(baseScreen, base.controls.get("formal_shared"), true)
                        && (boolean) change.invoke(firstScreen, first.controls.get("formal_shared"), true),
                "actual controls handler accepts base and first-copy changes");
        require(Boolean.TRUE.equals(first.cosmeticContext.control("formal_shared"))
                        && second.cosmeticContext.control("formal_shared") == null
                        && calls.getOrDefault(base, 0) == 1 && calls.getOrDefault(first, 0) == 1
                        && calls.getOrDefault(second, 0) == 0,
                "same-ID controls persist only in their selected instance context and callback owner");
        require(!(boolean) change.invoke(firstScreen, second.controls.get("formal_shared"), true),
                "a stale widget cannot submit another copy's definition");
        var press = screenType.getDeclaredMethod("pressControl", top.aihmc.omnimorph.avatar.control.AvatarControlDefinition.class);
        press.setAccessible(true);
        press.invoke(baseScreen, base.controls.get("formal_button"));
        press.invoke(firstScreen, first.controls.get("formal_button"));
        require(presses.getOrDefault(base, 0) == 1 && presses.getOrDefault(first, 0) == 1
                        && presses.getOrDefault(second, 0) == 0,
                "formal button handlers preserve base/copy ownership and budget");
        var active = screenType.getDeclaredMethod("controlsActive");
        active.setAccessible(true);
        var client = Minecraft.getInstance();
        var previousWorld = client.level;
        client.level = null;
        try {
            require(!(boolean) active.invoke(firstScreen), "world retirement invalidates the formal controls session");
        } finally {
            client.level = previousWorld;
        }
        firstScreen.removed();
        require(!(boolean) change.invoke(firstScreen, first.controls.get("formal_shared"), false)
                        && Boolean.TRUE.equals(first.cosmeticContext.control("formal_shared")),
                "removed controls screens reject retained widget callbacks");
        var replacement = new OutfitConfig(1, configuration.revision() + 1, base.outfitSource(), List.of(), List.of(), Map.of(), false);
        var next = new top.aihmc.omnimorph.model.outfit.OutfitComposition(replacement, OutfitLimits.defaults(), wearer,
                Map.of(), new top.aihmc.omnimorph.model.outfit.SkeletonIndex.Cache());
        await(user.publishOutfit(replacement, base, next, user.ysmLoads.begin(), () -> true), drain);
        require(!(boolean) active.invoke(baseScreen) && !(boolean) active.invoke(secondScreen)
                        && !(boolean) change.invoke(firstScreen, first.controls.get("formal_shared"), false)
                        && top.aihmc.omnimorph.gui.screens.OutfitControlsScreen.create(null, first, "root") == null,
                "resident replacement invalidates captured base/copy screens and rejects retired owners");
        base.controls.remove("formal_shared");
        base.controls.remove("formal_button");
        System.out.println("PASS formal controls handlers: resident identity, base/instance budgets, duplicate control IDs, disabled copies and world/replacement retirement; window and remote persistence pending");
    }

    private static void testWorldFrames(UserData user, LivingEntity wearer, Runnable drain) throws Exception {
        var state = new net.minecraft.client.renderer.entity.state.LivingEntityRenderState();
        Avatar base = user.getMainAvatar();
        top.aihmc.omnimorph.model.outfit.OutfitRenderBinding.capture(wearer, state, 0.5f);
        require(top.aihmc.omnimorph.model.outfit.OutfitRenderBinding.composition(state) != null
                        && AvatarManager.getAvatar(state) == base && AvatarManager.getEntity(state) == wearer
                        && top.aihmc.omnimorph.model.outfit.OutfitRenderBinding.renderMode(state)
                        == top.aihmc.omnimorph.model.rendering.EntityRenderMode.RENDER,
                "world extraction captures the actual resident base/composition pair without a GUI binding");
        user.clear();
        require(base.accelerationOwner().isCurrent() && AvatarManager.getAvatar(state) == base,
                "resident retirement preserves the captured base until deferred world/hand consumption ends");
        top.aihmc.omnimorph.model.outfit.OutfitRenderBinding.finishFrame();
        require(!base.accelerationOwner().isCurrent()
                        && !top.aihmc.omnimorph.model.outfit.OutfitRenderBinding.hasFrame(state),
                "frame completion releases retired bases and frozen composition leases");
        var vanilla = top.aihmc.omnimorph.model.outfit.OutfitPreparation.prepare(OutfitConfig.empty(), OutfitLimits.defaults(),
                wearer.getUUID(), UUID.randomUUID(), wearer, top.aihmc.omnimorph.permissions.PermissionManager.get(wearer.getUUID()),
                new top.aihmc.omnimorph.model.outfit.SkeletonIndex.Cache(),
                (reference, cancelled) -> { throw new AssertionError("no vanilla dependencies"); }, () -> false,
                top.aihmc.omnimorph.model.outfit.CosmeticInstanceContext.Scope.OWNER, null, reference -> false, reference -> false);
        await(user.publishOutfit(OutfitConfig.empty(), null, vanilla, user.ysmLoads.begin(), () -> true), drain);
        var vanillaState = new net.minecraft.client.renderer.entity.state.LivingEntityRenderState();
        top.aihmc.omnimorph.model.outfit.OutfitRenderBinding.capture(wearer, vanillaState);
        require(top.aihmc.omnimorph.model.outfit.OutfitRenderBinding.hasComposition(wearer)
                        && top.aihmc.omnimorph.model.outfit.OutfitRenderBinding.isVanilla(vanillaState)
                        && AvatarManager.getAvatar(vanillaState) == null,
                "vanilla outfits retain their composition without fetching a placeholder Avatar");
        try (var preview = new LibraryPreviewBinding(wearer, null, false)) {
            var previewState = new net.minecraft.client.renderer.entity.state.LivingEntityRenderState();
            preview.withPlayerContext(() -> {
                require(!top.aihmc.omnimorph.model.outfit.OutfitRenderBinding.hasComposition(wearer),
                        "an isolated vanilla preview suppresses the formal outfit");
                top.aihmc.omnimorph.model.outfit.OutfitRenderBinding.capture(wearer, previewState);
            });
            require(top.aihmc.omnimorph.model.outfit.OutfitRenderBinding.composition(previewState) == null
                            && !top.aihmc.omnimorph.model.outfit.OutfitRenderBinding.hasFrame(previewState),
                    "deferred preview states never inherit the resident outfit after their scope exits");
        } finally {
            LibraryPreviewBinding.finishFrame();
            top.aihmc.omnimorph.model.outfit.OutfitRenderBinding.finishFrame();
            user.clear();
        }
        System.out.println("PASS formal world/hand frame binding: native pair identity, deferred base retirement, vanilla and preview isolation; Mixin/GPU pending");
    }

    @SuppressWarnings("unchecked")
    private static void testWardrobe(LibraryClient client, Server server, UserData user, Runnable drain) throws Exception {
        var minecraft = Minecraft.getInstance();
        var previousPlayer = minecraft.player;
        var unsafeField = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
        unsafeField.setAccessible(true);
        var unsafe = (sun.misc.Unsafe) unsafeField.get(null);
        var player = (WardrobePlayer) unsafe.allocateInstance(WardrobePlayer.class);
        player.setUUID(server.player);
        minecraft.player = player;
        var screen = new top.aihmc.omnimorph.gui.screens.WardrobeScreen(null);
        var type = screen.getClass();
        field(type, "client").set(screen, client);
        var capabilities = client.capabilities();
        await(capabilities.future(), drain);
        field(type, "outfitCapabilitiesRequest").set(screen, capabilities);
        field(type, "outfitLimits").set(screen, OutfitLimits.defaults());
        try (var preview = new LibraryPreviewSession(client)) {
            var begin = type.getDeclaredMethod("beginOutfitDraft");
            begin.setAccessible(true);
            var draft = await((CompletableFuture<top.aihmc.omnimorph.model.outfit.OutfitDraft>) begin.invoke(screen), drain);
            require(draft.committed().equals(server.committed) && draft.committed().revision() > 0
                            && draft.committed().instances().getFirst().instanceId().equals(new UUID(0, 41)),
                    "wardrobe captures the whole server outfit and exact revision instead of an empty revision-zero draft");
            var cached = (Map<OutfitConfig.ResourceRef, net.minecraft.nbt.CompoundTag>)
                    field(type, "draftPreparedResources").get(screen);
            require(cached.containsKey(server.accessory.reference())
                            && cached.get(server.accessory.reference()).getCompoundOrEmpty("metadata")
                            .getStringOr("resource_kind", "").equals("ACCESSORY"),
                    "editing reuses the resident outfit dependencies without source requests or original device files");
            Avatar retained = user.getMainAvatar();
            draft.setEnabled(new UUID(0, 41), false);
            server.loseResponse = true;
            var apply = type.getDeclaredMethod("applyOutfitDraft");
            apply.setAccessible(true);
            apply.invoke(screen);
            var operation = (OutfitApplyService.Operation) field(type, "applyingOutfit").get(screen);
            failure(operation.future(), drain, "BUSY");
            require(field(type, "outfitDraft").get(screen) == draft && draft.dirty()
                            && user.getMainAvatar() == retained,
                    "wardrobe Apply failure retains the user's draft and resident base");
            var retry = type.getDeclaredMethod("retryOutfitDraft");
            retry.setAccessible(true);
            retry.invoke(screen);
            await(operation.future(), drain);
            var refreshed = await((CompletableFuture<top.aihmc.omnimorph.model.outfit.OutfitDraft>)
                    field(type, "draftStart").get(screen), drain);
            require(!refreshed.dirty() && refreshed.committed().equals(server.committed)
                            && !refreshed.configuration().instances().getFirst().enabled() && user.getMainAvatar() == retained,
                    "the retry UI replays one operation and refreshes the editor only after successful activation");
            long published = user.getOutfit().configuration().revision();
            server.revision++;
            server.committed = new OutfitConfig(1, server.revision, server.committed.base(), server.committed.instances(),
                    server.committed.virtualAnchors(), server.committed.displayOrdinals(), server.committed.showEquipment());
            refreshed.setEnabled(new UUID(0, 41), true);
            apply.invoke(screen);
            operation = (OutfitApplyService.Operation) field(type, "applyingOutfit").get(screen);
            require(await(operation.future(), drain) instanceof OutfitApplyResult.Conflict
                            && field(type, "outfitConflict").get(screen) != null
                            && field(type, "outfitDraft").get(screen) == refreshed && refreshed.dirty()
                            && user.getOutfit().configuration().revision() == published,
                    "the real wardrobe conflict handler preserves edits and never accepts or merges remote state");
            var reload = type.getDeclaredMethod("reloadServerOutfitDraft");
            reload.setAccessible(true);
            reload.invoke(screen);
            var current = await((CompletableFuture<top.aihmc.omnimorph.model.outfit.OutfitDraft>)
                    field(type, "draftStart").get(screen), drain);
            require(current != refreshed && current.committed().equals(server.committed)
                            && field(type, "outfitConflict").get(screen) == null
                            && user.getOutfit().configuration().revision() == published,
                    "explicit confirmed refresh replaces only the editing baseline, not world equipment");
            server.denySources = true;
            reload.invoke(screen);
            try {
                await((CompletableFuture<?>) field(type, "draftStart").get(screen), drain);
                throw new AssertionError("revoked wardrobe refresh accepted");
            } catch (ExecutionException expected) {
                require(field(type, "outfitDraft").get(screen) == current
                                && !((List<?>) field(type, "outfitIssues").get(screen)).isEmpty(),
                        "failed refresh retains the previous editing baseline and source/instance errors");
            }
            server.denySources = false;
            field(type, "preview").set(screen, preview);
            var cancel = type.getDeclaredMethod("cancelOutfitDraft");
            cancel.setAccessible(true);
            current.setEnabled(new UUID(0, 41), true);
            cancel.invoke(screen);
            require(!current.dirty() && user.getOutfit().configuration().revision() == published,
                    "cancel restores committed draft edits without changing formal equipment");
            System.out.println("PASS wardrobe Apply handlers: fixed current baseline, lost-response retry, conflict retention, "
                    + "explicit refresh, failed refresh issues and cancel; window/layout/CJK pending");
        } finally {
            var close = type.getDeclaredMethod("closeOutfitOperations");
            close.setAccessible(true);
            close.invoke(screen);
            minecraft.player = previousPlayer;
            drain.run();
        }
    }

    private static final class WardrobePlayer extends net.minecraft.client.player.LocalPlayer
            implements top.aihmc.omnimorph.mixin.EntityAccessor {
        private WardrobePlayer() {
            super(null, null, null, null, null, null, false, null);
        }

        @Override
        public net.minecraft.world.level.Level getLevel() {
            return Minecraft.getInstance().level;
        }
    }

    private static java.lang.reflect.Field field(Class<?> owner, String name) throws Exception {
        var field = owner.getDeclaredField(name);
        field.setAccessible(true);
        return field;
    }

    private static <T> T await(CompletableFuture<T> future, Runnable drain) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(15);
        while (!future.isDone() && System.nanoTime() < deadline) {
            drain.run();
            Thread.sleep(1);
        }
        return future.get(1, TimeUnit.SECONDS);
    }

    private static void failure(CompletableFuture<?> future, Runnable drain, String code) throws Exception {
        try {
            await(future, drain);
            throw new AssertionError("Expected " + code);
        } catch (ExecutionException error) {
            Throwable cause = error.getCause();
            while (cause instanceof java.util.concurrent.CompletionException && cause.getCause() != null) {
                cause = cause.getCause();
            }
            require(cause instanceof LibraryClient.Failure failure && failure.code.equals(code), "failure: " + cause);
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    private record Source(OutfitConfig.ResourceRef reference, CompoundTag data, byte[] bytes, String hash) {
        private static Source of(CompoundTag data) throws Exception {
            var output = new ByteArrayOutputStream();
            NbtIo.writeCompressed(top.aihmc.omnimorph.avatar.ysm.YsmSourcePayload.forOutput(data), output);
            byte[] bytes = output.toByteArray();
            boolean ysm = data.contains("ysm");
            var format = !ysm ? OutfitConfig.Format.FIGURA
                    : top.aihmc.omnimorph.avatar.ysm.YsmSourcePayload.isEncrypted(data.getCompoundOrEmpty("ysm"))
                    ? OutfitConfig.Format.YSM_ENCRYPTED : OutfitConfig.Format.YSM_PLAIN;
            return new Source(new OutfitConfig.ResourceRef(OutfitConfig.Source.LOCAL, UUID.randomUUID(), null, null,
                    null, "a".repeat(64), format), data, bytes, CloudHttp.sha256(bytes));
        }
    }

    private static final class Server implements AutoCloseable {
        private final UUID player;
        private final Source base;
        private final Source accessory;
        private final UserData user;
        private final HttpServer http;
        private final java.util.concurrent.ExecutorService workers = java.util.concurrent.Executors.newFixedThreadPool(3);
        private final Map<String, JsonObject> inputs = new HashMap<>();
        private final Map<String, JsonObject> receipts = new HashMap<>();
        private volatile OutfitConfig committed = OutfitConfig.empty();
        private volatile long revision;
        private volatile int commits, replays, prematurePublication;
        private volatile boolean loseResponse, advanceAfterReceipt, denySources;
        private volatile boolean wrongEquipmentRevision, tightenAfterReceipt, tightened;
        private volatile boolean observerBadBody;
        private volatile boolean observerBusy;
        private volatile boolean observerTighten;
        private final java.util.concurrent.atomic.AtomicInteger observerSnapshots = new java.util.concurrent.atomic.AtomicInteger();

        private Server(UUID player, CompoundTag base, CompoundTag accessory, UserData user) throws Exception {
            this.player = player;
            this.base = Source.of(base);
            this.accessory = Source.of(accessory);
            this.user = user;
            http = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            http.setExecutor(workers);
            http.createContext("/", this::handle);
            http.start();
        }

        private String origin() {
            return "http://127.0.0.1:" + http.getAddress().getPort();
        }

        private OutfitConfig configuration(long revision) {
            var instance = new OutfitConfig.Instance(new UUID(0, 41), accessory.reference(), 1, true,
                    OutfitConfig.AnchorRef.authorDefault("unknown_target"), OutfitConfig.Transform.identity(),
                    List.of(), Map.of(), Map.of());
            return new OutfitConfig(1, revision, base.reference(), List.of(instance), List.of(),
                    Map.of(accessory.reference().identity(), 1), false);
        }

        private final Map<UUID, JsonObject> instanceStates = new java.util.concurrent.ConcurrentHashMap<>();
        private final java.util.concurrent.atomic.AtomicInteger instanceStateReads = new java.util.concurrent.atomic.AtomicInteger();

        private void instanceState(HttpExchange exchange) throws java.io.IOException {
            if (exchange.getRequestMethod().equals("GET")) {
                instanceStateReads.incrementAndGet();
            }
            String[] path = exchange.getRequestURI().getPath().split("/");
            UUID id = UUID.fromString(path[path.length - 2]);
            var declaration = committed.instances().stream().filter(instance -> instance.instanceId().equals(id))
                    .findFirst().orElseThrow();
            JsonObject baseline = instanceStates.compute(id, (key, previous) -> {
                if (previous != null && previous.get("outfit_revision").getAsLong() == committed.revision()) {
                    return previous;
                }
                var value = new JsonObject();
                value.addProperty("outfit_revision", committed.revision());
                value.addProperty("instance_id", id.toString());
                value.add("resource", JSON.toJsonTree(declaration.resource()));
                value.addProperty("revision", 0);
                var state = new JsonObject();
                state.addProperty("generation", new UUID(0, 0).toString());
                state.addProperty("sequence", 0);
                state.addProperty("template_version", "");
                state.addProperty("template_signature", "");
                state.add("controls", JSON.toJsonTree(declaration.controls()));
                state.add("lua_state", JSON.toJsonTree(declaration.luaState()));
                state.add("animation", JsonNull.INSTANCE);
                value.add("state", state);
                return value;
            });
            if (exchange.getRequestMethod().equals("POST")) {
                var update = JsonParser.parseString(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8))
                        .getAsJsonObject();
                if (!update.getAsJsonObject("state").has("animation")) {
                    update.getAsJsonObject("state").add("animation", JsonNull.INSTANCE);
                }
                if (update.get("revision").getAsLong() != baseline.get("revision").getAsLong()) {
                    json(exchange, 409, "{\"code\":\"CONFLICT\"}");
                    return;
                }
                update.addProperty("revision", baseline.get("revision").getAsLong() + 1);
                instanceStates.put(id, update);
                baseline = update;
            }
            json(exchange, 200, baseline.toString());
        }

        private void handle(HttpExchange exchange) throws java.io.IOException {
            try (exchange) {
                require("Bearer apply-test".equals(exchange.getRequestHeaders().getFirst("authorization")), "ordinary authenticated Apply");
                String path = exchange.getRequestURI().getPath();
                if (path.endsWith("/state")) {
                    instanceState(exchange);
                } else if (path.endsWith("/capabilities")) {
                    if (observerBusy) {
                        json(exchange, 503, "{\"code\":\"BUSY\"}");
                        return;
                    }
                    var limits = OutfitLimits.defaults();
                    if (tightened) {
                        var changed = JSON.toJsonTree(limits).getAsJsonObject();
                        changed.addProperty("max_total_nodes", limits.maxTotalNodes() - 1);
                        limits = JSON.fromJson(changed, OutfitLimits.class);
                    }
                    var outfits = new LibraryModels.OutfitCapabilities(1, true, true, true, false,
                            limits.maxInstances(), limits.maxVirtualAnchors(), limits.maxDepth(), limits.maxResourceNodes(),
                            limits.maxTotalNodes(), limits.maxBoneOverrides(), limits.maxJsonBytes(), limits.maxTrails(),
                            limits.maxTrailSamples(), limits);
                    json(exchange, 200, JSON.toJson(new LibraryModels.Capabilities(true, 1, 67108864,
                            8, 2097152, 2048, 2048, List.of("zip", "ysm"), List.of(base.reference().format().name()),
                            List.of("general"), false, outfits)));
                } else if (path.contains("/outfits/players/")) {
                    observer(exchange);
                } else if (path.endsWith("/session")) {
                    json(exchange, 200, "{\"uuid\":\"" + player + "\",\"name\":\"Synthetic\",\"admin\":false,\"equipment_revision\":"
                            + (commits + (wrongEquipmentRevision ? 1 : 0)) + "}");
                } else if (path.endsWith("/outfits/current")) {
                    if (exchange.getRequestMethod().equals("GET")) {
                        json(exchange, 200, OutfitCodec.encode(committed));
                    } else {
                        apply(exchange);
                    }
                } else {
                    var input = JsonParser.parseString(new String(exchange.getRequestBody().readAllBytes(),
                            StandardCharsets.UTF_8)).getAsJsonObject();
                    Source source = input.getAsJsonObject("reference").get("local_id").getAsString()
                            .equals(base.reference().localId().toString()) ? base : accessory;
                    if (denySources) {
                        json(exchange, 403, "{\"code\":\"FORBIDDEN\"}");
                    } else if (path.endsWith("/resolve")) {
                        var binding = new JsonObject();
                        binding.add("reference", input.get("reference"));
                        binding.addProperty("asset_id", source == base ? "fixed_base" : "fixed_accessory");
                        binding.addProperty("asset_revision", 1);
                        binding.addProperty("raw_sha256", source.hash());
                        binding.addProperty("resource_kind", source == base ? "AVATAR" : "ACCESSORY");
                        binding.add("accessory_metadata", source == base ? JsonNull.INSTANCE : OutfitCodec.encodeAccessoryMetadata(
                                CosmeticMetadata.read(source.data().getCompoundOrEmpty("metadata").getStringOr("cosmetic_json", ""))));
                        var response = new JsonObject();
                        response.add("resource", binding);
                        json(exchange, 200, response.toString());
                    } else if (path.endsWith("/body")) {
                        exchange.getResponseHeaders().add("content-type", "application/octet-stream");
                        exchange.getResponseHeaders().add("x-omnimorph-sha256", source.hash());
                        exchange.sendResponseHeaders(200, source.bytes().length);
                        exchange.getResponseBody().write(source.bytes());
                    } else {
                        throw new AssertionError("Unexpected Apply path " + path);
                    }
                }
            }
        }

        private void apply(HttpExchange exchange) throws java.io.IOException {
            var input = JsonParser.parseString(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8)).getAsJsonObject();
            String operation = input.get("operation_id").getAsString();
            if (receipts.containsKey(operation)) {
                require(inputs.get(operation).equals(input), "replay preserves exact CAS input");
                replays++;
                json(exchange, 200, receipts.get(operation).toString());
                return;
            }
            long expected = input.get("expected_revision").getAsLong();
            if (expected != revision) {
                var conflict = new JsonObject();
                conflict.addProperty("code", "CONFLICT");
                conflict.addProperty("retryable", false);
                conflict.addProperty("request_id", UUID.randomUUID().toString());
                conflict.addProperty("current_revision", revision);
                conflict.add("configuration", JsonParser.parseString(OutfitCodec.encode(committed)));
                json(exchange, 409, conflict.toString());
                return;
            }
            if (user.getOutfit() != null && user.getOutfit().configuration().revision() > expected) {
                prematurePublication++;
            }
            var prepared = OutfitCodec.decode(input.getAsJsonObject("configuration").toString());
            revision++;
            commits++;
            committed = new OutfitConfig(1, revision, prepared.base(), prepared.instances(), prepared.virtualAnchors(),
                    prepared.displayOrdinals(), prepared.showEquipment());
            var applied = new JsonObject();
            var wire = input.getAsJsonObject("configuration").deepCopy();
            wire.addProperty("revision", revision);
            applied.add("configuration", wire);
            applied.addProperty("equipment_revision", commits);
            var response = new JsonObject();
            response.addProperty("schema_version", 1);
            response.addProperty("operation_id", operation);
            response.addProperty("current_revision", revision);
            response.add("applied", applied);
            inputs.put(operation, input);
            receipts.put(operation, response);
            if (advanceAfterReceipt) {
                revision++;
                committed = new OutfitConfig(1, revision, null, List.of(), List.of(), Map.of(), false);
            }
            if (tightenAfterReceipt) {
                tightened = true;
            }
            if (loseResponse) {
                loseResponse = false;
                json(exchange, 503, "{\"code\":\"BUSY\"}");
            } else {
                json(exchange, 200, response.toString());
            }
        }

        private void observer(HttpExchange exchange) throws java.io.IOException {
            require(exchange.getRequestMethod().equals("GET"), "observer requests are read-only");
            if (exchange.getRequestURI().getPath().contains("/resources/")) {
                require(exchange.getRequestURI().getQuery().equals("revision=" + revision), "observer resource revision is current");
                boolean baseSource = exchange.getRequestURI().getPath().endsWith("1".repeat(64));
                Source source = baseSource ? base : accessory;
                byte[] bytes = source.bytes().clone();
                if (observerBadBody) {
                    bytes[0] ^= 1;
                }
                exchange.getResponseHeaders().add("content-type", "application/octet-stream");
                exchange.getResponseHeaders().add("x-omnimorph-sha256", source.hash());
                exchange.sendResponseHeaders(200, bytes.length);
                exchange.getResponseBody().write(bytes);
                return;
            }
            var snapshot = new JsonObject();
            snapshot.addProperty("schema_version", 1);
            snapshot.addProperty("owner", player.toString());
            snapshot.addProperty("revision", revision);
            snapshot.addProperty("equipment_revision", revision);
            snapshot.add("configuration", denySources || revision == 0 ? JsonNull.INSTANCE : JsonParser.parseString(OutfitCodec.encode(committed)));
            var resources = new com.google.gson.JsonArray();
            if (!denySources && revision != 0) {
                for (Source source : List.of(base, accessory)) {
                    boolean baseSource = source == base;
                    var resource = new JsonObject();
                    String id = (baseSource ? "1" : "2").repeat(64);
                    resource.addProperty("id", id);
                    resource.add("reference", JSON.toJsonTree(source.reference()));
                    resource.addProperty("resource_kind", baseSource ? "AVATAR" : "ACCESSORY");
                    resource.add("accessory_metadata", baseSource ? JsonNull.INSTANCE : OutfitCodec.encodeAccessoryMetadata(
                            CosmeticMetadata.read(source.data().getCompoundOrEmpty("metadata").getStringOr("cosmetic_json", ""))));
                    resource.addProperty("distribution_format", source.reference().format() == OutfitConfig.Format.FIGURA ? 1 : 2);
                    resource.addProperty("byte_length", source.bytes().length);
                    resource.addProperty("raw_sha256", source.hash());
                    resource.addProperty("content_sha256", "0".repeat(64));
                    resource.addProperty("schema_sha256", "0".repeat(64));
                    resource.addProperty("relative_path", top.aihmc.omnimorph.backend2.library.OutfitObserverSnapshot.resourcePath(player, revision, id));
                    resources.add(resource);
                }
            }
            snapshot.add("resources", resources);
            if (observerTighten && observerSnapshots.incrementAndGet() == 6) {
                tightened = true;
            }
            json(exchange, 200, snapshot.toString());
        }

        private void json(HttpExchange exchange, int status, String json) throws java.io.IOException {
            byte[] body = json.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("content-type", "application/json");
            exchange.sendResponseHeaders(status, body.length);
            exchange.getResponseBody().write(body);
        }

        @Override
        public void close() {
            http.stop(0);
            workers.shutdownNow();
        }
    }
}
