package top.aihmc.omnimorph.render.acceleration;

public interface AccelerationBackend extends AutoCloseable {
    enum Operation {
        VERTEX_TRANSFORM,
        GPU_DRAW
    }

    boolean supports(Operation operation, DrawBatch batch);

    /** Returns owned xyzw/normal/UV results in MeshHandle layout, without drawing or modifying input. */
    double[] transform(DrawBatch batch);

    default void beginFrame() {
    }

    default void invalidateResources() {
    }

    @Override
    default void close() {
    }
}
