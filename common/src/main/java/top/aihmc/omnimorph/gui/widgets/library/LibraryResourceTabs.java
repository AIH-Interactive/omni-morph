package top.aihmc.omnimorph.gui.widgets.library;

import org.figuramc.figura.utils.FiguraText;
import top.aihmc.omnimorph.gui.widgets.wardrobe.WardrobeVerticalTab;

import java.util.List;
import java.util.function.Consumer;

/** The two resource kinds share one right-side tab strip across collection, catalog and upload. */
public final class LibraryResourceTabs {
    public static final int RESERVED_WIDTH = 24;

    private LibraryResourceTabs() {
    }

    public static List<WardrobeVerticalTab> create(int width, int top, boolean accessory,
                                                  Consumer<Boolean> select) {
        return List.of(
                new WardrobeVerticalTab(width - 26, top, 20, 64,
                        FiguraText.of("gui.library.tab_avatar"), !accessory, button -> select.accept(false)),
                new WardrobeVerticalTab(width - 26, top + 70, 20, 64,
                        FiguraText.of("gui.library.tab_accessory"), accessory, button -> select.accept(true)));
    }
}
