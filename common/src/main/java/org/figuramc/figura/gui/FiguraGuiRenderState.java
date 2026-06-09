package org.figuramc.figura.gui;

import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.renderer.state.gui.pip.PictureInPictureRenderState;
import net.minecraft.world.entity.Entity;
import org.figuramc.figura.avatar.Avatar;
import org.jetbrains.annotations.Nullable;

public record FiguraGuiRenderState(
        Avatar avatar,
        Entity entity,
        float tickDelta,
        int x0,
        int y0,
        int x1,
        int y1,
        float scale,
        @Nullable ScreenRectangle scissorArea,
        @Nullable ScreenRectangle bounds
) implements PictureInPictureRenderState {

    public FiguraGuiRenderState(Avatar avatar, Entity entity, float tickDelta, int screenWidth, int screenHeight) {
        this(
                avatar, entity, tickDelta,
                0, 0, screenWidth, screenHeight,
                1.0f,
                null,
                PictureInPictureRenderState.getBounds(0, 0, screenWidth, screenHeight, null)
        );
    }
}
