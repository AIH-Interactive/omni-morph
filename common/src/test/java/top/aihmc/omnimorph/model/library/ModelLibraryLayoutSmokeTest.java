package top.aihmc.omnimorph.model.library;

import top.aihmc.omnimorph.gui.widgets.library.ModelLibraryLayout;
import top.aihmc.omnimorph.gui.widgets.library.LibraryWorkspaceLayout;
import top.aihmc.omnimorph.gui.widgets.library.LibraryActionBar;

public final class ModelLibraryLayoutSmokeTest {
    public static void main(String[] args) {
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
        require(ModelLibraryLayout.of(240, 100, 8).columns() == 1, "one horizontal card column");
        require(ModelLibraryLayout.of(486, 100, 8).columns() == 2, "two columns");
        require(ModelLibraryLayout.of(732, 100, 8).columns() == 3, "three columns");
        require(ModelLibraryLayout.of(1470, 100, 8).columns() == 6, "more than three columns");
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
        var old = ModelLibraryLayout.of(486, 116, 8);
        int anchor = old.first(244);
        var resized = ModelLibraryLayout.of(240, 116, 8);
        require(resized.first(resized.scrollTo(anchor)) == anchor, "resize retains first visible model index");
        var row = ModelLibraryLayout.of(486, 116, 8);
        require(row.endWithPrefetch(0) == 4, "one prefetch row after a fully visible row");
        require(row.hit(240, 0, 0) == -1 && row.hit(0, 116, 0) == -1, "gaps are not card hits");
        require(ModelLibraryLayout.of(128, 116, 0).hit(0, 0, 0) == -1, "empty grid");
        System.out.println("PASS model library layout: responsive columns, scroll anchors, virtualization and clipped hit tests");
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
