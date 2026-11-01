package top.aihmc.omnimorph.mixin.forge;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphics;
import org.figuramc.figura.avatar.AvatarManager;
import org.figuramc.figura.gui.ActionWheel;
import org.figuramc.figura.math.vector.FiguraVec2;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(Gui.class)
public abstract class GuiMixin {
    @Shadow @Final private Minecraft minecraft;

    @WrapMethod(method = "renderCrosshair")
    private void figura$renderCrosshair(GuiGraphics graphics, Operation<Void> original) {
        if (ActionWheel.isEnabled()) {
            return;
        }
        var entity = minecraft.getCameraEntity();
        var avatar = entity == null ? null : AvatarManager.getAvatar(entity);
        FiguraVec2 offset = null;
        if (avatar != null && avatar.luaRuntime != null) {
            if (!avatar.luaRuntime.renderer.renderCrosshair) {
                return;
            }
            offset = avatar.luaRuntime.renderer.crosshairOffset;
        }
        if (offset == null) {
            original.call(graphics);
            return;
        }
        graphics.pose().pushPose();
        try {
            graphics.pose().translate(offset.x, offset.y, 0);
            original.call(graphics);
        } finally {
            graphics.pose().popPose();
        }
    }
}
