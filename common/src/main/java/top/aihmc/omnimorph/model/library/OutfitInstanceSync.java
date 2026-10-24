package top.aihmc.omnimorph.model.library;

import org.figuramc.figura.avatar.Avatar;
import org.figuramc.figura.avatar.AvatarManager;
import org.figuramc.figura.avatar.UserData;
import top.aihmc.omnimorph.backend2.library.LibraryClient;
import top.aihmc.omnimorph.backend2.library.OutfitInstanceState;
import top.aihmc.omnimorph.model.outfit.CosmeticInstanceContext;
import top.aihmc.omnimorph.model.ysm.sync.YsmSyncCodec;
import top.aihmc.omnimorph.model.ysm.sync.YsmSyncMessage;
import top.aihmc.omnimorph.model.ysm.sync.YsmSyncModel;

import java.util.ArrayList;
import java.util.Base64;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

/** Per-instance state over the existing authenticated library lane and notification stream. */
public final class OutfitInstanceSync implements AutoCloseable {
    private record Key(UUID owner, UUID instance) { }
    private final LibraryClient client;
    private final Consumer<UUID> invalidated;
    private final Map<Key, Entry> entries = new LinkedHashMap<>();
    private long nextRequest;
    private int cursor;

    public OutfitInstanceSync(LibraryClient client, Consumer<UUID> invalidated) {
        this.client = client;
        this.invalidated = invalidated;
    }

    public void tick(long tick, Set<UUID> observers) {
        var owners = new HashSet<>(observers);
        owners.add(client.session().player());
        var current = new HashSet<Key>();
        for (UUID owner : owners) {
            UserData user = AvatarManager.getLoadedUser(owner);
            var outfit = user == null ? null : user.getOutfit();
            if (outfit == null) {
                continue;
            }
            for (var instance : outfit.composition().runtimeInstances().entrySet()) {
                var key = new Key(owner, instance.getKey());
                current.add(key);
                Entry old = entries.get(key);
                if (old == null || old.outfit != outfit || old.avatar != instance.getValue()) {
                    if (old != null) {
                        old.close();
                    }
                    entries.put(key, new Entry(key, user, outfit, instance.getValue()));
                }
            }
        }
        entries.entrySet().removeIf(entry -> {
            if (!current.contains(entry.getKey())) {
                entry.getValue().close();
                return true;
            }
            return false;
        });
        if (tick < nextRequest || entries.isEmpty()) {
            return;
        }
        var candidates = new ArrayList<>(entries.values());
        for (int checked = 0; checked < candidates.size(); checked++) {
            Entry entry = candidates.get(Math.floorMod(cursor++, candidates.size()));
            if (entry.pending == null && tick >= entry.retryAt && entry.request(tick)) {
                // Ten state requests/second leave room in the shared 32-message account budget.
                nextRequest = tick + 2;
                break;
            }
        }
    }

    public void changed(UUID owner, UUID instance, long outfitRevision, long stateRevision) {
        Entry entry = entries.get(new Key(owner, instance));
        if (entry != null && entry.outfit.configuration().revision() == outfitRevision
                && stateRevision > entry.revision) {
            entry.notifiedRevision = Math.max(entry.notifiedRevision, stateRevision);
            entry.dirty = true;
        }
    }

    @Override
    public void close() {
        entries.values().forEach(Entry::close);
        entries.clear();
    }

    private final class Entry implements AutoCloseable {
        private final Key key;
        private final UserData user;
        private final UserData.OutfitState outfit;
        private final Avatar avatar;
        private final CosmeticInstanceContext context;
        private final Object world = net.minecraft.client.Minecraft.getInstance().level;
        private LibraryClient.Request<OutfitInstanceState> pending;
        private long revision = -1;
        private long notifiedRevision;
        private long sequence;
        private long lastWatermark;
        private long retryAt;
        private boolean dirty = true;
        private boolean retired;
        private YsmSyncModel model;
        private Map<String, Object> lastControls;
        private Map<String, Object> lastLua;
        private YsmSyncMessage.State lastAnimation;

        private Entry(Key key, UserData user, UserData.OutfitState outfit, Avatar avatar) {
            this.key = key;
            this.user = user;
            this.outfit = outfit;
            this.avatar = avatar;
            context = avatar.cosmeticContext;
        }

        private boolean current() {
            return !retired && client.current() && entries.get(key) == this
                    && net.minecraft.client.Minecraft.getInstance().level == world
                    && AvatarManager.getLoadedUser(key.owner()) == user && user.getOutfit() == outfit
                    && avatar.accelerationOwner().isCurrent();
        }

        private boolean owner() {
            return context.scope() == CosmeticInstanceContext.Scope.OWNER
                    && key.owner().equals(client.session().player());
        }

        private boolean request(long tick) {
            try {
                return requestState(tick);
            } catch (Exception invalid) {
                failed(tick, invalid);
                return false;
            }
        }

