package top.aihmc.omnimorph.gui.widgets.wardrobe;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import top.aihmc.omnimorph.gui.widgets.SearchBar;
import top.aihmc.omnimorph.gui.widgets.lists.AbstractList;
import top.aihmc.omnimorph.model.library.BoneTreeState;
import top.aihmc.omnimorph.utils.FiguraText;
import top.aihmc.omnimorph.utils.ui.UIHelper;

/** Read-only model hierarchy; only the search, scrollbar and visible text rows participate in the GUI. */
public final class BoneTreeWidget extends AbstractList {
    private static final int ROW_HEIGHT = 18;
    private final BoneTreeState state;
    private final SearchBar search;
    private long modelVersion;

    public BoneTreeWidget(int x, int y, int width, int height, BoneTreeState state) {
        super(x, y, width, height);
        this.state = state;
        modelVersion = state.modelVersion();
        search = new SearchBar(x + 4, y + 4, width - 8, 20, value -> {
            state.search(value);
            scrollBar.setScrollProgressNoAnim(state.scroll());
        });
        children.add(search);
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
            scrollBar.setScrollProgressNoAnim(0);
        }
        super.tick();
    }

    private int maximumScroll() {
        return Math.max(0, state.rows().size() * ROW_HEIGHT - Math.max(1, getHeight() - 30));
    }

    private int offset() {
        return (int) Math.round(scrollBar.getScrollProgress() * maximumScroll());
    }

    private int indent(BoneTreeState.Row row) {
        return Math.min(row.depth() * 10, Math.max(0, getWidth() / 2 - 20));
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor gui, int mouseX, int mouseY, float delta) {
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
            int end = Math.min(state.rows().size(), (offset + Math.max(1, getHeight() - 28)) / ROW_HEIGHT + 1);
            for (int index = first; index < end; index++) {
                var row = state.rows().get(index);
                int y = getY() + 28 + index * ROW_HEIGHT - offset;
                int x = getX() + 4 + indent(row);
                boolean selected = row.node().id() == state.selected();
                if (selected) {
                    gui.fill(getX() + 2, y, getX() + getWidth() - 14, y + ROW_HEIGHT, 0xff264d5a);
                }
                String arrow = " ";
                if (row.expandable()) {
                    arrow = row.expanded() ? "v" : ">";
                }
                gui.text(font, arrow, x, y + 4, 0xffdddddd);
                Component name = row.node().name().isEmpty() ? FiguraText.of("gui.wardrobe.unnamed_bone")
                        : Component.literal(row.node().name());
                if (row.matched()) {
                    name = name.copy().withStyle(ChatFormatting.YELLOW);
                }
                UIHelper.renderScrollingText(gui, name, x + 12, y + 4,
                        Math.max(1, getWidth() - (x - getX()) - 30), 0xffffffff);
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
        if (super.mouseClicked(event, doubleClick)) {
            return true;
        }
        if (!isInsideScissors(event.x(), event.y()) || event.button() != 0) {
            return false;
        }
        int index = ((int) event.y() - getY() - 28 + offset()) / ROW_HEIGHT;
        if (index < 0 || index >= state.rows().size()) {
            return false;
        }
        search.setFocused(false);
        var row = state.rows().get(index);
        state.select(row.node().id());
        if (row.expandable() && event.x() < getX() + 18 + indent(row)) {
            state.toggle(row.node().id());
        }
        return true;
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
}
