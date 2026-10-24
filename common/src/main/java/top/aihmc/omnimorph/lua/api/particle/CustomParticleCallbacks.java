package top.aihmc.omnimorph.lua.api.particle;

import net.minecraft.client.Minecraft;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaFunction;
import org.figuramc.figura.FiguraMod;
import top.aihmc.omnimorph.model.outfit.OutfitInstructionBudget;
import top.aihmc.omnimorph.model.ysm.animation.YsmRenderFrameClock;

import org.figuramc.figura.lua.api.particle.LuaParticle;
/** One particle's callback handles. Execution is always through its owner's existing bounded Lua runtime. */
final class CustomParticleCallbacks implements AutoCloseable {
    private final CustomParticle particle;
    private LuaFunction tick;
    private LuaFunction frame;
    private OutfitInstructionBudget.ParticleCallbackLease lease;
    private long tickEpoch = Long.MIN_VALUE;
    private long frameEpoch = Long.MIN_VALUE;
    private boolean running;
    private String error;

    CustomParticleCallbacks(CustomParticle particle) {
        this.particle = particle;
    }

    void set(boolean perFrame, LuaFunction function) {
        if (function != null) {
            if (!particle.isAlive()) {
                throw new LuaError("Cannot register callbacks on a removed particle");
            }
            int maximum = perFrame ? particle.limits().maxParticleCallbacksPerFrame()
                    : particle.limits().maxParticleCallbacksPerTick();
            if (maximum == 0) {
                throw new LuaError(perFrame ? "PARTICLE_FRAME_CALLBACKS_DISABLED" : "PARTICLE_TICK_CALLBACKS_DISABLED");
            }
        }
        if (perFrame) {
            frame = function;
        } else {
            tick = function;
        }
        error = null;
        updateLease();
    }

    boolean canActivate() {
        return (tick == null || particle.limits().maxParticleCallbacksPerTick() > 0)
                && (frame == null || particle.limits().maxParticleCallbacksPerFrame() > 0);
    }

    void updateLease() {
        if (particle.active() && (tick != null || frame != null)) {
            if (lease == null) {
                lease = OutfitInstructionBudget.retainParticleCallbacks(particle.owner());
            }
        } else if (lease != null) {
            lease.close();
            lease = null;
        }
    }

    boolean running() {
        return running;
    }

    String error() {
        return error;
    }

    void run(boolean perFrame, float delta) {
        LuaFunction function = perFrame ? frame : tick;
        if (function == null || running || !particle.active() || particle.world() != Minecraft.getInstance().level) {
            return;
        }
        long epoch = perFrame ? YsmRenderFrameClock.currentFrame() : OutfitInstructionBudget.particleTick();
        if (perFrame ? frameEpoch == epoch : tickEpoch == epoch) {
            return;
        }
        // Claim identity before invoking Lua: callback replacement, removal and reentry never repeat this epoch.
        if (perFrame) {
            frameEpoch = epoch;
        } else {
            tickEpoch = epoch;
        }
        var owner = particle.owner();
        var runtime = owner.luaRuntime;
        if (!owner.loaded || owner.scriptError || runtime == null) {
            error = "CALLBACK_OWNER_INACTIVE";
            return;
        }
        if (runtime.particleCallbackAllowance(perFrame) <= 0) {
            error = "CALLBACK_INSTRUCTION_LIMIT";
            return;
        }
        if (!CustomParticleRuntime.claimCallback(particle, perFrame)) {
            error = perFrame ? "CALLBACK_FRAME_LIMIT" : "CALLBACK_TICK_LIMIT";
            return;
        }
        error = null;
        running = true;
        try {
            var result = runtime.run(function, perFrame ? owner.render : owner.tick,
                    new LuaParticle("custom", particle, owner), delta);
            if (result == null) {
                error = owner.scriptError ? "CALLBACK_ERROR" : "CALLBACK_INSTRUCTION_LIMIT";
            }
        } catch (Exception | StackOverflowError failure) {
            // A failing author callback (including failure while reporting its error) cannot unwind the engine.
            error = "CALLBACK_ERROR";
            owner.scriptError = true;
            owner.luaRuntime = null;
            owner.clearParticles();
            FiguraMod.LOGGER.warn("Custom particle callback failed for {}", owner.owner, failure);
        } finally {
            running = false;
        }
    }

    @Override
    public void close() {
        tick = null;
        frame = null;
        if (lease != null) {
            lease.close();
            lease = null;
        }
    }
}
