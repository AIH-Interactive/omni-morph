package top.aihmc.omnimorph.gui.widgets.wardrobe;

/** Widths of the settings column and the preview on the model, outfit and accessory control screens. */
public record ControlsPaneLayout(int panelWidth, int previewWidth, boolean compact) {
    public static final int GAP = 16;
    public static final int COMPACT_GAP = 6;

    public static ControlsPaneLayout of(int screenWidth, boolean compact) {
        if (compact) {
            // Settings on the left and a narrow preview on the right, aligned with the wardrobe tabs on
            // the left and the right-side pane tabs; the preview takes a quarter of the shared width.
            int compactPanel = Math.max(1, screenWidth - 62);
            return new ControlsPaneLayout(compactPanel, Math.max(1, (compactPanel - COMPACT_GAP) / 4), true);
        }
        int panel = Math.min(960, screenWidth - 40);
        if (screenWidth < 620) {
            return new ControlsPaneLayout(panel, 0, false);
        }
        return new ControlsPaneLayout(panel, Math.max(180, Math.min(280, panel / 3)), false);
    }

    /** Width of a row's control widget. */
    public int rowWidgetWidth() {
        if (!compact) {
            return 180;
        }
        return net.minecraft.util.Mth.clamp((controlsWidth() - 20) * 2 / 5, 60, 180);
    }

    /** Distance from the right edge of the settings column to the control widget. */
    public int rowWidgetOffset() {
        return rowWidgetWidth() + 58;
    }

    /** Width left for a row's title between the column padding and the control widget. */
    public int rowLabelWidth() {
        return controlsWidth() - rowWidgetOffset() - 16;
    }

    public int gap() {
        return compact ? COMPACT_GAP : GAP;
    }

    public int controlsWidth() {
        if (previewWidth <= 0) {
            return panelWidth;
        }
        int remaining = panelWidth - previewWidth - gap();
        return compact ? remaining : Math.max(360, remaining);
    }
}
