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

    /** A divider occupies one toolbar slot and participates in the existing responsive wrapping. */
    public void separator() {
        if (!actions.isEmpty() && actions.getLast() != null) {
            actions.add(null);
        }
    }

    private List<Button> visibleActions() {
        var visible = new ArrayList<Button>();
        boolean separatorPending = false;
        for (Button button : actions) {
            if (button == null) {
                separatorPending = !visible.isEmpty();
            } else if (button.isVisible()) {
                if (separatorPending) {
                    visible.add(null);
                    separatorPending = false;
                }
                visible.add(button);
            }
        }
        return visible;
    }

    public <T extends Button> T option(T button) {
        option = button;
        children.add(button);
        return button;
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor gui, int mouseX, int mouseY, float delta) {
        List<Button> visible = visibleActions();
        int columns = Math.max(1, (getWidth() + 4) / 28);
        for (int index = 0; index < visible.size(); index++) {
            int row = index / columns;
            int count = Math.min(columns, visible.size() - row * columns);
            int left = getX() + (getWidth() - (count * 28 - 4)) / 2;
            Button button = visible.get(index);
            int x = left + index % columns * 28;
            int y = getY() + row * 28;
            if (button == null) {
                gui.fill(x + 11, y + 3, x + 12, y + 21, 0xff58666c);
            } else {
                button.setX(x);
                button.setY(y);
            }
        }
        if (option != null) {
            option.setX(getX());
            option.setY(getY() + ((visible.size() + columns - 1) / columns) * 28);
            option.setWidth(getWidth());
        }
        super.extractRenderState(gui, mouseX, mouseY, delta);
    }
}
