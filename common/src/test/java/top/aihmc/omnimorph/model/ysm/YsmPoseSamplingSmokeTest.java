package top.aihmc.omnimorph.model.ysm;

import top.aihmc.omnimorph.model.ysm.animation.YsmAnimationParser;
import top.aihmc.omnimorph.model.ysm.animation.YsmAnimationSampler;
import top.aihmc.omnimorph.model.ysm.animation.YsmBonePose;
import top.aihmc.omnimorph.model.ysm.controller.YsmBlendTransition;
import top.aihmc.omnimorph.molang.MolangEngine;
import top.aihmc.omnimorph.molang.runtime.ExpressionEvaluator;
import top.aihmc.omnimorph.molang.runtime.Variable;
import top.aihmc.omnimorph.molang.runtime.binding.ObjectBinding;

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
        equal(180f, YsmBonePose.blend(outgoing, incoming, 0.5f, true).rotation()[0], "shortest rotation crosses 180");
        equal(0f, YsmBonePose.blend(outgoing, incoming, 0.5f, false).rotation()[0], "ordinary rotation uses full path");
        var reverse = YsmBonePose.blend(incoming, outgoing, 0.5f, true);
        equal(-180f, reverse.rotation()[0], "reverse shortest rotation");
        var curve = YsmBlendTransition.curve(Map.of(0f, 1f, 0.01f, 0.896f, 0.02f, 0.648f, 0.05f, 0f));
        var blended = YsmBonePose.blend(outgoing, incoming, curve.progress(0.015f), true);
        equal(2.28f, blended.position()[0], "reference curve position");
        equal(174.56f, blended.rotation()[0], "reference curve shortest rotation");
        equal(1.456f, blended.scale()[0], "reference curve scale");
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
        System.out.println("M3 live sampling, owned pose snapshots and reference-shaped shortest rotation blending passed.");
    }

    private static void equal(float expected, float actual, String message) {
        if (!Float.isFinite(actual) || Math.abs(expected - actual) > 1e-5f) {
            throw new AssertionError(message + ": " + actual);
        }
    }
}
