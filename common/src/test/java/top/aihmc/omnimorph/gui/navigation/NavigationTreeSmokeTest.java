package top.aihmc.omnimorph.gui.navigation;

import top.aihmc.omnimorph.gui.widgets.navigation.NavigationLayout;
import top.aihmc.omnimorph.gui.widgets.navigation.NavigationRoute;
import top.aihmc.omnimorph.gui.widgets.navigation.NavigationTree;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/** Checks the shared left navigation: menu order, defaults, folding, overflow geometry and pointer hits. */
public final class NavigationTreeSmokeTest {
    public static void main(String[] args) {
        checkMenuTree();
        checkDefaultRoutes();
        checkFolding();
        checkBreadcrumbs();
        checkGeometry();
        checkCompactInformation();
        System.out.println("NavigationTreeSmokeTest passed");
    }

    private static void checkCompactInformation() {
        var tree = new NavigationTree();
        tree.setCompact(false);
        require(!tree.visibleRoutes().contains(NavigationRoute.INFORMATION)
                        && !tree.visibleRoutes().contains(NavigationRoute.ANNOUNCEMENTS),
                "wide navigation hides model information and announcements");
        require(tree.visibleRoutes().contains(NavigationRoute.BONES)
                        && tree.visibleRoutes().contains(NavigationRoute.PREVIEW),
                "wide navigation retains the other outfit destinations");
        int wideRows = tree.visibleRows().size();
        tree.setCompact(true);
        require(tree.visibleRoutes().contains(NavigationRoute.INFORMATION)
                        && tree.visibleRoutes().contains(NavigationRoute.ANNOUNCEMENTS)
                        && tree.visibleRows().size() == wideRows + 2,
                "returning to compact restores the information row without changing folding");
    }

    private static void checkMenuTree() {
        var tree = new NavigationTree();
        require(!tree.visibleRoutes().contains(NavigationRoute.ADMIN),
                "the management entry is hidden until the player is confirmed as an administrator");
        tree.setAdministratorVisible(true);
        List<String> expected = List.of(
                "0 衣柜", "1 配装", "2 形象", "2 装饰", "2 模型信息", "2 公告", "1 我的形象集",
                "0 模型库", "1 我的模型", "1 我的收藏", "1 模型库", "1 上传模型", "1 管理员后台",
                "0 权限", "0 设置", "0 帮助");
        List<String> actual = tree.visibleRows().stream()
                .map(node -> node.depth() + " " + chinese(node.translationKey()))
                .toList();
        require(actual.equals(expected), "menu text, order and levels follow the reference tree: " + actual);
        require(tree.topLevel().equals(List.of(NavigationRoute.WARDROBE, NavigationRoute.LIBRARY,
                        NavigationRoute.PERMISSIONS, NavigationRoute.SETTINGS, NavigationRoute.HELP)),
                "the collapsed rail shows exactly five parents");
        require(tree.isBranch(NavigationRoute.WARDROBE) && tree.isBranch(NavigationRoute.OUTFIT)
                        && tree.isBranch(NavigationRoute.LIBRARY) && !tree.isBranch(NavigationRoute.PERMISSIONS),
                "only wardrobe, outfit and library fold");
    }

    private static void checkDefaultRoutes() {
        var tree = new NavigationTree();
        require(tree.destination(NavigationRoute.WARDROBE) == NavigationRoute.PREVIEW, "wardrobe opens preview");
        require(tree.destination(NavigationRoute.OUTFIT) == NavigationRoute.PREVIEW, "outfit opens preview");
        require(tree.destination(NavigationRoute.LIBRARY) == NavigationRoute.PUBLIC, "library opens its catalog");
        for (NavigationRoute leaf : List.of(NavigationRoute.PERMISSIONS, NavigationRoute.SETTINGS,
                NavigationRoute.HELP, NavigationRoute.ADMIN, NavigationRoute.BONES)) {
            require(tree.destination(leaf) == leaf, "leaf entries open themselves: " + leaf);
        }
        require(tree.topLevelOf(NavigationRoute.BONES) == NavigationRoute.WARDROBE
                        && tree.topLevelOf(NavigationRoute.UPLOAD) == NavigationRoute.LIBRARY,
                "selection highlights the owning parent on the rail");
    }

    private static void checkFolding() {
        var tree = new NavigationTree();
        tree.toggle(NavigationRoute.OUTFIT);
        require(!tree.visibleRoutes().contains(NavigationRoute.PREVIEW)
                        && tree.visibleRoutes().contains(NavigationRoute.COLLECTION),
                "folding outfit hides only its children");
        tree.toggle(NavigationRoute.WARDROBE);
        require(!tree.visibleRoutes().contains(NavigationRoute.COLLECTION)
                        && tree.visibleRoutes().contains(NavigationRoute.MINE),
                "folding wardrobe keeps the library branch");
        tree.toggle(NavigationRoute.WARDROBE);
        require(!tree.visibleRoutes().contains(NavigationRoute.PREVIEW) && tree.isCollapsed(NavigationRoute.OUTFIT),
                "branches fold independently and keep their own state");
        tree.toggle(NavigationRoute.LIBRARY);
        require(tree.visibleRows().size() == 7, "outfit and library folded: " + tree.visibleRows().size());
        tree.toggle(NavigationRoute.PERMISSIONS);
        require(tree.visibleRows().size() == 7, "leaf entries cannot fold");
    }

