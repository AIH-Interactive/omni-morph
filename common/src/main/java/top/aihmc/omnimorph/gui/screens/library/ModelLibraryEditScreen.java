package top.aihmc.omnimorph.gui.screens.library;

import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import top.aihmc.omnimorph.backend2.library.LibraryClient;
import top.aihmc.omnimorph.backend2.library.LibraryAdminSession;
import top.aihmc.omnimorph.backend2.library.LibraryModels;
import top.aihmc.omnimorph.gui.screens.ModelLibraryScreen;
import org.figuramc.figura.gui.widgets.Button;
import org.figuramc.figura.gui.widgets.TextField;
import top.aihmc.omnimorph.gui.widgets.library.*;
import top.aihmc.omnimorph.model.library.LibraryModelEdits;
import top.aihmc.omnimorph.model.library.LibraryUploadDraft;
import org.figuramc.figura.utils.FiguraText;
import org.figuramc.figura.utils.ui.UIHelper;

import java.util.Map;
import java.util.UUID;

/** Reads fresh details before editing and retries an immutable CAS patch after uncertain failures. */
public final class ModelLibraryEditScreen extends LibraryFormScreen {
    private final LibraryClient client;
    private final LibraryModels.Capabilities capabilities;
    private final LibraryAdminSession admin;
    private final UUID modelId;
    private final LibraryManagementPreview preview;
    private LibraryClient.Request<?> pending;
    private LibraryModels.Model model;
    private Map<String, Object> submitted;
    private String name = "";
    private String description = "";
    private String tags = "";
    private int category;
    private boolean isPublic;
    private boolean requiresAuthorization;
    private boolean loading;
    private boolean closed;
    private Component message = Component.empty();

    public ModelLibraryEditScreen(Screen parent, LibraryClient client, LibraryModels.Capabilities capabilities,
                                   UUID modelId) {
        this(parent, client, capabilities, modelId, null);
    }

    public ModelLibraryEditScreen(Screen parent, LibraryClient client, LibraryModels.Capabilities capabilities,
                                   UUID modelId, LibraryAdminSession admin) {
        super(parent, FiguraText.of("gui.library.edit"));
        this.client = client;
        this.capabilities = capabilities;
        this.modelId = modelId;
        this.admin = admin;
        preview = new LibraryManagementPreview(client, () -> admin == null ? "" : admin.token());
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
        addRenderableWidget(preview.place(this, layout.previewX(), layout.top(), layout.previewWidth(),
                layout.previewHeight()));
        var body = addRenderableWidget(new LibraryFormBody(layout.left(), layout.bodyY(),
                layout.contentWidth(), layout.bodyHeight() - 12));
        addRenderableOnly((gui, x, y, delta) -> {
            UIHelper.renderScrollingText(gui, getTitle(), layout.left(), layout.top() + 5,
                    layout.contentWidth(), 0xffeeeeee);
            UIHelper.renderScrollingText(gui, message, layout.left(), layout.footerY() - 12,
                    layout.contentWidth(), 0xffd3e7e1);
        });
        if (model != null) {
            int label = Math.min(72, layout.contentWidth() / 4);
            int fieldWidth = Math.max(24, layout.contentWidth() - label - 22);
            boolean editable = submitted == null && !busy();
            var nameField = new TextField(0, 0, fieldWidth, 20, TextField.HintType.ANY, value -> name = value);
            nameField.getField().setValue(name);
            nameField.setEnabled(editable);
            body.row(FiguraText.of("gui.library.display_name"), nameField, 24, label);
            body.row(FiguraText.of("gui.library.authors"), readonly(fieldWidth,
                    Component.literal(String.join(", ", model.authors())),
                    Component.literal(String.join("\n", model.authors()))), 24, label);
            body.row(FiguraText.of("gui.library.uploader"), readonly(fieldWidth,
                    Component.literal(model.uploader().name())), 24, label);
            var categories = new LibraryDropdown(0, 0, fieldWidth,
                    () -> capabilities.categories().stream().map(LibraryLabels::category).toList(),
                    () -> category, value -> category = value);
            categories.setActive(editable);
            body.row(FiguraText.of("gui.library.category"), categories, 24, label);
            var tagField = new LibraryTagField(fieldWidth, tags, value -> tags = value);
            tagField.setEnabled(editable);
            body.row(FiguraText.of("gui.library.tags"), tagField, 48, label);
            var visibility = new LibraryRadioGroup(fieldWidth, FiguraText.of("gui.library.public"),
                    FiguraText.of("gui.library.private"), isPublic, value -> isPublic = value);
            visibility.setActive(editable && !model.moderationStatus().equals("BLOCKED"));
            body.row(FiguraText.of("gui.library.visibility"), visibility, 24, label);
            var authorization = new LibraryRadioGroup(fieldWidth, FiguraText.of("gui.yes"), FiguraText.of("gui.no"),
                    requiresAuthorization, value -> requiresAuthorization = value);
            authorization.setActive(editable);
            body.row(FiguraText.of("gui.library.authorization"), authorization, 24, label);
            var descriptionField = new LibraryMultilineField(fieldWidth, 72, description, value -> description = value);
            descriptionField.setCharacterLimit(4096);
            descriptionField.setEnabled(editable);
            body.row(FiguraText.of("gui.library.description"), descriptionField, 76, label);
        }
        int buttonWidth = Math.max(40, (layout.contentWidth() - 8) / 3);
        addRenderableWidget(new Button(layout.left(), layout.footerY(), buttonWidth, 20,
                FiguraText.of("gui.library.cancel"), null, ignored -> onClose()));
        var reload = addRenderableWidget(new Button(layout.left() + buttonWidth + 4, layout.footerY(), buttonWidth, 20,
                FiguraText.of("gui.library.refresh"), null, ignored -> load()));
        reload.setActive(!busy());
        var save = addRenderableWidget(new Button(layout.left() + (buttonWidth + 4) * 2, layout.footerY(), buttonWidth, 20,
                FiguraText.of(submitted == null ? "gui.library.save_changes" : "gui.library.retry_change"),
                null, ignored -> save()));
        save.setActive(!busy() && canEdit(model));
        if (model == null && pending == null) {
            load();
        }
    }

