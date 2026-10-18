package top.aihmc.omnimorph.gui.screens.library;

import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import top.aihmc.omnimorph.FiguraMod;
import top.aihmc.omnimorph.avatar.AvatarLoadExecutor;
import top.aihmc.omnimorph.backend2.library.LibraryClient;
import top.aihmc.omnimorph.backend2.library.LibraryModels;
import top.aihmc.omnimorph.backend2.protocol.CloudHttp;
import top.aihmc.omnimorph.gui.screens.AbstractPanelScreen;
import top.aihmc.omnimorph.gui.screens.ModelLibraryScreen;
import top.aihmc.omnimorph.gui.widgets.Button;
import top.aihmc.omnimorph.gui.widgets.EntityPreview;
import top.aihmc.omnimorph.gui.widgets.SwitchButton;
import top.aihmc.omnimorph.gui.widgets.TextField;
import top.aihmc.omnimorph.gui.widgets.library.LibraryFormBody;
import top.aihmc.omnimorph.model.library.LibraryPackagePreparation;
import top.aihmc.omnimorph.model.library.LibraryPreviewSession;
import top.aihmc.omnimorph.model.library.LibraryThumbnailService;
import top.aihmc.omnimorph.model.library.LibraryUploadDraft;
import top.aihmc.omnimorph.model.library.LibraryUploadService;
import top.aihmc.omnimorph.utils.FiguraIdentifier;
import top.aihmc.omnimorph.utils.FiguraText;
import top.aihmc.omnimorph.utils.ui.UIHelper;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletionException;

/** Local source preparation and isolated previews never equip or broadcast the draft. */
public final class ModelUploadScreen extends AbstractPanelScreen {
    private final LibraryClient client;
    private final LibraryUploadDraft draft;
    private final LibraryUploadService uploads;
    private final LibraryPreviewSession preview;
    private final LibraryThumbnailService thumbnails;
    private final Runnable published;
    private LibraryModels.Capabilities limits;
    private LibraryPackagePreparation.Prepared prepared;
    private LibraryUploadService.Operation operation;
    private LibraryUploadService.Image image;
    private LibraryThumbnailService.Handle cover;
    private LibraryClient.Request<?> sourceRequest;
    private LibraryClient.Request<?> imageRequest;
    private LibraryClient.Request<?> sessionRequest;
    private final List<Button> editableButtons = new ArrayList<>();
    private final List<TextField> editableFields = new ArrayList<>();
    private TextField name;
    private TextField description;
    private Button sourceButton;
    private Button author;
    private Button uploader;
    private Button imageButton;
    private Button upload;
    private SwitchButton authorization;
    private Path source;
    private Path imagePath;
    private String tags = "";
    private String uploaderName;
    private int category;
    private volatile long generation;
    private volatile long imageGeneration;
    private boolean building;
    private boolean retainingChild;
    private volatile boolean closed;
    private volatile LibraryPackagePreparation.Phase preparationPhase = LibraryPackagePreparation.Phase.READING;
    private Component message = Component.empty();

    public ModelUploadScreen(Screen parent, LibraryClient client, LibraryModels.Capabilities limits,
                              Runnable published) {
        super(parent, FiguraText.of("gui.library.upload"));
        this.client = client;
        this.limits = limits;
        this.published = published;
        uploaderName = client.session().player().toString();
        draft = new LibraryUploadDraft(client.session().player());
        uploads = new LibraryUploadService(client);
        preview = new LibraryPreviewSession(client);
        thumbnails = new LibraryThumbnailService(client, FiguraMod.getCacheDirectory());
        if (!limits.categories().isEmpty()) {
            draft.category(limits.categories().getFirst());
        }
    }

    @Override
    public Class<? extends Screen> getSelectedPanel() {
        return ModelLibraryScreen.class;
    }

