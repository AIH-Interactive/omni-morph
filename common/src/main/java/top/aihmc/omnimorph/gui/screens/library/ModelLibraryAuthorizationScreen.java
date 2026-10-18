package top.aihmc.omnimorph.gui.screens.library;

import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import top.aihmc.omnimorph.backend2.library.LibraryAuthorization;
import top.aihmc.omnimorph.backend2.library.LibraryAdminSession;
import top.aihmc.omnimorph.backend2.library.LibraryClient;
import top.aihmc.omnimorph.backend2.library.LibraryModels;
import top.aihmc.omnimorph.gui.screens.ModelLibraryScreen;
import top.aihmc.omnimorph.gui.widgets.Button;
import top.aihmc.omnimorph.gui.widgets.TextField;
import top.aihmc.omnimorph.gui.widgets.library.LibraryRowList;
import top.aihmc.omnimorph.model.library.LibraryThumbnailService;
import top.aihmc.omnimorph.FiguraMod;
import top.aihmc.omnimorph.utils.FiguraIdentifier;
import top.aihmc.omnimorph.utils.FiguraText;
import top.aihmc.omnimorph.utils.ui.UIHelper;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

/** Verified name resolution followed by a separate UUID confirmation. Closing never submits a change. */
public final class ModelLibraryAuthorizationScreen extends LibraryFormScreen {
    private final LibraryClient client;
    private final LibraryAuthorization service;
    private final LibraryThumbnailService thumbnails;
    private LibraryThumbnailService.Handle thumbnail;
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
        thumbnails = new LibraryThumbnailService(client, FiguraMod.getCacheDirectory(), admin);
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
        int sidebar = Math.min(140, width / 4);
        int bodyX = sidebar + 20;
        int rowWidth = width - bodyX - 8;
        addRenderableOnly((gui, mouseX, mouseY, delta) -> {
            UIHelper.blitSliced(gui, 8, 52, sidebar, height - 84, UIHelper.OUTLINE_FILL);
            var texture = thumbnail == null ? null : thumbnail.capture();
            if (texture == null) {
                texture = new FiguraIdentifier("textures/gui/library_placeholder.png");
            }
            int size = Math.min(72, sidebar - 12);
            gui.blit(RenderPipelines.GUI_TEXTURED, texture, 8 + (sidebar - size) / 2, 60,
                    0, 0, size, size, size, size);
            UIHelper.renderScrollingText(gui, model == null ? Component.empty() : Component.literal(model.displayName()),
                    8, 32, width - 40, 0xffeeeeee);
            if (model != null) {
                UIHelper.renderScrollingText(gui, Component.literal(model.internalName()), 14, 64 + size, sidebar - 12, 0xffeeeeee);
                UIHelper.renderScrollingText(gui, Component.literal(String.join(", ", model.authors())),
                        14, 80 + size, sidebar - 12, 0xffbac6d8);
                UIHelper.renderScrollingText(gui, Component.literal(model.uploader().name()),
                        14, 96 + size, sidebar - 12, 0xffbac6d8);
            }
        });
        addRenderableWidget(new Button(12, height - 28, sidebar - 8, 20,
                FiguraText.of("gui.library.grants"), null, ignored -> {}));
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
        resolve = addRenderableWidget(new Button(width - 96, 52, 88, 20,
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
        addRenderableWidget(new Button(width - 96, height - 28, 88, 20,
                FiguraText.of("gui.library.refresh"), null, ignored -> refresh()));
        addRenderableWidget(new Button(width - 28, 28, 20, 20,
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
        if (thumbnail != null) {
            thumbnail.close();
            thumbnail = null;
        }
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
            if (model.imageAvailable() && model.moderationStatus().equals("ACTIVE")) {
                thumbnail = thumbnails.acquire(model.modelId());
            }
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
        closed = true;
        generation++;
        candidate = null;
        revocation = null;
        model = null;
        retry = null;
        thumbnails.close();
        thumbnail = null;
        if (pending != null) {
            pending.cancel();
        }
        super.removed();
    }
}
