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

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static top.aihmc.omnimorph.model.outfit.OutfitNativeRenderSmokeTest.addQuad;
import static top.aihmc.omnimorph.model.outfit.OutfitNativeRenderSmokeTest.near;
import static top.aihmc.omnimorph.model.outfit.OutfitNativeRenderSmokeTest.vertexSink;

/** Actual trusted Lua selection and native accessory vertices; custom base fallback fixtures are CPU matrices. */
public final class OutfitAutomaticAnchorSmokeTest {
    private enum Case { DEFAULT, PREFERRED, DISABLED, INVALID, AMBIGUOUS, MANUAL_INVALID, CYCLE }
    private record Node(String name, float position, List<Node> children) {}

    public static void run() throws Exception {
        for (var value : Case.values()) {
            nativeTails(value);
        }
        var body = new Node("Body", 4, List.of());
        var hip = new Node("hip", 7, List.of());
        var tip = new Node("tailtip", 10, List.of());
        fallback(List.of(new Node("Root", 1, List.of(body, hip, tip))), 10);
        fallback(List.of(new Node("Root", 1, List.of(body, hip))), 7);
        fallback(List.of(new Node("Root", 1, List.of(body))), 4);
        fallback(List.of(new Node("Root", 1, List.of())), 1);
        fallback(List.of(new Node("First", 2, List.of()), new Node("Second", 3, List.of())), 0);
        fallback(List.of(new Node("Root", 1, List.of(hip, tip, new Node("tailtip", 20, List.of())))), 7);
        System.out.println("PASS automatic base fallback: tailtip/hip/Body/native root/entity root and ambiguous-name rejection; CPU matrices only");
    }

