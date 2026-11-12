package top.aihmc.omnimorph.gui.navigation;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.MouseButtonInfo;
import org.figuramc.figura.utils.FiguraText;
import top.aihmc.omnimorph.gui.widgets.navigation.NavigationLayout;
import top.aihmc.omnimorph.gui.widgets.navigation.NavigationRoute;
import top.aihmc.omnimorph.gui.widgets.navigation.NavigationTree;
import top.aihmc.omnimorph.gui.widgets.navigation.SideNavigationWidget;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;

/** Production navigation input and callbacks in the existing offline client; no window/pixel claim. */
public final class SideNavigationInputSmokeTest {
    private SideNavigationInputSmokeTest() {
    }

    public static void run() throws Exception {
        var tree = SideNavigationWidget.tree();
        boolean compact = field(NavigationTree.class, "compact").getBoolean(tree);
        var folding = new EnumMap<NavigationRoute, Boolean>(NavigationRoute.class);
        for (var route : NavigationRoute.values()) {
            if (tree.isBranch(route)) {
                folding.put(route, tree.isCollapsed(route));
            }
        }
        try {
            for (var route : folding.keySet()) {
                if (tree.isCollapsed(route)) {
                    tree.toggle(route);
                }
            }
            List<NavigationRoute> opened = new ArrayList<>();
            var widget = new SideNavigationWidget(480, 900, NavigationRoute.UPLOAD, true, opened::add);
            int libraryY = NavigationLayout.RAIL_ITEMS_TOP + NavigationLayout.RAIL_ITEM_HEIGHT + 8;
            require(widget.mouseClicked(mouse(12, libraryY, InputConstants.MOUSE_BUTTON_LEFT), false),
                    "native left click is consumed by the icon rail");
            require(opened.equals(List.of(NavigationRoute.PUBLIC)),
                    "native left click on the library rail must invoke the public catalog callback");
            opened.clear();
            require(widget.mouseClicked(mouse(12, libraryY, InputConstants.MOUSE_BUTTON_RIGHT), false)
                            && opened.isEmpty(), "right click cannot navigate");
            require(!widget.mouseClicked(mouse(300, libraryY, InputConstants.MOUSE_BUTTON_LEFT), false),
                    "content outside the rail is not consumed");

            field(SideNavigationWidget.class, "expanded").setBoolean(widget, true);
            tree.setCompact(true);
            var layout = NavigationLayout.of(tree, 480, 900,
                    key -> Minecraft.getInstance().font.width(FiguraText.of(key)));
            int settings = tree.visibleRoutes().indexOf(NavigationRoute.SETTINGS);
            require(widget.mouseClicked(mouse(8, layout.rowY(settings, 0) + 8,
                            InputConstants.MOUSE_BUTTON_LEFT), false)
                            && opened.equals(List.of(NavigationRoute.SETTINGS)),
                    "native left click on an expanded row must invoke its route callback");
            opened.clear();
            field(SideNavigationWidget.class, "scrollX").setInt(widget, layout.maximumScrollX());
            int wardrobe = tree.visibleRoutes().indexOf(NavigationRoute.WARDROBE);
            require(widget.mouseClicked(mouse(layout.arrowX() - layout.maximumScrollX() + 1,
                            layout.rowY(wardrobe, 0) + 8, InputConstants.MOUSE_BUTTON_LEFT), false)
                            && tree.isCollapsed(NavigationRoute.WARDROBE) && opened.isEmpty(),
                    "native left click on a fold arrow changes folding without navigation");
            System.out.println("PASS navigation mouse input: actual rail, expanded row, fold arrow and native button filtering");
        } finally {
            for (var entry : folding.entrySet()) {
                if (tree.isCollapsed(entry.getKey()) != entry.getValue()) {
                    tree.toggle(entry.getKey());
                }
            }
            tree.setCompact(compact);
        }
    }

    private static MouseButtonEvent mouse(double x, double y, int button) {
        return new MouseButtonEvent(x, y, new MouseButtonInfo(button, 0));
    }

    private static Field field(Class<?> type, String name) throws ReflectiveOperationException {
        Field field = type.getDeclaredField(name);
        field.setAccessible(true);
        return field;
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
