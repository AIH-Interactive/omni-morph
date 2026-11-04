package top.aihmc.omnimorph.model.rendering.nodeRenderer;

import net.minecraft.client.renderer.SubmitNodeStorage;

import java.util.Objects;

/** The native collector for callback-generated tasks, including isolated picture-in-picture frames. */
public final class FeaturePreparationScope implements AutoCloseable {
    private static final ThreadLocal<SubmitNodeStorage> ACTIVE = new ThreadLocal<>();
    private final SubmitNodeStorage previous;

    public FeaturePreparationScope(SubmitNodeStorage storage) {
        previous = ACTIVE.get();
        ACTIVE.set(storage);
    }

    public static SubmitNodeStorage current() {
        return Objects.requireNonNull(ACTIVE.get(), "No native feature preparation is active");
    }

    @Override
    public void close() {
        if (previous == null) {
            ACTIVE.remove();
        } else {
            ACTIVE.set(previous);
        }
    }
}
