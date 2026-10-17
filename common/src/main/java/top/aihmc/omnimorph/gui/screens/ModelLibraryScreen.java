package top.aihmc.omnimorph.gui.screens;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import top.aihmc.omnimorph.FiguraMod;
import top.aihmc.omnimorph.backend2.NetworkStuff;
import top.aihmc.omnimorph.backend2.library.LibraryClient;
import top.aihmc.omnimorph.backend2.library.LibraryModels;
import top.aihmc.omnimorph.gui.widgets.Button;
import top.aihmc.omnimorph.gui.widgets.EntityPreview;
import top.aihmc.omnimorph.gui.widgets.SearchBar;
import top.aihmc.omnimorph.gui.widgets.lists.ModelLibraryGrid;
import top.aihmc.omnimorph.model.library.LibraryEquipmentService;
import top.aihmc.omnimorph.model.library.LibraryPreviewSession;
import top.aihmc.omnimorph.model.library.LibraryThumbnailService;
import top.aihmc.omnimorph.model.library.LibraryTransferService;
import top.aihmc.omnimorph.utils.FiguraText;
import top.aihmc.omnimorph.utils.ui.UIHelper;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Three server-paginated catalogs sharing one detached preview and source-bound formal-use service. */
public class ModelLibraryScreen extends AbstractPanelScreen {
    private static final List<String> SCOPES = List.of("mine", "favorites", "public");
    private static final List<String> TYPES = List.of("", "FIGURA", "YSM_PLAIN", "YSM_ENCRYPTED");
    private static final List<String> AUTHORIZATION = List.of("", "required", "free");
    private static final List<String> SORTS = List.of("newest", "downloads", "favorites", "name");
    private LibraryClient client;
    private LibraryModels.Capabilities capabilities;
    private LibraryTransferService transfers;
    private LibraryEquipmentService equipment;
    private LibraryEquipmentService.Operation equipmentOperation;
    private LibraryPreviewSession preview;
    private LibraryThumbnailService thumbnails;
    private ModelLibraryGrid grid;
    private LibraryClient.Request<?> pending;
    private LibraryClient.Request<?> mutation;
    private LibraryModels.Page results;
    private LibraryModels.Model selected;
    private UUID deleteArmed;
    private long generation;
    private int scope = 2;
    private int type;
    private int authorization;
    private int sort;
    private int category;
    private int page = 1;
    private String query = "";
    private long searchChangedAt;
    private boolean building;
    private boolean closed;
    private String state = "loading";
    private Component actionMessage = Component.empty();
    private Button previous;
    private Button next;
    private Button use;
    private Button favorite;
    private Button visibility;
    private Button delete;
    private int leftWidth;

    public ModelLibraryScreen(Screen parentScreen) {
        super(parentScreen, FiguraText.of("gui.panels.title.library"));
    }

