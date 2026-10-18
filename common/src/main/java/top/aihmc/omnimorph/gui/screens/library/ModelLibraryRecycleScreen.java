package top.aihmc.omnimorph.gui.screens.library;

import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import top.aihmc.omnimorph.backend2.library.LibraryAdminSession;
import top.aihmc.omnimorph.backend2.library.LibraryClient;
import top.aihmc.omnimorph.backend2.library.LibraryRecycleService;
import top.aihmc.omnimorph.gui.screens.ModelLibraryScreen;
import top.aihmc.omnimorph.gui.widgets.Button;
import top.aihmc.omnimorph.gui.widgets.SearchBar;
import top.aihmc.omnimorph.gui.widgets.library.LibraryIconButton;
import top.aihmc.omnimorph.gui.widgets.library.LibraryLabels;
import top.aihmc.omnimorph.gui.widgets.library.LibraryManagementPreview;
import top.aihmc.omnimorph.gui.widgets.library.LibraryRowList;
import top.aihmc.omnimorph.gui.widgets.library.LibraryWorkspaceLayout;
import top.aihmc.omnimorph.utils.FiguraIdentifier;
import top.aihmc.omnimorph.utils.FiguraText;
import top.aihmc.omnimorph.utils.ui.UIHelper;

import java.util.List;
import java.util.UUID;

/** One compact recycle table for both model sources; normal management pages exclude these rows. */
public final class ModelLibraryRecycleScreen extends LibraryFormScreen {
    private static final List<String> TYPES = List.of("", "FIGURA", "YSM_PLAIN", "YSM_ENCRYPTED");
    private static final List<String> SOURCES = List.of("", "LIBRARY", "WARDROBE");
    private final LibraryAdminSession admin;
    private final LibraryRecycleService service;
    private final LibraryManagementPreview preview;
    private LibraryClient.Request<?> pending;
    private LibraryRecycleService.Item selected;
    private LibraryRecycleService.Page results;
    private LibraryRowList rows;
    private Button restore;
    private Button purge;
    private Button previous;
    private Button next;
    private Component message = Component.empty();
    private String query = "";
    private int type;
    private int source;
    private int page = 1;
    private String armed;
    private UUID operation;
    private long generation;
    private long searchChanged;
    private boolean building;
    private boolean closed;

    public ModelLibraryRecycleScreen(Screen parent, LibraryClient client, LibraryAdminSession admin) {
        super(parent, FiguraText.of("gui.library.recycle_bin"));
        this.admin = admin;
        service = new LibraryRecycleService(client, admin::token);
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
        addRenderableOnly((gui, x, y, delta) -> UIHelper.renderScrollingText(gui,
                FiguraText.of("gui.library.recycle_bin"), 8, 34, (area - 4) / 2, 0xffeeeeee));
        addRenderableWidget(new Button(12 + (area - 4) / 2, 28, (area - 4) / 2, 20,
                FiguraText.of("gui.library.refresh"), null, ignored -> {
                    if (!busy()) {
                        refresh();
                    }
                }));
        building = true;
        int searchWidth = Math.max(64, area / 2);
        var search = addRenderableWidget(new SearchBar(8, 52, searchWidth, 20, value -> {
            if (!building && !query.equals(value)) {
                query = value;
                searchChanged = System.nanoTime();
            }
        }));
        search.getField().setMaxLength(64);
        search.getField().setValue(query);
        int filterWidth = Math.max(20, (area - searchWidth - 8) / 2);
        addRenderableWidget(new Button(12 + searchWidth, 52, filterWidth, 20, typeLabel(), null, button -> {
            if (!busy()) {
                type = (type + 1) % TYPES.size();
                page = 1;
                button.setMessage(typeLabel());
                refresh();
            }
        }));
        addRenderableWidget(new Button(16 + searchWidth + filterWidth, 52, filterWidth, 20, sourceLabel(), null, button -> {
            if (!busy()) {
                source = (source + 1) % SOURCES.size();
                page = 1;
                button.setMessage(sourceLabel());
                refresh();
            }
        }));
        building = false;
        rows = addRenderableWidget(new LibraryRowList(8, 100, area, Math.max(24, height - 176)));
        rows.columns(List.of(FiguraText.of("gui.library.table_cover"), FiguraText.of("gui.library.source"),
                FiguraText.of("gui.library.display_name"), FiguraText.of("gui.library.authors"),
                FiguraText.of("gui.library.uploader"), FiguraText.of("gui.library.status"),
                FiguraText.of("gui.library.downloads"), FiguraText.of("gui.library.actions")), 2, 3, 6, 4, 4, 3, 2, 3);
        restore = icon("library_restore", "gui.library.restore", 8, height - 72, (area - 4) / 2, false);
        purge = icon("library_delete", "gui.library.purge", 12 + (area - 4) / 2, height - 72, (area - 4) / 2, true);
        previous = addRenderableWidget(new Button(8, height - 48, 36, 20, Component.literal("<"), null, ignored -> {
            page = Math.max(1, page - 1);
            refresh();
        }));
        next = addRenderableWidget(new Button(48, height - 48, 36, 20, Component.literal(">"), null, ignored -> {
            page++;
            refresh();
        }));
        addRenderableWidget(new Button(8, height - 24, area, 20, FiguraText.of("gui.done"), null, ignored -> onClose()));
        addRenderableOnly((gui, x, y, delta) -> UIHelper.renderScrollingText(gui, message, 8, 80, area, 0xffd3e7e1));
        refresh();
    }

