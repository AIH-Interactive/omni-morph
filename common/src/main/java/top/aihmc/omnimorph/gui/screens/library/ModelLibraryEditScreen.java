package top.aihmc.omnimorph.gui.screens.library;

import net.minecraft.client.Screenshot;
import net.minecraft.client.input.MouseButtonEvent;
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
import top.aihmc.omnimorph.model.library.LibraryUploadService;
import top.aihmc.omnimorph.model.library.LibraryThumbnailService;
import top.aihmc.omnimorph.model.library.LibraryModelEditOperation;
import top.aihmc.omnimorph.model.library.PreviewCover;
import top.aihmc.omnimorph.backend2.protocol.CloudHttp;
import org.figuramc.figura.FiguraMod;
import org.figuramc.figura.utils.FiguraText;
import org.figuramc.figura.utils.ui.UIHelper;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

/** Reads fresh details before editing and retries an immutable CAS patch after uncertain failures. */
public final class ModelLibraryEditScreen extends LibraryFormScreen {
    private final LibraryClient client;
    private final LibraryModels.Capabilities capabilities;
    private final LibraryAdminSession admin;
    private final UUID modelId;
    private final LibraryManagementPreview preview;
    private final LibraryThumbnailService thumbnails;
    private LibraryThumbnailService.Handle cover;
    private LibraryUploadService.Image image;
    private LibraryClient.Request<?> imageRequest;
    private boolean clearCover;
    private boolean retainingChild;
    private boolean framingCover;
    private boolean framingDrag;
    private boolean capturingCover;
    private int frameStartX;
    private int frameStartY;
    private int frameEndX;
    private int frameEndY;
    private PreviewCover.Region pendingFrame;
    private int framesBeforeCapture;
    private Button frameButton;
    private LibraryClient.Request<?> pending;
    private LibraryModels.Model model;
    private LibraryModelEditOperation submitted;
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
        thumbnails = new LibraryThumbnailService(client, FiguraMod.getCacheDirectory(),
                () -> admin == null ? "" : admin.token());
    }

    @Override
    public Class<? extends Screen> getSelectedPanel() {
        return ModelLibraryScreen.class;
    }

    @Override
    protected void init() {
        retainingChild = false;
        framingCover = false;
        framingDrag = false;
        pendingFrame = null;
        frameButton = null;
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
            body.row(FiguraText.of("gui.library.table_cover"),
                    new LibraryCoverChooser(fieldWidth, () -> cover == null ? null : cover.capture(),
                            this::pickImage, () -> {
                                if (!busy() && submitted == null) {
                                    clearImage();
                                }
                            }, editable && model.allows("image")), 68, label);
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
        frameButton = addRenderableWidget(new Button(layout.previewX() + layout.previewWidth() - 72,
                layout.footerY() - 24, 72, 12, FiguraText.of("gui.library.cover_frame"),
                FiguraText.of("gui.library.cover_frame_hint"), ignored -> {
                    framingCover = !framingCover;
                    framingDrag = false;
                }));
        frameButton.setActive(model != null && model.allows("image") && submitted == null && !busy() && preview.ready());
        addRenderableOnly((gui, x, y, delta) -> renderCoverFrame(gui));
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
        return loading || framingDrag || capturingCover || pendingFrame != null
                || pending != null && !pending.future().isDone()
                || imageRequest != null && !imageRequest.future().isDone();
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
                image = null;
                clearCover = false;
                if (cover != null) {
                    cover.close();
                }
                cover = value.imageAvailable() ? thumbnails.acquire(modelId) : null;
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
                var metadata = LibraryModelEdits.snapshot(model, client.session().player(), admin != null,
                        capabilities, UUID.randomUUID(), name, description, capabilities.categories().get(category),
                        tags, isPublic, requiresAuthorization);
                submitted = new LibraryModelEditOperation(modelId, metadata, image, clearCover);
            }
            var request = client.submit(LibraryClient.Lane.CONTROL, exchange -> {
                if (admin != null) {
                    exchange.adminToken(admin.token());
                }
                submitted.execute(exchange);
                return true;
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

    private void pickImage() {
        if (busy() || submitted != null) {
            return;
        }
        retainingChild = true;
        minecraft.setScreen(new ModelFilePickerScreen(this, client, FiguraMod.getFiguraDirectory(),
                ModelFilePickerScreen.Kind.IMAGE, this::selectImage));
    }

    private void selectImage(Path path) {
        if (closed || busy() || submitted != null) {
            return;
        }
        var request = client.submit(LibraryClient.Lane.THUMBNAIL, exchange -> {
            try (var input = Files.newInputStream(path)) {
                byte[] bytes = CloudHttp.readBounded(input, (int) Math.min(capabilities.maxImageBytes(), 2 * 1024 * 1024));
                var decoded = LibraryThumbnailService.decode(bytes);
                if (decoded.sourceWidth() > capabilities.maxImageWidth()
                        || decoded.sourceHeight() > capabilities.maxImageHeight()) {
                    throw new java.io.IOException("IMAGE_DIMENSION_LIMIT");
                }
                boolean png = bytes.length >= 8 && bytes[0] == (byte) 0x89 && bytes[1] == 'P';
                return new LibraryUploadService.Image(bytes, png ? "image/png" : "image/jpeg");
            }
        });
        imageRequest = request;
        rebuildWidgets();
        request.future().whenComplete((value, failure) -> {
            if (closed || imageRequest != request) {
                return;
            }
            if (failure == null) {
                replaceImage(value);
            } else {
                message = FiguraText.of("gui.library.image_retry");
            }
            rebuildWidgets();
        });
    }

    private void clearImage() {
        if (cover != null) {
            cover.close();
            cover = null;
        }
        image = null;
        clearCover = model != null && model.imageAvailable();
    }

    private void replaceImage(LibraryUploadService.Image selected) {
        clearImage();
        clearCover = false;
        image = selected;
        cover = thumbnails.acquireLocal(selected.bytes());
        message = Component.empty();
    }

    private void renderCoverFrame(net.minecraft.client.gui.GuiGraphicsExtractor gui) {
        var entity = preview.entityPreview();
        if (entity == null) {
            return;
        }
        if (framingCover) {
            int accent = FiguraMod.getAccentColor().getColor().getValue() | 0xff000000;
            UIHelper.fillOutline(gui, entity.getX(), entity.getY(), entity.getWidth(), entity.getHeight(), accent);
            var square = framingDrag ? PreviewCover.framed(frameStartX, frameStartY, frameEndX, frameEndY,
                    entity.getX(), entity.getY(), entity.getWidth(), entity.getHeight()) : null;
            if (square != null) {
                UIHelper.fillOutline(gui, square.x() - 1, square.y() - 1, square.side() + 2, square.side() + 2, accent);
            }
        }
        // Capture only after the selection outline has disappeared from the rendered frame.
        if (pendingFrame != null && --framesBeforeCapture <= 0) {
            var region = pendingFrame;
            pendingFrame = null;
            captureCover(region);
        }
    }

    private void captureCover(PreviewCover.Region region) {
        int size = Math.min(PreviewCover.SIZE, Math.min(capabilities.maxImageWidth(), capabilities.maxImageHeight()));
        if (size < 1) {
            message = FiguraText.of("gui.library.image_retry");
            return;
        }
        int guiScale = minecraft.getWindow().getGuiScale();
        capturingCover = true;
        Screenshot.takeScreenshot(minecraft.getMainRenderTarget(), screenshot -> {
            try (screenshot) {
                if (closed || minecraft.screen != this) {
                    return;
                }
                int[] square = PreviewCover.crop(screenshot.getPixels(), screenshot.getWidth(), screenshot.getHeight(),
                        region, guiScale, size);
                if (square == null) {
                    message = FiguraText.of("gui.library.image_retry");
                    return;
                }
                byte[] bytes = PreviewCover.png(square, size);
                if (bytes.length > capabilities.maxImageBytes()) {
                    message = FiguraText.of("gui.library.image_retry");
                    return;
                }
                replaceImage(new LibraryUploadService.Image(bytes, "image/png"));
            } catch (RuntimeException failure) {
                FiguraMod.LOGGER.warn("Edited model cover capture failed", failure);
                message = FiguraText.of("gui.library.image_retry");
            } finally {
                capturingCover = false;
            }
        });
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        var entity = preview.entityPreview();
        if (framingCover && !busy() && preview.ready() && event.button() == 0
                && entity != null && entity.isMouseOver(event.x(), event.y())) {
            framingDrag = true;
            frameStartX = (int) event.x();
            frameStartY = (int) event.y();
            frameEndX = frameStartX;
            frameEndY = frameStartY;
            return true;
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double deltaX, double deltaY) {
        if (framingDrag) {
            frameEndX = (int) event.x();
            frameEndY = (int) event.y();
            return true;
        }
        return super.mouseDragged(event, deltaX, deltaY);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        if (framingDrag) {
            framingDrag = false;
            framingCover = false;
            var entity = preview.entityPreview();
            pendingFrame = PreviewCover.framed(frameStartX, frameStartY, (int) event.x(), (int) event.y(),
                    entity.getX(), entity.getY(), entity.getWidth(), entity.getHeight());
            framesBeforeCapture = 2;
            return true;
        }
        return super.mouseReleased(event);
    }

    @Override
    public void tick() {
        super.tick();
        // Preview readiness changes after the form is built; enable framing once loading finishes.
        if (frameButton != null) {
            frameButton.setActive(model != null && model.allows("image") && submitted == null
                    && !busy() && preview.ready());
        }
        if ((!client.current() || admin != null && !admin.active()) && !closed) {
            onClose();
        }
    }

    @Override
    public void removed() {
        if (!retainingChild && !preview.isExpanded()) {
            closed = true;
            if (pending != null) {
                pending.cancel();
            }
            preview.close();
            if (imageRequest != null) {
                imageRequest.cancel();
            }
            thumbnails.close();
        }
        super.removed();
    }
}
