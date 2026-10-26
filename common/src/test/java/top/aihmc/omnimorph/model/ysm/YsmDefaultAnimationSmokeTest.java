package top.aihmc.omnimorph.model.ysm;

import com.google.gson.JsonParser;
import net.minecraft.nbt.CompoundTag;
import org.figuramc.figura.avatar.Avatar;
import top.aihmc.omnimorph.molang.MolangEngine;
import top.aihmc.omnimorph.model.ysm.animation.YsmAnimationClip;
import top.aihmc.omnimorph.model.ysm.animation.YsmAnimationParser;
import top.aihmc.omnimorph.model.ysm.animation.YsmAnimationSampler;
import top.aihmc.omnimorph.model.ysm.animation.YsmDefaultAnimationResolver;
import top.aihmc.omnimorph.model.ysm.animation.YsmDefaultAnimationResolver.Domain;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Real bundled templates and native parser; no rendered-pixel assertions. */
public final class YsmDefaultAnimationSmokeTest {
    public static void run(Path workspace) throws Exception {
        CompoundTag accessory = fullAvatarSource("{}");
        accessory.getCompoundOrEmpty("metadata").putString("resource_kind", "ACCESSORY");
        run(workspace, accessory);
    }

    public static void run(Path workspace, CompoundTag prepared) throws Exception {
        testReferenceResources(workspace);
        Avatar owner = Avatar.createLibraryPreview(UUID.randomUUID(), "default-animation-test");
        try {
            owner.load(prepared.copy());
            owner.initializePreviewPlayer(null);
            require(owner.getYsmRuntime().defaultAnimationTemplateVersion().isEmpty(),
                    "an accessory wrapper does not automatically receive a humanoid movement machine");
            MolangEngine engine = MolangEngine.fromCustomBinding(owner.getAvatarBindings());
            var templateRoot = workspace.resolve("common/src/main/resources/assets/omni-morph/default_animation");
            var main = JsonParser.parseString(Files.readString(templateRoot.resolve("main.animation.json")))
                    .getAsJsonObject().getAsJsonObject("animations");
            var arm = JsonParser.parseString(Files.readString(templateRoot.resolve("arm.animation.json")))
                    .getAsJsonObject().getAsJsonObject("animations");
            var bones = new HashSet<String>();
            for (var source : List.of(main, arm)) {
                for (var clip : source.asMap().values()) {
                    var channels = clip.getAsJsonObject().getAsJsonObject("bones");
                    if (channels != null) {
                        bones.addAll(channels.keySet());
                    }
                }
            }
            bones.removeIf(name -> name.equalsIgnoreCase("molang"));
            require(main.keySet().containsAll(YsmDefaultAnimationResolver.MAIN_ACTIONS),
                    "every runtime main fallback action exists in the reference resources");
            YsmDefaultAnimationResolver.clear();
            int reads = YsmDefaultAnimationResolver.sourceReads();
            int mappings = YsmDefaultAnimationResolver.mappings();
            var selected = YsmDefaultAnimationResolver.resolve("revision-1", Domain.MAIN, bones,
                    Map.of(), Set.of("idle"), Set.of("run"), true);
            require(!selected.definitions().containsKey("idle") && !selected.definitions().containsKey("run")
                            && selected.definitions().containsKey("walk"),
                    "authored empty idle and explicit disabled run are not replaced; missing walk is resolved");
            require(selected.definitions().keySet().stream().noneMatch(name -> name.contains("parallel")
                            || name.contains("blink") || name.contains("physics")),
                    "only the basic main whitelist is selected");
            var first = selected.compile(engine);
            var second = selected.compile(engine);
            require(first.size() == 25 && first.get("walk") != second.get("walk"),
                    "every selected template compiles through the native parser with private clip objects");
            first.get("walk").boneAnimations.clear();
            require(!second.get("walk").boneAnimations.isEmpty(), "runtime edits cannot mutate another native clip");
            var reversed = new ArrayList<>(bones);
            java.util.Collections.reverse(reversed);
            var repeated = YsmDefaultAnimationResolver.resolve("revision-1", Domain.MAIN, reversed,
                    Map.of(), Set.of("idle"), Set.of("run"), true);
            require(selected.signature().equals(repeated.signature())
                            && YsmDefaultAnimationResolver.mappings() == mappings + 1
                            && YsmDefaultAnimationResolver.sourceReads() == reads + 1,
                    "mapping cache and effective signature are stable across input traversal order");
            var hands = YsmDefaultAnimationResolver.resolve("revision-1", Domain.ARM, bones,
                    Map.of(), Set.of("use_mainhand:bow"), Set.of(), true);
            require(!hands.definitions().containsKey("use_mainhand:bow")
                            && hands.definitions().containsKey("use_offhand:bow")
                            && hands.definitions().keySet().stream().noneMatch(name -> name.contains("parallel")),
                    "one authored hand action does not suppress the other side or import fp.arm parallel clips");
            var handClips = hands.compile(engine);
            testReferenceSamples(workspace, engine, second, handClips);
            // Independent golden values from Sparkle's use_mainhand:bow / RightArm keys.
            var bow = YsmDefaultAnimationResolver.resolve("bow-golden", Domain.ARM, bones,
                    Map.of(), Set.of(), Set.of(), true).compile(engine).get("use_mainhand:bow");
            requireVector(YsmAnimationSampler.sampleBone(bow.boneAnimations.get("RightArm"), 0f, null).rotation(),
                    new float[]{-51.470402f, 7.4448f, -28.9102f}, "reference bow initial rotation");
            requireVector(YsmAnimationSampler.sampleBone(bow.boneAnimations.get("RightArm"), .5f, null).rotation(),
                    new float[]{-112.6097f, 29.4633f, -11.3843f}, "reference bow half-second rotation");
            requireVector(YsmAnimationSampler.sampleBone(bow.boneAnimations.get("RightArm"), 1f, null).rotation(),
                    new float[]{-77.0228f, 61.5595f, -5.0828f}, "reference bow final rotation");
            require(handClips.get("swing:sword").events.size() >= 3
                            && hands.definitions().get("swing:sword").json().contains("variable.omnimorph_default.qh")
                            && hands.definitions().get("swing$minecraft:mace").json().contains("variable.omnimorph_default.mace"),
                    "arm timeline initialization and its private variable dependency closure survive selection");
            var alias = YsmDefaultAnimationResolver.resolve("revision-2", Domain.MAIN,
                    List.of("allbody", "author_upper_arm", "tentacle"), Map.of("LeftArm", "author_upper_arm"),
                    Set.of(), Set.of(), true);
            var aliasClips = alias.compile(engine);
            require(aliasClips.get("walk").boneAnimations.containsKey("author_upper_arm")
                            && aliasClips.get("walk").boneAnimations.containsKey("allbody")
                            && !aliasClips.get("walk").boneAnimations.containsKey("tentacle")
                            && alias.definitions().get("walk").missingBones().contains("RightArm"),
                    "exact/normalized names precede explicit aliases; missing bones are diagnosed without inventing channels");
            require(YsmDefaultAnimationResolver.resolve("disabled", Domain.MAIN, bones,
                    Map.of(), Set.of(), Set.of(), false).definitions().isEmpty(), "disabled native state does not force fallback on");
            YsmDefaultAnimationResolver.clear();
            require(selected.signature().equals(YsmDefaultAnimationResolver.resolve("revision-1", Domain.MAIN,
                            bones, Map.of(), Set.of("idle"), Set.of("run"), true).signature()),
                    "resource cache invalidation preserves deterministic template identity");
            testNativeRuntime();
            testBuiltinFallback();
            testBeginningTransitionPose();
            testAuthoredDomains();
            System.out.println("PASS default animation resolution: bundled hashes/whitelist, author/disabled priority, native parsing, domains, timeline dependencies, aliases and cache identity");
        } finally {
            owner.clean();
            YsmDefaultAnimationResolver.clear();
        }
    }

