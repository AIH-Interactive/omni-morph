package top.aihmc.omnimorph.mixin.gui;

import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.screens.Screen;
import org.figuramc.figura.gui.ActionWheel;
import org.figuramc.figura.gui.PopupMenu;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Gui.class)
public abstract class GuiScreenMixin {
    @Inject(method = "setScreen", at = @At("HEAD"))
    private void figura$closeTransientMenus(Screen screen, CallbackInfo ci) {
        if (ActionWheel.isEnabled()) {
            ActionWheel.setEnabled(false);
        }
        if (PopupMenu.isEnabled()) {
            PopupMenu.run();
        }
    }
}
