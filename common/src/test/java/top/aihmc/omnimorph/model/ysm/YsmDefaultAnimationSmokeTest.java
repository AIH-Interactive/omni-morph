package top.aihmc.omnimorph.model.ysm;

import com.google.gson.JsonParser;
import net.minecraft.nbt.CompoundTag;
import org.figuramc.figura.avatar.Avatar;
import top.aihmc.omnimorph.molang.MolangEngine;
import top.aihmc.omnimorph.model.ysm.animation.YsmDefaultAnimationResolver;
import top.aihmc.omnimorph.model.ysm.animation.YsmDefaultAnimationResolver.Domain;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Real bundled templates and native parser; no rendered-pixel assertions. */
public final class YsmDefaultAnimationSmokeTest {
    public static void run(Path workspace, CompoundTag prepared) throws Exception {
        Avatar owner = Avatar.createLibraryPreview(UUID.randomUUID(), "default-animation-test");
        try {
            owner.load(prepared.copy());
            owner.initializePreviewPlayer(null);
            require(owner.getYsmRuntime().defaultAnimationTemplateVersion().isEmpty(),
                    "an accessory wrapper does not automatically receive a humanoid movement machine");
            MolangEngine engine = MolangEngine.fromCustomBinding(owner.getAvatarBindings());
            var templateRoot = workspace.resolve("common/src/main/resources/assets/omni-morph/ysm_default/animations");
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
            bones.remove("molang");
            var manifest = JsonParser.parseString(Files.readString(workspace.resolve("protocol/outfit-v1/template-manifest.json")))
                    .getAsJsonObject();
            var whitelist = new ArrayList<String>();
            manifest.getAsJsonArray("base_action_whitelist").forEach(value -> whitelist.add(value.getAsString()));
            require(whitelist.equals(YsmDefaultAnimationResolver.MAIN_ACTIONS), "runtime whitelist matches the locked phase-one manifest");
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
            require(first.size() == 26 && first.get("walk") != second.get("walk"),
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
            require(handClips.get("swing:sword").events.size() >= 3
                            && hands.definitions().get("swing:sword").json().contains("variable.omnimorph_default.qh")
                            && hands.definitions().get("swing$minecraft:mace").json().contains("variable.omnimorph_default.mace"),
                    "arm timeline initialization and its private variable dependency closure survive selection");
            var alias = YsmDefaultAnimationResolver.resolve("revision-2", Domain.MAIN,
                    List.of("root", "author_upper_arm", "tentacle"), Map.of("LeftArm", "author_upper_arm"),
                    Set.of(), Set.of(), true);
            var aliasClips = alias.compile(engine);
            require(aliasClips.get("walk").boneAnimations.containsKey("author_upper_arm")
                            && aliasClips.get("walk").boneAnimations.containsKey("root")
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
            System.out.println("PASS default animation resolution: bundled hashes/whitelist, author/disabled priority, native parsing, domains, timeline dependencies, aliases and cache identity");
        } finally {
            owner.clean();
            YsmDefaultAnimationResolver.clear();
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

    private static Avatar loadFullAvatar(String clips) {
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
        Avatar avatar = Avatar.createLibraryPreview(UUID.randomUUID(), "full default fixture");
        avatar.load(source);
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
