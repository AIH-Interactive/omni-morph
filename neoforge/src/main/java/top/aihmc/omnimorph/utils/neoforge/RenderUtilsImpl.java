package top.aihmc.omnimorph.utils.neoforge;

import net.minecraft.client.renderer.entity.layers.WingsLayer;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.resources.Identifier;
import top.aihmc.omnimorph.mixin.neoforge.ElytraLayerAccessor;

public class RenderUtilsImpl {
    public static Identifier getPlayerSkinTexture(WingsLayer<?, ?> wingsLayer, HumanoidRenderState humanoidRenderState) {
        return ElytraLayerAccessor.invoke$getPlayerElytraTexture(humanoidRenderState);
    }
}
