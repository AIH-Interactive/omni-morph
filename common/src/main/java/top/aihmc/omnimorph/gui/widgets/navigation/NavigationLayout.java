package top.aihmc.omnimorph.gui.widgets.navigation;

import java.util.List;
import java.util.function.ToIntFunction;

/**
 * Geometry shared by rendering and pointer hits of the left navigation, in logical GUI units.
 * The Penpot boards use 2x GUI coordinates: a 56 wide rail and a 280 wide expanded maximum.
 */
public record NavigationLayout(int screenHeight, List<Boolean> branchRows, int contentWidth, int expandedWidth) {
    public static final int RAIL_WIDTH = 28;
    public static final int MAX_EXPANDED_WIDTH = 140;
    public static final int TOP_BAR_HEIGHT = 24;
    public static final int ROWS_TOP = 28;
    public static final int ROW_HEIGHT = 18;
    public static final int RAIL_ITEMS_TOP = 27;
    public static final int RAIL_ITEM_HEIGHT = 24;
    public static final int RAIL_ITEMS = 5;
    public static final int ARROW_WIDTH = 14;
    public static final int SCROLLBAR = 4;
    private static final int TEXT_GAP = 4;
    private static final int RIGHT_PADDING = 6;

    /** Label start for each level; parent icons occupy the first column. */
    public static int textX(int depth) {
        return switch (depth) {
            case 0 -> 26;
            case 1 -> 36;
            default -> 48;
        };
    }

    /**
     * Measures the visible rows. {@code textWidth} receives each row's translation key and returns
     * the rendered width of its label.
     */
    public static NavigationLayout of(NavigationTree tree, int screenWidth, int screenHeight,
                                      ToIntFunction<String> textWidth) {
        var rows = tree.visibleRows();
        int content = RAIL_WIDTH;
        for (var row : rows) {
            int arrow = tree.isBranch(row.route()) ? ARROW_WIDTH : 0;
            int labelRight = textX(row.depth()) + textWidth.applyAsInt(row.translationKey());
            content = Math.max(content, labelRight + TEXT_GAP + arrow + RIGHT_PADDING);
        }
        int expanded = Math.min(MAX_EXPANDED_WIDTH, Math.min(content, Math.max(RAIL_WIDTH, screenWidth)));
        List<Boolean> branches = rows.stream().map(row -> tree.isBranch(row.route())).toList();
        return new NavigationLayout(screenHeight, branches, content, expanded);
    }

    public int rowCount() {
        return branchRows.size();
    }

    public int maximumScrollX() {
        return Math.max(0, contentWidth - expandedWidth);
    }

    /** Bottom of the row viewport, above the horizontal scrollbar when one is shown. */
    public int rowsBottom() {
        return screenHeight - (maximumScrollX() > 0 ? SCROLLBAR : 0);
    }

    public int maximumScrollY() {
        return Math.max(0, ROWS_TOP + rowCount() * ROW_HEIGHT - rowsBottom());
    }

    public int clampScrollX(int scroll) {
        return Math.max(0, Math.min(maximumScrollX(), scroll));
    }

    public int clampScrollY(int scroll) {
        return Math.max(0, Math.min(maximumScrollY(), scroll));
    }

    public int rowY(int index, int scrollY) {
        return ROWS_TOP + index * ROW_HEIGHT - scrollY;
    }

    /** Visible row under the pointer, or -1 outside the overlay, in the header or on a scrollbar. */
    public int rowAt(double mouseX, double mouseY, int scrollX, int scrollY) {
        if (mouseX < 0 || mouseX >= expandedWidth || mouseY < ROWS_TOP || mouseY >= rowsBottom()) {
            return -1;
        }
        if (maximumScrollY() > 0 && mouseX >= expandedWidth - SCROLLBAR) {
            return -1;
        }
        int index = (int) Math.floor((mouseY - ROWS_TOP + scrollY) / ROW_HEIGHT);
        return index >= 0 && index < rowCount() ? index : -1;
    }

    /** Content x of the fold arrows, aligned at the right edge of the measured content. */
    public int arrowX() {
        return Math.max(expandedWidth, contentWidth) - RIGHT_PADDING - ARROW_WIDTH;
    }

    /** True when the pointer is on a branch row's fold arrow rather than its label. */
    public boolean isArrow(int rowIndex, double mouseX, int scrollX) {
        if (rowIndex < 0 || rowIndex >= rowCount() || !branchRows.get(rowIndex)) {
            return false;
        }
        return mouseX + scrollX >= arrowX();
    }

    public int railItemY(int index) {
        return RAIL_ITEMS_TOP + index * RAIL_ITEM_HEIGHT;
    }

    /** Parent index on the collapsed rail under the pointer, or -1. */
    public int railItemAt(double mouseX, double mouseY) {
        if (mouseX < 0 || mouseX >= RAIL_WIDTH || mouseY < RAIL_ITEMS_TOP) {
            return -1;
        }
        int index = (int) Math.floor((mouseY - RAIL_ITEMS_TOP) / RAIL_ITEM_HEIGHT);
        return index < RAIL_ITEMS ? index : -1;
    }
}