    private Button icon(String texture, String label, int x, int y, int width, boolean remove) {
        var button = new LibraryIconButton(texture, 1, () -> 0, FiguraText.of(label), FiguraText.of(label),
                ignored -> change(remove));
        button.setX(x);
        button.setY(y);
        button.setWidth(width);
        button.setHeight(20);
        button.setActive(false);
        return addRenderableWidget(button);
    }

    private Component typeLabel() {
        return FiguraText.of("gui.library.filter.type", type == 0 ? FiguraText.of("gui.library.all")
                : FiguraText.of("gui.library.value." + TYPES.get(type).toLowerCase(java.util.Locale.ROOT)));
    }

    private Component sourceLabel() {
        return FiguraText.of("gui.library.filter.source", source == 0 ? FiguraText.of("gui.library.all")
                : FiguraText.of("gui.library.recycle_source." + SOURCES.get(source).toLowerCase(java.util.Locale.ROOT)));
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
        armed = null;
        operation = null;
        results = null;
        preview.clear();
        rows.rows(List.of());
        var request = service.list(page, query, TYPES.get(type), SOURCES.get(source));
        pending = request;
        request.future().whenComplete((value, failure) -> {
            if (closed || expected != generation) {
                return;
            }
            if (failure != null) {
                failed(failure);
            } else {
                results = value;
                message = FiguraText.of("gui.library.page", page, Math.max(1, (value.total() + 7) / 8), value.total());
                rows.rows(value.items().stream().map(this::row).toList());
            }
        });
    }

    private LibraryRowList.Row row(LibraryRecycleService.Item item) {
        return new LibraryRowList.Row(Component.empty(), FiguraText.of("gui.library.recycle_source."
                + item.kind().toLowerCase(java.util.Locale.ROOT)).append("\n" + item.id() + "\n" + item.owner()),
                true, () -> select(item), List.of(Component.empty(),
                FiguraText.of("gui.library.recycle_source." + item.kind().toLowerCase(java.util.Locale.ROOT)),
                Component.literal(item.name()), item.authors().isEmpty() ? FiguraText.of("gui.library.unknown_author")
                        : Component.literal(String.join(", ", item.authors())),
                Component.literal(item.uploaderName()), LibraryLabels.status(item.status()),
                Component.literal(item.kind().equals("LIBRARY") ? Long.toString(item.downloads()) : "—"), Component.empty()),
                () -> new FiguraIdentifier("textures/gui/library_placeholder.png"), List.of(
                new LibraryRowList.Action(FiguraText.of("gui.library.restore"),
                        FiguraText.of(item.canRestore() ? "gui.library.restore" : "gui.library.restore_conflict"),
                        item.canRestore(), () -> change(false), LibraryRowList.Icon.RESTORE),
                new LibraryRowList.Action(FiguraText.of("gui.library.purge"), FiguraText.of("gui.library.purge"),
                        true, () -> change(true), LibraryRowList.Icon.DELETE)));
    }

    private void select(LibraryRecycleService.Item item) {
        if (busy() || selected == item) {
            return;
        }
        selected = item;
        armed = null;
        operation = null;
        preview.recycled(item.kind(), item.owner(), item.id(), Component.literal(item.name()));
        message = item.canRestore() ? Component.literal(item.name()) : FiguraText.of("gui.library.restore_conflict");
    }

    private void change(boolean remove) {
        if (closed || busy() || selected == null || !admin.active() || !remove && !selected.canRestore()) {
            return;
        }
        String action = remove ? "purge" : "restore";
        if (!action.equals(armed)) {
            armed = action;
            operation = UUID.randomUUID();
            message = FiguraText.of(remove ? "gui.library.confirm_purge" : "gui.library.confirm_restore",
                    selected.name() + " [" + selected.id() + "]");
            return;
        }
        long expected = ++generation;
        var request = service.change(selected, remove, operation);
        pending = request;
        request.future().whenComplete((value, failure) -> {
            if (!closed && expected == generation) {
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
            return;
        }
        message = failure instanceof LibraryClient.Failure typed && typed.status == 409
                ? FiguraText.of("gui.library.restore_conflict")
                : FiguraText.of("gui.library.failure", failure instanceof LibraryClient.Failure typed ? typed.code : "LIBRARY_UNAVAILABLE");
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
        if (!busy() && searchChanged != 0 && System.nanoTime() - searchChanged >= 300_000_000L) {
            searchChanged = 0;
            page = 1;
            refresh();
        }
        restore.setActive(!busy() && selected != null && selected.canRestore());
        purge.setActive(!busy() && selected != null);
        previous.setActive(!busy() && page > 1);
        next.setActive(!busy() && results != null && results.hasNext());
    }

    @Override
    public void removed() {
        if (!preview.isExpanded()) {
            closed = true;
            generation++;
            preview.close();
            if (pending != null) {
                pending.cancel();
            }
        }
        super.removed();
    }
}
