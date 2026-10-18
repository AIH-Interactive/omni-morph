package top.aihmc.omnimorph.gui.widgets.library;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import top.aihmc.omnimorph.gui.widgets.AbstractContainerElement;
import top.aihmc.omnimorph.gui.widgets.Button;
import top.aihmc.omnimorph.utils.FiguraText;
import top.aihmc.omnimorph.FiguraMod;

import java.util.List;
import java.util.function.IntConsumer;

/** The five reference sections, using Figura buttons and their normal focus/keyboard behavior. */
public final class LibrarySectionTabs extends AbstractContainerElement {
    private static final List<String> KEYS = List.of("scope.mine", "scope.favorites", "scope.public", "upload", "manage");
    private boolean administrator;

    public LibrarySectionTabs(int x, int y, int width, int selected, boolean administrator, IntConsumer navigate) {
        super(x, y, width, 20);
        this.administrator = administrator;
        int count = administrator ? 5 : 4;
        for (int index = 0; index < KEYS.size(); index++) {
            int section = index;
            int left = x + width * index / count;
            int right = x + width * (index + 1) / count;
            children.add(new Button(left, y, right - left - 3, 20,
                    FiguraText.of("gui.library." + KEYS.get(index)), null, ignored -> {
                        if (section != selected) {
                            navigate.accept(section);
                        }
                    }) {
                @Override
                protected void renderDefaultTexture(GuiGraphicsExtractor gui, float delta) {
                    super.renderDefaultTexture(gui, delta);
                    if (section == selected) {
                        gui.fill(getX() + 1, getY() + getHeight() - 2,
                                getX() + getWidth() - 1, getY() + getHeight() - 1,
                                FiguraMod.getAccentColor().getColor().getValue() | 0xff000000);
                    }
                }
            });
        }
    }

    public Button button(int section) {
        return (Button) children.get(section);
    }

    public void administrator(boolean available) {
        administrator = available;
    }

    public void setEnabled(boolean enabled) {
        children.forEach(child -> ((Button) child).setActive(enabled));
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor gui, int mouseX, int mouseY, float delta) {
        int count = administrator ? 5 : 4;
        for (int index = 0; index < children.size(); index++) {
            Button button = button(index);
            button.setVisible(index < count);
            button.setX(getX() + getWidth() * index / count);
            button.setY(getY());
            button.setWidth(getWidth() * (index + 1) / count - getWidth() * index / count - 3);
        }
        super.extractRenderState(gui, mouseX, mouseY, delta);
    }
}
