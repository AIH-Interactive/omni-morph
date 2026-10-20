package top.aihmc.omnimorph.gui.widgets.wardrobe;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import top.aihmc.omnimorph.backend2.library.LibraryModels;
import top.aihmc.omnimorph.gui.widgets.Button;
import top.aihmc.omnimorph.gui.widgets.SearchBar;
import top.aihmc.omnimorph.gui.widgets.library.LibraryDropdown;
import top.aihmc.omnimorph.gui.widgets.ModelResourceBadges;
import top.aihmc.omnimorph.gui.widgets.lists.AbstractList;
import top.aihmc.omnimorph.model.library.LibraryThumbnailService;
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

    private final View view;
    private final Consumer<LibraryModels.CollectionEntry> select;
    private final java.util.function.Function<UUID, Component> cacheState;
    private List<LibraryModels.CollectionEntry> entries = List.of();
    private UUID selected;
    private LibraryThumbnailService thumbnails;
    private final Map<UUID, LibraryThumbnailService.Handle> icons = new HashMap<>();
    private final LibraryDropdown formatFilter;

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

    private void updateHeader() {
        formatFilter.setVisible(view.filtersExpanded);
        scrollBar.setY(getY() + contentTop());
        scrollBar.setHeight(Math.max(16, getHeight() - contentTop() - 4));
        updateScissors(1, contentTop(), -2, -contentTop() - 1);
    }

    public void entries(List<LibraryModels.CollectionEntry> values, UUID selected) {
        this.selected = selected;
        if (!entries.equals(values)) {
            close();
            entries = List.copyOf(values);
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
        int maximum = Math.max(0, filtered.size() * 26 - area);
        scrollBar.setVisible(maximum > 0);
        scrollBar.setScrollRatio(26, Math.max(1, maximum));
        view.scroll = scrollBar.getScrollProgress();
        int offset = (int) Math.round(view.scroll * maximum);
        int first = offset / 26;
        int end = Math.min(filtered.size(), (offset + area) / 26 + 1);
        var nextRows = new ArrayList<Integer>();
        for (int index = first; index < end; index++) {
            nextRows.add(index);
        }
        var visible = nextRows.stream().map(index -> filtered.get(index).modelId()).collect(java.util.stream.Collectors.toSet());
        for (UUID id : List.copyOf(icons.keySet())) {
            if (!visible.contains(id)) {
                icons.remove(id).close();
            }
        }
        // Only visible rows have widgets, independent of the total account collection size.
        children.removeIf(child -> child instanceof Row);
        for (int index : nextRows) {
            var entry = filtered.get(index);
            if (thumbnails != null && entry.model() != null && entry.model().imageAvailable()) {
                icons.computeIfAbsent(entry.modelId(), thumbnails::acquire);
            }
            var row = new Row(getX() + 4, getY() + contentTop() + index * 26 - offset, getWidth() - 20,
                    entry, button -> {
                        selected = entry.modelId();
                        select.accept(entry);
                    });
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
            if (values.get(index).modelId().equals(selected)) {
                current = index;
                break;
            }
        }
        int next = Math.clamp(current + (event.key() == 264 ? 1 : -1), 0, values.size() - 1);
        selected = values.get(next).modelId();
        select.accept(values.get(next));
        int maximum = Math.max(0, values.size() * 26 - Math.max(1, getHeight() - 54));
        scrollBar.setScrollProgressNoAnim(maximum == 0 ? 0 : Math.min(1d, next * 26d / maximum));
        return true;
    }

    private List<LibraryModels.CollectionEntry> filteredEntries() {
        String query = view.query.toLowerCase(Locale.ROOT);
        return entries.stream().filter(entry -> {
            var model = entry.model();
            String title = model == null ? entry.modelId().toString() : model.displayName();
            String format = model == null ? "" : model.modelType();
            return title.toLowerCase(Locale.ROOT).contains(query)
                    && (view.format == 0 || format.equals(List.of("", "FIGURA", "YSM_PLAIN", "YSM_ENCRYPTED").get(view.format)));
        }).toList();
    }

    private final class Row extends Button {
        private final LibraryModels.CollectionEntry entry;

        private Row(int x, int y, int width, LibraryModels.CollectionEntry entry, OnPress action) {
            super(x, y, width, 24, entry.model() == null ? FiguraText.of("gui.wardrobe.unavailable")
                    : Component.literal(entry.model().displayName()), null, action);
            this.entry = entry;
            setTooltip(ModelResourceBadges.tooltip(getMessage(), true, entry.model() == null ? "" : entry.model().modelType()));
            shouldHaveBackground(false);
        }

        @Override
        public void extractContents(GuiGraphicsExtractor gui, int mouseX, int mouseY, float delta) {
            super.extractContents(gui, mouseX, mouseY, delta);
            if (entry.modelId().equals(selected)) {
                UIHelper.fillOutline(gui, getX(), getY(), getWidth(), getHeight(), 0xffffffff);
            }
        }

        @Override
        protected void renderText(GuiGraphicsExtractor gui, float delta) {
            var handle = icons.get(entry.modelId());
            var icon = handle == null ? null : handle.capture();
            UIHelper.blit(gui, getX() + 2, getY() + 2, 20, 20,
                    icon == null ? new FiguraIdentifier("textures/gui/unknown_icon.png") : icon);
            ModelResourceBadges.draw(gui, getX() + 2, getY() + 2, true);
            ModelResourceBadges.drawType(gui, getX() + 12, getY() + 12,
                    entry.model() == null ? "" : entry.model().modelType());
            UIHelper.renderScrollingText(gui, getMessage(), getX() + 24, getY() + 2, getWidth() - 26, 0xffffffff);
            UIHelper.renderScrollingText(gui, cacheState.apply(entry.modelId()), getX() + 24, getY() + 13,
                    getWidth() - 26, 0xffaaaaaa);
        }

        @Override
        public boolean mouseClicked(net.minecraft.client.input.MouseButtonEvent event, boolean doubleClick) {
            return isInsideScissors(event.x(), event.y()) && super.mouseClicked(event, doubleClick);
        }
    }

    @Override
    public void close() {
        icons.values().forEach(LibraryThumbnailService.Handle::close);
        icons.clear();
    }
}
