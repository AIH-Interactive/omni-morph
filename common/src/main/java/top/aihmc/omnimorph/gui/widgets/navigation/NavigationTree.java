package top.aihmc.omnimorph.gui.widgets.navigation;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * The reference menu tree of the shared left navigation and its folding state.
 * Text, order and levels follow 后台式菜单树参考.md; parents open their default page.
 */
public final class NavigationTree {
    public record Node(NavigationRoute route, NavigationRoute parent, int depth, String translationKey) {
    }

    /** Indexed by {@link NavigationRoute#ordinal()}, which follows the same order. */
    private static final List<Node> NODES = List.of(
            node(NavigationRoute.WARDROBE, null, 0, "wardrobe"),
            node(NavigationRoute.OUTFIT, NavigationRoute.WARDROBE, 1, "outfit"),
            node(NavigationRoute.PREVIEW, NavigationRoute.OUTFIT, 2, "preview"),
            node(NavigationRoute.BONES, NavigationRoute.OUTFIT, 2, "bones"),
            node(NavigationRoute.INFORMATION, NavigationRoute.OUTFIT, 2, "information"),
            node(NavigationRoute.ANNOUNCEMENTS, NavigationRoute.OUTFIT, 2, "announcements"),
            node(NavigationRoute.COLLECTION, NavigationRoute.WARDROBE, 1, "collection"),
            node(NavigationRoute.LIBRARY, null, 0, "library"),
            node(NavigationRoute.MINE, NavigationRoute.LIBRARY, 1, "mine"),
            node(NavigationRoute.FAVORITES, NavigationRoute.LIBRARY, 1, "favorites"),
            node(NavigationRoute.PUBLIC, NavigationRoute.LIBRARY, 1, "public"),
            node(NavigationRoute.UPLOAD, NavigationRoute.LIBRARY, 1, "upload"),
            node(NavigationRoute.ADMIN, NavigationRoute.LIBRARY, 1, "admin"),
            node(NavigationRoute.PERMISSIONS, null, 0, "permissions"),
            node(NavigationRoute.SETTINGS, null, 0, "settings"),
            node(NavigationRoute.HELP, null, 0, "help"));

    /** Folded branches; every branch starts expanded. */
    private final Set<NavigationRoute> collapsed = EnumSet.noneOf(NavigationRoute.class);
    /** The management entry stays hidden until the service confirms this player is an administrator. */
    private boolean administratorVisible;
    private boolean compact = true;

    private static Node node(NavigationRoute route, NavigationRoute parent, int depth, String key) {
        return new Node(route, parent, depth, "gui.navigation." + key);
    }

    public static Node nodeOf(NavigationRoute route) {
        return NODES.get(route.ordinal());
    }

    public List<NavigationRoute> topLevel() {
        return NODES.stream()
                .filter(node -> node.parent() == null)
                .map(Node::route)
                .toList();
    }

    public boolean isBranch(NavigationRoute route) {
        return NODES.stream().anyMatch(node -> node.parent() == route);
    }

    public void setAdministratorVisible(boolean visible) {
        administratorVisible = visible;
    }

    public void setCompact(boolean compact) {
        this.compact = compact;
    }

    public boolean isCollapsed(NavigationRoute route) {
        return collapsed.contains(route);
    }

    /** Folds or unfolds a branch; leaf entries are unaffected. */
    public void toggle(NavigationRoute route) {
        if (!isBranch(route)) {
            return;
        }
        if (!collapsed.remove(route)) {
            collapsed.add(route);
        }
    }

    /** Rows shown by the expanded overlay: entries whose ancestors are all unfolded. */
    public List<Node> visibleRows() {
        List<Node> rows = new ArrayList<>();
        for (Node node : NODES) {
            boolean hiddenAdministrator = node.route() == NavigationRoute.ADMIN && !administratorVisible;
            boolean hiddenInformation = (node.route() == NavigationRoute.INFORMATION
                    || node.route() == NavigationRoute.ANNOUNCEMENTS) && !compact;
            if (!hiddenAdministrator && !hiddenInformation && ancestorsExpanded(node)) {
                rows.add(node);
            }
        }
        return rows;
    }

    public List<NavigationRoute> visibleRoutes() {
        return visibleRows().stream().map(Node::route).toList();
    }

    private boolean ancestorsExpanded(Node node) {
        NavigationRoute parent = node.parent();
        while (parent != null) {
            if (collapsed.contains(parent)) {
                return false;
            }
            parent = nodeOf(parent).parent();
        }
        return true;
    }

    /** The page a click opens: wardrobe and outfit open the preview, the library opens its catalog. */
    public NavigationRoute destination(NavigationRoute route) {
        return switch (route) {
            case WARDROBE, OUTFIT -> NavigationRoute.PREVIEW;
            case LIBRARY -> NavigationRoute.PUBLIC;
            default -> route;
        };
    }

    public NavigationRoute topLevelOf(NavigationRoute route) {
        Node node = nodeOf(route);
        while (node.parent() != null) {
            node = nodeOf(node.parent());
        }
        return node.route();
    }

    /** Top bar path: the parent, then the page, except where the page is the library catalog itself. */
    public List<NavigationRoute> breadcrumb(NavigationRoute route) {
        NavigationRoute top = topLevelOf(route);
        if (top == route || route == NavigationRoute.PUBLIC) {
            return List.of(top);
        }
        return List.of(top, route);
    }
}
