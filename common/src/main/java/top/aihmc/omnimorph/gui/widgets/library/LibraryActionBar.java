package top.aihmc.omnimorph.gui.widgets.library;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import top.aihmc.omnimorph.gui.widgets.AbstractContainerElement;
import top.aihmc.omnimorph.gui.widgets.Button;

import java.util.ArrayList;
import java.util.List;

/** Compact action rows below the preview, followed by the source-bound visibility option. */
public final class LibraryActionBar extends AbstractContainerElement {
    private final List<Button> actions = new ArrayList<>();
    private Button option;

    public LibraryActionBar(int x, int y, int width, int height) {
        super(x, y, width, height);
    }

    public static int heightFor(int width, int actions) {
        int columns = Math.max(1, (width + 4) / 28);
        return ((actions + columns - 1) / columns) * 28 + 28;
    }

    public <T extends Button> T action(T button) {
        actions.add(button);
        children.add(button);
        return button;
    }

    public <T extends Button> T option(T button) {
        option = button;
        children.add(button);
        return button;
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor gui, int mouseX, int mouseY, float delta) {
        List<Button> visible = actions.stream().filter(Button::isVisible).toList();
        int columns = Math.max(1, (getWidth() + 4) / 28);
        for (int index = 0; index < visible.size(); index++) {
            int row = index / columns;
            int count = Math.min(columns, visible.size() - row * columns);
            int left = getX() + (getWidth() - (count * 28 - 4)) / 2;
            Button button = visible.get(index);
            button.setX(left + index % columns * 28);
            button.setY(getY() + row * 28);
        }
        if (option != null) {
            option.setX(getX());
            option.setY(getY() + ((visible.size() + columns - 1) / columns) * 28);
            option.setWidth(getWidth());
        }
        super.extractRenderState(gui, mouseX, mouseY, delta);
    }
}
