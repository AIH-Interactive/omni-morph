package top.aihmc.omnimorph.gui.neoforge;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.LayeredDraw;
import top.aihmc.omnimorph.avatar.AvatarManager;
import top.aihmc.omnimorph.gui.FiguraGui;
import org.jetbrains.annotations.NotNull;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

public class GuiOverlay implements LayeredDraw.Layer {
    @Override
    public void render(@NotNull GuiGraphics guiGraphics, DeltaTracker deltaTracker) {
        if (!AvatarManager.panic)
            FiguraGui.renderOverlays(guiGraphics);
    }
}
