package top.aihmc.omnimorph.model.outfit;

import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/** Client-thread capacity shared by controller trails and custom-particle ribbons. */
public final class OutfitTrailBudget {
    private static final Map<Object, UUID> SLOTS = new IdentityHashMap<>();
    private static final Map<UUID, Integer> COUNTS = new HashMap<>();
    private static final Map<UUID, Integer> VERTICES = new HashMap<>();
    private static long vertexFrame = Long.MIN_VALUE;

    private OutfitTrailBudget() {
    }

    public static boolean acquire(Object trail, UUID wearer, int maximum) {
        Objects.requireNonNull(trail);
        Objects.requireNonNull(wearer);
        UUID existing = SLOTS.get(trail);
        if (existing != null) {
            if (!existing.equals(wearer)) {
                throw new IllegalArgumentException("TRAIL_OWNER_CHANGED");
            }
            return count(wearer) <= maximum;
        }
        if (count(wearer) >= maximum) {
            return false;
        }
        SLOTS.put(trail, wearer);
        COUNTS.merge(wearer, 1, Integer::sum);
        return true;
    }

    public static void release(Object trail) {
        UUID wearer = SLOTS.remove(trail);
        if (wearer != null) {
            COUNTS.computeIfPresent(wearer, (key, count) -> count == 1 ? null : count - 1);
        }
        // Submitted vertices remain charged until the next frame, even after removal.
    }

    public static int count(UUID wearer) {
        return COUNTS.getOrDefault(wearer, 0);
    }

    public static int availableVertices(UUID wearer, long frame, int maximum) {
        if (vertexFrame != frame) {
            vertexFrame = frame;
            VERTICES.clear();
        }
        return Math.max(0, maximum - VERTICES.getOrDefault(wearer, 0));
    }

    public static void chargeVertices(UUID wearer, long frame, int maximum, int vertices) {
        Objects.requireNonNull(wearer);
        if (vertices < 0 || vertices > availableVertices(wearer, frame, maximum)) {
            throw new IllegalArgumentException("RIBBON_VERTEX_LIMIT");
        }
        if (vertices != 0) {
            VERTICES.merge(wearer, vertices, Integer::sum);
        }
    }
}
