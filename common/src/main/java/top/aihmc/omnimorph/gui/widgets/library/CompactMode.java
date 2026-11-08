package top.aihmc.omnimorph.gui.widgets.library;

import net.minecraft.client.Minecraft;

/**
 * Decides when the compact-only wardrobe and library arrangements apply: the player uses the
 * automatic GUI scale and the resulting logical width is narrow. Fixed scales keep the existing
 * layouts even at the same logical size.
 */
public final class CompactMode {
    /** GUI width below which the automatic scale uses the compact arrangements. */
    public static final int SCREEN_WIDTH = 560;

    private CompactMode() {
    }

    public static boolean active(int width) {
        var minecraft = Minecraft.getInstance();
        if (minecraft == null || minecraft.options == null || minecraft.options.guiScale() == null) {
            return false;
        }
        return active(width, minecraft.options.guiScale().get());
    }

    /** Option value 0 is Minecraft's "Auto" GUI scale. */
    public static boolean active(int width, int guiScaleSetting) {
        return guiScaleSetting == 0 && width < SCREEN_WIDTH;
    }
}
