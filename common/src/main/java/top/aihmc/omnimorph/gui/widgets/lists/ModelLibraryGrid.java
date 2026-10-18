package top.aihmc.omnimorph.gui.widgets.lists;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import top.aihmc.omnimorph.backend2.library.LibraryModels;
import top.aihmc.omnimorph.gui.widgets.library.ModelCardWidget;
import top.aihmc.omnimorph.gui.widgets.library.ModelLibraryLayout;
import top.aihmc.omnimorph.model.library.LibraryThumbnailService;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

/** Scrollable page with visible cards and one row of thumbnail prefetch; API pagination is separate. */
public final class ModelLibraryGrid extends AbstractList implements AutoCloseable {
    private final LibraryThumbnailService thumbnails;
    private final Consumer<LibraryModels.Model> select;
    private final Consumer<LibraryModels.Model> favorite;
    private final Consumer<LibraryModels.Model> report;
    private final Map<Integer, ModelCardWidget> cards = new HashMap<>();
    private List<LibraryModels.Model> models = List.of();
    private UUID selected;
    private ModelLibraryLayout layout;
    private int previousScroll;

    public ModelLibraryGrid(int x, int y, int width, int height, LibraryThumbnailService thumbnails,
                            Consumer<LibraryModels.Model> select, Consumer<LibraryModels.Model> favorite) {
        this(x, y, width, height, thumbnails, select, favorite, ignored -> {});
    }

    public ModelLibraryGrid(int x, int y, int width, int height, LibraryThumbnailService thumbnails,
                            Consumer<LibraryModels.Model> select, Consumer<LibraryModels.Model> favorite,
                            Consumer<LibraryModels.Model> report) {
        super(x, y, width, height);
        this.thumbnails = thumbnails;
        this.select = select;
        this.favorite = favorite;
        this.report = report;
    }

    public void models(List<LibraryModels.Model> models, UUID selected) {
        closeCards();
        this.models = List.copyOf(models);
        this.selected = selected;
        layout = null;
        scrollBar.setScrollProgressNoAnim(0);
    }

    public void selected(UUID value) {
        selected = value;
    }

    private int scroll() {
        return layout == null ? 0 : (int) Math.round(scrollBar.getScrollProgress() * layout.maximumScroll());
    }

    private void arrange() {
        var next = ModelLibraryLayout.of(getWidth() - 18, getHeight() - 4, models.size());
        if (!next.equals(layout)) {
            int anchor = layout == null ? 0 : layout.first(previousScroll);
            layout = next;
            scrollBar.setScrollProgressNoAnim(layout.maximumScroll() == 0 ? 0
                    : (double) layout.scrollTo(anchor) / layout.maximumScroll());
        }
        scrollBar.setX(getX() + getWidth() - 14);
        scrollBar.setY(getY() + 4);
        scrollBar.setHeight(Math.max(24, getHeight() - 8));
        scrollBar.setVisible(layout.maximumScroll() > 0);
        scrollBar.setActive(layout.maximumScroll() > 0);
        if (layout.maximumScroll() > 0) {
            scrollBar.setScrollRatio(ModelLibraryLayout.CARD_HEIGHT, layout.maximumScroll());
        }
        int offset = scroll();
        previousScroll = offset;
        int first = layout.first(offset);
        int end = layout.endWithPrefetch(offset);
        for (int index : List.copyOf(cards.keySet())) {
            if (index < first || index >= end) {
                cards.remove(index).close();
            }
        }
        children.clear();
        children.add(scrollBar);
        for (int index = first; index < end; index++) {
            int position = index;
            var card = cards.computeIfAbsent(index,
                    ignored -> new ModelCardWidget(models.get(position), thumbnails, model -> {
                        selected = model.modelId().equals(selected) ? null : model.modelId();
                        select.accept(selected == null ? null : model);
                    }, favorite, report));
            card.setX(getX() + 2 + layout.x(index));
            card.setY(getY() + 2 + layout.y(index, offset));
            card.setWidth(layout.cardWidth());
            card.selected(models.get(index).modelId().equals(selected));
            children.add(card);
        }
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor gui, int mouseX, int mouseY, float delta) {
        arrange();
        enableScissors(gui);
        try {
            for (var card : cards.values()) {
                card.extractRenderState(gui, isInsideScissors(mouseX, mouseY) ? mouseX : -1,
                        isInsideScissors(mouseX, mouseY) ? mouseY : -1, delta);
            }
        } finally {
            gui.disableScissor();
        }
        scrollBar.extractRenderState(gui, mouseX, mouseY, delta);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (!isInsideScissors(event.x(), event.y())) {
            return false;
        }
        arrange();
        if (scrollBar.isVisible() && scrollBar.isMouseOver(event.x(), event.y())) {
            return super.mouseClicked(event, doubleClick);
        }
        int index = layout.hit(event.x() - getX() - 2, event.y() - getY() - 2, scroll());
        ModelCardWidget card = cards.get(index);
        return card != null && card.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseScrolled(double x, double y, double horizontal, double vertical) {
        return isMouseOver(x, y) && super.mouseScrolled(x, y, horizontal, vertical);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (models.isEmpty() || layout == null) {
            return false;
        }
        int index = 0;
        for (int i = 0; i < models.size(); i++) {
            if (models.get(i).modelId().equals(selected)) {
                index = i;
                break;
            }
        }
        int step = switch (event.key()) {
            case 262 -> 1;
            case 263 -> -1;
            case 264 -> layout.columns();
            case 265 -> -layout.columns();
            default -> 0;
        };
        if (step == 0) {
            return super.keyPressed(event);
        }
        int next = Math.clamp(index + step, 0, models.size() - 1);
        selected = models.get(next).modelId();
        select.accept(models.get(next));
        int offset = layout.scrollTo(next);
        scrollBar.setScrollProgressNoAnim(layout.maximumScroll() == 0 ? 0
                : (double) offset / layout.maximumScroll());
        return true;
    }

    private void closeCards() {
        cards.values().forEach(ModelCardWidget::close);
        cards.clear();
        children.clear();
        children.add(scrollBar);
    }

    @Override
    public void close() {
        closeCards();
        models = List.of();
        selected = null;
        layout = null;
    }
}
