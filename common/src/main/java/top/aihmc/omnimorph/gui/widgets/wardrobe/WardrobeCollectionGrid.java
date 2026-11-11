package top.aihmc.omnimorph.gui.widgets.wardrobe;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.figuramc.figura.avatar.local.LocalAvatarFetcher;
import top.aihmc.omnimorph.backend2.library.LibraryModels;
import org.figuramc.figura.gui.widgets.Button;
import org.figuramc.figura.gui.widgets.SearchBar;
import top.aihmc.omnimorph.gui.widgets.library.ModelLibraryLayout;
import top.aihmc.omnimorph.gui.widgets.library.LibraryDropdown;
import top.aihmc.omnimorph.gui.widgets.library.ModelCardWidget;
import org.figuramc.figura.gui.widgets.lists.AbstractList;
import top.aihmc.omnimorph.model.library.LibraryThumbnailService;
import top.aihmc.omnimorph.model.outfit.LocalCosmeticInventory;
import org.figuramc.figura.utils.FiguraIdentifier;
import org.figuramc.figura.utils.FiguraText;
import org.figuramc.figura.utils.ui.UIHelper;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Function;

/** Collection cards share the catalog geometry; selecting a card never equips its resource. */
public final class WardrobeCollectionGrid extends AbstractList implements AutoCloseable {
    public record Entry(LocalAvatarFetcher.AvatarPath local, LibraryModels.CollectionEntry cloud,
                        LocalCosmeticInventory.Entry cosmetic) {
        public Entry(LocalAvatarFetcher.AvatarPath local, LibraryModels.CollectionEntry cloud) {
            this(local, cloud, null);
        }

        public String key() {
            return cosmetic != null ? cosmetic.path().toUri().toString()
                    : local != null ? local.getTheActualPathForThis().toUri().toString() : cloud.modelId().toString();
        }

        public Component name() {
            if (cosmetic != null) {
                String name = cosmetic.source() == null ? null : cosmetic.source().metadata().common().name;
                return Component.literal(name == null || name.isBlank() ? cosmetic.path().getFileName().toString() : name);
            }
            if (local != null) {
                return Component.literal(local.getName());
            }
            return cloud.model() == null ? FiguraText.of("gui.wardrobe.unavailable")
                    : Component.literal(cloud.model().displayName());
        }

        public String format() {
            if (cosmetic != null) {
                return cosmetic.source() == null ? "" : cosmetic.source().format().name();
            }
            if (local == null) {
                return cloud.model() == null || cloud.model().modelType() == null ? "" : cloud.model().modelType();
            }
            if (local.isEncryptedYsm()) {
                return "YSM_ENCRYPTED";
            }
            return local.isYsm() ? "YSM_PLAIN" : "FIGURA";
        }

        public String resourceKind() {
            if (cosmetic != null) {
                return "ACCESSORY";
            }
            return local != null || cloud.model() == null ? "AVATAR" : cloud.model().resourceKind();
        }

        public Component localDescription() {
            if (cosmetic != null) {
                if (cosmetic.source() == null) {
                    return FiguraText.of("gui.wardrobe.unavailable");
                }
                String description = cosmetic.source().metadata().common().description;
                return Component.literal(description == null ? "" : description);
            }
            return local == null ? Component.empty() : Component.literal(local.getDescription());
        }

        public boolean search(String query) {
            return local != null ? local.search(query) : name().getString().toLowerCase(Locale.ROOT).contains(query);
        }
    }

    public static final class View {
        public String query = "";
        public int format;
        public double scroll;
        public boolean filtersExpanded;
    }

    private static final ResourceLocation PLACEHOLDER = FiguraIdentifier.of("textures/gui/library_placeholder.png");
    private static final List<String> FORMATS = List.of("", "FIGURA", "YSM_PLAIN", "YSM_ENCRYPTED");
    private final View view;
    private final Consumer<Entry> select;
    private final Function<UUID, Component> cacheState;
    private LibraryThumbnailService thumbnails;
    private final Map<Integer, LibraryThumbnailService.Handle> images = new HashMap<>();
    private List<Entry> entries = List.of();
    private List<Entry> filtered = List.of();
    private ModelLibraryLayout layout;
    private Consumer<LibraryModels.Model> report = ignored -> {};
    private Consumer<LibraryModels.Model> favorite = ignored -> {};
    private String selected;
    private final LibraryDropdown formatFilter;
    private List<LibraryDropdown> resourceFilters = List.of();
    private Consumer<Boolean> filtersChanged = ignored -> {};

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
            filtersChanged.accept(view.filtersExpanded);
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

    public void setResourceFilters(LibraryDropdown source, LibraryDropdown mode, boolean expanded,
                                   Consumer<Boolean> changed) {
        children.removeAll(resourceFilters);
        resourceFilters = List.of(source, mode);
        children.remove(formatFilter);
        children.addAll(resourceFilters);
        children.add(formatFilter);
        view.filtersExpanded = expanded;
        filtersChanged = changed;
        updateHeader();
    }

