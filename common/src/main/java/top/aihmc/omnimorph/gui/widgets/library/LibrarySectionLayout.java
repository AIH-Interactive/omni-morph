package top.aihmc.omnimorph.gui.widgets.library;

/**
 * Geometry shared by rendering and pointer hits of the model library section navigation:
 * a wardrobe-style column of vertical tabs with its own scrollbar when the height is short.
 */
public record LibrarySectionLayout(int x, int y, int width, int height, int count) {
    public static final int WIDTH = 30;
    public static final int TAB_WIDTH = 22;
    public static final int TAB_HEIGHT = 60;
    public static final int GAP = 4;

    public int tabX(int index) {
        return x;
    }

    public int tabY(int index, int scroll) {
        return y + index * (TAB_HEIGHT + GAP) - scroll;
    }

    public int tabWidth(int index) {
        return TAB_WIDTH;
    }

    public int tabHeight() {
        return TAB_HEIGHT;
    }

    public int contentHeight() {
        return Math.max(0, count * (TAB_HEIGHT + GAP) - GAP);
    }

    public int maximumScroll() {
        return Math.max(0, contentHeight() - height);
    }

    public int clampScroll(int scroll) {
        return Math.max(0, Math.min(maximumScroll(), scroll));
    }

    /** Returns the section under the pointer, or -1 for gaps, the scrollbar and clipped tabs. */
    public int hit(double mouseX, double mouseY, int scroll) {
        if (mouseX < x || mouseX >= x + width || mouseY < y || mouseY >= y + height) {
            return -1;
        }
        for (int index = 0; index < count; index++) {
            int tabY = tabY(index, scroll);
            if (mouseX >= tabX(index) && mouseX < tabX(index) + tabWidth(index)
                    && mouseY >= tabY && mouseY < tabY + tabHeight()) {
                return index;
            }
        }
        return -1;
    }
}