    @Override
    protected void init() {
        super.init();
        retainingChild = false;
        if (width < 300 || height < 220) {
            addRenderableOnly((gui, x, y, delta) -> UIHelper.renderCenteredScrollingText(gui,
                    FiguraText.of("gui.library.scale"), 8, height / 2, width - 16, 20, 0xffffffff));
            addRenderableWidget(new Button(width - 84, height - 24, 80, 20,
                    FiguraText.of("gui.done"), null, button -> onClose()));
            return;
        }
        building = true;
        editableButtons.clear();
        editableFields.clear();
        int left = Math.max(160, (width - 16) * 3 / 5);
        int rightX = left + 12;
        int right = width - rightX - 4;
        var body = addRenderableWidget(new LibraryFormBody(4, 28, left, Math.max(40, height - 36)));
        int fieldWidth = left - 22;
        sourceButton = body.row(FiguraText.of("gui.library.source"), button(fieldWidth,
                FiguraText.of("gui.library.choose_model"), ignored -> pick(ModelFilePickerScreen.Kind.MODEL)));
        uploader = body.row(FiguraText.of("gui.library.uploader"), readonly(fieldWidth,
                Component.literal(uploaderName)));
        author = body.row(FiguraText.of("gui.library.authors"), readonly(fieldWidth, Component.empty()));
        name = body.row(FiguraText.of("gui.library.display_name"), field(fieldWidth, draft.displayName(), value -> {
            if (!building) {
                draft.displayName(value);
            }
        }));
        description = body.row(FiguraText.of("gui.library.description"), field(fieldWidth, draft.description(), value -> {
            if (!building) {
                draft.description(value);
            }
        }));
        body.row(FiguraText.of("gui.library.category"), button(fieldWidth,
                Component.literal(limits.categories().isEmpty() ? "" : limits.categories().get(category)), ignored -> {
                    if (!limits.categories().isEmpty()) {
                        category = (category + 1) % limits.categories().size();
                        draft.category(limits.categories().get(category));
                        ignored.setMessage(Component.literal(limits.categories().get(category)));
                    }
                }));
        body.row(FiguraText.of("gui.library.tags"), field(fieldWidth, tags, value -> {
            if (!building) {
                tags = value;
                draft.tags(value);
            }
        }));
        var visibility = new SwitchButton(0, 0, fieldWidth, 20, FiguraText.of("gui.library.public"), null,
                button -> draft.isPublic(((SwitchButton) button).isToggled()));
        visibility.setToggled(draft.isPublic());
        editableButtons.add(visibility);
        body.row(FiguraText.of("gui.library.visibility"), visibility);
        authorization = new SwitchButton(0, 0, fieldWidth, 20, FiguraText.of("gui.library.needs_authorization"),
                FiguraText.of("gui.library.uploader_granted"), button -> {
                    if (!building) {
                        draft.requiresAuthorization(((SwitchButton) button).isToggled());
                    }
                });
        editableButtons.add(authorization);
        body.row(FiguraText.of("gui.library.authorization"), authorization);
        imageButton = body.row(FiguraText.of("gui.library.cover"), button(fieldWidth,
                FiguraText.of("gui.library.choose_image"), ignored -> pick(ModelFilePickerScreen.Kind.IMAGE)));
        if (imagePath != null) {
            imageButton.setMessage(Component.literal(imagePath.getFileName().toString()));
        }
        body.row(Component.empty(), button(fieldWidth, FiguraText.of("gui.library.clear_image"), ignored -> clearImage()));
        body.row(Component.empty(), button(fieldWidth, FiguraText.of("gui.library.preview_draft"), ignored -> {
            if (prepared != null) {
                preview.selectDraft(prepared::nbt);
            }
        }));
        var entity = addRenderableWidget(new EntityPreview(rightX, 30, right, Math.max(32, height - 164),
                Math.max(12, right / 3), -15, 30, preview.entity(), this));
        entity.setLibraryPreview(preview);
        upload = addRenderableWidget(new Button(rightX, height - 48, right, 20,
                FiguraText.of("gui.library.publish"), null, ignored -> submit()));
        addRenderableWidget(new Button(rightX, height - 24, right, 20,
                FiguraText.of("gui.done"), null, ignored -> onClose()));
        addRenderableWidget(new Button(rightX, height - 72, right, 20,
                FiguraText.of("gui.library.cancel"), null, ignored -> cancelUpload()));
        addRenderableOnly((gui, x, y, delta) -> {
            var texture = cover == null ? null : cover.capture();
            if (texture == null) {
                texture = new FiguraIdentifier("textures/gui/library_placeholder.png");
            }
            int size = Math.max(16, Math.min(right, 56));
            gui.blit(RenderPipelines.GUI_TEXTURED, texture, rightX, height - 144,
                    0, 0, size, size, size, size);
            Component status = message;
            if (operation != null && !operation.future().isDone() && operation.progress() != null) {
                var progress = operation.progress();
                status = progress.total() > 0 ? FiguraText.of("gui.library.sent", progress.received(), progress.total())
                        : FiguraText.of("gui.library.phase." + progress.stage().name().toLowerCase(java.util.Locale.ROOT));
            } else if (sourceRequest != null && !sourceRequest.future().isDone()) {
                status = FiguraText.of("gui.library.phase." + preparationPhase.name().toLowerCase(java.util.Locale.ROOT));
            } else if (preview.status().stage() == LibraryPreviewSession.Stage.FAILED) {
                status = FiguraText.of("gui.library.failure", preview.status().errorCode());
            }
            UIHelper.renderScrollingText(gui, status, rightX, height - 84, right, 0xffd3e7e1);
        });
        updateSourceFields();
        building = false;
        if (sessionRequest == null) {
            var request = client.sessionInfo();
            sessionRequest = request;
            request.future().whenComplete((session, failure) -> {
                if (!closed && failure == null && session.uuid().equals(client.session().player())) {
                    uploaderName = session.name();
                    uploader.setMessage(Component.literal(session.name()));
                    uploader.setTooltip(Component.literal(session.uuid().toString()));
                }
            });
        }
        updateButtons();
    }

