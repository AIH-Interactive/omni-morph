package top.aihmc.omnimorph.lua.api.particle;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import org.luaj.vm2.LuaError;
import org.figuramc.figura.avatar.Avatar;
import top.aihmc.omnimorph.model.outfit.OutfitInstructionBudget;
import top.aihmc.omnimorph.model.outfit.OutfitLimits;
import top.aihmc.omnimorph.model.ysm.animation.YsmRenderFrameClock;

import java.util.Collections;
import java.util.Collection;
import java.util.IdentityHashMap;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/** One preview generation's particles. Neither native world ticking nor world light registration owns them. */
public final class PreviewParticleScope implements AutoCloseable {
    private final UUID budgetId = UUID.randomUUID();
    private final ClientLevel world;
    private final Set<Avatar> owners = Collections.newSetFromMap(new IdentityHashMap<>());
    private final Set<CustomParticle> particles = new LinkedHashSet<>();
    private OutfitLimits limits;
    private long tick = Long.MIN_VALUE;
    private long frame = Long.MIN_VALUE;
    private boolean closed;

    public PreviewParticleScope(ClientLevel world, OutfitLimits limits) {
        this.world = Objects.requireNonNull(world);
        this.limits = Objects.requireNonNull(limits);
    }

    public UUID budgetId() {
        return budgetId;
    }

    public OutfitLimits limits() {
        return limits;
    }

    public void useLimits(OutfitLimits limits) {
        this.limits = Objects.requireNonNull(limits);
    }

    public void bind(Avatar owner) {
        bindAll(java.util.List.of(owner));
    }

    public void bindAll(Collection<Avatar> additions) {
        for (Avatar owner : additions) {
            checkOwner(owner);
        }
        for (Avatar owner : additions) {
            owners.add(owner);
            owner.previewParticles = this;
        }
    }

    private void checkOwner(Avatar owner) {
        if (closed || owner.previewParticles != null && owner.previewParticles != this
                || CustomParticleRuntime.hasWorldParticles(owner)) {
            throw new IllegalStateException("PARTICLE_PREVIEW_OWNER_ALREADY_ACTIVE");
        }
    }

    boolean accepts(Avatar owner, ClientLevel world) {
        return !closed && owners.contains(owner) && this.world == world && Minecraft.getInstance().level == world;
    }

    void activated(CustomParticle particle) {
        particles.add(particle);
    }

    void removed(CustomParticle particle) {
        particles.remove(particle);
    }

    boolean canActivateLight(CustomParticle particle) {
        try {
            validateLight(particle, particle.lightSettings());
            return true;
        } catch (LuaError rejected) {
            return false;
        }
    }

    void validateLight(CustomParticle particle, ParticleLightSettings settings) {
        settings.validate(limits);
        if (settings.enabled()) {
            int otherLights = 0;
            for (var active : particles) {
                if (active != particle && active.lightSettings().enabled()) {
                    otherLights++;
                }
            }
            if (otherLights >= limits.maxDynamicLights()) {
                throw new LuaError("PARTICLE_DYNAMIC_LIGHT_COUNT_LIMIT");
            }
        }
    }

    public int liveCount() {
        return particles.size();
    }

    public boolean isActive() {
        return !closed && currentWorld();
    }

    public void clearOwner(Avatar owner) {
        for (var particle : particles.toArray(CustomParticle[]::new)) {
            if (particle.owner() == owner) {
                particle.remove();
            }
        }
    }

    public void retireOwner(Avatar owner) {
        owners.remove(owner);
        clearOwner(owner);
    }

    public void tick() {
        if (closed || !currentWorld() || Minecraft.getInstance().isPaused()
                || tick == OutfitInstructionBudget.particleTick()) {
            return;
        }
        tick = OutfitInstructionBudget.particleTick();
        int lights = 0;
        for (var particle : particles.toArray(CustomParticle[]::new)) {
            if (particles.size() > limits.maxCustomParticles() || particle.layerCount() > limits.maxParticleLayers()) {
                particle.remove();
            } else {
                if (particle.lightSettings().enabled()) {
                    try {
                        particle.lightSettings().validate(limits);
                        if (lights >= limits.maxDynamicLights()) {
                            throw new LuaError("PARTICLE_DYNAMIC_LIGHT_COUNT_LIMIT");
                        }
                        lights++;
                    } catch (LuaError rejected) {
                        particle.disableLightFromPolicy();
                    }
                }
                particle.tick();
            }
        }
    }

    public void prepareFrame(float partialTick) {
        if (closed || !currentWorld() || !Float.isFinite(partialTick) || frame == YsmRenderFrameClock.currentFrame()) {
            return;
        }
        frame = YsmRenderFrameClock.currentFrame();
        for (var particle : particles.toArray(CustomParticle[]::new)) {
            particle.runFrameCallback(partialTick);
        }
    }

    private boolean currentWorld() {
        if (Minecraft.getInstance().level != world) {
            close();
            return false;
        }
        return true;
    }

    public PreviewParticleFrame extract(net.minecraft.world.phys.Vec3 origin,
                                        org.joml.Quaternionf viewRotation, float partialTick) {
        if (closed || !currentWorld() || !Float.isFinite(partialTick)) {
            return PreviewParticleFrame.empty();
        }
        prepareFrame(partialTick);
        if (particles.isEmpty()) {
            return PreviewParticleFrame.empty();
        }
        return PreviewParticleFrame.extract(particles.toArray(CustomParticle[]::new), origin, viewRotation, partialTick);
    }

    @Override
    public void close() {
        if (closed) {
            return;
        }
        closed = true;
        owners.clear();
        for (var particle : particles.toArray(CustomParticle[]::new)) {
            particle.remove();
        }
    }
}
