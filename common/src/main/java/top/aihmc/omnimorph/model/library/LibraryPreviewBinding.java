package top.aihmc.omnimorph.model.library;

import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.figuramc.figura.avatar.Avatar;
import top.aihmc.omnimorph.model.outfit.OutfitComposition;
import top.aihmc.omnimorph.model.outfit.OutfitLimits;
import top.aihmc.omnimorph.lua.api.particle.PreviewParticleScope;
import top.aihmc.omnimorph.lua.api.particle.PreviewParticleFrame;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;

import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Scoped player/model lookup and ownership retained by deferred GUI frames. */
public final class LibraryPreviewBinding implements AutoCloseable {
    private static final Map<Entity, LibraryPreviewBinding> ENTITIES = new IdentityHashMap<>();
    private static final Map<UUID, LibraryPreviewBinding> PLAYERS = new HashMap<>();
    private static final Map<EntityRenderState, LibraryPreviewBinding> FRAMES = new IdentityHashMap<>();
    private static final Map<EntityRenderState, OutfitComposition.Frame> OUTFIT_FRAMES = new IdentityHashMap<>();
    private static final Map<EntityRenderState, Vec3> PARTICLE_ORIGINS = new IdentityHashMap<>();
    private static final Map<EntityRenderState, PreviewParticleFrame> PARTICLE_FRAMES = new IdentityHashMap<>();
    private final LivingEntity entity;
    private final Avatar avatar;
    private final UUID player;
    private final PreviewParticleScope particles;
    private OutfitComposition composition;
    private Object selectedBone;
    private int frames;
    private boolean closed;

    LibraryPreviewBinding(LivingEntity entity, Avatar avatar) {
        this(entity, avatar, true);
    }

    LibraryPreviewBinding(LivingEntity entity, Avatar avatar, boolean persistent) {
        this(entity, avatar, persistent, false);
    }

    LibraryPreviewBinding(LivingEntity entity, Avatar avatar, boolean persistent, boolean worldParticles) {
        this.entity = entity;
        this.avatar = avatar;
        this.player = avatar == null ? entity.getUUID() : avatar.owner;
        if (persistent && (ENTITIES.containsKey(entity) || PLAYERS.containsKey(player))) {
            throw new IllegalStateException("PREVIEW_ALREADY_BOUND");
        }
        if (avatar != null && worldParticles) {
            avatar.enableWorldParticles();
        }
        this.particles = !worldParticles && entity.level() instanceof net.minecraft.client.multiplayer.ClientLevel level
                ? new PreviewParticleScope(level, OutfitLimits.defaults()) : null;
        if (avatar != null && particles != null) {
            particles.bind(avatar);
        }
        if (!persistent) {
            return;
        }
        ENTITIES.put(entity, this);
        PLAYERS.put(player, this);
    }

    /** Limit current-player model lookup to preview work; deferred frames retain their own binding. */
    public void withPlayerContext(Runnable action) {
        LibraryPreviewBinding previousEntity = ENTITIES.put(entity, this);
        LibraryPreviewBinding previousPlayer = PLAYERS.put(player, this);
        try {
            action.run();
        } finally {
            if (previousEntity == null) {
                ENTITIES.remove(entity, this);
            } else {
                ENTITIES.put(entity, previousEntity);
            }
            if (previousPlayer == null) {
                PLAYERS.remove(player, this);
            } else {
                PLAYERS.put(player, previousPlayer);
            }
        }
    }

    public static Avatar avatar(Entity entity) {
        LibraryPreviewBinding binding = ENTITIES.get(entity);
        return binding == null ? null : binding.avatar;
    }

    public static Avatar avatar(UUID player) {
        LibraryPreviewBinding binding = PLAYERS.get(player);
        return binding == null ? null : binding.avatar;
    }

    public static Avatar avatar(EntityRenderState frame) {
        LibraryPreviewBinding binding = FRAMES.get(frame);
        return binding == null ? null : binding.avatar;
    }

    public static Entity entity(EntityRenderState frame) {
        LibraryPreviewBinding binding = FRAMES.get(frame);
        return binding == null ? null : binding.entity;
    }

    /** A deliberate vanilla selection must not fall through to the equipped Avatar. */
    public static boolean isVanilla(Entity entity) {
        LibraryPreviewBinding binding = ENTITIES.get(entity);
        return binding != null && binding.avatar == null;
    }

