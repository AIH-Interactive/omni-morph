package top.aihmc.omnimorph.render;

import net.minecraft.world.entity.Entity;
import top.aihmc.omnimorph.model.outfit.OutfitRenderBinding;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * One entity render call, used as an identity key for per-render bindings.
 * <p>
 * MC 1.21.1 renders entities immediately and has no entity render state. Renderer
 * mixins open a frame for every render call, so preview, outfit and particle
 * bindings stay scoped to a single draw exactly like the former render-state keys.
 * <p>
 * GUI code that binds a preview before drawing registers its frame with
 * {@link #prepare}; the next {@link #begin} for that entity reuses it. Any other
 * render is a world frame and captures the wearer's outfit like the former
 * extraction hook did.
 */
public final class EntityFrame {
    private static final ThreadLocal<EntityFrame> PREPARED = new ThreadLocal<>();
    private static final ThreadLocal<Deque<EntityFrame>> ACTIVE = ThreadLocal.withInitial(ArrayDeque::new);

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

    /** Registers a GUI frame that the next render of its entity on this thread will use. */
    public static void prepare(EntityFrame frame) {
        PREPARED.set(frame);
    }

    /** Opens the frame for an entity render call; pair every call with {@link #end}. */
    public static EntityFrame begin(Entity entity, float partialTick) {
        EntityFrame prepared = PREPARED.get();
        EntityFrame frame;
        if (prepared != null && prepared.entity == entity) {
            PREPARED.remove();
            frame = prepared;
        } else {
            frame = new EntityFrame(entity, partialTick);
            OutfitRenderBinding.capture(entity, frame, partialTick, true);
        }
        ACTIVE.get().push(frame);
        return frame;
    }

    /** The innermost frame being rendered on this thread, or {@code null} outside entity rendering. */
    public static EntityFrame current() {
        return ACTIVE.get().peek();
    }

    public static void end(EntityFrame frame) {
        Deque<EntityFrame> active = ACTIVE.get();
        if (active.peek() == frame) {
            active.pop();
        } else {
            active.remove(frame);
        }
    }
}
