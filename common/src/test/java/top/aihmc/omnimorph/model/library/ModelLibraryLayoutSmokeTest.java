package top.aihmc.omnimorph.model.library;

import top.aihmc.omnimorph.gui.widgets.library.ModelLibraryLayout;
import top.aihmc.omnimorph.gui.widgets.library.LibraryWorkspaceLayout;
import top.aihmc.omnimorph.gui.widgets.library.LibraryActionBar;
import top.aihmc.omnimorph.utils.FileSizeFormatter;

public final class ModelLibraryLayoutSmokeTest {
    public static void main(String[] args) {
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
        require(ModelLibraryLayout.of(196, 100, 8).columns() == 1, "one compact card column");
        require(ModelLibraryLayout.of(398, 100, 8).columns() == 2, "two columns");
        require(ModelLibraryLayout.of(600, 100, 8).columns() == 3, "three compact columns in the former two-column area");
        require(ModelLibraryLayout.of(600, 430, 0).pageCapacity(64) == 12,
                "three columns and four complete rows request twelve models");
        require(ModelLibraryLayout.of(600, 430, 0).pageCapacity(8) == 8,
                "older backend maximum is respected");
        require(ModelLibraryLayout.of(398, 190, 0).pageCapacity(64) == 4,
                "smaller viewport requests only the rows it can display");
        require(ModelLibraryLayout.of(10000, 10000, 0).pageCapacity(64) == 64,
                "large screens remain within the server bound");
        require(ModelLibraryLayout.of(1206, 100, 8).columns() == 6, "more than three columns");
        for (int width : new int[]{1, 128, 170, 240, 486, 732, 1470}) {
            for (int height : new int[]{1, 92, 116, 230, 800}) {
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
        var old = ModelLibraryLayout.of(398, 92, 8);
        int anchor = old.first(196);
        var resized = ModelLibraryLayout.of(196, 92, 8);
        require(resized.first(resized.scrollTo(anchor)) == anchor, "resize retains first visible model index");
        var row = ModelLibraryLayout.of(398, 92, 8);
        require(row.endWithPrefetch(0) == 4, "one prefetch row after a fully visible row");
        require(row.hit(196, 0, 0) == -1, "horizontal gaps are not card hits");
        var tallerViewport = ModelLibraryLayout.of(398, 100, 8);
        require(tallerViewport.hit(0, 92, 0) == -1, "vertical gaps are not card hits");
        require(ModelLibraryLayout.of(128, 116, 0).hit(0, 0, 0) == -1, "empty grid");
        System.out.println("PASS model library layout: responsive columns, scroll anchors, virtualization and clipped hit tests");
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
