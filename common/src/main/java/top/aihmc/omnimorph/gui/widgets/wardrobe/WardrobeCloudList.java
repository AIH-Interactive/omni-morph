package top.aihmc.omnimorph.gui.widgets.wardrobe;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import top.aihmc.omnimorph.backend2.library.LibraryModels;
import top.aihmc.omnimorph.gui.widgets.Button;
import top.aihmc.omnimorph.gui.widgets.SearchBar;
import top.aihmc.omnimorph.gui.widgets.lists.AbstractList;
import top.aihmc.omnimorph.utils.FiguraText;
import top.aihmc.omnimorph.utils.ui.UIHelper;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.function.Consumer;

/** Uses native list/search/button controls and creates only the visible collection rows. */
public final class WardrobeCloudList extends AbstractList {
    public static final class View {
        public String query = "";
        public int format;
        public double scroll;
    }

    private final View view;
    private final Consumer<LibraryModels.CollectionEntry> select;
    private final java.util.function.Function<UUID, Component> cacheState;
    private List<LibraryModels.CollectionEntry> entries = List.of();
    private UUID selected;

    public WardrobeCloudList(int x, int y, int width, int height, View view,
                             Consumer<LibraryModels.CollectionEntry> select,
                             java.util.function.Function<UUID, Component> cacheState) {
        super(x, y, width, height);
        this.view = view;
        this.select = select;
        this.cacheState = cacheState;
        var search = new SearchBar(x + 4, y + 4, width - 8, 20, value -> {
            view.query = value;
            view.scroll = 0;
            scrollBar.setScrollProgressNoAnim(0);
        });
        children.add(search);
        String initial = view.query;
        double initialScroll = view.scroll;
        search.getField().setValue(initial);
        view.scroll = initialScroll;
        children.add(new Button(x + 4, y + 28, width - 8, 20, formatLabel(), null, button -> {
            view.format = (view.format + 1) % 4;
            button.setMessage(formatLabel());
            scrollBar.setScrollProgressNoAnim(0);
        }));
        scrollBar.setY(y + 52);
        scrollBar.setHeight(Math.max(16, height - 56));
        scrollBar.setScrollProgressNoAnim(view.scroll);
        updateScissors(1, 52, -2, -53);
    }

    private Component formatLabel() {
        return FiguraText.of("gui.wardrobe.filter." + List.of("all", "figura", "ysm_plain", "ysm_encrypted").get(view.format));
    }

    public void entries(List<LibraryModels.CollectionEntry> values, UUID selected) {
        this.selected = selected;
        if (!entries.equals(values)) {
            entries = List.copyOf(values);
        }
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor gui, int mouseX, int mouseY, float delta) {
        var filtered = filteredEntries();
        int area = Math.max(1, getHeight() - 54);
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
        // Only visible rows have widgets, independent of the total account collection size.
        children.removeIf(child -> child instanceof Row);
        for (int index : nextRows) {
            var entry = filtered.get(index);
            Component title = entry.model() == null ? FiguraText.of("gui.wardrobe.unavailable")
                    : Component.literal(entry.model().displayName()).append(" · ").append(cacheState.apply(entry.modelId()));
            if (entry.modelId().equals(selected)) {
                title = title.copy().withStyle(net.minecraft.ChatFormatting.AQUA);
            }
            var row = new Row(getX() + 4, getY() + 52 + index * 26 - offset, getWidth() - 20,
                    title, button -> select.accept(entry));
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
        private Row(int x, int y, int width, Component text, OnPress action) {
            super(x, y, width, 24, text, text, action);
        }

        @Override
        public boolean mouseClicked(net.minecraft.client.input.MouseButtonEvent event, boolean doubleClick) {
            return isInsideScissors(event.x(), event.y()) && super.mouseClicked(event, doubleClick);
        }
    }
}
