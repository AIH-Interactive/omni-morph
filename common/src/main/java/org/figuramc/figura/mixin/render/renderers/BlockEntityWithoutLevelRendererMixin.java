package org.figuramc.figura.mixin.render.renderers;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.PlayerSkinRenderCache;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.special.PlayerHeadSpecialRenderer;
import net.minecraft.world.item.ItemStack;
import org.figuramc.figura.ducks.SkullBlockRendererAccessor;
import top.aihmc.omnimorph.ducks.SkullSpecialRendererExtension;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(PlayerHeadSpecialRenderer.class)
public class BlockEntityWithoutLevelRendererMixin implements SkullSpecialRendererExtension {

    @Unique
    ItemStack figura$stack;

    @Inject(method = "extractArgument(Lnet/minecraft/world/item/ItemStack;)Lnet/minecraft/client/renderer/PlayerSkinRenderCache$RenderInfo;",
            at = @At("HEAD"))
    void saveTargetItem(ItemStack stack, CallbackInfoReturnable<PlayerSkinRenderCache.RenderInfo> cir) {
        SkullBlockRendererAccessor.setItem(stack);
        figura$stack = stack;
    }


    @Inject(method = "submit(Lnet/minecraft/client/renderer/PlayerSkinRenderCache$RenderInfo;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;IIZI)V",
            at = @At("HEAD"))
    void setTargetItem(
            PlayerSkinRenderCache.RenderInfo renderInfo, PoseStack poseStack,
            SubmitNodeCollector submitNodeCollector, int lightCoords, int overlayCoords,
            boolean hasFoil, int outlineColor, CallbackInfo ci) {
        SkullBlockRendererAccessor.setItem(figura$stack);
    }


    @Override
    public ItemStack figura$getItemStack() {
        return figura$stack;
    }
}
