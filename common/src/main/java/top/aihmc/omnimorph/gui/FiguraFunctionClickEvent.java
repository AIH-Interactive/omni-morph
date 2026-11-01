package top.aihmc.omnimorph.gui;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.network.chat.ClickEvent;

public final class FiguraFunctionClickEvent extends ClickEvent {
    public static final MapCodec<FiguraFunctionClickEvent> CODEC = RecordCodecBuilder.mapCodec(
            instance -> instance.group(Codec.STRING.fieldOf("value").forGetter(FiguraFunctionClickEvent::chunk)).apply(instance, FiguraFunctionClickEvent::new)
    );

    public FiguraFunctionClickEvent(String chunk) {
        super(Action.valueOf("FIGURA_FUNCTION"), chunk);
    }

    public String chunk() {
        return getValue();
    }
}
