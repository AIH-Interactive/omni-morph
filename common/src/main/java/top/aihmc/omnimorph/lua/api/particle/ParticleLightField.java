package top.aihmc.omnimorph.lua.api.particle;

import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectMaps;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import net.minecraft.core.SectionPos;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/** Immutable render-light snapshot. Worker threads never read particle objects or mutable Lua state. */
public final class ParticleLightField {
    public record Source(Vec3 position, int level, double radius) {
        public Source {
            if (position == null || !Double.isFinite(position.x) || !Double.isFinite(position.y)
                    || !Double.isFinite(position.z) || level < 0 || level > 15
                    || !Double.isFinite(radius) || radius <= 0 || radius > 32) {
                throw new IllegalArgumentException("INVALID_PARTICLE_LIGHT");
            }
        }

        public void sections(Consumer<Section> consumer) {
            if (level == 0) {
                return;
            }
            // AO/light probes can sample across a section boundary, so include one block of padding.
            int minX = section(position.x - radius - 1);
            int minY = section(position.y - radius - 1);
            int minZ = section(position.z - radius - 1);
            int maxX = section(position.x + radius + 1);
            int maxY = section(position.y + radius + 1);
            int maxZ = section(position.z + radius + 1);
            for (long x = minX; x <= maxX; x++) {
                for (long y = minY; y <= maxY; y++) {
                    for (long z = minZ; z <= maxZ; z++) {
                        consumer.accept(new Section((int) x, (int) y, (int) z));
                    }
                }
            }
        }
    }

    public record Section(int x, int y, int z) {
        long key() {
            return SectionPos.asLong(x, y, z);
        }
    }

    public static final class Snapshot {
        private final Object world;
        private final List<Source> sources;
        private final Long2ObjectMap<List<Source>> sections;

        public Snapshot(Object world, List<Source> sources) {
            this.world = world;
            this.sources = List.copyOf(sources);
            var building = new Long2ObjectOpenHashMap<List<Source>>();
            for (Source source : this.sources) {
                if (source.level() == 0) {
                    continue;
                }
                source.sections(section -> building.computeIfAbsent(section.key(), key -> new ArrayList<>()).add(source));
            }
            var frozen = new Long2ObjectOpenHashMap<List<Source>>();
            building.long2ObjectEntrySet().forEach(entry -> frozen.put(entry.getLongKey(), List.copyOf(entry.getValue())));
            sections = Long2ObjectMaps.unmodifiable(frozen);
        }

        public int apply(Object queryWorld, double x, double y, double z, int original) {
            if (world == null || world != queryWorld || sources.isEmpty() || (original & 0xFFFF) >= 240
                    || !Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)) {
                return original;
            }
            var candidates = sections.get(SectionPos.asLong(section(x), section(y), section(z)));
            if (candidates == null) {
                return original;
            }
            int block = original & 0xFFFF;
            for (Source source : candidates) {
                double dx = x - source.position().x;
                double dy = y - source.position().y;
                double dz = z - source.position().z;
                double squared = dx * dx + dy * dy + dz * dz;
                if (squared >= source.radius() * source.radius()) {
                    continue;
                }
                int level = (int) Math.round(source.level() * (1 - Math.sqrt(squared) / source.radius()));
                block = Math.max(block, level << 4);
                if (block >= 240) {
                    break;
                }
            }
            return (original & 0xFFFF0000) | block;
        }
    }

    private ParticleLightField() {
    }

    private static int section(double coordinate) {
        return (int) Math.floor(coordinate / 16);
    }
}
