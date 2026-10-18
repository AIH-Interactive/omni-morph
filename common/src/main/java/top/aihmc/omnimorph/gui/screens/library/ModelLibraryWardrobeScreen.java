package top.aihmc.omnimorph.gui.screens.library;

import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import top.aihmc.omnimorph.backend2.library.LibraryAdminSession;
import top.aihmc.omnimorph.backend2.library.LibraryClient;
import top.aihmc.omnimorph.gui.screens.ModelLibraryScreen;
import top.aihmc.omnimorph.gui.widgets.Button;
import top.aihmc.omnimorph.gui.widgets.SearchBar;
import top.aihmc.omnimorph.gui.widgets.library.LibraryRowList;
import top.aihmc.omnimorph.gui.widgets.library.LibraryManagementPreview;
import top.aihmc.omnimorph.gui.widgets.library.LibraryWorkspaceLayout;
import top.aihmc.omnimorph.utils.FiguraText;
import top.aihmc.omnimorph.utils.ui.UIHelper;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Targets an immutable wardrobe resource and the selected player's equipment revision. */
public final class ModelLibraryWardrobeScreen extends LibraryFormScreen {
    public record Item(UUID owner, String name, String assetId, long assetRevision, long equipmentRevision, int modelType) {
        public Item {
            if (owner == null || name == null || assetId == null || assetRevision < 1 || equipmentRevision < 1
                    || modelType < 1 || modelType > 3) {
                throw new IllegalArgumentException("INVALID_WARDROBE_ITEM");
            }
        }
    }

    public record Page(List<Item> items, int page, int pageSize, long total, boolean hasNext) {
        public Page {
            items = List.copyOf(items);
            if (page < 1 || pageSize != 8 || items.size() > 8 || total < items.size()) {
                throw new IllegalArgumentException("INVALID_WARDROBE_PAGE");
            }
        }
    }

    private final LibraryClient client;
    private final LibraryAdminSession admin;
    private final LibraryManagementPreview preview;
    private LibraryClient.Request<?> pending;
    private LibraryRowList rows;
    private Button reset;
    private Button block;
    private Button previous;
    private Button next;
    private Page results;
    private Item selected;
    private UUID operation;
    private String armed;
    private String query = "";
    private String operationAction;
    private int page = 1;
    private long generation;
    private long searchChanged;
    private boolean building;
    private boolean closed;
    private Component message = Component.empty();

    public ModelLibraryWardrobeScreen(Screen parent, LibraryClient client, LibraryAdminSession admin) {
        super(parent, FiguraText.of("gui.library.wardrobe_manage"));
        this.client = client;
        this.admin = admin;
        preview = new LibraryManagementPreview(client, admin::token);
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
        var layout = new LibraryWorkspaceLayout(width, height);
        int area = layout.contentWidth();
        addRenderableWidget(preview.place(this, layout.previewX(), 28, layout.previewWidth(), height - 36));
        building = true;
        var search = addRenderableWidget(new SearchBar(8, 28, area, 20, value -> {
            if (!building && !value.equals(query)) {
                query = value;
                searchChanged = System.nanoTime();
            }
        }));
        search.getField().setMaxLength(64);
        search.getField().setValue(query);
        building = false;
        rows = addRenderableWidget(new LibraryRowList(8, 76, area, Math.max(24, height - 152)));
        rows.columns(List.of(FiguraText.of("gui.library.player_name"), Component.literal("UUID"),
                FiguraText.of("gui.library.source")), 4, 7, 7);
        reset = addRenderableWidget(new Button(8, height - 72, (area - 4) / 2, 20,
                FiguraText.of("gui.library.wardrobe_reset"), null, ignored -> change("reset")));
        block = addRenderableWidget(new Button(12 + (area - 4) / 2, height - 72, (area - 4) / 2, 20,
                FiguraText.of("gui.library.block"), null, ignored -> change("block")));
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
        addRenderableWidget(new Button(88, height - 48, Math.max(40, area - 80), 20,
                FiguraText.of("gui.library.refresh"), null, ignored -> refresh()));
        addRenderableWidget(new Button(8 + area - 88, height - 24, 88, 20,
                FiguraText.of("gui.done"), null, ignored -> onClose()));
        addRenderableOnly((gui, x, y, delta) -> UIHelper.renderScrollingText(gui, message, 8, 54, area, 0xffd3e7e1));
        refresh();
    }