    @Override
    protected void init() {
        super.init();
        building = true;
        if (width < 300 || height < 220) {
            addRenderableOnly((gui, x, y, delta) -> UIHelper.renderCenteredScrollingText(gui,
                    FiguraText.of("gui.library.scale"), 8, height / 2, width - 16, 20, 0xffffffff));
            addRenderableWidget(new Button(width - 84, height - 24, 80, 20,
                    FiguraText.of("gui.done"), null, button -> onClose()));
            building = false;
            return;
        }
        leftWidth = (width - 18) * 7 / 10;
        int rightX = leftWidth + 12;
        int rightWidth = width - rightX - 4;
        for (int i = 0; i < SCOPES.size(); i++) {
            int target = i;
            addRenderableWidget(new Button(4 + i * (leftWidth + 2) / 3, 28, (leftWidth - 10) / 3, 20,
                    FiguraText.of("gui.library.scope." + SCOPES.get(i)), null, button -> {
                        scope = target;
                        page = 1;
                        refresh();
                    }));
        }
        addRenderableWidget(new Button(rightX, 28, rightWidth, 20, FiguraText.of("gui.library.refresh"),
                null, button -> reconnectOrRefresh()));
        var search = new SearchBar(4, 52, leftWidth, 20, value -> {
            if (!building && !value.equals(query)) {
                query = value;
                searchChangedAt = System.nanoTime();
            }
        });
        search.getField().setMaxLength(256);
        search.getField().setValue(query);
        addRenderableWidget(search);
        int filterWidth = (leftWidth - 9) / 4;
        addRenderableWidget(new Button(4, 76, filterWidth, 20, filterText("category", categoryValue()),
                null, button -> {
                    category = (category + 1) % categories().size();
                    button.setMessage(filterText("category", categoryValue()));
                    filterChanged();
                }));
        addRenderableWidget(new Button(7 + filterWidth, 76, filterWidth, 20,
                filterText("type", TYPES.get(type)), null, button -> {
                    type = (type + 1) % TYPES.size();
                    button.setMessage(filterText("type", TYPES.get(type)));
                    filterChanged();
                }));
        addRenderableWidget(new Button(10 + filterWidth * 2, 76, filterWidth, 20,
                filterText("authorization", AUTHORIZATION.get(authorization)), null, button -> {
                    authorization = (authorization + 1) % AUTHORIZATION.size();
                    button.setMessage(filterText("authorization", AUTHORIZATION.get(authorization)));
                    filterChanged();
                }));
        addRenderableWidget(new Button(13 + filterWidth * 3, 76, filterWidth, 20,
                filterText("sort", SORTS.get(sort)), null, button -> {
                    sort = (sort + 1) % SORTS.size();
                    button.setMessage(filterText("sort", SORTS.get(sort)));
                    filterChanged();
                }));
        if (client == null && !closed) {
            connect();
        }
        if (client != null && grid == null) {
            grid = new ModelLibraryGrid(4, 100, leftWidth, height - 128, thumbnails,
                    this::select, this::favorite);
        }
        if (grid != null) {
            grid.setX(4);
            grid.setY(100);
            grid.setWidth(leftWidth);
            grid.setHeight(height - 128);
            addRenderableWidget(grid);
        }
        if (preview != null) {
            int previewHeight = Math.max(24, height - 200);
            var widget = new EntityPreview(rightX, 52, rightWidth, previewHeight, Math.max(12, rightWidth / 3),
                    -15, 30, preview.entity(), this);
            widget.setLibraryPreview(preview);
            addRenderableWidget(widget);
        }
        use = new Button(rightX, height - 120, rightWidth, 20, FiguraText.of("gui.library.use"),
                null, button -> enable());
        favorite = new Button(rightX, height - 96, rightWidth, 20, FiguraText.of("gui.library.favorite"),
                null, button -> favorite(selected));
        visibility = new Button(rightX, height - 72, rightWidth, 20, FiguraText.of("gui.library.visibility"),
                null, button -> toggleVisibility());
        delete = new Button(rightX, height - 48, rightWidth, 20, FiguraText.of("gui.library.delete"),
                FiguraText.of("gui.library.delete_confirm"), button -> delete());
        addRenderableWidget(use);
        addRenderableWidget(favorite);
        addRenderableWidget(visibility);
        addRenderableWidget(delete);
        previous = new Button(4, height - 24, 24, 20, Component.literal("<"),
                FiguraText.of("gui.library.previous"), button -> changePage(-1));
        next = new Button(leftWidth - 20, height - 24, 24, 20, Component.literal(">"),
                FiguraText.of("gui.library.next"), button -> changePage(1));
        addRenderableWidget(previous);
        addRenderableWidget(next);
        addRenderableWidget(new Button(rightX, height - 24, rightWidth, 20,
                FiguraText.of("gui.done"), null, button -> onClose()));
        addRenderableOnly((gui, x, y, delta) -> renderStatus(gui));
        building = false;
        updateButtons();
    }

    private void connect() {
        try {
            client = NetworkStuff.libraryClient();
            transfers = new LibraryTransferService(client, FiguraMod.getCacheDirectory());
            equipment = new LibraryEquipmentService(client, transfers);
            thumbnails = new LibraryThumbnailService(client, FiguraMod.getCacheDirectory());
            preview = new LibraryPreviewSession(client);
            state = "loading";
            long requestGeneration = ++generation;
            var request = client.capabilities();
            pending = request;
            request.future().whenComplete((value, error) -> {
                if (closed || generation != requestGeneration) {
                    return;
                }
                if (error != null) {
                    state = "error";
                    actionMessage = error(error);
                } else if (!value.enabled()) {
                    state = "unsupported";
                } else {
                    capabilities = value;
                    refresh();
                }
            });
        } catch (java.io.IOException unavailable) {
            state = "offline";
            client = null;
        }
    }

    private void reconnectOrRefresh() {
        if (client == null || !client.current()) {
            closeServices();
            closed = false;
            client = null;
            grid = null;
            capabilities = null;
            rebuildWidgets();
        } else if (capabilities == null) {
            closeServices();
            closed = false;
            client = null;
            grid = null;
            rebuildWidgets();
        } else {
            refresh();
        }
    }

