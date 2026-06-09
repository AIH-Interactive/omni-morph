package org.figuramc.figura.gui;

import com.mojang.blaze3d.platform.Window;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.entity.Entity;
import org.figuramc.figura.FiguraMod;
import org.figuramc.figura.avatar.Avatar;
import org.figuramc.figura.avatar.AvatarManager;
import org.figuramc.figura.mixin.gui.GuiGraphicsAccessor;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

public class FiguraGui {

    public static void onRender(GuiGraphicsExtractor guiGraphicsExtractor, float tickDelta, CallbackInfo ci) {
        if (AvatarManager.panic)
            return;

        FiguraMod.pushProfiler(FiguraMod.MOD_ID);

        // render popup menu below everything, as if it were in the world
        FiguraMod.pushProfiler("popupMenu");
        PopupMenu.render(guiGraphicsExtractor);
        FiguraMod.popProfiler();

        // get avatar
        Entity entity = Minecraft.getInstance().getCameraEntity();
        Avatar avatar = entity == null ? null : AvatarManager.getAvatar(entity);

        if (avatar != null) {
            FiguraMod.pushProfiler("guiRender");
            Window window = Minecraft.getInstance().getWindow();
            int screenWidth = window.getWidth() / window.getGuiScale();
            int screenHeight = window.getHeight() / window.getGuiScale();

            FiguraGuiRenderState guiState = new FiguraGuiRenderState(
                    avatar, entity, tickDelta, screenWidth, screenHeight
            );
            ((GuiGraphicsAccessor) guiGraphicsExtractor).figura$getRenderState().addPicturesInPictureState(guiState);
            FiguraMod.popProfiler();

            // hud hidden by script
            if (avatar.luaRuntime != null && !avatar.luaRuntime.renderer.renderHUD) {
                // render figura overlays
                renderOverlays(guiGraphicsExtractor);
                // cancel this method
                ci.cancel();
            }
        }

        FiguraMod.popProfiler();
    }

    public static void renderOverlays(GuiGraphicsExtractor guiGraphicsExtractor) {
        FiguraMod.pushProfiler(FiguraMod.MOD_ID);

        // render paperdoll
        FiguraMod.pushProfiler("paperdoll");
        PaperDoll.render(guiGraphicsExtractor, false);

        // render wheel
        FiguraMod.popPushProfiler("actionWheel");
        ActionWheel.render(guiGraphicsExtractor);

        FiguraMod.popProfiler(2);
    }
}
