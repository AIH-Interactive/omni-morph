package top.aihmc.omnimorph.model.ysm;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import top.aihmc.omnimorph.avatar.ysm.YsmManifestReader;
import top.aihmc.omnimorph.model.ysm.animation.YsmAnimationClip;
import top.aihmc.omnimorph.model.ysm.animation.YsmAnimationPlayer;
import top.aihmc.omnimorph.molang.MolangEngine;
import top.aihmc.omnimorph.molang.runtime.ExpressionEvaluator;

import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Real package loading and CPU pose checks; does not claim GPU or game input validation. */
public final class YsmResourceScopeSmokeTest {
    public static void main(String[] args) throws Exception {
        Path model = Path.of(args[0]);
        List<String> failures = new ArrayList<>();
        var index = YsmManifestReader.read(model).resourceIndex();
        check(index.animationPaths().equals(List.of(
                "animations/main.animation.json", "animations/extra.animation.json",
                "animations/tac.animation.json", "animations/carryon.animation.json",
                "animations/tlm.animation.json", "animations/fp.arm.animation.json")),
                "player animations must follow only manifest declarations", failures);
        check(index.animationControllerPaths().equals(List.of("controller/wine_fox.animation_controllers.json")),
                "undeclared parallel_0 controller must not enter player runtime", failures);
        check(index.projectilePaths().contains("animations/arrow.animation.json"),
                "arrow animation must remain available in projectile resources", failures);

        YsmMaterialNativeSmokeTest.replayAcceleration(model, runtime -> {
            try {
                var player = runtime.animations();
                check(player.getClips().get("parallel0").boneAnimations.containsKey("gun"),
                        "body parallel0 must retain gun visibility channel", failures);
                check(player.usesBuiltinController("player.parallel0"),
                        "unlisted controller must not suppress body parallel0", failures);
                var evaluator = ExpressionEvaluator.evaluator(runtime.owner().getMolangContext());
                Method sample = YsmAnimationPlayer.class.getDeclaredMethod("sampleAnimations",
                        ExpressionEvaluator.class, boolean.class);
                sample.setAccessible(true);
                for (int frame = 0; frame < 3; frame++) {
                    sample.invoke(player, evaluator, false);
                    check(!runtime.getPart("gun").visibleRaw(), "gun hidden at default controls frame " + frame, failures);
                    check(!runtime.getPart("dingwei").visibleRaw(), "locator strips hidden frame " + frame, failures);
                }
                var engine = MolangEngine.fromCustomBinding(runtime.owner().getAvatarBindings());
                evaluator.evalAsFloat(engine.parse("v.roaming.gun = 1;").getFirst());
                sample.invoke(player, evaluator, false);
                check(runtime.getPart("gun").visibleRaw(), "author control can show gun", failures);
                check(!runtime.getPart("dingwei").visibleRaw(), "gun control must not show locator strips", failures);
                evaluator.evalAsFloat(engine.parse("v.roaming.gun = 0;").getFirst());
                sample.invoke(player, evaluator, false);
                check(!runtime.getPart("gun").visibleRaw(), "author control can hide gun again", failures);
                verifyDeclaredOrder(runtime, failures);
                verifyWalking(runtime, evaluator, sample, failures);
                YsmHandAnimationSmokeTest.verifyTacticsSustainedUse(runtime, failures);
            } catch (Exception exception) {
                throw new AssertionError(exception);
            }
        });
        if (!failures.isEmpty()) {
            throw new AssertionError(String.join("\n", failures));
        }
        System.out.println("YSM resource scope, declared precedence and authored visibility PASS");
    }

    @SuppressWarnings("unchecked")
    private static void verifyDeclaredOrder(YsmModelRuntime runtime, List<String> failures)
            throws ReflectiveOperationException {
        ListTag resources = new ListTag();
        addAnimation(resources, """
                {"animations":{"parallel0":{"bones":{"first":{"scale":0},"second":{"scale":0}}},
                "idle":{"bones":{"first":{"rotation":[10,0,0]}}}}}
                """);
        addAnimation(resources, """
                {"animations":{"parallel0":{"bones":{"replacement":{"scale":0}}},"idle":{}}}
                """);
        CompoundTag tag = new CompoundTag();
        tag.put("animations", resources);
        Method read = YsmModelRuntime.class.getDeclaredMethod("readAnimations", CompoundTag.class, MolangEngine.class);
        read.setAccessible(true);
        Map<String, YsmAnimationClip> clips = (Map<String, YsmAnimationClip>) read.invoke(null, tag,
                MolangEngine.fromCustomBinding(runtime.owner().getAvatarBindings()));
        check(clips.get("parallel0").boneAnimations.keySet().equals(java.util.Set.of("replacement")),
                "later declared animation must win regardless of bone count", failures);
        check(clips.get("idle").boneAnimations.isEmpty(),
                "later explicitly empty author animation must remain authoritative", failures);
    }

    private static void verifyWalking(YsmModelRuntime runtime, ExpressionEvaluator<?> evaluator,
                                      Method sample, List<String> failures) throws ReflectiveOperationException {
        var player = runtime.animations();
        var walking = player.playControllerAnimation("player.post_main", "walk#", null, 1f, 0f);
        check(walking.clip.name.equals("walk#"), "exact walk# must not resolve to empty walk", failures);
        walking.time = 0.125f;
        sample.invoke(player, evaluator, false);
        double first = runtime.getPart("LeftLeg").animRotRaw().x;
        walking.time = 0.375f;
        sample.invoke(player, evaluator, false);
        check(Math.abs(runtime.getPart("LeftLeg").animRotRaw().x - first) > 5f,
                "authored walk# must move the rendered leg across samples", failures);
        player.fadeOutControllerAnimation("player.post_main", "walk#", 0f);
    }

    private static void addAnimation(ListTag resources, String json) {
        CompoundTag entry = new CompoundTag();
        entry.putByteArray("data", json.getBytes(StandardCharsets.UTF_8));
        resources.add(entry);
    }

    private static void check(boolean condition, String message, List<String> failures) {
        if (!condition) {
            failures.add(message);
        }
    }
}
