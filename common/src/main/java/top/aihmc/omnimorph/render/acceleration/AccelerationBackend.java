package top.aihmc.omnimorph.render.acceleration;

public interface AccelerationBackend {
    enum Operation {
        VERTEX_TRANSFORM
    }

    boolean supports(Operation operation, DrawBatch batch);

    /** Returns owned xyzw/normal/UV results in MeshHandle layout, without drawing or modifying input. */
    double[] transform(DrawBatch batch);
}
