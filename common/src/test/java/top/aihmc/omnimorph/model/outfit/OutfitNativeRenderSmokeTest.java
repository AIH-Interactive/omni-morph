package top.aihmc.omnimorph.model.outfit;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import org.joml.Matrix4f;
import org.figuramc.figura.avatar.Avatar;
import org.figuramc.figura.model.FiguraModelPart;
import org.figuramc.figura.model.PartCustomization;
import org.figuramc.figura.model.TextureCustomization;
import org.figuramc.figura.model.rendering.EntityRenderMode;
import org.figuramc.figura.model.rendering.Vertex;
import org.figuramc.figura.model.rendering.texture.FiguraTextureSet;
import org.figuramc.figura.permissions.PermissionPack;
import org.figuramc.figura.permissions.Permissions;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Actual native Figura vertex emission; pixels, GUI geometry and GPU output remain manual. */
public final class OutfitNativeRenderSmokeTest {
    public static void run() throws Exception {
        var limits = OutfitLimits.defaults();
        var model = new PlayerModel(LayerDefinition.create(PlayerModel.createMesh(CubeDeformation.NONE, false),
                64, 64).bakeRoot(), false);
        var base = new VanillaSkeletonAdapter(model, limits);
        var anchor = new OutfitConfig.AnchorRef(OutfitConfig.AnchorType.BASE_BONE, null,
                base.binding().definition().reference(null, 0), null, null, null);
        var resource = new OutfitConfig.ResourceRef(OutfitConfig.Source.LOCAL, UUID.randomUUID(), null, null,
                null, "9".repeat(64), OutfitConfig.Format.FIGURA);
        var declarations = new ArrayList<OutfitConfig.Instance>();
        var avatars = new LinkedHashMap<UUID, Avatar>();
        UUID account = UUID.randomUUID();
        UUID outfit = UUID.randomUUID();
        var permissions = new PermissionPack.PlayerPermissionPack(
                new PermissionPack.CategoryPermissionPack(Permissions.Category.DEFAULT), account.toString());
        try {
            for (int index = 0; index < 2; index++) {
                var transform = new OutfitConfig.Transform(new OutfitConfig.Vector(3 + index * 6, 5, 8),
                        new OutfitConfig.Vector(0, 0, 90), new OutfitConfig.Vector(2, 3, 4));
                var declaration = new OutfitConfig.Instance(UUID.randomUUID(), resource, index + 1, true,
                        anchor, transform, List.of(), Map.of(), Map.of());
                declarations.add(declaration);
                var context = new CosmeticInstanceContext(account, outfit, account, declaration, limits,
                        CosmeticInstanceContext.Scope.DRAFT);
                Avatar avatar = Avatar.createCosmetic(context, permissions, "native mounted quad");
                avatars.put(declaration.instanceId(), avatar);
                avatar.load(new CompoundTag());
                avatar.renderMode = EntityRenderMode.RENDER;
                addQuad(avatar);
            }
            var configuration = new OutfitConfig(1, 0, null, declarations, List.of(),
                    Map.of(resource.identity(), 2), false);
            var indexes = new SkeletonIndex.Cache();
            try (var composition = new OutfitComposition(configuration, limits, null, avatars, indexes);
                 var frame = composition.retain()) {
                var positions = new ArrayList<float[]>();
                VertexConsumer sink = (VertexConsumer) Proxy.newProxyInstance(VertexConsumer.class.getClassLoader(),
                        new Class<?>[]{VertexConsumer.class}, (proxy, method, arguments) -> {
                            if (method.isDefault()) {
                                return InvocationHandler.invokeDefault(proxy, method, arguments);
                            }
                            if (method.getName().equals("addVertex")) {
                                positions.add(new float[]{((Number) arguments[0]).floatValue(),
                                        ((Number) arguments[1]).floatValue(), ((Number) arguments[2]).floatValue()});
                            }
                            return method.getReturnType() == void.class ? null : proxy;
                        });
                var entityToDraw = new Matrix4f().translation(7, 11, 13);
                var state = new net.minecraft.client.renderer.entity.state.AvatarRenderState();
                state.lightCoords = 0x00f000f0;
                OutfitNativeRenderer.drawVanilla(frame, model, state, .5f, entityToDraw, entityToDraw,
                        type -> sink, EntityRenderMode.FIGURA_GUI);
                require(positions.size() == 8, "both native accessory quads emit once");
                // (1,2,3) -> S(2,3,4) -> Rz90 -> T(3,5,8) = (-3,7,20) model units.
                // Divide by 16 then add the independent draw-space translation (7,11,13).
                near(positions.get(0), 6.8125f, 11.4375f, 14.25f);
                near(positions.get(4), 7.1875f, 11.4375f, 14.25f);
                require(indexes.parseCount() == 2, "one resource definition and one vanilla skeleton are parsed");
                positions.clear();
                for (Avatar avatar : avatars.values()) {
                    var part = avatar.renderer.root.children.getFirst();
                    part.customization.setPos(16, 0, 0);
                    part.customization.setAnimPos(8, 0, 0);
                    part.postRender = avatar.loadScript("native pose persistence", """
                            return function(_, _, part)
                                part:setPos(32, 0, 0)
                            end
                            """).call().checkfunction();
                }
                try (var nextFrame = composition.retain()) {
                    OutfitNativeRenderer.drawVanilla(nextFrame, model, state, .5f, entityToDraw, entityToDraw,
                            type -> sink, EntityRenderMode.FIGURA_GUI);
                    require(positions.size() == 8, "newer retained frame draws both native quads");
                    near(positions.getFirst(), 6.8125f, 14.4375f, 14.25f);
                    for (Avatar avatar : avatars.values()) {
                        require(avatar.renderer.root.children.getFirst().customization.getPos().x == 32,
                                "current-frame native post-render edits remain persistent");
                    }
                    positions.clear();
                    OutfitNativeRenderer.drawVanilla(frame, model, state, .5f, entityToDraw, entityToDraw,
                            type -> sink, EntityRenderMode.FIGURA_GUI);
                    require(positions.size() == 8, "older retained frame remains drawable after the newer pass");
                    near(positions.getFirst(), 6.8125f, 11.4375f, 14.25f);
                    for (Avatar avatar : avatars.values()) {
                        var custom = avatar.renderer.root.children.getFirst().customization;
                        require(custom.getPos().x == 32 && custom.getAnimPos().x == 8,
                                "old pose restores newer authored and animation channels after native drawing");
                    }
                    positions.clear();
                    OutfitNativeRenderer.drawVanilla(nextFrame, model, state, .5f, entityToDraw, entityToDraw,
                            type -> sink, EntityRenderMode.FIGURA_GUI);
                    near(positions.getFirst(), 6.8125f, 14.4375f, 14.25f);
                    try {
                        frame.draw(base.capture(new Matrix4f()), state, .5f, command ->
                                command.figuraPose().draw(() -> {
                                    command.avatar().renderer.root.children.getFirst().customization.setPos(999, 0, 0);
                                    throw new IllegalStateException("fixture draw failure");
                                }));
                        throw new AssertionError("fixture draw failure was swallowed");
                    } catch (IllegalStateException failure) {
                        require("fixture draw failure".equals(failure.getMessage()),
                                "the original drawing failure reaches the caller");
                    }
                    require(avatars.values().stream().allMatch(avatar ->
                                    avatar.renderer.root.children.getFirst().customization.getPos().x == 32),
                            "failed old-pose drawing restores the newer native part state");
                }
                require(indexes.parseCount() == 2 && positions.size() == 8,
                        "subsequent native vanilla frames rebind the shared skeleton without reparsing");
                for (Avatar avatar : avatars.values()) {
                    require(!avatar.scriptError && avatar.renderMode == EntityRenderMode.RENDER,
                            "native mounted drawing restores each instance mode");
                }
                for (Avatar avatar : avatars.values()) {
                    avatar.permissions.insert(Permissions.COMPLEXITY, 1, org.figuramc.figura.FiguraMod.MOD_ID);
                    avatar.complexity.reset(1);
                }
                top.aihmc.omnimorph.model.ysm.animation.YsmRenderFrameClock.beginFrame();
                positions.clear();
                OutfitNativeRenderer.drawVanilla(frame, model, state, .5f, entityToDraw, entityToDraw,
                        type -> sink, EntityRenderMode.FIGURA_GUI);
                require(positions.size() == 4, "two Figura instances share one native face allowance");
                positions.clear();
                OutfitNativeRenderer.drawVanilla(frame, model, state, .5f, entityToDraw, entityToDraw,
                        type -> sink, EntityRenderMode.FIGURA_GUI);
                require(positions.isEmpty(), "replaying the same native frame cannot refill face allowance");
            }
            System.out.println("PASS composed native Figura vertices: two copies, mount matrices, interleaved frame poses, native callbacks and failure cleanup; GUI/GPU MANUAL/PENDING");
        } finally {
            for (Avatar avatar : avatars.values()) {
                avatar.clean();
            }
        }
        testVanillaArms();
        testFiguraBaseFrames();
        testAccessoryMounts(true, false);
        testAccessoryMounts(false, false);
        testAccessoryMounts(true, true);
        testYsmAccessorySocket();
        CosmeticBoneOverrideSmokeTest.run();
        OutfitAutomaticAnchorSmokeTest.run();
        OutfitTrailRuntimeSmokeTest.run();
        testYsmBaseBudget(false);
        testYsmBaseBudget(true);
    }

