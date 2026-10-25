package top.aihmc.omnimorph.lua.api.particle;

import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.state.level.ParticlesRenderState;
import org.luaj.vm2.LuaError;
import org.figuramc.figura.avatar.Avatar;
import top.aihmc.omnimorph.model.outfit.CosmeticInstanceContext;
import top.aihmc.omnimorph.model.outfit.OutfitLimits;
import top.aihmc.omnimorph.model.outfit.OutfitTrailBudget;
import org.figuramc.figura.model.rendering.texture.FiguraTexture;
import top.aihmc.omnimorph.model.ysm.animation.YsmRenderFrameClock;

import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Client-thread bookkeeping; execution IDs clean instances while wearer IDs share capacity. */
public final class CustomParticleRuntime {
    private static final Map<CustomParticle, UUID> LIVE = new LinkedHashMap<>();
    private static final Map<UUID, Integer> COUNTS = new HashMap<>();
    private static final Map<UUID, Integer> TICK_CALLBACKS = new HashMap<>();
    private static final Map<UUID, Integer> FRAME_CALLBACKS = new HashMap<>();
    private static long callbackFrame = Long.MIN_VALUE;
    private static long callbackDispatchFrame = Long.MIN_VALUE;
    private static boolean dispatchingCallbacks;
    private static final Map<FiguraTexture, FiguraTexture.Lease> FRAME_TEXTURES = new IdentityHashMap<>();

    private CustomParticleRuntime() {
    }

    static boolean activate(CustomParticle particle) {
        Avatar owner = particle.owner();
        if (particle.world() == null || particle.world() != Minecraft.getInstance().level) {
            return false;
        }
        var context = owner.cosmeticContext;
        var preview = particle.preview();
        if (preview != owner.previewParticles) {
            return false;
        }
        if (preview != null) {
            if (!preview.accepts(owner, particle.world())) {
                return false;
            }
        } else if (owner.usesPreviewParticles()) {
            // Draft particles need the isolated preview renderer, never the live world engine.
            return false;
        }
        OutfitLimits limits = particle.limits();
        UUID wearer = preview != null ? preview.budgetId() : context == null ? owner.owner : context.wearerId();
        if (LIVE.containsKey(particle) || !particle.isAlive() || !particle.canActivateCallbacks()
                || !ParticleDynamicLights.canActivate(particle)
                || particle.layerCount() > limits.maxParticleLayers()
                || COUNTS.getOrDefault(wearer, 0) >= limits.maxCustomParticles()
                || particle.independentRibbon() && OutfitTrailBudget.count(wearer) >= limits.maxTrails()) {
            return false;
        }
        particle.retainTexture();
        LIVE.put(particle, wearer);
        COUNTS.merge(wearer, 1, Integer::sum);
        if (particle.independentRibbon()) {
            OutfitTrailBudget.acquire(particle, wearer, limits.maxTrails());
        }
        try {
            if (preview != null) {
                preview.activated(particle);
            } else if (particle.lightSettings().enabled() && ParticleDynamicLights.intervalSelects(particle)) {
                try {
                    ParticleDynamicLights.update(particle, particle.lightSettings());
                } catch (LuaError rejected) {
                    if (!"PARTICLE_DYNAMIC_LIGHT_COUNT_LIMIT".equals(rejected.getMessage())) {
                        throw rejected;
                    }
                    // The wearer already lights the first negotiated number of particles: keep this particle
                    // alive without illumination instead of rejecting it or failing the script.
                    particle.disableLightFromPolicy();
                }
            }
        } catch (RuntimeException | Error failure) {
            particle.remove();
            throw failure;
        }
        return true;
    }

    static void removed(CustomParticle particle) {
        if (particle.preview() != null) {
            particle.preview().removed(particle);
        }
        UUID wearer = LIVE.remove(particle);
        if (wearer != null) {
            COUNTS.computeIfPresent(wearer, (key, count) -> count == 1 ? null : count - 1);
            OutfitTrailBudget.release(particle);
        }
    }

    static void changeRibbonMode(CustomParticle particle, boolean before, boolean after) {
        if (before == after) {
            return;
        }
        UUID wearer = LIVE.get(particle);
        if (after && (particle.limits().maxTrails() == 0
                || wearer != null && OutfitTrailBudget.count(wearer) >= particle.limits().maxTrails())) {
            throw new LuaError("CUSTOM_PARTICLE_TRAIL_LIMIT");
        }
        if (wearer != null) {
            if (after) {
                OutfitTrailBudget.acquire(particle, wearer, particle.limits().maxTrails());
            } else {
                OutfitTrailBudget.release(particle);
            }
        }
    }

