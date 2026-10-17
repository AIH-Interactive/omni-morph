package top.aihmc.omnimorph.utils.forge;

import net.minecraft.client.renderer.entity.layers.WingsLayer;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.resources.Identifier;
import top.aihmc.omnimorph.mixin.forge.ElytraLayerAccessor;

public class RenderUtilsImpl {
    public static Identifier getPlayerSkinTexture(WingsLayer<?, ?> wingsLayer, HumanoidRenderState humanoidRenderState) {
        return ((ElytraLayerAccessor)wingsLayer).invoke$getPlayerElytraTexture(humanoidRenderState);
    }
}
