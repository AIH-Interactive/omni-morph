package top.aihmc.omnimorph.gui.widgets.library;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.util.FormattedCharSequence;
import org.figuramc.figura.gui.widgets.TextField;
import org.figuramc.figura.utils.FiguraText;

/** Password input shares the existing control skin but never exposes its text to narration/copy. */
public final class LibraryPasswordField extends TextField {
    public LibraryPasswordField(int width) {
        super(0, 0, width, 20, null, ignored -> {});
        getField().setMaxLength(256);
    }

    @Override
    protected EditBox createField(int x, int y, int width, int height) {
        var field = new EditBox(Minecraft.getInstance().font, x, y, width, height,
                FiguraText.of("gui.library.password")) {
            @Override
            protected MutableComponent createNarrationMessage() {
                return FiguraText.of("gui.library.password");
            }

            @Override
            public boolean keyPressed(KeyEvent event) {
                if ((event.modifiers() & (2 | 8)) != 0 && (event.key() == 67 || event.key() == 88)) {
                    return true;
                }
                return super.keyPressed(event);
            }

            @Override
            public void updateWidgetNarration(NarrationElementOutput output) {
                // Deliberately omit password contents, selection, length and hints.
            }
        };
        field.addFormatter((text, offset) -> FormattedCharSequence.forward("*".repeat(text.length()), Style.EMPTY));
        return field;
    }

    public void clear() {
        getField().setValue("");
    }

    @Override
    public void updateNarration(NarrationElementOutput output) {
        // The visible label identifies this field; its value is always private.
    }
}
