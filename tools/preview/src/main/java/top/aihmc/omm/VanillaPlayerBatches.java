package top.aihmc.omm;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.resources.Identifier;

/** Feeds original Minecraft player parts through the selected OMP acceleration backend. */
final class VanillaPlayerBatches {
    private static final String ACCELERATION = "top.aihmc.omnimorph.render.acceleration.";
    private final Object lease;
    private final Object api;
    private final PreviewSkin skin;
    private final VanillaPlayerGeometry geometry;
    private final Map<double[], Object> meshes = new IdentityHashMap<>();
    private final RenderInputs inputs;

    VanillaPlayerBatches(Object lease, Object api) throws Exception {
        this.lease = lease;
        this.api = api;
        skin = new PreviewSkin();
        geometry = new VanillaPlayerGeometry(skin.slim());
        inputs = RenderInputs.read();
    }

    List<Object> sample(SceneLayout.Placement placement) throws Exception {
        var result = new ArrayList<Object>();
        Class<?> meshType = Class.forName(ACCELERATION + "MeshHandle");
        Class<?> poseType = Class.forName(ACCELERATION + "PoseSnapshot");
        Class<?> semanticsType = Class.forName(ACCELERATION + "DrawSemantics");
        Class<?> layerType = Class.forName(ACCELERATION + "DrawSemantics$Layer");
        Class<?> batchType = Class.forName(ACCELERATION + "DrawBatch");
        Identifier texture = Identifier.parse(PreviewSkin.IDENTIFIER);
        for (var part : geometry.parts(api, placement)) {
            Object mesh = meshes.get(part.vertices());
            if (mesh == null) {
                mesh = meshType.getConstructor(double[].class, int.class).newInstance(part.vertices(), 4);
                meshes.put(part.vertices(), mesh);
            }
            Object pose = poseType.getConstructor(part.pose().getClass()).newInstance(part.pose());
            Object semantics = semanticsType.getConstructors()[0].newInstance(
                RenderTypes.entityTranslucent(texture), texture, layerType.getField("PRIMARY").get(null),
                "vanilla-player", 0, part.vertices().length / 36, inputs.packedLight(), inputs.packedOverlay(),
                inputs.tint().x, inputs.tint().y, inputs.tint().z, inputs.tint().w, 0d, part.visible());
            result.add(batchType.getConstructor(lease.getClass(), meshType, poseType, semanticsType)
                .newInstance(lease, mesh, pose, semantics));
        }
        return result;
    }

    byte[] textureBytes() {
        return skin.bytes();
    }

    void updateBindings(Object vanillaModelData) throws Exception {
        geometry.updateBindings(api, vanillaModelData);
    }
}
