package top.aihmc.omnimorph.mixin.gui.books;

import net.minecraft.client.gui.screens.inventory.BookViewScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import top.aihmc.omnimorph.font.Emojis;
import top.aihmc.omnimorph.utils.TextUtils;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

@Mixin(value = BookViewScreen.class, priority = 1100)
public class BookViewScreenMixin {
    @ModifyArg(method = "visitText", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/ActiveTextCollector;accept(IILnet/minecraft/util/FormattedCharSequence;)V"))
    public FormattedCharSequence render(FormattedCharSequence formattedCharSequence) {
        return Emojis.applyEmojis(TextUtils.charSequenceToText(formattedCharSequence)).getVisualOrderText();
    }

    @ModifyArg(method = "visitText", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/ActiveTextCollector;accept(Lnet/minecraft/client/gui/TextAlignment;IILnet/minecraft/network/chat/Component;)V"), index = 3)
    public Component render(Component component) {
        return Emojis.applyEmojis(component);
    }
}