    private static void nativeTails(Case variant) throws Exception {
        UUID account = UUID.randomUUID();
        UUID outfit = UUID.randomUUID();
        UUID firstId = UUID.fromString("00000000-0000-0000-0000-000000000001");
        UUID secondId = UUID.fromString("00000000-0000-0000-0000-000000000002");
        var indexes = new SkeletonIndex.Cache();
        var limits = OutfitLimits.defaults();
        var model = new PlayerModel(LayerDefinition.create(PlayerModel.createMesh(CubeDeformation.NONE, false),
                64, 64).bakeRoot(), false);
        var base = new VanillaSkeletonAdapter(model, limits);
        var anchor = new OutfitConfig.AnchorRef(OutfitConfig.AnchorType.BASE_BONE, null,
                base.binding().definition().reference(null, 0), null, null, null);
        var tailResource = resource();
        var trailResource = resource();
        var first = instance(firstId, tailResource, 1, variant != Case.DISABLED,
                variant == Case.INVALID ? OutfitConfig.AnchorRef.authorDefault("missing") : anchor, 16);
        var second = instance(secondId, tailResource, 2, variant != Case.CYCLE, anchor, 32);
        var automatic = new OutfitConfig.AnchorRef(OutfitConfig.AnchorType.AUTO_TRAIL, null, null, null,
                variant == Case.PREFERRED ? secondId : null, null);
        var trail = instance(UUID.randomUUID(), trailResource, 1, true, automatic, 0);
        var avatars = new LinkedHashMap<UUID, Avatar>();
        try {
            for (var declaration : List.of(first, second, trail)) {
                avatars.put(declaration.instanceId(), load(account, outfit, declaration));
            }
            for (var declaration : List.of(first, second)) {
                Avatar avatar = avatars.get(declaration.instanceId());
                var parent = avatar.renderer.root.children.getFirst();
                parent.customization.setPos(4, 0, 0);
                emptyTip(avatar, parent, 7);
                if (variant == Case.AMBIGUOUS) {
                    emptyTip(avatar, parent, 9);
                }
            }
            var tailIndex = new FiguraSkeletonAdapter((ImmediateFiguraRenderer) avatars.get(firstId).renderer,
                    tailResource, firstId, indexes, limits).binding().definition();
            var json = new JsonObject();
            json.addProperty("category", "Tail");
            json.addProperty("has_bones", true);
            json.addProperty("skeleton_hash", tailIndex.hash());
            var exports = new JsonArray();
            var serializer = new GsonBuilder().setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES).create();
            for (int index = 0; index < tailIndex.nodes().size(); index++) {
                if (tailIndex.nodes().get(index).name().equals("tailtip")) {
                    exports.add(serializer.toJsonTree(tailIndex.reference(null, index)));
                }
            }
            json.add("exported_bones", exports);
            for (UUID id : List.of(firstId, secondId)) {
                var metadata = new CompoundTag();
                metadata.putString("cosmetic_json", json.toString());
                avatars.get(id).nbt.put("metadata", metadata);
            }
            if (variant == Case.MANUAL_INVALID) {
                var stale = new OutfitConfig.BoneRef(firstId, tailIndex.nodes().getLast().path(), "1".repeat(64));
                trail = instance(trail.instanceId(), trailResource, 1, true,
                        new OutfitConfig.AnchorRef(OutfitConfig.AnchorType.ACCESSORY_BONE, null, stale, null, null, null), 0);
            } else if (variant == Case.CYCLE) {
                var trailIndex = new FiguraSkeletonAdapter((ImmediateFiguraRenderer) avatars.get(trail.instanceId()).renderer,
                        trailResource, trail.instanceId(), indexes, limits).binding().definition();
                first = instance(firstId, tailResource, 1, true,
                        new OutfitConfig.AnchorRef(OutfitConfig.AnchorType.ACCESSORY_BONE, null,
                                trailIndex.reference(trail.instanceId(), trailIndex.uniqueNamed("Cosmetic").orElseThrow()),
                                null, null, null), 16);
            }
            var config = new OutfitConfig(1, 0, null, List.of(trail, second, first), List.of(),
                    Map.of(tailResource.identity(), 2, trailResource.identity(), 1), false);
            try (var composition = new OutfitComposition(config, limits, null, avatars, indexes);
                 var frame = composition.retain()) {
                composition.editorSnapshot(null);
                frame.capturePreviewSelection(new top.aihmc.omnimorph.model.library.PreviewBoneHighlight.Mount(trail.instanceId()));
                var state = new net.minecraft.client.renderer.entity.state.AvatarRenderState();
                state.lightCoords = 0x00f000f0;
                var outer = new Matrix4f().translation(7, 11, 13);
                var positions = new ArrayList<float[]>();
                var sink = vertexSink(positions);
                OutfitNativeRenderer.drawVanilla(frame, model, state, .5f, outer, outer, type -> sink,
                        EntityRenderMode.FIGURA_GUI);
                var commands = new ArrayList<OutfitComposition.Draw>();
                frame.draw(base.capture(new Matrix4f()), state, .5f, commands::add);
                UUID trailId = trail.instanceId();
                int trailDraw = -1;
                for (int index = 0; index < commands.size(); index++) {
                    if (commands.get(index).instanceId().equals(trailId)) {
                        trailDraw = index;
                    }
                }
                if (variant == Case.MANUAL_INVALID) {
                    require(trailDraw < 0 && avatars.get(trailId).luaRuntime == null,
                            "manual stale binding does not fall back or initialize effects");
                    require(composition.previewMountTarget(trailId) == null && frame.previewHelpers(base.capture(new Matrix4f())).isEmpty(),
                            "manual invalid bindings are never reported as a successful automatic preview target");
                } else {
                    require(trailDraw >= 0, "an automatic anchor resolves after dependency waves");
                    boolean selectedFirst = variant == Case.DEFAULT;
                    boolean selectedSecond = variant == Case.PREFERRED || variant == Case.DISABLED;
                    UUID expected = selectedFirst ? firstId : selectedSecond ? secondId : null;
                    require(java.util.Objects.equals(composition.automaticTailSelections().get(trailId), expected),
                            "reported selected instance matches the stable automatic target: " + variant);
                    var target = composition.previewMountTarget(trailId);
                    require(target != null && target.bone() != null
                                    && java.util.Objects.equals(target.bone().instanceId(), expected),
                            "the GUI diagnostic retains the actual controller bone, including base fallback: " + variant);
                    var helpers = frame.previewHelpers(base.capture(new Matrix4f()));
                    var matrix = commands.get(trailDraw).matrix();
                    require(!helpers.isEmpty(), "captured automatic preview selection produces native target helpers");
                    near(new float[]{helpers.getFirst().x0(), helpers.getFirst().y0(), helpers.getFirst().z0()},
                            matrix.m30(), matrix.m31(), matrix.m32());
                    var point = positions.get(trailDraw * 4);
                    if (expected != null) {
                        near(point, 7 + (selectedFirst ? 28f : 44f) / 16, 11 + 13f / 16, 14);
                    } else {
                        near(point, 7 + 1f / 16, 11 + 14f / 16, 13 + 3f / 16);
                    }
                    if (variant == Case.DEFAULT) {
                        avatars.get(firstId).scriptError = true;
                        try (var next = composition.retain()) {
                            next.capturePreviewSelection(new top.aihmc.omnimorph.model.library.PreviewBoneHighlight.Mount(trailId));
                            positions.clear();
                            OutfitNativeRenderer.drawVanilla(next, model, state, .5f, outer, outer, type -> sink,
                                    EntityRenderMode.FIGURA_GUI);
                            require(!composition.automaticTailSelections().containsKey(trailId),
                                    "failed selected tail uses the base fallback rather than jumping to another tail");
                            var fallback = composition.previewMountTarget(trailId);
                            require(fallback != null && fallback.bone().instanceId() == null
                                            && !next.previewHelpers(base.capture(new Matrix4f())).equals(helpers),
                                    "failure switches GUI helper coordinates to the controller's actual base fallback");
                            OutfitNativeRenderer.drawVanilla(frame, model, state, .5f, outer, outer, type -> sink,
                                    EntityRenderMode.FIGURA_GUI);
                            require(composition.previewMountTarget(trailId).equals(fallback)
                                            && frame.previewHelpers(base.capture(new Matrix4f())).equals(helpers),
                                    "replaying an older frame preserves its helpers without overwriting the newest GUI target");
                        }
                    }
                }
                require(indexes.parseCount() == 3, "automatic selection does not rebuild definitions");
            }
        } finally {
            avatars.values().forEach(Avatar::clean);
        }
        System.out.println("PASS automatic native tail selection: " + variant + "; GUI/GPU MANUAL/PENDING");
    }

    private static void fallback(List<Node> roots, float expected) throws Exception {
        var binding = SkeletonIndex.capture(null, roots, Node::name, Node::children, OutfitLimits.defaults());
        var matrices = new IdentityHashMap<Node, Matrix4f>();
        for (var node : binding.parts()) {
            matrices.put(node, new Matrix4f().translation(node.position() / 16, 0, 0));
        }
        var pose = SkeletonPose.fromNative(binding, matrices, new Matrix4f(), SkeletonPose.NativeUnits.BLOCKS);
        var instance = instance(UUID.randomUUID(), resource(), 1, true,
                new OutfitConfig.AnchorRef(OutfitConfig.AnchorType.AUTO_TRAIL, null, null, null, null, null), 0);
        var account = UUID.randomUUID();
        Avatar avatar = load(account, UUID.randomUUID(), instance);
        try {
            var config = new OutfitConfig(1, 0, resource(), List.of(instance), List.of(),
                    Map.of(instance.resource().identity(), 1), false);
            try (var composition = new OutfitComposition(config, OutfitLimits.defaults(), null,
                    Map.of(instance.instanceId(), avatar), new SkeletonIndex.Cache()); var frame = composition.retain()) {
                frame.capturePreviewSelection(new top.aihmc.omnimorph.model.library.PreviewBoneHighlight.Mount(instance.instanceId()));
                var commands = new ArrayList<OutfitComposition.Draw>();
                frame.draw(pose, null, 0, commands::add);
                require(commands.size() == 1 && Math.abs(commands.getFirst().matrix().m30() - expected) < .0001f,
                        "Lua fallback chooses the expected base point: " + expected);
                var target = composition.previewMountTarget(instance.instanceId());
                var helpers = frame.previewHelpers(pose);
                require(target != null && helpers.size() >= 3 && Math.abs(helpers.getFirst().x0() - expected) < .0001f,
                        "automatic helper position comes from the same selected base/root target");
                require((target.bone() == null) == (roots.size() > 1),
                        "multi-root fallback is an explicit entity root, never a fabricated persistent bone path");
            }
        } finally {
            avatar.clean();
        }
    }

    private static void emptyTip(Avatar avatar, FiguraModelPart parent, int x) {
        var tip = new FiguraModelPart(avatar, "tailtip", new PartCustomization(), Map.of(), new ArrayList<>());
        tip.customization.setPivot(x, 11, 13);
        tip.parent = parent;
        tip.facesByTexture = List.of();
        tip.textures = List.of();
        parent.children.add(tip);
    }

    private static Avatar load(UUID account, UUID outfit, OutfitConfig.Instance instance) {
        var context = new CosmeticInstanceContext(account, outfit, account, instance,
                OutfitLimits.defaults(), CosmeticInstanceContext.Scope.DRAFT);
        var permissions = new PermissionPack.PlayerPermissionPack(
                new PermissionPack.CategoryPermissionPack(Permissions.Category.DEFAULT), account.toString());
        Avatar avatar = Avatar.createCosmetic(context, permissions, "automatic trail fixture");
        avatar.load(new CompoundTag());
        addQuad(avatar);
        return avatar;
    }

    private static OutfitConfig.ResourceRef resource() {
        return new OutfitConfig.ResourceRef(OutfitConfig.Source.LOCAL, UUID.randomUUID(), null, null,
                null, "8".repeat(64), OutfitConfig.Format.FIGURA);
    }

    private static OutfitConfig.Instance instance(UUID id, OutfitConfig.ResourceRef resource, int ordinal, boolean enabled,
                                                   OutfitConfig.AnchorRef anchor, double x) {
        return new OutfitConfig.Instance(id, resource, ordinal, enabled, anchor,
                new OutfitConfig.Transform(new OutfitConfig.Vector(x, 0, 0), new OutfitConfig.Vector(0, 0, 0),
                        new OutfitConfig.Vector(1, 1, 1)), List.of(), Map.of(), Map.of());
    }

    private static void require(boolean condition, String label) {
        if (!condition) {
            throw new AssertionError(label);
        }
    }
}
