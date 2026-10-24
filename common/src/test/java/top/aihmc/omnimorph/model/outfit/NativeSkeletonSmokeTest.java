package top.aihmc.omnimorph.model.outfit;

import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import top.aihmc.omnimorph.avatar.Avatar;
import top.aihmc.omnimorph.model.FiguraModelPart;
import top.aihmc.omnimorph.model.PartCustomization;
import top.aihmc.omnimorph.model.rendering.ImmediateFiguraRenderer;
import top.aihmc.omnimorph.model.rendering.PartFilterScheme;
import top.aihmc.omnimorph.permissions.PermissionPack;
import top.aihmc.omnimorph.permissions.Permissions;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Native CPU transforms only. Actual client rendering and first-person combinations remain pending. */
public final class NativeSkeletonSmokeTest {
    public static void run(CompoundTag prepared) {
        testVanilla();
        testYsm(prepared);
        testYsmArms(prepared);
        System.out.println("PASS native skeleton poses: vanilla/YSM pivots, empty child, numeric axes, snapshot isolation and shared index");
    }

    /** Invoked by the existing native Figura draw harness after its camera/debug fixtures are installed. */
    public static void testFigura(Avatar avatar) {
        var renderer = (ImmediateFiguraRenderer) avatar.renderer;
        FiguraModelPart root = renderer.root;
        var empty = new FiguraModelPart(avatar, "empty_anchor", new PartCustomization(), Map.of(),
                new java.util.ArrayList<>());
        empty.facesByTexture = List.of();
        empty.textures = List.of();
        empty.parent = root;
        empty.customization.setPivot(7, 11, 13);
        var hidden = new FiguraModelPart(avatar, "hidden_anchor", new PartCustomization(), Map.of(),
                new java.util.ArrayList<>());
        hidden.facesByTexture = List.of();
        hidden.textures = List.of();
        hidden.parent = root;
        hidden.customization.setPivot(7, 11, 13);
        hidden.customization.visible = false;
        hidden.savedPartToWorldMat.translate(9999, 9999, 9999);
        root.children.add(empty);
        root.children.add(hidden);
        var prunedChild = new FiguraModelPart(avatar, "pruned_child", new PartCustomization(), Map.of(),
                new java.util.ArrayList<>());
        prunedChild.facesByTexture = List.of();
        prunedChild.textures = List.of();
        prunedChild.parent = hidden;
        hidden.children.add(prunedChild);
        root.preRender = avatar.loadScript("native pose pre", """
                return function()
                    models:setPos(2,3,5):setRot(0,0,90):setScale(2,3,4)
                end
                """).call().checkfunction();
        root.postRender = avatar.loadScript("native pose post", """
                return function()
                    models:setPos(0,0,0):setRot(0,0,0):setScale(1,1,1)
                end
                """).call().checkfunction();
        var resource = new OutfitConfig.ResourceRef(OutfitConfig.Source.LOCAL, UUID.randomUUID(), null, null,
                null, "1".repeat(64), OutfitConfig.Format.FIGURA);
        var cache = new SkeletonIndex.Cache();
        var adapter = new FiguraSkeletonAdapter(renderer, resource, null, cache, OutfitLimits.defaults());
        var visibleReference = adapter.binding().definition().reference(null,
                adapter.binding().definition().uniqueNamed("empty_anchor").orElseThrow());
        var hiddenReference = adapter.binding().definition().reference(null,
                adapter.binding().definition().uniqueNamed("hidden_anchor").orElseThrow());
        renderer.setupRenderer(PartFilterScheme.MODEL, type -> {
            throw new AssertionError("empty native skeleton must not require a vertex buffer");
        }, new com.mojang.blaze3d.vertex.PoseStack(), .5f, 0x00f000f0, 1, 0, false, false);
        renderer.posMat.reset();
        renderer.normalMat.reset();
        avatar.render.reset(65536);
        SkeletonPose pose = adapter.capture(new Matrix4f(), renderer::render);
        // Native root = T(0,24,0)*Rz180; callback pose = T(2,3,5)*Rz90*S(2,3,4).
        // The empty child pivot (7,11,13) therefore lands at (31,7,57).
        float[] expected = {0, -2, 0, 0, 3, 0, 0, 0, 0, 0, 4, 0, 31, 7, 57, 1};
        matrix(pose.matrix(visibleReference).orElseThrow(), expected, "Figura actual callback-time empty pivot");
        matrix(pose.matrix(hiddenReference).orElseThrow(), expected,
                "hidden child inherits current parent before its post-render reset");
        require(root.getTrueRot().z == 0 && !avatar.scriptError,
                "native post-render callback still resets the live part without changing the captured frame");
        try {
            renderer.capturePartPivots(renderer::render, 0, 64);
            throw new AssertionError("zero node capture budget accepted");
        } catch (IllegalArgumentException expectedFailure) {
            require(!avatar.scriptError && root.getTrueRot().z == 0,
                    "pose budget rejection happens after the normal native draw completes");
        }
        matrix(adapter.capture(new Matrix4f(), renderer::render).matrix(hiddenReference).orElseThrow(),
                expected, "capture cleanup permits a subsequent native frame");
        root.postRender = avatar.loadScript("hide after native frame", """
                return function()
                    models:setPos(0,0,0):setRot(0,0,0):setScale(1,1,1):setVisible(false)
                end
                """).call().checkfunction();
        matrix(adapter.capture(new Matrix4f(), renderer::render).matrix(hiddenReference).orElseThrow(),
                expected, "post-render visibility changes cannot overwrite the just-drawn pose");
        require(!root.customization.visible, "native post-render visibility remains authoritative");
        matrix(adapter.capture(new Matrix4f(), renderer::render).matrix(hiddenReference).orElseThrow(),
                new float[]{-1, 0, 0, 0, 0, -1, 0, 0, 0, 0, 1, 0, -7, 13, 13, 1},
                "an entirely hidden root still has this frame's bounded anchor transforms");
        require(cache.parseCount() == 1, "subsequent native draws do not parse the skeleton again");
        root.customization.visible = true;
        root.preRender = null;
        root.postRender = null;
        empty.parentType = top.aihmc.omnimorph.model.ParentType.LeftArm;
        hidden.parentType = top.aihmc.omnimorph.model.ParentType.RightArm;
        empty.postRender = avatar.loadScript("change parent after arm draw", """
                return function(_, _, part)
                    part:setParentType("RightArm")
                end
                """).call().checkfunction();
        renderer.setupRenderer(PartFilterScheme.LEFT_ARM, type -> {
            throw new AssertionError("empty arm skeleton must not require a vertex buffer");
        }, new com.mojang.blaze3d.vertex.PoseStack(), .5f, 0x00f000f0, 1, 0, false, false);
        renderer.posMat.reset();
        renderer.normalMat.reset();
        hidden.parentType = top.aihmc.omnimorph.model.ParentType.World;
        SkeletonPose leftArm = adapter.captureArm(new Matrix4f(), true, renderer::render);
        var prunedReference = adapter.binding().definition().reference(null,
                adapter.binding().definition().uniqueNamed("pruned_child").orElseThrow());
        require(leftArm.matrix(prunedReference).isEmpty(),
                "first-person arm capture prunes descendants of a rejected separate branch without unboxing null");
        require(leftArm.matrix(visibleReference).isPresent() && leftArm.matrix(hiddenReference).isEmpty(),
                "Figura arm capture uses native draw-time parent types before post-render edits");
        require(empty.parentType == top.aihmc.omnimorph.model.ParentType.RightArm,
                "arm capture preserves the author's post-render mutation");
        require(leftArm.definition() == pose.definition() && cache.parseCount() == 1,
                "arm filtering preserves the same stable body references and cached definition");
        System.out.println("PASS Figura native skeleton: draw callbacks, visible/hidden empty pivots, stale-world exclusion and bounded capture cleanup");
    }

