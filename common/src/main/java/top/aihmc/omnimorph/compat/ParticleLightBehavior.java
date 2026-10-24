package top.aihmc.omnimorph.compat;

import dev.lambdaurora.lambdynlights.api.behavior.DynamicLightBehavior;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import top.aihmc.omnimorph.lua.api.particle.ParticleDynamicLights;

/**
 * A single bounded LambDynamicLights behavior covering every particle light source of the current frame.
 *
 * <p>LambDynamicLights queries {@link #lightAtPos} for blocks inside {@link #getBoundingBox()}. The light model
 * matches the built-in particle field: {@code level * (1 - distance / radius)} with a hard radius cutoff, so
 * switching providers does not change the brightness the author configured.
 */
final class ParticleLightBehavior implements DynamicLightBehavior {
    private static final BoundingBox EMPTY = new BoundingBox(0, 0, 0, 0, 0, 0);

    private long lastVersion = -1;
    private BoundingBox bounds = EMPTY;

    @Override
    public double lightAtPos(BlockPos pos, double falloffRatio) {
        var snapshot = ParticleDynamicLights.publishedSnapshot();
        if (snapshot == null || snapshot.world() == null || snapshot.world() != Minecraft.getInstance().level) {
            return 0.0;
        }
        return snapshot.luminanceAt(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
    }

    @Override
    public BoundingBox getBoundingBox() {
        var snapshot = ParticleDynamicLights.publishedSnapshot();
        if (snapshot == null || snapshot.world() == null || snapshot.world() != Minecraft.getInstance().level
                || snapshot.sources().isEmpty()) {
            bounds = EMPTY;
            return bounds;
        }
        int minX = Integer.MAX_VALUE;
        int minY = Integer.MAX_VALUE;
        int minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int maxY = Integer.MIN_VALUE;
        int maxZ = Integer.MIN_VALUE;
        for (var source : snapshot.sources()) {
            minX = Math.min(minX, (int) Math.floor(source.position().x - source.radius()));
            minY = Math.min(minY, (int) Math.floor(source.position().y - source.radius()));
            minZ = Math.min(minZ, (int) Math.floor(source.position().z - source.radius()));
            maxX = Math.max(maxX, (int) Math.ceil(source.position().x + source.radius()));
            maxY = Math.max(maxY, (int) Math.ceil(source.position().y + source.radius()));
            maxZ = Math.max(maxZ, (int) Math.ceil(source.position().z + source.radius()));
        }
        // One block of padding covers interpolation and neighbor sampling at the boundary.
        bounds = new BoundingBox(minX - 1, minY - 1, minZ - 1, maxX + 1, maxY + 1, maxZ + 1);
        return bounds;
    }

    @Override
    public boolean hasChanged() {
        long version = ParticleDynamicLights.publishedVersion();
        if (version == lastVersion) {
            return false;
        }
        lastVersion = version;
        return true;
    }
}
