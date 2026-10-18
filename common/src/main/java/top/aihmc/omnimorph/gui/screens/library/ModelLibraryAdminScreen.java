package top.aihmc.omnimorph.gui.screens.library;

import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import top.aihmc.omnimorph.backend2.library.LibraryAdminSession;
import top.aihmc.omnimorph.backend2.library.LibraryAuthorization;
import top.aihmc.omnimorph.backend2.library.LibraryClient;
import top.aihmc.omnimorph.backend2.library.LibraryModels;
import top.aihmc.omnimorph.gui.screens.ModelLibraryScreen;
import top.aihmc.omnimorph.gui.widgets.Button;
import top.aihmc.omnimorph.gui.widgets.SearchBar;
import top.aihmc.omnimorph.gui.widgets.library.LibraryRowList;
import top.aihmc.omnimorph.gui.widgets.library.LibrarySectionTabs;
import top.aihmc.omnimorph.model.library.LibraryThumbnailService;
import top.aihmc.omnimorph.FiguraMod;
import top.aihmc.omnimorph.utils.FiguraIdentifier;
import top.aihmc.omnimorph.utils.FiguraText;
import top.aihmc.omnimorph.utils.ui.UIHelper;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Management data is requested only with a verified, current administrator session. */
public final class ModelLibraryAdminScreen extends LibraryFormScreen {
    private static final List<String> STATUSES = List.of("", "ACTIVE", "BLOCKED", "DELETED");
    private final LibraryClient client;
    private final LibraryAdminSession admin;
    private final LibraryModels.Capabilities capabilities;
    private final LibraryThumbnailService thumbnails;
    private final Map<UUID, LibraryThumbnailService.Handle> images = new java.util.HashMap<>();
    private LibraryClient.Request<?> pending;
    private LibraryRowList rows;
    private LibraryModels.Page results;
    private LibraryModels.Model selected;
    private Button grants;
    private Button block;
    private Button delete;
    private Button previous;
    private Button next;
    private String query = "";
    private int category;
    private int type;
    private int status;
    private int page = 1;
    private long generation;
    private long searchChanged;
    private String armed;
    private Mutation retry;
    private boolean building;
    private boolean closed;
    private boolean retainingChild;
    private Component message = Component.empty();

    private record Mutation(UUID model, String method, String suffix, UUID operation, long revision) {}

    public ModelLibraryAdminScreen(Screen parent, LibraryClient client, LibraryAdminSession admin,
                                   LibraryModels.Capabilities capabilities) {
        super(parent, FiguraText.of("gui.library.manage"));
        this.client = client;
        this.admin = admin;
        this.capabilities = capabilities;
        thumbnails = new LibraryThumbnailService(client, FiguraMod.getCacheDirectory(), admin::token);
    }

    @Override
    public Class<? extends Screen> getSelectedPanel() {
        return ModelLibraryScreen.class;
    }

