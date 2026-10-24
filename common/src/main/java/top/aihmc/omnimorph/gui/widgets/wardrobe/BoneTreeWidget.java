package top.aihmc.omnimorph.gui.widgets.wardrobe;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.figuramc.figura.gui.widgets.Button;
import org.figuramc.figura.gui.widgets.ScrollBarWidget;
import org.figuramc.figura.gui.widgets.SearchBar;
import org.figuramc.figura.gui.widgets.lists.AbstractList;
import top.aihmc.omnimorph.model.library.BoneTreeState;
import org.figuramc.figura.utils.FiguraIdentifier;
import org.figuramc.figura.utils.FiguraText;
import org.figuramc.figura.utils.ui.UIHelper;

/** Read-only model hierarchy; only the search, scrollbar and visible text rows participate in the GUI. */
public final class BoneTreeWidget extends AbstractList {
    private static final int ROW_HEIGHT = 18;
    private final BoneTreeState state;
    private final SearchBar search;
    private final ScrollBarWidget horizontalBar;
    private java.util.List<BoneTreeState.Row> measuredRows;
    private int measuredWidth;
    private int horizontalMaximum;
    private long modelVersion;
    private final java.util.function.Consumer<top.aihmc.omnimorph.model.library.OutfitHierarchy.Target> selectionChanged;
    private java.util.function.BiConsumer<top.aihmc.omnimorph.model.library.OutfitHierarchy.Target, MouseButtonEvent> contextAction;

    public void setContextAction(java.util.function.BiConsumer<top.aihmc.omnimorph.model.library.OutfitHierarchy.Target, MouseButtonEvent> action) {
        contextAction = action;
    }

    public BoneTreeWidget(int x, int y, int width, int height, BoneTreeState state) {
        this(x, y, width, height, state, null);
    }

    public BoneTreeWidget(int x, int y, int width, int height, BoneTreeState state, Runnable refresh) {
        this(x, y, width, height, state, refresh, null);
    }

    public BoneTreeWidget(int x, int y, int width, int height, BoneTreeState state, Runnable refresh,
                           java.util.function.Consumer<top.aihmc.omnimorph.model.library.OutfitHierarchy.Target> selectionChanged) {
        super(x, y, width, height);
        this.state = state;
        this.selectionChanged = selectionChanged;
        modelVersion = state.modelVersion();
        search = new SearchBar(x + 4, y + 4, width - (refresh == null ? 8 : 36), 20, value -> {
            state.search(value);
            scrollBar.setScrollProgressNoAnim(state.scroll());
        });
        children.add(search);
        if (refresh != null) {
            children.add(new Button(x + width - 28, y + 2, 24, 24, 0, 0, 24,
                    new FiguraIdentifier("textures/gui/reload.png"), 72, 24,
                    FiguraText.of("gui.wardrobe.refresh_bones"), button -> refresh.run()));
        }
        horizontalBar = new ScrollBarWidget(x + 4, y + height - 12, Math.max(24, width - 22), 10,
                state.horizontalScroll(), false);
        horizontalBar.setVisible(false);
        horizontalBar.setActive(false);
        horizontalBar.setAction(bar -> state.horizontalScroll(bar.getScrollProgress()));
        children.add(horizontalBar);
        search.getField().setValue(state.query());
        scrollBar.setY(y + 28);
        scrollBar.setHeight(Math.max(16, height - 32));
        scrollBar.setScrollProgressNoAnim(state.scroll());
        updateScissors(1, 28, -2, -29);
    }

    @Override
    public void tick() {
        if (modelVersion != state.modelVersion()) {
            modelVersion = state.modelVersion();
            search.getField().setValue(state.query());
            scrollBar.setScrollProgressNoAnim(state.scroll());
            horizontalBar.setScrollProgressNoAnim(state.horizontalScroll());
        }
        super.tick();
    }

    private int maximumScroll() {
        return Math.max(0, state.rows().size() * ROW_HEIGHT - rowArea());
    }

    private int offset() {
        return (int) Math.round(scrollBar.getScrollProgress() * maximumScroll());
    }

    private int indent(BoneTreeState.Row row) {
        return row.depth() * 10;
    }

    private int rowArea() {
        return Math.max(1, getHeight() - 30 - (horizontalBar.isVisible() ? 12 : 0));
    }

    private int horizontalOffset() {
        return (int) Math.round(horizontalBar.getScrollProgress() * horizontalMaximum);
    }

    private void updateScrollbars() {
        if (measuredRows != state.rows() || measuredWidth != getWidth()) {
            measuredRows = state.rows();
            measuredWidth = getWidth();
            int contentWidth = 0;
            var font = Minecraft.getInstance().font;
            for (var row : measuredRows) {
                contentWidth = Math.max(contentWidth, indent(row) + 16 + font.width(name(row)));
            }
            horizontalMaximum = Math.max(0, contentWidth - Math.max(1, getWidth() - 22));
        }
        boolean horizontal = horizontalMaximum > 0;
        horizontalBar.setVisible(horizontal);
        horizontalBar.setActive(horizontal);
        if (!horizontal) {
            horizontalBar.setScrollProgressNoAnim(0);
        }
        horizontalBar.setScrollRatio(10, Math.max(1, horizontalMaximum));
        scrollBar.setHeight(Math.max(16, getHeight() - 32 - (horizontal ? 12 : 0)));
        scrollBar.setVisible(maximumScroll() > 0);
        scrollBar.setActive(maximumScroll() > 0);
        updateScissors(1, 28, -18, -29 - (horizontal ? 12 : 0));
    }

