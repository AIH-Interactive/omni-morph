package org.figuramc.figura.mixin.render.renderers;

import net.minecraft.client.renderer.blockentity.state.SkullBlockRenderState;
import org.figuramc.figura.avatar.Avatar;
import org.figuramc.figura.ducks.FiguraSkullRenderStateExtension;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(SkullBlockRenderState.class)
public class SkullBlockRenderStateMixin implements FiguraSkullRenderStateExtension {
    @Unique
    private Avatar figura$avatar;

    @Override
    public void figura$setAvatar(Avatar avatar) {
        this.figura$avatar = avatar;
    }

    @Override
    public Avatar figura$getAvatar() {
        return this.figura$avatar;
    }
}
