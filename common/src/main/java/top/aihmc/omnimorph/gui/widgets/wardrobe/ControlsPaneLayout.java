package top.aihmc.omnimorph.gui.widgets.wardrobe;

/** Widths of the settings column and the preview on the model, outfit and accessory control screens. */
public record ControlsPaneLayout(int panelWidth, int previewWidth, boolean compact) {
    public static final int GAP = 16;

    public static ControlsPaneLayout of(int screenWidth, boolean compact) {
        int panel = Math.min(960, screenWidth - 40);
        if (compact) {
            // Settings on the left and the preview on the right, in two equal columns.
            return new ControlsPaneLayout(panel, Math.max(1, (panel - GAP) / 2), true);
        }
        if (screenWidth < 620) {
            return new ControlsPaneLayout(panel, 0, false);
        }
        return new ControlsPaneLayout(panel, Math.max(180, Math.min(280, panel / 3)), false);
    }

    public int controlsWidth() {
        if (previewWidth <= 0) {
            return panelWidth;
        }
        int remaining = panelWidth - previewWidth - GAP;
        return compact ? remaining : Math.max(360, remaining);
    }
}
