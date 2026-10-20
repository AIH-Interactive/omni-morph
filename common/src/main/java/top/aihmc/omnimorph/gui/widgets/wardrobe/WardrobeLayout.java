package top.aihmc.omnimorph.gui.widgets.wardrobe;

/** Dimensions are Minecraft GUI units. Compact windows expose each pane through navigation. */
public record WardrobeLayout(int leftX, int leftWidth, int previewX, int previewWidth,
                             int treeX, int treeWidth, int top, int height, boolean compact) {
    public static WardrobeLayout of(int width, int height) {
        int available = Math.max(1, width - 36);
        int contentHeight = Math.max(24, height - 160);
        if (width < 560) {
            return new WardrobeLayout(30, available, 30, available, 30, available,
                    54, contentHeight, true);
        }
        int side = Math.min(224, available / 4);
        int previewX = 30 + side + 6;
        int center = available - side * 2 - 12;
        return new WardrobeLayout(30, side, previewX, center, previewX + center + 6,
                side, 54, contentHeight, false);
    }
}
