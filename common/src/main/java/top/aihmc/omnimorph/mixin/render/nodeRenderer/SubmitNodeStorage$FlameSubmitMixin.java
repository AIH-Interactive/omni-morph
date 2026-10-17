package top.aihmc.omnimorph.mixin.render.nodeRenderer;

import net.minecraft.client.renderer.SubmitNodeStorage;
import top.aihmc.omnimorph.avatar.Avatar;
import top.aihmc.omnimorph.ducks.FlameSubmitExtension;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(SubmitNodeStorage.FlameSubmit.class)
public class SubmitNodeStorage$FlameSubmitMixin implements FlameSubmitExtension {
    @Unique
    private Avatar figura$avatar = null;

    @Override
    public void figura$setAvatar(Avatar avatar) {
        this.figura$avatar = avatar;
    }

    @Override
    public Avatar figura$getAvatar() {
        return figura$avatar;
    }
}
