package top.aihmc.omnimorph.render;

import net.minecraft.world.entity.Entity;

/**
 * One entity render call, used as an identity key for per-render bindings.
 * <p>
 * MC 1.21.1 renders entities immediately and has no entity render state. Renderer
 * mixins create a new frame for every render call, so preview, outfit and particle
 * bindings stay scoped to a single draw exactly like the former render-state keys.
 */
public final class EntityFrame {
    private final Entity entity;
    private final float partialTick;

    public EntityFrame(Entity entity, float partialTick) {
        this.entity = entity;
        this.partialTick = partialTick;
    }

    public Entity entity() {
        return entity;
    }

    public int entityId() {
        return entity.getId();
    }

    public float partialTick() {
        return partialTick;
    }
}
