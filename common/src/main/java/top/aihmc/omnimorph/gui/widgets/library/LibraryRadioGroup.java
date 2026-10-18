package top.aihmc.omnimorph.gui.widgets.library;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import top.aihmc.omnimorph.gui.widgets.AbstractContainerElement;
import top.aihmc.omnimorph.gui.widgets.Button;
import top.aihmc.omnimorph.utils.ui.UIHelper;
import top.aihmc.omnimorph.FiguraMod;

import java.util.function.Consumer;

/** A pair of mutually exclusive Figura buttons with the reference's compact radio markers. */
public final class LibraryRadioGroup extends AbstractContainerElement {
    private boolean value;
    private final Consumer<Boolean> changed;

    public LibraryRadioGroup(int width, Component yes, Component no, boolean initial, Consumer<Boolean> changed) {
        super(0, 0, width, 20);
        value = initial;
        this.changed = changed;
        children.add(option(yes, true));
        children.add(option(no, false));
    }

    private Button option(Component label, boolean option) {
        return new Button(0, 0, getWidth() / 2, 20, label, null, ignored -> {
            value = option;
            changed.accept(option);
        }) {
            @Override
            protected void renderDefaultTexture(GuiGraphicsExtractor gui, float delta) {
                int x = getX() + 2;
                int y = getY() + 6;
                int accent = FiguraMod.getAccentColor().getColor().getValue() | 0xff000000;
                int color = !isActive() ? 0xff666666 : value == option || isHoveredOrFocused() ? accent : 0xffbac6d8;
                gui.fill(x + 1, y, x + 6, y + 7, color);
                gui.fill(x, y + 1, x + 7, y + 6, color);
                gui.fill(x + 1, y + 1, x + 6, y + 6, 0xff101820);
                if (value == option) {
                    gui.fill(x + 2, y + 2, x + 5, y + 5, color);
                }
            }

            @Override
            protected void renderText(GuiGraphicsExtractor gui, float delta) {
                UIHelper.renderScrollingText(gui, getMessage(), getX() + 14, getY() + 6,
                        Math.max(1, getWidth() - 16), getTextColor() | 0xff000000);
            }
        };
    }

    public void setValue(boolean value) {
        this.value = value;
    }

    public void setActive(boolean active) {
        children.forEach(child -> ((Button) child).setActive(active));
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor gui, int mouseX, int mouseY, float delta) {
        for (int index = 0; index < children.size(); index++) {
            Button button = (Button) children.get(index);
            button.setX(getX() + index * getWidth() / 2);
            button.setY(getY());
        }
        super.extractRenderState(gui, mouseX, mouseY, delta);
    }
}