    private static void testVanilla() {
        PlayerModel wide = playerModel(false);
        PlayerModel slim = playerModel(true);
        var adapter = new VanillaSkeletonAdapter(wide, OutfitLimits.defaults());
        var slimAdapter = new VanillaSkeletonAdapter(slim, OutfitLimits.defaults());
        require(adapter.binding().definition().hash().equals(slimAdapter.binding().definition().hash()),
                "wide/slim share stable vanilla bone references");
        wide.root().x = 1;
        wide.root().y = 2;
        wide.root().z = 3;
        wide.body.x = 2;
        wide.body.y = 3;
        wide.body.z = 5;
        wide.body.zRot = (float) Math.PI / 2;
        wide.body.xScale = 2;
        wide.body.yScale = 3;
        wide.body.zScale = 4;
        var body = adapter.binding().definition().reference(null,
                adapter.binding().definition().uniqueNamed("Body").orElseThrow());
        SkeletonPose pose = adapter.capture(new Matrix4f());
        matrix(pose.matrix(body).orElseThrow(), new float[]{
                0, 2, 0, 0, -3, 0, 0, 0, 0, 0, 4, 0, 3, 5, 8, 1
        }, "actual vanilla part pivot, rotation and scale");
        pose.matrix(body).orElseThrow().identity();
        near(pose.matrix(body).orElseThrow().m30(), 3, "caller cannot mutate a retained frame");
        wide.body.x = 9;
        near(adapter.capture(new Matrix4f()).matrix(body).orElseThrow().m30(), 10,
                "same adapter reads the next native animation pose");
        near(pose.matrix(body).orElseThrow().m30(), 3, "previous frame remains detached");
        require(pose.matrix(new OutfitConfig.BoneRef(UUID.randomUUID(), body.path(), body.skeletonHash())).isEmpty(),
                "a base pivot cannot resolve as an accessory bone");
    }

