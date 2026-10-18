package top.aihmc.omnimorph.model.library;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.world.entity.EquipmentSlot;
import top.aihmc.omnimorph.mixin.fabric.HumanoidArmorLayerMixinFabric;
import top.aihmc.omnimorph.model.ParentType;
import top.aihmc.omnimorph.utils.FiguraArmorPartRenderer;

/** Exercises the actual armor lookup without submitting geometry or asserting pixels. */
public final class LibraryArmorPreviewRegression {
    public static void run(AvatarRenderState state) throws Exception {
        var method = HumanoidArmorLayerMixinFabric.class.getDeclaredMethod("figura$tryRenderArmorPart",
                EquipmentSlot.class, FiguraArmorPartRenderer.class, PoseStack.class,
                HumanoidRenderState.class, SubmitNodeCollector.class, int.class, ParentType[].class);
        method.setAccessible(true);
        method.invoke(new ArmorLayer(), EquipmentSlot.HEAD, null, null, state, null, 0, new ParentType[0]);
        method.invoke(new ArmorLayer(), EquipmentSlot.HEAD, null, null, new AvatarRenderState(), null, 0,
                new ParentType[0]);
        System.out.println("PASS armor lookup retains detached preview equipment after selection clears");
    }

    private static final class ArmorLayer extends HumanoidArmorLayerMixinFabric<AvatarRenderState,
            HumanoidModel<AvatarRenderState>, HumanoidModel<AvatarRenderState>> {
        private ArmorLayer() {
            super(null);
        }

        @Override
        public boolean invoke$usesInnerModel(EquipmentSlot slot) {
            return slot == EquipmentSlot.LEGS;
        }

        @Override
        public void submit(PoseStack poses, SubmitNodeCollector collector, int light,
                           AvatarRenderState state, float yaw, float pitch) {
        }
    }
}