    private Button button(int width, Component text, Button.OnPress action) {
        var widget = new Button(0, 0, width, 20, text, text, action);
        editableButtons.add(widget);
        return widget;
    }

    private Button readonly(int width, Component text) {
        var widget = new Button(0, 0, width, 20, text, text, ignored -> {});
        widget.setActive(false);
        return widget;
    }

    private TextField field(int width, String value, java.util.function.Consumer<String> listener) {
        var widget = new TextField(0, 0, width, 20, TextField.HintType.ANY, listener);
        widget.getField().setMaxLength(4096);
        widget.getField().setValue(value);
        editableFields.add(widget);
        return widget;
    }

    private boolean busy() {
        return operation != null && !operation.future().isDone();
    }

    private void pick(ModelFilePickerScreen.Kind kind) {
        Path selected = kind == ModelFilePickerScreen.Kind.MODEL ? source : imagePath;
        Path directory = selected == null ? FiguraMod.getFiguraDirectory() : selected.toAbsolutePath().getParent();
        retainingChild = true;
        minecraft.setScreen(new ModelFilePickerScreen(this, client, directory, kind,
                kind == ModelFilePickerScreen.Kind.MODEL ? this::selectFile : this::selectImage));
    }

    private void selectFile(Path path) {
        if (busy() || closed || !ModelFilePickerScreen.accepts(path, ModelFilePickerScreen.Kind.MODEL)) {
            message = FiguraText.of("gui.library.file_error");
            return;
        }
        long requestGeneration = ++generation;
        long sourceGeneration = draft.beginSource(path);
        source = path;
        prepared = null;
        if (operation != null) {
            operation.close();
        }
        operation = null;
        preview.clear();
        if (sourceRequest != null) {
            sourceRequest.cancel();
        }
        message = FiguraText.of("gui.library.phase.reading");
        updateSourceFields();
        var request = client.submit(LibraryClient.Lane.TRANSFER, exchange -> {
            var preparation = AvatarLoadExecutor.submit(() -> {
                try {
                    return LibraryPackagePreparation.prepare(path, client.session().player(), limits.maxUploadBytes(),
                            () -> closed || generation != requestGeneration || !client.current(), phase -> {
                                if (!closed && generation == requestGeneration) {
                                    preparationPhase = phase;
                                }
                            });
                } catch (Exception failure) {
                    throw new CompletionException(failure);
                }
            });
            try {
                return preparation.get();
            } catch (InterruptedException interrupted) {
                preparation.cancel(false);
                throw interrupted;
            }
        });
        sourceRequest = request;
        request.future().whenComplete((value, failure) -> {
            if (closed || requestGeneration != generation) {
                return;
            }
            if (failure != null) {
                message = FiguraText.of("gui.library.preparation_error");
            } else if (draft.prepared(sourceGeneration, value)) {
                prepared = value;
                message = FiguraText.of("gui.library.ready_upload", value.byteLength(), limits.maxUploadBytes());
                updateSourceFields();
            }
            updateButtons();
        });
    }

    private void selectImage(Path path) {
        if (busy() || closed || !ModelFilePickerScreen.accepts(path, ModelFilePickerScreen.Kind.IMAGE)) {
            return;
        }
        long expected = ++imageGeneration;
        if (imageRequest != null) {
            imageRequest.cancel();
        }
        var request = client.submit(LibraryClient.Lane.THUMBNAIL, exchange -> {
            try (var input = Files.newInputStream(path)) {
                byte[] bytes = CloudHttp.readBounded(input, (int) Math.min(limits.maxImageBytes(), 2 * 1024 * 1024));
                var decoded = LibraryThumbnailService.decode(bytes);
                if (decoded.sourceWidth() > limits.maxImageWidth() || decoded.sourceHeight() > limits.maxImageHeight()) {
                    throw new java.io.IOException("IMAGE_DIMENSION_LIMIT");
                }
                boolean png = bytes.length >= 8 && bytes[0] == (byte) 0x89 && bytes[1] == 'P';
                return new LibraryUploadService.Image(bytes, png ? "image/png" : "image/jpeg");
            }
        });
        imageRequest = request;
        request.future().whenComplete((value, failure) -> {
            if (closed || expected != imageGeneration) {
                return;
            }
            if (failure != null) {
                clearImage();
                message = FiguraText.of("gui.library.image_retry");
            } else {
                if (cover != null) {
                    cover.close();
                }
                image = value;
                imagePath = path;
                cover = thumbnails.acquireLocal(value.bytes());
                if (imageButton != null) {
                    imageButton.setMessage(Component.literal(path.getFileName().toString()));
                }
            }
        });
    }

