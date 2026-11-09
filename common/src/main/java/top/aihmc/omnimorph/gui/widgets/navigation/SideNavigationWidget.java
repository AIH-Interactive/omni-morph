package top.aihmc.omnimorph.gui.widgets.navigation;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;
import org.figuramc.figura.gui.widgets.AbstractContainerElement;
import org.figuramc.figura.utils.FiguraIdentifier;
import org.figuramc.figura.utils.FiguraText;
import org.figuramc.figura.utils.IOUtils;

import java.util.List;
import java.util.function.Consumer;

/**
 * The shared left navigation: a collapsed icon rail with the five parents, a top bar showing the
 * current path, and a tree that temporarily expands over the content while the pointer is inside.
 * The content layout never changes when the tree expands.
 */
public final class SideNavigationWidget extends AbstractContainerElement {
    private static final int RAIL_COLOR = 0xff0b1220;
    private static final int TOP_BAR_COLOR = 0xff111b29;
    private static final int OVERLAY_COLOR = 0xff0f1a2b;
    private static final int SELECTED_COLOR = 0xff233d60;
    private static final int HOVER_COLOR = 0xff17263b;
    private static final int SELECTED_TEXT_COLOR = 0xff93c5fd;
    private static final int TEXT_COLOR = 0xffcbd5e1;
    private static final int HEADER_COLOR = 0xfff1f5f9;
    private static final int ARROW_COLOR = 0xff64748b;
    private static final int DIVIDER_COLOR = 0xff27364a;
    private static final int SCROLL_TRACK_COLOR = 0xff1f2d40;
    private static final int SCROLL_THUMB_COLOR = 0xff64748b;
    private static final int ICON_SIZE = 16;

    private static final Identifier PANEL_ICONS = new FiguraIdentifier("textures/gui/panels.png");
    private static final Identifier LIBRARY_ICON = new FiguraIdentifier("textures/gui/library.png");
    private static final Identifier LOGO_ICON = new FiguraIdentifier("textures/gui/icon.png");

    /** Folding is kept for the session, so every page shows the same tree. */
    private static final NavigationTree TREE = new NavigationTree();

    private final NavigationRoute current;
    private final boolean railVisible;
    private final Consumer<NavigationRoute> navigate;
    private final Font font = Minecraft.getInstance().font;
    private boolean expanded;
    private int scrollX;
    private int scrollY;

    /**
     * @param railVisible false for pages laid out without the rail: only the top bar is drawn
     * @param navigate    receives the destination page of a clicked entry
     */
    public SideNavigationWidget(int screenWidth, int screenHeight, NavigationRoute current,
                                boolean railVisible, Consumer<NavigationRoute> navigate) {
        super(0, 0, screenWidth, screenHeight);
        this.current = current;
        this.railVisible = railVisible;
        this.navigate = navigate;
    }

    public static NavigationTree tree() {
        return TREE;
    }

    private NavigationLayout layout() {
        return NavigationLayout.of(TREE, getWidth(), getHeight(), key -> font.width(FiguraText.of(key)));
    }

    /** True when the pointer is over the rail or the expanded tree, which then receive all input. */
    public boolean capturesPointer(double mouseX, double mouseY) {
        if (!railVisible || !isVisible() || mouseY < 0 || mouseY >= getHeight()) {
            return false;
        }
        int right = expanded ? layout().expandedWidth() : NavigationLayout.RAIL_WIDTH;
        return mouseX >= 0 && mouseX < right;
    }

    @Override
    public boolean isMouseOver(double mouseX, double mouseY) {
        return capturesPointer(mouseX, mouseY);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor gui, int mouseX, int mouseY, float delta) {
        if (!isVisible()) {
            return;
        }
        // Enter the rail to expand; leave the tree to collapse.
        expanded = railVisible && capturesPointer(mouseX, mouseY);
        renderTopBar(gui);
        if (!railVisible) {
            return;
        }
        if (expanded) {
            renderTree(gui, mouseX, mouseY);
        } else {
            renderRail(gui);
        }
    }

    private void renderTopBar(GuiGraphicsExtractor gui) {
        int left = railVisible ? NavigationLayout.RAIL_WIDTH : 0;
        gui.fill(left, 0, getWidth(), NavigationLayout.TOP_BAR_HEIGHT, TOP_BAR_COLOR);
        gui.text(font, path(), left + 10, (NavigationLayout.TOP_BAR_HEIGHT - font.lineHeight) / 2 + 1,
                TEXT_COLOR, false);
    }

