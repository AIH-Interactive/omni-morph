package top.aihmc.omnimorph.model.ysm;

import top.aihmc.omnimorph.model.ysm.animation.YsmAnimationParser;
import top.aihmc.omnimorph.model.ysm.animation.YsmAnimationSampler;
import top.aihmc.omnimorph.model.ysm.animation.YsmBonePose;
import top.aihmc.omnimorph.model.ysm.controller.YsmBlendTransition;
import top.aihmc.omnimorph.molang.MolangEngine;
import top.aihmc.omnimorph.molang.runtime.ExpressionEvaluator;
import top.aihmc.omnimorph.molang.runtime.Variable;
import top.aihmc.omnimorph.molang.runtime.binding.ObjectBinding;

import org.joml.Quaternionf;

import java.util.Map;

public final class YsmPoseSamplingSmokeTest {
    private YsmPoseSamplingSmokeTest() {
    }

    public static void main(String[] args) {
        var clip = YsmAnimationParser.parse("""
                {"animations":{"fixture":{"animation_length":1,"bones":{"root":{
                  "position":{"0":[0,0,0],"1":{"pre":[10,0,0],"post":[20,0,0]}},
                  "rotation":[170,0,0],"scale":[1,1,1]
                }}}}}
                """, MolangEngine.createEmpty()).get("fixture");
        var bone = clip.boneAnimations.get("root");
        equal(5f, YsmAnimationSampler.sampleBone(bone, 0.5f, null).position()[0], "between post and next pre");
        equal(20f, YsmAnimationSampler.sampleBone(bone, 1f, null).position()[0], "at discontinuous post keyframe");
        var outgoing = YsmAnimationSampler.sampleBone(bone, 0f, null);
        var incoming = new YsmBonePose(new float[]{10, 0, 0}, new float[]{-170, 0, 0}, new float[]{3, 3, 3});
        // D010: Sparkle nlerps rendered quaternions, so a single-axis blend always takes the short arc.
        equalAngle(180f, YsmBonePose.blend(outgoing, incoming, 0.5f, null).rotation()[0], "nlerp crosses 180");
        equalAngle(180f, YsmBonePose.blend(incoming, outgoing, 0.5f, null).rotation()[0], "reverse nlerp crosses 180");
        var curve = YsmBlendTransition.curve(Map.of(0f, 1f, 0.01f, 0.896f, 0.02f, 0.648f, 0.05f, 0f));
        var blended = YsmBonePose.blend(outgoing, incoming, curve.progress(0.015f), null);
        equal(2.28f, blended.position()[0], "reference curve position");
        equalAngle(170f + singleAxisNlerpDegrees(20f, 0.228f), blended.rotation()[0], "reference curve nlerp rotation");
        equal(1.456f, blended.scale()[0], "reference curve scale");
        // Two axes: hand-computed nlerp of Rx(-90) and Ry(-90) at t=0.5, extracted as ZYX Euler.
        var twoAxis = YsmBonePose.blend(new YsmBonePose(null, new float[]{90, 0, 0}, null),
                new YsmBonePose(null, new float[]{0, 90, 0}, null), 0.5f, null).rotation();
        equalAngle(63.43495f, twoAxis[0], "two-axis nlerp x");
        equalAngle(41.81031f, twoAxis[1], "two-axis nlerp y");
        equalAngle(26.56505f, twoAxis[2], "two-axis nlerp z");
        // A middle-axis rest rotation does not factor out of a two-axis blend: the result must be the
        // nlerp of the rendered rotations (rest included), not the rest-free blend.
        float[] rest = {0, 45, 0};
        float[] from = {0, 0, 0};
        float[] to = {90, 0, 90};
        var withRest = YsmBonePose.blend(new YsmBonePose(null, from, null),
                new YsmBonePose(null, to, null), 0.5f, rest).rotation();
        var expected = rendered(from, rest).nlerp(rendered(to, rest), 0.5f, new Quaternionf());
        require(Math.abs(Math.abs(rendered(withRest, rest).dot(expected)) - 1f) < 1e-5f,
                "rest-offset blend equals nlerp of rendered rotations");
        var withoutRest = YsmBonePose.blend(new YsmBonePose(null, from, null),
                new YsmBonePose(null, to, null), 0.5f, null).rotation();
        require(Math.abs(withRest[0] - withoutRest[0]) + Math.abs(withRest[1] - withoutRest[1])
                + Math.abs(withRest[2] - withoutRest[2]) > 1f, "the rest rotation changes the blended pose");
        float[] borrowed = {1, 2, 3};
        var owned = new YsmBonePose(borrowed, null, null);
        borrowed[0] = 99;
        owned.position()[0] = 88;
        equal(1f, owned.position()[0], "snapshot owns input and output vectors");
        Variable value = context -> ((float[]) context.entity())[0];
        ObjectBinding query = name -> name.equals("value") ? value : null;
        var engine = MolangEngine.fromCustomBinding(name -> name.equals("q") ? query : null);
        var dynamic = YsmAnimationParser.parse("""
                {"animations":{"fixture":{"bones":{"root":{"position":["q.value",0,0]}}}}}
                """, engine).get("fixture").boneAnimations.get("root");
        float[] context = {0.2f};
        var evaluator = ExpressionEvaluator.evaluator(context);
        var snapshot = YsmAnimationSampler.sampleBone(dynamic, 0f, evaluator);
        context[0] = 4f;
        equal(0.2f, snapshot.position()[0], "snapshot does not reevaluate a changed Molang variable");
        equal(4f, YsmAnimationSampler.sampleBone(dynamic, 0f, evaluator).position()[0], "live pose still reevaluates");
        System.out.println("M3 live sampling, owned pose snapshots and reference quaternion nlerp rotation blending passed.");
    }

    /**
     * Rotation reached by nlerp between two rotations about one axis that differ by {@code arc} degrees:
     * twice atan2(t sin(phi), (1 - t) + t cos(phi)) with phi = arc / 2 in quaternion space.
     */
    static float singleAxisNlerpDegrees(float arc, float t) {
        double phi = Math.toRadians(arc / 2d);
        return (float) Math.toDegrees(2d * Math.atan2(t * Math.sin(phi), (1d - t) + t * Math.cos(phi)));
    }

    /** OmniMorph's renderer applies Z, Y, X of (rest.x - anim.x, rest.y - anim.y, rest.z + anim.z). */
    private static Quaternionf rendered(float[] animation, float[] rest) {
        return new Quaternionf().rotateZYX((float) Math.toRadians(rest[2] + animation[2]),
                (float) Math.toRadians(rest[1] - animation[1]), (float) Math.toRadians(rest[0] - animation[0]));
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    static void equalAngle(float expected, float actual, String message) {
        double difference = ((actual - expected) % 360d + 540d) % 360d - 180d;
        if (!Float.isFinite(actual) || Math.abs(difference) > 1e-3d) {
            throw new AssertionError(message + ": expected " + expected + " but was " + actual);
        }
    }

    private static void equal(float expected, float actual, String message) {
        if (!Float.isFinite(actual) || Math.abs(expected - actual) > 1e-5f) {
            throw new AssertionError(message + ": " + actual);
        }
    }
}
