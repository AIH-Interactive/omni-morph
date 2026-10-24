package top.aihmc.omnimorph.gui.widgets.library;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.input.InputWithModifiers;
import net.minecraft.network.chat.Component;
import org.figuramc.figura.gui.widgets.Button;
import org.figuramc.figura.gui.widgets.ContextMenu;
import org.figuramc.figura.utils.ui.UIHelper;
import org.figuramc.figura.utils.FiguraText;

import java.util.List;
import java.util.function.IntConsumer;
import java.util.function.IntSupplier;
import java.util.function.Supplier;

/** Dynamic server-backed choices using Figura's existing context-menu control. */
public final class LibraryDropdown extends Button {
    private final Supplier<List<Component>> choices;
    private final IntSupplier selected;
    private final IntConsumer changed;
    private final int pageSize;

    public LibraryDropdown(int x, int y, int width, Supplier<List<Component>> choices,
                            IntSupplier selected, IntConsumer changed) {
        this(x, y, width, choices, selected, changed, Integer.MAX_VALUE);
    }

    /** Bounded menus for attachment trees; ordinary selectors retain their existing presentation. */
    public LibraryDropdown(int x, int y, int width, Supplier<List<Component>> choices,
                            IntSupplier selected, IntConsumer changed, int pageSize) {
        super(x, y, width, 20, Component.empty(), null, ignored -> {});
        if (pageSize < 1) {
            throw new IllegalArgumentException("Dropdown page size must be positive");
        }
        this.choices = choices;
        this.selected = selected;
        this.changed = changed;
        this.pageSize = pageSize;
        updateMessage();
    }

    private void updateMessage() {
        var values = choices.get();
        setMessage(values.isEmpty() ? Component.empty() : values.get(Math.clamp(selected.getAsInt(), 0, values.size() - 1)));
    }

    @Override
    public void onPress(InputWithModifiers input) {
        openPage(Math.max(0, selected.getAsInt()) / pageSize);
    }

    private void openPage(int requestedPage) {
        var values = choices.get();
        int page = Math.clamp(requestedPage, 0, values.isEmpty() ? 0 : (values.size() - 1) / pageSize);
        int start = page * pageSize;
        int end = (int) Math.min(values.size(), (long) start + pageSize);
        var menu = new ContextMenu(this, getWidth());
        if (start > 0) {
            menu.addAction(FiguraText.of("gui.wardrobe.anchor_previous"), null, ignored -> openPage(page - 1));
        }
        for (int index = start; index < end; index++) {
            int option = index;
            Component fullLabel = values.get(index);
            Component label = pageSize == Integer.MAX_VALUE ? fullLabel : Component.literal(
                    Minecraft.getInstance().font.plainSubstrByWidth(fullLabel.getString(), Math.max(1, getWidth() - 12)));
            menu.addAction(label, pageSize == Integer.MAX_VALUE ? null : fullLabel, ignored -> {
                changed.accept(option);
                updateMessage();
                menu.setVisible(false);
            });
        }
        if (end < values.size()) {
            menu.addAction(FiguraText.of("gui.wardrobe.anchor_next"), null, ignored -> openPage(page + 1));
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
