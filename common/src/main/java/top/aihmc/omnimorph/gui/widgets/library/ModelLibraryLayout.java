package top.aihmc.omnimorph.gui.widgets.library;

/** Logical-pixel geometry shared by card rendering, clipping and pointer hit tests. */
public record ModelLibraryLayout(int width, int height, int count, int columns, int cardWidth) {
    public static final int GAP = 6;
    public static final int CARD_HEIGHT = 92;
    public static final int MIN_CARD_WIDTH = 196;

    public static ModelLibraryLayout of(int width, int height, int count) {
        int available = Math.max(1, width);
        int columns = Math.max(1, (available + GAP) / (MIN_CARD_WIDTH + GAP));
        return new ModelLibraryLayout(available, Math.max(1, height), Math.max(0, count), columns,
                Math.max(1, (available - (columns - 1) * GAP) / columns));
    }

    public int rows() {
        return (count + columns - 1) / columns;
    }

    public int maximumScroll() {
        return Math.max(0, rows() * (CARD_HEIGHT + GAP) - GAP - height);
    }

    public int x(int index) {
        return index % columns * (cardWidth + GAP);
    }

    public int y(int index, int scroll) {
        return index / columns * (CARD_HEIGHT + GAP) - Math.clamp(scroll, 0, maximumScroll());
    }

    public int first(int scroll) {
        return Math.min(count, Math.clamp(scroll, 0, maximumScroll()) / (CARD_HEIGHT + GAP) * columns);
    }

    public int endWithPrefetch(int scroll) {
        int step = CARD_HEIGHT + GAP;
        int visibleEndRow = (Math.clamp(scroll, 0, maximumScroll()) + height + step - 1) / step;
        return Math.min(count, (visibleEndRow + 1) * columns);
    }

    public int hit(double x, double y, int scroll) {
        if (x < 0 || y < 0 || x >= width || y >= height) {
            return -1;
        }
        int column = (int) x / (cardWidth + GAP);
        double absoluteY = y + Math.clamp(scroll, 0, maximumScroll());
        int row = (int) absoluteY / (CARD_HEIGHT + GAP);
        int index = row * columns + column;
        if (column >= columns || x - column * (cardWidth + GAP) >= cardWidth
                || absoluteY - row * (CARD_HEIGHT + GAP) >= CARD_HEIGHT || index >= count) {
            return -1;
        }
        return index;
    }

    public int scrollTo(int index) {
        return Math.clamp(index / columns * (CARD_HEIGHT + GAP), 0, maximumScroll());
    }
}
