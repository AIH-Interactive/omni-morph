package top.aihmc.omnimorph.gui.widgets.wardrobe;

import top.aihmc.omnimorph.gui.widgets.library.LibraryWorkspaceLayout;

/** Dimensions are Minecraft GUI units. Compact windows expose each pane through navigation. */
public record WardrobeLayout(int leftX, int leftWidth, int previewX, int previewWidth,
                             int treeX, int treeWidth, int top, int height, boolean compact) {
    /** The collection uses a card workspace and a preview on its right, rather than three columns. */
    public static WardrobeLayout collectionOf(int width, int height) {
        if (width < 300) {
            return of(width, height);
        }
        // The vertical wardrobe tabs consume 22 GUI units; the remaining workspace is the catalog's 70/30 layout.
        var workspace = new LibraryWorkspaceLayout(width - 22, height);
        int previewX = workspace.previewX() + 22;
        return new WardrobeLayout(workspace.left() + 22, workspace.contentWidth(),
                previewX, workspace.previewWidth(), previewX, workspace.previewWidth(),
                52, Math.max(24, height - 160), false);
    }

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