    private static PlayerModel playerModel(boolean slim) {
        return new PlayerModel(LayerDefinition.create(PlayerModel.createMesh(CubeDeformation.NONE, slim),
                64, 64).bakeRoot(), slim);
    }

    private static void testYsm(CompoundTag prepared) {
        CompoundTag fixture = prepared.copy();
        CompoundTag ysm = new CompoundTag();
        ysm.putByteArray("main_model", """
                {"minecraft:geometry":[{"description":{"identifier":"geometry.cosmetic",
                "texture_width":16,"texture_height":16},"bones":[
                {"name":"Root","pivot":[2,3,5]},
                {"name":"empty_anchor","parent":"Root","pivot":[7,11,13]}]}]}
                """.getBytes(StandardCharsets.UTF_8));
        ysm.remove("arm_model");
        ysm.putFloat("width_scale", 1);
        ysm.putFloat("height_scale", 1);
        for (String key : List.of("animations", "animation_controllers", "functions", "action_schemas")) {
            ysm.put(key, new ListTag());
        }
        fixture.put("ysm", ysm);
        var resource = new OutfitConfig.ResourceRef(OutfitConfig.Source.LOCAL, UUID.randomUUID(), null, null,
                null, "0".repeat(64), OutfitConfig.Format.YSM_PLAIN);
        Avatar first = createAvatar(resource);
        Avatar second = createAvatar(resource);
        try {
            first.load(fixture.copy());
            second.load(fixture.copy());
            first.initializePreviewPlayer(null);
            second.initializePreviewPlayer(null);
            require(!first.scriptError && first.getYsmRuntime() != null && second.getYsmRuntime() != null,
                    "numeric fixture loads through the original native runtime");
            var cache = new SkeletonIndex.Cache();
            var a = new YsmSkeletonAdapter(first.getYsmRuntime(), first.cosmeticContext.resource(),
                    first.cosmeticContext.instanceId(), cache, OutfitLimits.defaults());
            var b = new YsmSkeletonAdapter(second.getYsmRuntime(), second.cosmeticContext.resource(),
                    second.cosmeticContext.instanceId(), cache, OutfitLimits.defaults());
            require(cache.parseCount() == 1 && a.binding().definition() == b.binding().definition(),
                    "native duplicate instances only parse their structural definition once");
            var runtime = first.getYsmRuntime();
            var root = runtime.getPart("Root");
            root.setPos(4d, 6d, 8d).setRot(0d, 0d, 90d).setScale(2d, 3d, 4d);
            var reference = a.binding().definition().reference(first.cosmeticContext.instanceId(),
                    a.binding().definition().uniqueNamed("empty_anchor").orElseThrow());
            var worldBefore = root.getWorldMatrix().toMatrix4f();
            var submitted = runtime.captureRenderPose(false);
            SkeletonPose pose = a.capture(new Matrix4f(), submitted);
            // The native parser mirrors authored X: parent pivot=(-2,3,5), child=(-7,11,13).
            // Parent script translation=(-4,6,8); Rz90 * S(2,3,4) * delta(-5,8,8)=(-24,-10,32).
            // The resulting pivot=(-30,-1,45) is then multiplied by native model scale 15/16.
            matrix(pose.matrix(reference).orElseThrow(), new float[]{
                    0, 1.875f, 0, 0, -2.8125f, 0, 0, 0, 0, 0, 3.75f, 0,
                    -28.125f, -.9375f, 42.1875f, 1
            }, "YSM empty pivot inherits native signed position, rotation, scale and parent transform");
            require(root.getWorldMatrix().toMatrix4f().equals(worldBefore), "anchor capture never replaces world-space Lua matrices");
            root.resetPose();
            matrix(a.capture(new Matrix4f(), submitted).matrix(reference).orElseThrow(),
                    pose.matrix(reference).orElseThrow().get(new float[16]), "deferred submission keeps its original pose");
            Vector3f next = a.capture(new Matrix4f(), null).matrix(reference).orElseThrow().getTranslation(new Vector3f());
            near(next.x, -7 * .9375f, "fresh native frame uses current state and native mirrored X");
            near(next.y, 11 * .9375f, "fresh native frame keeps YSM height scale");
            require(b.capture(new Matrix4f(), null).matrix(reference).isEmpty(), "native duplicate pose rejects another instance reference");
        } finally {
            first.clean();
            second.clean();
        }
    }

