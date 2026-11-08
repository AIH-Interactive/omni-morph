package top.aihmc.omnimorph.gui.widgets.library;

/** Logical-pixel geometry shared by card rendering, clipping and pointer hit tests. */
public record ModelLibraryLayout(int width, int height, int count, int columns, int cardWidth,
                                 int cardHeight, int imageSize) {
    public static final int GAP = 6;
    public static final int CARD_HEIGHT = 92;
    public static final int MIN_CARD_WIDTH = 196;
    public static final int COMPACT_CARD_HEIGHT = 68;
    public static final int COMPACT_IMAGE_SIZE = 32;
    public static final int COLLECTION_CARD_HEIGHT = 54;
    public static final int COLLECTION_IMAGE_SIZE = 24;

    public static int imageSize(int cardWidth) {
        return Math.max(1, Math.min(CARD_HEIGHT - 8, (cardWidth - 16) / 2));
    }

    /** The wide, responsive card grid. */
    public static ModelLibraryLayout of(int width, int height, int count) {
        int available = Math.max(1, width);
        int columns = Math.max(1, (available + GAP) / (MIN_CARD_WIDTH + GAP));
        int cardWidth = Math.max(1, (available - (columns - 1) * GAP) / columns);
        return new ModelLibraryLayout(available, Math.max(1, height), Math.max(0, count), columns,
                cardWidth, CARD_HEIGHT, imageSize(cardWidth));
    }

    /** Compact model library catalog: always two small cards per row. */
    public static ModelLibraryLayout compact(int width, int height, int count) {
        return twoColumns(width, height, count, COMPACT_CARD_HEIGHT, COMPACT_IMAGE_SIZE);
    }

    /** Compact wardrobe collection: always two short cards per row. */
    public static ModelLibraryLayout compactCollection(int width, int height, int count) {
        return twoColumns(width, height, count, COLLECTION_CARD_HEIGHT, COLLECTION_IMAGE_SIZE);
    }

    private static ModelLibraryLayout twoColumns(int width, int height, int count, int cardHeight, int imageSize) {
        int available = Math.max(1, width);
        int cardWidth = Math.max(1, (available - GAP) / 2);
        return new ModelLibraryLayout(available, Math.max(1, height), Math.max(0, count), 2,
                cardWidth, cardHeight, imageSize);
    }

    public int rows() {
        return (count + columns - 1) / columns;
    }

    public int pageCapacity(int maximum) {
        int visibleRows = Math.max(1, (height + GAP) / (cardHeight + GAP));
        return (int) Math.min(Math.max(1, maximum), (long) columns * visibleRows);
    }

    public int maximumScroll() {
        return Math.max(0, rows() * (cardHeight + GAP) - GAP - height);
    }

    public int x(int index) {
        return index % columns * (cardWidth + GAP);
    }

    public int y(int index, int scroll) {
        return index / columns * (cardHeight + GAP) - Math.clamp(scroll, 0, maximumScroll());
    }

    public int first(int scroll) {
        return Math.min(count, Math.clamp(scroll, 0, maximumScroll()) / (cardHeight + GAP) * columns);
    }

    public int endWithPrefetch(int scroll) {
        int step = cardHeight + GAP;
        int visibleEndRow = (Math.clamp(scroll, 0, maximumScroll()) + height + step - 1) / step;
        return Math.min(count, (visibleEndRow + 1) * columns);
    }

    public int hit(double x, double y, int scroll) {
        if (x < 0 || y < 0 || x >= width || y >= height) {
            return -1;
        }
        int column = (int) x / (cardWidth + GAP);
        double absoluteY = y + Math.clamp(scroll, 0, maximumScroll());
        int row = (int) absoluteY / (cardHeight + GAP);
        int index = row * columns + column;
        if (column >= columns || x - column * (cardWidth + GAP) >= cardWidth
                || absoluteY - row * (cardHeight + GAP) >= cardHeight || index >= count) {
            return -1;
        }
        return index;
    }

    public int scrollTo(int index) {
        return Math.clamp(index / columns * (cardHeight + GAP), 0, maximumScroll());
    }
}
