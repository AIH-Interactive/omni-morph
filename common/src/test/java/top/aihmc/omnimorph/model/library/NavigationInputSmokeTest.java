package top.aihmc.omnimorph.model.library;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.components.Renderable;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import org.figuramc.figura.gui.screens.AbstractPanelScreen;
import top.aihmc.omnimorph.gui.widgets.navigation.NavigationLayout;
import top.aihmc.omnimorph.gui.widgets.navigation.NavigationRoute;
import top.aihmc.omnimorph.gui.widgets.navigation.SideNavigationWidget;

import java.util.ArrayList;
import java.util.List;

/** Exercises 1.20.1 screen input dispatch without drawing pixels or changing screens. */
final class NavigationInputSmokeTest {
    static void run() throws Exception {
        var minecraft = Minecraft.getInstance();
        var fontField = Minecraft.class.getDeclaredField("font");
        fontField.setAccessible(true);
        Object previousFont = fontField.get(minecraft);
        fontField.set(minecraft, new Font(null, false) {
            @Override
            public int width(FormattedText text) {
                return text.getString().length() * 6;
            }
        });
        try {
            var screen = new InputScreen();
            screen.initialize();
            var tree = SideNavigationWidget.tree();
            int settingsY = NavigationLayout.RAIL_ITEMS_TOP + 3 * NavigationLayout.RAIL_ITEM_HEIGHT + 2;
            require(screen.mouseClicked(10, settingsY, 0) && screen.destination == NavigationRoute.SETTINGS,
                    "collapsed rail routes the actual screen click");
            require(screen.underlyingClicks == 0, "rail clicks do not reach covered content");
            var expanded = SideNavigationWidget.class.getDeclaredField("expanded");
            expanded.setAccessible(true);
            expanded.setBoolean(screen.panels, true); // hover rendering is outside this input-only fixture
            var geometry = NavigationLayout.of(tree, screen.width, screen.height, key -> minecraft.font.width(org.figuramc.figura.utils.FiguraText.of(key)));
            int index = tree.visibleRoutes().indexOf(NavigationRoute.COLLECTION);
            require(index >= 0, "collection row is visible");
            int rowY = geometry.rowY(index, 0) + 2;
            require(screen.mouseClicked(40, rowY, 0) && screen.destination == NavigationRoute.COLLECTION,
                    "expanded row forwards its destination through the screen");
            require(screen.underlyingClicks == 0, "expanded overlay consumes covered clicks");
            var scrollX = SideNavigationWidget.class.getDeclaredField("scrollX");
            scrollX.setAccessible(true);
            scrollX.setInt(screen.panels, geometry.maximumScrollX());
            int branch = tree.visibleRoutes().indexOf(NavigationRoute.WARDROBE);
            boolean wasCollapsed = tree.isCollapsed(NavigationRoute.WARDROBE);
            require(screen.mouseClicked(geometry.expandedWidth() - 2, geometry.rowY(branch, 0) + 2, 0),
                    "branch arrow click is consumed");
            require(tree.isCollapsed(NavigationRoute.WARDROBE) != wasCollapsed, "arrow toggles the shared tree");
            tree.toggle(NavigationRoute.WARDROBE);
            require(screen.mouseClicked(250, 150, 0) && screen.underlyingClicks == 1,
                    "uncovered content still receives its click");
            screen.detachNavigation();
            require(screen.mouseClicked(10, settingsY, 0) && screen.underlyingClicks == 2,
                    "render-only modal header does not capture navigation input");
            System.out.println("PASS navigation input: real screen rail/row/arrow dispatch, covered clicks and modal detachment; pixels unverified");
        } finally {
            fontField.set(minecraft, previousFont);
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    private static final class InputScreen extends AbstractPanelScreen {
        private final List<Renderable> renderables = new ArrayList<>();
        private NavigationRoute destination;
        private int underlyingClicks;

        private InputScreen() {
            super(null, Component.empty());
        }

        private void initialize() {
            width = 640;
            height = 360;
            super.init();
            addWidget(new org.figuramc.figura.gui.widgets.AbstractContainerElement(0, 0, 640, 360) {
                @Override
                public boolean mouseClicked(double x, double y, int button) {
                    underlyingClicks++;
                    return true;
                }

                @Override
                public void setFocused(boolean focused) {
                }

                @Override
                public boolean isFocused() {
                    return false;
                }
            });
        }

        private void detachNavigation() {
            removeWidget(panels);
            addRenderableOnly(panels);
        }

        @Override
        protected boolean usesSideNavigation() {
            return true;
        }

        @Override
        protected NavigationRoute navigationRoute() {
            return NavigationRoute.PREVIEW;
        }

        @Override
        public void navigate(NavigationRoute route) {
            destination = route;
        }

        @Override
        public List<Renderable> renderables() {
            return renderables;
        }
    }
}