    @Override
    protected void init() {
        if (!initForm()) {
            return;
        }
        retainingChild = false;
        building = true;
        int area = Math.max(40, width - 16);
        addRenderableWidget(new LibrarySectionTabs(8, 28, area, 4, true, section -> {
            if (!busy() && parentScreen instanceof ModelLibraryScreen catalog) {
                minecraft.setScreen(catalog);
                catalog.openSection(section);
            }
        }));
        int searchWidth = Math.max(80, area / 2 - 4);
        var search = addRenderableWidget(new SearchBar(8, 52, searchWidth, 20, value -> {
            if (!building && !query.equals(value)) {
                query = value;
                searchChanged = System.nanoTime();
            }
        }));
        search.getField().setMaxLength(256);
        search.getField().setValue(query);
        int column = Math.max(20, (area - 8) / 3);
        int filterWidth = Math.max(24, (area - searchWidth - 12) / 3);
        addRenderableWidget(new Button(12 + searchWidth, 52, filterWidth, 20, filter("status", STATUSES.get(status)), null, button -> {
            status = (status + 1) % STATUSES.size();
            button.setMessage(filter("status", STATUSES.get(status)));
            page = 1;
            refresh();
        }));
        addRenderableWidget(new Button(16 + searchWidth + filterWidth, 52, filterWidth, 20, filter("category", category()), null, button -> {
            category = (category + 1) % (capabilities.categories().size() + 1);
            button.setMessage(filter("category", category()));
            page = 1;
            refresh();
        }));
        addRenderableWidget(new Button(20 + searchWidth + filterWidth * 2, 52, filterWidth, 20, filter("type", type()), null, button -> {
            type = (type + 1) % (capabilities.modelTypes().size() + 1);
            button.setMessage(filter("type", type()));
            page = 1;
            refresh();
        }));
        rows = addRenderableWidget(new LibraryRowList(8, 96, area, Math.max(24, height - 172)));
        rows.columns(List.of(FiguraText.of("gui.library.cover"), FiguraText.of("gui.library.display_name"),
                FiguraText.of("gui.library.authors"), FiguraText.of("gui.library.uploader"),
                FiguraText.of("gui.library.status"), FiguraText.of("gui.library.downloads"),
                FiguraText.of("gui.library.actions")), 2, 6, 4, 4, 3, 2, 5);
        grants = addRenderableWidget(new Button(8, height - 72, column, 20,
                FiguraText.of("gui.library.grants"), null, ignored -> {
                    if (LibraryAuthorization.available(selected) && !busy() && admin.active()) {
                        retainingChild = true;
                        minecraft.setScreen(new ModelLibraryAuthorizationScreen(this, client, selected.modelId(), admin));
                    }
                }));
        block = addRenderableWidget(new Button(12 + column, height - 72, column, 20,
                FiguraText.of("gui.library.block"), null, ignored -> moderate(false)));
        delete = addRenderableWidget(new Button(16 + column * 2, height - 72, column, 20,
                FiguraText.of("gui.library.delete"), null, ignored -> moderate(true)));
        previous = addRenderableWidget(new Button(8, height - 48, 36, 20, Component.literal("<"), null,
                ignored -> {
                    page = Math.max(1, page - 1);
                    refresh();
                }));
        next = addRenderableWidget(new Button(48, height - 48, 36, 20, Component.literal(">"), null,
                ignored -> {
                    page++;
                    refresh();
                }));
        int managementWidth = Math.max(20, (area - 84) / 2);
        addRenderableWidget(new Button(88, height - 48, managementWidth, 20,
                FiguraText.of("gui.library.reports"), null, ignored -> {
                    if (admin.active() && !busy()) {
                        retainingChild = true;
                        minecraft.setScreen(new ModelLibraryReportsScreen(this, client, admin, this::inspectReported));
                    }
                }));
        addRenderableWidget(new Button(92 + managementWidth, height - 48, managementWidth, 20,
                FiguraText.of("gui.library.manage_appeals"), null, ignored -> {
                    if (admin.active() && !busy()) {
                        retainingChild = true;
                        minecraft.setScreen(new ModelLibraryAppealsScreen(this, client, admin));
                    }
                }));
        addRenderableWidget(new Button(8, height - 24, (area - 4) / 2, 20,
                FiguraText.of("gui.library.wardrobe_manage"), null, ignored -> {
                    if (admin.active() && !busy()) {
                        retainingChild = true;
                        minecraft.setScreen(new ModelLibraryWardrobeScreen(this, client, admin));
                    }
                }));
        addRenderableWidget(new Button(8, 76, 88, 16,
                FiguraText.of("gui.library.logout"), null, ignored -> logout()));
        addRenderableWidget(new Button(12 + (area - 4) / 2, height - 24, (area - 4) / 2, 20,
                FiguraText.of("gui.done"), null, ignored -> onClose()));
        addRenderableOnly((gui, x, y, delta) -> UIHelper.renderScrollingText(gui, message, 100, 78, Math.max(20, area - 92), 0xffd3e7e1));
        building = false;
        refresh();
        updateButtons();
    }

    private String category() {
        return category == 0 ? "" : capabilities.categories().get(category - 1);
    }

