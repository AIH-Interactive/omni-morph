package top.aihmc.omnimorph.lua.api.particle;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockAndLightGetter;
import top.aihmc.omnimorph.mixin.particle.ParticleLightRegionAccessor;
import top.aihmc.omnimorph.model.outfit.OutfitInstructionBudget;

import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Client-owned sources and section invalidation; published light lookup is safe on chunk compiler workers. */
public final class ParticleDynamicLights {
    private record Entry(UUID wearer, ParticleLightSettings settings) {
    }

    private static final Map<CustomParticle, Entry> SOURCES = new LinkedHashMap<>();
    private static final Map<UUID, Integer> COUNTS = new HashMap<>();
    private static Map<CustomParticle, ParticleLightField.Source> terrain = Map.of();
    private static Map<CustomParticle, ParticleLightField.Source> entityField = Map.of();
    private static Object entityWorld;
    private static Object terrainWorld;
    private static long terrainTick = Long.MIN_VALUE;
    private static int batches;
    private static boolean pending;
    private static volatile ParticleLightField.Snapshot published = new ParticleLightField.Snapshot(null, List.of());
    private static volatile ParticleLightField.Snapshot terrainPublished = published;

    private ParticleDynamicLights() {
    }

    static boolean canActivate(CustomParticle particle) {
        if (particle.preview() != null) {
            return particle.preview().canActivateLight(particle);
        }
        var settings = particle.lightSettings();
        try {
            settings.validate(particle.limits());
            return !settings.enabled() || liveCount(wearer(particle)) < particle.limits().maxDynamicLights();
        } catch (org.luaj.vm2.LuaError rejected) {
            return false;
        }
    }

    static void update(CustomParticle particle, ParticleLightSettings settings) {
        if (particle.preview() != null) {
            particle.preview().validateLight(particle, settings);
            return;
        }
        settings.validate(particle.limits());
        if (!settings.enabled()) {
            remove(particle);
            return;
        }
        UUID wearer = wearer(particle);
        Entry previous = SOURCES.get(particle);
        if (previous == null && liveCount(wearer) >= particle.limits().maxDynamicLights()) {
            throw new org.luaj.vm2.LuaError("PARTICLE_DYNAMIC_LIGHT_COUNT_LIMIT");
        }
        if (previous == null) {
            COUNTS.merge(wearer, 1, Integer::sum);
        }
        SOURCES.put(particle, new Entry(wearer, settings));
        changed();
    }

    static void remove(CustomParticle particle) {
        Entry previous = SOURCES.remove(particle);
        if (previous != null) {
            COUNTS.computeIfPresent(previous.wearer(), (key, count) -> count == 1 ? null : count - 1);
            changed();
        }
    }

    static void beginBatch() {
        batches++;
    }

    static void endBatch() {
        batches--;
        if (batches == 0 && pending) {
            changed();
        }
    }

    private static void changed() {
        pending = true;
        if (batches == 0) {
            updateFrame(1);
        }
    }

    private static UUID wearer(CustomParticle particle) {
        var context = particle.owner().cosmeticContext;
        return context == null ? particle.owner().owner : context.wearerId();
    }

    public static int liveCount(UUID wearer) {
        return COUNTS.getOrDefault(wearer, 0);
    }

    public static void updateFrame(float partialTick) {
        if (batches != 0 || !Float.isFinite(partialTick)) {
            return;
        }
        var client = Minecraft.getInstance();
        ClientLevel world = client.level;
        if (SOURCES.isEmpty() && terrain.isEmpty() && !pending && terrainWorld == world) {
            return;
        }
        var current = new IdentityHashMap<CustomParticle, ParticleLightField.Source>();
        var interpolated = new IdentityHashMap<CustomParticle, ParticleLightField.Source>();
        var iterator = SOURCES.entrySet().iterator();
        while (iterator.hasNext()) {
            var entry = iterator.next();
            CustomParticle particle = entry.getKey();
            var settings = entry.getValue().settings();
            boolean valid = world != null && particle.active() && particle.world() == world;
            try {
                settings.validate(particle.limits());
                valid &= liveCount(entry.getValue().wearer()) <= particle.limits().maxDynamicLights();
                if (valid) {
                    var currentPosition = particle.lightPosition(1);
                    var interpolatedPosition = particle.lightPosition(partialTick);
                    // An attached Ribbon may await its first valid anchor sample. Keep its reserved light
                    // capacity, but publish no illumination while its controller history is unavailable.
                    if (currentPosition != null && interpolatedPosition != null) {
                        current.put(particle, new ParticleLightField.Source(currentPosition,
                                settings.level(), settings.radius()));
                        interpolated.put(particle, new ParticleLightField.Source(interpolatedPosition,
                                settings.level(), settings.radius()));
                    }
                }
            } catch (org.luaj.vm2.LuaError | IllegalArgumentException rejected) {
                valid = false;
            }
            if (!valid) {
                iterator.remove();
                current.remove(particle);
                interpolated.remove(particle);
                COUNTS.computeIfPresent(entry.getValue().wearer(), (key, count) -> count == 1 ? null : count - 1);
                particle.disableLightFromPolicy();
                pending = true;
            }
        }
        if (entityWorld != world || !interpolated.equals(entityField)) {
            entityField = Map.copyOf(interpolated);
            entityWorld = world;
            published = new ParticleLightField.Snapshot(world, List.copyOf(interpolated.values()));
        }
        long tick = OutfitInstructionBudget.particleTick();
        if (pending || terrainWorld != world || terrainTick != tick) {
            var dirty = new HashSet<ParticleLightField.Section>();
            if (terrainWorld == world) {
                terrain.forEach((particle, source) -> {
                    if (!source.equals(current.get(particle))) {
                        source.sections(dirty::add);
                    }
                });
            }
            current.forEach((particle, source) -> {
                if (terrainWorld != world || !source.equals(terrain.get(particle))) {
                    source.sections(dirty::add);
                }
            });
            terrain = Map.copyOf(current);
            // Terrain uses tick positions, not a changing partial-frame snapshot during asynchronous compilation.
            terrainPublished = new ParticleLightField.Snapshot(world, List.copyOf(current.values()));
            terrainWorld = world;
            terrainTick = tick;
            pending = false;
            if (world != null && client.levelRenderer != null) {
                for (var section : dirty) {
                    client.levelRenderer.setSectionDirty(section.x(), section.y(), section.z());
                }
            }
        }
    }

    public static int apply(BlockAndLightGetter getter, BlockPos position, int original) {
        Object world = getter instanceof ClientLevel level ? level
                : getter instanceof ParticleLightRegionAccessor region ? region.figura$lightLevel() : null;
        return terrainPublished.apply(world, position.getX() + .5, position.getY() + .5, position.getZ() + .5, original);
    }

    public static int apply(Object world, double x, double y, double z, int original) {
        return published.apply(world, x, y, z, original);
    }
}