    private void clearImage() {
        imageGeneration++;
        if (imageRequest != null) {
            imageRequest.cancel();
        }
        if (cover != null) {
            cover.close();
            cover = null;
        }
        image = null;
        imagePath = null;
        if (imageButton != null) {
            imageButton.setMessage(FiguraText.of("gui.library.choose_image"));
        }
    }

    private void updateSourceFields() {
        if (name == null) {
            return;
        }
        boolean previous = building;
        building = true;
        name.getField().setValue(draft.displayName());
        description.getField().setValue(draft.description());
        authorization.setToggled(draft.requiresAuthorization());
        sourceButton.setMessage(source == null ? FiguraText.of("gui.library.choose_model")
                : Component.literal(source.getFileName().toString()));
        author.setMessage(prepared == null || prepared.metadata().authors().isEmpty()
                ? FiguraText.of("gui.library.unknown_author") : Component.literal(String.join(", ", prepared.metadata().authors())));
        author.setTooltip(author.getMessage());
        building = previous;
    }

    private void submit() {
        if (busy() || prepared == null || closed || imageRequest != null && !imageRequest.future().isDone()) {
            return;
        }
        try {
            if (operation != null && operation.future().isCompletedExceptionally()) {
                operation.retry();
            } else if (operation == null) {
                operation = uploads.upload(draft.snapshot(limits), image);
            } else {
                return;
            }
            var started = operation;
            started.future().whenComplete((result, failure) -> {
                if (!closed && operation == started) {
                    message = failure == null ? FiguraText.of("gui.library.upload_success")
                            : FiguraText.of("gui.library.failure", failure instanceof LibraryClient.Failure typed
                            ? typed.code : "UPLOAD_FAILED");
                    if (failure == null) {
                        published.run();
                    }
                    updateButtons();
                }
            });
        } catch (LibraryUploadDraft.InvalidField invalid) {
            message = FiguraText.of("gui.library.invalid_field",
                    FiguraText.of("gui.library." + invalid.field()), invalid.maximumBytes());
        } catch (IllegalArgumentException invalid) {
            message = FiguraText.of("gui.library.metadata_error");
        }
        updateButtons();
    }

    private void cancelUpload() {
        if (operation != null) {
            if (operation.future().isDone() && !operation.future().isCompletedExceptionally()) {
                onClose();
                return;
            }
            operation.cancel();
            operation = null;
        }
        generation++;
        imageGeneration++;
        preview.clear();
        if (sourceRequest != null) {
            sourceRequest.cancel();
        }
        if (imageRequest != null) {
            imageRequest.cancel();
        }
        message = FiguraText.of("gui.library.cancelled");
        updateButtons();
    }

    private void updateButtons() {
        if (upload == null) {
            return;
        }
        boolean editable = !closed && client.current() && !busy() && operation == null;
        editableButtons.forEach(button -> button.setActive(editable));
        editableFields.forEach(field -> field.setEnabled(editable));
        upload.setActive(!closed && client.current() && !busy() && prepared != null
                && (imageRequest == null || imageRequest.future().isDone())
                && (operation == null || operation.future().isCompletedExceptionally()));
        upload.setMessage(FiguraText.of(operation != null && operation.future().isCompletedExceptionally()
                ? "gui.library.retry_upload" : "gui.library.publish"));
    }

    @Override
    public void onFilesDrop(List<Path> paths) {
        if (!paths.isEmpty()) {
            if (ModelFilePickerScreen.accepts(paths.getFirst(), ModelFilePickerScreen.Kind.IMAGE)) {
                selectImage(paths.getFirst());
            } else {
                selectFile(paths.getFirst());
            }
        }
    }

    @Override
    public void tick() {
        super.tick();
        preview.tick();
        if (!client.current()) {
            message = FiguraText.of("gui.library.state.offline");
        }
        updateButtons();
    }

    @Override
    public void removed() {
        if (!retainingChild && !preview.isExpanded()) {
            closed = true;
            generation++;
            imageGeneration++;
            if (sourceRequest != null) {
                sourceRequest.cancel();
            }
            if (imageRequest != null) {
                imageRequest.cancel();
            }
            if (sessionRequest != null) {
                sessionRequest.cancel();
            }
            uploads.close();
            preview.close();
            thumbnails.close();
        }
        super.removed();
    }
}
