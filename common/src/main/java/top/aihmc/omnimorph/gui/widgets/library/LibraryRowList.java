package top.aihmc.omnimorph.gui.widgets.library;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import top.aihmc.omnimorph.gui.widgets.Button;
import top.aihmc.omnimorph.gui.widgets.lists.AbstractList;
import top.aihmc.omnimorph.utils.FiguraIdentifier;
import top.aihmc.omnimorph.utils.ui.UIHelper;

import java.util.List;
import java.util.function.Supplier;

/** Shared clipped row selector for file, authorization and management lists. */
public final class LibraryRowList extends AbstractList {
    private static final int TABLE_HEADER_HEIGHT = 20;
    private static final int TABLE_ROW_HEIGHT = 24;
    private static final int VERTICAL_PADDING = 4;

    public static int tablePageCapacity(int height, int maximum) {
        int visibleRows = Math.max(1, (height - TABLE_HEADER_HEIGHT - VERTICAL_PADDING) / TABLE_ROW_HEIGHT);
        return Math.min(Math.max(1, maximum), visibleRows);
    }

    public enum Icon { GRANT, BLOCK, DELETE, RESTORE, EDIT }

    public record Action(Component text, Component tooltip, boolean enabled, Runnable invoke, Icon icon) {
        public Action(Component text, Component tooltip, boolean enabled, Runnable invoke) {
            this(text, tooltip, enabled, invoke, null);
        }
    }

    public record Row(Component text, Component tooltip, boolean enabled, Runnable action,
                      List<Component> cells, Supplier<Identifier> thumbnail, List<Action> actions) {
        public Row(Component text, Component tooltip, boolean enabled, Runnable action) {
            this(text, tooltip, enabled, action, List.of(text), null, List.of());
        }

        public Row {
            cells = List.copyOf(cells);
            actions = List.copyOf(actions);
        }

        public static Row table(List<Component> cells, Component tooltip, boolean enabled, Runnable select) {
            return new Row(Component.empty(), tooltip, enabled, select, cells, null, List.of());
        }
    }
    private List<Row> rows = List.of();
    private int first = -1;
    private int selected = -1;
    private List<Component> headings = List.of();
    private int[] weights = new int[0];
    private boolean cards;

    public void cards() {
        cards = true;
        headings = List.of();
        weights = new int[0];
        first = -1;
    }

    private int rowStep() {
        return cards ? 100 : TABLE_ROW_HEIGHT;
    }

    public LibraryRowList(int x, int y, int width, int height) {
        super(x, y, width, height);
    }

    public void rows(List<Row> value) {
        rows = List.copyOf(value);
        first = -1;
        selected = -1;
        scrollBar.setScrollProgressNoAnim(0);
    }

    public void columns(List<Component> headings, int... weights) {
        if (headings.size() != weights.length || java.util.Arrays.stream(weights).anyMatch(weight -> weight < 1)) {
            throw new IllegalArgumentException("INVALID_TABLE_COLUMNS");
        }
        this.headings = List.copyOf(headings);
        this.weights = weights.clone();
        first = -1;
    }

    private int columnX(int index, int width) {
        int total = java.util.Arrays.stream(weights).sum();
        int before = java.util.Arrays.stream(weights, 0, index).sum();
        return total == 0 ? 0 : width * before / total;
    }