    private void filterChanged() {
        page = 1;
        refresh();
    }

    private void changePage(int direction) {
        page = Math.max(1, page + direction);
        select(null);
        refresh();
    }

    private List<String> categories() {
        var values = new ArrayList<String>();
        values.add("");
        if (capabilities != null) {
            values.addAll(capabilities.categories());
        }
        return values;
    }

    private String categoryValue() {
        return categories().get(Math.min(category, categories().size() - 1));
    }

    private Component filterText(String name, String value) {
        Component label = value.isEmpty() ? FiguraText.of("gui.library.all")
                : name.equals("category") ? Component.literal(value)
                : FiguraText.of("gui.library.value." + value.toLowerCase(java.util.Locale.ROOT));
        return FiguraText.of("gui.library.filter." + name, label);
    }

    private void refresh() {
        if (closed || client == null || capabilities == null || !client.current()) {
            return;
        }
        long requestGeneration = ++generation;
        if (pending != null) {
            pending.cancel();
        }
        state = "loading";
        results = null;
        if (grid != null) {
            grid.models(List.of(), null);
        }
        if (preview != null) {
            preview.clear();
        }
        Map<String, String> query = Map.of("scope", SCOPES.get(scope), "q", this.query,
                "category", categoryValue(), "type", TYPES.get(type),
                "authorization", AUTHORIZATION.get(authorization), "sort", SORTS.get(sort),
                "page", Integer.toString(page), "page_size", Integer.toString(capabilities.maxPageSize()));
        var request = client.list(query);
        pending = request;
        request.future().whenComplete((value, failure) -> {
            if (closed || generation != requestGeneration || !client.current()) {
                return;
            }
            if (failure != null) {
                state = "error";
                actionMessage = error(failure);
                select(null);
                return;
            }
            results = value;
            page = value.page();
            state = value.items().isEmpty() ? "empty" : "ready";
            UUID selectedId = selected == null ? null : selected.modelId();
            var current = value.items().stream().filter(model -> model.modelId().equals(selectedId)).findFirst();
            select(current.orElse(null));
            if (grid != null) {
                grid.models(value.items(), selected == null ? null : selected.modelId());
            }
            updateButtons();
        });
        updateButtons();
    }

    private void select(LibraryModels.Model model) {
        if (equipmentOperation != null && !equipmentOperation.future().isDone()) {
            equipmentOperation.close();
        }
        equipmentOperation = null;
        selected = model;
        deleteArmed = null;
        if (grid != null) {
            grid.selected(model == null ? null : model.modelId());
        }
        if (preview != null) {
            preview.clear();
            if (model != null && model.allows("preview")) {
                preview.select(model.modelId(), transfers);
            }
        }
        updateButtons();
    }

    private void enable() {
        if (selected == null) {
            return;
        }
        if (equipmentOperation != null && equipmentOperation.future().isCompletedExceptionally()) {
            equipmentOperation.retry();
        } else {
            equipmentOperation = equipment.enable(selected.modelId());
        }
        var operation = equipmentOperation;
        operation.future().whenComplete((result, failure) -> {
            if (!closed && operation == equipmentOperation) {
                actionMessage = failure == null ? FiguraText.of("gui.library.enabled") : error(failure);
                updateButtons();
            }
        });
        updateButtons();
    }

    private void favorite(LibraryModels.Model model) {
        if (model == null || !model.allows("favorite")) {
            return;
        }
        mutate(model, model.favorited() ? "DELETE" : "PUT", "/favorite",
                Map.of("operation_id", UUID.randomUUID().toString()));
    }

    private void toggleVisibility() {
        if (selected != null && selected.allows("patch")) {
            mutate(selected, "PATCH", "", Map.of("operation_id", UUID.randomUUID().toString(),
                    "expected_revision", selected.policyRevision(),
                    "visibility", selected.visibility().equals("PUBLIC") ? "PRIVATE" : "PUBLIC"));
        }
    }

    private void delete() {
        if (selected == null || !selected.allows("delete")) {
            return;
        }
        if (!selected.modelId().equals(deleteArmed)) {
            deleteArmed = selected.modelId();
            delete.setMessage(FiguraText.of("gui.library.delete_confirm"));
            return;
        }
        mutate(selected, "DELETE", "", Map.of("operation_id", UUID.randomUUID().toString(),
                "expected_revision", selected.policyRevision()));
    }

