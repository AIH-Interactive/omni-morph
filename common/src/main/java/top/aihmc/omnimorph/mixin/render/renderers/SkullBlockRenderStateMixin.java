package top.aihmc.omnimorph.mixin.render.renderers;

import net.minecraft.client.renderer.blockentity.state.SkullBlockRenderState;
import top.aihmc.omnimorph.avatar.Avatar;
import top.aihmc.omnimorph.ducks.FiguraSkullRenderStateExtension;
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
