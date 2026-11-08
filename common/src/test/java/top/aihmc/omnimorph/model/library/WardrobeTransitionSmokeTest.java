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
        // Reference 480x252 GUI: wardrobe tabs end at 26, the right pane tabs start at 452.
        var outfit = WardrobeLayout.compactOf(480, 252);
        require(outfit.compact() && outfit.leftX() == 30 && outfit.leftWidth() == 132,
                "compact outfit keeps a narrow resource list instead of a full-width single pane");
        require(outfit.previewX() == 168 && outfit.previewWidth() == 280
                        && outfit.treeX() == 168 && outfit.treeWidth() == 280,
                "compact preview and default information share the wide pane beside the list");
        require(WardrobeLayout.rightTabX(480) == 452 && outfit.previewX() + outfit.previewWidth() <= 448,
                "the right vertical tabs stay clear of the content");
        var bones = WardrobeLayout.bonesOf(480, 252);
        require(bones.compact() && bones.leftWidth() == 0 && bones.previewX() == 30 && bones.previewWidth() == 206
                        && bones.treeX() == 242 && bones.treeWidth() == 206,
                "compact bone editing hides the list and splits preview and tree into equal panes");
        // Penpot 01/37/40: panes start at the top row, right tabs are 22x52 at y 28, 84 and 140.
        require(outfit.top() == 28 && bones.top() == 28,
                "compact outfit panes take over the former pane-switch row");
        require(WardrobeLayout.rightTabY(0) == 28 && WardrobeLayout.rightTabY(1) == 84
                        && WardrobeLayout.rightTabY(2) == 140 && WardrobeLayout.RIGHT_TAB_HEIGHT == 52,
                "right pane tabs follow the reference size and spacing");
        require(top.aihmc.omnimorph.gui.widgets.library.CompactMode.active(480, 0)
                        && !top.aihmc.omnimorph.gui.widgets.library.CompactMode.active(560, 0),
                "the automatic GUI scale uses compact layouts only below 560 GUI units");
        require(!top.aihmc.omnimorph.gui.widgets.library.CompactMode.active(480, 2)
                        && !top.aihmc.omnimorph.gui.widgets.library.CompactMode.active(320, 3),
                "fixed GUI scales keep the existing layouts at the same logical width");
        // Control screens: compact windows keep "settings | preview" instead of hiding the preview.
        var controls = top.aihmc.omnimorph.gui.widgets.wardrobe.ControlsPaneLayout.of(480, true);
        // Penpot 27: settings 30..236 and preview 242..448.
        require(controls.previewWidth() == 206 && controls.controlsWidth() == 206 && controls.gap() == 6,
                "compact controls keep a preview beside an equally wide settings column");
        require(controls.panelWidth() == 418 && controls.controlsWidth() + controls.gap()
                        + controls.previewWidth() == controls.panelWidth(),
                "compact settings and preview fill the panel without overlap");
        require(top.aihmc.omnimorph.gui.widgets.wardrobe.ControlsPaneLayout.of(480, false).previewWidth() == 0,
                "fixed GUI scales keep the existing narrow controls layout");
        var wideControls = top.aihmc.omnimorph.gui.widgets.wardrobe.ControlsPaneLayout.of(854, false);
        require(wideControls.previewWidth() == 271 && wideControls.controlsWidth() == 527,
                "wide controls keep their existing proportions");
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
