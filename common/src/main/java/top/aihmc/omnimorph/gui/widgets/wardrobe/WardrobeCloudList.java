package top.aihmc.omnimorph.gui.widgets.wardrobe;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import top.aihmc.omnimorph.backend2.library.LibraryModels;
import top.aihmc.omnimorph.avatar.local.LocalAvatarFetcher;
import top.aihmc.omnimorph.gui.widgets.Button;
import top.aihmc.omnimorph.gui.widgets.ContextMenu;
import top.aihmc.omnimorph.gui.widgets.SearchBar;
import top.aihmc.omnimorph.gui.widgets.library.LibraryDropdown;
import top.aihmc.omnimorph.gui.widgets.ModelResourceBadges;
import top.aihmc.omnimorph.gui.widgets.lists.AbstractList;
import top.aihmc.omnimorph.model.library.LibraryThumbnailService;
import top.aihmc.omnimorph.model.outfit.LocalCosmeticInventory;
import top.aihmc.omnimorph.utils.FiguraIdentifier;
import top.aihmc.omnimorph.utils.FiguraText;
import top.aihmc.omnimorph.utils.ui.UIHelper;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

/** Uses native list/search/button controls and creates only the visible collection rows. */
public final class WardrobeCloudList extends AbstractList implements AutoCloseable {
    public static final class View {
        public String query = "";
        public int format;
        public double scroll;
        public boolean filtersExpanded;
    }

    public static final int ROW_HEIGHT = 28;
    private static final int ROW_STRIDE = ROW_HEIGHT + 2;
    private Consumer<LibraryModels.CollectionEntry> outfitRemoval;
    private final View view;
    private final Consumer<LibraryModels.CollectionEntry> select;
    private final java.util.function.Function<UUID, Component> cacheState;
    private List<LibraryModels.CollectionEntry> entries = List.of();
    private UUID selected;
    private List<LocalAvatarFetcher.AvatarPath> localEntries = List.of();
    private Consumer<LocalAvatarFetcher.AvatarPath> selectLocal;
    private Consumer<LocalAvatarFetcher.AvatarPath> removeLocal;
    private java.nio.file.Path selectedLocal;
    private List<LocalCosmeticInventory.Entry> cosmetics = List.of();
    private Consumer<LocalCosmeticInventory.Entry> selectCosmetic;
    private Consumer<LocalCosmeticInventory.Entry> removeCosmetic;
    private LibraryThumbnailService thumbnails;
    private final Map<UUID, LibraryThumbnailService.Handle> icons = new HashMap<>();
    private final LibraryDropdown formatFilter;
    private List<LibraryDropdown> resourceFilters = List.of();
    private Consumer<Boolean> filtersChanged = ignored -> {};

