package top.aihmc.omnimorph.gui.widgets.wardrobe;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import top.aihmc.omnimorph.FiguraMod;
import top.aihmc.omnimorph.avatar.local.LocalAvatarFetcher;
import top.aihmc.omnimorph.backend2.library.LibraryModels;
import top.aihmc.omnimorph.gui.widgets.Button;
import top.aihmc.omnimorph.gui.widgets.SearchBar;
import top.aihmc.omnimorph.gui.widgets.library.ModelLibraryLayout;
import top.aihmc.omnimorph.gui.widgets.library.LibraryDropdown;
import top.aihmc.omnimorph.gui.widgets.lists.AbstractList;
import top.aihmc.omnimorph.model.library.LibraryThumbnailService;
import top.aihmc.omnimorph.utils.FiguraIdentifier;
import top.aihmc.omnimorph.utils.FiguraText;
import top.aihmc.omnimorph.utils.ui.UIHelper;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Function;

/** Collection cards share the catalog geometry; selecting a card never equips its resource. */
public final class WardrobeCollectionGrid extends AbstractList implements AutoCloseable {
    public record Entry(LocalAvatarFetcher.AvatarPath local, LibraryModels.CollectionEntry cloud) {
        public String key() {
            return local != null ? local.getTheActualPathForThis().toUri().toString() : cloud.modelId().toString();
        }

        public Component name() {
            if (local != null) {
                return Component.literal(local.getName());
            }
            return cloud.model() == null ? FiguraText.of("gui.wardrobe.unavailable")
                    : Component.literal(cloud.model().displayName());
        }

        public String format() {
            if (local == null) {
                return cloud.model() == null ? "" : cloud.model().modelType();
            }
            if (local.isEncryptedYsm()) {
                return "YSM_ENCRYPTED";
            }
            return local.isYsm() ? "YSM_PLAIN" : "FIGURA";
        }
    }

    public static final class View {
        public String query = "";
        public int format;
        public double scroll;
        public boolean filtersExpanded;
    }

    private static final Identifier PLACEHOLDER = new FiguraIdentifier("textures/gui/library_placeholder.png");
    private static final List<String> FORMATS = List.of("", "FIGURA", "YSM_PLAIN", "YSM_ENCRYPTED");
    private final View view;
    private final Consumer<Entry> select;
    private final Function<UUID, Component> cacheState;
    private LibraryThumbnailService thumbnails;
    private final Map<Integer, LibraryThumbnailService.Handle> images = new HashMap<>();
    private List<Entry> entries = List.of();
    private List<Entry> filtered = List.of();
    private ModelLibraryLayout layout;
    private String selected;
    private final LibraryDropdown formatFilter;

    public WardrobeCollectionGrid(int x, int y, int width, int height, View view,
                                  LibraryThumbnailService thumbnails, Consumer<Entry> select,
                                  Function<UUID, Component> cacheState) {
        super(x, y, width, height);
        this.view = view;
        this.thumbnails = thumbnails;
        this.select = select;
        this.cacheState = cacheState;
        var search = new SearchBar(x + 4, y + 4, width - 32, 20, value -> {
            view.query = value;
            view.scroll = 0;
            scrollBar.setScrollProgressNoAnim(0);
            filter();
        });
        children.add(search);
        double savedScroll = view.scroll;
        search.getField().setValue(view.query);
        view.scroll = savedScroll;
        children.add(new Button(x + width - 24, y + 4, 20, 20, Component.literal("+"),
                FiguraText.of("gui.library.filters"), button -> {
            view.filtersExpanded = !view.filtersExpanded;
            updateHeader();
        }));
        formatFilter = new LibraryDropdown(x + 4, y + 28, Math.min(128, width - 8),
                () -> List.of("all", "figura", "ysm_plain", "ysm_encrypted").stream()
                        .<Component>map(key -> FiguraText.of("gui.wardrobe.filter." + key)).toList(),
                () -> view.format, selected -> {
            view.format = selected;
            view.scroll = 0;
            scrollBar.setScrollProgressNoAnim(0);
            filter();
        });
        children.add(formatFilter);
        updateHeader();
        scrollBar.setScrollProgressNoAnim(view.scroll);
    }

    private int contentTop() {
        return view.filtersExpanded ? 54 : 30;
    }

    private void updateHeader() {
        formatFilter.setVisible(view.filtersExpanded);
        scrollBar.setY(getY() + contentTop());
        scrollBar.setHeight(Math.max(16, getHeight() - contentTop() - 4));
        updateScissors(2, contentTop(), -18, -contentTop() - 2);
        layout = null;
    }

    public void entries(List<Entry> values, String selected) {
        this.selected = selected;
        if (!entries.equals(values)) {
            entries = List.copyOf(values);
            filter();
        }
    }

    public void thumbnails(LibraryThumbnailService next) {
        if (thumbnails != next) {
            closeImages();
            thumbnails = next;
        }
    }

    private void filter() {
        closeImages();
        String query = view.query.toLowerCase(Locale.ROOT);
        filtered = entries.stream().filter(entry -> (view.format == 0 || entry.format().equals(FORMATS.get(view.format)))
                && (entry.local() != null ? entry.local().search(query)
                : entry.name().getString().toLowerCase(Locale.ROOT).contains(query))).toList();
        layout = null;
    }