    private static void testAccessoryMounts(boolean authorized, boolean staleDeclaration) throws Exception {
        var limits = OutfitLimits.defaults();
        var model = new PlayerModel(LayerDefinition.create(PlayerModel.createMesh(CubeDeformation.NONE, false),
                64, 64).bakeRoot(), false);
        var base = new VanillaSkeletonAdapter(model, limits);
        var anchor = new OutfitConfig.AnchorRef(OutfitConfig.AnchorType.BASE_BONE, null,
                base.binding().definition().reference(null, 0), null, null, null);
        var resource = new OutfitConfig.ResourceRef(OutfitConfig.Source.LOCAL, UUID.randomUUID(), null, null,
                null, "7".repeat(64), OutfitConfig.Format.FIGURA);
        UUID account = UUID.randomUUID();
        UUID outfit = UUID.randomUUID();
        var permissions = new PermissionPack.PlayerPermissionPack(
                new PermissionPack.CategoryPermissionPack(Permissions.Category.DEFAULT), account.toString());
        var parent = new OutfitConfig.Instance(UUID.randomUUID(), resource, 1, true, anchor,
                translation(16, 0, 0), List.of(), Map.of(), Map.of());
        var avatars = new LinkedHashMap<UUID, Avatar>();
        var indexes = new SkeletonIndex.Cache();
        try {
            Avatar parentAvatar = mountAvatar(parent, account, outfit, permissions);
            avatars.put(parent.instanceId(), parentAvatar);
            var skeleton = new FiguraSkeletonAdapter(
                    (top.aihmc.omnimorph.model.rendering.ImmediateFiguraRenderer) parentAvatar.renderer,
                    resource, parent.instanceId(), indexes, limits).binding().definition();
            var export = skeleton.reference(null, skeleton.uniqueNamed("Cosmetic").orElseThrow());
            var parentBone = new OutfitConfig.BoneRef(parent.instanceId(), export.path(), export.skeletonHash());
            var child = new OutfitConfig.Instance(UUID.randomUUID(), resource, 2, true,
                    new OutfitConfig.AnchorRef(OutfitConfig.AnchorType.ACCESSORY_BONE, null, parentBone, null, null, null),
                    translation(0, 4, 0), List.of(), Map.of(), Map.of());
            var virtual = new OutfitConfig.VirtualAnchor(UUID.randomUUID(), "child socket",
                    new OutfitConfig.BoneRef(child.instanceId(), export.path(), export.skeletonHash()), translation(0, 0, 8));
            var grandchild = new OutfitConfig.Instance(UUID.randomUUID(), resource, 3, true,
                    new OutfitConfig.AnchorRef(OutfitConfig.AnchorType.VIRTUAL, null, null, virtual.id(), null, null),
                    OutfitConfig.Transform.identity(), List.of(), Map.of(), Map.of());
            var privateRoot = new OutfitConfig.Instance(UUID.randomUUID(), resource, 4, true,
                    new OutfitConfig.AnchorRef(OutfitConfig.AnchorType.ACCESSORY_BONE, null,
                            skeleton.reference(parent.instanceId(), skeleton.roots().getFirst()), null, null, null),
                    OutfitConfig.Transform.identity(), List.of(), Map.of(), Map.of());
            for (var declaration : List.of(child, grandchild, privateRoot)) {
                avatars.put(declaration.instanceId(), mountAvatar(declaration, account, outfit, permissions));
            }
            var declaredExport = staleDeclaration
                    ? new OutfitConfig.BoneRef(null, export.path(), "1".repeat(64)) : export;
            var json = new com.google.gson.JsonObject();
            json.addProperty("has_bones", true);
            json.addProperty("display_bones", true);
            json.addProperty("skeleton_hash", declaredExport.skeletonHash());
            var exports = new com.google.gson.JsonArray();
            exports.add(new com.google.gson.GsonBuilder()
                    .setFieldNamingPolicy(com.google.gson.FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES)
                    .create().toJsonTree(declaredExport));
            json.add("exported_bones", exports);
            for (Avatar avatar : avatars.values()) {
                var metadata = new CompoundTag();
                metadata.putString("cosmetic_json", json.toString());
                avatar.nbt.put("metadata", metadata);
            }
            parentAvatar.initializePreviewPlayer(null);
            var part = parentAvatar.renderer.root.children.getFirst();
            part.preRender = parentAvatar.loadScript("exported native pivot before draw", """
                    return function(_, _, part) part:setPos(8, 0, 0) end
                    """).call().checkfunction();
            part.postRender = parentAvatar.loadScript("exported native pivot after draw", """
                    return function(_, _, part) part:setPos(64, 0, 0) end
                    """).call().checkfunction();
            // Deliberately reverse the chain: readiness, not display order, must determine native submission.
            var config = new OutfitConfig(1, 0, null, List.of(grandchild, privateRoot, child, parent),
                    List.of(virtual), Map.of(resource.identity(), 4), false);
            try (var composition = new OutfitComposition(config, limits, null, avatars, indexes, ignored -> authorized);
                 var frame = composition.retain()) {
                var positions = new ArrayList<float[]>();
                var sink = vertexSink(positions);
                var state = new net.minecraft.client.renderer.entity.state.AvatarRenderState();
                state.lightCoords = 0x00f000f0;
                var outer = new Matrix4f().translation(7, 11, 13);
                OutfitNativeRenderer.drawVanilla(frame, model, state, .5f, outer, outer,
                        type -> sink, EntityRenderMode.FIGURA_GUI);
                int count = authorized && !staleDeclaration ? 12 : 4;
                require(positions.size() == count, "only authorized, matching exported paths can submit dependants");
                near(positions.getFirst(), 7 + 25f / 16, 11 + 2f / 16, 13 + 3f / 16);
                if (count == 12) {
                    near(positions.get(4), 7 + 25f / 16, 11 + 6f / 16, 13 + 3f / 16);
                    near(positions.get(8), 7 + 25f / 16, 11 + 6f / 16, 13 + 11f / 16);
                } else {
                    require(avatars.get(child.instanceId()).luaRuntime == null
                                    && avatars.get(grandchild.instanceId()).luaRuntime == null,
                            "rejected dependencies do not initialize author effects");
                }
                require(avatars.get(privateRoot.instanceId()).luaRuntime == null,
                        "a non-exported internal root cannot be used as an accessory socket");
                require(part.customization.getPos().x == 64,
                        "post-render edits persist without changing the captured draw-time pivot");
                require(indexes.parseCount() == 2, "dependency waves reuse native skeleton definitions");
                positions.clear();
                part.customization.setPos(999, 0, 0);
                OutfitNativeRenderer.drawVanilla(frame, model, state, .5f, outer, outer,
                        type -> sink, EntityRenderMode.FIGURA_GUI);
                require(positions.size() == count, "replaying a retained dependency frame keeps its command list");
                if (count == 12) {
                    near(positions.get(4), 7 + 25f / 16, 11 + 6f / 16, 13 + 3f / 16);
                }
                require(part.customization.getPos().x == 999, "old dependency rendering restores later live state");
            }
        } finally {
            avatars.values().forEach(Avatar::clean);
        }
        System.out.println("PASS native accessory sockets: reversed dependency chain, shared virtual parent, draw callbacks, private paths and policy/hash rejection; authorized="
                + authorized + ", stale=" + staleDeclaration);
    }

