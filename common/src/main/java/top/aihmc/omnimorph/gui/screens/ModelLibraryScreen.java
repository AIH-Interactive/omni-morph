package top.aihmc.omnimorph.gui.screens;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import top.aihmc.omnimorph.FiguraMod;
import top.aihmc.omnimorph.backend2.NetworkStuff;
import top.aihmc.omnimorph.backend2.library.LibraryClient;
import top.aihmc.omnimorph.backend2.library.LibraryModels;
import top.aihmc.omnimorph.backend2.library.LibraryAdminSession;
import top.aihmc.omnimorph.backend2.library.LibraryAuthorization;
import top.aihmc.omnimorph.backend2.library.LibraryEquipmentVisibility;
import top.aihmc.omnimorph.gui.widgets.Button;
import top.aihmc.omnimorph.gui.widgets.EntityPreview;
import top.aihmc.omnimorph.gui.widgets.SearchBar;
import top.aihmc.omnimorph.gui.widgets.SwitchButton;
import top.aihmc.omnimorph.gui.widgets.library.LibraryActionBar;
import top.aihmc.omnimorph.gui.widgets.library.LibraryIconButton;
import top.aihmc.omnimorph.gui.widgets.library.LibrarySectionTabs;
import top.aihmc.omnimorph.gui.widgets.library.LibraryWorkspaceLayout;
import top.aihmc.omnimorph.gui.widgets.library.LibraryCheckBox;
import top.aihmc.omnimorph.gui.widgets.library.LibraryDropdown;
import top.aihmc.omnimorph.gui.widgets.lists.ModelLibraryGrid;
import top.aihmc.omnimorph.gui.screens.library.ModelUploadScreen;
import top.aihmc.omnimorph.gui.screens.library.ModelLibraryAuthorizationScreen;
import top.aihmc.omnimorph.gui.screens.library.ModelLibraryAdminLoginScreen;
import top.aihmc.omnimorph.gui.screens.library.ModelLibraryAdminScreen;
import top.aihmc.omnimorph.gui.screens.library.ModelReportScreen;
import top.aihmc.omnimorph.gui.screens.library.ModelLibraryAppealsScreen;
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
    private boolean retainingChild;
    private String state = "loading";
    private Component actionMessage = Component.empty();
    private Button previous;
    private Button next;
    private Button use;
    private Button favorite;
    private Button visibility;
    private Button delete;
    private Button uploadEntry;
    private Button adminEntry;
    private Button grants;
    private Button authorizationToggle;
    private SwitchButton showPrivate;
    private LibraryEquipmentVisibility equipmentVisibility;
    private LibraryEquipmentVisibility.State visibilityState;
    private LibraryClient.Request<LibraryEquipmentVisibility.State> visibilityRequest;
    private long visibilityGeneration;
    private LibraryAdminSession admin;
    private LibraryClient.Request<Boolean> adminRestore;
    private int leftWidth;
    private int actionsY;
    private LibrarySectionTabs sections;
    private boolean filtersExpanded;

    public ModelLibraryScreen(Screen parentScreen) {
        super(parentScreen, FiguraText.of("gui.panels.title.library"));
    }

    @Override
    protected void init() {
        boolean resuming = retainingChild;
        retainingChild = false;
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
        var layout = new LibraryWorkspaceLayout(width, height);
        leftWidth = layout.contentWidth();
        int rightX = layout.previewX();
        int rightWidth = layout.previewWidth();
        int actionsHeight = LibraryActionBar.heightFor(rightWidth, 6);
        actionsY = height - 28 - actionsHeight;
        sections = addRenderableWidget(new LibrarySectionTabs(8, 28, leftWidth, scope,
                capabilities != null && capabilities.adminLoginAvailable(), this::openSection));
        uploadEntry = sections.button(3);
        adminEntry = sections.button(4);
        addRenderableWidget(new Button(rightX, 28, (rightWidth - 4) / 2, 20, FiguraText.of("gui.library.refresh"),
                null, button -> reconnectOrRefresh()));
        int searchWidth = Math.max(80, leftWidth - 112);
        var search = new SearchBar(8, 52, searchWidth, 20, value -> {
            if (!building && !value.equals(query)) {
                query = value;
                searchChangedAt = System.nanoTime();
            }
        });
        search.getField().setMaxLength(256);
        search.getField().setValue(query);
        addRenderableWidget(search);
        addRenderableWidget(new Button(12 + searchWidth, 52, 20, 20, Component.literal("+"),
                FiguraText.of("gui.library.filters"), ignored -> {
                    filtersExpanded = !filtersExpanded;
                    rebuildWidgets();
                }));
        addRenderableWidget(new LibraryDropdown(36 + searchWidth, 52, 76,
                () -> SORTS.stream().map(value -> filterText("sort", value)).toList(), () -> sort, selected -> {
                    sort = selected;
                    filterChanged();
                }));
        int filterWidth = (leftWidth - 8) / 3;
        if (filtersExpanded) {
            addRenderableWidget(new LibraryDropdown(8, 76, filterWidth,
                    () -> categories().stream().map(value -> filterText("category", value)).toList(), () -> category, selected -> {
                        category = selected;
                        filterChanged();
                    }));
            addRenderableWidget(new LibraryDropdown(12 + filterWidth, 76, filterWidth,
                    () -> TYPES.stream().map(value -> filterText("type", value)).toList(), () -> type, selected -> {
                        type = selected;
                        filterChanged();
                    }));
            addRenderableWidget(new LibraryDropdown(16 + filterWidth * 2, 76, filterWidth,
                    () -> AUTHORIZATION.stream().map(value -> filterText("authorization", value)).toList(),
                    () -> authorization, selected -> {
                        authorization = selected;
                        filterChanged();
                    }));
        }
        if (client == null && !closed) {
            connect();
        }
        if (client != null && grid == null) {
            int gridY = filtersExpanded ? 100 : 76;
            grid = new ModelLibraryGrid(8, gridY, leftWidth, height - gridY - 28, thumbnails,
                    this::select, this::favorite, model -> {
                        preview.clear();
                        retainingChild = true;
                        minecraft.setScreen(new ModelReportScreen(this, client, model.modelId()));
                    });
        }
        if (grid != null) {
            grid.setX(8);
            grid.setY(filtersExpanded ? 100 : 76);
            grid.setWidth(leftWidth);
            grid.setHeight(height - grid.getY() - 28);
            addRenderableWidget(grid);
        }
        if (preview != null) {
            addRenderableWidget(new Button(rightX + (rightWidth + 4) / 2, 28, (rightWidth - 4) / 2, 20,
                    FiguraText.of("gui.library.my_appeals"), null, button -> {
                        if (client.current() && capabilities != null && capabilities.enabled()) {
                            preview.clear();
                            retainingChild = true;
                            minecraft.setScreen(new ModelLibraryAppealsScreen(this, client, null));
                        }
                    }));
            var widget = new EntityPreview(rightX, 52, rightWidth, Math.max(24, actionsY - 64), Math.max(12, rightWidth / 3),
                    -15, 30, preview.entity(), this);
            widget.setLibraryPreview(preview);
            addRenderableWidget(widget);
        }
        var actions = addRenderableWidget(new LibraryActionBar(rightX, actionsY, rightWidth, actionsHeight));
        grants = actions.action(new LibraryIconButton("library_grants", 1, () -> 0,
                FiguraText.of("gui.library.grants"), null, button -> {
                    if (LibraryAuthorization.available(selected)) {
                        preview.clear();
                        retainingChild = true;
                        minecraft.setScreen(new ModelLibraryAuthorizationScreen(this, client, selected.modelId(), () -> ""));
                    }
                }));
        showPrivate = actions.option(new LibraryCheckBox(0, 0, rightWidth,
                FiguraText.of("gui.library.show_private"), FiguraText.of("gui.library.show_private_hint"), ignored -> savePrivateVisibility()));
        showPrivate.setToggled(visibilityState != null && visibilityState.showing());
        authorizationToggle = actions.action(new LibraryIconButton("library_authorization", 2,
                () -> selected != null && selected.requiresAuthorization() ? 1 : 0,
                FiguraText.of("gui.library.authorization"), null, button -> {
                    if (selected != null && selected.allows("patch")) {
                        mutate(selected, "PATCH", "", Map.of("operation_id", UUID.randomUUID().toString(),
                                "expected_revision", selected.policyRevision(),
                                "requires_authorization", !selected.requiresAuthorization()));
                    }
                }));
        use = actions.action(new LibraryIconButton("library_use", 1, () -> 0, FiguraText.of("gui.library.use"),
                null, button -> enable()));
        favorite = actions.action(new LibraryIconButton("library_favorite", 2,
                () -> selected != null && selected.favorited() ? 1 : 0, FiguraText.of("gui.library.favorite"),
                null, button -> favorite(selected)));
        visibility = actions.action(new LibraryIconButton("library_visibility", 2,
                () -> selected != null && selected.visibility().equals("PUBLIC") ? 1 : 0,
                FiguraText.of("gui.library.visibility"), null, button -> toggleVisibility()));
        delete = actions.action(new LibraryIconButton("library_delete", 1, () -> 0, FiguraText.of("gui.library.delete"),
                FiguraText.of("gui.library.delete_confirm"), button -> delete()));
        previous = new Button(8, height - 24, 24, 20, Component.literal("<"),
                FiguraText.of("gui.library.previous"), button -> changePage(-1));
        next = new Button(leftWidth - 16, height - 24, 24, 20, Component.literal(">"),
                FiguraText.of("gui.library.next"), button -> changePage(1));
        addRenderableWidget(previous);
        addRenderableWidget(next);
        addRenderableWidget(new Button(rightX, height - 24, rightWidth, 20,
                FiguraText.of("gui.done"), null, button -> onClose()));
        addRenderableOnly((gui, x, y, delta) -> renderStatus(gui));
        building = false;
        if (resuming && capabilities != null) {
            refresh();
        }
        updateButtons();
    }

    private void connect() {
        try {
            client = NetworkStuff.libraryClient();
            transfers = new LibraryTransferService(client, FiguraMod.getCacheDirectory());
            equipment = new LibraryEquipmentService(client, transfers);
            equipmentVisibility = new LibraryEquipmentVisibility(client);
            thumbnails = new LibraryThumbnailService(client, FiguraMod.getCacheDirectory());
            preview = new LibraryPreviewSession(client);
            admin = new LibraryAdminSession(client, FiguraMod.getCacheDirectory());
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
                    if (value.adminLoginAvailable()) {
                        adminRestore = admin.restore();
                    }
                    refresh();
                }
            });
        } catch (java.io.IOException unavailable) {
            state = "offline";
            client = null;
        }
    }

    /** Shared reference navigation, also used when returning from an upload form. */
    public void openSection(int section) {
        if (section < 0 || section > 4 || closed) {
            return;
        }
        if (section < SCOPES.size()) {
            scope = section;
            page = 1;
            rebuildWidgets();
            refresh();
        } else if (client != null && client.current() && capabilities != null && capabilities.enabled()) {
            if (section == 3) {
                preview.clear();
                retainingChild = true;
                minecraft.setScreen(new ModelUploadScreen(this, client, capabilities, this::refresh));
            } else {
                openAdmin();
            }
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
                : name.equals("category") ? top.aihmc.omnimorph.gui.widgets.library.LibraryLabels.category(value)
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
        if (showPrivate != null) {
            showPrivate.setToggled(false);
        }
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
        readPrivateVisibility();
        updateButtons();
    }

    private void readPrivateVisibility() {
        long expected = ++visibilityGeneration;
        if (visibilityRequest != null) {
            visibilityRequest.cancel();
        }
        visibilityRequest = null;
        visibilityState = null;
        if (selected == null || !selected.visibility().equals("PRIVATE") || equipmentVisibility == null) {
            return;
        }
        UUID model = selected.modelId();
        var request = equipmentVisibility.read(model);
        visibilityRequest = request;
        request.future().whenComplete((value, failure) -> {
            if (closed || expected != visibilityGeneration || selected == null || !model.equals(selected.modelId())) {
                return;
            }
            visibilityState = failure == null ? value : null;
            showPrivate.setToggled(value != null && value.showing());
            if (failure != null) {
                actionMessage = error(failure);
            }
            updateButtons();
        });
    }

    private void savePrivateVisibility() {
        if (visibilityState == null || selected == null) {
            return;
        }
        if (!visibilityState.equipped()) {
            actionMessage = FiguraText.of("gui.library.visibility_on_enable");
            return;
        }
        long expected = ++visibilityGeneration;
        UUID model = selected.modelId();
        var request = equipmentVisibility.save(visibilityState, showPrivate.isToggled());
        visibilityRequest = request;
        request.future().whenComplete((value, failure) -> {
            if (closed || expected != visibilityGeneration || selected == null || !model.equals(selected.modelId())) {
                return;
            }
            if (failure != null) {
                actionMessage = error(failure);
                readPrivateVisibility();
            } else {
                visibilityState = value;
                showPrivate.setToggled(value.showing());
                actionMessage = FiguraText.of("gui.library.visibility_saved");
            }
            updateButtons();
        });
        updateButtons();
    }

    private void enable() {
        if (selected == null) {
            return;
        }
        if (equipmentOperation != null && equipmentOperation.future().isCompletedExceptionally()) {
            equipmentOperation.retry();
        } else {
            equipmentOperation = equipment.enable(selected.modelId(), showPrivate != null && showPrivate.isToggled()
                    && selected.visibility().equals("PRIVATE") && selected.moderationStatus().equals("ACTIVE"));
        }
        var operation = equipmentOperation;
        operation.future().whenComplete((result, failure) -> {
            if (!closed && operation == equipmentOperation) {
                actionMessage = failure == null ? FiguraText.of("gui.library.enabled") : error(failure);
                if (failure == null) {
                    readPrivateVisibility();
                }
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
                || visibilityRequest != null && !visibilityRequest.future().isDone()
                || equipmentOperation != null && !equipmentOperation.future().isDone();
        boolean available = !closed && client != null && client.current() && state.equals("ready") && !busy;
        sections.administrator(capabilities != null && capabilities.adminLoginAvailable());
        adminEntry.setActive(!closed && client != null && client.current() && capabilities != null && !busy
                && (adminRestore == null || adminRestore.future().isDone()));
        adminEntry.setMessage(FiguraText.of("gui.library.manage"));
        adminEntry.setTooltip(FiguraText.of(admin != null && admin.active() ? "gui.library.manage" : "gui.library.admin_login"));
        grants.setVisible(LibraryAuthorization.available(selected));
        grants.setActive(available && LibraryAuthorization.available(selected));
        authorizationToggle.setVisible(selected != null && selected.allows("patch"));
        authorizationToggle.setActive(available && selected != null && selected.allows("patch"));
        authorizationToggle.setMessage(FiguraText.of(selected != null && selected.requiresAuthorization()
                ? "gui.library.needs_authorization" : "gui.library.value.free"));
        boolean privateModel = selected != null && selected.visibility().equals("PRIVATE");
        showPrivate.setVisible(privateModel);
        showPrivate.setActive(available && visibilityState != null && privateModel
                && selected.moderationStatus().equals("ACTIVE") && selected.allows("equip"));
        showPrivate.setTooltip(FiguraText.of(visibilityState != null && !visibilityState.equipped()
                ? "gui.library.visibility_on_enable" : "gui.library.show_private_hint"));
        if (!privateModel || !selected.moderationStatus().equals("ACTIVE")) {
            showPrivate.setToggled(false);
        }
        if (uploadEntry != null) {
            uploadEntry.setActive(!closed && client != null && client.current() && capabilities != null
                    && capabilities.enabled() && !busy);
        }
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
        UIHelper.renderScrollingText(gui, message, leftWidth + 12, actionsY - 12,
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
        visibilityGeneration++;
        if (visibilityRequest != null) {
            visibilityRequest.cancel();
        }
        if (admin != null) {
            admin.close();
        }
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

    private void openAdmin() {
        if (admin == null || capabilities == null || !capabilities.adminLoginAvailable()) {
            return;
        }
        if (admin.active()) {
            preview.clear();
            retainingChild = true;
            minecraft.setScreen(new ModelLibraryAdminScreen(this, client, admin, capabilities));
        } else {
            if (adminRestore != null && !adminRestore.future().isDone()) {
                return;
            }
            adminRestore = admin.restore();
            adminRestore.future().whenComplete((restored, failure) -> {
                if (!closed && client.current() && minecraft.screen == this) {
                    if (failure == null && Boolean.TRUE.equals(restored) && admin.active()) {
                        openAdmin();
                    } else {
                        preview.clear();
                        retainingChild = true;
                        minecraft.setScreen(new ModelLibraryAdminLoginScreen(this, client, admin, this::openAdmin));
                    }
                }
            });
        }
    }

    @Override
    public void removed() {
        if (!retainingChild && (preview == null || !preview.isExpanded())) {
            closeServices();
        }
        super.removed();
    }
}
