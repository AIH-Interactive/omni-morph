package top.aihmc.omm;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.player.PlayerModel;

/** Uses Minecraft's player mesh and Figura's own vanilla-part customization. */
final class VanillaPlayerGeometry {
    private final ModelPart root;
    private final PlayerModel model;
    private final java.util.Map<ModelPart.Cube, double[]> meshes = new java.util.IdentityHashMap<>();

    record Part(double[] vertices, PoseStack.Pose pose, boolean visible) {
    }

    VanillaPlayerGeometry(boolean slim) {
        root = LayerDefinition.create(PlayerModel.createMesh(CubeDeformation.NONE, slim), 64, 64).bakeRoot();
        model = new PlayerModel(root, slim);
    }

    List<Part> parts(Object api, SceneLayout.Placement placement) throws Exception {
        Object player = api.getClass().getField("PLAYER").get(api);
        player.getClass().getMethod("save", EntityModel.class).invoke(player, model);
        try {
            player.getClass().getMethod("preTransform", EntityModel.class).invoke(player, model);
            player.getClass().getMethod("posTransform", EntityModel.class).invoke(player, model);
            var pose = new PoseStack();
            if (placement != null) {
                pose.translate(placement.x(), placement.y(), placement.z());
            }
            pose.translate(0, 1.5f, 0);
            pose.scale(-1, -1, 1);
            var result = new ArrayList<Part>();
            root.visit(pose, (transform, path, index, cube) -> {
                ModelPart part = root;
                boolean visible = root.visible;
                for (String name : path.split("/")) {
                    if (!name.isEmpty()) {
                        part = part.getChild(name);
                        visible &= part.visible;
                    }
                }
                double[] vertices = meshes.computeIfAbsent(cube, key -> {
                    var output = new Vertices();
                    key.compile(new PoseStack().last(), output, 0x00f000f0, 0, -1);
                    return output.flatten();
                });
                result.add(new Part(vertices, transform.copy(), visible && !part.skipDraw));
            });
            return result;
        } finally {
            player.getClass().getMethod("restore", EntityModel.class).invoke(player, model);
        }
    }

    void updateBindings(Object api, Object vanillaModelData) throws Exception {
        Object player = api.getClass().getField("PLAYER").get(api);
        player.getClass().getMethod("save", EntityModel.class).invoke(player, model);
        try {
            player.getClass().getMethod("preTransform", EntityModel.class).invoke(player, model);
            // Original custom-model rendering samples transforms before PLAYER visibility is applied.
            vanillaModelData.getClass().getMethod("update", EntityModel.class).invoke(vanillaModelData, model);
        } finally {
            player.getClass().getMethod("restore", EntityModel.class).invoke(player, model);
        }
    }

    double[] vertices(Object vanillaModelApi) throws Exception {
        Object player = vanillaModelApi == null ? null : vanillaModelApi.getClass().getField("PLAYER").get(vanillaModelApi);
        if (player != null) {
            player.getClass().getMethod("save", EntityModel.class).invoke(player, model);
        }
        try {
            if (player != null) {
                player.getClass().getMethod("preTransform", EntityModel.class).invoke(player, model);
                player.getClass().getMethod("posTransform", EntityModel.class).invoke(player, model);
            }
            var output = new Vertices();
            var pose = new PoseStack();
            pose.translate(0, 1.5f, 0);
            pose.scale(-1, -1, 1);
            root.render(pose, output, 0x00f000f0, 0);
            return output.flatten();
        } finally {
            if (player != null) {
                player.getClass().getMethod("restore", EntityModel.class).invoke(player, model);
            }
        }
    }

    private static final class Vertices implements VertexConsumer {
        private final List<double[]> values = new ArrayList<>();

        @Override
        public VertexConsumer addVertex(float x, float y, float z) {
            values.add(new double[]{x, y, z, 1, 0, 0, 0, 0, 0});
            return this;
        }

        @Override
        public VertexConsumer setUv(float u, float v) {
            values.getLast()[7] = u;
            values.getLast()[8] = v;
            return this;
        }

        @Override
        public VertexConsumer setNormal(float x, float y, float z) {
            double[] vertex = values.getLast();
            vertex[4] = x;
            vertex[5] = y;
            vertex[6] = z;
            return this;
        }

        // Material color and lighting are supplied by the original OMP DrawSemantics.
        @Override
        public VertexConsumer setColor(int red, int green, int blue, int alpha) {
            return this;
        }

        @Override
        public VertexConsumer setColor(int color) {
            return this;
        }

        @Override
        public VertexConsumer setUv1(int u, int v) {
            return this;
        }

        @Override
        public VertexConsumer setUv2(int u, int v) {
            return this;
        }

        @Override
        public VertexConsumer setLineWidth(float width) {
            throw new UnsupportedOperationException("Player mesh contains no line primitives");
        }

        double[] flatten() {
            double[] result = new double[values.size() * 9];
            for (int index = 0; index < values.size(); index++) {
                System.arraycopy(values.get(index), 0, result, index * 9, 9);
            }
            return result;
        }
    }
}
