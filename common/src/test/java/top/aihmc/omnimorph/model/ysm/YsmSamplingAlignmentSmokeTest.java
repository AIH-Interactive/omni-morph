package top.aihmc.omnimorph.model.ysm;

import top.aihmc.omnimorph.model.ysm.animation.YsmAnimationClip;
import top.aihmc.omnimorph.model.ysm.animation.YsmAnimationParser;
import top.aihmc.omnimorph.model.ysm.animation.YsmAnimationPlayer;
import top.aihmc.omnimorph.model.ysm.animation.YsmAnimationSampler;
import top.aihmc.omnimorph.molang.MolangEngine;
import top.aihmc.omnimorph.molang.runtime.ExpressionEvaluator;
import top.aihmc.omnimorph.molang.runtime.Function;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * SAMP-01/03/04/05/06: expected values come from Sparkle's JsonAnimationUtils.calculateLength,
 * RawBoneKeyFrame (LINEAR default), BoneKeyFrameProcessor.process, AnimationControllerInstance
 * (wrap only when runningTick > length) and AnimationProcessor (rotation, position, scale order).
 */
public final class YsmSamplingAlignmentSmokeTest {
    private static final MolangEngine PLAIN = MolangEngine.fromCustomBinding(name -> null);

    private YsmSamplingAlignmentSmokeTest() {
    }

    public static void main(String[] args) throws ReflectiveOperationException {
        testDerivedLength();
        testObjectKeyframeDefaultsToLinear();
        testCatmullRomTargetWinsOverStep();
        testLoopKeepsExactEnd();
        testChannelEvaluationOrder();
        System.out.println("PASS sampling alignment: derived length, linear default, catmullrom priority, loop end, channel order");
    }

    private static void testDerivedLength() {
        Map<String, YsmAnimationClip> clips = parse("""
                {"keyed":{"bones":{
                   "a":{"rotation":{"0":[0,0,0],"1.5":[10,0,0]}},
                   "b":{"position":{"0.25":[0,0,0],"0.75":[1,0,0]},"scale":{"2.0":[1,1,1]}}}},
                 "static":{"bones":{"a":{"rotation":[5,0,0]}}},
                 "declared":{"animation_length":0.5,"bones":{"a":{"rotation":{"0":[0,0,0],"3":[1,0,0]}}}}}
                """);
        require(clips.get("keyed").length == 2.0f, "missing length uses the latest keyframe: " + clips.get("keyed").length);
        require(clips.get("static").length == Float.MAX_VALUE,
                "a clip without keyframes is unbounded: " + clips.get("static").length);
        require(clips.get("declared").length == 0.5f, "a declared length is kept");
    }

    private static void testObjectKeyframeDefaultsToLinear() {
        YsmAnimationClip clip = parse("""
                {"clip":{"animation_length":1,"bones":{"a":{"rotation":{
                   "0":{"post":[0,0,0]},"1":{"post":[10,0,0]}}}}}}
                """).get("clip");
        float[] sampled = YsmAnimationSampler.sampleChannel(clip.boneAnimations.get("a").rotation, 0.5f, null, new float[3]);
        require(Math.abs(sampled[0] - 5f) < 0.0001f, "object keyframe without lerp_mode is linear: " + sampled[0]);
    }

    private static void testCatmullRomTargetWinsOverStep() {
        YsmAnimationClip clip = parse("""
                {"clip":{"animation_length":1,"bones":{"a":{"rotation":{
                   "0":{"post":[0,0,0],"lerp_mode":"step"},
                   "1":{"post":[10,0,0],"lerp_mode":"catmullrom"}}}}}}
                """).get("clip");
        float[] sampled = YsmAnimationSampler.sampleChannel(clip.boneAnimations.get("a").rotation, 0.5f, null, new float[3]);
        // Catmull-Rom with p0 = p1 = 0 and p2 = p3 = 10 at t = 0.5.
        require(Math.abs(sampled[0] - 5f) < 0.0001f, "a catmullrom target overrides a step source: " + sampled[0]);
    }

    private static void testLoopKeepsExactEnd() throws ReflectiveOperationException {
        Method advance = YsmAnimationPlayer.class.getDeclaredMethod("advanceAnimationTime",
                YsmAnimationPlayer.PlayingAnimation.class);
        advance.setAccessible(true);
        var player = new YsmAnimationPlayer(null);
        var playing = new YsmAnimationPlayer.PlayingAnimation(
                new YsmAnimationClip("loop", 1f, YsmAnimationClip.LoopMode.LOOP), false);
        playing.loopMode = YsmAnimationClip.LoopMode.LOOP;
        playing.time = 1f;
        advance.invoke(player, playing);
        require(playing.time == 1f, "a loop landing exactly on its end samples the end frame: " + playing.time);
        playing.time = 1.25f;
        advance.invoke(player, playing);
        require(Math.abs(playing.time - 0.25f) < 0.0001f, "a loop past its end wraps: " + playing.time);
    }

    private static void testChannelEvaluationOrder() {
        List<String> order = new ArrayList<>();
        Function mark = (context, arguments) -> {
            order.add(arguments.getAsString(context, 0));
            return 0f;
        };
        MolangEngine engine = MolangEngine.fromCustomBinding(name -> name.equals("mark") ? mark : null);
        YsmAnimationClip clip = YsmAnimationParser.parse("""
                {"animations":{"clip":{"animation_length":1,"bones":{"a":{
                   "position":["mark('position')",0,0],
                   "rotation":["mark('rotation')",0,0],
                   "scale":["mark('scale')",1,1]}}}}}
                """, engine).get("clip");
        YsmAnimationSampler.sampleBone(clip.boneAnimations.get("a"), 0f, ExpressionEvaluator.evaluator(order));
        require(order.equals(List.of("rotation", "position", "scale")),
                "channels are evaluated rotation, position, scale: " + order);
    }

    private static Map<String, YsmAnimationClip> parse(String animations) {
        return YsmAnimationParser.parse("{\"animations\":" + animations + "}", PLAIN);
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