    private String type() {
        return type == 0 ? "" : capabilities.modelTypes().get(type - 1);
    }

    private Component filter(String label, String value) {
        return FiguraText.of("gui.library.filter." + label,
                value.isEmpty() ? FiguraText.of("gui.library.all") : Component.literal(value));
    }

    private boolean busy() {
        return pending != null && !pending.future().isDone();
    }

    private void refresh() {
        if (closed || !admin.active()) {
            return;
        }
        long requestGeneration = ++generation;
        if (pending != null) {
            pending.cancel();
        }
        selected = null;
        armed = null;
        results = null;
        images.values().forEach(LibraryThumbnailService.Handle::close);
        images.clear();
        rows.rows(List.of());
        StringBuilder path = new StringBuilder("/api/library/v1/models?scope=admin&page_size=8");
        Map<String, String> queryValues = Map.of("q", query, "status", STATUSES.get(status), "type", type(),
                "category", category(), "page", Integer.toString(page));
        queryValues.forEach((key, value) -> path.append('&').append(key).append('=')
                .append(URLEncoder.encode(value, StandardCharsets.UTF_8)));
        var request = client.submit(LibraryClient.Lane.CONTROL, exchange -> {
            exchange.adminToken(admin.token());
            return exchange.json("GET", path.toString(), null, LibraryModels.Page.class);
        });
        pending = request;
        request.future().whenComplete((value, failure) -> {
            if (closed || generation != requestGeneration) {
                return;
            }
            if (failure != null) {
                failed(failure);
                return;
            }
            results = value;
            message = FiguraText.of("gui.library.page", value.page(), Math.max(1, (value.total() + 7) / 8), value.total());
            rows.rows(value.items().stream().map(this::tableRow).toList());
        });
    }

    private LibraryRowList.Row tableRow(LibraryModels.Model model) {
        var image = model.imageAvailable() && model.moderationStatus().equals("ACTIVE")
                ? images.computeIfAbsent(model.modelId(), thumbnails::acquire) : null;
        var actions = new java.util.ArrayList<LibraryRowList.Action>();
        if (LibraryAuthorization.available(model)) {
            actions.add(new LibraryRowList.Action(FiguraText.of("gui.library.grants"), FiguraText.of("gui.library.grants"), true, () -> {
                if (!busy() && admin.active()) {
                    retainingChild = true;
                    minecraft.setScreen(new ModelLibraryAuthorizationScreen(this, client, model.modelId(), admin));
                }
            }, LibraryRowList.Icon.GRANT));
        }
        boolean mutable = !model.moderationStatus().equals("DELETED");
        actions.add(new LibraryRowList.Action(FiguraText.of("gui.library.block"),
                FiguraText.of(model.moderationStatus().equals("BLOCKED") ? "gui.library.unblock" : "gui.library.block"),
                mutable, () -> moderate(false), LibraryRowList.Icon.BLOCK));
        actions.add(new LibraryRowList.Action(FiguraText.of("gui.library.delete"), FiguraText.of("gui.library.delete"),
                mutable, () -> moderate(true), LibraryRowList.Icon.DELETE));
        return new LibraryRowList.Row(Component.empty(),
                Component.literal(model.modelId() + "\n" + model.uploader().uuid()), true,
                () -> {
                    if (selected == null || !selected.modelId().equals(model.modelId())) {
                        armed = null;
                    }
                    selected = model;
                    updateButtons();
                }, List.of(Component.empty(), Component.literal(model.displayName()),
                    model.authors().isEmpty() ? FiguraText.of("gui.library.unknown_author")
                            : Component.literal(model.authors().getFirst()),
                    Component.literal(model.uploader().name()),
                    FiguraText.of("gui.library.value." + model.moderationStatus().toLowerCase(java.util.Locale.ROOT)),
                    Component.literal(Long.toString(model.downloads()))),
                () -> {
                    var texture = image == null ? null : image.capture();
                    return texture == null ? new FiguraIdentifier("textures/gui/library_placeholder.png") : texture;
                }, actions);
    }