    private boolean busy() {
        return pending != null && !pending.future().isDone();
    }

    private void refresh() {
        if (closed || !admin.active()) {
            return;
        }
        long expected = ++generation;
        if (pending != null) {
            pending.cancel();
        }
        selected = null;
        preview.clear();
        operation = null;
        armed = null;
        results = null;
        rows.rows(List.of());
        String path = "/api/library/v1/admin/wardrobe?page=" + page + "&q="
                + URLEncoder.encode(query, StandardCharsets.UTF_8);
        var request = client.submit(LibraryClient.Lane.CONTROL, exchange -> {
            exchange.adminToken(admin.token());
            return exchange.json("GET", path, null, Page.class);
        });
        pending = request;
        request.future().whenComplete((value, failure) -> {
            if (closed || generation != expected) {
                return;
            }
            if (failure != null) {
                failed(failure);
                return;
            }
            results = value;
            message = FiguraText.of("gui.library.page", value.page(), Math.max(1, (value.total() + 7) / 8), value.total());
            rows.rows(value.items().stream().map(item -> LibraryRowList.Row.table(
                    List.of(Component.literal(item.name()), Component.literal(item.owner().toString()),
                            Component.literal(item.assetId() + " #" + item.assetRevision())),
                    Component.literal(item.owner() + "\n" + item.assetId() + " #" + item.assetRevision()
                            + "\n" + item.equipmentRevision()), true, () -> {
                        selected = item;
                        preview.wardrobe(item.owner(), item.assetId(), item.assetRevision(), item.equipmentRevision(),
                                Component.literal(item.name()));
                        armed = null;
                        operation = null;
                        message = Component.literal(item.name() + "  " + item.owner());
                    })).toList());
        });
    }

    private void change(String action) {
        if (selected == null || busy() || !admin.active()) {
            return;
        }
        if (!action.equals(armed)) {
            armed = action;
            message = FiguraText.of("gui.library.confirm_wardrobe");
            return;
        }
        if (operation == null || !action.equals(operationAction)) {
            operation = UUID.randomUUID();
            operationAction = action;
        }
        UUID operationId = operation;
        Item target = selected;
        long expected = ++generation;
        var request = client.submit(LibraryClient.Lane.CONTROL, exchange -> {
            exchange.adminToken(admin.token());
            return exchange.json("POST", "/api/library/v1/admin/wardrobe/" + target.owner() + "/" + action,
                    Map.of("operation_id", operationId.toString(), "expected_equipment_revision", target.equipmentRevision(),
                            "asset_id", target.assetId(), "asset_revision", target.assetRevision()), Map.class);
        });
        pending = request;
        request.future().whenComplete((value, failure) -> {
            if (!closed && generation == expected) {
                if (failure != null) {
                    failed(failure);
                } else {
                    refresh();
                }
            }
        });
    }

    private void failed(Throwable failure) {
        if (failure instanceof LibraryClient.Failure typed && (typed.status == 401 || typed.status == 403)) {
            admin.invalidate();
            onClose();
        }
        message = FiguraText.of("gui.library.failure", failure instanceof LibraryClient.Failure typed ? typed.code : "LIBRARY_UNAVAILABLE");
    }

    @Override
    public void tick() {
        super.tick();
        if (!formReady) {
            return;
        }
        if (!admin.active()) {
            onClose();
            return;
        }
        if (searchChanged != 0 && System.nanoTime() - searchChanged >= 300_000_000L) {
            searchChanged = 0;
            page = 1;
            refresh();
        }
        reset.setActive(!closed && !busy() && selected != null);
        block.setActive(!closed && !busy() && selected != null);
        previous.setActive(!busy() && page > 1);
        next.setActive(!busy() && results != null && results.hasNext());
    }

    @Override
    public void removed() {
        if (preview.isExpanded()) {
            super.removed();
            return;
        }
        closed = true;
        preview.close();
        generation++;
        selected = null;
        results = null;
        message = Component.empty();
        if (rows != null) {
            rows.rows(List.of());
        }
        if (pending != null) {
            pending.cancel();
        }
        super.removed();
    }
}
