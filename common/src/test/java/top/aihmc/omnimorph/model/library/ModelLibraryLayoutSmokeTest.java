package top.aihmc.omnimorph.model.library;

import top.aihmc.omnimorph.gui.widgets.library.ModelLibraryLayout;

public final class ModelLibraryLayoutSmokeTest {
    public static void main(String[] args) {
        require(ModelLibraryLayout.of(128, 100, 8).columns() == 1, "one column");
        require(ModelLibraryLayout.of(262, 100, 8).columns() == 2, "two columns");
        require(ModelLibraryLayout.of(396, 100, 8).columns() == 3, "three columns");
        require(ModelLibraryLayout.of(798, 100, 8).columns() == 6, "more than three columns");
        for (int width : new int[]{1, 128, 170, 262, 396, 798, 1200}) {
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
        var old = ModelLibraryLayout.of(262, 116, 8);
        int anchor = old.first(244);
        var resized = ModelLibraryLayout.of(128, 116, 8);
        require(resized.first(resized.scrollTo(anchor)) == anchor, "resize retains first visible model index");
        var row = ModelLibraryLayout.of(262, 116, 8);
        require(row.endWithPrefetch(0) == 4, "one prefetch row after a fully visible row");
        require(row.hit(128, 0, 0) == -1 && row.hit(0, 116, 0) == -1, "gaps are not card hits");
        require(ModelLibraryLayout.of(128, 116, 0).hit(0, 0, 0) == -1, "empty grid");
        System.out.println("PASS model library layout: responsive columns, scroll anchors, virtualization and clipped hit tests");
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