    private static void testReferenceResources(Path workspace) throws Exception {
        Path bundled = workspace.resolve("common/src/main/resources/assets/omni-morph/default_animation");
        Path reference = workspace.resolve("refs/Sparkle-Morpher/common/src/main/resources/assets/"
                + "sparkle_morpher/builtin/default/animations");
        try (var files = Files.list(reference)) {
            var animations = files.filter(path -> path.getFileName().toString().endsWith(".animation.json")).toList();
            require(animations.size() == 14, "reference default animation inventory contains fourteen files");
            for (Path file : animations) {
                require(Arrays.equals(Files.readAllBytes(file), Files.readAllBytes(bundled.resolve(file.getFileName()))),
                        "bundled default animation matches Sparkle byte for byte: " + file.getFileName());
            }
        }
    }

    private static void testReferenceSamples(Path workspace, MolangEngine engine, Map<String, YsmAnimationClip> main,
                                             Map<String, YsmAnimationClip> hands) throws Exception {
        Path reference = workspace.resolve("refs/Sparkle-Morpher/common/src/main/resources/assets/"
                + "sparkle_morpher/builtin/default/animations");
        for (var domain : Map.of("main", main, "arm", hands).entrySet()) {
            // This checks resource selection/mapping through our parser, not Sparkle's runtime semantics.
            var expected = YsmAnimationParser.parse(
                    Files.readString(reference.resolve(domain.getKey() + ".animation.json")), true, engine, true);
            for (var entry : domain.getValue().entrySet()) {
                YsmAnimationClip actual = entry.getValue();
                YsmAnimationClip source = expected.get(entry.getKey());
                require(source != null && actual.length == source.length && actual.loopMode == source.loopMode,
                        "selected default preserves reference length/loop: " + entry.getKey());
                for (var bone : actual.boneAnimations.entrySet()) {
                    require(source.boneAnimations.containsKey(bone.getKey()),
                            "reference contains mapped bone: " + entry.getKey() + "/" + bone.getKey());
                    for (float fraction : new float[]{0f, .25f, .5f, .75f, 1f}) {
                        float time = source.length * fraction;
                        var actualPose = YsmAnimationSampler.sampleBone(bone.getValue(), time, null);
                        var sourcePose = YsmAnimationSampler.sampleBone(source.boneAnimations.get(bone.getKey()), time, null);
                        String label = entry.getKey() + "/" + bone.getKey() + " at " + time;
                        requireVector(actualPose.position(), sourcePose.position(), label + " position");
                        requireVector(actualPose.rotation(), sourcePose.rotation(), label + " rotation");
                        requireVector(actualPose.scale(), sourcePose.scale(), label + " scale");
                    }
                }
            }
        }
    }

