package top.aihmc.omnimorph.model.outfit;

import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.nbt.CompoundTag;
import org.joml.Matrix4f;
import org.figuramc.figura.avatar.Avatar;
import org.figuramc.figura.permissions.PermissionPack;
import org.figuramc.figura.permissions.Permissions;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Native CPU poses and the actual trusted Lua controller; real client pixels remain pending. */
public final class OutfitAnchorSmokeTest {
    private record Node(String name, List<Node> children) {}

    public static void run() throws Exception {
        TrailBufferSmokeTest.run();
        PlayerModel model = new PlayerModel(LayerDefinition.create(
                PlayerModel.createMesh(CubeDeformation.NONE, false), 64, 64).bakeRoot(), false);
        model.head.setPos(1, 2, 3);
        model.head.xRot = (float) Math.PI / 2;
        model.leftLeg.setPos(5, 12, -2);
        model.leftLeg.zRot = (float) Math.PI / 2;
        model.rightLeg.setPos(-4, 12, 1);
        model.rightLeg.zRot = (float) -Math.PI / 2;
        var adapter = new VanillaSkeletonAdapter(model, OutfitLimits.defaults());
        SkeletonPose pose = adapter.capture(new Matrix4f());
        for (String name : OutfitConfig.STANDARD_ANCHORS) {
            require(StandardAnchors.resolve(pose.definition(), true, name).size()
                    == (name.equals("tailtip") ? 0 : 1), "vanilla standard point: " + name);
        }
        position(standard(pose, "head_anchor"), 1, 2, -5, "rotated head top");
        position(standard(pose, "hair_anchor"), 1, 2, -5, "rotated hair point");
        position(standard(pose, "face_anchor"), 1, 6, -1, "rotated face point");
        position(standard(pose, "left_leg_anchor"), -1, 12, -2, "left leg point");
        position(standard(pose, "left_foot_anchor"), -7, 12, -2, "left foot point");
        position(standard(pose, "right_leg_anchor"), 2, 12, 1, "right leg point");
        position(standard(pose, "right_foot_anchor"), 8, 12, 1, "right foot point");
        var custom = SkeletonIndex.capture(null, List.of(new Node("Root", List.of(
                new Node("head_anchor", List.of()), new Node("head_anchor", List.of()),
                new Node("tailtip", List.of())))), Node::name, Node::children, OutfitLimits.defaults()).definition();
        var candidates = StandardAnchors.resolve(custom, false, "head_anchor");
        require(candidates.size() == 2 && !candidates.get(0).bone().equals(candidates.get(1).bone()),
                "duplicate empty author groups remain distinct full-path candidates");
        require(StandardAnchors.resolve(custom, false, "tailtip").size() == 1
                        && StandardAnchors.resolve(custom, false, "hip").isEmpty(),
                "empty author tail tip is retained; absent custom points are not invented");
        var aliased = SkeletonIndex.capture(null, List.of(new Node("Root", List.of(
                new Node("HEAD", List.of()), new Node("Seg1", List.of())))), Node::name, Node::children,
                OutfitLimits.defaults()).definition();
        require(StandardAnchors.resolve(aliased, false, "head_anchor").size() == 1,
                "custom rigs resolve standard anchors by case-insensitive alias");
        var unnamedHead = SkeletonIndex.capture(null, List.of(new Node("Root", List.of(
                new Node("HairChip", List.of()), new Node("Seg1", List.of())))), Node::name, Node::children,
                OutfitLimits.defaults()).definition();
        require(StandardAnchors.resolve(unnamedHead, false, "head_anchor").size() == 1,
                "custom rigs without a named head fall back to the first authored rig node");
        var shared = new Node("Shared", List.of());
        var deduplicated = SkeletonIndex.capture(null, List.of(new Node("Root", List.of(shared, shared))),
                Node::name, Node::children, OutfitLimits.defaults()).definition();
        require(deduplicated.nodes().size() == 2,
                "a native part exposed through two paths is captured once instead of failing the frame");
        testExportDeclarations(custom);
        var head = pose.definition().reference(null, pose.definition().uniqueNamed("Head").orElseThrow());
        UUID virtualId = UUID.randomUUID();
        var local = new OutfitConfig.Transform(new OutfitConfig.Vector(3, 4, 5),
                new OutfitConfig.Vector(0, 0, 90), new OutfitConfig.Vector(2, 3, 4));
        var anchor = new OutfitConfig.VirtualAnchor(virtualId, "共享锚点", head, local);
        var first = virtualCommands(pose, anchor);
        require(first.size() == 2, "two instances share one virtual anchor");
        position(first.get(0), 4, -3, 21, "virtual parent, rotation, scale and first offset");
        position(first.get(1), 4, -3, 3, "shared anchor keeps second instance offset independent");
        var renamed = virtualCommands(pose,
                new OutfitConfig.VirtualAnchor(virtualId, "renamed", head, local));
        require(first.equals(renamed), "virtual rename retains identity and both instance transforms");
        var obsolete = new OutfitConfig.BoneRef(null, head.path(), "1".repeat(64));
        require(virtualCommands(pose, new OutfitConfig.VirtualAnchor(virtualId, "obsolete", obsolete, local)).isEmpty(),
                "skeleton revision invalidation stops dependants without silent fallback");
        System.out.println("PASS standard/virtual anchors: native part rotations, exact empty groups, ambiguity, sharing, rename and stale skeleton rejection");
    }

