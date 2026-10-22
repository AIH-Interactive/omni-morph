package top.aihmc.omnimorph.model.outfit;

import com.google.gson.FieldNamingPolicy;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.nbt.CompoundTag;
import org.joml.Matrix4f;
import top.aihmc.omnimorph.avatar.Avatar;
import top.aihmc.omnimorph.model.FiguraModelPart;
import top.aihmc.omnimorph.model.PartCustomization;
import top.aihmc.omnimorph.model.rendering.EntityRenderMode;
import top.aihmc.omnimorph.model.rendering.ImmediateFiguraRenderer;
import top.aihmc.omnimorph.permissions.PermissionPack;
import top.aihmc.omnimorph.permissions.Permissions;
import top.aihmc.omnimorph.render.acceleration.AccelerationBackend;
import top.aihmc.omnimorph.render.acceleration.AccelerationService;
import top.aihmc.omnimorph.render.acceleration.JavaAccelerationBackend;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static top.aihmc.omnimorph.model.outfit.OutfitNativeRenderSmokeTest.addQuad;
import static top.aihmc.omnimorph.model.outfit.OutfitNativeRenderSmokeTest.near;
import static top.aihmc.omnimorph.model.outfit.OutfitNativeRenderSmokeTest.vertexSink;
import static top.aihmc.omnimorph.model.outfit.OutfitNativeRenderSmokeTest.ysmCube;

/** Actual native CPU vertices and snapshots. No claim of GUI, JNI or GPU validation. */
public final class CosmeticBoneOverrideSmokeTest {
    public static void run() throws Exception {
        nativeOverrides(false, false);
        nativeOverrides(false, true);
        nativeOverrides(true, false);
        nativeOverrides(true, true);
        skinnedOverride(false);
        skinnedOverride(true);
        validation();
    }

    private static void skinnedOverride(boolean accelerated) throws Exception {
        var service = AccelerationService.INSTANCE;
        var setter = service.getClass().getDeclaredMethod("useBackend", AccelerationBackend.class);
        setter.setAccessible(true);
        setter.invoke(service, accelerated ? new JavaAccelerationBackend() : null);
        UUID account = UUID.randomUUID();
        var resource = resource(OutfitConfig.Format.FIGURA);
        var model = new PlayerModel(LayerDefinition.create(PlayerModel.createMesh(CubeDeformation.NONE, false),
                64, 64).bakeRoot(), false);
        var base = new VanillaSkeletonAdapter(model, OutfitLimits.defaults());
        var anchor = new OutfitConfig.AnchorRef(OutfitConfig.AnchorType.BASE_BONE, null,
                base.binding().definition().reference(null, 0), null, null, null);
        var original = instance(UUID.randomUUID(), resource, 1, anchor, OutfitConfig.Transform.identity(), List.of());
        Avatar avatar = load(original, account, UUID.randomUUID(), permissions(account));
        try {
            var quad = avatar.renderer.root.children.getFirst();
            var joint = new FiguraModelPart(avatar, "EditJoint", new PartCustomization(), Map.of(), new ArrayList<>());
            joint.parent = avatar.renderer.root;
            joint.facesByTexture = List.of();
            joint.textures = List.of();
            joint.customization.setPivot(1, 2, 3);
            avatar.renderer.root.children.add(joint);
            quad.skinBoneNames = new String[]{"EditJoint"};
            quad.skinBoneIndices = new int[][]{{0}, {0}, {0}, {0}};
            quad.skinBoneWeights = new float[][]{{1}, {1}, {1}, {1}};
            for (int index = 0; index < 4; index++) {
                quad.vertices.get(0).get(index).origIdx = index;
            }
            var indexes = new SkeletonIndex.Cache();
            var skeleton = new FiguraSkeletonAdapter((ImmediateFiguraRenderer) avatar.renderer, resource,
                    original.instanceId(), indexes, OutfitLimits.defaults()).binding().definition();
            var relative = skeleton.reference(null, skeleton.uniqueNamed("EditJoint").orElseThrow());
            var own = skeleton.reference(original.instanceId(), skeleton.uniqueNamed("EditJoint").orElseThrow());
            var override = new OutfitConfig.BoneOverride(own, new OutfitConfig.Transform(
                    new OutfitConfig.Vector(2, 3, 4), new OutfitConfig.Vector(0, 0, 90), new OutfitConfig.Vector(2, 3, 4)));
            var declared = instance(original.instanceId(), resource, 1, anchor, original.transform(), List.of(override));
            var metadata = new CompoundTag();
            metadata.putString("cosmetic_json", declaration(relative, true, true).toString());
            avatar.nbt.put("metadata", metadata);
            var config = new OutfitConfig(1, 0, null, List.of(declared), List.of(), Map.of(resource.identity(), 1), false);
            try (var composition = new OutfitComposition(config, OutfitLimits.defaults(), null,
                    Map.of(declared.instanceId(), avatar), indexes); var frame = composition.retain()) {
                var state = new net.minecraft.client.renderer.entity.state.AvatarRenderState();
                state.lightCoords = 0x00f000f0;
                var positions = new ArrayList<float[]>();
                var sink = vertexSink(positions);
                OutfitNativeRenderer.drawVanilla(frame, model, state, .5f, new Matrix4f(), new Matrix4f(),
                        type -> sink, EntityRenderMode.FIGURA_GUI);
                require(positions.size() == 4, "weighted mesh emits its original quad");
                near(positions.get(0), 3f / 16, 5f / 16, 7f / 16);
                near(positions.get(1), 3f / 16, 7f / 16, 7f / 16);
                near(positions.get(2), 0, 7f / 16, 7f / 16);
                require(joint.customization.getPos().x == 0 && joint.customization.getScale().x == 1,
                        "weighted bone overlay does not replace author channels");
            }
        } finally {
            avatar.clean();
            setter.invoke(service, new Object[]{null});
        }
        System.out.println("PASS weighted Figura internal bone overlay: native joint pivot, rotation and scale; accelerated preparation="
                + accelerated + "; GUI/GPU MANUAL/PENDING");
    }

