package top.aihmc.omnimorph.ducks;

import net.minecraft.client.Camera;
import net.minecraft.client.gui.render.GuiRenderer;

public interface GameRendererAccessor {

    double figura$getFov(Camera camera, float tickDelta, boolean changingFov);

    GuiRenderer figura$getGuiRenderer();
}
