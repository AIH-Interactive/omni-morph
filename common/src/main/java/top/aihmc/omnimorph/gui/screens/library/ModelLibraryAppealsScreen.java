package top.aihmc.omnimorph.gui.screens.library;

import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import top.aihmc.omnimorph.backend2.library.LibraryAdminSession;
import top.aihmc.omnimorph.backend2.library.LibraryClient;
import org.figuramc.figura.gui.screens.AbstractPanelScreen;
import top.aihmc.omnimorph.gui.screens.ModelLibraryScreen;
import org.figuramc.figura.gui.widgets.Button;
import top.aihmc.omnimorph.gui.widgets.library.LibraryRowList;
import top.aihmc.omnimorph.gui.widgets.library.LibraryMultilineField;
import top.aihmc.omnimorph.gui.widgets.library.LibraryWorkspaceLayout;
import top.aihmc.omnimorph.gui.widgets.library.LibraryManagementPreview;
import org.figuramc.figura.utils.FiguraText;
import org.figuramc.figura.utils.ui.UIHelper;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** The uploader's restricted appeal page never requests blocked model details, images or packages. */
public final class ModelLibraryAppealsScreen extends LibraryFormScreen {
    public record Target(String targetKind, UUID targetId, String name) {
        public Target {
            if (!List.of("LIBRARY", "WARDROBE").contains(targetKind) || targetId == null || name == null) {
                throw new IllegalArgumentException("INVALID_APPEAL_TARGET");
            }
        }
    }

    public record Appeal(UUID appealId, String targetKind, UUID targetId, UUID uploaderUuid, String reason,
                         long createdAt, long revision, Long resolvedAt, String response) {
        public Appeal {
            if (appealId == null || targetId == null || uploaderUuid == null || reason == null || response == null
                    || !List.of("LIBRARY", "WARDROBE").contains(targetKind) || revision < 1
                    || reason.getBytes(StandardCharsets.UTF_8).length > 512 || response.getBytes(StandardCharsets.UTF_8).length > 512) {
                throw new IllegalArgumentException("INVALID_APPEAL");
            }
        }
    }

    public record Targets(List<Target> items, int page, int pageSize, long total, boolean hasNext) {
        public Targets {
            items = List.copyOf(items);
            checkPage(items.size(), page, pageSize, total);
        }
    }

    public record Appeals(List<Appeal> items, int page, int pageSize, long total, boolean hasNext) {
        public Appeals {
            items = List.copyOf(items);
            checkPage(items.size(), page, pageSize, total);
        }
    }

    private static void checkPage(int size, int page, int pageSize, long total) {
        if (page < 1 || pageSize != 8 || size > 8 || total < size) {
            throw new IllegalArgumentException("INVALID_APPEAL_PAGE");
        }
    }

    private record View(List<LibraryRowList.Row> rows, long total, boolean hasNext) {}
    private record Mutation(String path, Map<String, Object> body) {}
    private final LibraryClient client;
    private final LibraryAdminSession admin;
    private final LibraryManagementPreview preview;
    private LibraryClient.Request<?> pending;
    private LibraryRowList rows;
    private LibraryMultilineField text;
    private Button submit;
    private Button unblock;
    private Button previous;
    private Button next;
    private Target target;
    private Appeal appeal;
    private Mutation retry;
    private String input = "";
    private int mode;
    private int page = 1;
    private boolean hasNext;
    private boolean closed;
    private String armed;
    private long generation;
    private Component message = Component.empty();

    public ModelLibraryAppealsScreen(Screen parent, LibraryClient client, LibraryAdminSession admin) {
        super(parent, FiguraText.of(admin == null ? "gui.library.my_appeals" : "gui.library.manage_appeals"));
        this.client = client;
        this.admin = admin;
        preview = admin == null ? null : new LibraryManagementPreview(client, admin::token);
        mode = admin == null ? 0 : 1;
    }

    @Override
    public Class<? extends Screen> getSelectedPanel() {
        return parentScreen instanceof AbstractPanelScreen panel ? panel.getSelectedPanel() : ModelLibraryScreen.class;
    }

