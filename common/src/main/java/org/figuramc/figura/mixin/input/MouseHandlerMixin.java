package org.figuramc.figura.mixin.input;

import com.mojang.blaze3d.platform.InputConstants;
import org.figuramc.figura.utils.LegacyInput;
import net.minecraft.client.Minecraft;
import net.minecraft.client.MouseHandler;
import net.minecraft.client.input.MouseButtonInfo;
import org.figuramc.figura.avatar.Avatar;
import top.aihmc.omnimorph.model.outfit.VisibleAvatar;
import org.figuramc.figura.gui.ActionWheel;
import org.figuramc.figura.gui.PopupMenu;
import top.aihmc.omnimorph.model.ysm.YsmInputState;
import org.figuramc.figura.lua.api.keybind.FiguraKeybind;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MouseHandler.class)
public class MouseHandlerMixin {

    @Shadow @Final private Minecraft minecraft;
    @Shadow private double xpos;
    @Shadow private double ypos;

    @Shadow private boolean mouseGrabbed;

    @Inject(method = "onButton", at = @At("HEAD"), cancellable = true)
    private void onPress(long window, MouseButtonInfo mouseButtonInfo, int action, CallbackInfo ci) {
        if (window != this.minecraft.getWindow().handle())
            return;

        int button = LegacyInput.mouse(mouseButtonInfo.button());
        int modifiers = LegacyInput.modifiers(mouseButtonInfo.modifiers());

        if (button == 0 && action == 0) {
            ActionWheel.releaseYsmPress();
        }

        YsmInputState.device().updateReadiness(this.minecraft);
        YsmInputState.device().mouseEvent(button, action);

        Avatar avatar = VisibleAvatar.localPlayer();
        if (avatar != null && avatar.luaRuntime != null
                && avatar.mousePressEvent(button, action, modifiers)
                && (this.mouseGrabbed || this.minecraft.gui.screen() == null)) {
            ci.cancel();
            return;
        }

        boolean pressed = action != 0;

        if (avatar != null && avatar.luaRuntime != null && FiguraKeybind.set(avatar.luaRuntime.keybinds.keyBindings,
                InputConstants.Type.MOUSE.getOrCreate(mouseButtonInfo.button()), pressed, modifiers)) {
            ci.cancel();
        }

        if (avatar != null && avatar.luaRuntime != null && pressed
                && avatar.luaRuntime.host.unlockCursor && this.minecraft.gui.screen() == null) {
            ci.cancel();
        }

        // The HUD wheel retains its formal owner even while the visible draft is vanilla.
        if (pressed && ActionWheel.isEnabled()) {
            if (button >= 0 && button <= 1) {
                ActionWheel.execute(ActionWheel.getSelected(), button == 0);
            }
            ci.cancel();
        }
    }

    @Inject(method = "onScroll", at = @At("HEAD"), cancellable = true)
    private void onScroll(long window, double scrollDeltaX, double scrollDeltaY, CallbackInfo ci) {
        Avatar avatar = VisibleAvatar.localPlayer();
        if (avatar != null && avatar.mouseScrollEvent(scrollDeltaY) && (this.mouseGrabbed || this.minecraft.gui.screen() == null)) {
            ci.cancel();
            return;
        }

        if (ActionWheel.isEnabled()) {
            ActionWheel.scroll(scrollDeltaY);
            ci.cancel();
        } else if (PopupMenu.isEnabled() && PopupMenu.hasEntity()) {
            PopupMenu.scroll(Math.signum(scrollDeltaY));
            ci.cancel();
        }
    }

    @Inject(method = "onMove", at = @At("HEAD"), cancellable = true)
    private void onMove(long window, double x, double y, double relativeX, double relativeY, CallbackInfo ci) {
        Avatar avatar = VisibleAvatar.localPlayer();
        if (avatar != null && avatar.mouseMoveEvent(mouseGrabbed ? relativeX : x - this.xpos, mouseGrabbed ? relativeY : y - this.ypos) && (this.mouseGrabbed || this.minecraft.gui.screen() == null)) {
            this.xpos = x;
            this.ypos = y;
            ci.cancel();
        }
    }

    @Inject(method = "grabMouse", at = @At("HEAD"), cancellable = true)
    private void grabMouse(CallbackInfo ci) {
        Avatar avatar = VisibleAvatar.localPlayer();
        if (ActionWheel.isEnabled() || (avatar != null && avatar.luaRuntime != null && avatar.luaRuntime.host.unlockCursor))
            ci.cancel();
    }
}
