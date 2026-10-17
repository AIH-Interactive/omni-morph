package top.aihmc.omnimorph.model.ysm;

import com.mojang.blaze3d.vertex.PoseStack;
import top.aihmc.omnimorph.model.ysm.animation.YsmAnimationParser;
import top.aihmc.omnimorph.model.ysm.animation.YsmAnimationPlayer;
import top.aihmc.omnimorph.model.ysm.animation.YsmAnimationSampler;
import top.aihmc.omnimorph.model.ysm.animation.YsmBonePose;
import top.aihmc.omnimorph.model.ysm.controller.YsmBlendTransition;
import top.aihmc.omnimorph.model.ysm.controller.YsmControllerPoseLayer;
import top.aihmc.omnimorph.molang.MolangEngine;
import org.joml.Matrix4f;

import java.lang.reflect.Method;
import java.util.Map;

/** Differential checks of actual rendered bone matrices against independent numeric coefficients. */
public final class YsmBoneMatrixSmokeTest {
    private static final float TOLERANCE = 0.00002f;

    private YsmBoneMatrixSmokeTest() {
    }

    public static void main(String[] args) throws ReflectiveOperationException {
        var clips = YsmAnimationParser.parse("""
                {"animations":{
                  "weighted":{"animation_length":2,"bones":{"root":{
                    "position":[32,0,0],"rotation":[0,0,90],"scale":[3,3,3]}}},
                  "outgoing":{"animation_length":2,"bones":{"root":{
                    "position":[32,0,0],"rotation":[0,0,170],"scale":[3,3,3]}}},
                  "incoming":{"animation_length":2,"bones":{"root":{
                    "position":[64,0,0],"rotation":[0,0,-170],"scale":[1,1,1]}}}
                }}
                """, MolangEngine.createEmpty());
        var sample = YsmAnimationSampler.sampleBone(clips.get("weighted").boneAnimations.get("root"), 0.5f, null);
        for (float weight : new float[]{0f, 0.2f, 1f}) {
            YsmBonePose weighted = YsmBonePose.accumulate(null, sample, weight);
            Matrix4f actual = matrix(weighted, new float[]{0, 0, 0});
            assertZMatrix(actual, 90f * weight, 1f + 2f * weight, -2f * weight, 0f,
                    "rendered matrix at weight " + weight);
        }
        var outgoing = YsmAnimationSampler.sampleBone(clips.get("outgoing").boneAnimations.get("root"), 0.5f, null);
        var incoming = YsmAnimationSampler.sampleBone(clips.get("incoming").boneAnimations.get("root"), 0.5f, null);
        for (boolean shortest : new boolean[]{false, true}) {
            YsmControllerPoseLayer layer = new YsmControllerPoseLayer();
            layer.compose(Map.of("root", outgoing));
            layer.beginTransition(YsmBlendTransition.linear(1f), shortest);
            layer.advance(0.5f);
            Matrix4f actual = matrix(layer.compose(Map.of("root", incoming)).get("root"), new float[]{0, 0, 0});
            assertZMatrix(actual, shortest ? 180f : 0f, 2f, -3f, 0f,
                    "controller matrix across +/-180 degrees, shortest=" + shortest);
            layer.advance(0.5f);
            assertZMatrix(matrix(layer.compose(Map.of("root", incoming)).get("root"), new float[]{0, 0, 0}),
                    -170f, 1f, -4f, 0f, "transition endpoint matrix");
        }
        // Pivot (1,1,0), translated -2 on X, rotated 90 degrees and scaled by 3:
        // pivot + translation - transformed pivot = (2,-2,0).
        assertZMatrix(matrix(sample, new float[]{16, 16, 0}), 90f, 3f, 2f, -2f,
                "authored pivot affects the actual rendered matrix");
        System.out.println("M3 actual bone matrix differential passed for 0/.2/1 weights, pivot and controller +/-180 transitions.");
    }

    private static Matrix4f matrix(YsmBonePose pose, float[] pivot) throws ReflectiveOperationException {
        YsmModelPart part = new YsmModelPart("root", null, pivot, new float[]{0, 0, 0});
        Method applyPose = YsmAnimationPlayer.class.getDeclaredMethod("applyControllerPose", YsmModelPart.class, YsmBonePose.class);
        applyPose.setAccessible(true);
        applyPose.invoke(null, part, pose);
        YsmGeometry.Bone bone = new YsmGeometry.Bone("root", null, pivot, new float[]{0, 0, 0});
        Method transform = YsmRenderer.class.getDeclaredMethod("applyBoneTransform",
                YsmGeometry.Bone.class, YsmModelPart.class, PoseStack.class);
        transform.setAccessible(true);
        PoseStack stack = new PoseStack();
        transform.invoke(new YsmRenderer(null), bone, part, stack);
        return new Matrix4f(stack.last().pose());
    }

    private static void assertZMatrix(Matrix4f actual, float degrees, float scale, float x, float y, String label) {
        double angle = Math.toRadians(degrees);
        float cosine = (float) Math.cos(angle) * scale;
        float sine = (float) Math.sin(angle) * scale;
        float[] expected = {
                cosine, sine, 0, 0,
                -sine, cosine, 0, 0,
                0, 0, scale, 0,
                x, y, 0, 1
        };
        float[] values = actual.get(new float[16]);
        for (int index = 0; index < expected.length; index++) {
            if (!Float.isFinite(values[index]) || Math.abs(values[index] - expected[index]) > TOLERANCE) {
                throw new AssertionError(label + " coefficient " + index + ": expected=" + expected[index]
                        + " actual=" + values[index]);
            }
        }
    }
}