    public static void beginTick() {
        top.aihmc.omnimorph.model.outfit.OutfitInstructionBudget.beginParticleTick();
        TICK_CALLBACKS.clear();
    }

    static boolean claimCallback(CustomParticle particle, boolean frame) {
        UUID wearer = LIVE.get(particle);
        if (wearer == null) {
            return false;
        }
        return claimCallback(wearer, particle.limits(), frame);
    }

    public static boolean claimAnimationCallback(Avatar owner) {
        var context = owner.cosmeticContext;
        if (context == null) {
            return false;
        }
        return owner.previewParticles == null ? claimCallback(context.wearerId(), context.limits(), false)
                : claimCallback(owner.previewParticles.budgetId(), owner.previewParticles.limits(), false);
    }

    private static boolean claimCallback(UUID wearer, OutfitLimits limits, boolean frame) {
        if (frame && callbackFrame != YsmRenderFrameClock.currentFrame()) {
            callbackFrame = YsmRenderFrameClock.currentFrame();
            FRAME_CALLBACKS.clear();
        }
        var counts = frame ? FRAME_CALLBACKS : TICK_CALLBACKS;
        int maximum = frame ? limits.maxParticleCallbacksPerFrame() : limits.maxParticleCallbacksPerTick();
        int used = counts.getOrDefault(wearer, 0);
        if (used >= maximum) {
            return false;
        }
        counts.put(wearer, used + 1);
        return true;
    }

    public static void runFrameCallbacks(float partialTick) {
        long frame = YsmRenderFrameClock.currentFrame();
        if (dispatchingCallbacks || callbackDispatchFrame == frame) {
            return;
        }
        callbackDispatchFrame = frame;
        dispatchingCallbacks = true;
        try {
            for (CustomParticle particle : LIVE.keySet().toArray(CustomParticle[]::new)) {
                if (particle.preview() == null) {
                    particle.runFrameCallback(partialTick);
                }
            }
        } finally {
            dispatchingCallbacks = false;
        }
    }

    static int availableRibbonVertices(CustomParticle particle) {
        return OutfitTrailBudget.availableVertices(LIVE.get(particle), YsmRenderFrameClock.currentFrame(),
                particle.limits().maxRibbonVertices());
    }

    static void chargeRibbonVertices(CustomParticle particle, int vertices) {
        UUID wearer = LIVE.get(particle);
        if (wearer == null) {
            throw new IllegalArgumentException("RIBBON_VERTEX_LIMIT");
        }
        OutfitTrailBudget.chargeVertices(wearer, YsmRenderFrameClock.currentFrame(),
                particle.limits().maxRibbonVertices(), vertices);
    }

    public static void extractRibbons(ParticlesRenderState state, Frustum frustum, Camera camera, float partialTick) {
        // Activation order makes competition for the shared frame quota deterministic.
        for (CustomParticle particle : LIVE.keySet().toArray(CustomParticle[]::new)) {
            if (!particle.ribbon() || particle.preview() != null) {
                continue;
            }
            UUID wearer = LIVE.get(particle);
            if (particle.world() != Minecraft.getInstance().level
                    || OutfitTrailBudget.count(wearer) > particle.limits().maxTrails()) {
                particle.remove();
                continue;
            }
            var ribbon = particle.extractRibbon(camera, frustum, partialTick, availableRibbonVertices(particle));
            if (ribbon != null) {
                state.add(ribbon);
            }
        }
    }

    static void retainFrame(FiguraTexture texture, FiguraTexture.Lease particleLease) {
        FRAME_TEXTURES.computeIfAbsent(texture, key -> particleLease.retain());
    }

    public static int liveCount(UUID wearer) {
        return COUNTS.getOrDefault(wearer, 0);
    }

    static boolean hasWorldParticles(Avatar owner) {
        return LIVE.keySet().stream().anyMatch(particle -> particle.preview() == null && particle.owner() == owner);
    }

    public static void clearOwner(UUID executionId) {
        ParticleDynamicLights.beginBatch();
        try {
            for (CustomParticle particle : LIVE.keySet().toArray(CustomParticle[]::new)) {
                if (executionId == null || particle.preview() == null && particle.owner().owner.equals(executionId)) {
                    particle.remove();
                }
            }
        } finally {
            ParticleDynamicLights.endBatch();
        }
    }

    public static void finishFrame() {
        var leases = FRAME_TEXTURES.values().toArray(FiguraTexture.Lease[]::new);
        FRAME_TEXTURES.clear();
        try {
            for (var lease : leases) {
                lease.close();
            }
        } finally {
            top.aihmc.omnimorph.model.outfit.OutfitInstructionBudget.finishParticleFrame();
        }
    }
}