        private boolean requestState(long tick) throws Exception {
            if (!current()) {
                return false;
            }
            if (dirty || revision < 0) {
                Map<String, Object> controls = context.controls();
                Map<String, Object> lua = context.luaState();
                boolean localChanges = owner() && revision >= 0
                        && (!controls.equals(lastControls) || !lua.equals(lastLua));
                pending = client.instanceState(key.owner(), key.instance());
                pending.future().whenComplete((received, failure) -> {
                    if (!current()) {
                        return;
                    }
                    pending = null;
                    if (failure != null) {
                        failed(tick, failure);
                        return;
                    }
                    try {
                        requireIdentity(received);
                        if (received.revision() < revision) {
                            throw new IllegalArgumentException("INSTANCE_STATE_STALE");
                        }
                        boolean baseline = revision < 0 || received.revision() != revision + 1;
                        boolean hydrate = !owner() || !localChanges
                                && controls.equals(context.controls()) && lua.equals(context.luaState());
                        if (hydrate) {
                            apply(received, baseline);
                        }
                        revision = received.revision();
                        sequence = received.state().generation().equals(context.executionId())
                                ? received.state().sequence() : 0;
                        lastControls = hydrate ? context.controls() : received.state().controls();
                        lastLua = hydrate ? context.luaState() : received.state().luaState();
                        dirty = revision < notifiedRevision;
                    } catch (Exception invalid) {
                        failed(tick, invalid);
                    }
                });
                return true;
            }
            if (!owner()) {
                return false;
            }
            Map<String, Object> controls = context.controls();
            Map<String, Object> lua = context.luaState();
            YsmSyncMessage.State animation = null;
            String encoded = null;
            if (avatar.getYsmRuntime() != null) {
                if (model == null) {
                    model = new YsmSyncModel(avatar.getYsmRuntime());
                }
                animation = model.capture();
            }
            if (controls.equals(lastControls) && lua.equals(lastLua) && java.util.Objects.equals(animation, lastAnimation)
                    && (model == null || avatar.getYsmRuntime().functions().syncWatermark() == lastWatermark)) {
                return false;
            }
            long outgoing = sequence + 1;
            if (model != null) {
                var functions = avatar.getYsmRuntime().functions();
                var events = functions.drainSyncEvents();
                if (sequence == 0) {
                    events = List.of();
                }
                var message = new YsmSyncMessage(key.owner(), context.executionId(), model.contentHash(), model.schemaHash(),
                        0, outgoing, -1, tick, true, functions.syncWatermark(), animation, events);
                encoded = Base64.getEncoder().encodeToString(YsmSyncCodec.encode(message));
            }
            var state = new OutfitInstanceState(outfit.configuration().revision(), key.instance(), context.resource(), revision,
                    new OutfitInstanceState.State(context.executionId(), outgoing, controls, lua, encoded,
                            model == null ? "" : avatar.getYsmRuntime().defaultAnimationTemplateVersion(),
                            model == null ? "" : avatar.getYsmRuntime().defaultAnimationSignature()));
            pending = client.publishInstanceState(state);
            YsmSyncMessage.State sentAnimation = animation;
            long sentWatermark = model == null ? 0 : avatar.getYsmRuntime().functions().syncWatermark();
            pending.future().whenComplete((accepted, failure) -> {
                if (!current()) {
                    return;
                }
                pending = null;
                if (failure != null) {
                    dirty = true;
                    failed(tick, failure);
                    return;
                }
                try {
                    requireIdentity(accepted);
                    if (!accepted.state().equals(state.state()) || accepted.revision() != state.revision() + 1) {
                        throw new IllegalArgumentException("INSTANCE_STATE_RECEIPT_CHANGED");
                    }
                    revision = accepted.revision();
                    sequence = outgoing;
                    lastControls = controls;
                    lastLua = lua;
                    lastAnimation = sentAnimation;
                    lastWatermark = sentWatermark;
                    dirty = revision < notifiedRevision;
                } catch (Exception invalid) {
                    failed(tick, invalid);
                }
            });
            return true;
        }

        private void requireIdentity(OutfitInstanceState state) {
            if (state.outfitRevision() != outfit.configuration().revision() || !state.instanceId().equals(key.instance())
                    || !state.resource().equals(context.resource())) {
                throw new IllegalArgumentException("INSTANCE_STATE_IDENTITY_CHANGED");
            }
        }

        private void apply(OutfitInstanceState state, boolean baseline) throws Exception {
            var runtime = avatar.getYsmRuntime();
            try (var effects = runtime == null ? null : runtime.functions().beginRemoteState()) {
                if (!owner() && state.state().animation() != null) {
                    if (runtime == null || !state.state().templateVersion().equals(runtime.defaultAnimationTemplateVersion())
                            || !state.state().templateSignature().equals(runtime.defaultAnimationSignature())) {
                        throw new IllegalArgumentException("INSTANCE_ANIMATION_TEMPLATE_CHANGED");
                    }
                    if (model == null) {
                        model = new YsmSyncModel(runtime);
                    }
                    var message = YsmSyncCodec.decode(Base64.getDecoder().decode(state.state().animation()));
                    if (!message.owner().equals(key.owner()) || !message.contentHash().equals(model.contentHash())
                            || !message.schemaHash().equals(model.schemaHash()) || !message.snapshot()
                            || !message.session().equals(state.state().generation()) || message.sequence() != state.state().sequence()) {
                        throw new IllegalArgumentException("INSTANCE_ANIMATION_IDENTITY_CHANGED");
                    }
                    model.apply(message.state(), baseline ? List.of() : message.events());
                }
                context.restoreState(state.state().controls(), state.state().luaState());
                state.state().controls().forEach((id, value) -> avatar.controls.applyRemoteValue(avatar, id, value));
            }
        }

        private void failed(long tick, Throwable failure) {
            while (failure instanceof java.util.concurrent.CompletionException && failure.getCause() != null) {
                failure = failure.getCause();
            }
            retryAt = tick + 20;
            if (failure instanceof LibraryClient.Failure response) {
                if (response.status == 403 || response.status == 404) {
                    invalidated.accept(key.owner());
                }
            } else if (!(failure instanceof java.util.concurrent.CancellationException)) {
                invalidated.accept(key.owner());
            }
        }

        @Override
        public void close() {
            retired = true;
            if (pending != null) {
                pending.cancel();
            }
        }
    }
}