    private Component path() {
        List<NavigationRoute> routes = TREE.breadcrumb(current);
        MutableComponent text = Component.empty();
        for (int index = 0; index < routes.size(); index++) {
            if (index > 0) {
                text.append(" / ");
            }
            text.append(label(routes.get(index)));
        }
        return text;
    }

    private static Component label(NavigationRoute route) {
        return FiguraText.of(NavigationTree.nodeOf(route).translationKey());
    }

    private void renderRail(GuiGraphicsExtractor gui) {
        var layout = layout();
        gui.fill(0, 0, NavigationLayout.RAIL_WIDTH, getHeight(), RAIL_COLOR);
        gui.blit(RenderPipelines.GUI_TEXTURED, LOGO_ICON, 7, 6, 0, 0, 14, 14, 20, 20, 60, 20);
        NavigationRoute selected = TREE.topLevelOf(current);
        List<NavigationRoute> parents = TREE.topLevel();
        for (int index = 0; index < parents.size(); index++) {
            int y = layout.railItemY(index);
            if (parents.get(index) == selected) {
                gui.fill(4, y + 1, 25, y + 21, SELECTED_COLOR);
            }
            renderIcon(gui, parents.get(index), 6, y + 3);
        }
    }

    private void renderTree(GuiGraphicsExtractor gui, int mouseX, int mouseY) {
        var layout = layout();
        scrollX = layout.clampScrollX(scrollX);
        scrollY = layout.clampScrollY(scrollY);
        int width = layout.expandedWidth();
        gui.fill(0, 0, width, getHeight(), OVERLAY_COLOR);
        gui.text(font, FiguraText.of("gui.navigation.title"), 10, 8, HEADER_COLOR, false);
        gui.fill(6, NavigationLayout.TOP_BAR_HEIGHT - 1, width - 6, NavigationLayout.TOP_BAR_HEIGHT, DIVIDER_COLOR);

        var rows = TREE.visibleRows();
        int hovered = layout.rowAt(mouseX, mouseY, scrollX, scrollY);
        gui.enableScissor(0, NavigationLayout.ROWS_TOP, width, layout.rowsBottom());
        try {
            for (int index = 0; index < rows.size(); index++) {
                renderRow(gui, layout, rows.get(index), index, index == hovered);
            }
        } finally {
            gui.disableScissor();
        }
        renderScrollbars(gui, layout);
    }

    private void renderRow(GuiGraphicsExtractor gui, NavigationLayout layout, NavigationTree.Node row,
                           int index, boolean hovered) {
        int y = layout.rowY(index, scrollY);
        if (y + NavigationLayout.ROW_HEIGHT <= NavigationLayout.ROWS_TOP || y >= layout.rowsBottom()) {
            return;
        }
        boolean selected = row.route() == current;
        int rowRight = layout.expandedWidth() - (layout.maximumScrollY() > 0 ? NavigationLayout.SCROLLBAR + 1 : 2);
        if (selected) {
            gui.fill(3, y + 1, rowRight, y + NavigationLayout.ROW_HEIGHT, SELECTED_COLOR);
        } else if (hovered) {
            gui.fill(3, y + 1, rowRight, y + NavigationLayout.ROW_HEIGHT, HOVER_COLOR);
        }
        int textY = y + (NavigationLayout.ROW_HEIGHT - font.lineHeight) / 2 + 1;
        if (row.depth() == 0) {
            renderIcon(gui, row.route(), 6 - scrollX, y + 1);
        }
        gui.text(font, label(row.route()), NavigationLayout.textX(row.depth()) - scrollX, textY,
                selected ? SELECTED_TEXT_COLOR : TEXT_COLOR, false);
        if (TREE.isBranch(row.route())) {
            String arrow = TREE.isCollapsed(row.route()) ? ">" : "v";
            int arrowX = layout.arrowX() - scrollX + (NavigationLayout.ARROW_WIDTH - font.width(arrow)) / 2;
            gui.text(font, arrow, arrowX, textY, ARROW_COLOR, false);
        }
    }

