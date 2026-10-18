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
import net.minecraft.client.model.geom.ModelPart;
import top.aihmc.omnimorph.ducks.PlayerModelCapeAccessor;
import top.aihmc.omnimorph.mixin.render.layers.CapeLayerMixin;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import java.util.List;
import java.util.Map;
import java.lang.reflect.InvocationTargetException;
import sun.misc.Unsafe;

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
        var unsafeField = Unsafe.class.getDeclaredField("theUnsafe");
        unsafeField.setAccessible(true);
        var unsafe = (Unsafe) unsafeField.get(null);
        var model = (CapeModel) unsafe.allocateInstance(CapeModel.class);
        model.cloak = new ModelPart(List.of(), Map.of());
        model.fakeCloak = new ModelPart(List.of(), Map.of());
        var layer = new CapeTestLayer();
        var modelField = CapeLayerMixin.class.getDeclaredField("model");
        modelField.setAccessible(true);
        modelField.set(layer, model);
        var cape = CapeLayerMixin.class.getDeclaredMethod("preRender", PoseStack.class,
                SubmitNodeCollector.class, int.class, AvatarRenderState.class, float.class, float.class,
                CallbackInfo.class);
        cape.setAccessible(true);
        try {
            cape.invoke(layer, null, null, 0, state, 0f, 0f, new CallbackInfo("cape", true));
            throw new AssertionError("Cape must resolve the bound preview player");
        } catch (InvocationTargetException failure) {
            if (!(failure.getCause() instanceof LibraryPreviewRuntimeSmokeTest.CapeStateReached)) {
                throw failure;
            }
        }
        cape.invoke(layer, null, null, 0, new AvatarRenderState(), 0f, 0f, new CallbackInfo("cape", true));
        System.out.println("PASS cape lookup reaches the retained preview player; unbound state is safe");
    }

    private static final class CapeModel extends HumanoidModel<AvatarRenderState> implements PlayerModelCapeAccessor {
        private ModelPart cloak;
        private ModelPart fakeCloak;

        private CapeModel() {
            super(null);
        }

        public ModelPart figura$getCloak() {
            return cloak;
        }

        public ModelPart figura$getFakeCloak() {
            return fakeCloak;
        }
    }

    private static final class CapeTestLayer extends CapeLayerMixin {
        private CapeTestLayer() {
            super(null);
        }

        @Override
        public void submit(PoseStack poses, SubmitNodeCollector collector, int light,
                           AvatarRenderState state, float yaw, float pitch) {
        }
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