    private int scroll() {
        return layout == null ? 0 : (int) Math.round(scrollBar.getScrollProgress() * layout.maximumScroll());
    }

    private void arrange() {
        layout = ModelLibraryLayout.of(getWidth() - 20, getHeight() - contentTop() - 2, filtered.size());
        scrollBar.setVisible(layout.maximumScroll() > 0);
        scrollBar.setActive(layout.maximumScroll() > 0);
        scrollBar.setScrollRatio(ModelLibraryLayout.CARD_HEIGHT, Math.max(1, layout.maximumScroll()));
        view.scroll = scrollBar.getScrollProgress();
        int first = layout.first(scroll());
        int end = layout.endWithPrefetch(scroll());
        for (int index : List.copyOf(images.keySet())) {
            if (index < first || index >= end) {
                images.remove(index).close();
            }
        }
        if (thumbnails != null) {
            for (int index = first; index < end; index++) {
                var entry = filtered.get(index);
                if (entry.cloud() != null && entry.cloud().model() != null && entry.cloud().model().imageAvailable()) {
                    int position = index;
                    images.computeIfAbsent(index, ignored -> thumbnails.acquire(filtered.get(position).cloud().modelId()));
                }
            }
        }
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor gui, int mouseX, int mouseY, float delta) {
        arrange();
        UIHelper.blitSliced(gui, getX(), getY(), getWidth(), getHeight(), UIHelper.OUTLINE_FILL);
        super.extractRenderState(gui, mouseX, mouseY, delta);
        enableScissors(gui);
        try {
            for (int index = layout.first(scroll()); index < layout.endWithPrefetch(scroll()); index++) {
                drawCard(gui, index, mouseX, mouseY);
            }
        } finally {
            gui.disableScissor();
        }
    }

    private void drawCard(GuiGraphicsExtractor gui, int index, int mouseX, int mouseY) {
        Entry entry = filtered.get(index);
        int x = getX() + 2 + layout.x(index);
        int y = getY() + contentTop() + layout.y(index, scroll());
        int width = layout.cardWidth();
        boolean active = entry.key().equals(selected);
        gui.fill(x, y, x + width, y + ModelLibraryLayout.CARD_HEIGHT, active ? 0xc0335566 : 0xb51a202c);
        UIHelper.fillOutline(gui, x, y, width, ModelLibraryLayout.CARD_HEIGHT,
                UIHelper.adjustColor(active ? FiguraMod.getAccentColor().getColor().getValue() : 0x4b5569));
        Identifier image = null;
        if (entry.local() != null) {
            var icon = entry.local().getIcon();
            image = icon == null ? null : icon.getLocation(); // The fetcher owns local icon textures.
        } else if (images.containsKey(index)) {
            image = images.get(index).capture();
        }
        int size = Math.max(1, Math.min(80, (width - 16) / 2));
        gui.blit(RenderPipelines.GUI_TEXTURED, image == null ? PLACEHOLDER : image, x + 4, y + 4,
                0, 0, size, size, size, size);
        int textX = x + size + 10;
        int textWidth = Math.max(1, width - size - 16);
        UIHelper.renderScrollingText(gui, entry.name(), textX, y + 8, textWidth, 0xffeeeeee);
        UIHelper.renderScrollingText(gui, Component.literal(entry.format()), textX, y + 24, textWidth, 0xffbac6d8);
        Component detail = entry.local() != null ? Component.literal(entry.local().getDescription())
                : cacheState.apply(entry.cloud().modelId());
        UIHelper.renderScrollingText(gui, detail, textX, y + 42, textWidth, 0xffbac6d8);
        if (entry.cloud() != null && entry.cloud().model() != null) {
            UIHelper.renderScrollingText(gui, FiguraText.of(entry.cloud().model().visibility().equals("PRIVATE")
                    ? "gui.library.private" : "gui.library.public"), textX, y + 60, textWidth, 0xffa9bbc9);
        }
        if (isInsideScissors(mouseX, mouseY) && layout.hit(mouseX - getX() - 2, mouseY - getY() - contentTop(), scroll()) == index) {
            UIHelper.setTooltip(entry.name().copy().append("\n").append(detail));
        }
    }

    private void choose(int index) {
        Entry entry = filtered.get(index);
        selected = entry.key();
        select.accept(entry);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (event.button() == 0 && isInsideScissors(event.x(), event.y())) {
            arrange();
            int index = layout.hit(event.x() - getX() - 2, event.y() - getY() - contentTop(), scroll());
            if (index >= 0) {
                choose(index);
                return true;
            }
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (getFocused() instanceof SearchBar) {
            return super.keyPressed(event);
        }
        arrange();
        if (filtered.isEmpty()) {
            return super.keyPressed(event);
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
        int current = -1;
        for (int index = 0; index < filtered.size(); index++) {
            if (filtered.get(index).key().equals(selected)) {
                current = index;
                break;
            }
        }
        int next = Math.clamp(current < 0 ? 0 : current + step, 0, filtered.size() - 1);
        choose(next);
        scrollBar.setScrollProgressNoAnim(layout.maximumScroll() == 0 ? 0
                : (double) layout.scrollTo(next) / layout.maximumScroll());
        return true;
    }

    private void closeImages() {
        images.values().forEach(LibraryThumbnailService.Handle::close);
        images.clear();
    }

    @Override
    public void close() {
        view.scroll = scrollBar.getScrollProgress();
        closeImages();
    }
}