    private static void nativeOverrides(boolean ysm, boolean accelerated) throws Exception {
        var service = AccelerationService.INSTANCE;
        var setBackend = service.getClass().getDeclaredMethod("useBackend", AccelerationBackend.class);
        setBackend.setAccessible(true);
        setBackend.invoke(service, accelerated ? new JavaAccelerationBackend() : null);
        var limits = OutfitLimits.defaults();
        var indexes = new SkeletonIndex.Cache();
        var model = new PlayerModel(LayerDefinition.create(PlayerModel.createMesh(CubeDeformation.NONE, false),
                64, 64).bakeRoot(), false);
        var base = new VanillaSkeletonAdapter(model, limits);
        var anchor = new OutfitConfig.AnchorRef(OutfitConfig.AnchorType.BASE_BONE, null,
                base.binding().definition().reference(null, 0), null, null, null);
        var resource = resource(ysm ? OutfitConfig.Format.YSM_PLAIN : OutfitConfig.Format.FIGURA);
        var childResource = ysm ? resource(OutfitConfig.Format.FIGURA) : resource;
        UUID account = UUID.randomUUID();
        UUID outfit = UUID.randomUUID();
        var permissions = permissions(account);
        var original = instance(UUID.randomUUID(), resource, 1, anchor, translation(16, 0, 0), List.of());
        var second = instance(UUID.randomUUID(), resource, 2, anchor, translation(32, 0, 0), List.of());
        var avatars = new LinkedHashMap<UUID, Avatar>();
        try (var textures = top.aihmc.omnimorph.model.ysm.YsmMaterialNativeSmokeTest.recordingTextureDevice()) {
            Avatar parent = load(original, account, outfit, permissions);
            avatars.put(original.instanceId(), parent);
            avatars.put(second.instanceId(), load(second, account, outfit, permissions));
            SkeletonIndex skeleton;
            if (ysm) {
                skeleton = new YsmSkeletonAdapter(parent.getYsmRuntime(), resource, original.instanceId(), indexes, limits)
                        .binding().definition();
                parent.getYsmRuntime().getPart("Body").setPos(-4d, 0d, 0d);
            } else {
                skeleton = new FiguraSkeletonAdapter((ImmediateFiguraRenderer) parent.renderer, resource,
                        original.instanceId(), indexes, limits).binding().definition();
                var part = parent.renderer.root.children.getFirst();
                part.customization.setPos(4, 0, 0);
                part.customization.setAnimPos(2, 0, 0);
                parent.initializePreviewPlayer(null);
                part.preRender = parent.loadScript("internal override author channels", """
                        return function(_, _, part)
                            assert(part:getPos().x == 4 and part:getScale().x == 1)
                            part:setPos(4, 0, 0)
                        end
                        """).call().checkfunction();
            }
            var relative = skeleton.reference(null, skeleton.uniqueNamed(ysm ? "Body" : "Cosmetic").orElseThrow());
            var owned = new OutfitConfig.BoneRef(original.instanceId(), relative.path(), relative.skeletonHash());
            var override = new OutfitConfig.BoneOverride(owned, new OutfitConfig.Transform(
                    new OutfitConfig.Vector(2, 3, 4), new OutfitConfig.Vector(0, 0, 90), new OutfitConfig.Vector(2, 3, 4)));
            var first = instance(original.instanceId(), resource, 1, anchor, original.transform(), List.of(override));
            var childAnchor = new OutfitConfig.AnchorRef(OutfitConfig.AnchorType.ACCESSORY_BONE, null,
                    owned, null, null, null);
            var child = instance(UUID.randomUUID(), childResource, ysm ? 1 : 3, childAnchor,
                    OutfitConfig.Transform.identity(), List.of());
            avatars.put(child.instanceId(), load(child, account, outfit, permissions));
            String metadata = declaration(relative, true, true).toString();
            for (var id : List.of(first.instanceId(), second.instanceId())) {
                var source = avatars.get(id).nbt;
                var tag = source.getCompoundOrEmpty("metadata");
                tag.putString("cosmetic_json", metadata);
                source.put("metadata", tag);
            }
            var sourceBefore = parent.nbt.copy();
            var watermarks = new LinkedHashMap<String, Integer>();
            watermarks.put(resource.identity(), ysm ? 2 : 3);
            if (ysm) {
                watermarks.put(childResource.identity(), 1);
            }
            var config = new OutfitConfig(1, 0, null, List.of(child, second, first), List.of(), watermarks, false);
            try (var composition = new OutfitComposition(config, limits, null, avatars, indexes);
                 var frame = composition.retain()) {
                var state = new net.minecraft.client.renderer.entity.state.AvatarRenderState();
                state.lightCoords = 0x00f000f0;
                var positions = new ArrayList<float[]>();
                var normals = new ArrayList<float[]>();
                var sink = vertexSink(positions, normals);
                var outer = new Matrix4f().translation(7, 11, 13);
                OutfitNativeRenderer.drawVanilla(frame, model, state, .5f, outer, outer, type -> sink,
                        EntityRenderMode.FIGURA_GUI);
                require(service.accelerated() == accelerated, "the requested native preparation branch is active");
                require(positions.size() == (ysm ? 52 : 12), "both copies and the dependent emit their original native meshes");
                if (ysm) {
                    for (var point : positions.subList(24, 48)) {
                        inRange(point[0], 7 + 10.375f / 16, 8, "YSM overridden X");
                        inRange(point[1], 11 - .9375f / 16, 11 + .9375f / 16, "YSM overridden Y");
                        inRange(point[2], 13 + 15f / 16, 13 + 26.25f / 16, "YSM overridden Z");
                    }
                    near(positions.get(48), 8, 11 + 4.6875f / 16, 13 + 15f / 16);
                    require(parent.getYsmRuntime().getPart("Body").getPos().x == -4,
                            "YSM authored transforms remain independent from the retained override");
                    require(avatars.get(second.instanceId()).getYsmRuntime().getPart("Body").getPos().x == 0,
                            "a second copy has no first-copy author or override state");
                } else {
                    near(positions.get(0), 7 + 33f / 16, 11 + 2f / 16, 13 + 3f / 16);
                    near(positions.get(4), 7 + 25f / 16, 11 + 5f / 16, 13 + 7f / 16);
                    near(positions.get(5), 7 + 25f / 16, 11 + 7f / 16, 13 + 7f / 16);
                    near(positions.get(8), 7 + 19f / 16, 11 + 7f / 16, 13 + 19f / 16);
                    require(normals.size() == positions.size(), "native Figura normals accompany every emitted vertex");
                    near(normals.get(4), 0, 0, normals.getFirst()[2] * (float) Math.cbrt(24) / 4);
                    var part = parent.renderer.root.children.getFirst();
                    require(part.customization.getPos().x == 4 && part.customization.getAnimPos().x == 2
                                    && part.customization.getScale().x == 1,
                            "Figura authored and animation channels do not absorb or accumulate the overlay");
                }
                require(parent.nbt.equals(sourceBefore), "runtime overlays never rewrite source NBT");
                var retained = positions.stream().map(float[]::clone).toList();
                positions.clear();
                if (ysm) {
                    parent.getYsmRuntime().getPart("Body").setPos(999d, 0d, 0d);
                } else {
                    parent.renderer.root.children.getFirst().customization.setPos(999, 0, 0);
                }
                OutfitNativeRenderer.drawVanilla(frame, model, state, .5f, outer, outer, type -> sink,
                        EntityRenderMode.FIGURA_GUI);
                require(positions.size() == retained.size(), "retained override snapshots survive later live mutations");
                for (int index = 0; index < positions.size(); index++) {
                    var expected = retained.get(index);
                    near(positions.get(index), expected[0], expected[1], expected[2]);
                }
                require(indexes.parseCount() == (ysm ? 3 : 2), "internal edits reuse the cached definitions");
                if (!ysm) {
                    var parts = new FiguraSkeletonAdapter((ImmediateFiguraRenderer) parent.renderer, resource,
                            first.instanceId(), indexes, limits).binding().parts();
                    var mounted = new ArrayList<OutfitComposition.Draw>();
                    frame.draw(base.capture(new Matrix4f()), state, .5f, mounted::add);
                    var command = mounted.stream().filter(draw -> draw.instanceId().equals(first.instanceId()))
                            .findFirst().orElseThrow();
                    try {
                        command.figuraPose().draw(() -> {
                            throw new IllegalStateException("overlay scope failure");
                        });
                        throw new AssertionError("overlay scope failure was swallowed");
                    } catch (IllegalStateException expected) {
                        require(expected.getMessage().equals("overlay scope failure"), "native scope preserves its exception");
                    }
                    require(parent.renderer.root.children.getFirst().customization.getPos().x == 999,
                            "failed retained overlay restores newer authored state");
                    parent.renderer.root.children.getFirst().customization.setPos(4, 0, 0);
                    var bare = new FiguraRenderPose(parts, service.capture(parent.accelerationOwner(), "bare"));
                    positions.clear();
                    OutfitNativeRenderer.drawInstance(new OutfitComposition.Draw(first.instanceId(), parent,
                            command.matrix(), null, bare), .5f, outer, type -> sink, state.lightCoords,
                            EntityRenderMode.FIGURA_GUI);
                    require(positions.size() == 4, "a draw without configured offsets uses the original mesh");
                    near(positions.getFirst(), 7 + 23f / 16, 11 + 2f / 16, 13 + 3f / 16);
                }
            }
        } finally {
            avatars.values().forEach(Avatar::clean);
            setBackend.invoke(service, new Object[]{null});
        }
        System.out.println("PASS internal native overlays: local pivot T/R/S, child sockets, two-copy isolation, immutable source and retained pose; YSM="
                + ysm + ", accelerated preparation=" + accelerated + "; GUI/GPU MANUAL/PENDING");
    }