    public WardrobeCloudList(int x, int y, int width, int height, View view,
                             Consumer<LibraryModels.CollectionEntry> select,
                             java.util.function.Function<UUID, Component> cacheState) {
        super(x, y, width, height);
        this.view = view;
        this.select = select;
        this.cacheState = cacheState;
        var search = new SearchBar(x + 4, y + 4, width - 32, 20, value -> {
            view.query = value;
            view.scroll = 0;
            scrollBar.setScrollProgressNoAnim(0);
        });
        children.add(search);
        String initial = view.query;
        double initialScroll = view.scroll;
        search.getField().setValue(initial);
        view.scroll = initialScroll;
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
        });
        children.add(formatFilter);
        updateHeader();
        scrollBar.setScrollProgressNoAnim(view.scroll);
    }

    private int contentTop() {
        return view.filtersExpanded ? 52 : 28;
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
        updateScissors(1, contentTop(), -2, -contentTop() - 1);
    }

    public void setOutfitRemoval(Consumer<LibraryModels.CollectionEntry> remove) {
        outfitRemoval = remove;
    }

    public void entries(List<LibraryModels.CollectionEntry> values, UUID selected) {
        this.selected = selected;
        if (!entries.equals(values)) {
            close();
            entries = List.copyOf(values);
        }
    }

    public void localEntries(List<LocalAvatarFetcher.AvatarPath> values,
                             Consumer<LocalAvatarFetcher.AvatarPath> select,
                             Consumer<LocalAvatarFetcher.AvatarPath> remove, java.nio.file.Path selected) {
        localEntries = List.copyOf(values);
        selectLocal = select;
        removeLocal = remove;
        selectedLocal = selected;
    }

    private void choose(WardrobeCollectionGrid.Entry entry) {
        if (entry.cosmetic() != null) {
            selectedLocal = entry.cosmetic().path();
            selected = null;
            selectCosmetic.accept(entry.cosmetic());
        } else if (entry.local() != null) {
            selectedLocal = entry.local().getTheActualPathForThis();
            selected = null;
            selectLocal.accept(entry.local());
        } else {
            selected = entry.cloud().modelId();
            selectedLocal = null;
            select.accept(entry.cloud());
        }
    }

    private boolean isSelected(WardrobeCollectionGrid.Entry entry) {
        return entry.cosmetic() != null ? entry.cosmetic().path().equals(selectedLocal)
                : entry.local() != null ? entry.local().getTheActualPathForThis().equals(selectedLocal)
                : entry.cloud().modelId().equals(selected);
    }

    public void cosmetics(List<LocalCosmeticInventory.Entry> values, Consumer<LocalCosmeticInventory.Entry> select,
                           Consumer<LocalCosmeticInventory.Entry> remove, java.nio.file.Path selected) {
        cosmetics = List.copyOf(values);
        selectCosmetic = select;
        removeCosmetic = remove;
        if (selected != null) {
            selectedLocal = selected;
        }
    }

    public void thumbnails(LibraryThumbnailService next) {
        if (thumbnails != next) {
            close();
            thumbnails = next;
        }
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor gui, int mouseX, int mouseY, float delta) {
        var filtered = filteredEntries();
        int area = Math.max(1, getHeight() - contentTop() - 2);
        int maximum = Math.max(0, filtered.size() * ROW_STRIDE - area);
        scrollBar.setVisible(maximum > 0);
        scrollBar.setScrollRatio(ROW_STRIDE, Math.max(1, maximum));
        view.scroll = scrollBar.getScrollProgress();
        int offset = (int) Math.round(view.scroll * maximum);
        int first = offset / ROW_STRIDE;
        int end = Math.min(filtered.size(), (offset + area) / ROW_STRIDE + 1);
        var nextRows = new ArrayList<Integer>();
        for (int index = first; index < end; index++) {
            nextRows.add(index);
        }
        var visible = nextRows.stream().map(index -> filtered.get(index).cloud()).filter(java.util.Objects::nonNull)
                .map(LibraryModels.CollectionEntry::modelId).collect(java.util.stream.Collectors.toSet());
        for (UUID id : List.copyOf(icons.keySet())) {
            if (!visible.contains(id)) {
                icons.remove(id).close();
            }
        }
        // Only visible rows have widgets, independent of the total account collection size.
        children.removeIf(child -> child instanceof Row);
        for (int index : nextRows) {
            var entry = filtered.get(index);
            var cloud = entry.cloud();
            if (thumbnails != null && cloud != null && cloud.model() != null && cloud.model().imageAvailable()) {
                icons.computeIfAbsent(cloud.modelId(), thumbnails::acquire);
            }
            var row = new Row(getX() + 4, getY() + contentTop() + index * ROW_STRIDE - offset, getWidth() - 20,
                    entry, button -> choose(entry));
            children.add(row);
        }
        UIHelper.blitSliced(gui, getX(), getY(), getWidth(), getHeight(), UIHelper.OUTLINE_FILL);
        super.extractRenderState(gui, mouseX, mouseY, delta);
        enableScissors(gui);
        try {
            for (var child : children) {
                if (child instanceof Row row) {
                    row.extractRenderState(gui, isInsideScissors(mouseX, mouseY) ? mouseX : -1,
                            isInsideScissors(mouseX, mouseY) ? mouseY : -1, delta);
                }
            }
        } finally {
            gui.disableScissor();
        }
    }

    @Override
    public List<? extends net.minecraft.client.gui.components.events.GuiEventListener> contents() {
        return children.stream().filter(child -> child instanceof Row).toList();
    }

    @Override
    public boolean keyPressed(net.minecraft.client.input.KeyEvent event) {
        if (event.key() != 264 && event.key() != 265) {
            return super.keyPressed(event);
        }
        var values = filteredEntries();
        if (values.isEmpty()) {
            return false;
        }
        int current = -1;
        for (int index = 0; index < values.size(); index++) {
            if (isSelected(values.get(index))) {
                current = index;
                break;
            }
        }
        int next = Math.clamp(current + (event.key() == 264 ? 1 : -1), 0, values.size() - 1);
        choose(values.get(next));
        int maximum = Math.max(0, values.size() * ROW_STRIDE - Math.max(1, getHeight() - contentTop() - 2));
        scrollBar.setScrollProgressNoAnim(maximum == 0 ? 0 : Math.min(1d, next * (double) ROW_STRIDE / maximum));
        return true;
    }

    private List<WardrobeCollectionGrid.Entry> filteredEntries() {
        String query = view.query.toLowerCase(Locale.ROOT);
        var resources = new ArrayList<WardrobeCollectionGrid.Entry>();
        localEntries.forEach(entry -> resources.add(new WardrobeCollectionGrid.Entry(entry, null)));
        cosmetics.forEach(entry -> resources.add(new WardrobeCollectionGrid.Entry(null, null, entry)));
        entries.forEach(entry -> resources.add(new WardrobeCollectionGrid.Entry(null, entry)));
        return resources.stream().filter(entry -> {
            boolean matches = entry.search(query);
            return matches && (view.format == 0
                    || entry.format().equals(List.of("", "FIGURA", "YSM_PLAIN", "YSM_ENCRYPTED").get(view.format)));
        }).toList();
    }

    private final class Row extends Button {
        private final WardrobeCollectionGrid.Entry entry;

        private Row(int x, int y, int width, WardrobeCollectionGrid.Entry entry, OnPress action) {
            super(x, y, width, ROW_HEIGHT, entry.name(), null, action);
            this.entry = entry;
            setTooltip(ModelResourceBadges.tooltip(getMessage(), entry.cloud() != null, entry.format()));
            shouldHaveBackground(false);
        }

        @Override
        public void extractContents(GuiGraphicsExtractor gui, int mouseX, int mouseY, float delta) {
            super.extractContents(gui, mouseX, mouseY, delta);
            if (isSelected(entry)) {
                UIHelper.fillOutline(gui, getX(), getY(), getWidth(), getHeight(), 0xffffffff);
            }
        }

        @Override
        protected void renderText(GuiGraphicsExtractor gui, float delta) {
            var handle = entry.cloud() == null ? null : icons.get(entry.cloud().modelId());
            var localIcon = entry.local() == null ? null : entry.local().getIcon();
            var icon = localIcon != null ? localIcon.getLocation() : handle == null ? null : handle.capture();
            UIHelper.blit(gui, getX() + 2, getY() + 4, 20, 20,
                    icon == null ? new FiguraIdentifier("textures/gui/unknown_icon.png") : icon);
            ModelResourceBadges.drawType(gui, getX() + 2, getY() + 4,
                    entry.format());
            UIHelper.renderScrollingText(gui, getMessage(), getX() + 24, getY() + 4, getWidth() - 26, 0xffffffff);
            Component detail = entry.cloud() == null ? entry.localDescription() : cacheState.apply(entry.cloud().modelId());
            UIHelper.renderScrollingText(gui, detail, getX() + 24, getY() + 15,
                    getWidth() - 26, 0xffaaaaaa);
        }

        @Override
        public boolean mouseClicked(net.minecraft.client.input.MouseButtonEvent event, boolean doubleClick) {
            if (!isInsideScissors(event.x(), event.y())) {
                return false;
            }
            boolean removable = entry.cosmetic() != null ? removeCosmetic != null
                    : entry.local() != null ? removeLocal != null : outfitRemoval != null;
            if (event.button() == 1 && removable && isMouseOver(event.x(), event.y())) {
                var menu = new ContextMenu(this);
                menu.addAction(FiguraText.of("gui.wardrobe.remove_outfit"), null, ignored -> {
                    if (entry.cosmetic() != null) {
                        removeCosmetic.accept(entry.cosmetic());
                    } else if (entry.local() != null) {
                        removeLocal.accept(entry.local());
                    } else {
                        outfitRemoval.accept(entry.cloud());
                    }
                    menu.setVisible(false);
                });
                menu.setX((int) event.x());
                menu.setY((int) event.y());
                menu.setVisible(true);
                UIHelper.setContext(menu);
                return true;
            }
            return super.mouseClicked(event, doubleClick);
        }
    }

    @Override
    public void close() {
        icons.values().forEach(LibraryThumbnailService.Handle::close);
        icons.clear();
    }
}
