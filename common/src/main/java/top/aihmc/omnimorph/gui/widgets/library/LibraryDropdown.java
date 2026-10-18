package top.aihmc.omnimorph.gui.widgets.library;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.InputWithModifiers;
import net.minecraft.network.chat.Component;
import top.aihmc.omnimorph.gui.widgets.Button;
import top.aihmc.omnimorph.gui.widgets.ContextMenu;
import top.aihmc.omnimorph.utils.ui.UIHelper;

import java.util.List;
import java.util.function.IntConsumer;
import java.util.function.IntSupplier;
import java.util.function.Supplier;

/** Dynamic server-backed choices using Figura's existing context-menu control. */
public final class LibraryDropdown extends Button {
    private final Supplier<List<Component>> choices;
    private final IntSupplier selected;
    private final IntConsumer changed;

    public LibraryDropdown(int x, int y, int width, Supplier<List<Component>> choices,
                            IntSupplier selected, IntConsumer changed) {
        super(x, y, width, 20, Component.empty(), null, ignored -> {});
        this.choices = choices;
        this.selected = selected;
        this.changed = changed;
        updateMessage();
    }

    private void updateMessage() {
        var values = choices.get();
        setMessage(values.isEmpty() ? Component.empty() : values.get(Math.clamp(selected.getAsInt(), 0, values.size() - 1)));
    }

    @Override
    public void onPress(InputWithModifiers input) {
        var values = choices.get();
        var menu = new ContextMenu(this, getWidth());
        for (int index = 0; index < values.size(); index++) {
            int option = index;
            menu.addAction(values.get(index), null, ignored -> {
                changed.accept(option);
                updateMessage();
                menu.setVisible(false);
            });
        }
        if (!values.isEmpty()) {
            menu.setX(getX());
            menu.setY(getY() + getHeight());
            menu.setVisible(true);
            UIHelper.setContext(menu);
        }
    }

    @Override
    protected void renderText(GuiGraphicsExtractor gui, float delta) {
        updateMessage();
        UIHelper.renderCenteredScrollingText(gui, getMessage(), getX() + 3, getY(),
                Math.max(1, getWidth() - 16), getHeight(), getTextColor() | 0xff000000);
        int x = getX() + getWidth() - 10;
        int color = getTextColor() | 0xff000000;
        gui.fill(x, getY() + 8, x + 5, getY() + 9, color);
        gui.fill(x + 1, getY() + 9, x + 4, getY() + 10, color);
        gui.fill(x + 2, getY() + 10, x + 3, getY() + 11, color);
    }
}