    private Button readonly(int width, Component text) {
        return readonly(width, text, text);
    }

    private Button readonly(int width, Component text, Component tooltip) {
        var button = new Button(0, 0, width, 20, text, tooltip, ignored -> {});
        button.setActive(false);
        return button;
    }

    private boolean busy() {
        return loading || pending != null && !pending.future().isDone();
    }

    private boolean canEdit(LibraryModels.Model value) {
        return (admin == null || admin.active())
                && LibraryModelEdits.available(value, client.session().player(), admin != null);
    }

    private void load() {
        if (closed || busy()) {
            return;
        }
        loading = true;
        message = FiguraText.of("gui.library.state.loading");
        var request = client.submit(LibraryClient.Lane.CONTROL, exchange -> {
            if (admin != null) {
                exchange.adminToken(admin.token());
            }
            return exchange.json("GET", "/api/library/v1/models/" + modelId, null, LibraryModels.Model.class);
        });
        pending = request;
        request.future().whenComplete((value, failure) -> {
            if (closed || pending != request) {
                return;
            }
            loading = false;
            if (failure == null && canEdit(value)) {
                model = value;
                name = value.displayName();
                description = value.description();
                tags = String.join(", ", value.tags());
                category = Math.max(0, capabilities.categories().indexOf(value.categoryId()));
                isPublic = value.visibility().equals("PUBLIC");
                requiresAuthorization = value.requiresAuthorization();
                submitted = null;
                message = Component.empty();
                preview.model(modelId, Component.literal(name));
            } else {
                model = null;
                message = error(failure, "FORBIDDEN");
                preview.clear();
            }
            rebuildWidgets();
        });
        rebuildWidgets();
    }

    private void save() {
        if (closed || busy() || !canEdit(model)) {
            return;
        }
        try {
            if (submitted == null) {
                submitted = LibraryModelEdits.snapshot(model, client.session().player(), admin != null, capabilities, UUID.randomUUID(),
                        name, description, capabilities.categories().get(category), tags, isPublic, requiresAuthorization);
            }
            var request = client.submit(LibraryClient.Lane.CONTROL, exchange -> {
                if (admin != null) {
                    exchange.adminToken(admin.token());
                }
                return exchange.json("PATCH", "/api/library/v1/models/" + modelId, submitted, Map.class);
            });
            pending = request;
            request.future().whenComplete((result, failure) -> {
                if (closed || pending != request) {
                    return;
                }
                if (failure == null) {
                    onClose();
                } else {
                    message = failure instanceof LibraryClient.Failure typed && typed.status == 409
                            ? FiguraText.of("gui.library.edit_conflict") : error(failure, "LIBRARY_UNAVAILABLE");
                    rebuildWidgets();
                }
            });
            rebuildWidgets();
        } catch (LibraryUploadDraft.InvalidField invalid) {
            message = FiguraText.of("gui.library.invalid_field", FiguraText.of("gui.library." + invalid.field()),
                    invalid.maximumBytes());
        } catch (LibraryUploadDraft.InvalidCount invalid) {
            message = FiguraText.of("gui.library.invalid_count", FiguraText.of("gui.library." + invalid.field()),
                    invalid.maximumEntries());
        } catch (IllegalArgumentException invalid) {
            message = FiguraText.of("gui.library.metadata_error");
        }
    }

    private Component error(Throwable failure, String fallback) {
        return FiguraText.of("gui.library.failure", failure instanceof LibraryClient.Failure typed ? typed.code : fallback);
    }

    @Override
    public void tick() {
        super.tick();
        if ((!client.current() || admin != null && !admin.active()) && !closed) {
            onClose();
        }
    }

    @Override
    public void removed() {
        if (!preview.isExpanded()) {
            closed = true;
            if (pending != null) {
                pending.cancel();
            }
            preview.close();
        }
        super.removed();
    }
}
