package top.aihmc.omnimorph.mixin.render.renderers;

import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.client.renderer.PlayerSkinRenderCache;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.special.PlayerHeadSpecialRenderer;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ResolvableProfile;
import top.aihmc.omnimorph.avatar.Avatar;
import top.aihmc.omnimorph.avatar.AvatarManager;
import top.aihmc.omnimorph.ducks.PlayerHeadRenderInfoExtension;
import top.aihmc.omnimorph.ducks.SkullBlockRendererHelper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PlayerHeadSpecialRenderer.class)
public abstract class PlayerHeadSpecialRendererMixin {
    @ModifyReturnValue(method = "extractArgument(Lnet/minecraft/world/item/ItemStack;)Lnet/minecraft/client/renderer/PlayerSkinRenderCache$RenderInfo;", at = @At("TAIL"))
    public PlayerSkinRenderCache.RenderInfo setAvatar(PlayerSkinRenderCache.RenderInfo original, @Local(argsOnly = true) ItemStack itemStack) {
        ResolvableProfile profile = itemStack.get(DataComponents.PROFILE);
        Avatar avatar = profile != null ? AvatarManager.getAvatarForPlayer(profile.partialProfile().id()) : null;
        ((PlayerHeadRenderInfoExtension)(Object)original).figura$setAvatar(avatar);
        return original;
    }

    @Inject(method = "submit(Lnet/minecraft/client/renderer/PlayerSkinRenderCache$RenderInfo;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;IIZI)V", at = @At("HEAD"))
    private void captureAvatar(PlayerSkinRenderCache.RenderInfo playerHeadRenderInfo, PoseStack poseStack, SubmitNodeCollector submitNodeCollector, int i, int j, boolean bl, int k, CallbackInfo ci) {
        if (playerHeadRenderInfo == null) {
            return;
        }
        Avatar avatar = ((PlayerHeadRenderInfoExtension)(Object)playerHeadRenderInfo).figura$getAvatar();
        SkullBlockRendererHelper.setAvatar(avatar);
    }
}