    private void renderScrollbars(GuiGraphicsExtractor gui, NavigationLayout layout) {
        int width = layout.expandedWidth();
        int viewport = layout.rowsBottom() - NavigationLayout.ROWS_TOP;
        if (layout.maximumScrollY() > 0) {
            int trackX = width - NavigationLayout.SCROLLBAR;
            int content = viewport + layout.maximumScrollY();
            int thumb = Math.max(8, viewport * viewport / content);
            int thumbY = NavigationLayout.ROWS_TOP + (viewport - thumb) * scrollY / layout.maximumScrollY();
            gui.fill(trackX, NavigationLayout.ROWS_TOP, trackX + 2, layout.rowsBottom(), SCROLL_TRACK_COLOR);
            gui.fill(trackX, thumbY, trackX + 2, thumbY + thumb, SCROLL_THUMB_COLOR);
        }
        if (layout.maximumScrollX() > 0) {
            int trackY = getHeight() - NavigationLayout.SCROLLBAR;
            int thumb = Math.max(8, width * width / layout.contentWidth());
            int thumbX = (width - thumb) * scrollX / layout.maximumScrollX();
            gui.fill(0, trackY + 1, width, trackY + 3, SCROLL_TRACK_COLOR);
            gui.fill(thumbX, trackY + 1, thumbX + thumb, trackY + 3, SCROLL_THUMB_COLOR);
        }
    }

    /** Reuses the project's existing panel icons; the library has its own texture. */
    private static void renderIcon(GuiGraphicsExtractor gui, NavigationRoute parent, int x, int y) {
        if (parent == NavigationRoute.LIBRARY) {
            gui.blit(RenderPipelines.GUI_TEXTURED, LIBRARY_ICON, x, y, 0, 0, ICON_SIZE, ICON_SIZE, 20, 20, 20, 20);
            return;
        }
        int iconIndex = switch (parent) {
            case WARDROBE -> 2;
            case PERMISSIONS -> 3;
            case SETTINGS -> 4;
            default -> 5;
        };
        gui.blit(RenderPipelines.GUI_TEXTURED, PANEL_ICONS, x, y, iconIndex * 20, 0, ICON_SIZE, ICON_SIZE,
                20, 20, 140, 20);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (!capturesPointer(event.x(), event.y())) {
            return false;
        }
        if (event.button() != 0) {
            return true;
        }
        var layout = layout();
        if (expanded) {
            int index = layout.rowAt(event.x(), event.y(), scrollX, scrollY);
            if (index >= 0) {
                NavigationRoute route = TREE.visibleRows().get(index).route();
                if (layout.isArrow(index, event.x(), scrollX)) {
                    TREE.toggle(route);
                } else {
                    open(route);
                }
            }
            return true;
        }
        int item = layout.railItemAt(event.x(), event.y());
        if (item >= 0) {
            open(TREE.topLevel().get(item));
        }
        return true;
    }

    private void open(NavigationRoute route) {
        NavigationRoute destination = TREE.destination(route);
        if (destination != current) {
            navigate.accept(destination);
        }
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontal, double vertical) {
        if (!expanded || !capturesPointer(mouseX, mouseY)) {
            return false;
        }
        var layout = layout();
        boolean sideways = horizontal != 0 || layout.maximumScrollY() == 0 || IOUtils.hasShiftDown();
        if (sideways) {
            double amount = horizontal != 0 ? horizontal : vertical;
            scrollX = layout.clampScrollX(scrollX - (int) Math.round(amount * 16));
        } else {
            scrollY = layout.clampScrollY(scrollY - (int) Math.round(vertical * NavigationLayout.ROW_HEIGHT));
        }
        return true;
    }

    /** Ctrl+1..5 opens a parent; Ctrl+Tab and Ctrl+Shift+Tab cycle the parents. */
    public boolean cycleTab(int keyCode) {
        if (!railVisible || !IOUtils.hasControlDown()) {
            return false;
        }
        List<NavigationRoute> parents = TREE.topLevel();
        int index = -1;
        if (keyCode >= 49 && keyCode < 49 + parents.size()) {
            index = keyCode - 49;
        } else if (keyCode == 258) {
            int selected = parents.indexOf(TREE.topLevelOf(current));
            index = Math.floorMod(selected + (IOUtils.hasShiftDown() ? -1 : 1), parents.size());
        }
        if (index < 0) {
            return false;
        }
        open(parents.get(index));
        return true;
    }
}
