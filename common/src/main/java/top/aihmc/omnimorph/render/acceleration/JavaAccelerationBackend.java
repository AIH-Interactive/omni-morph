package top.aihmc.omnimorph.render.acceleration;

/** Numerical reference uses the same matrix operations as the existing renderers. */
public final class JavaAccelerationBackend implements AccelerationBackend {
    @Override
    public boolean supports(Operation operation, DrawBatch batch) {
        return operation == Operation.VERTEX_TRANSFORM;
    }

    @Override
    public double[] transform(DrawBatch batch) {
        double[] result = new double[Math.multiplyExact(batch.mesh().vertexCount(), MeshHandle.STRIDE)];
        batch.pose().transform(batch.mesh(), result);
        return result;
    }
}