    @Override
    protected void init() {
        if (!initForm()) {
            return;
        }
        var layout = new LibraryWorkspaceLayout(width, height);
        int area = layout.contentWidth();
        int contentX = admin == null ? 8 : 16 + layout.previewWidth();
        if (preview != null) {
            addRenderableWidget(preview.place(this, 8, 28, layout.previewWidth(), height - 36));
        }
        addRenderableWidget(new Button(contentX, 28, (area - 4) / 2, 20, modeText(), null, button -> {
            mode = admin == null ? (mode + 1) % 3 : mode == 1 ? 2 : 1;
            page = 1;
            button.setMessage(modeText());
            refresh();
        }));
        addRenderableWidget(new Button(contentX + 4 + (area - 4) / 2, 28, (area - 4) / 2, 20,
                FiguraText.of("gui.library.refresh"), null, ignored -> refresh()));
        rows = addRenderableWidget(new LibraryRowList(contentX, 56, area,
                Math.max(24, height - (admin == null ? 88 : 196))));
        int editorHeight = admin == null ? Math.max(48, Math.min(120, height - 160)) : 48;
        int editorX = admin == null ? layout.previewX() : contentX;
        int editorY = admin == null ? 52 : height - 124;
        int editorWidth = admin == null ? layout.previewWidth() : area;
        text = addRenderableWidget(new LibraryMultilineField(editorWidth, editorHeight, input, value -> input = value));
        text.setX(editorX);
        text.setY(editorY);
        text.setCharacterLimit(512);
        int actionWidth = admin == null ? layout.previewWidth() : (area - 4) / 2;
        submit = addRenderableWidget(new Button(admin == null ? editorX : editorX + actionWidth + 4,
                height - (admin == null ? 52 : 72), actionWidth, 20,
                FiguraText.of(admin == null ? "gui.library.submit_appeal" : "gui.library.resolve_appeal"), null, ignored -> submit(false)));
        unblock = addRenderableWidget(new Button(editorX, height - 72, actionWidth, 20,
                FiguraText.of("gui.library.appeal_unblock"), FiguraText.of("gui.library.appeal_unblock_hint"), ignored -> submit(true)));
        unblock.setVisible(admin != null);
        previous = addRenderableWidget(new Button(contentX, height - 28, 28, 20, Component.literal("<"), null,
                ignored -> {
                    page = Math.max(1, page - 1);
                    refresh();
                }));
        next = addRenderableWidget(new Button(contentX + 32, height - 28, 28, 20, Component.literal(">"), null,
                ignored -> {
                    page++;
                    refresh();
                }));
        addRenderableWidget(new Button(admin == null ? editorX : contentX + area - 60,
                height - 28, admin == null ? layout.previewWidth() : 60, 20,
                FiguraText.of("gui.done"), null, ignored -> onClose()));
        addRenderableOnly((gui, x, y, delta) -> {
            UIHelper.renderScrollingText(gui, FiguraText.of(admin == null ? "gui.library.submit_appeal" : "gui.library.resolve_appeal"),
                    editorX, admin == null ? 32 : height - 136, editorWidth, 0xffeeeeee);
            UIHelper.renderScrollingText(gui, message, editorX, admin == null ? 60 + editorHeight : height - 44,
                    editorWidth, 0xffd3e7e1);
        });
        refresh();
    }

    private Component modeText() {
        return FiguraText.of(mode == 0 ? "gui.library.appeal_targets"
                : mode == 1 ? "gui.library.appeals_open" : "gui.library.appeals_resolved");
    }

    private boolean current() {
        return !closed && client.current() && (admin == null || admin.active());
    }

    private boolean busy() {
        return pending != null && !pending.future().isDone();
    }

