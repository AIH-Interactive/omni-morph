package org.figuramc.figura.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.entity.Entity;
import org.figuramc.figura.FiguraMod;
import org.figuramc.figura.avatar.Avatar;
import org.figuramc.figura.avatar.AvatarManager;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

public class FiguraGui {

    public static void onRender(GuiGraphics gui, float tickDelta, CallbackInfo ci) {
        if (AvatarManager.panic) {
            return;
        }
        FiguraMod.pushProfiler(FiguraMod.MOD_ID);
        try {
            // The popup is drawn before both custom and vanilla HUD contents.
            FiguraMod.pushProfiler("popupMenu");
            try {
                PopupMenu.render(gui);
            } finally {
                FiguraMod.popProfiler();
            }
            Entity entity = Minecraft.getInstance().getCameraEntity();
            Avatar avatar = entity == null ? null : AvatarManager.getAvatar(entity);
            if (avatar != null) {
                FiguraMod.pushProfiler("guiRender");
                try {
                    gui.flush();
                    avatar.hudRender(gui.pose(), gui.bufferSource(), entity, tickDelta);
                    gui.flush();
                } finally {
                    FiguraMod.popProfiler();
                }
                if (avatar.luaRuntime != null && !avatar.luaRuntime.renderer.renderHUD) {
                    renderOverlays(gui);
                    ci.cancel();
                }
            }
        } finally {
            FiguraMod.popProfiler();
        }
    }

    public static void renderOverlays(GuiGraphics gui) {
        FiguraMod.pushProfiler(FiguraMod.MOD_ID);

        // render paperdoll
        FiguraMod.pushProfiler("paperdoll");
        PaperDoll.render(gui, false);

        // render wheel
        FiguraMod.popPushProfiler("actionWheel");
        ActionWheel.render(gui);

        FiguraMod.popProfiler(2);
    }
}
