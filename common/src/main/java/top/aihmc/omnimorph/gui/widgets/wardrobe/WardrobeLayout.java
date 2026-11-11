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
        // The navigation rail and its margin consume 22 GUI units beyond the left inset; the remaining workspace is the catalog's 70/30 layout.
        var workspace = new LibraryWorkspaceLayout(width - 22, height);
        int previewX = workspace.previewX() + 22;
        return new WardrobeLayout(workspace.left() + 22, workspace.contentWidth(),
                previewX, workspace.previewWidth(), previewX, workspace.previewWidth(),
                52, Math.max(24, height - 160), false);
    }

    /** Below this width the outfit shows its list beside one pane instead of three columns. */
    public static final int WIDE_WIDTH = 560;
    /** Compact outfit panes start on the top row, which no longer holds pane-switch buttons. */
    private static final int COMPACT_TOP = 28;
    private static final int COMPACT_RIGHT_MARGIN = 28;

    /**
     * Compact outfit under the automatic GUI scale: a narrow list beside one wide pane, which shows
     * the preview or the default information.
     */
    public static WardrobeLayout compactOf(int width, int height) {
        int content = compactAvailable(width);
        int list = Math.min(132, content / 3);
        int paneX = 30 + list + 6;
        int pane = Math.max(1, content - list - 6);
        return new WardrobeLayout(30, list, paneX, pane, paneX, pane, COMPACT_TOP, compactHeight(height), true);
    }

    /** Compact bone tree and related editors: the resource list gives way to two equal panes. */
    public static WardrobeLayout bonesOf(int width, int height) {
        int available = compactAvailable(width);
        int half = Math.max(1, (available - 6) / 2);
        return new WardrobeLayout(30, 0, 30, half, 30 + half + 6, available - half - 6,
                COMPACT_TOP, compactHeight(height), true);
    }

    /** The regular content height, extended by the reclaimed pane-switch row. */
    private static int compactHeight(int height) {
        return Math.max(24, height - 160 + (54 - COMPACT_TOP));
    }

    /**
     * Content width right of the navigation rail. Penpot 01/37 keep a 28 unit right margin where the
     * pane tabs used to be; the pane entries themselves now live in the navigation.
     */
    private static int compactAvailable(int width) {
        return Math.max(1, width - COMPACT_RIGHT_MARGIN - 4 - 30);
    }

    public static WardrobeLayout of(int width, int height) {
        int available = Math.max(1, width - 36);
        int contentHeight = Math.max(24, height - 160);
        if (width < WIDE_WIDTH) {
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
