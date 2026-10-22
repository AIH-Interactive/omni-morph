package top.aihmc.omnimorph.lua.api;

import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaFunction;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import top.aihmc.omnimorph.avatar.Avatar;
import top.aihmc.omnimorph.lua.FiguraLuaRuntime;
import top.aihmc.omnimorph.lua.LuaWhitelist;
import top.aihmc.omnimorph.lua.api.particle.CustomParticleRuntime;
import top.aihmc.omnimorph.lua.docs.LuaTypeDoc;
import top.aihmc.omnimorph.model.outfit.OutfitInstructionBudget;

import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.function.BooleanSupplier;

/** Instance-local notifications; producers never invoke Lua inside an animation traversal. */
@LuaWhitelist
@LuaTypeDoc(name = "CosmeticAnimationsAPI", value = "cosmetic_animations")
public final class CosmeticAnimationsAPI implements AutoCloseable {
    private record Notification(String animation, String type, String marker, double time,
                                long generation, long tick, BooleanSupplier current) {
        private LuaTable table() {
            var value = new LuaTable();
            value.rawset("animation", LuaValue.valueOf(animation));
            value.rawset("type", LuaValue.valueOf(type));
            value.rawset("marker", LuaValue.valueOf(marker));
            value.rawset("time", LuaValue.valueOf(time));
            value.rawset("generation", LuaValue.valueOf((double) generation));
            return value;
        }
    }

    private final Avatar owner;
    private final ArrayDeque<Notification> pending = new ArrayDeque<>();
    private LuaFunction callback;
    private FiguraLuaRuntime runtime;
    private long dispatchedTick = Long.MIN_VALUE;
    private boolean dispatching;
    private boolean closed;
    private String error;

    public CosmeticAnimationsAPI(Avatar owner) {
        if (owner.cosmeticContext == null) {
            throw new IllegalArgumentException("COSMETIC_INSTANCE_REQUIRED");
        }
        this.owner = owner;
        if (owner.cosmeticAnimations != null) {
            owner.cosmeticAnimations.close();
        }
        owner.cosmeticAnimations = this;
    }

    @LuaWhitelist
    public CosmeticAnimationsAPI onEvent(LuaFunction callback) {
        if (closed) {
            throw new LuaError("ANIMATION_EVENTS_CLOSED");
        }
        if (callback != null && owner.cosmeticContext.limits().maxParticleCallbacksPerTick() == 0) {
            throw new LuaError("ANIMATION_EVENT_CALLBACK_DISABLED");
        }
        this.callback = callback;
        runtime = owner.luaRuntime;
        pending.clear();
        error = null;
        return this;
    }

    @LuaWhitelist
    public String getEventError() {
        return error;
    }

    public static void offer(Avatar owner, String animation, String type, String marker,
                             double time, long generation, BooleanSupplier current) {
        var api = owner == null ? null : owner.cosmeticAnimations;
        if (api == null || api.closed || api.callback == null || api.runtime != owner.luaRuntime) {
            return;
        }
        var limits = owner.cosmeticContext.limits();
        int maximumBytes = limits.maxStateStringBytes();
        if (!Double.isFinite(time) || generation < 0 || current == null
                || !bounded(animation, maximumBytes) || !bounded(type, maximumBytes)
                || !bounded(marker, maximumBytes)) {
            api.error = "ANIMATION_EVENT_VALUE_LIMIT";
            return;
        }
        if (api.pending.size() >= limits.maxParticleCallbacksPerTick()) {
            api.error = "ANIMATION_EVENT_QUEUE_LIMIT";
            return;
        }
        api.pending.addLast(new Notification(animation, type, marker, time, generation,
                OutfitInstructionBudget.particleTick(), current));
    }

    private static boolean bounded(String value, int maximumBytes) {
        return value != null && value.length() <= maximumBytes
                && value.getBytes(StandardCharsets.UTF_8).length <= maximumBytes;
    }

    public void dispatch() {
        long tick = OutfitInstructionBudget.particleTick();
        if (closed || dispatching || dispatchedTick == tick || callback == null) {
            return;
        }
        dispatchedTick = tick;
        dispatching = true;
        try {
            // Fix the candidate count before callbacks. Reentrant producers wait until another tick.
            int candidates = pending.size();
            for (int index = 0; index < candidates && !pending.isEmpty() && !closed; index++) {
                Notification event = pending.peekFirst();
                if (event.tick() >= tick) {
                    break;
                }
                pending.removeFirst();
                if (owner.scriptError || !owner.loaded || runtime == null || runtime != owner.luaRuntime) {
                    pending.clear();
                    return;
                }
                if (!event.current().getAsBoolean()) {
                    continue;
                }
                if (runtime.particleCallbackAllowance(false) <= 0) {
                    error = "ANIMATION_EVENT_INSTRUCTION_LIMIT";
                    continue;
                }
                if (!CustomParticleRuntime.claimAnimationCallback(owner)) {
                    error = "ANIMATION_EVENT_CALLBACK_LIMIT";
                    continue;
                }
                try {
                    if (runtime.run(callback, owner.tick, event.table()) != null) {
                        continue;
                    }
                } catch (Exception | StackOverflowError failure) {
                    owner.scriptError = true;
                    owner.luaRuntime = null;
                    owner.clearParticles();
                    top.aihmc.omnimorph.FiguraMod.LOGGER.warn("Animation event callback failed for {}", owner.owner, failure);
                }
                error = "ANIMATION_EVENT_CALLBACK_FAILED";
                if (owner.scriptError) {
                    close();
                } else {
                    pending.clear();
                }
                return;
            }
        } finally {
            dispatching = false;
        }
    }

    public void discardPending() {
        pending.clear();
    }

    @Override
    public void close() {
        closed = true;
        pending.clear();
        callback = null;
        runtime = null;
    }
}
