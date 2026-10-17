package top.aihmc.omnimorph.mixin.gui;

import net.minecraft.client.multiplayer.chat.GuiMessage;
import top.aihmc.omnimorph.ducks.GuiMessageAccessor;
import org.spongepowered.asm.mixin.Intrinsic;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(GuiMessage.class)
public class GuiMessageMixin implements GuiMessageAccessor {

    @Unique private int color = 0;

    @Override @Intrinsic
    public void figura$setColor(int color) {
        this.color = color;
    }

    @Override @Intrinsic
    public int figura$getColor() {
        return color;
    }
}