    private static void requireVector(float[] actual, float[] expected, String message) {
        if (actual == null || expected == null) {
            require(actual == expected, message + " channel presence");
            return;
        }
        for (int axis = 0; axis < 3; axis++) {
            require(Math.abs(actual[axis] - expected[axis]) < .0001f, message + " axis " + axis);
        }
    }

    private static void testNativeRuntime() throws Exception {
        Avatar avatar = loadFullAvatar("{\"gesture\":{\"animation_length\":1},\"idle\":{}}");
        try {
            var runtime = avatar.getYsmRuntime();
            var player = runtime.animations();
            require(runtime.defaultAnimationTemplateVersion().equals(YsmDefaultAnimationResolver.VERSION)
                            && runtime.defaultAnimationSignature().matches("[0-9a-f]{64}"),
                    "full avatar assembly records the effective template identity");
            require(player.getClips().values().stream().noneMatch(clip -> clip.name.startsWith("omnimorph.default/")),
                    "default templates never enter the authored v1 inventory or generated action wheel");
            var legacy = new top.aihmc.omnimorph.model.ysm.sync.YsmSyncModel(runtime);
            String schema = legacy.schemaHash();
            var state = new net.minecraft.client.renderer.entity.state.LivingEntityRenderState();
            state.ageInTicks = 10;
            top.aihmc.omnimorph.model.ysm.animation.YsmRenderFrameClock.beginFrame();
            runtime.updateAnimations(state, null);
            require(player.getActiveAnimations().get("idle").clip.name.equals("idle"),
                    "an explicitly empty author idle remains authoritative in the actual native state machine");
            state.walkAnimationSpeed = .8f;
            state.ageInTicks = 12;
            top.aihmc.omnimorph.model.ysm.animation.YsmRenderFrameClock.beginFrame();
            runtime.updateAnimations(state, null);
            var walk = player.getActiveAnimations().get("walk");
            require(walk != null && walk.clip.name.endsWith("/main/walk") && walk.targetWeight == 1,
                    "missing walk plays the selected bundled template through the native player");
            var legRotation = runtime.getPart("LeftLeg").animRotRaw();
            require(Math.abs(legRotation.x) + Math.abs(legRotation.y) + Math.abs(legRotation.z) > .0001,
                    "default walk affects the actual native bone pose");
            require(legacy.capture().playbacks().stream().noneMatch(playback -> playback.clip() < 0)
                            && new top.aihmc.omnimorph.model.ysm.sync.YsmSyncModel(runtime).schemaHash().equals(schema),
                    "v1 capture excludes local templates and keeps authored clip indexes/schema stable");
            player.setNativeAnimationEnabled("walk", false);
            state.ageInTicks = 14;
            top.aihmc.omnimorph.model.ysm.animation.YsmRenderFrameClock.beginFrame();
            runtime.updateAnimations(state, null);
            var disabledWalk = player.getActiveAnimations().get("walk");
            require(disabledWalk == null || disabledWalk.targetWeight == 0,
                    "explicit disabling fades out an active default movement action");
            require(avatar.controls.setValue(avatar, "ysm.native_state_machine", false),
                    "the native state-machine control accepts an explicit disable");
            state.ageInTicks = 16;
            top.aihmc.omnimorph.model.ysm.animation.YsmRenderFrameClock.beginFrame();
            runtime.updateAnimations(state, null);
            require(player.getActiveAnimations().values().stream().filter(animation -> animation.isNative)
                            .allMatch(animation -> animation.targetWeight == 0),
                    "default resolution never reopens a disabled native state machine");
        } finally {
            avatar.clean();
        }
        Avatar damaged = loadFullAvatar("{\"walk\":{\"animation_length\":\"invalid\"}}");
        try {
            require(damaged.getYsmRuntime().defaultAnimationDiagnostics()
                            .contains("DEFAULT_FALLBACK_SKIPPED_DAMAGED_AUTHOR_ANIMATION"),
                    "a damaged author action is diagnosed rather than silently classified as missing");
            var state = new net.minecraft.client.renderer.entity.state.LivingEntityRenderState();
            state.walkAnimationSpeed = .8f;
            state.ageInTicks = 18;
            top.aihmc.omnimorph.model.ysm.animation.YsmRenderFrameClock.beginFrame();
            damaged.getYsmRuntime().updateAnimations(state, null);
            require(!damaged.getYsmRuntime().animations().getActiveAnimations().containsKey("walk"),
                    "damaged walk declaration remains excluded from template substitution");
        } finally {
            damaged.clean();
        }
        System.out.println("PASS native YSM default playback: real assembly/pose, author-empty priority, disable controls, damage diagnostics and frozen v1 inventory");
    }