    private static void testYsmArms(CompoundTag prepared) {
        CompoundTag fixture = prepared.copy();
        CompoundTag ysm = new CompoundTag();
        ysm.putByteArray("main_model", """
                {"minecraft:geometry":[{"description":{"identifier":"geometry.body",
                "texture_width":16,"texture_height":16},"bones":[
                {"name":"Root","pivot":[0,0,0]},
                {"name":"LeftArm","parent":"Root","pivot":[2,3,4]},
                {"name":"hand_anchor","parent":"LeftArm","pivot":[5,6,7]},
                {"name":"body_only","parent":"LeftArm","pivot":[8,9,10]},
                {"name":"RightArm","parent":"Root","pivot":[-2,3,4]}]}]}
                """.getBytes(StandardCharsets.UTF_8));
        ysm.putByteArray("arm_model", """
                {"minecraft:geometry":[{"description":{"identifier":"geometry.arm",
                "texture_width":16,"texture_height":16},"bones":[
                {"name":"Root","pivot":[1,2,3]},
                {"name":"LeftArm","parent":"Root","pivot":[7,11,13]},
                {"name":"hand_anchor","parent":"LeftArm","pivot":[17,19,23]},
                {"name":"RightArm","parent":"Root","pivot":[-7,11,13]}]}]}
                """.getBytes(StandardCharsets.UTF_8));
        ysm.putFloat("width_scale", 3);
        ysm.putFloat("height_scale", 4);
        for (String key : List.of("animations", "animation_controllers", "functions", "action_schemas")) {
            ysm.put(key, new ListTag());
        }
        fixture.put("ysm", ysm);
        var resource = new OutfitConfig.ResourceRef(OutfitConfig.Source.LOCAL, UUID.randomUUID(), null, null,
                null, "7".repeat(64), OutfitConfig.Format.YSM_PLAIN);
        Avatar avatar = createAvatar(resource);
        try {
            avatar.load(fixture);
            avatar.initializePreviewPlayer(null);
            var runtime = avatar.getYsmRuntime();
            require(runtime != null && runtime.hasArmModel() && !avatar.scriptError,
                    "separate body/arm fixture loads through the native runtime");
            var cache = new SkeletonIndex.Cache();
            var adapter = new YsmSkeletonAdapter(runtime, resource, avatar.cosmeticContext.instanceId(),
                    cache, OutfitLimits.defaults());
            var definition = adapter.binding().definition();
            var hand = definition.reference(avatar.cosmeticContext.instanceId(),
                    definition.uniqueNamed("hand_anchor").orElseThrow());
            var right = definition.reference(avatar.cosmeticContext.instanceId(),
                    definition.uniqueNamed("RightArm").orElseThrow());
            var missing = definition.reference(avatar.cosmeticContext.instanceId(),
                    definition.uniqueNamed("body_only").orElseThrow());
            runtime.getPart("Root").setPos(1000d, 1000d, 1000d);
            runtime.getArmPart("Root").setPos(4d, 6d, 8d).setRot(0d, 0d, 90d).setScale(2d, 3d, 4d);
            var submitted = runtime.captureRenderPose(true);
            SkeletonPose left = adapter.captureArm(new Matrix4f(), true, submitted);
            matrix(left.matrix(hand).orElseThrow(), new float[]{
                    0, 2, 0, 0, -3, 0, 0, 0, 0, 0, 4, 0, -56, -24, 91, 1
            }, "first-person arm hierarchy uses its own pivots and omits body width/height scale");
            require(left.matrix(right).isEmpty() && left.matrix(missing).isEmpty(),
                    "opposite-hand and body-only bones cannot resolve in the left arm pass");
            runtime.getArmPart("Root").resetPose();
            matrix(adapter.captureArm(new Matrix4f(), true, submitted).matrix(hand).orElseThrow(),
                    left.matrix(hand).orElseThrow().get(new float[16]), "arm snapshot survives later native part edits");
            SkeletonPose rightPose = adapter.captureArm(new Matrix4f(), false, submitted);
            require(rightPose.matrix(right).isPresent() && rightPose.matrix(hand).isEmpty(),
                    "right arm keeps independent availability with the same body references");
            require(left.definition() == rightPose.definition() && cache.parseCount() == 1,
                    "arm passes reuse the body definition without reparsing");
        } finally {
            avatar.clean();
        }
        System.out.println("PASS native YSM arm anchors: separate geometry, mirrored pivots, no body scale, left/right availability and deferred pose");
    }

