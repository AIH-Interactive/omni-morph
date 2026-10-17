package top.aihmc.omnimorph.mixin.gui;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import top.aihmc.omnimorph.FiguraMod;
import top.aihmc.omnimorph.avatar.Avatar;
import top.aihmc.omnimorph.avatar.AvatarManager;
import top.aihmc.omnimorph.gui.FiguraFunctionClickEvent;
import top.aihmc.omnimorph.utils.TextUtils;
import org.luaj.vm2.LuaValue;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Screen.class)
public class ScreenMixin {

    @Inject(at = @At("HEAD"), method = "defaultHandleClickEvent", cancellable = true)
    private static void handleComponentClicked(ClickEvent event, Minecraft minecraft, Screen screen, CallbackInfo ci) {
        if (event == null)
            return;

        if (event instanceof TextUtils.FiguraClickEvent figuraEvent) {
            figuraEvent.onClick().run();
            ci.cancel();
        } else if (event instanceof FiguraFunctionClickEvent) {
            Avatar avatar = AvatarManager.getAvatarForPlayer(FiguraMod.getLocalPlayerUUID());
            if (avatar == null)
                return;
            LuaValue value = null;
            try {
                value = avatar.loadScript("figura_function", ((FiguraFunctionClickEvent) event).chunk());
            } catch (Exception e) {
                FiguraMod.sendChatMessage(Component.literal(e.getMessage()).withStyle(Style.EMPTY.withColor(ChatFormatting.RED).withBold(true)));
            }
            if (value != null)
                avatar.run(value, avatar.tick);
            ci.cancel();
        }
    }
}
