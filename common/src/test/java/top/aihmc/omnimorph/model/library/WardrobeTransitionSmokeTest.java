package top.aihmc.omnimorph.model.library;

import top.aihmc.omnimorph.gui.widgets.wardrobe.WardrobeLayout;

import java.nio.file.Files;
import java.util.concurrent.TimeUnit;

/** Data/layout checks only; rendered GUI and input acceptance remain MANUAL/PENDING. */
public final class WardrobeTransitionSmokeTest {
    public static void main(String[] args) throws Exception {
        for (int width : new int[]{320, 427, 559, 560, 640, 854, 1280, 1920}) {
            for (int height : new int[]{240, 360, 480, 720, 1080}) {
                var layout = WardrobeLayout.of(width, height);
                require(layout.leftWidth() > 0 && layout.previewWidth() > 0 && layout.treeWidth() > 0,
                        "all pane widths remain positive");
                require(layout.treeX() + layout.treeWidth() <= width && layout.previewX() + layout.previewWidth() <= width,
                        "panes fit the logical window");
                require(layout.top() + layout.height() < height - 78, "content leaves room for actions");
                if (!layout.compact()) {
                    require(layout.leftX() + layout.leftWidth() < layout.previewX()
                            && layout.previewX() + layout.previewWidth() < layout.treeX(), "three columns never overlap");
                }
                var collection = WardrobeLayout.collectionOf(width, height);
                require(collection.leftWidth() > 0 && collection.previewWidth() > 0
                                && collection.previewX() + collection.previewWidth() <= width,
                        "collection cards and right preview fit the logical window");
                if (!collection.compact()) {
                    var catalog = new top.aihmc.omnimorph.gui.widgets.library.LibraryWorkspaceLayout(width - 22, height);
                    require(collection.leftWidth() == catalog.contentWidth()
                                    && collection.previewWidth() == catalog.previewWidth()
                                    && collection.previewX() == catalog.previewX() + 22,
                            "collection reuses catalog 70/30 proportions after reserving the vertical tabs");
                    require(collection.leftX() + collection.leftWidth() < collection.previewX()
                                    && collection.leftWidth() > collection.previewWidth(),
                            "collection reserves a wide card workspace left of the selected preview");
                }
            }
        }
        var directory = Files.createTempDirectory("wardrobe-candidates-");
        var source = directory.resolve("original-model.txt");
        try {
            Files.writeString(source, "original user model");
            var candidates = LocalOutfitCandidates.forDirectory(directory);
            require(!candidates.contains(source), "existing models are not implicitly outfit candidates");
            require(candidates.set(source, true).get(5, TimeUnit.SECONDS), "candidate write succeeds");
            require(candidates.contains(source), "explicit candidate is selectable");
            String stored = Files.readString(directory.resolve("wardrobe-local-outfit.json"));
            require(stored.contains("original-model.txt"), "candidate reference is durable");
            require(candidates.set(source, false).get(5, TimeUnit.SECONDS), "candidate removal is durable");
            require(!candidates.contains(source) && Files.readString(source).equals("original user model"),
                    "removing an outfit candidate preserves the user's model");
            require(Files.readString(directory.resolve("wardrobe-local-outfit.json")).equals("[]"),
                    "candidate removal does not leave stale stored references");
        } finally {
            Files.deleteIfExists(directory.resolve("wardrobe-local-outfit.json"));
            Files.deleteIfExists(source);
            Files.delete(directory);
        }
        System.out.println("PASS wardrobe transition: bounded layouts and durable local candidate references; GUI MANUAL/PENDING");
    }

    private static void require(boolean value, String message) {
        if (!value) {
            throw new AssertionError(message);
        }
    }
}