    private void refresh() {
        if (!current()) {
            return;
        }
        long expected = ++generation;
        if (pending != null) {
            pending.cancel();
        }
        target = null;
        appeal = null;
        if (preview != null) {
            preview.clear();
        }
        retry = null;
        armed = null;
        hasNext = false;
        rows.rows(List.of());
        if (mode == 0) {
            rows.columns(List.of(FiguraText.of("gui.library.display_name"), FiguraText.of("gui.library.source")), 3, 1);
        } else {
            rows.columns(List.of(FiguraText.of("gui.library.source"), FiguraText.of("gui.library.report_reason"),
                    FiguraText.of("gui.library.response")), 2, 5, 5);
        }
        int requestedMode = mode;
        int requestedPage = page;
        var request = client.submit(LibraryClient.Lane.CONTROL, exchange -> {
            if (admin != null) {
                exchange.adminToken(admin.token());
            }
            if (requestedMode == 0) {
                var value = exchange.json("GET", "/api/library/v1/appeal-targets?page=" + requestedPage, null, Targets.class);
                return new View(value.items().stream().map(item -> LibraryRowList.Row.table(
                        List.of(Component.literal(item.name()), FiguraText.of("gui.library.recycle_source."
                                + item.targetKind().toLowerCase(java.util.Locale.ROOT))),
                        Component.literal(item.targetId().toString()), true,
                        () -> {
                            if (!busy()) {
                                target = item;
                                appeal = null;
                                retry = null;
                                armed = null;
                                message = Component.literal(item.name());
                            }
                        })).toList(), value.total(), value.hasNext());
            }
            String root = admin == null ? "/api/library/v1/appeals" : "/api/library/v1/admin/appeals";
            var value = exchange.json("GET", root + "?page=" + requestedPage + "&resolved=" + (requestedMode == 2), null, Appeals.class);
            return new View(value.items().stream().map(item -> LibraryRowList.Row.table(
                    List.of(Component.literal(item.targetKind()), Component.literal(item.reason()), Component.literal(item.response())),
                    Component.literal(item.uploaderUuid() + "\n" + item.targetId() + "\n" + item.reason() + "\n" + item.response()), true,
                    () -> {
                        if (!busy()) {
                            appeal = item;
                            if (preview != null) {
                                Component title = Component.literal(item.targetId().toString());
                                if (item.targetKind().equals("LIBRARY")) {
                                    preview.model(item.targetId(), title);
                                } else {
                                    preview.blockedWardrobe(item.targetId(), title);
                                }
                            }
                            target = null;
                            retry = null;
                            armed = null;
                            message = Component.literal(item.response().isEmpty() ? item.reason() : item.response());
                        }
                    })).toList(), value.total(), value.hasNext());
        });
        pending = request;
        request.future().whenComplete((value, failure) -> {
            if (!current() || generation != expected) {
                return;
            }
            if (failure != null) {
                failed(failure);
            } else {
                rows.rows(value.rows());
                hasNext = value.hasNext();
                message = FiguraText.of("gui.library.page", page, Math.max(1, (value.total() + 7) / 8), value.total());
            }
        });
    }

    private void submit(boolean lift) {
        if (!current() || busy()) {
            return;
        }
        if (retry == null) {
            UUID operation = UUID.randomUUID();
            if (admin == null && mode == 0 && target != null && validInput()) {
                retry = new Mutation("/api/library/v1/appeals", Map.of("operation_id", operation.toString(),
                        "target_kind", target.targetKind(), "target_id", target.targetId().toString(), "reason", input.trim()));
            } else if (admin != null && appeal != null && appeal.resolvedAt() == null && (lift || validInput())) {
                String action = lift ? "unblock" : "resolve";
                if (!action.equals(armed)) {
                    armed = action;
                    message = FiguraText.of(lift ? "gui.library.appeal_unblock_hint" : "gui.library.confirm_resolve_appeal");
                    return;
                }
                Map<String, Object> body = lift ? Map.of("operation_id", operation.toString(), "expected_revision", appeal.revision())
                        : Map.of("operation_id", operation.toString(), "expected_revision", appeal.revision(), "response", input.trim());
                retry = new Mutation("/api/library/v1/admin/appeals/" + appeal.appealId() + "/" + action, body);
            } else {
                return;
            }
        }
        Mutation mutation = retry;
        long expected = ++generation;
        var request = client.submit(LibraryClient.Lane.CONTROL, exchange -> {
            if (admin != null) {
                exchange.adminToken(admin.token());
            }
            return exchange.json("POST", mutation.path(), mutation.body(), Map.class);
        });
        pending = request;
        request.future().whenComplete((value, failure) -> {
            if (current() && generation == expected) {
                if (failure != null) {
                    failed(failure);
                } else {
                    input = "";
                    text.setValue("");
                    refresh();
                }
            }
        });
    }

    private boolean validInput() {
        return !input.isBlank() && input.trim().getBytes(StandardCharsets.UTF_8).length <= 512;
    }

    private void failed(Throwable failure) {
        if (admin != null && failure instanceof LibraryClient.Failure typed && (typed.status == 401 || typed.status == 403)) {
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
        if (!current()) {
            onClose();
            return;
        }
        boolean editable = admin == null ? mode == 0 && target != null : mode == 1 && appeal != null;
        text.setEnabled(editable && !busy() && retry == null);
        submit.setActive(!busy() && (retry != null || editable && validInput()));
        submit.setMessage(FiguraText.of(retry != null ? "gui.library.retry_change"
                : admin == null ? "gui.library.submit_appeal" : "gui.library.resolve_appeal"));
        unblock.setActive(admin != null && !busy() && mode == 1 && appeal != null && retry == null);
        previous.setActive(!busy() && page > 1);
        next.setActive(!busy() && hasNext);
    }

    @Override
    public void removed() {
        if (preview != null && preview.isExpanded()) {
            super.removed();
            return;
        }
        closed = true;
        if (preview != null) {
            preview.close();
        }
        generation++;
        appeal = null;
        target = null;
        retry = null;
        input = "";
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
