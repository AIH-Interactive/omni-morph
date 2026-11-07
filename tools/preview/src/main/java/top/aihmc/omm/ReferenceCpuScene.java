package top.aihmc.omm;

import java.util.ArrayList;
import java.util.List;
import org.joml.Matrix4f;

/** Independent original animation state per placement, feeding original CPU renderer output. */
final class ReferenceCpuScene implements CpuPreviewWindow.FrameSource {
    private final Object model;
    private final boolean nativeSimd;
    private final float[] restPose;
    private final List<ReferenceVertexOutput> outputs = new ArrayList<>();
    private final List<ReferenceModelAnimation> animations = new ArrayList<>();

    ReferenceCpuScene(String backend, Object model, boolean nativeSimd,
                      ReferenceModelAnimation animation, SceneLayout layout) throws Exception {
        this.model = model;
        this.nativeSimd = nativeSimd;
        restPose = (float[]) model.getClass().getMethod("getBoneTransformData").invoke(model);
        for (SceneLayout.Placement placement : layout.instances()) {
            var output = new ReferenceVertexOutput(backend);
            output.placement(placement);
            output.camera(new Matrix4f());
            outputs.add(output);
            animations.add(animation == null ? null : animation.independent(placement.animationOffsetSeconds()));
        }
    }

    @Override
    public void camera(Matrix4f camera) {
        for (ReferenceVertexOutput output : outputs) {
            output.camera(camera);
        }
    }

    @Override
    public List<float[]> colors() {
        return outputs.stream().map(ReferenceVertexOutput::vertexColors).toList();
    }

    @Override
    public List<int[]> lightingCoordinates() {
        return outputs.stream().map(ReferenceVertexOutput::vertexLightingCoordinates).toList();
    }

    @Override
    public List<double[]> sample(int frame) throws Exception {
        var batches = new ArrayList<double[]>();
        for (int index = 0; index < outputs.size(); index++) {
            ReferenceModelAnimation animation = animations.get(index);
            float[] pose = animation == null ? restPose : animation.sample(frame);
            batches.add(outputs.get(index).vertexData(model, nativeSimd, pose));
        }
        return batches;
    }
}