    private static Avatar createAvatar(OutfitConfig.ResourceRef resource) {
        UUID wearer = UUID.randomUUID();
        var instance = new OutfitConfig.Instance(UUID.randomUUID(), resource, 1, true,
                OutfitConfig.AnchorRef.authorDefault("head_anchor"), OutfitConfig.Transform.identity(),
                List.of(), Map.of(), Map.of());
        var context = new CosmeticInstanceContext(wearer, UUID.randomUUID(), wearer, instance,
                OutfitLimits.defaults(), CosmeticInstanceContext.Scope.DRAFT);
        var permissions = new PermissionPack.PlayerPermissionPack(
                new PermissionPack.CategoryPermissionPack(Permissions.Category.DEFAULT), wearer.toString());
        return Avatar.createCosmetic(context, permissions, "native-pose");
    }

    private static void matrix(Matrix4f actual, float[] expected, String label) {
        float[] values = actual.get(new float[16]);
        for (int index = 0; index < values.length; index++) {
            near(values[index], expected[index], label + " coefficient " + index);
        }
    }

    private static void near(float actual, float expected, String label) {
        require(Float.isFinite(actual) && Math.abs(actual - expected) < .0001f,
                label + ": expected " + expected + ", actual " + actual);
    }

    private static void require(boolean condition, String label) {
        if (!condition) {
            throw new AssertionError(label);
        }
    }
}
