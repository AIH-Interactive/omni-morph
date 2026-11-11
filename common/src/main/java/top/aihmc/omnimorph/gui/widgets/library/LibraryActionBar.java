package top.aihmc.omnimorph.gui.widgets.library;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import org.figuramc.figura.gui.widgets.AbstractContainerElement;
import org.figuramc.figura.gui.widgets.Button;
import org.figuramc.figura.gui.widgets.ContextMenu;
import org.figuramc.figura.utils.FiguraText;
import org.figuramc.figura.utils.ui.UIHelper;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Compact action rows below the preview, followed by the source-bound visibility option.
 * With a budget, the bar keeps one row and moves the remaining actions into a text menu.
 */
public final class LibraryActionBar extends AbstractContainerElement {
    private final List<Button> actions = new ArrayList<>();
    private Button option;
    private int budget = Integer.MAX_VALUE;
    private Button more;

    public LibraryActionBar(int x, int y, int width, int height) {
        super(x, y, width, height);
    }

    public static int heightFor(int width, int actions) {
        int columns = Math.max(1, (width + 4) / 28);
        return ((actions + columns - 1) / columns) * 28 + 28;
    }

    /** Height of a budgeted bar: one button row, plus the option row when the bar has one. */
    public static int budgetedHeight(boolean option) {
        return option ? 52 : 24;
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

    /** Limits the bar to this many buttons, including the overflow menu button. */
    public void budget(int maximum) {
        budget = Math.max(2, maximum);
    }

    private List<Button> overflowActions() {
        var buttons = visibleActions().stream().filter(Objects::nonNull).toList();
        return buttons.size() <= budget ? List.of() : buttons.subList(budget - 1, buttons.size());
    }

    /** The slots currently placed in the bar, in display order; null entries are dividers. */
    private List<Button> slots() {
        var visible = visibleActions();
        if (overflowActions().isEmpty()) {
            return visible;
        }
        // A budgeted row drops dividers so every slot holds an action.
        var slots = new ArrayList<>(visible.stream().filter(Objects::nonNull).limit(budget - 1).toList());
        slots.add(moreButton());
        return slots;
    }

    /** The action buttons currently placed in the bar, in display order. */
    public List<Button> shownButtons() {
        return slots().stream().filter(Objects::nonNull).toList();
    }

    /** The text menu of actions beyond the budget, or null when every action fits. */
    public ContextMenu overflowMenu() {
        var overflow = overflowActions();
        if (overflow.isEmpty()) {
            return null;
        }
        var menu = new ContextMenu(moreButton());
        for (Button button : overflow) {
            menu.addAction(label(button), button.tooltip(), ignored -> {
                menu.setVisible(false);
                if (button.isActive()) {
                    button.onPress();
                }
            });
            ((Button) menu.getEntries().getLast()).setActive(button.isActive());
        }
        return menu;
    }

    /** Icon buttons keep their name in the message or, for texture buttons, the first tooltip line. */
    private static Component label(Button button) {
        if (!button.getMessage().getString().isEmpty()) {
            return button.getMessage();
        }
        Component tooltip = button.tooltip();
        if (tooltip == null) {
            return Component.empty();
        }
        String text = tooltip.getString();
        int line = text.indexOf('\n');
        return line < 0 ? tooltip : Component.literal(text.substring(0, line));
    }

    private Button moreButton() {
        if (more == null) {
            more = new MoreButton(FiguraText.of("gui.library.more_actions"), button -> openMenu());
            children.add(more);
        }
        return more;
    }

    private void openMenu() {
        var menu = overflowMenu();
        if (menu == null) {
            return;
        }
        // Open downwards when the menu fits below the button, otherwise above it.
        int screenHeight = Minecraft.getInstance().getWindow().getGuiScaledHeight();
        int below = more.getY() + more.getHeight();
        int y = below + menu.getHeight() <= screenHeight ? below : more.getY() - menu.getHeight();
        menu.setX(more.getX());
        menu.setY(Math.max(0, y));
        menu.setVisible(true);
        UIHelper.setContext(menu);
    }

    public <T extends Button> T option(T button) {
        option = button;
        children.add(button);
        return button;
    }

    @Override
    public void render(GuiGraphics gui, int mouseX, int mouseY, float delta) {
        List<Button> visible = slots();
        boolean budgeted = more != null && visible.contains(more);
        if (more != null) {
            more.setVisible(budgeted);
        }
        // Overflow actions stay owned by the screen, but only the menu can reach them.
        for (Button button : overflowActions()) {
            button.setX(-10000);
        }
        int columns = budgeted ? visible.size() : Math.max(1, (getWidth() + 4) / 28);
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
        super.render(gui, mouseX, mouseY, delta);
    }

    /** Self-drawn menu icon: three short rows, each a small square followed by a line. */
    private static final class MoreButton extends Button {
        private MoreButton(Component tooltip, OnPress action) {
            super(0, 0, 24, 24, Component.empty(), tooltip, action);
        }

        @Override
        protected void renderText(GuiGraphics gui, float delta) {
            int color = UIHelper.adjustColor(getTextColor()) | 0xff000000;
            for (int row = 0; row < 3; row++) {
                int y = getY() + 6 + row * 5;
                gui.fill(getX() + 5, y, getX() + 7, y + 2, color);
                gui.fill(getX() + 10, y, getX() + 19, y + 2, color);
            }
        }
    }
}