    private static void testExportDeclarations(SkeletonIndex skeleton) {
        var json = new com.google.gson.JsonObject();
        json.addProperty("has_bones", true);
        json.addProperty("skeleton_hash", skeleton.hash());
        var exports = new com.google.gson.JsonArray();
        var serializer = new com.google.gson.GsonBuilder()
                .setFieldNamingPolicy(com.google.gson.FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES).create();
        exports.add(serializer.toJsonTree(skeleton.reference(null, skeleton.uniqueNamed("tailtip").orElseThrow())));
        json.add("exported_bones", exports);
        require(CosmeticMetadata.read(json.toString()).exportedBones().size() == 1,
                "author declaration keeps a resource-relative empty socket path");
        exports.add(exports.get(0).deepCopy());
        rejectsMetadata(json, "duplicate exported paths");
        exports.remove(1);
        exports.get(0).getAsJsonObject().addProperty("instance_id", UUID.randomUUID().toString());
        rejectsMetadata(json, "author declaration cannot capture a wearer instance UUID");
        exports.get(0).getAsJsonObject().remove("instance_id");
        json.addProperty("has_bones", false);
        json.addProperty("skeleton_hash", "");
        rejectsMetadata(json, "a resource without bones cannot export a socket");
    }

    private static void rejectsMetadata(com.google.gson.JsonObject value, String label) {
        try {
            CosmeticMetadata.read(value.toString());
            throw new AssertionError("accepted " + label);
        } catch (IllegalArgumentException expected) {
            // The native metadata reader, not a test-only schema, enforces this boundary.
        }
    }

    private static Matrix4f standard(SkeletonPose pose, String name) {
        var target = StandardAnchors.resolve(pose.definition(), true, name).getFirst();
        return pose.matrix(target.bone()).orElseThrow().mul(target.offset().modelMatrix());
    }

    private static List<Matrix4f> virtualCommands(SkeletonPose pose, OutfitConfig.VirtualAnchor virtual) throws Exception {
        var resource = new OutfitConfig.ResourceRef(OutfitConfig.Source.LOCAL, UUID.randomUUID(), null, null,
                null, "0".repeat(64), OutfitConfig.Format.FIGURA);
        var target = new OutfitConfig.AnchorRef(OutfitConfig.AnchorType.VIRTUAL, null, null, virtual.id(), null, null);
        var declarations = new ArrayList<OutfitConfig.Instance>();
        for (int offset : List.of(7, -2)) {
            declarations.add(new OutfitConfig.Instance(UUID.randomUUID(), resource, declarations.size() + 1, true,
                    target, new OutfitConfig.Transform(new OutfitConfig.Vector(offset, 0, 0),
                    new OutfitConfig.Vector(0, 0, 0), new OutfitConfig.Vector(1, 1, 1)),
                    List.of(), Map.of(), Map.of()));
        }
        var config = new OutfitConfig(1, 0, null, declarations, List.of(virtual), Map.of(resource.identity(), 2), false);
        var instances = new LinkedHashMap<UUID, Avatar>();
        UUID wearer = UUID.randomUUID();
        UUID outfit = UUID.randomUUID();
        var permissions = new PermissionPack.PlayerPermissionPack(
                new PermissionPack.CategoryPermissionPack(Permissions.Category.DEFAULT), wearer.toString());
        try {
            for (var declaration : declarations) {
                var context = new CosmeticInstanceContext(wearer, outfit, wearer, declaration,
                        OutfitLimits.defaults(), CosmeticInstanceContext.Scope.DRAFT);
                Avatar avatar = Avatar.createCosmetic(context, permissions, "virtual-anchor-fixture");
                instances.put(declaration.instanceId(), avatar);
                avatar.load(new CompoundTag());
            }
            try (var composition = new OutfitComposition(config, OutfitLimits.defaults(), null, instances,
                    new SkeletonIndex.Cache()); var frame = composition.retain()) {
                var commands = new ArrayList<Matrix4f>();
                frame.draw(pose, null, 0, draw -> commands.add(draw.matrix()));
                if (commands.isEmpty()) {
                    require(instances.values().stream().allMatch(avatar -> avatar.luaRuntime == null),
                            "invalid virtual parent never initializes author effects");
                }
                return commands;
            }
        } finally {
            instances.values().forEach(Avatar::clean);
        }
    }

    private static void position(Matrix4f matrix, float x, float y, float z, String label) {
        require(Math.abs(matrix.m30() - x) < .0001f && Math.abs(matrix.m31() - y) < .0001f
                        && Math.abs(matrix.m32() - z) < .0001f,
                label + ": got " + matrix.m30() + ", " + matrix.m31() + ", " + matrix.m32());
    }

    private static void require(boolean condition, String label) {
        if (!condition) {
            throw new AssertionError(label);
        }
    }
}