    public static boolean isVanilla(UUID player) {
        LibraryPreviewBinding binding = PLAYERS.get(player);
        return binding != null && binding.avatar == null;
    }

    public static boolean isVanilla(EntityRenderState frame) {
        LibraryPreviewBinding binding = FRAMES.get(frame);
        return binding != null && binding.avatar == null;
    }

    public void setComposition(OutfitComposition composition) {
        if (closed) {
            throw new IllegalStateException("PREVIEW_CLOSED");
        }
        if (this.composition != composition) {
            if (composition != null && particles != null) {
                composition.bindPreviewParticles(particles);
            }
            if (this.composition != null) {
                this.composition.close();
            }
            this.composition = composition;
        }
    }

    public void tick() {
        if (closed) {
            return;
        }
        if (particles != null) {
            withBaseBudget(particles::tick);
        }
        if (composition != null) {
            composition.tick(avatar);
        } else if (avatar != null) {
            avatar.tick();
        }
    }

    public void selectedBone(Object part) {
        selectedBone = closed ? null : part;
    }

    public void withBaseBudget(Runnable preparation) {
        if (composition != null && avatar != null) {
            composition.withBaseBudget(avatar, preparation);
        } else {
            preparation.run();
        }
    }

    /** The composition captured during extraction survives later selection changes. */
    public static OutfitComposition.Frame composition(EntityRenderState frame) {
        return OUTFIT_FRAMES.get(frame);
    }

    public static boolean hasComposition(Entity entity) {
        LibraryPreviewBinding binding = ENTITIES.get(entity);
        return binding != null && !binding.closed && binding.composition != null;
    }

    public static void capture(Entity entity, EntityRenderState frame) {
        capture(entity, frame, 1);
    }

    public static void capture(Entity entity, EntityRenderState frame, float partialTick) {
        LibraryPreviewBinding binding = ENTITIES.get(entity);
        if (binding == null || FRAMES.containsKey(frame)) {
            return;
        }
        FRAMES.put(frame, binding);
        // UIHelper binds before native extraction, while the independent Figura state has no world coordinates.
        if (binding.particles != null) {
            PARTICLE_ORIGINS.put(frame, entity.getPosition(partialTick));
        }
        binding.frames++;
        if (binding.composition != null) {
            var outfit = binding.composition.retain();
            outfit.capturePreviewSelection(binding.selectedBone);
            OUTFIT_FRAMES.put(frame, outfit);
        }
    }

    /** Runs after actual GUI drawing, including exceptional completion and renderer shutdown. */
    public static void finishFrame() {
        PARTICLE_FRAMES.clear();
        PARTICLE_ORIGINS.clear();
        PreviewBoneHighlight.finishFrame();
        LibraryThumbnailService.finishFrame();
        List<OutfitComposition.Frame> outfits = List.copyOf(OUTFIT_FRAMES.values());
        OUTFIT_FRAMES.clear();
        for (OutfitComposition.Frame outfit : outfits) {
            outfit.close();
        }
        List<LibraryPreviewBinding> retained = List.copyOf(FRAMES.values());
        FRAMES.clear();
        for (LibraryPreviewBinding binding : retained) {
            binding.frames--;
            if (binding.closed && binding.frames == 0 && binding.avatar != null) {
                binding.avatar.clean();
            }
        }
    }

    /** Each submitted view owns one frozen extraction, independent of later entity movement or selection. */
    public static PreviewParticleFrame particles(EntityRenderState frame, Quaternionf viewRotation, float partialTick) {
        return PARTICLE_FRAMES.computeIfAbsent(frame, key -> {
            LibraryPreviewBinding binding = FRAMES.get(key);
            if (binding == null || binding.particles == null) {
                return PreviewParticleFrame.empty();
            }
            PreviewParticleFrame[] extracted = {PreviewParticleFrame.empty()};
            binding.withBaseBudget(() -> extracted[0] = binding.particles.extract(
                    PARTICLE_ORIGINS.get(key), viewRotation, partialTick));
            return extracted[0];
        });
    }

    @Override
    public void close() {
        if (closed) {
            return;
        }
        closed = true;
        selectedBone = null;
        if (particles != null) {
            particles.close();
        }
        ENTITIES.remove(entity, this);
        PLAYERS.remove(player, this);
        if (composition != null) {
            composition.close();
            composition = null;
        }
        if (frames == 0 && avatar != null) {
            avatar.clean();
        }
    }
}