    private void arrange() {
        int headerHeight = headings.isEmpty() ? 0 : TABLE_HEADER_HEIGHT;
        int step = rowStep();
        int maximum = Math.max(0, rows.size() * step - getHeight() + headerHeight + VERTICAL_PADDING);
        int offset = (int) Math.round(scrollBar.getScrollProgress() * maximum);
        scrollBar.setVisible(maximum > 0);
        scrollBar.setActive(maximum > 0);
        if (maximum > 0) {
            scrollBar.setScrollRatio(step, maximum);
        }
        int start = offset / step;
        if (first != start) {
            first = start;
            children.clear();
            children.add(scrollBar);
            int end = Math.min(rows.size(), start + (getHeight() + step - 1) / step + 1);
            for (int i = start; i < end; i++) {
                children.add(new RowButton(i));
            }
        }
        for (int i = 1; i < children.size(); i++) {
            Button button = (Button) children.get(i);
            button.setX(getX() + 3);
            button.setWidth(getWidth() - 20);
            button.setHeight(step - 2);
            button.setY(getY() + headerHeight + 2 + (first + i - 1) * step - offset);
        }
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor gui, int mouseX, int mouseY, float delta) {
        arrange();
        int headerHeight = headings.isEmpty() ? 0 : TABLE_HEADER_HEIGHT;
        gui.enableScissor(getX() + 1, getY() + headerHeight + 1,
                getX() + getWidth() - 1, getY() + getHeight() - 1);
        try {
            for (int i = 1; i < children.size(); i++) {
                ((Button) children.get(i)).extractRenderState(gui,
                        isInsideScissors(mouseX, mouseY) ? mouseX : -1,
                        isInsideScissors(mouseX, mouseY) ? mouseY : -1, delta);
            }
        } finally {
            gui.disableScissor();
        }
        if (!headings.isEmpty()) {
            gui.fill(getX() + 1, getY() + 1, getX() + getWidth() - 1, getY() + 20, 0xd0101820);
            for (int column = 0; column < headings.size(); column++) {
                int left = columnX(column, getWidth() - 20);
                int right = columnX(column + 1, getWidth() - 20);
                UIHelper.renderScrollingText(gui, headings.get(column), getX() + left + 6,
                        getY() + 7, Math.max(1, right - left - 6), 0xffbac6d8);
            }
        }
        scrollBar.extractRenderState(gui, mouseX, mouseY, delta);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        arrange();
        return event.y() >= getY() + (headings.isEmpty() ? 0 : 20)
                && isInsideScissors(event.x(), event.y()) && super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseScrolled(double x, double y, double horizontal, double vertical) {
        return isMouseOver(x, y) && super.mouseScrolled(x, y, horizontal, vertical);
    }

    private final class RowButton extends Button {
        private final int index;
        private final Row row;
        private final List<Button> actions;

        private RowButton(int index) {
            super(0, 0, 20, 22, rows.get(index).text(), rows.get(index).tooltip(), ignored -> {
                selected = index;
                rows.get(index).action().run();
            });
            this.index = index;
            row = rows.get(index);
            setActive(row.enabled());
            actions = row.actions().stream().<Button>map(item -> {
                var button = new Button(0, 0, 20, 20, item.text(), item.tooltip(), ignored -> item.invoke().run()) {
                    @Override
                    protected void renderText(GuiGraphicsExtractor gui, float delta) {
                        if (item.icon() == null) {
                            super.renderText(gui, delta);
                        } else {
                            int color = !isActive() ? 0xff666666 : isHoveredOrFocused() ? 0xff55ffff : 0xffeeeeee;
                            drawIcon(gui, item.icon(), getX() + (getWidth() - 10) / 2, getY() + 5, color, getU());
                        }
                    }
                };
                button.setActive(row.enabled() && item.enabled());
                return button;
            }).toList();
        }

        @Override
        protected void renderDefaultTexture(GuiGraphicsExtractor gui, float delta) {
            if (headings.isEmpty() && !cards) {
                super.renderDefaultTexture(gui, delta);
                return;
            }
            int background = selected == index ? 0x90335566 : isHoveredOrFocused() ? 0x70253340 : 0x50101820;
            gui.fill(getX(), getY(), getX() + getWidth(), getY() + getHeight(), background);
            gui.fill(getX(), getY() + getHeight() - 1, getX() + getWidth(), getY() + getHeight(), 0xff253541);
        }

        @Override
        protected void renderText(GuiGraphicsExtractor gui, float delta) {
            if (cards) {
                int size = Math.max(1, Math.min(getHeight() - 8, getWidth() / 3));
                Identifier texture = row.thumbnail() == null ? null : row.thumbnail().get();
                if (texture != null) {
                    gui.blit(RenderPipelines.GUI_TEXTURED, texture, getX() + 4, getY() + 4,
                            0, 0, size, size, size, size);
                }
                int textX = getX() + size + 10;
                for (int line = 0; line < Math.min(5, row.cells().size()); line++) {
                    UIHelper.renderScrollingText(gui, row.cells().get(line), textX, getY() + 6 + line * 13,
                            Math.max(1, getWidth() - size - 16), getTextColor() | 0xff000000);
                }
                return;
            }
            if (headings.isEmpty()) {
                super.renderText(gui, delta);
                return;
            }
            for (int column = 0; column < Math.min(row.cells().size(), weights.length); column++) {
                int left = columnX(column, getWidth());
                int right = columnX(column + 1, getWidth());
                if (column == 0 && row.thumbnail() != null) {
                    Identifier texture = row.thumbnail().get();
                    if (texture != null) {
                        int size = Math.max(1, Math.min(18, right - left - 4));
                        gui.blit(RenderPipelines.GUI_TEXTURED, texture, getX() + left + 2,
                                getY() + 2, 0, 0, size, size, size, size);
                    }
                } else {
                    UIHelper.renderScrollingText(gui, row.cells().get(column), getX() + left + 3,
                            getY() + 7, Math.max(1, right - left - 6), getTextColor() | 0xff000000);
                }
            }
        }

        private void arrangeActions() {
            if (actions.isEmpty() || !cards && weights.length == 0) {
                return;
            }
            int size = Math.max(1, Math.min(getHeight() - 8, getWidth() / 3));
            int start = cards ? size + 10 : columnX(weights.length - 1, getWidth());
            int span = Math.max(1, (getWidth() - start) / actions.size());
            for (int index = 0; index < actions.size(); index++) {
                Button button = actions.get(index);
                button.setX(getX() + start + index * span);
                button.setY(cards ? getY() + getHeight() - 24 : getY() + 1);
                int preferredWidth = cards && row.actions().get(index).icon() == null ? span - 2 : 20;
                button.setWidth(Math.max(1, Math.min(preferredWidth, span - 2)));
            }
        }

        @Override
        public void extractContents(GuiGraphicsExtractor gui, int mouseX, int mouseY, float delta) {
            super.extractContents(gui, mouseX, mouseY, delta);
            arrangeActions();
            actions.forEach(button -> button.extractRenderState(gui, mouseX, mouseY, delta));
        }

        @Override
        public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
            arrangeActions();
            if (isActive()) {
                for (int action = 0; action < actions.size(); action++) {
                    Button button = actions.get(action);
                    if (button.isMouseOver(event.x(), event.y())) {
                        if (button.isActive() && event.button() == 0) {
                            selected = index;
                            row.action().run();
                            button.run();
                        }
                        return true;
                    }
                }
            }
            return super.mouseClicked(event, doubleClick);
        }
    }

    static void drawIcon(GuiGraphicsExtractor gui, Icon icon, int x, int y, int color, int frame) {
        switch (icon) {
            case GRANT, DELETE, RESTORE, EDIT -> {
                String name = switch (icon) {
                    case GRANT -> "library_grants";
                    case RESTORE -> "library_restore";
                    case EDIT -> "library_edit";
                    default -> "library_delete";
                };
                gui.blit(RenderPipelines.GUI_TEXTURED, new FiguraIdentifier("textures/gui/" + name + ".png"),
                        x - 3, y - 3, frame * 24, 0, 16, 16, 24, 24, 72, 24);
            }
            case BLOCK -> {
                UIHelper.fillOutline(gui, x + 1, y + 1, 8, 8, color);
                for (int step = 1; step < 9; step++) {
                    gui.fill(x + step, y + step, x + step + 1, y + step + 1, color);
                }
            }
        }
    }
}
