package top.aihmc.omnimorph.utils.fabric;

import net.minecraft.client.renderer.entity.layers.WingsLayer;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.resources.ResourceLocation;
import top.aihmc.omnimorph.mixin.fabric.ElytraLayerAccessor;

public class RenderUtilsImpl {
    public static ResourceLocation getPlayerSkinTexture(WingsLayer<?, ?> wingsLayer, HumanoidRenderState humanoidRenderState) {
        return ElytraLayerAccessor.invoke$getPlayerElytraTexture(humanoidRenderState);
    }
}
