package top.aihmc.omnimorph.gui.widgets.lists;

import net.minecraft.client.gui.GuiGraphics;
import top.aihmc.omnimorph.backend2.library.LibraryModels;
import top.aihmc.omnimorph.gui.widgets.library.ModelCardWidget;
import top.aihmc.omnimorph.gui.widgets.library.ModelLibraryLayout;
import top.aihmc.omnimorph.model.library.LibraryThumbnailService;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

import org.figuramc.figura.gui.widgets.lists.AbstractList;
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

    /** The width available to cards beside the slim scrollbar, shared with the page budget of the owning screen. */
    public static int cardAreaWidth(int width) {
        return width - 7;
    }

    public void selected(UUID value) {
        selected = value;
    }

    private int scroll() {
        return layout == null ? 0 : (int) Math.round(scrollBar.getScrollProgress() * layout.maximumScroll());
    }

    private void arrange() {
        int area = cardAreaWidth(getWidth());
        var next = ModelLibraryLayout.of(area, getHeight() - 4, models.size());
        if (!next.equals(layout)) {
            int anchor = layout == null ? 0 : layout.first(previousScroll);
            layout = next;
            scrollBar.setScrollProgressNoAnim(layout.maximumScroll() == 0 ? 0
                    : (double) layout.scrollTo(anchor) / layout.maximumScroll());
        }
        scrollBar.setX(getX() + getWidth() - 5);
        scrollBar.setWidth(4);
        scrollBar.setY(getY() + 4);
        scrollBar.setHeight(Math.max(24, getHeight() - 8));
        scrollBar.setVisible(layout.maximumScroll() > 0);
        scrollBar.setActive(layout.maximumScroll() > 0);
        if (layout.maximumScroll() > 0) {
            scrollBar.setScrollRatio(layout.cardHeight(), layout.maximumScroll());
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
            card.selected(models.get(index).modelId().equals(selected));
            children.add(card);
        }
    }

    @Override
    public void render(GuiGraphics gui, int mouseX, int mouseY, float delta) {
        arrange();
        enableScissors(gui);
        try {
            for (var card : cards.values()) {
                card.render(gui, isInsideScissors(mouseX, mouseY) ? mouseX : -1,
                        isInsideScissors(mouseX, mouseY) ? mouseY : -1, delta);
            }
        } finally {
            gui.disableScissor();
        }
        scrollBar.render(gui, mouseX, mouseY, delta);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (!isInsideScissors(mouseX, mouseY)) {
            return false;
        }
        arrange();
        if (scrollBar.isVisible() && scrollBar.isMouseOver(mouseX, mouseY)) {
            return super.mouseClicked(mouseX, mouseY, button);
        }
        int index = layout.hit(mouseX - getX() - 2, mouseY - getY() - 2, scroll());
        ModelCardWidget card = cards.get(index);
        return card != null && card.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double x, double y, double vertical) {
        return isMouseOver(x, y) && super.mouseScrolled(x, y, vertical);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
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
        int step = switch (keyCode) {
            case 262 -> 1;
            case 263 -> -1;
            case 264 -> layout.columns();
            case 265 -> -layout.columns();
            default -> 0;
        };
        if (step == 0) {
            return super.keyPressed(keyCode, scanCode, modifiers);
        }
        int next = net.minecraft.util.Mth.clamp(index + step, 0, models.size() - 1);
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
