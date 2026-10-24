package top.aihmc.omnimorph.gui.screens.library;

import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.figuramc.figura.FiguraMod;
import top.aihmc.omnimorph.avatar.AvatarLoadExecutor;
import org.figuramc.figura.avatar.local.LocalAvatarFetcher;
import top.aihmc.omnimorph.backend2.library.LibraryClient;
import top.aihmc.omnimorph.backend2.library.LibraryModels;
import top.aihmc.omnimorph.backend2.protocol.CloudHttp;
import org.figuramc.figura.gui.screens.AbstractPanelScreen;
import top.aihmc.omnimorph.gui.screens.ModelLibraryScreen;
import org.figuramc.figura.gui.widgets.Button;
import org.figuramc.figura.gui.widgets.EntityPreview;
import org.figuramc.figura.gui.widgets.TextField;
import top.aihmc.omnimorph.gui.widgets.library.LibraryFormBody;
import top.aihmc.omnimorph.gui.widgets.library.LibraryLabels;
import top.aihmc.omnimorph.gui.widgets.library.LibraryRadioGroup;
import top.aihmc.omnimorph.gui.widgets.library.LibraryMultilineField;
import top.aihmc.omnimorph.gui.widgets.library.LibraryTagField;
import top.aihmc.omnimorph.gui.widgets.library.LibraryDropdown;
import top.aihmc.omnimorph.gui.widgets.library.LibrarySectionTabs;
import top.aihmc.omnimorph.gui.widgets.library.LibraryUploadSource;
import top.aihmc.omnimorph.gui.widgets.library.LibraryWorkspaceLayout;
import top.aihmc.omnimorph.model.library.LibraryPackagePreparation;
import top.aihmc.omnimorph.model.library.LibraryPreviewSession;
import top.aihmc.omnimorph.model.library.LibraryThumbnailService;
import top.aihmc.omnimorph.model.library.LibraryUploadDraft;
import top.aihmc.omnimorph.model.library.LibraryUploadService;
import top.aihmc.omnimorph.model.library.OutfitUploadDraft;
import top.aihmc.omnimorph.model.library.OutfitPresetResources;
import top.aihmc.omnimorph.model.library.LibraryTransferService;
import top.aihmc.omnimorph.model.outfit.CosmeticMetadata;
import top.aihmc.omnimorph.model.outfit.OutfitConfig;
import top.aihmc.omnimorph.model.outfit.OutfitLimits;
import org.figuramc.figura.permissions.PermissionManager;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.figuramc.figura.utils.FiguraText;
import org.figuramc.figura.utils.ui.UIHelper;

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
    private LibraryMultilineField description;
    private int descriptionHeight = 56;
    private Button sourceButton;
    private Button author;
    private Button uploader;
    private Button imageButton;
    private Button upload;
    private LibraryRadioGroup authorization;
    private LibraryRadioGroup visibility;
    private LibraryTagField tagField;
    private LibrarySectionTabs sectionTabs;
    private Path source;
    private boolean accessorySource;
    private boolean outfitSource;
    private OutfitUploadDraft.Prepared outfitPrepared;
    private OutfitPresetResources outfitPreparation;
    private OutfitPresetResources.Prepared outfitResources;
    private boolean outfitReady;
    private boolean outfitDistributable;
    private String cosmeticCategory = "Unknown";
    private String cosmeticAnchor = "";
    private final String[] cosmeticTransform = {"0, 0, 0", "0, 0, 0", "1, 1, 1"};
    private final java.util.UUID previewIdentity = java.util.UUID.randomUUID();
    private java.util.concurrent.CompletableFuture<Boolean> draftPreview;
    private long previewGeneration;
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
        draft.categories(limits.categories());
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
        var layout = new LibraryWorkspaceLayout(width, height);
        int left = layout.contentWidth();
        int rightX = layout.previewX();
        int right = layout.previewWidth();
        sectionTabs = addRenderableWidget(new LibrarySectionTabs(layout.left(), layout.top(), left, 3,
                limits.adminLoginAvailable(), section -> {
                    if (!busy() && parentScreen instanceof ModelLibraryScreen catalog) {
                        minecraft.setScreen(catalog);
                        catalog.openSection(section);
                    }
                }));
        var body = addRenderableWidget(new LibraryFormBody(layout.left(), layout.bodyY(), left,
                height - layout.bodyY() - 8));
        int labelWidth = Math.min(72, Math.max(48, left / 5));
        int fieldWidth = left - labelWidth - 22;
        var purpose = new LibraryDropdown(0, 0, fieldWidth,
                this::purposes, () -> outfitSource ? 2 : accessorySource ? 1 : 0, this::changePurpose);
        editableButtons.add(purpose);
        body.row(FiguraText.of("gui.library.purpose"), purpose, 24, labelWidth);
        var sourcePicker = new LibraryUploadSource(left - 22, limits.maxUploadBytes(),
                () -> cover == null ? null : cover.capture(), () -> pick(sourceKind()),
                () -> pick(ModelFilePickerScreen.Kind.IMAGE), this::clearImage);
        if (accessorySource) {
            sourcePicker.sourceFormat(FiguraText.of("gui.library.accessory_source_format"),
                    FiguraText.of("gui.library.accessory_source_hint", LibraryLabels.fileSize(limits.maxUploadBytes())));
        }
        if (outfitSource) {
            sourcePicker.sourceFormat(Component.literal(".json"), FiguraText.of("gui.library.outfit_source_hint"));
        }
        body.row(FiguraText.of("gui.library.source"), sourcePicker, 108, 0);
        sourceButton = sourcePicker.sourceButton();
        imageButton = sourcePicker.imageButton();
        editableButtons.add(sourceButton);
        editableButtons.add(imageButton);
        editableButtons.add(sourcePicker.clearButton());
        name = body.row(FiguraText.of("gui.library.display_name"), field(fieldWidth, draft.displayName(), value -> {
            if (!building) {
                draft.displayName(value);
            }
        }), 24, labelWidth);
        uploader = body.row(FiguraText.of("gui.library.uploader"), readonly(fieldWidth,
                Component.literal(uploaderName)), 24, labelWidth);
        author = body.row(FiguraText.of("gui.library.authors"), readonly(fieldWidth, Component.empty()), 24, labelWidth);
        if (outfitSource && outfitPrepared != null) {
            var configuration = outfitPrepared.configuration();
            body.row(FiguraText.of("gui.library.outfit_summary"), readonly(fieldWidth,
                    FiguraText.of("gui.library.outfit_summary_value", configuration.base() != null,
                            configuration.instances().size(), configuration.virtualAnchors().size())), 24, labelWidth);
            var references = new java.util.LinkedHashSet<OutfitConfig.ResourceRef>();
            if (configuration.base() != null) {
                references.add(configuration.base());
            }
            configuration.instances().forEach(instance -> references.add(instance.resource()));
            for (var reference : references) {
                Component status = FiguraText.of("gui.library.state.loading");
                if (outfitResources != null) {
                    var issue = outfitResources.issues().stream().filter(value -> value.resource().equals(reference)).findFirst();
                    status = issue.isPresent() ? Component.literal(issue.get().code())
                            : FiguraText.of("gui.library.outfit_dependency_ready");
                }
                if (reference.source() == OutfitConfig.Source.LOCAL || !client.origin().equals(reference.origin())) {
                    status = FiguraText.of("gui.library.outfit_dependency_mapping");
                }
                Component label = Component.literal(reference.identity() + " @ " + reference.contentHash())
                        .append(" · ").append(status);
                body.row(FiguraText.of("gui.library.outfit_dependency"), readonly(fieldWidth, label), 24, labelWidth);
            }
        }
        if (accessorySource) {
            body.row(FiguraText.of("gui.library.cosmetic_category"), field(fieldWidth, cosmeticCategory,
                    value -> cosmeticCategory = value), 24, labelWidth);
            body.row(FiguraText.of("gui.avatar_wizard.cosmetic_anchor"), field(fieldWidth, cosmeticAnchor,
                    value -> cosmeticAnchor = value), 24, labelWidth);
            for (int index = 0; index < cosmeticTransform.length; index++) {
                int component = index;
                body.row(FiguraText.of("gui.library.cosmetic_transform_" + index),
                        field(fieldWidth, cosmeticTransform[index], value -> cosmeticTransform[component] = value),
                        24, labelWidth);
            }
            if (prepared != null) {
                var declaration = CosmeticMetadata.read(prepared.accessoryMetadata().toString());
                var capabilities = FiguraText.of("gui.library.cosmetic_declarations",
                        String.join(", ", declaration.supportedBases()), declaration.hasBones(),
                        declaration.displayBones(), declaration.editBones(), declaration.skeletonRevision());
                body.row(FiguraText.of("gui.library.cosmetic_capabilities"), readonly(fieldWidth, capabilities), 24, labelWidth);
            }
        }
        var categoryField = new LibraryDropdown(0, 0, fieldWidth,
                () -> limits.categories().stream().map(top.aihmc.omnimorph.gui.widgets.library.LibraryLabels::category).toList(),
                () -> category, selected -> {
                    if (!limits.categories().isEmpty()) {
                        category = selected;
                        draft.category(limits.categories().get(category));
                    }
                });
        editableButtons.add(categoryField);
        body.row(FiguraText.of("gui.library.category"), categoryField, 24, labelWidth);
        tagField = body.row(FiguraText.of("gui.library.tags"), new LibraryTagField(fieldWidth, tags, value -> {
            if (!building) {
                tags = value;
                draft.tags(value);
            }
        }), 48, labelWidth);
        visibility = new LibraryRadioGroup(fieldWidth, FiguraText.of("gui.library.public"),
                FiguraText.of("gui.library.private"), draft.isPublic(), draft::isPublic);
        body.row(FiguraText.of("gui.library.visibility"), visibility, 24, labelWidth);
        authorization = new LibraryRadioGroup(fieldWidth, FiguraText.of("gui.yes"), FiguraText.of("gui.no"),
                draft.requiresAuthorization(), value -> {
                    if (!building) {
                        draft.requiresAuthorization(value);
                    }
                });
        body.row(FiguraText.of("gui.library.authorization"), authorization, 24, labelWidth);
        descriptionHeight = Math.clamp(descriptionHeight, 40, Math.max(56, height / 2));
        description = body.row(FiguraText.of("gui.library.description"), new LibraryMultilineField(fieldWidth, descriptionHeight,
                draft.description(), value -> {
            if (!building) {
                draft.description(value);
            }
        }).resizable(40, Math.max(56, height / 2), value -> descriptionHeight = value), 44, labelWidth);
        if (imagePath != null) {
            imageButton.setMessage(Component.literal(imagePath.getFileName().toString()));
        }
        body.row(Component.empty(), button(fieldWidth, FiguraText.of("gui.library.preview_draft"), ignored -> {
            if (prepared != null || outfitPrepared != null) {
                previewDraft();
            }
        }), 24, labelWidth);
        var entity = addRenderableWidget(new EntityPreview(rightX, layout.top(), right, layout.previewHeight() - 16,
                Math.max(12, right / 3), -15, 30, preview.entity(), this));
        entity.setLibraryPreview(preview);
        int actionWidth = (right - 4) / 2;
        upload = addRenderableWidget(new Button(rightX + actionWidth + 4, layout.footerY(), actionWidth, 20,
                FiguraText.of("gui.library.publish"), null, ignored -> submit()));
        addRenderableWidget(new Button(rightX + right - 20, layout.top(), 20, 20,
                Component.literal("×"), FiguraText.of("gui.done"), ignored -> onClose()));
        addRenderableWidget(new Button(rightX, layout.footerY(), actionWidth, 20,
                FiguraText.of("gui.library.cancel"), null, ignored -> cancelUpload()));
        addRenderableOnly((gui, x, y, delta) -> {
            Component status = message;
            if (operation != null && !operation.future().isDone() && operation.progress() != null) {
                var progress = operation.progress();
                status = progress.total() > 0 ? FiguraText.of("gui.library.sent",
                        LibraryLabels.fileSize(progress.received()), LibraryLabels.fileSize(progress.total()))
                        : FiguraText.of("gui.library.phase." + progress.stage().name().toLowerCase(java.util.Locale.ROOT));
            } else if (sourceRequest != null && !sourceRequest.future().isDone()) {
                status = FiguraText.of("gui.library.phase." + preparationPhase.name().toLowerCase(java.util.Locale.ROOT));
            } else if (preview.status().stage() == LibraryPreviewSession.Stage.FAILED) {
                status = FiguraText.of("gui.library.failure", preview.status().errorCode());
            }
            UIHelper.renderScrollingText(gui, status, rightX, layout.footerY() - 12, right, 0xffd3e7e1);
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
        var widget = new Button(0, 0, width, 20, text, text, ignored -> {}) {
            @Override
            protected int getTextColor() {
                return 0xffbac6d8;
            }
        };
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

    private ModelFilePickerScreen.Kind sourceKind() {
        return outfitSource ? ModelFilePickerScreen.Kind.OUTFIT
                : accessorySource ? ModelFilePickerScreen.Kind.ACCESSORY : ModelFilePickerScreen.Kind.MODEL;
    }

    private void changePurpose(boolean accessory) {
        changePurpose(accessory ? 1 : 0);
    }

    private List<Component> purposes() {
        var values = new ArrayList<Component>();
        values.add(FiguraText.of("gui.library.purpose_avatar"));
        values.add(FiguraText.of("gui.library.purpose_accessory"));
        if (limits.outfitsV1() != null && limits.outfitsV1().publication()) {
            values.add(FiguraText.of("gui.library.purpose_outfit"));
        }
        return List.copyOf(values);
    }

    private void changePurpose(int purpose) {
        if (closed || !client.current() || busy() || operation != null || purpose < 0 || purpose >= purposes().size()
                || purpose == (outfitSource ? 2 : accessorySource ? 1 : 0)) {
            return;
        }
        accessorySource = purpose == 1;
        outfitSource = purpose == 2;
        generation++;
        previewGeneration++;
        if (sourceRequest != null) {
            sourceRequest.cancel();
        }
        source = null;
        prepared = null;
        outfitPrepared = null;
        outfitResources = null;
        outfitReady = false;
        if (outfitPreparation != null) {
            outfitPreparation.close();
        }
        preview.clear();
        message = Component.empty();
        rebuildWidgets();
    }

    private void pick(ModelFilePickerScreen.Kind kind) {
        Path directory;
        if (kind == ModelFilePickerScreen.Kind.MODEL) {
            directory = LocalAvatarFetcher.getLocalAvatarDirectory();
        } else if (kind == ModelFilePickerScreen.Kind.ACCESSORY) {
            directory = FiguraMod.getFiguraDirectory().resolve(CosmeticMetadata.DIRECTORY);
        } else if (kind == ModelFilePickerScreen.Kind.OUTFIT) {
            directory = FiguraMod.getFiguraDirectory().resolve("outfit-presets");
        } else {
            directory = imagePath == null ? FiguraMod.getFiguraDirectory() : imagePath.toAbsolutePath().getParent();
        }
        retainingChild = true;
        minecraft.setScreen(new ModelFilePickerScreen(this, client, directory, kind,
                kind == ModelFilePickerScreen.Kind.IMAGE ? this::selectImage : this::selectFile));
    }

    private void selectFile(Path path) {
        if (busy() || closed || !client.current() || !ModelFilePickerScreen.accepts(path, sourceKind())) {
            message = FiguraText.of("gui.library.file_error");
            return;
        }
        if (outfitSource) {
            selectOutfit(path);
            return;
        }
        long requestGeneration = ++generation;
        long sourceGeneration = draft.beginSource(path);
        boolean accessory = accessorySource;
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
                    java.util.function.BooleanSupplier cancelled =
                            () -> closed || generation != requestGeneration || !client.current();
                    java.util.function.Consumer<LibraryPackagePreparation.Phase> progress = phase -> {
                        if (!closed && generation == requestGeneration) {
                            preparationPhase = phase;
                        }
                    };
                    return accessory
                            ? LibraryPackagePreparation.prepareAccessory(path, client.session().player(),
                                    limits.maxUploadBytes(), cancelled, progress)
                            : LibraryPackagePreparation.prepare(path, client.session().player(),
                                    limits.maxUploadBytes(), cancelled, progress);
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
                if (accessory) {
                    var metadata = CosmeticMetadata.read(value.accessoryMetadata().toString());
                    cosmeticCategory = metadata.category();
                    cosmeticAnchor = metadata.cosmeticAnchor();
                    var transform = metadata.defaultTransform();
                    cosmeticTransform[0] = vectorText(transform.position());
                    cosmeticTransform[1] = vectorText(transform.rotation());
                    cosmeticTransform[2] = vectorText(transform.scale());
                }
                previewDraft().thenAccept(ready -> {
                    if (ready && !closed && requestGeneration == generation) {
                        draft.defaultCategory(accessory || value.metadata().modelType().equals("FIGURA") && preview.showsVanillaPlayer());
                        category = Math.max(0, limits.categories().indexOf(draft.category()));
                    }
                });
                message = FiguraText.of("gui.library.ready_upload", LibraryLabels.fileSize(value.byteLength()),
                        LibraryLabels.fileSize(limits.maxUploadBytes()));
                rebuildWidgets();
            }
            updateButtons();
        });
    }

    private void selectOutfit(Path path) {
        long ticket = ++generation;
        previewGeneration++;
        long draftGeneration = draft.beginSource(path);
        if (sourceRequest != null) {
            sourceRequest.cancel();
        }
        if (outfitPreparation != null) {
            outfitPreparation.close();
        }
        if (operation != null) {
            operation.close();
            operation = null;
        }
        source = path;
        prepared = null;
        outfitPrepared = null;
        outfitResources = null;
        outfitReady = false;
        outfitDistributable = false;
        preview.clear();
        message = FiguraText.of("gui.library.phase.reading");
        updateSourceFields();
        var request = client.submit(LibraryClient.Lane.TRANSFER,
                exchange -> OutfitUploadDraft.prepare(path, limits.outfitsV1().limits()));
        sourceRequest = request;
        request.future().whenComplete((value, failure) -> {
            if (closed || ticket != generation || !client.current()) {
                return;
            }
            if (failure != null) {
                message = FiguraText.of("gui.library.preparation_error");
            } else {
                outfitPrepared = value;
                var configuration = value.configuration();
                var references = new ArrayList<OutfitConfig.ResourceRef>();
                if (configuration.base() != null) {
                    references.add(configuration.base());
                }
                configuration.instances().forEach(instance -> references.add(instance.resource()));
                outfitDistributable = references.stream().allMatch(reference -> reference.source() == OutfitConfig.Source.CLOUD
                        && client.origin().equals(reference.origin()));
                draft.metadata(draftGeneration, new top.aihmc.omnimorph.model.library.LibraryMetadataReader.Metadata(
                        "", value.name(), List.of(), "", true));
                previewDraft();
                rebuildWidgets();
            }
            updateButtons();
        });
    }

    private java.util.concurrent.CompletableFuture<Boolean> previewOutfit() {
        long ticket = ++previewGeneration;
        long sourceTicket = generation;
        outfitReady = false;
        outfitResources = null;
        if (outfitPreparation != null) {
            outfitPreparation.close();
        }
        preview.clear();
        preview.setIndependentFigura(true);
        var result = new java.util.concurrent.CompletableFuture<Boolean>();
        draftPreview = result;
        java.util.function.BooleanSupplier active = () -> !closed && sourceTicket == generation
                && ticket == previewGeneration && client.current();
        var configuration = outfitPrepared.configuration();
        var budget = limits.outfitsV1().limits();
        outfitPreparation = new OutfitPresetResources(configuration, budget, client.session().player(),
                FiguraMod.getFiguraDirectory(), client, new LibraryTransferService(client, FiguraMod.getCacheDirectory()),
                minecraft::execute, active);
        outfitPreparation.future().whenComplete((resources, failure) -> {
            if (!active.getAsBoolean()) {
                result.complete(false);
                return;
            }
            if (failure != null) {
                finishOutfitPreview(result, false);
                return;
            }
            outfitResources = resources;
            if (!resources.ready()) {
                finishOutfitPreview(result, false);
                return;
            }
            try {
                var base = resources.base();
                var loaded = base == null ? java.util.concurrent.CompletableFuture.completedFuture(preview.selectVanilla())
                        : preview.selectDraft(() -> base.copy());
                loaded.whenComplete((ready, problem) -> {
                    if (!active.getAsBoolean()) {
                        result.complete(false);
                        return;
                    }
                    if (problem != null || !Boolean.TRUE.equals(ready)) {
                        finishOutfitPreview(result, false);
                        return;
                    }
                    preview.prepareComposition(configuration, budget, client.session().player(), previewIdentity,
                            PermissionManager.get(client.session().player()), resources::read).whenComplete((complete, error) -> {
                        if (active.getAsBoolean()) {
                            finishOutfitPreview(result, error == null && Boolean.TRUE.equals(complete) && resources.ready());
                        } else {
                            result.complete(false);
                        }
                    });
                });
            } catch (Exception invalid) {
                finishOutfitPreview(result, false);
            }
        });
        return result;
    }

    private void finishOutfitPreview(java.util.concurrent.CompletableFuture<Boolean> result, boolean ready) {
        outfitReady = ready;
        message = FiguraText.of(!outfitDistributable ? "gui.library.outfit_dependency_mapping"
                : ready ? "gui.library.outfit_ready" : "gui.library.preparation_error");
        result.complete(ready);
        rebuildWidgets();
        updateButtons();
    }

    private static String vectorText(OutfitConfig.Vector value) {
        return value.x() + ", " + value.y() + ", " + value.z();
    }

    private JsonObject accessoryDeclaration() {
        JsonObject declaration = prepared.accessoryMetadata();
        declaration.addProperty("category", cosmeticCategory);
        declaration.addProperty("cosmetic_anchor", cosmeticAnchor);
        var transform = new JsonObject();
        String[] components = {"position", "rotation", "scale"};
        for (int index = 0; index < components.length; index++) {
            String[] values = cosmeticTransform[index].split(",", -1);
            if (values.length != 3) {
                throw new IllegalArgumentException("INVALID_COSMETIC_TRANSFORM");
            }
            var vector = new JsonArray();
            for (String value : values) {
                vector.add(Double.parseDouble(value.strip()));
            }
            transform.add(components[index], vector);
        }
        declaration.add("default_transform", transform);
        CosmeticMetadata.read(declaration.toString()).validateLimits(limits.outfitsV1() == null
                ? OutfitLimits.defaults() : limits.outfitsV1().limits());
        return declaration;
    }

    /** The same edited native distribution is mounted as an isolated accessory, never equipped as a base Avatar. */
    private java.util.concurrent.CompletableFuture<Boolean> previewDraft() {
        if (closed || !client.current() || prepared == null && outfitPrepared == null) {
            return java.util.concurrent.CompletableFuture.completedFuture(false);
        }
        if (outfitSource) {
            return previewOutfit();
        }
        if (!accessorySource) {
            draftPreview = preview.selectDraft(prepared::nbt);
            return draftPreview;
        }
        final JsonObject declaration;
        try {
            declaration = accessoryDeclaration();
        } catch (RuntimeException invalid) {
            message = FiguraText.of("gui.library.metadata_error");
            return java.util.concurrent.CompletableFuture.completedFuture(false);
        }
        long ticket = ++previewGeneration;
        long sourceTicket = generation;
        var distribution = prepared;
        var result = new java.util.concurrent.CompletableFuture<Boolean>();
        draftPreview = result;
        preview.setIndependentFigura(true);
        if (!preview.selectVanilla()) {
            result.complete(false);
            return result;
        }
        AvatarLoadExecutor.submit(() -> {
            try {
                return LibraryPackagePreparation.withAccessoryMetadata(distribution, declaration, limits.maxUploadBytes(),
                        () -> closed || sourceTicket != generation || ticket != previewGeneration || !client.current());
            } catch (Exception failure) {
                throw new CompletionException(failure);
            }
        }).whenComplete((edited, failure) -> minecraft.execute(() -> {
            if (closed || sourceTicket != generation || ticket != previewGeneration || !client.current()) {
                result.complete(false);
                return;
            }
            if (failure != null) {
                message = FiguraText.of("gui.library.preparation_error");
                result.complete(false);
                return;
            }
            try {
                var metadata = CosmeticMetadata.read(declaration.toString());
                var reference = new OutfitConfig.ResourceRef(OutfitConfig.Source.LOCAL, previewIdentity,
                        null, null, null, edited.sha256(), OutfitConfig.Format.valueOf(edited.metadata().modelType()));
                var instance = new OutfitConfig.Instance(previewIdentity, reference, 1, true, metadata.defaultAnchor(),
                        metadata.defaultTransform(), List.of(), java.util.Map.of(), java.util.Map.of());
                var configuration = new OutfitConfig(1, 0, null, List.of(instance), List.of(),
                        java.util.Map.of(reference.identity(), 1), false);
                OutfitLimits budget = limits.outfitsV1() == null ? OutfitLimits.defaults() : limits.outfitsV1().limits();
                preview.prepareComposition(configuration, budget, client.session().player(), previewIdentity,
                        PermissionManager.get(client.session().player()), (requested, cancelled) -> {
                            if (!reference.equals(requested)) {
                                throw new java.io.IOException("COSMETIC_REFERENCE_CHANGED");
                            }
                            return edited.nbt();
                        }).whenComplete((ready, problem) -> {
                    if (problem != null) {
                        message = FiguraText.of("gui.library.preparation_error");
                    }
                    result.complete(problem == null && Boolean.TRUE.equals(ready));
                });
            } catch (RuntimeException invalid) {
                message = FiguraText.of("gui.library.preparation_error");
                result.complete(false);
            }
        }));
        return result;
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
        category = Math.max(0, limits.categories().indexOf(draft.category()));
        name.getField().setValue(draft.displayName());
        description.setValue(draft.description());
        authorization.setValue(draft.requiresAuthorization());
        sourceButton.setMessage(source == null ? FiguraText.of("gui.library.choose_model")
                : Component.literal(source.getFileName().toString()));
        author.setMessage(prepared == null || prepared.metadata().authors().isEmpty()
                ? FiguraText.of("gui.library.unknown_author") : Component.literal(String.join(", ", prepared.metadata().authors())));
        author.setTooltip(author.getMessage());
        building = previous;
    }

    private void submit() {
        if (busy() || prepared == null && outfitPrepared == null || closed
                || outfitSource && !outfitReady
                || draftPreview != null && !draftPreview.isDone()
                || preview.status().stage() == LibraryPreviewSession.Stage.LOADING
                || imageRequest != null && !imageRequest.future().isDone()) {
            return;
        }
        try {
            if (operation != null && operation.future().isCompletedExceptionally()) {
                operation.retry();
            } else if (operation == null) {
                if (outfitSource) {
                    operation = uploads.uploadOutfit(OutfitUploadDraft.snapshot(outfitPrepared, draft.displayName(),
                            draft.description(), draft.category(), LibraryUploadDraft.normalizeTags(tags), draft.isPublic(),
                            draft.requiresAuthorization(), limits, client.origin()), image);
                } else {
                    if (accessorySource) {
                        draft.resourceKind("ACCESSORY", accessoryDeclaration());
                    }
                    operation = uploads.upload(draft.snapshot(limits), image);
                }
            } else {
                return;
            }
            var started = operation;
            started.future().whenComplete((result, failure) -> {
                if (!closed && operation == started) {
                    message = failure != null && started.outfitPublished() ? FiguraText.of("gui.library.outfit_cover_retry")
                            : failure == null ? FiguraText.of("gui.library.upload_success")
                            : FiguraText.of("gui.library.failure", failure instanceof LibraryClient.Failure typed
                            ? typed.code : "UPLOAD_FAILED");
                    if (failure == null) {
                        published.run();
                    }
                    updateButtons();
                }
            });
        } catch (LibraryUploadDraft.InvalidCount invalid) {
            message = FiguraText.of("gui.library.invalid_count",
                    FiguraText.of("gui.library." + invalid.field()), invalid.maximumEntries());
        } catch (LibraryUploadDraft.InvalidField invalid) {
            Component field = FiguraText.of("gui.library." + invalid.field());
            message = invalid.maximumBytes() == 0 ? FiguraText.of("gui.library.invalid_text", field)
                    : FiguraText.of("gui.library.invalid_field", field, invalid.maximumBytes());
        } catch (IllegalArgumentException invalid) {
            message = FiguraText.of("gui.library.metadata_error");
        }
        updateButtons();
    }

    private void cancelUpload() {
        boolean publishedOutfit = operation != null && operation.outfitPublished();
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
        outfitReady = false;
        if (outfitPreparation != null) {
            outfitPreparation.close();
        }
        preview.clear();
        if (sourceRequest != null) {
            sourceRequest.cancel();
        }
        if (imageRequest != null) {
            imageRequest.cancel();
        }
        message = FiguraText.of(publishedOutfit ? "gui.library.outfit_cancelled_after_publish" : "gui.library.cancelled");
        updateButtons();
    }

    private void updateButtons() {
        if (upload == null) {
            return;
        }
        boolean editable = !closed && client.current() && !busy() && operation == null;
        sectionTabs.setEnabled(!closed && client.current() && !busy());
        editableButtons.forEach(button -> button.setActive(editable));
        editableFields.forEach(field -> field.setEnabled(editable));
        visibility.setActive(editable);
        authorization.setActive(editable);
        description.setEnabled(editable);
        tagField.setEnabled(editable);
        upload.setActive(!closed && client.current() && !busy() && (prepared != null || outfitPrepared != null && outfitReady)
                && (!outfitSource || outfitDistributable)
                && (draftPreview == null || draftPreview.isDone())
                && preview.status().stage() != LibraryPreviewSession.Stage.LOADING
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
            if (outfitPreparation != null) {
                outfitPreparation.close();
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