    private static void checkBreadcrumbs() {
        var tree = new NavigationTree();
        require(tree.breadcrumb(NavigationRoute.PREVIEW).equals(List.of(NavigationRoute.WARDROBE, NavigationRoute.PREVIEW)),
                "wardrobe path is parent / page");
        require(tree.breadcrumb(NavigationRoute.PUBLIC).equals(List.of(NavigationRoute.LIBRARY)),
                "the catalog does not repeat its parent label");
        require(tree.breadcrumb(NavigationRoute.HELP).equals(List.of(NavigationRoute.HELP)), "top-level pages");
    }

    private static void checkGeometry() {
        var tree = new NavigationTree();
        tree.setAdministratorVisible(true);
        // A fake font: every character is 6 units wide.
        var wide = NavigationLayout.of(tree, 480, 252, text -> chinese(text).length() * 6);
        require(wide.expandedWidth() <= NavigationLayout.MAX_EXPANDED_WIDTH
                        && wide.expandedWidth() > NavigationLayout.RAIL_WIDTH,
                "expanded width is measured and capped: " + wide.expandedWidth());
        require(wide.maximumScrollX() == 0 && wide.maximumScrollY() > 0,
                "Penpot 01: the expanded tree scrolls vertically in a 480x252 window");
        var tall = NavigationLayout.of(tree, 640, 360, text -> chinese(text).length() * 6);
        require(tall.maximumScrollY() == 0, "Penpot 06: the full tree fits a 640x360 window");
        var narrowText = NavigationLayout.of(tree, 480, 252, text -> chinese(text).length() * 3);
        require(narrowText.expandedWidth() < wide.expandedWidth(), "width follows the longest visible text");

        var longText = NavigationLayout.of(tree, 480, 252, text -> chinese(text).length() * 30);
        require(longText.expandedWidth() == NavigationLayout.MAX_EXPANDED_WIDTH && longText.maximumScrollX() > 0,
                "text beyond the maximum width scrolls horizontally");
        require(longText.rowsBottom() == 252 - NavigationLayout.FOOTER_HEIGHT - NavigationLayout.SCROLLBAR, "horizontal scrollbar stays above the fixed connection footer");

        for (int height : new int[]{120, 150, 199, 233}) {
            var shortWindow = NavigationLayout.of(tree, 480, height, text -> chinese(text).length() * 6);
            require(shortWindow.maximumScrollY() > 0, "short windows scroll vertically: " + height);
            int last = tree.visibleRows().size() - 1;
            int bottom = shortWindow.rowY(last, shortWindow.maximumScrollY()) + NavigationLayout.ROW_HEIGHT;
            require(bottom <= shortWindow.rowsBottom(), "the last entry is reachable at the bottom: " + height);
        }

        int firstRow = wide.rowY(0, 0) + 2;
        require(wide.rowAt(10, firstRow, 0, 0) == 0, "row hit inside the overlay");
        require(wide.rowAt(wide.expandedWidth() + 1, firstRow, 0, 0) == -1, "no hit outside the overlay");
        require(wide.rowAt(10, wide.rowY(0, 0) - 1, 0, 0) == -1, "the header is not an entry");
        require(wide.isArrow(0, wide.expandedWidth() - 4, 0) && !wide.isArrow(0, 30, 0),
                "the arrow zone toggles, the label navigates");
        require(!wide.isArrow(12, wide.expandedWidth() - 4, 0), "leaf rows have no arrow");
        require(wide.railItemAt(14, wide.railItemY(1) + 2) == 1 && wide.railItemAt(40, wide.railItemY(1) + 2) == -1,
                "collapsed rail hits only inside its width");
        require(wide.railItemAt(14, wide.railItemY(4) + NavigationLayout.RAIL_ITEM_HEIGHT) == -1,
                "no rail item below help");
    }

    private static JsonObject chineseTranslations;

    private static String chinese(String key) {
        if (chineseTranslations == null) {
            Path file = Path.of("src/main/resources/assets/omni-morph/lang/zh_cn.json");
            try {
                chineseTranslations = JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8))
                        .getAsJsonObject();
            } catch (IOException failure) {
                throw new IllegalStateException("cannot read " + file.toAbsolutePath(), failure);
            }
        }
        var value = chineseTranslations.get("omni-morph." + key);
        require(value != null, "missing Chinese translation: " + key);
        return value.getAsString();
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
