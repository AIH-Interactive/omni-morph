package top.aihmc.omnimorph.gui.screens.library;

import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import top.aihmc.omnimorph.backend2.library.LibraryAuthorization;
import top.aihmc.omnimorph.backend2.library.LibraryAdminSession;
import top.aihmc.omnimorph.backend2.library.LibraryClient;
import top.aihmc.omnimorph.backend2.library.LibraryModels;
import top.aihmc.omnimorph.gui.screens.ModelLibraryScreen;
import org.figuramc.figura.gui.widgets.Button;
import org.figuramc.figura.gui.widgets.TextField;
import top.aihmc.omnimorph.gui.widgets.library.LibraryRowList;
import top.aihmc.omnimorph.gui.widgets.library.LibraryManagementPreview;
import top.aihmc.omnimorph.gui.widgets.library.LibraryWorkspaceLayout;
import org.figuramc.figura.utils.FiguraText;
import org.figuramc.figura.utils.ui.UIHelper;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

/** Verified name resolution followed by a separate UUID confirmation. Closing never submits a change. */
public final class ModelLibraryAuthorizationScreen extends LibraryFormScreen {
    private final LibraryClient client;
    private final LibraryAuthorization service;
    private final LibraryManagementPreview preview;
    private LibraryAdminSession admin;
    private LibraryClient.Request<?> pending;
    private LibraryModels.Model model;
    private LibraryAuthorization.Player candidate;
    private LibraryAuthorization.Player revocation;
    private LibraryAuthorization.Change retry;
    private LibraryAuthorization.Page results;
    private LibraryRowList rows;
    private Button resolve;
    private Button confirm;
    private Button previous;
    private Button next;
    private String name = "";
    private int page = 1;
    private long generation;
    private boolean closed;
    private Component message = Component.empty();

    public ModelLibraryAuthorizationScreen(Screen parent, LibraryClient client, UUID model, Supplier<String> admin) {
        super(parent, FiguraText.of("gui.library.grants"));
        this.client = client;
        service = new LibraryAuthorization(client, model, admin);
        preview = new LibraryManagementPreview(client, admin);
    }

    public ModelLibraryAuthorizationScreen(Screen parent, LibraryClient client, UUID model, LibraryAdminSession admin) {
        this(parent, client, model, admin::token);
        this.admin = admin;
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
        int bodyX = layout.left();
        int rowWidth = layout.contentWidth();
        addRenderableWidget(preview.place(this, layout.previewX(), 28, layout.previewWidth(), height - 36));
        addRenderableOnly((gui, mouseX, mouseY, delta) -> {
            UIHelper.renderScrollingText(gui, model == null ? getTitle() : Component.literal(model.displayName()),
                    bodyX, 32, rowWidth - 24, 0xffeeeeee);
            if (model != null && UIHelper.isMouseOver(bodyX, 28, rowWidth - 24, 20, mouseX, mouseY)) {
                UIHelper.setTooltip(FiguraText.of("gui.library.card_tooltip", model.internalName(),
                        String.join(", ", model.authors()), model.uploader().name(), model.uploader().uuid().toString()));
            }
        });
        var field = addRenderableWidget(new TextField(bodyX, 52, Math.max(40, rowWidth - 92), 20,
                TextField.HintType.NAME, value -> {
                    if (!name.equals(value)) {
                        name = value;
                        candidate = null;
                        revocation = null;
                        retry = null;
                    }
                }));
        field.getField().setMaxLength(64);
        field.getField().setValue(name);
        resolve = addRenderableWidget(new Button(bodyX + rowWidth - 88, 52, 88, 20,
                FiguraText.of("gui.library.resolve"), null, ignored -> resolve()));
        confirm = addRenderableWidget(new Button(bodyX, height - 52, rowWidth, 20,
                FiguraText.of("gui.library.confirm_grant"), null, ignored -> {
                    if (retry != null) {
                        change(retry);
                    } else if (revocation != null && model != null) {
                        change(new LibraryAuthorization.Change(revocation.uuid(), false, UUID.randomUUID(), model.policyRevision()));
                    } else if (candidate != null && model != null) {
                        change(new LibraryAuthorization.Change(candidate.uuid(), true, UUID.randomUUID(), model.policyRevision()));
                    }
                }));
        rows = addRenderableWidget(new LibraryRowList(bodyX, 96, rowWidth, Math.max(24, height - 152)));
        rows.columns(List.of(FiguraText.of("gui.library.player_name"), Component.literal("UUID"),
                FiguraText.of("gui.library.authorized_at"), FiguraText.of("gui.library.actions")), 4, 7, 5, 2);
        previous = addRenderableWidget(new Button(bodyX, height - 28, 28, 20, Component.literal("<"), null,
                ignored -> {
                    page = Math.max(1, page - 1);
                    refresh();
                }));
        next = addRenderableWidget(new Button(bodyX + 32, height - 28, 28, 20, Component.literal(">"), null,
                ignored -> {
                    page++;
                    refresh();
                }));
        addRenderableWidget(new Button(bodyX + rowWidth - 88, height - 28, 88, 20,
                FiguraText.of("gui.library.refresh"), null, ignored -> refresh()));
        addRenderableWidget(new Button(bodyX + rowWidth - 20, 28, 20, 20,
                Component.literal("×"), FiguraText.of("gui.done"), ignored -> onClose()));
        addRenderableOnly((gui, x, y, delta) -> UIHelper.renderScrollingText(gui, message, bodyX, 78, rowWidth, 0xffd3e7e1));
        refresh();
    }

