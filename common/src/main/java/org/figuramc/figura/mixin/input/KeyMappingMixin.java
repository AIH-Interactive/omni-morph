package org.figuramc.figura.mixin.input;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import org.figuramc.figura.FiguraMod;
import org.figuramc.figura.avatar.Avatar;
import top.aihmc.omnimorph.model.outfit.VisibleAvatar;
import org.figuramc.figura.gui.ActionWheel;
import org.figuramc.figura.lua.api.keybind.FiguraKeybind;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(KeyMapping.class)
public class KeyMappingMixin {

    @Shadow private InputConstants.Key key;

    @Inject(method = "setAll", at = @At("HEAD"))
    private static void setAll(CallbackInfo ci) {
        Avatar avatar = VisibleAvatar.localPlayer();
        if (avatar != null && avatar.luaRuntime != null)
            FiguraKeybind.updateAll(avatar.luaRuntime.keybinds.keyBindings);
    }

    @Inject(method = "releaseAll", at = @At("HEAD"))
    private static void releaseAll(CallbackInfo ci) {
        ActionWheel.releaseYsmPress();
        Avatar avatar = VisibleAvatar.localPlayer();
        if (avatar != null && avatar.luaRuntime != null)
            FiguraKeybind.releaseAll(avatar.luaRuntime.keybinds.keyBindings);
    }

    @ModifyVariable(method = "setDown", at = @At("HEAD"), argsOnly = true)
    private boolean setDown(boolean pressed) {
        Avatar avatar = VisibleAvatar.localPlayer();
        if (avatar == null || avatar.luaRuntime == null)
            return pressed;

        return pressed && !FiguraKeybind.overridesKey(avatar.luaRuntime.keybinds.keyBindings, this.key);
    }

    @Inject(method = "matches", at = @At("HEAD"), cancellable = true)
    private void matches(int keyCode, int scanCode, CallbackInfoReturnable<Boolean> cir) {
        if (FiguraMod.processingKeybind)
            cir.setReturnValue(false);
    }
}
