package top.aihmc.omnimorph.gui.screens.library;

import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import top.aihmc.omnimorph.backend2.library.LibraryAdminSession;
import top.aihmc.omnimorph.backend2.library.LibraryClient;
import top.aihmc.omnimorph.gui.screens.ModelLibraryScreen;
import top.aihmc.omnimorph.gui.widgets.Button;
import top.aihmc.omnimorph.gui.widgets.library.LibraryRowList;
import top.aihmc.omnimorph.utils.FiguraText;
import top.aihmc.omnimorph.utils.ui.UIHelper;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

/** Reports stay in an administrator-only page and are never merged into ordinary model metadata. */
public final class ModelLibraryReportsScreen extends LibraryFormScreen {
    public record Report(UUID reportId, UUID modelId, String modelName, UUID reporterUuid, String reason,
                         long createdAt, Long resolvedAt, UUID resolvedBy, long revision) {
        public Report {
            if (reportId == null || modelId == null || reporterUuid == null || modelName == null || reason == null
                    || reason.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > 512 || revision < 1) {
                throw new IllegalArgumentException("INVALID_REPORT");
            }
        }
    }

    public record Page(List<Report> items, int page, int pageSize, long total, boolean hasNext) {
        public Page {
            items = List.copyOf(items);
            if (page < 1 || pageSize != 8 || items.size() > 8 || total < items.size()) {
                throw new IllegalArgumentException("INVALID_REPORT_PAGE");
            }
        }
    }

    private final LibraryClient client;
    private final LibraryAdminSession admin;
    private final Consumer<UUID> inspect;
    private LibraryClient.Request<?> pending;
    private LibraryRowList rows;
    private Button resolve;
    private Button open;
    private Button previous;
    private Button next;
    private Report selected;
    private UUID operation;
    private boolean resolved;
    private boolean armed;
    private boolean closed;
    private int page = 1;
    private long generation;
    private Page results;
    private Component message = Component.empty();

    public ModelLibraryReportsScreen(Screen parent, LibraryClient client, LibraryAdminSession admin, Consumer<UUID> inspect) {
        super(parent, FiguraText.of("gui.library.reports"));
        this.client = client;
        this.admin = admin;
        this.inspect = inspect;
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
        int area = Math.max(80, width - 16);
        addRenderableWidget(new Button(8, 28, (area - 4) / 2, 20,
                FiguraText.of(resolved ? "gui.library.reports_resolved" : "gui.library.reports_open"), null, button -> {
                    resolved = !resolved;
                    page = 1;
                    button.setMessage(FiguraText.of(resolved ? "gui.library.reports_resolved" : "gui.library.reports_open"));
                    refresh();
                }));
        addRenderableWidget(new Button(12 + (area - 4) / 2, 28, (area - 4) / 2, 20,
                FiguraText.of("gui.library.refresh"), null, ignored -> refresh()));
        rows = addRenderableWidget(new LibraryRowList(8, 76, area, Math.max(24, height - 152)));
        rows.columns(List.of(FiguraText.of("gui.library.display_name"), FiguraText.of("gui.library.player_name"),
                FiguraText.of("gui.library.report_reason"), FiguraText.of("gui.library.created_at")), 4, 5, 7, 4);
        resolve = addRenderableWidget(new Button(8, height - 72, (area - 4) / 2, 20,
                FiguraText.of("gui.library.resolve_report"), null, ignored -> resolve()));
        open = addRenderableWidget(new Button(12 + (area - 4) / 2, height - 72, (area - 4) / 2, 20,
                FiguraText.of("gui.library.inspect_model"), null, ignored -> {
                    if (selected != null && !busy()) {
                        UUID model = selected.modelId();
                        minecraft.setScreen(parentScreen);
                        inspect.accept(model);
                    }
                }));
        previous = addRenderableWidget(new Button(8, height - 48, 60, 20, Component.literal("<"), null,
                ignored -> {
                    page = Math.max(1, page - 1);
                    refresh();
                }));
        next = addRenderableWidget(new Button(72, height - 48, 60, 20, Component.literal(">"), null,
                ignored -> {
                    page++;
                    refresh();
                }));
        addRenderableWidget(new Button(width - 96, height - 24, 88, 20,
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
        operation = null;
        armed = false;
        results = null;
        rows.rows(List.of());
        var request = client.submit(LibraryClient.Lane.CONTROL, exchange -> {
            exchange.adminToken(admin.token());
            return exchange.json("GET", "/api/library/v1/admin/reports?page=" + page + "&resolved=" + resolved, null, Page.class);
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
            rows.rows(value.items().stream().map(report -> LibraryRowList.Row.table(
                    List.of(Component.literal(report.modelName()), Component.literal(report.reporterUuid().toString()),
                            Component.literal(report.reason()), Component.literal(Instant.ofEpochSecond(report.createdAt()).toString())),
                    Component.literal(report.reporterUuid() + "\n" + Instant.ofEpochSecond(report.createdAt())
                            + "\n" + report.reason() + (report.resolvedBy() == null ? "" : "\n" + report.resolvedBy())), true,
                    () -> {
                        selected = report;
                        operation = null;
                        armed = false;
                        message = Component.literal(report.modelName() + "  " + report.reason());
                    })).toList());
        });
    }

    private void resolve() {
        if (selected == null || selected.resolvedAt() != null || busy() || !admin.active()) {
            return;
        }
        if (!armed) {
            armed = true;
            message = FiguraText.of("gui.library.confirm_resolve_report");
            return;
        }
        if (operation == null) {
            operation = UUID.randomUUID();
        }
        Report report = selected;
        UUID requestOperation = operation;
        long expected = ++generation;
        var request = client.submit(LibraryClient.Lane.CONTROL, exchange -> {
            exchange.adminToken(admin.token());
            return exchange.json("POST", "/api/library/v1/admin/reports/" + report.reportId() + "/resolve",
                    Map.of("operation_id", requestOperation.toString(), "expected_revision", report.revision()), Map.class);
        });
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
        resolve.setActive(!closed && !busy() && selected != null && selected.resolvedAt() == null);
        open.setActive(!closed && !busy() && selected != null);
        previous.setActive(!busy() && page > 1);
        next.setActive(!busy() && results != null && results.hasNext());
    }

    @Override
    public void removed() {
        closed = true;
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
