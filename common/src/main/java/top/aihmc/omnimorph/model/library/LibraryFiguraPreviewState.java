package top.aihmc.omnimorph.model.library;

import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.state.gui.pip.PictureInPictureRenderState;
import top.aihmc.omnimorph.avatar.Avatar;

/** Model and skin inputs only; the drawing path performs no entity lookup or dispatch. */
public record LibraryFiguraPreviewState(Avatar avatar, AvatarRenderState playerPose, float delta,
                                       float pitch, float yaw, float offsetX, float offsetY,
                                       int x0, int y0, int x1, int y1, float scale,
                                       ScreenRectangle scissorArea, ScreenRectangle bounds)
        implements PictureInPictureRenderState {
}