    private static void validation() {
        var account = UUID.randomUUID();
        var resource = resource(OutfitConfig.Format.FIGURA);
        var original = instance(UUID.randomUUID(), resource, 1, OutfitConfig.AnchorRef.authorDefault("head_anchor"),
                OutfitConfig.Transform.identity(), List.of());
        Avatar avatar = load(original, account, UUID.randomUUID(), permissions(account));
        try {
            var indexes = new SkeletonIndex.Cache();
            var skeleton = new FiguraSkeletonAdapter((ImmediateFiguraRenderer) avatar.renderer, resource,
                    original.instanceId(), indexes, OutfitLimits.defaults()).binding().definition();
            var relative = skeleton.reference(null, skeleton.uniqueNamed("Cosmetic").orElseThrow());
            var own = skeleton.reference(original.instanceId(), skeleton.uniqueNamed("Cosmetic").orElseThrow());
            var instance = instance(original.instanceId(), resource, 1, original.anchor(), original.transform(),
                    List.of(new OutfitConfig.BoneOverride(own, translation(1, 2, 3))));
            var valid = CosmeticMetadata.read(declaration(relative, true, true).toString());
            rejects(() -> CosmeticBoneOverrides.bind(avatar, instance, valid, false, indexes, OutfitLimits.defaults()),
                    "FORBIDDEN");
            rejects(() -> CosmeticBoneOverrides.bind(avatar, instance, null, true, indexes, OutfitLimits.defaults()),
                    "FORBIDDEN");
            var readOnly = CosmeticMetadata.read(declaration(relative, false, false).toString());
            rejects(() -> CosmeticBoneOverrides.bind(avatar, instance, readOnly, true, indexes, OutfitLimits.defaults()),
                    "FORBIDDEN");
            var noRange = CosmeticMetadata.read(declaration(relative, true, false).toString());
            rejects(() -> CosmeticBoneOverrides.bind(avatar, instance, noRange, true, indexes, OutfitLimits.defaults()),
                    "OUTSIDE_DECLARATION");
            var obsolete = new OutfitConfig.BoneRef(null, relative.path(), "1".repeat(64));
            var staleMetadata = CosmeticMetadata.read(declaration(obsolete, true, true).toString());
            var stale = instance(original.instanceId(), resource, 1, original.anchor(), original.transform(),
                    List.of(new OutfitConfig.BoneOverride(new OutfitConfig.BoneRef(original.instanceId(), obsolete.path(),
                            obsolete.skeletonHash()), translation(1, 2, 3))));
            rejects(() -> CosmeticBoneOverrides.bind(avatar, stale, staleMetadata, true, indexes, OutfitLimits.defaults()),
                    "STALE");
            require(CosmeticBoneOverrides.bind(avatar, instance, valid, true, indexes, OutfitLimits.defaults())
                    .figura().size() == 1, "re-binding the correct authorized path succeeds after rejection");
        } finally {
            avatar.clean();
        }
        System.out.println("PASS internal edit gates: author flag/range, user policy and runtime revision, with authorized rebind");
    }