    private void mutate(LibraryModels.Model model, String method, String suffix, Map<String, Object> input) {
        if (closed || mutation != null && !mutation.future().isDone()
                || equipmentOperation != null && !equipmentOperation.future().isDone()) {
            return;
        }
        mutation = client.submit(LibraryClient.Lane.CONTROL, exchange -> exchange.json(method,
                "/api/library/v1/models/" + model.modelId() + suffix, input, Map.class));
        mutation.future().whenComplete((result, failure) -> {
            if (!closed) {
                actionMessage = failure == null ? Component.empty() : error(failure);
                refresh();
            }
        });
        updateButtons();
    }

    private void updateButtons() {
        if (use == null) {
            return;
        }
        boolean busy = mutation != null && !mutation.future().isDone()
                || equipmentOperation != null && !equipmentOperation.future().isDone();
        boolean available = !closed && client != null && client.current() && state.equals("ready") && !busy;
        use.setActive(available && selected != null && selected.allows("equip")
                && (equipmentOperation == null || equipmentOperation.future().isDone()));
        use.setMessage(FiguraText.of(equipmentOperation != null && equipmentOperation.future().isCompletedExceptionally()
                ? "gui.library.retry_use" : "gui.library.use"));
        favorite.setActive(available && selected != null && selected.allows("favorite"));
        favorite.setMessage(FiguraText.of(selected != null && selected.favorited()
                ? "gui.library.unfavorite" : "gui.library.favorite"));
        visibility.setActive(available && selected != null && selected.allows("patch"));
        visibility.setVisible(selected != null && selected.allows("patch"));
        visibility.setMessage(FiguraText.of(selected != null && selected.visibility().equals("PUBLIC")
                ? "gui.library.make_private" : "gui.library.make_public"));
        delete.setActive(available && selected != null && selected.allows("delete"));
        delete.setVisible(selected != null && selected.allows("delete"));
        delete.setMessage(FiguraText.of(deleteArmed == null ? "gui.library.delete" : "gui.library.delete_confirm"));
        boolean canPage = !closed && client != null && client.current() && !busy && results != null;
        previous.setActive(canPage && page > 1);
        next.setActive(canPage && results.hasNext());
    }

    private void renderStatus(GuiGraphicsExtractor gui) {
        if (!state.equals("ready")) {
            UIHelper.renderCenteredScrollingText(gui, FiguraText.of("gui.library.state." + state),
                    8, 110, leftWidth - 8, 24, 0xffeeeeee);
        }
        long pages = results == null ? 0 : Math.max(1, (results.total() + results.pageSize() - 1) / results.pageSize());
        UIHelper.renderCenteredScrollingText(gui, FiguraText.of("gui.library.page", page, pages,
                results == null ? 0 : results.total()), 34, height - 24, leftWidth - 64, 20, 0xffeeeeee);
        Component message = actionMessage;
        if (equipmentOperation != null && !equipmentOperation.future().isDone()) {
            message = FiguraText.of("gui.library.state.enabling");
        } else if (preview != null && preview.status().stage() == LibraryPreviewSession.Stage.LOADING) {
            message = FiguraText.of("gui.library.state.preview_loading");
        } else if (preview != null && preview.status().stage() == LibraryPreviewSession.Stage.FAILED) {
            message = FiguraText.of("gui.library.failure", preview.status().errorCode());
        }
        UIHelper.renderScrollingText(gui, message, leftWidth + 12, height - 134,
                width - leftWidth - 16, 0xffd3e7e1);
    }

    private Component error(Throwable failure) {
        String code = failure instanceof LibraryClient.Failure typed ? typed.code : "LIBRARY_UNAVAILABLE";
        return FiguraText.of("gui.library.failure", code);
    }

    @Override
    public void tick() {
        super.tick();
        if (client != null && !client.current() && !closed) {
            closeServices();
            state = "offline";
        }
        if (preview != null && !closed) {
            preview.tick();
        }
        if (searchChangedAt != 0 && System.nanoTime() - searchChangedAt >= 300_000_000L) {
            searchChangedAt = 0;
            page = 1;
            refresh();
        }
        updateButtons();
    }

    private void closeServices() {
        closed = true;
        generation++;
        if (pending != null) {
            pending.cancel();
        }
        if (mutation != null) {
            mutation.cancel();
        }
        if (grid != null) {
            grid.close();
        }
        if (thumbnails != null) {
            thumbnails.close();
        }
        if (equipment != null) {
            equipment.close();
        }
        if (preview != null) {
            preview.close();
        }
    }

    @Override
    public void removed() {
        if (preview == null || !preview.isExpanded()) {
            closeServices();
        }
        super.removed();
    }
}
