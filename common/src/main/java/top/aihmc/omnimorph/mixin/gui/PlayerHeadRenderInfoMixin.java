package top.aihmc.omnimorph.mixin.gui;

import net.minecraft.client.renderer.PlayerSkinRenderCache;
import net.minecraft.client.renderer.special.PlayerHeadSpecialRenderer;
import top.aihmc.omnimorph.avatar.Avatar;
import top.aihmc.omnimorph.ducks.PlayerHeadRenderInfoExtension;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(PlayerSkinRenderCache.RenderInfo.class)
public class PlayerHeadRenderInfoMixin implements PlayerHeadRenderInfoExtension {
    @Unique
    Avatar figura$avatar = null;
    @Override
    public Avatar figura$getAvatar() {
        return figura$avatar;
    }

    @Override
    public void figura$setAvatar(Avatar avatar) {
        this.figura$avatar = avatar;
    }
}