    private static JsonObject declaration(OutfitConfig.BoneRef relative, boolean edit, boolean range) {
        var result = new JsonObject();
        result.addProperty("has_bones", true);
        result.addProperty("edit_bones", edit);
        result.addProperty("skeleton_hash", relative.skeletonHash());
        var bones = new JsonArray();
        bones.add(new GsonBuilder().setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES)
                .create().toJsonTree(relative));
        result.add("exported_bones", bones);
        if (range) {
            result.add("editable_bones", bones.deepCopy());
        }
        return result;
    }

    private static Avatar load(OutfitConfig.Instance declaration, UUID account, UUID outfit,
                                PermissionPack.PlayerPermissionPack permissions) {
        var context = new CosmeticInstanceContext(account, outfit, account, declaration,
                OutfitLimits.defaults(), CosmeticInstanceContext.Scope.DRAFT);
        Avatar avatar = Avatar.createCosmetic(context, permissions, "native internal override");
        avatar.load(declaration.resource().format() == OutfitConfig.Format.FIGURA ? new CompoundTag() : ysmCube());
        if (!avatar.isYsmNative()) {
            addQuad(avatar);
            avatar.renderer.root.children.getFirst().customization.setPivot(1, 2, 3);
        }
        return avatar;
    }

    private static PermissionPack.PlayerPermissionPack permissions(UUID account) {
        return new PermissionPack.PlayerPermissionPack(
                new PermissionPack.CategoryPermissionPack(Permissions.Category.DEFAULT), account.toString());
    }

    private static OutfitConfig.ResourceRef resource(OutfitConfig.Format format) {
        return new OutfitConfig.ResourceRef(OutfitConfig.Source.LOCAL, UUID.randomUUID(), null, null,
                null, "8".repeat(64), format);
    }

    private static OutfitConfig.Instance instance(UUID id, OutfitConfig.ResourceRef resource, int ordinal,
                                                   OutfitConfig.AnchorRef anchor, OutfitConfig.Transform transform,
                                                   List<OutfitConfig.BoneOverride> overrides) {
        return new OutfitConfig.Instance(id, resource, ordinal, true, anchor, transform, overrides, Map.of(), Map.of());
    }

    private static OutfitConfig.Transform translation(double x, double y, double z) {
        return new OutfitConfig.Transform(new OutfitConfig.Vector(x, y, z), new OutfitConfig.Vector(0, 0, 0),
                new OutfitConfig.Vector(1, 1, 1));
    }

    private static void inRange(float value, float min, float max, String label) {
        require(Float.isFinite(value) && value >= min - .0001f && value <= max + .0001f,
                label + ": got " + value + ", expected [" + min + ", " + max + "]");
    }

    private static void rejects(Runnable operation, String reason) {
        try {
            operation.run();
            throw new AssertionError("expected rejection: " + reason);
        } catch (IllegalArgumentException expected) {
            require(expected.getMessage().contains(reason), "explicit internal edit rejection: " + expected);
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