    private static void testAuthoredDomains() throws Exception {
        CompoundTag source = fullAvatarSource("""
                {"shared":{"loop":true,"bones":{"LeftArm":{"rotation":[10,0,0]}}}}
                """);
        CompoundTag tag = source.getCompoundOrEmpty("ysm");
        tag.putByteArray("arm_model", tag.getByteArray("main_model").orElseThrow());
        var arm = new CompoundTag();
        arm.putByteArray("data", """
                {"ysm_animation_type":11,"animations":{"shared":{"loop":true,
                  "bones":{"LeftArm":{"rotation":[90,0,0]},"RightArm":{"rotation":[90,0,0]}}}}}
                """.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        tag.getListOrEmpty("animations").add(arm);
        Avatar avatar = Avatar.createLibraryPreview(UUID.randomUUID(), "authored domain fixture");
        try {
            avatar.load(source);
            avatar.initializePreviewPlayer(null);
            var runtime = avatar.getYsmRuntime();
            var player = runtime.animations();
            player.play("shared", true, 1f);
            var sample = player.getClass().getDeclaredMethod("sampleAnimationPoses",
                    top.aihmc.omnimorph.molang.runtime.ExpressionEvaluator.class, boolean.class);
            sample.setAccessible(true);
            var evaluator = top.aihmc.omnimorph.molang.runtime.ExpressionEvaluator.evaluator(avatar.getMolangContext());
            sample.invoke(player, evaluator, false);
            require(Math.abs(runtime.getPart("LeftArm").animRotRaw().x - 10f) < .0001f,
                    "body geometry samples its own same-name authored clip");
            sample.invoke(player, evaluator, true);
            require(Math.abs(runtime.getAnimationParts("LeftArm", true).getFirst().animRotRaw().x - 90f) < .0001f,
                    "arm geometry samples independent authored clip despite body resource weight");
            require(Math.abs(runtime.getPart("LeftArm").animRotRaw().x - 10f) < .0001f,
                    "arm sampling preserves body geometry");
        } finally {
            avatar.clean();
        }
    }

    private static void testBuiltinFallback() {
        CompoundTag source = fullAvatarSource("{}");
        var controllers = new net.minecraft.nbt.ListTag();
        var controller = new CompoundTag();
        controller.putByteArray("data", """
                {"animation_controllers":{"player.main":{"initial_state":"ysm-builtin","states":{
                  "ysm-builtin":{"transitions":[{"custom":"v.custom"}]},
                  "custom":{"transitions":[{"ysm-builtin":"!v.custom"}]}
                }}}}
                """.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        controllers.add(controller);
        source.getCompoundOrEmpty("ysm").put("animation_controllers", controllers);
        Avatar avatar = Avatar.createLibraryPreview(UUID.randomUUID(), "builtin fallback fixture");
        try {
            avatar.load(source);
            avatar.initializePreviewPlayer(null);
            var runtime = avatar.getYsmRuntime();
            var state = new net.minecraft.client.renderer.entity.state.LivingEntityRenderState();
            state.walkAnimationSpeed = .8f;
            for (int frame = 0; frame < 3; frame++) {
                avatar.getMolangContext().variables.setPath("custom", frame == 1 ? 1f : 0f);
                state.ageInTicks = frame * 2;
                top.aihmc.omnimorph.model.ysm.animation.YsmRenderFrameClock.beginFrame();
                runtime.updateAnimations(state, null);
                require((runtime.animations().getActiveAnimation("walk") != null) == (frame != 1),
                        "same-frame builtin/custom switching controls missing-walk fallback: " + frame);
            }
        } finally {
            avatar.clean();
        }
    }

    private static void testBeginningTransitionPose() {
        CompoundTag source = fullAvatarSource("""
                {"old":{"loop":true,"bones":{"LeftLeg":{"rotation":[0,0,0]}}},
                 "incoming":{"loop":true,"animation_length":1,
                   "bones":{"LeftLeg":{"rotation":{"0":[20,0,0],"1":[100,0,0]}}},
                   "timeline":{"0":"v.zero=v.zero+1;","0.125":"v.mid=v.mid+1;"}}}
                """);
        var controllers = new net.minecraft.nbt.ListTag();
        var controller = new CompoundTag();
        controller.putByteArray("data", """
                {"animation_controllers":{"player.main":{"states":{
                  "default":{"animations":["old"],"transitions":[{"target":"v.custom"}]},
                  "target":{"animations":["incoming"],"blend_transition":0.5}
                }}}}
                """.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        controllers.add(controller);
        source.getCompoundOrEmpty("ysm").put("animation_controllers", controllers);
        Avatar avatar = Avatar.createLibraryPreview(UUID.randomUUID(), "beginning transition pose fixture");
        try {
            avatar.load(source);
            avatar.initializePreviewPlayer(null);
            var runtime = avatar.getYsmRuntime();
            var context = avatar.getMolangContext();
            context.variables.setPath("zero", 0f);
            context.variables.setPath("mid", 0f);
            var state = new net.minecraft.client.renderer.entity.state.LivingEntityRenderState();
            for (int frame = 0; frame <= 6; frame++) {
                context.variables.setPath("custom", frame == 0 ? 0f : 1f);
                state.ageInTicks = frame * 2.5f;
                top.aihmc.omnimorph.model.ysm.animation.YsmRenderFrameClock.beginFrame();
                runtime.updateAnimations(state, null);
                if (frame == 0) {
                    continue;
                }
                var playing = runtime.animations().getControllerAnimation("player.main", "incoming");
                float expectedTime = frame <= 5 ? 0f : .125f;
                require(playing.time == expectedTime, "real Avatar transition clock at frame " + frame);
                float expectedRotation = frame <= 5 ? (frame - 1) * 5f : 30f;
                require(Math.abs(runtime.getPart("LeftLeg").animRotRaw().x - expectedRotation) < .0001,
                        "real Avatar snapshot-to-first-key pose at frame " + frame);
                require(((Number) context.variables.getPath("zero")).floatValue() == (frame >= 5 ? 1f : 0f),
                        "real Avatar zero-time instruction waits for transition completion");
                require(((Number) context.variables.getPath("mid")).floatValue() == (frame == 6 ? 1f : 0f),
                        "real Avatar later instruction retains its relative time");
            }
        } finally {
            avatar.clean();
        }
    }

    private static CompoundTag fullAvatarSource(String clips) {
        var source = new CompoundTag();
        var metadata = new CompoundTag();
        metadata.putString("format", "ysm-native");
        metadata.putString("resource_kind", "AVATAR");
        source.put("metadata", metadata);
        var ysm = new CompoundTag();
        ysm.putByteArray("main_model", """
                {"minecraft:geometry":[{"description":{"texture_width":1,"texture_height":1},"bones":[
                {"name":"Root"},{"name":"UpBody","parent":"Root"},
                {"name":"LeftArm","parent":"UpBody"},{"name":"RightArm","parent":"UpBody"},
                {"name":"LeftLeg","parent":"Root"},{"name":"RightLeg","parent":"Root"}]}]}
                """.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        var animation = new CompoundTag();
        animation.putByteArray("data", ("{\"animations\":" + clips + "}").getBytes(java.nio.charset.StandardCharsets.UTF_8));
        var animations = new net.minecraft.nbt.ListTag();
        animations.add(animation);
        ysm.put("animations", animations);
        source.put("ysm", ysm);
        return source;
    }

    private static Avatar loadFullAvatar(String clips) {
        Avatar avatar = Avatar.createLibraryPreview(UUID.randomUUID(), "full default fixture");
        avatar.load(fullAvatarSource(clips));
        avatar.initializePreviewPlayer(null);
        require(avatar.loaded && avatar.getYsmRuntime() != null && !avatar.scriptError,
                "default fixture loads through the actual Avatar/native YSM path");
        return avatar;
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
