package top.aihmc.omnimorph.model.library;

import top.aihmc.omnimorph.gui.widgets.library.ModelLibraryLayout;
import top.aihmc.omnimorph.gui.widgets.library.LibraryWorkspaceLayout;
import top.aihmc.omnimorph.gui.widgets.library.LibraryActionBar;
import top.aihmc.omnimorph.gui.widgets.library.LibraryRowList;
import top.aihmc.omnimorph.utils.FileSizeFormatter;

public final class ModelLibraryLayoutSmokeTest {
    public static void main(String[] args) {
        require(LibraryRowList.tablePageCapacity(402, 64) == 15,
                "management table fills the available height after its header and padding");
        require(LibraryRowList.tablePageCapacity(402, 8) == 8,
                "management respects the older backend maximum");
        require(LibraryRowList.tablePageCapacity(239, 64) == 8
                        && LibraryRowList.tablePageCapacity(240, 64) == 9,
                "management requests only complete rows at the next-row boundary");
        require(LibraryRowList.tablePageCapacity(24, 64) == 1
                        && LibraryRowList.tablePageCapacity(10000, 64) == 64,
                "management capacity stays within minimum and server bounds");
        require(FileSizeFormatter.format(0, "字节").equals("0 字节"), "zero bytes");
        require(FileSizeFormatter.format(1023, "字节").equals("1023 字节"), "byte unit boundary");
        require(FileSizeFormatter.format(1024, "bytes").equals("1 KB"), "KB boundary");
        require(FileSizeFormatter.format(1536, "bytes").equals("1.5 KB"), "fractional size");
        require(FileSizeFormatter.format(1048576, "bytes").equals("1 MB"), "MB boundary");
        require(FileSizeFormatter.format(67108864, "bytes").equals("64 MB"), "server upload limit");
        require(FileSizeFormatter.format(1073741824, "bytes").equals("1 GB"), "GB boundary");
        require(FileSizeFormatter.format(Long.MAX_VALUE, "bytes").endsWith(" GB"), "large limits stay bounded");
        for (int width : new int[]{300, 426, 640, 960, 1280}) {
            for (int height : new int[]{220, 240, 360, 540, 720}) {
                var workspace = new LibraryWorkspaceLayout(width, height);
                require(workspace.left() >= 4 && workspace.previewX() >= workspace.left() + workspace.contentWidth() + 4,
                        "content and preview columns never overlap");
                require(workspace.previewX() + workspace.previewWidth() <= width - 4 && workspace.previewWidth() >= 72,
                        "preview and its controls remain inside the supported viewport");
                require(workspace.bodyY() + workspace.bodyHeight() < workspace.footerY()
                                && workspace.top() + workspace.previewHeight() < workspace.footerY()
                                && workspace.footerY() + 20 <= height - 4,
                        "scrolling content and preview leave a separate usable footer");
                int actionsHeight = LibraryActionBar.heightFor(workspace.previewWidth(), 6);
                int actionsY = height - 28 - actionsHeight;
                require(actionsY >= 100 && actionsY + actionsHeight <= height - 28,
                        "icon actions and the visibility option leave a separate preview and footer");
            }
        }
        // Fixed cards (Penpot 54): 90x108 logical units, a square 90 cover and an 18 tall name row.
        for (int width : new int[]{1, 89, 90, 200, 282, 394, 1000}) {
            for (int height : new int[]{1, 108, 400}) {
                var fixed = ModelLibraryLayout.of(width, height, 8);
                require(fixed.cardWidth() == 90 && fixed.cardHeight() == 108 && fixed.imageSize() == 90,
                        "card size never follows the available area: " + width + "x" + height);
            }
        }
        // Columns and rows: floor((A + G) / (W + G)) and floor((A + G) / (H + G)) with a gap of 6.
        int[][] capacities = {
                {282, 168, 3, 1}, {282, 276, 3, 2}, {394, 276, 4, 2}, // Penpot 55 examples
                {281, 168, 2, 1}, {186, 107, 2, 1}, {185, 222, 1, 2}, // just below a column or row boundary
                {500, 500, 5, 4}, {731, 333, 7, 2}, {1000, 600, 10, 5}}; // sizes not shown in the prototype
        for (int[] example : capacities) {
            var grid = ModelLibraryLayout.of(example[0], example[1], 0);
            require(grid.columns() == example[2] && grid.pageCapacity(64) == example[2] * example[3],
                    "capacity of " + example[0] + "x" + example[1] + ": " + grid.columns() + " columns, "
                            + grid.pageCapacity(64) + " models");
        }
        require(ModelLibraryLayout.of(1000, 900, 0).pageCapacity(8) == 8, "older backend maximum is respected");
        require(ModelLibraryLayout.of(10000, 10000, 0).pageCapacity(64) == 64, "large screens stay within the server bound");
        require(ModelLibraryLayout.of(60, 60, 0).columns() == 1 && ModelLibraryLayout.of(60, 60, 0).pageCapacity(64) == 1,
                "an area smaller than one card still shows one unscaled card");
        for (int width : new int[]{90, 128, 282, 486, 732, 1470}) {
            for (int height : new int[]{1, 108, 116, 230, 800}) {
                var layout = ModelLibraryLayout.of(width, height, 8);
                for (int scroll : new int[]{0, layout.maximumScroll() / 2, layout.maximumScroll()}) {
                    require(layout.hit(-1, 0, scroll) == -1 && layout.hit(0, -1, scroll) == -1
                            && layout.hit(width, 0, scroll) == -1 && layout.hit(0, height, scroll) == -1,
                            "viewport clipping also clips pointer hits");
                    for (int index = 0; index < 8; index++) {
                        int x = layout.x(index);
                        int y = layout.y(index, scroll);
                        require(x >= 0 && x + layout.cardWidth() <= width, "card fits its column");
                        int visibleY = Math.max(0, y);
                        if (visibleY < height && visibleY < y + ModelLibraryLayout.CARD_HEIGHT) {
                            require(layout.hit(x, visibleY, scroll) == index, "render/hit geometry agrees");
                        }
                    }
                    require(layout.first(scroll) <= layout.endWithPrefetch(scroll)
                            && layout.endWithPrefetch(scroll) <= 8, "bounded virtualization");
                }
            }
        }
        var old = ModelLibraryLayout.of(394, 108, 8);
        int anchor = old.first(114);
        var resized = ModelLibraryLayout.of(186, 108, 8);
        require(resized.first(resized.scrollTo(anchor)) == anchor, "resize retains first visible model index");
        var row = ModelLibraryLayout.of(282, 108, 8);
        require(row.endWithPrefetch(0) == 6, "one prefetch row after a fully visible row");
        require(row.hit(92, 10, 0) == -1 && row.hit(10, 110, 0) == -1, "gaps are not card hits");
        require(ModelLibraryLayout.of(282, 116, 0).hit(0, 0, 0) == -1, "empty grid");
        // A changed capacity keeps the first model of the current page on screen.
        require(ModelLibraryLayout.pageKeepingFirst(3, 6, 3) == 5, "smaller pages move forward to the same model");
        require(ModelLibraryLayout.pageKeepingFirst(5, 3, 8) == 2, "larger pages move back to the same model");
        require(ModelLibraryLayout.pageKeepingFirst(1, 8, 3) == 1 && ModelLibraryLayout.pageKeepingFirst(4, 0, 3) == 1,
                "the first page and unknown previous sizes stay valid");
        System.out.println("PASS model library layout: responsive columns, scroll anchors, virtualization and clipped hit tests");
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
