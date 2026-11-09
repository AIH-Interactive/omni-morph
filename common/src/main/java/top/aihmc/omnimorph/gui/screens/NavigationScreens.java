package top.aihmc.omnimorph.gui.screens;

import net.minecraft.client.gui.screens.Screen;
import org.figuramc.figura.gui.screens.ConfigScreen;
import org.figuramc.figura.gui.screens.HelpScreen;
import org.figuramc.figura.gui.screens.PermissionsScreen;
import org.figuramc.figura.gui.screens.WardrobeScreen;
import top.aihmc.omnimorph.gui.widgets.navigation.NavigationRoute;

/** Maps navigation destinations to the existing pages that own them. */
public final class NavigationScreens {
    private NavigationScreens() {
    }

    /** Creates the page for a destination, preselecting the entry inside pages that own several. */
    public static Screen open(NavigationRoute route, Screen parent) {
        return switch (route) {
            case WARDROBE, OUTFIT, PREVIEW, INFORMATION, BONES, COLLECTION -> {
                var wardrobe = new WardrobeScreen(parent);
                wardrobe.selectNavigationRoute(route);
                yield wardrobe;
            }
            case LIBRARY, MINE, FAVORITES, PUBLIC, UPLOAD, ADMIN -> {
                var library = new ModelLibraryScreen(parent);
                library.selectNavigationRoute(route);
                yield library;
            }
            case PERMISSIONS -> new PermissionsScreen(parent);
            case SETTINGS -> new ConfigScreen(parent);
            case HELP -> new HelpScreen(parent);
        };
    }

    /** The default current entry of a page, from the panel it belongs to. */
    public static NavigationRoute routeOf(Class<? extends Screen> panel) {
        if (panel == WardrobeScreen.class) {
            return NavigationRoute.PREVIEW;
        }
        if (panel == PermissionsScreen.class) {
            return NavigationRoute.PERMISSIONS;
        }
        if (panel == ConfigScreen.class) {
            return NavigationRoute.SETTINGS;
        }
        if (panel == HelpScreen.class) {
            return NavigationRoute.HELP;
        }
        return NavigationRoute.PUBLIC;
    }
}
