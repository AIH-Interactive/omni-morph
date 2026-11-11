package top.aihmc.omnimorph.gui.widgets.library;

/**
 * Logical-pixel geometry shared by card rendering, clipping and pointer hit tests.
 * Cards share a compact fixed size in every view; the available area only decides
 * how many columns, rows and models per page fit.
 */
public record ModelLibraryLayout(int width, int height, int count, int columns, int cardWidth,
                                 int cardHeight, int imageSize) {
    public static final int GAP = 6;
    public static final int CARD_WIDTH = 80;
    public static final int COVER_SIZE = 80;
    public static final int NAME_ROW_HEIGHT = 18;
    public static final int CARD_HEIGHT = COVER_SIZE + NAME_ROW_HEIGHT;

    /** The card grid: columns = floor((available + gap) / (card + gap)), at least one. */
    public static ModelLibraryLayout of(int width, int height, int count) {
        int available = Math.max(1, width);
        int columns = Math.max(1, (available + GAP) / (CARD_WIDTH + GAP));
        return new ModelLibraryLayout(available, Math.max(1, height), Math.max(0, count), columns,
                CARD_WIDTH, CARD_HEIGHT, COVER_SIZE);
    }

    /**
     * The page that still contains the first model of the current page after the page size changed.
     * An unknown previous size starts again at the first page.
     */
    public static int pageKeepingFirst(int page, int previousSize, int nextSize) {
        if (previousSize <= 0 || nextSize <= 0) {
            return 1;
        }
        long firstIndex = (long) (Math.max(1, page) - 1) * previousSize;
        return (int) Math.min(Integer.MAX_VALUE, firstIndex / nextSize + 1);
    }

    public int rows() {
        return (count + columns - 1) / columns;
    }

    /** Complete rows that fit the viewport, at least one, times the columns, within the server maximum. */
    public int pageCapacity(int maximum) {
        int visibleRows = Math.max(1, (height + GAP) / (cardHeight + GAP));
        return (int) Math.min(Math.max(1, maximum), (long) columns * visibleRows);
    }

    public int maximumScroll() {
        return Math.max(0, rows() * (cardHeight + GAP) - GAP - height);
    }

    private int horizontalInset() {
        int occupiedColumns = Math.min(columns, count);
        int contentWidth = occupiedColumns == 0 ? 0 : occupiedColumns * (cardWidth + GAP) - GAP;
        return Math.max(0, (width - contentWidth) / 2);
    }

    public int x(int index) {
        return horizontalInset() + index % columns * (cardWidth + GAP);
    }

    public int y(int index, int scroll) {
        return index / columns * (cardHeight + GAP) - net.minecraft.util.Mth.clamp(scroll, 0, maximumScroll());
    }

    public int first(int scroll) {
        return Math.min(count, net.minecraft.util.Mth.clamp(scroll, 0, maximumScroll()) / (cardHeight + GAP) * columns);
    }

    public int endWithPrefetch(int scroll) {
        int step = cardHeight + GAP;
        int visibleEndRow = (net.minecraft.util.Mth.clamp(scroll, 0, maximumScroll()) + height + step - 1) / step;
        return Math.min(count, (visibleEndRow + 1) * columns);
    }

    public int hit(double x, double y, int scroll) {
        if (x < 0 || y < 0 || x >= width || y >= height) {
            return -1;
        }
        x -= horizontalInset();
        if (x < 0 || y < 0) {
            return -1;
        }
        int column = (int) x / (cardWidth + GAP);
        double absoluteY = y + net.minecraft.util.Mth.clamp(scroll, 0, maximumScroll());
        int row = (int) absoluteY / (cardHeight + GAP);
        int index = row * columns + column;
        if (column >= columns || x - column * (cardWidth + GAP) >= cardWidth
                || absoluteY - row * (cardHeight + GAP) >= cardHeight || index >= count) {
            return -1;
        }
        return index;
    }

    public int scrollTo(int index) {
        return net.minecraft.util.Mth.clamp(index / columns * (cardHeight + GAP), 0, maximumScroll());
    }
}