    private void moderate(boolean remove) {
        if (busy() || selected == null || !admin.active()) {
            return;
        }
        String key = selected.modelId() + ":" + remove;
        if (!key.equals(armed)) {
            armed = key;
            message = FiguraText.of("gui.library.confirm_moderation", selected.displayName());
            return;
        }
        String method = remove ? "DELETE" : selected.moderationStatus().equals("BLOCKED") ? "DELETE" : "PUT";
        String suffix = remove ? "" : "/block";
        if (retry == null || !retry.model().equals(selected.modelId()) || !retry.method().equals(method)
                || !retry.suffix().equals(suffix)) {
            retry = new Mutation(selected.modelId(), method, suffix, UUID.randomUUID(), selected.policyRevision());
        }
        Mutation input = retry;
        long requestGeneration = ++generation;
        var request = client.submit(LibraryClient.Lane.CONTROL, exchange -> {
            exchange.adminToken(admin.token());
            String root = input.suffix().isEmpty() ? "/api/library/v1/models/" : "/api/library/v1/admin/models/";
            return exchange.json(input.method(), root + input.model() + input.suffix(),
                    Map.of("operation_id", input.operation().toString(), "expected_revision", input.revision()), Map.class);
        });
        pending = request;
        request.future().whenComplete((value, failure) -> {
            if (!closed && generation == requestGeneration) {
                if (failure != null) {
                    failed(failure);
                } else {
                    retry = null;
                    refresh();
                }
            }
        });
    }

    private void inspectReported(UUID model) {
        long expected = ++generation;
        if (pending != null) {
            pending.cancel();
        }
        var request = client.submit(LibraryClient.Lane.CONTROL, exchange -> {
            exchange.adminToken(admin.token());
            return exchange.json("GET", "/api/library/v1/models/" + model, null, LibraryModels.Model.class);
        });
        pending = request;
        request.future().whenComplete((value, failure) -> {
            if (!closed && generation == expected) {
                if (failure != null) {
                    failed(failure);
                } else {
                    selected = value;
                    armed = null;
                    message = Component.literal(value.displayName() + "  " + value.moderationStatus());
                    updateButtons();
                }
            }
        });
    }

    private void failed(Throwable failure) {
        if (failure instanceof LibraryClient.Failure typed && (typed.status == 401 || typed.status == 403)) {
            admin.invalidate();
            rows.rows(List.of());
            selected = null;
            onClose();
            return;
        }
        message = FiguraText.of("gui.library.failure", failure instanceof LibraryClient.Failure typed
                ? typed.code : "LIBRARY_UNAVAILABLE");
    }

    private void updateButtons() {
        boolean available = !closed && !busy() && admin.active() && selected != null;
        grants.setVisible(LibraryAuthorization.available(selected));
        grants.setActive(available && LibraryAuthorization.available(selected));
        block.setActive(available && selected.allows("moderate") && !selected.moderationStatus().equals("DELETED"));
        block.setMessage(FiguraText.of(selected != null && selected.moderationStatus().equals("BLOCKED")
                ? "gui.library.unblock" : "gui.library.block"));
        delete.setActive(available && selected.allows("delete"));
        previous.setActive(!busy() && page > 1);
        next.setActive(!busy() && results != null && results.hasNext());
    }

    private void logout() {
        admin.logout();
        onClose();
    }

    @Override
    public void tick() {
        super.tick();
        if (!formReady) {
            return;
        }
        if (!admin.active()) {
            rows.rows(List.of());
            selected = null;
            onClose();
            return;
        }
        if (searchChanged != 0 && System.nanoTime() - searchChanged >= 300_000_000L) {
            searchChanged = 0;
            page = 1;
            refresh();
        }
        updateButtons();
    }

    @Override
    public void removed() {
        if (!retainingChild) {
            closed = true;
            generation++;
            selected = null;
            results = null;
            retry = null;
            thumbnails.close();
            images.clear();
            if (pending != null) {
                pending.cancel();
            }
        }
        super.removed();
    }
}