    private static Component name(BoneTreeState.Row row) {
        return row.node().name().isEmpty() ? FiguraText.of("gui.wardrobe.unnamed_bone")
                : Component.literal(row.node().name());
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor gui, int mouseX, int mouseY, float delta) {
        updateScrollbars();
        int maximum = maximumScroll();
        scrollBar.setVisible(maximum > 0);
        scrollBar.setScrollRatio(ROW_HEIGHT, Math.max(1, maximum));
        state.scroll(scrollBar.getScrollProgress());
        UIHelper.blitSliced(gui, getX(), getY(), getWidth(), getHeight(), UIHelper.OUTLINE_FILL);
        super.extractRenderState(gui, mouseX, mouseY, delta);
        enableScissors(gui);
        try {
            var font = Minecraft.getInstance().font;
            int offset = offset();
            int first = offset / ROW_HEIGHT;
            int end = Math.min(state.rows().size(), (offset + rowArea()) / ROW_HEIGHT + 1);
            for (int index = first; index < end; index++) {
                var row = state.rows().get(index);
                int y = getY() + 28 + index * ROW_HEIGHT - offset;
                int x = getX() + 4 + indent(row) - horizontalOffset();
                boolean selected = row.node().id() == state.selected();
                if (selected) {
                    gui.fill(getX() + 2, y, getX() + getWidth() - 14, y + ROW_HEIGHT, 0xff264d5a);
                }
                String arrow = " ";
                if (row.expandable()) {
                    arrow = row.expanded() ? "v" : ">";
                }
                gui.text(font, arrow, x, y + 4, 0xffdddddd);
                Component name = name(row);
                if (row.matched()) {
                    name = name.copy().withStyle(ChatFormatting.YELLOW);
                }
                gui.text(font, name, x + 12, y + 4, 0xffffffff);
                if (isInsideScissors(mouseX, mouseY) && mouseY >= y && mouseY < y + ROW_HEIGHT) {
                    UIHelper.setTooltip(name);
                }
            }
            if (state.rows().isEmpty()) {
                gui.text(font, FiguraText.of(state.query().isEmpty() ? "gui.wardrobe.no_bones" : "gui.wardrobe.no_bone_matches"),
                        getX() + 6, getY() + 34, 0xffaaaaaa);
            }
        } finally {
            gui.disableScissor();
        }
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        updateScrollbars();
        if (super.mouseClicked(event, doubleClick)) {
            return true;
        }
        if (!isInsideScissors(event.x(), event.y()) || event.button() != 0 && event.button() != 1) {
            return false;
        }
        int index = ((int) event.y() - getY() - 28 + offset()) / ROW_HEIGHT;
        if (index < 0 || index >= state.rows().size()) {
            return false;
        }
        search.setFocused(false);
        var row = state.rows().get(index);
        state.select(row.node().id());
        notifySelection();
        if (event.button() == 1) {
            if (contextAction != null) {
                contextAction.accept(state.selectedTarget(), event);
            }
            return true;
        }
        int arrowX = getX() + 4 + indent(row) - horizontalOffset();
        if (row.expandable() && event.x() >= arrowX && event.x() < arrowX + 14) {
            state.toggle(row.node().id());
        }
        return true;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontal, double vertical) {
        if (!isMouseOver(mouseX, mouseY)) {
            return false;
        }
        updateScrollbars();
        if (horizontalBar.isVisible() && (horizontal != 0 || horizontalBar.isMouseOver(mouseX, mouseY))) {
            return horizontalBar.mouseScrolled(mouseX, mouseY, 0, horizontal != 0 ? horizontal : vertical);
        }
        return super.mouseScrolled(mouseX, mouseY, horizontal, vertical);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (search.getField().isFocused()) {
            return super.keyPressed(event);
        }
        var rows = state.rows();
        if (rows.isEmpty()) {
            return super.keyPressed(event);
        }
        int index = 0;
        for (int position = 0; position < rows.size(); position++) {
            if (rows.get(position).node().id() == state.selected()) {
                index = position;
                break;
            }
        }
        var row = rows.get(index);
        if (event.key() == 264 || event.key() == 265) {
            index = Math.clamp(index + (event.key() == 264 ? 1 : -1), 0, rows.size() - 1);
            state.select(rows.get(index).node().id());
            notifySelection();
            scrollBar.setScrollProgressNoAnim(maximumScroll() == 0 ? 0
                    : Math.min(1d, index * (double) ROW_HEIGHT / maximumScroll()));
            return true;
        }
        if (event.key() == 257 || (event.key() == 262 && !row.expanded()) || (event.key() == 263 && row.expanded())) {
            state.toggle(row.node().id());
            return true;
        }
        return super.keyPressed(event);
    }

    private void notifySelection() {
        if (selectionChanged != null) {
            selectionChanged.accept(state.selectedTarget());
        }
    }
}
