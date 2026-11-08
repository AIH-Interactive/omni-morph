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

    /** X of the compact right-side pane tabs. */
    public static int rightTabX(int width) {
        return width - 28;
    }

    /**
     * Compact outfit under the automatic GUI scale: a narrow list beside one wide pane, which shows
     * the preview or the default information.
     */
    public static WardrobeLayout compactOf(int width, int height) {
        int content = compactAvailable(width);
        int list = Math.min(132, content / 3);
        int paneX = 30 + list + 6;
        int pane = Math.max(1, content - list - 6);
        return new WardrobeLayout(30, list, paneX, pane, paneX, pane, 54, Math.max(24, height - 160), true);
    }

    /** Compact bone tree and related editors: the resource list gives way to two equal panes. */
    public static WardrobeLayout bonesOf(int width, int height) {
        int available = compactAvailable(width);
        int half = Math.max(1, (available - 6) / 2);
        return new WardrobeLayout(30, 0, 30, half, 30 + half + 6, available - half - 6,
                54, Math.max(24, height - 160), true);
    }

    /** Content width between the left wardrobe tabs and the right pane tabs. */
    private static int compactAvailable(int width) {
        return Math.max(1, rightTabX(width) - 4 - 30);
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