    private void updateHeader() {
        formatFilter.setVisible(view.filtersExpanded);
        int selectorWidth = Math.min(100, (getWidth() - 16) / 3);
        for (int index = 0; index < resourceFilters.size(); index++) {
            var selector = resourceFilters.get(index);
            selector.setVisible(view.filtersExpanded);
            selector.setX(getX() + 4 + index * (selectorWidth + 4));
            selector.setY(getY() + 28);
            selector.setWidth(selectorWidth);
        }
        int selectorsWidth = resourceFilters.isEmpty() ? 0 : 2 * (selectorWidth + 4);
        formatFilter.setX(getX() + 4 + selectorsWidth);
        formatFilter.setWidth(Math.min(128, getWidth() - 8 - selectorsWidth));
        formatFilter.setY(getY() + 28);
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
                && entry.search(query)).toList();
        layout = null;
    }

    private int scroll() {
        return layout == null ? 0 : (int) Math.round(scrollBar.getScrollProgress() * layout.maximumScroll());
    }

    private void arrange() {
        int area = getWidth() - 18;
        int viewport = getHeight() - contentTop() - 2;
        layout = ModelLibraryLayout.of(area, viewport, filtered.size());
        scrollBar.setVisible(layout.maximumScroll() > 0);
        scrollBar.setActive(layout.maximumScroll() > 0);
        scrollBar.setScrollRatio(layout.cardHeight(), Math.max(1, layout.maximumScroll()));
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
    public void render(GuiGraphics gui, int mouseX, int mouseY, float delta) {
        arrange();
        UIHelper.blitSliced(gui, getX(), getY(), getWidth(), getHeight(), UIHelper.OUTLINE_FILL);
        super.render(gui, mouseX, mouseY, delta);
        enableScissors(gui);
        try {
            for (int index = layout.first(scroll()); index < layout.endWithPrefetch(scroll()); index++) {
                drawCard(gui, index, mouseX, mouseY);
            }
        } finally {
            gui.disableScissor();
        }
    }

    private void drawCard(GuiGraphics gui, int index, int mouseX, int mouseY) {
        Entry entry = filtered.get(index);
        int x = cardX(index);
        int y = cardY(index);
        ResourceLocation image = null;
        if (entry.local() != null) {
            var icon = entry.local().getIcon();
            image = icon == null ? null : icon.getLocation(); // The fetcher owns local icon textures.
        } else if (images.containsKey(index)) {
            image = images.get(index).capture();
        }
        LibraryModels.Model model = cloudModel(entry);
        ModelCardWidget.drawFace(gui, x, y, image, entry.format(), model, entry.name(), entry.key().equals(selected),
                mouseX, mouseY);
        boolean hovered = isInsideScissors(mouseX, mouseY)
                && layout.hit(mouseX - getX() - 2, mouseY - getY() - contentTop(), scroll()) == index;
        if (hovered && (model == null || !ModelCardWidget.isOnMenu(x, y, mouseX, mouseY))) {
            Component detail = entry.cloud() == null ? entry.localDescription() : cacheState.apply(entry.cloud().modelId());
            var tooltip = entry.name().copy().append("\n").append(detail);
            if (model != null) {
                tooltip.append("\n").append(ModelCardWidget.statusTooltip(model));
            }
            UIHelper.setTooltip(tooltip);
        }
    }

    private int cardX(int index) {
        return getX() + 2 + layout.x(index);
    }

    private int cardY(int index) {
        return getY() + contentTop() + layout.y(index, scroll());
    }

    private static LibraryModels.Model cloudModel(Entry entry) {
        return entry.cloud() == null ? null : entry.cloud().model();
    }

    /** Report and favorite actions of the cloud cards' three-dot menu. */
    public void cardActions(Consumer<LibraryModels.Model> report, Consumer<LibraryModels.Model> favorite) {
        this.report = report;
        this.favorite = favorite;
    }

    private void choose(int index) {
        Entry entry = filtered.get(index);
        selected = entry.key();
        select.accept(entry);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && isInsideScissors(mouseX, mouseY)) {
            arrange();
            int index = layout.hit(mouseX - getX() - 2, mouseY - getY() - contentTop(), scroll());
            if (index >= 0) {
                LibraryModels.Model model = cloudModel(filtered.get(index));
                if (model != null && ModelCardWidget.isOnMenu(cardX(index), cardY(index), mouseX, mouseY)) {
                    ModelCardWidget.openMenu(cardX(index), cardY(index), model, report, favorite);
                } else {
                    choose(index);
                }
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (getFocused() instanceof SearchBar) {
            return super.keyPressed(keyCode, scanCode, modifiers);
        }
        arrange();
        if (filtered.isEmpty()) {
            return super.keyPressed(keyCode, scanCode, modifiers);
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
