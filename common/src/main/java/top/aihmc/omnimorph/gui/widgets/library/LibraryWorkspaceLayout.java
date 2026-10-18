package top.aihmc.omnimorph.gui.widgets.library;

/** The reference workspace: a content pane, a narrow preview pane and a 24-pixel footer. */
public record LibraryWorkspaceLayout(int width, int height) {
    public int left() {
        return 8;
    }

    public int top() {
        return 28;
    }

    public int contentWidth() {
        return (width - 24) * 7 / 10;
    }

    public int previewX() {
        return left() + contentWidth() + 8;
    }

    public int previewWidth() {
        return width - previewX() - 8;
    }

    public int footerY() {
        return height - 28;
    }

    public int bodyY() {
        return 56;
    }

    public int bodyHeight() {
        return footerY() - bodyY() - 4;
    }

    public int previewHeight() {
        return footerY() - top() - 8;
    }
}