    private boolean busy() {
        return pending != null && !pending.future().isDone();
    }

    private void refresh() {
        if (closed || !client.current()) {
            return;
        }
        long requestGeneration = ++generation;
        if (pending != null) {
            pending.cancel();
        }
        model = null;
        preview.clear();
        candidate = null;
        revocation = null;
        results = null;
        rows.rows(List.of());
        var request = service.list(page);
        pending = request;
        request.future().whenComplete((value, failure) -> {
            if (closed || generation != requestGeneration) {
                return;
            }
            if (failure != null) {
                failed(failure);
                return;
            }
            model = value.model();
            preview.model(model.modelId(), Component.literal(model.displayName()));
            results = value.grants();
            message = FiguraText.of(model.visibility().equals("PRIVATE") ? "gui.library.private_grants" : "gui.library.uploader_granted");
            rows.rows(results.items().stream().map(this::grantRow).toList());
        });
    }

    private LibraryRowList.Row grantRow(LibraryAuthorization.Player player) {
        boolean revocable = !model.uploader().uuid().equals(player.uuid());
        Component tooltip = Component.literal(Instant.ofEpochSecond(player.createdAt()).toString())
                .append("\n").append(FiguraText.of(revocable ? "gui.library.revoke" : "gui.library.uploader_granted"));
        return new LibraryRowList.Row(Component.empty(), tooltip, revocable, () -> {
            if (!busy() && revocable) {
                revocation = player;
                candidate = null;
                retry = null;
                message = Component.literal(player.name() + "  " + player.uuid());
            }
        }, List.of(Component.literal(player.name()), Component.literal(player.uuid().toString()),
                Component.literal(Instant.ofEpochSecond(player.createdAt()).toString())), null,
                revocable ? List.of(new LibraryRowList.Action(FiguraText.of("gui.library.revoke"),
                        FiguraText.of("gui.library.revoke"), true, () -> {}, LibraryRowList.Icon.DELETE)) : List.of());
    }

    private void resolve() {
        if (busy() || !LibraryAuthorization.available(model) || name.isBlank()) {
            return;
        }
        candidate = null;
        revocation = null;
        String requestedName = name;
        long requestGeneration = ++generation;
        var request = service.resolve(name);
        pending = request;
        request.future().whenComplete((value, failure) -> {
            if (closed || generation != requestGeneration || !requestedName.equals(name)) {
                return;
            }
            if (failure != null) {
                failed(failure);
            } else if (value.items().isEmpty()) {
                message = FiguraText.of("gui.library.player_not_found");
            } else {
                candidate = value.items().getFirst();
                message = Component.literal(candidate.name() + "  " + candidate.uuid());
            }
        });
    }

    private void change(LibraryAuthorization.Change input) {
        if (busy() || !LibraryAuthorization.available(model)) {
            return;
        }
        retry = input;
        long requestGeneration = ++generation;
        var request = service.change(input);
        pending = request;
        request.future().whenComplete((value, failure) -> {
            if (closed || generation != requestGeneration) {
                return;
            }
            if (failure != null) {
                failed(failure);
            } else {
                retry = null;
                refresh();
            }
        });
    }

    private void failed(Throwable failure) {
        if (admin != null && failure instanceof LibraryClient.Failure typed && (typed.status == 401 || typed.status == 403)) {
            admin.invalidate();
            onClose();
            return;
        }
        String code = failure instanceof LibraryClient.Failure typed ? typed.code : "LIBRARY_UNAVAILABLE";
        message = FiguraText.of("gui.library.failure", code);
        if (code.equals("AUTHORIZATION_NOT_REQUIRED") || code.equals("FORBIDDEN") || code.equals("NOT_FOUND")) {
            model = null;
            preview.clear();
            candidate = null;
            revocation = null;
            retry = null;
            rows.rows(List.of());
        }
    }

    @Override
    public void tick() {
        super.tick();
        if (!client.current() || admin != null && !admin.active()) {
            onClose();
            return;
        }
        if (!formReady) {
            return;
        }
        boolean available = !closed && !busy() && LibraryAuthorization.available(model);
        resolve.setActive(available && !name.isBlank());
        confirm.setActive(available && (candidate != null || revocation != null || retry != null));
        confirm.setMessage(FiguraText.of(retry != null ? "gui.library.retry_change"
                : revocation != null ? "gui.library.confirm_revoke" : "gui.library.confirm_grant"));
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
        generation++;
        candidate = null;
        revocation = null;
        model = null;
        retry = null;
        preview.close();
        if (pending != null) {
            pending.cancel();
        }
        super.removed();
    }
}