    private static Avatar mountAvatar(OutfitConfig.Instance declaration, UUID account, UUID outfit,
                                      PermissionPack.PlayerPermissionPack permissions) {
        var context = new CosmeticInstanceContext(account, outfit, account, declaration,
                OutfitLimits.defaults(), CosmeticInstanceContext.Scope.DRAFT);
        Avatar avatar = Avatar.createCosmetic(context, permissions, "native accessory socket");
        avatar.load(new CompoundTag());
        addQuad(avatar);
        return avatar;
    }

    private static void testYsmAccessorySocket() throws Exception {
        var limits = OutfitLimits.defaults();
        var model = new PlayerModel(LayerDefinition.create(PlayerModel.createMesh(CubeDeformation.NONE, false),
                64, 64).bakeRoot(), false);
        var base = new VanillaSkeletonAdapter(model, limits);
        var baseBone = new OutfitConfig.AnchorRef(OutfitConfig.AnchorType.BASE_BONE, null,
                base.binding().definition().reference(null, 0), null, null, null);
        UUID account = UUID.randomUUID();
        UUID outfit = UUID.randomUUID();
        var permissions = new PermissionPack.PlayerPermissionPack(
                new PermissionPack.CategoryPermissionPack(Permissions.Category.DEFAULT), account.toString());
        var parentResource = new OutfitConfig.ResourceRef(OutfitConfig.Source.LOCAL, UUID.randomUUID(), null, null,
                null, "2".repeat(64), OutfitConfig.Format.YSM_PLAIN);
        var childResource = new OutfitConfig.ResourceRef(OutfitConfig.Source.LOCAL, UUID.randomUUID(), null, null,
                null, "3".repeat(64), OutfitConfig.Format.FIGURA);
        var parent = new OutfitConfig.Instance(UUID.randomUUID(), parentResource, 1, true, baseBone,
                translation(16, 0, 0), List.of(), Map.of(), Map.of());
        var context = new CosmeticInstanceContext(account, outfit, account, parent, limits, CosmeticInstanceContext.Scope.DRAFT);
        Avatar parentAvatar = Avatar.createCosmetic(context, permissions, "native YSM exported socket");
        var avatars = new LinkedHashMap<UUID, Avatar>();
        avatars.put(parent.instanceId(), parentAvatar);
        try (var textures = top.aihmc.omnimorph.model.ysm.YsmMaterialNativeSmokeTest.recordingTextureDevice()) {
            var nbt = ysmCube();
            nbt.getCompoundOrEmpty("ysm").putByteArray("main_model", """
                    {"minecraft:geometry":[{"description":{"identifier":"geometry.exported_socket",
                    "texture_width":16,"texture_height":16},"bones":[
                    {"name":"Root","pivot":[2,3,5],"cubes":[{"origin":[1,2,3],"size":[1,2,3],"uv":[0,0]}]},
                    {"name":"tailtip","parent":"Root","pivot":[7,11,13]}]}]}
                    """.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            parentAvatar.load(nbt);
            parentAvatar.initializePreviewPlayer(null);
            var root = parentAvatar.getYsmRuntime().getPart("Root");
            root.setPos(4d, 6d, 8d).setRot(0d, 0d, 90d).setScale(2d, 3d, 4d);
            var indexes = new SkeletonIndex.Cache();
            var skeleton = new YsmSkeletonAdapter(parentAvatar.getYsmRuntime(), parentResource, parent.instanceId(),
                    indexes, limits).binding().definition();
            var exported = skeleton.reference(null, skeleton.uniqueNamed("tailtip").orElseThrow());
            var json = new com.google.gson.JsonObject();
            json.addProperty("has_bones", true);
            json.addProperty("skeleton_hash", skeleton.hash());
            var exports = new com.google.gson.JsonArray();
            exports.add(new com.google.gson.GsonBuilder()
                    .setFieldNamingPolicy(com.google.gson.FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES)
                    .create().toJsonTree(exported));
            json.add("exported_bones", exports);
            parentAvatar.nbt.getCompoundOrEmpty("metadata").putString("cosmetic_json", json.toString());
            var childBone = new OutfitConfig.AnchorRef(OutfitConfig.AnchorType.ACCESSORY_BONE, null,
                    new OutfitConfig.BoneRef(parent.instanceId(), exported.path(), exported.skeletonHash()), null, null, null);
            var child = new OutfitConfig.Instance(UUID.randomUUID(), childResource, 1, true, childBone,
                    OutfitConfig.Transform.identity(), List.of(), Map.of(), Map.of());
            avatars.put(child.instanceId(), mountAvatar(child, account, outfit, permissions));
            var config = new OutfitConfig(1, 0, null, List.of(child, parent), List.of(),
                    Map.of(parentResource.identity(), 1, childResource.identity(), 1), false);
            try (var composition = new OutfitComposition(config, limits, null, avatars, indexes)) {
                var state = new net.minecraft.client.renderer.entity.state.AvatarRenderState();
                state.lightCoords = 0x00f000f0;
                var outer = new Matrix4f().translation(7, 11, 13);
                var positions = new ArrayList<float[]>();
                var sink = vertexSink(positions);
                try (var frame = composition.retain()) {
                    OutfitNativeRenderer.drawVanilla(frame, model, state, .5f, outer, outer,
                            type -> sink, EntityRenderMode.FIGURA_GUI);
                    require(positions.size() == 28, "a native YSM cube exports its empty animated child to Figura");
                    // Native mirrored X, parent Rz90/S(2,3,4), native 15/16 model scale, then parent T16.
                    near(positions.get(24), 7 - 17.75f / 16, 11 + .9375f / 16, 13 + 53.4375f / 16);
                    positions.clear();
                    root.setPos(999d, 0d, 0d);
                    OutfitNativeRenderer.drawVanilla(frame, model, state, .5f, outer, outer,
                            type -> sink, EntityRenderMode.FIGURA_GUI);
                    require(positions.size() == 28, "retained YSM socket command remains drawable after live changes");
                    near(positions.get(24), 7 - 17.75f / 16, 11 + .9375f / 16, 13 + 53.4375f / 16);
                }
                try (var failedFrame = composition.retain()) {
                    try {
                        failedFrame.draw(base.capture(new Matrix4f()), state, .5f, ignored -> {
                            throw new IllegalStateException("native socket fixture failure");
                        });
                        throw new AssertionError("native failure must reach the caller");
                    } catch (IllegalStateException failure) {
                        require("native socket fixture failure".equals(failure.getMessage()), "native failure remains unchanged");
                    }
                    var frozen = new ArrayList<OutfitComposition.Draw>();
                    failedFrame.draw(base.capture(new Matrix4f()), state, .5f, frozen::add);
                    require(frozen.size() == 1 && frozen.getFirst().instanceId().equals(parent.instanceId()),
                            "a first-draw failure freezes prepared parent commands without duplicate emit or child initialization");
                }
                require(indexes.parseCount() == 3, "native YSM socket frames do not reparse their definitions");
            }
        } finally {
            avatars.values().forEach(Avatar::clean);
        }
        System.out.println("PASS native YSM exported empty pivot: signed axes, parent animation, nonuniform scale, mixed Figura child and retained failure replay; GUI/GPU MANUAL/PENDING");
    }

    private static OutfitConfig.Transform translation(double x, double y, double z) {
        return new OutfitConfig.Transform(new OutfitConfig.Vector(x, y, z),
                new OutfitConfig.Vector(0, 0, 0), new OutfitConfig.Vector(1, 1, 1));
    }

    private static void testYsmBaseBudget(boolean accelerated) throws Exception {
        var service = top.aihmc.omnimorph.render.acceleration.AccelerationService.INSTANCE;
        var backendSetter = service.getClass().getDeclaredMethod("useBackend",
                top.aihmc.omnimorph.render.acceleration.AccelerationBackend.class);
        backendSetter.setAccessible(true);
        backendSetter.invoke(service, accelerated
                ? new top.aihmc.omnimorph.render.acceleration.JavaAccelerationBackend() : null);
        Avatar base = Avatar.createLibraryPreview(UUID.randomUUID(), "YSM composed budget base");
        var avatars = new LinkedHashMap<UUID, Avatar>();
        try (var textures = top.aihmc.omnimorph.model.ysm.YsmMaterialNativeSmokeTest.recordingTextureDevice()) {
            require(service.accelerated() == accelerated, "fixture selects the actual mesh preparation branch");
            base.permissions.insert(Permissions.COMPLEXITY, 8, org.figuramc.figura.FiguraMod.MOD_ID);
            base.load(ysmCube());
            base.initializePreviewPlayer(null);
            base.renderMode = EntityRenderMode.FIGURA_GUI;
            require(base.getYsmRuntime() != null, "native YSM base assembles");
            var limits = OutfitLimits.defaults();
            var indexes = new SkeletonIndex.Cache();
            var baseResource = new OutfitConfig.ResourceRef(OutfitConfig.Source.LOCAL, UUID.randomUUID(), null,
                    null, null, "4".repeat(64), OutfitConfig.Format.YSM_ENCRYPTED);
            var skeleton = new YsmSkeletonAdapter(base.getYsmRuntime(), baseResource, null, indexes, limits)
                    .binding().definition();
            var anchor = new OutfitConfig.AnchorRef(OutfitConfig.AnchorType.BASE_BONE, null,
                    skeleton.reference(null, skeleton.uniqueNamed("Body").orElseThrow()), null, null, null);
            var declarations = new ArrayList<OutfitConfig.Instance>();
            var watermarks = new LinkedHashMap<String, Integer>();
            for (var format : List.of(OutfitConfig.Format.FIGURA, OutfitConfig.Format.YSM_ENCRYPTED)) {
                var resource = new OutfitConfig.ResourceRef(OutfitConfig.Source.LOCAL, UUID.randomUUID(), null,
                        null, null, "3".repeat(64), format);
                var declaration = new OutfitConfig.Instance(UUID.randomUUID(), resource, 1, true, anchor,
                        OutfitConfig.Transform.identity(), List.of(), Map.of(), Map.of());
                declarations.add(declaration);
                watermarks.put(resource.identity(), 1);
                var context = new CosmeticInstanceContext(base.owner, UUID.randomUUID(), base.owner,
                        declaration, limits, CosmeticInstanceContext.Scope.DRAFT);
                Avatar avatar = Avatar.createCosmetic(context, base.permissions, "mixed native budget accessory");
                avatars.put(declaration.instanceId(), avatar);
                avatar.load(format == OutfitConfig.Format.FIGURA ? new CompoundTag() : ysmCube());
                if (format == OutfitConfig.Format.FIGURA) {
                    addQuad(avatar);
                }
            }
            var configuration = new OutfitConfig(1, 0, baseResource, declarations, List.of(), watermarks, false);
            try (var composition = new OutfitComposition(configuration, limits, null, avatars, indexes);
                 var frame = composition.retain()) {
                var state = new net.minecraft.client.renderer.entity.state.AvatarRenderState();
                state.lightCoords = 0x00f000f0;
                var positions = new ArrayList<float[]>();
                var sink = vertexSink(positions);
                var runtime = base.getYsmRuntime().forRenderPass();
                require(runtime != null && runtime.owner() != base && runtime.renderBudgetOwner() == base,
                        "production YSM GUI pass uses detached playback but retains original budget ownership");
                require(runtime.owner().getMolangContext().executionBudget() == base.getMolangContext().executionBudget(),
                        "detached YSM GUI expressions share the original owner's execution allowance");
                var submitted = runtime.captureRenderPose(false);
                var pose = frame.captureYsmBase(runtime, new Matrix4f(), submitted);
                top.aihmc.omnimorph.model.ysm.animation.YsmRenderFrameClock.beginFrame();
                frame.drawYsmBase(base, () -> runtime.renderer().renderWithAttachments(
                        new com.mojang.blaze3d.vertex.PoseStack(), type -> sink, state.lightCoords, submitted));
                require(positions.size() == 24, "YSM base emits its six native cube faces");
                OutfitNativeRenderer.draw(frame, pose, state, .5f, new Matrix4f(), type -> sink,
                        EntityRenderMode.FIGURA_GUI);
                require(positions.size() == 32, "base plus Figura and partial YSM consume exactly eight faces");
                near(positions.get(24), .05859375f, .1171875f, .17578125f);
                float ysmUnit = .9375f * .9375f / 16;
                for (float[] vertex : positions.subList(28, 32)) {
                    require(vertex[0] >= -2 * ysmUnit - .0001f && vertex[0] <= -ysmUnit + .0001f
                                    && vertex[1] >= 2 * ysmUnit - .0001f && vertex[1] <= 4 * ysmUnit + .0001f
                                    && vertex[2] >= 3 * ysmUnit - .0001f && vertex[2] <= 6 * ysmUnit + .0001f,
                            "partial YSM face retains source axes, model units and both native model scales");
                }
                positions.clear();
                OutfitNativeRenderer.draw(frame, pose, state, .5f, new Matrix4f(), type -> sink,
                        EntityRenderMode.FIGURA_GUI);
                require(positions.isEmpty(), "mixed native replay cannot refill the consumed frame");
                require(indexes.parseCount() == 3, "mixed native drawing retains one index per exact resource");
            }
        } finally {
            avatars.values().forEach(Avatar::clean);
            base.clean();
            backendSetter.invoke(service, new Object[]{null});
        }
        System.out.println("PASS composed YSM base/Figura/YSM face allowance, accelerated preparation="
                + accelerated + "; CPU vertices only, GUI/GPU MANUAL/PENDING");
    }

    static CompoundTag ysmCube() {
        var result = new CompoundTag();
        var metadata = new CompoundTag();
        metadata.putString("format", "ysm-native");
        result.put("metadata", metadata);
        var data = new CompoundTag();
        data.putFloat("width_scale", 1);
        data.putFloat("height_scale", 1);
        data.putByteArray("main_model", """
                {"minecraft:geometry":[{"description":{"identifier":"geometry.outfit_budget",
                "texture_width":16,"texture_height":16},"bones":[{"name":"Body","pivot":[0,0,0],
                "cubes":[{"origin":[1,2,3],"size":[1,2,3],"uv":[0,0]}]}]}]}
                """.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        result.put("ysm", data);
        return result;
    }

    private static void testFiguraBaseFrames() throws Exception {
        var limits = OutfitLimits.defaults();
        var baseResource = new OutfitConfig.ResourceRef(OutfitConfig.Source.LOCAL, UUID.randomUUID(), null,
                null, null, "6".repeat(64), OutfitConfig.Format.FIGURA);
        var accessoryResource = new OutfitConfig.ResourceRef(OutfitConfig.Source.LOCAL, UUID.randomUUID(), null,
                null, null, "5".repeat(64), OutfitConfig.Format.FIGURA);
        Avatar base = Avatar.createLibraryPreview(UUID.randomUUID(), "retained native base");
        Avatar accessory = null;
        try {
            base.load(new CompoundTag());
            base.initializePreviewPlayer(null);
            base.renderMode = EntityRenderMode.FIGURA_GUI;
            addQuad(base);
            var indexes = new SkeletonIndex.Cache();
            var definition = new FiguraSkeletonAdapter(
                    (top.aihmc.omnimorph.model.rendering.ImmediateFiguraRenderer) base.renderer,
                    baseResource, null, indexes, limits).binding().definition();
            var anchor = new OutfitConfig.AnchorRef(OutfitConfig.AnchorType.BASE_BONE, null,
                    definition.reference(null, definition.uniqueNamed("Cosmetic").orElseThrow()), null, null, null);
            var instance = new OutfitConfig.Instance(UUID.randomUUID(), accessoryResource, 1, true, anchor,
                    OutfitConfig.Transform.identity(), List.of(), Map.of(), Map.of());
            var context = new CosmeticInstanceContext(base.owner, UUID.randomUUID(), base.owner, instance,
                    limits, CosmeticInstanceContext.Scope.DRAFT);
            accessory = Avatar.createCosmetic(context, base.permissions, "retained native accessory");
            accessory.load(new CompoundTag());
            addQuad(accessory);
            var config = new OutfitConfig(1, 0, baseResource, List.of(instance), List.of(),
                    Map.of(accessoryResource.identity(), 1), false);
            var beforeDraw = base.loadScript("native base before drawing", """
                    return function()
                        local part = models.Cosmetic
                        part:setPos(part:getPos().x + 4, 0, 0)
                    end
                    """).call().checkfunction();
            var afterDraw = base.loadScript("native base after drawing", """
                    return function()
                        local part = models.Cosmetic
                        part:setPos(part:getPos().x + 4, 0, 0)
                    end
                    """).call().checkfunction();
            var positions = new ArrayList<float[]>();
            VertexConsumer sink = vertexSink(positions);
            var state = new net.minecraft.client.renderer.entity.state.AvatarRenderState();
            state.lightCoords = 0x00f000f0;
            try (var composition = new OutfitComposition(config, limits, null,
                    Map.of(instance.instanceId(), accessory), indexes);
                 var oldFrame = composition.retain(); var newFrame = composition.retain()) {
                var part = base.renderer.root.children.getFirst();
                part.customization.setPos(16, 0, 0);
                oldFrame.prepareFiguraBase(base);
                part.customization.setPos(48, 0, 0);
                newFrame.prepareFiguraBase(base);
                for (var frame : List.of(newFrame, oldFrame, newFrame)) {
                    positions.clear();
                    base.render.reset(65536);
                    frame.drawFiguraBase(base, () -> {
                        base.run(beforeDraw, base.render);
                        var pose = frame.captureFiguraBase(base, new Matrix4f(), () -> base.render(
                                null, 0, .5f, 1, new com.mojang.blaze3d.vertex.PoseStack(), type -> sink,
                                state.lightCoords, net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY,
                                null, org.figuramc.figura.model.rendering.PartFilterScheme.MODEL, false, false));
                        OutfitNativeRenderer.draw(frame, pose, state, .5f, new Matrix4f(), type -> sink,
                                EntityRenderMode.FIGURA_GUI);
                        base.run(afterDraw, base.render);
                    });
                    require(positions.size() == 8 && !base.scriptError,
                            "retained base and its attached native accessory both emit a quad");
                    float expectedX = frame == oldFrame ? -1.3125f : -3.3125f;
                    near(positions.get(0), expectedX, 1.375f, .1875f);
                    near(positions.get(4), expectedX, 1.375f, .1875f);
                    require(part.customization.getPos().x == 56,
                            "old base callbacks do not overwrite the newer callback result");
                }
                require(indexes.parseCount() == 2, "base and accessory snapshots keep shared structural indexes");
                var mountedOwner = accessory.accelerationOwner();
                composition.close();
                newFrame.close();
                oldFrame.drawFiguraBase(base, () -> {
                    oldFrame.close();
                    require(mountedOwner.isCurrent(), "closing the base frame waits for its whole callback scope");
                });
                require(!mountedOwner.isCurrent() && base.accelerationOwner().isCurrent(),
                        "last base callback releases owned accessories without destroying the borrowed base");
            }
        } finally {
            base.clean();
            if (accessory != null) {
                accessory.clean();
            }
        }
        System.out.println("PASS retained Figura base: full Lua/native callback scope, matching accessory vertices and interleaved-frame restoration; GUI/GPU MANUAL/PENDING");
    }

    static VertexConsumer vertexSink(List<float[]> positions) {
        return vertexSink(positions, null);
    }

    static VertexConsumer vertexSink(List<float[]> positions, List<float[]> normals) {
        return (VertexConsumer) Proxy.newProxyInstance(VertexConsumer.class.getClassLoader(),
                new Class<?>[]{VertexConsumer.class}, (proxy, method, arguments) -> {
                    if (method.isDefault()) {
                        return InvocationHandler.invokeDefault(proxy, method, arguments);
                    }
                    if (method.getName().equals("addVertex")) {
                        positions.add(new float[]{((Number) arguments[0]).floatValue(),
                                ((Number) arguments[1]).floatValue(), ((Number) arguments[2]).floatValue()});
                    }
                    if (normals != null && method.getName().equals("setNormal")) {
                        normals.add(new float[]{((Number) arguments[0]).floatValue(),
                                ((Number) arguments[1]).floatValue(), ((Number) arguments[2]).floatValue()});
                    }
                    return method.getReturnType() == void.class ? null : proxy;
                });
    }

    private static void testVanillaArms() throws Exception {
        var limits = OutfitLimits.defaults();
        var model = new PlayerModel(LayerDefinition.create(PlayerModel.createMesh(CubeDeformation.NONE, true),
                64, 64).bakeRoot(), true);
        model.leftArm.setPos(6, 3, -2);
        model.rightArm.setPos(-5, 2, 4);
        var skeleton = new VanillaSkeletonAdapter(model, limits).binding().definition();
        var resource = new OutfitConfig.ResourceRef(OutfitConfig.Source.LOCAL, UUID.randomUUID(), null, null,
                null, "8".repeat(64), OutfitConfig.Format.FIGURA);
        var declarations = new ArrayList<OutfitConfig.Instance>();
        var avatars = new LinkedHashMap<UUID, Avatar>();
        UUID account = UUID.randomUUID();
        UUID outfit = UUID.randomUUID();
        var permissions = new PermissionPack.PlayerPermissionPack(
                new PermissionPack.CategoryPermissionPack(Permissions.Category.DEFAULT), account.toString());
        try {
            for (String name : List.of("LeftArm", "RightArm", "Body")) {
                var anchor = new OutfitConfig.AnchorRef(OutfitConfig.AnchorType.BASE_BONE, null,
                        skeleton.reference(null, skeleton.uniqueNamed(name).orElseThrow()), null, null, null);
                var declaration = new OutfitConfig.Instance(UUID.randomUUID(), resource, declarations.size() + 1,
                        true, anchor, OutfitConfig.Transform.identity(), List.of(), Map.of(), Map.of());
                declarations.add(declaration);
                var context = new CosmeticInstanceContext(account, outfit, account, declaration, limits,
                        CosmeticInstanceContext.Scope.DRAFT);
                Avatar avatar = Avatar.createCosmetic(context, permissions, "native mounted arm quad");
                avatars.put(declaration.instanceId(), avatar);
                avatar.load(new CompoundTag());
                addQuad(avatar);
            }
            var config = new OutfitConfig(1, 0, null, declarations, List.of(),
                    Map.of(resource.identity(), 3), false);
            try (var composition = new OutfitComposition(config, limits, null, avatars, new SkeletonIndex.Cache())) {
                var positions = new ArrayList<float[]>();
                VertexConsumer sink = (VertexConsumer) Proxy.newProxyInstance(VertexConsumer.class.getClassLoader(),
                        new Class<?>[]{VertexConsumer.class}, (proxy, method, arguments) -> {
                            if (method.isDefault()) {
                                return InvocationHandler.invokeDefault(proxy, method, arguments);
                            }
                            if (method.getName().equals("addVertex")) {
                                positions.add(new float[]{((Number) arguments[0]).floatValue(),
                                        ((Number) arguments[1]).floatValue(), ((Number) arguments[2]).floatValue()});
                            }
                            return method.getReturnType() == void.class ? null : proxy;
                        });
                var state = new net.minecraft.client.renderer.entity.state.AvatarRenderState();
                state.lightCoords = 0x00f000f0;
                var outer = new Matrix4f().translation(7, 11, 13);
                try (var left = composition.retain(); var right = composition.retain()) {
                    OutfitNativeRenderer.drawVanillaArm(left, model, state, .5f, outer, type -> sink, true);
                    require(positions.size() == 4, "left pass excludes right and body attachments");
                    near(positions.getFirst(), 7.4375f, 11.3125f, 13.0625f);
                    positions.clear();
                    OutfitNativeRenderer.drawVanillaArm(right, model, state, .5f, outer, type -> sink, false);
                    require(positions.size() == 4, "right pass retains independent commands and excludes left/body");
                    near(positions.getFirst(), 6.75f, 11.25f, 13.4375f);
                }
            }
        } finally {
            avatars.values().forEach(Avatar::clean);
        }
        System.out.println("PASS native vanilla arm composition: separate left/right frames, slim pivots and no body leakage; real first-person GUI/GPU MANUAL/PENDING");
    }

    static void addQuad(Avatar avatar) {
        var vertices = List.of(new Vertex(1, 2, 3, 0, 0, 0, 0, 1),
                new Vertex(2, 2, 3, 16, 0, 0, 0, 1), new Vertex(2, 3, 3, 16, 16, 0, 0, 1),
                new Vertex(1, 3, 3, 0, 16, 0, 0, 1));
        var part = new FiguraModelPart(avatar, "Cosmetic", new PartCustomization(), Map.of(0, vertices),
                new ArrayList<>());
        part.facesByTexture = List.of(1);
        part.textures = List.of(new FiguraTextureSet("fixture", null, null, null, null) {
            @Override
            public int getWidth() {
                return 16;
            }

            @Override
            public int getHeight() {
                return 16;
            }

            @Override
            public Identifier getOverrideTexture(UUID owner, TextureCustomization customization) {
                return customization.getOverrideType() == OverrideType.PRIMARY
                        ? Identifier.parse("omni-morph:mounted-fixture") : null;
            }
        });
        part.parent = avatar.renderer.root;
        avatar.renderer.root.children.add(part);
    }

    static void near(float[] value, float x, float y, float z) {
        require(Math.abs(value[0] - x) < .0001f && Math.abs(value[1] - y) < .0001f
                && Math.abs(value[2] - z) < .0001f,
                "native emitted position: " + java.util.Arrays.toString(value));
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
