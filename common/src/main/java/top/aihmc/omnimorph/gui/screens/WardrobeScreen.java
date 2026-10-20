package top.aihmc.omnimorph.gui.screens;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.nbt.CompoundTag;
import top.aihmc.omnimorph.FiguraMod;
import top.aihmc.omnimorph.avatar.AvatarManager;
import top.aihmc.omnimorph.avatar.AvatarLoadExecutor;
import top.aihmc.omnimorph.avatar.local.LocalAvatarFetcher;
import top.aihmc.omnimorph.avatar.local.LocalAvatarLoader;
import top.aihmc.omnimorph.avatar.ysm.YsmAvatarLoader;
import top.aihmc.omnimorph.avatar.ysm.YsmModelData;
import top.aihmc.omnimorph.backend2.NetworkStuff;
import top.aihmc.omnimorph.backend2.HttpAPI;
import top.aihmc.omnimorph.backend2.library.LibraryClient;
import top.aihmc.omnimorph.backend2.library.LibraryEquipmentVisibility;
import top.aihmc.omnimorph.backend2.library.LibraryModels;
import top.aihmc.omnimorph.backend2.library.LibraryAuthorization;
import top.aihmc.omnimorph.config.Configs;
import top.aihmc.omnimorph.gui.FiguraToast;
import top.aihmc.omnimorph.gui.screens.library.ModelLibraryAppealsScreen;
import top.aihmc.omnimorph.gui.screens.library.ModelLibraryAuthorizationScreen;
import top.aihmc.omnimorph.gui.screens.library.ModelLibraryEditScreen;
import top.aihmc.omnimorph.gui.widgets.AvatarInfoWidget;
import top.aihmc.omnimorph.gui.widgets.BackendMotdWidget;
import top.aihmc.omnimorph.gui.widgets.Button;
import top.aihmc.omnimorph.gui.widgets.EntityPreview;
import top.aihmc.omnimorph.gui.widgets.Label;
import top.aihmc.omnimorph.gui.widgets.SwitchButton;
import top.aihmc.omnimorph.gui.widgets.library.LibraryActionBar;
import top.aihmc.omnimorph.gui.widgets.library.LibraryDropdown;
import top.aihmc.omnimorph.gui.widgets.library.LibraryIconButton;
import top.aihmc.omnimorph.gui.widgets.library.LibraryWorkspaceLayout;
import top.aihmc.omnimorph.gui.widgets.lists.AvatarList;
import top.aihmc.omnimorph.gui.widgets.wardrobe.VerticalWardrobeTab;
import top.aihmc.omnimorph.gui.widgets.wardrobe.BoneTreeWidget;
import top.aihmc.omnimorph.gui.widgets.wardrobe.WardrobeCloudList;
import top.aihmc.omnimorph.gui.widgets.wardrobe.WardrobeLayout;
import top.aihmc.omnimorph.gui.widgets.wardrobe.WardrobeCollectionGrid;
import top.aihmc.omnimorph.model.library.LibraryThumbnailService;
import top.aihmc.omnimorph.model.library.LibraryModelEdits;
import top.aihmc.omnimorph.model.library.PreviewHierarchy;
import top.aihmc.omnimorph.utils.FiguraIdentifier;
import top.aihmc.omnimorph.model.library.LibraryCollectionService;
import top.aihmc.omnimorph.model.library.BoneTreeState;
import top.aihmc.omnimorph.model.library.LibraryEquipmentService;
import top.aihmc.omnimorph.model.library.LibraryPreviewSession;
import top.aihmc.omnimorph.model.library.LibraryTransferService;
import top.aihmc.omnimorph.model.library.LocalOutfitCandidates;
import top.aihmc.omnimorph.utils.FiguraText;
import top.aihmc.omnimorph.utils.IOUtils;
import top.aihmc.omnimorph.utils.TextUtils;
import top.aihmc.omnimorph.utils.ui.UIHelper;

import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Collection membership, outfit candidates, preview selection and equipment are independent states. */
public class WardrobeScreen extends AbstractPanelScreen {
    private enum Tab { OUTFIT, COLLECTION }
    private enum Source { LOCAL, CLOUD }
    private enum Pane { RESOURCES, PREVIEW, TREE, INFO }

    private record Selection(Path path, Path reference, boolean ysm, UUID cloud, Component name) {}
    private record Context(Tab tab, Source source) {}

    private Tab tab = Tab.OUTFIT;
    private Source source = Source.LOCAL;
    private Pane pane = Pane.RESOURCES;
    private boolean bonesShown;
    private boolean parts;
    private final Map<Context, AvatarList.View> localViews = new HashMap<>();
    private final Map<Context, WardrobeCloudList.View> cloudViews = new HashMap<>();
    private final Map<Context, WardrobeCollectionGrid.View> cardViews = new HashMap<>();
    private final Map<Tab, EntityPreview.View> previewViews = new HashMap<>();
    private final Map<Context, Selection> selections = new HashMap<>();
    private final Map<Tab, Source> tabSources = new HashMap<>();
    private final Map<Tab, Boolean> tabModes = new HashMap<>();
    private Context localContext;
    private LocalOutfitCandidates localCandidates;
    private Selection selected;
    private LibraryPreviewSession preview;
    private LibraryClient previewClient;
    private EntityPreview previewWidget;
    private Tab previewTab;
    private WardrobeCollectionGrid collectionGrid;
    private LibraryThumbnailService thumbnails;
    private LibraryClient client;
    private LibraryCollectionService collection;
    private LibraryTransferService transfers;
    private LibraryEquipmentService equipment;
    private LibraryEquipmentService.Operation enabling;
    private LibraryTransferService.Operation selectionCaching;
    private UUID cachingModel;
    private LibraryEquipmentVisibility visibility;
    private LibraryEquipmentVisibility.State libraryVisibility;
    private LibraryModels.WardrobeState localVisibility;
    private LibraryClient.Request<?> visibilityRequest;
    private volatile long selectionGeneration;
    private long visibilityGeneration;
    private volatile boolean removed;
    private boolean wasUploading;
    private List<LibraryModels.CollectionEntry> shownEntries = List.of();
    private List<LibraryModels.CollectionEntry> offlineEntries = List.of();
    private String offlineScope = "";
    private UUID cloudAccount;
    private top.aihmc.omnimorph.avatar.Avatar observedAvatar;
    private CompoundTag observedNbt;
    private boolean observedReady;
    private boolean liveHierarchy;
    private final BoneTreeState bones = new BoneTreeState();
    private LibraryPreviewSession boneSession;
    private long boneRevision = -1;
    private WardrobeLayout layout;
    private AvatarList localList;
    private WardrobeCloudList cloudList;
    private AvatarInfoWidget infoWidget;
    private BackendMotdWidget motdWidget;
    private Component observedMotd;
    private Button candidateButton;
    private Button uploadButton;
    private Button deleteButton;
    private SwitchButton showModel;
    private Button cacheSelectedButton;
    private Button removeCollectionButton;
    private Button editCollectionButton;
    private Button collectionGrantsButton;
    private Button collectionAuthorizationButton;
    private Button collectionPublicationButton;
    private UUID deleteCollectionArmed;
    private LibraryClient.Request<?> collectionMutation;
    private java.util.concurrent.CompletableFuture<?> collectionChange;
    private Component message = Component.empty();

    public WardrobeScreen(Screen parentScreen) {
        super(parentScreen, FiguraText.of("gui.panels.title.wardrobe"));
    }

    private Context context() {
        return new Context(tab, source);
    }

    private void saveView() {
        if (previewWidget != null) {
            previewViews.put(previewTab, previewWidget.view());
        }
        if (localList != null) {
            localViews.put(localContext, localList.view());
        }
    }

    private void rebuild() {
        saveView();
        rebuildWidgets();
    }

    @Override
    protected void init() {
        boolean returning = removed;
        removed = false;
        super.init();
        localList = null;
        cloudList = null;
        infoWidget = null;
        motdWidget = null;
        candidateButton = null;
        showModel = null;
        cacheSelectedButton = null;
        removeCollectionButton = null;
        editCollectionButton = null;
        collectionGrantsButton = null;
        collectionAuthorizationButton = null;
        collectionPublicationButton = null;
        uploadButton = null;
        deleteButton = null;
        previewWidget = null;
        if (localCandidates == null) {
            localCandidates = LocalOutfitCandidates.forDirectory(FiguraMod.getFiguraDirectory());
        }
        connect();
        if (returning && collection != null && client != null) {
            collection.refresh();
        }
        if (preview == null || preview.status().stage() == LibraryPreviewSession.Stage.CLOSED) {
            preview = new LibraryPreviewSession();
            previewClient = null;
            if (selected != null) {
                previewSelection();
            }
        }
        layout = tab == Tab.COLLECTION ? WardrobeLayout.collectionOf(width, height) : WardrobeLayout.of(width, height);
        addRenderableWidget(new VerticalWardrobeTab(4, 28, 66, text("outfit"), button -> switchTab(Tab.OUTFIT)));
        addRenderableWidget(new VerticalWardrobeTab(4, 98, 100, text("collection"), button -> switchTab(Tab.COLLECTION)));
        if (layout.compact()) {
            var panes = tab == Tab.COLLECTION ? List.of(Pane.RESOURCES, Pane.PREVIEW) : List.of(Pane.values());
            int sectionWidth = Math.max(1, (width - 36) / panes.size());
            for (Pane target : panes) {
                addRenderableWidget(new Button(30 + target.ordinal() * sectionWidth, 28, sectionWidth - 2, 20,
                        text(target == Pane.INFO ? "default_panel" : target.name().toLowerCase(java.util.Locale.ROOT)), null, button -> {
                    pane = target;
                    if (target == Pane.TREE || target == Pane.INFO) {
                        bonesShown = target == Pane.TREE;
                    }
                    rebuild();
                }));
            }
        } else {
            addRenderableOnly((gui, x, y, delta) -> gui.text(font,
                    tab == Tab.OUTFIT ? text("outfit") : text("collection"), 32, 32, 0xffffffff));
            if (tab == Tab.OUTFIT) {
                int half = layout.treeWidth() / 2;
                addRenderableWidget(new Button(layout.treeX(), 28, half - 2, 20, text("default_panel"), null,
                        button -> switchRightPane(false))).setActive(bonesShown);
                addRenderableWidget(new Button(layout.treeX() + half, 28, layout.treeWidth() - half, 20,
                        text("tree"), null, button -> switchRightPane(true))).setActive(!bonesShown);
            }
        }
        if (!layout.compact() || pane == Pane.RESOURCES) {
            buildResources();
        }
        if (!layout.compact() || pane == Pane.PREVIEW) {
            buildPreview();
        }
        if (tab == Tab.OUTFIT && (!layout.compact() || pane == Pane.TREE || pane == Pane.INFO)) {
            if (!bonesShown) {
                buildInformation();
            } else {
                buildRightPane();
            }
        }
        buildToolbar();
        refreshVisibility();
    }

    private static Component text(String key) {
        return FiguraText.of("gui.wardrobe." + key);
    }

    private void switchRightPane(boolean bones) {
        bonesShown = bones;
        pane = bones ? Pane.TREE : Pane.INFO;
        rebuild();
    }

    private void connect() {
        try {
            LibraryClient next = NetworkStuff.libraryClient();
            if (client != next) {
                if (equipment != null) {
                    equipment.close();
                }
                closeThumbnails();
                client = next;
                cloudAccount = client.session().player();
                collection = NetworkStuff.wardrobeCollection();
                transfers = new LibraryTransferService(client, FiguraMod.getCacheDirectory());
                equipment = new LibraryEquipmentService(client, transfers);
                visibility = new LibraryEquipmentVisibility(client);
                collection.refresh();
            }
            if (thumbnails == null) {
                thumbnails = new LibraryThumbnailService(client, FiguraMod.getCacheDirectory());
            }
            if (collectionGrid != null) {
                collectionGrid.thumbnails(thumbnails);
            }
            if (cloudList != null) {
                cloudList.thumbnails(thumbnails);
            }
        } catch (java.io.IOException offline) {
            client = null;
            loadOfflineMetadata();
        }
    }

    private void loadOfflineMetadata() {
        String origin;
        try {
            origin = HttpAPI.endpoint().origin().toString();
        } catch (IllegalArgumentException invalidEndpoint) {
            offlineEntries = List.of();
            return;
        }
        UUID viewer = FiguraMod.getLocalPlayerUUID();
        String scope = origin + "\n" + viewer;
        if (scope.equals(offlineScope)) {
            return;
        }
        offlineScope = scope;
        offlineEntries = List.of();
        AvatarLoadExecutor.submit(() -> LibraryCollectionService.offlineMetadata(
                FiguraMod.getCacheDirectory(), origin, viewer)).thenAccept(values -> minecraft.execute(() -> {
            if (!removed && client == null && scope.equals(offlineScope)
                    && viewer.equals(FiguraMod.getLocalPlayerUUID())) {
                offlineEntries = values;
                refreshCloudRows();
            }
        }));
    }

    private void switchTab(Tab next) {
        saveView();
        if (next == tab) {
            return;
        }
        if (preview != null) {
            preview.clear();
        }
        tabSources.put(tab, source);
        tabModes.put(tab, parts);
        tab = next;
        source = tabSources.getOrDefault(next, Source.LOCAL);
        parts = tabModes.getOrDefault(next, false);
        selected = selections.get(context());
        if (tab == Tab.COLLECTION && (pane == Pane.TREE || pane == Pane.INFO)) {
            pane = Pane.PREVIEW;
        }
        if (selected != null) {
            previewSelection();
        }
        syncHierarchy();
        rebuildWidgets();
    }

    private void buildResources() {
        int x = layout.leftX();
        int w = layout.leftWidth();
        int selectorWidth = Math.min(100, (w - 4) / 2);
        addRenderableWidget(new LibraryDropdown(x, 54, selectorWidth,
                () -> List.of(text("local"), text("cloud")), () -> source == Source.LOCAL ? 0 : 1, index -> {
            saveView();
            source = index == 0 ? Source.LOCAL : Source.CLOUD;
            if (source == Source.CLOUD && collection != null) {
                collection.refresh();
            }
            rebuildWidgets();
        }));
        addRenderableWidget(new LibraryDropdown(x + selectorWidth + 4, 54, selectorWidth,
                () -> List.of(text("avatars"), text("parts")), () -> parts ? 1 : 0, index -> {
            parts = index == 1;
            rebuild();
        }));
        if (parts) {
            addRenderableWidget(new Label(text("coming_2"), x + w / 2, 84, TextUtils.Alignment.CENTER));
            int row = 104;
            for (String feature : List.of("combination", "attachment", "anchor")) {
                addRenderableWidget(new Button(x, row, w, 20, text(feature), text("coming_2"), button -> {}))
                        .setActive(false);
                row += 24;
            }
            return;
        }
        int listHeight = Math.max(88, height - 112);
        if (tab == Tab.COLLECTION) {
            var view = cardViews.computeIfAbsent(context(), ignored -> new WardrobeCollectionGrid.View());
            collectionGrid = addRenderableWidget(new WardrobeCollectionGrid(x, 78, w,
                    source == Source.CLOUD ? listHeight - 14 : listHeight, view,
                    client == null ? null : thumbnails, entry -> {
                if (entry.local() != null) {
                    selectLocal(entry.local());
                } else {
                    selectCloud(entry.cloud());
                }
            }, id -> client == null ? FiguraText.of("gui.library.state.offline") : text(collection.cacheState(id))));
            refreshCollectionCards();
            if (source == Source.CLOUD) {
                addRenderableOnly((gui, mouseX, mouseY, delta) -> UIHelper.renderScrollingText(gui,
                        client == null ? FiguraText.of("gui.library.state.offline")
                                : collection.failure().isEmpty() ? text("cloud")
                                : FiguraText.of("gui.library.failure", collection.failure()),
                        x + 2, height - 46, w - 4, 0xffffdd88));
            }
        } else if (source == Source.LOCAL) {
            localList = new AvatarList(x, 78, w, listHeight, this, this::selectLocal,
                    avatar -> tab == Tab.COLLECTION || localCandidates.contains(avatar.getTheActualPathForThis()));
            localList.showSourceBadge(true);
            localList.setActionsVisible(false);
            var saved = selections.get(context());
            localContext = context();
            localList.restore(localViews.get(context()), saved == null ? null : saved.reference());
            addRenderableWidget(localList);
        } else {
            var view = cloudViews.computeIfAbsent(context(), ignored -> new WardrobeCloudList.View());
            cloudList = addRenderableWidget(new WardrobeCloudList(x, 78, w, Math.max(72, listHeight - 14), view,
                    this::selectCloud, id -> client == null ? FiguraText.of("gui.library.state.offline")
                    : text(collection.cacheState(id))));
            cloudList.thumbnails(client == null ? null : thumbnails);
            refreshCloudRows();
            addRenderableOnly((gui, mouseX, mouseY, delta) -> UIHelper.renderScrollingText(gui,
                    client == null ? FiguraText.of("gui.library.state.offline")
                            : collection.failure().isEmpty() ? text("cloud")
                            : FiguraText.of("gui.library.failure", collection.failure()),
                    x + 2, height - 46, w - 4, 0xffffdd88));
        }
    }

    private void buildPreview() {
        int x = layout.previewX();
        int w = layout.previewWidth();
        int actionCount = tab == Tab.OUTFIT ? 12 : source == Source.CLOUD ? 7 : 3;
        int actionsHeight = LibraryActionBar.heightFor(w, actionCount);
        int actionsY = height - 28 - actionsHeight;
        int size = LibraryWorkspaceLayout.previewHeightBeforeActions(layout.top(), actionsY);
        float scale = tab == Tab.COLLECTION ? Math.max(12f, w / 3f) : Math.min(w, size) * 0.38f;
        previewWidget = new EntityPreview(x, layout.top(), w, size, scale, -15, 30,
                Minecraft.getInstance().player, this);
        previewTab = tab;
        if (selectionPreview()) {
            previewWidget.setLibraryPreview(preview);
        }
        previewWidget.restoreView(previewViews.get(tab));
        previewWidget.setSelectedBone(tab == Tab.OUTFIT ? bones.selectedPart() : null);
        addRenderableWidget(previewWidget);
        addRenderableOnly((gui, mouseX, mouseY, delta) -> {
            Component name;
            if (tab == Tab.OUTFIT && selected == null) {
                name = text("current_equipped");
            } else if (selected == null) {
                name = FiguraText.of("gui.library.select_model");
            } else {
                name = text("previewing").copy().append(selected.name()).append(" · ")
                        .append(text(selected.cloud() == null ? "local" : "cloud"));
            }
            Component state = selectionPreview() && preview.status().stage() == LibraryPreviewSession.Stage.FAILED
                    ? FiguraText.of("gui.library.failure", preview.status().errorCode()) : message;
            Component footer = !state.getString().isEmpty() ? state : !bones.selectedName().isEmpty()
                    ? text("selected_bone").copy().append(bones.selectedName()) : name;
            UIHelper.renderScrollingText(gui, footer, x + 4, actionsY - 12, w - 8, 0xffd3e7e1);
            if (AvatarManager.panic) {
                gui.text(font, FiguraText.of("gui.panic", Configs.PANIC_BUTTON.keyBind.getTranslatedKeyMessage()),
                        x + 4, layout.top() + 4, 0xffffff55);
            }
        });
        var actions = addRenderableWidget(new LibraryActionBar(x, actionsY, w, actionsHeight));
        candidateButton = actions.action(new LibraryIconButton("wardrobe_outfit", 2,
                () -> candidate() ? 1 : 0, text("add_outfit"), null, button -> {
            if (selected == null && tab == Tab.OUTFIT) {
                switchTab(Tab.COLLECTION);
            } else {
                changeCandidate();
            }
        }));
        if (tab == Tab.COLLECTION && source == Source.CLOUD) {
            buildCloudCollectionActions(actions);
        }
        if (source == Source.LOCAL) {
            actions.action(icon(0, 0, "new_avatar", "avatars",
                    button -> minecraft.setScreen(new AvatarWizardScreen(this))));
            actions.action(icon(0, 0, "folder", "local", button ->
                    net.minecraft.util.Util.getPlatform().openUri(LocalAvatarFetcher.getLocalAvatarDirectory().toUri())));
        }
        if (tab == Tab.OUTFIT) {
            uploadButton = actions.action(icon(0, 0, "upload", "publish_current", button -> publishCurrent()));
            actions.action(icon(0, 0, "reload", "reload_current", button -> AvatarManager.reloadCloudAvatar()));
            showModel = actions.action(new SwitchButton(0, 0, 24, 24, 0, 0, 24,
                    new FiguraIdentifier("textures/gui/wardrobe_visibility.png"), 72, 48, text("show_others"),
                    button -> changeVisibility()));
            actions.action(icon(0, 0, "wardrobe_appeal", "info", button -> openAppeals()))
                    .setTooltip(FiguraText.of("gui.library.my_appeals"));
            deleteButton = actions.action(icon(0, 0, "delete", "delete_current", button -> {
                if (NetworkStuff.isConnected() && AvatarManager.localUploaded && NetworkStuff.getUploadProgress() == null) {
                    NetworkStuff.deleteAvatar(null);
                }
            }));
            actions.action(icon(0, 0, "sound", "sound", button -> minecraft.setScreen(new SoundScreen(this))));
            actions.action(icon(0, 0, "keybind", "keybinds", button -> minecraft.setScreen(new KeybindScreen(this))));
            actions.action(icon(0, 0, "unselect", "clear_current", button -> {
                AvatarManager.loadLocalAvatar(null);
                AvatarList.selectedEntry = null;
            }));
            actions.action(icon(0, 0, "reload", "refresh_bones", button -> {
                if (selectionPreview()) {
                    preview.refreshHierarchy();
                } else {
                    liveHierarchy = false;
                }
                syncHierarchy();
            }));
        }
    }

    private LibraryModels.Model selectedCollectionModel() {
        if (selected == null || selected.cloud() == null || collection == null) {
            return null;
        }
        return collection.entries().stream().filter(entry -> entry.modelId().equals(selected.cloud()))
                .map(LibraryModels.CollectionEntry::model).filter(java.util.Objects::nonNull).findFirst().orElse(null);
    }

    private boolean ownsCollectionModel(LibraryModels.Model model) {
        return model != null && client != null && model.uploader().uuid().equals(client.session().player());
    }

    private boolean collectionBusy() {
        return collectionMutation != null && !collectionMutation.future().isDone()
                || collectionChange != null && !collectionChange.isDone()
                || selected != null && selected.cloud() != null && collection != null && collection.busy(selected.cloud());
    }

    private void buildCloudCollectionActions(LibraryActionBar actions) {
        cacheSelectedButton = actions.action(new LibraryIconButton("wardrobe_cache", 2,
                () -> selected != null && selected.cloud() != null && collection != null
                        && collection.cacheState(selected.cloud()).equals("cached") ? 1 : 0,
                text("cache_selected"), null, button -> {
            var model = selectedCollectionModel();
            if (model != null && model.allows("download") && !collectionBusy()) {
                cachingModel = model.modelId();
                selectionCaching = collection.add(model.modelId());
                selectionCaching.request().future().whenComplete((value, failure) -> {
                    if (!removed && failure != null) {
                        error(failure);
                    }
                });
            }
        }));
        removeCollectionButton = actions.action(new LibraryIconButton("library_delete", 1, () -> 0,
                text("remove_collection"), null, button -> removeSelectedCollection()));
        editCollectionButton = actions.action(new LibraryIconButton("library_edit", 1, () -> 0,
                FiguraText.of("gui.library.edit"), null, button -> {
            var model = selectedCollectionModel();
            if (client == null || !LibraryModelEdits.available(model, client.session().player()) || collectionBusy()) {
                return;
            }
            var expectedClient = client;
            long generation = selectionGeneration;
            var request = client.capabilities();
            collectionMutation = request;
            request.future().whenComplete((capabilities, failure) -> {
                if (!removed && client == expectedClient && expectedClient.current() && generation == selectionGeneration) {
                    if (failure != null) {
                        error(failure);
                    } else if (capabilities.enabled()) {
                        preview.clear();
                        minecraft.setScreen(new ModelLibraryEditScreen(this, client, capabilities, model.modelId()));
                    }
                }
            });
        }));
        collectionGrantsButton = actions.action(new LibraryIconButton("library_grants", 1, () -> 0,
                FiguraText.of("gui.library.grants"), null, button -> {
            var model = selectedCollectionModel();
            if (LibraryAuthorization.available(model) && !collectionBusy()) {
                preview.clear();
                minecraft.setScreen(new ModelLibraryAuthorizationScreen(this, client, model.modelId(), () -> ""));
            }
        }));
        collectionAuthorizationButton = actions.action(new LibraryIconButton("library_authorization", 2,
                () -> selectedCollectionModel() != null && selectedCollectionModel().requiresAuthorization() ? 1 : 0,
                FiguraText.of("gui.library.authorization"), null, button -> {
            var model = selectedCollectionModel();
            if (model != null && model.allows("patch")) {
                mutateCollectionModel(model, "PATCH", Map.of("operation_id", UUID.randomUUID().toString(),
                        "expected_revision", model.policyRevision(), "requires_authorization", !model.requiresAuthorization()), false);
            }
        }));
        collectionPublicationButton = actions.action(new LibraryIconButton("library_visibility", 2,
                () -> selectedCollectionModel() != null && selectedCollectionModel().visibility().equals("PUBLIC") ? 1 : 0,
                FiguraText.of("gui.library.visibility"), null, button -> {
            var model = selectedCollectionModel();
            if (model != null && model.allows("patch")) {
                mutateCollectionModel(model, "PATCH", Map.of("operation_id", UUID.randomUUID().toString(),
                        "expected_revision", model.policyRevision(),
                        "visibility", model.visibility().equals("PUBLIC") ? "PRIVATE" : "PUBLIC"), false);
            }
        }));
        updateCollectionButtons();
    }

    private void removeSelectedCollection() {
        if (selected == null || selected.cloud() == null || client == null || !client.current()
                || collection == null || !collection.available() || collectionBusy()) {
            return;
        }
        UUID id = selected.cloud();
        var model = selectedCollectionModel();
        if (ownsCollectionModel(model)) {
            if (!model.allows("delete")) {
                return;
            }
            if (!id.equals(deleteCollectionArmed)) {
                deleteCollectionArmed = id;
                updateCollectionButtons();
                return;
            }
            mutateCollectionModel(model, "DELETE", Map.of("operation_id", UUID.randomUUID().toString(),
                    "expected_revision", model.policyRevision()), true);
        } else {
            var change = collection.remove(id);
            collectionChange = change;
            change.whenComplete((value, failure) -> finishCollectionChange(id, failure, true));
        }
    }

    private void mutateCollectionModel(LibraryModels.Model model, String method, Map<String, Object> input,
                                       boolean removeMember) {
        if (client == null || !client.current() || collectionBusy()) {
            return;
        }
        var request = client.submit(LibraryClient.Lane.CONTROL, exchange -> exchange.json(method,
                "/api/library/v1/models/" + model.modelId(), input, Map.class));
        collectionMutation = request;
        var targetCollection = collection;
        request.future().whenComplete((value, failure) -> {
            if (failure != null) {
                finishCollectionChange(model.modelId(), failure, false);
            } else {
                var refresh = removeMember ? targetCollection.remove(model.modelId()) : targetCollection.refresh();
                collectionChange = refresh;
                refresh.whenComplete((entries, refreshFailure) -> finishCollectionChange(model.modelId(), refreshFailure, removeMember));
            }
        });
    }

    private void finishCollectionChange(UUID id, Throwable failure, boolean clearSelection) {
        if (removed) {
            return;
        }
        if (failure != null) {
            error(failure);
        } else if (clearSelection && selected != null && id.equals(selected.cloud())) {
            selected = null;
            selections.remove(context());
            deleteCollectionArmed = null;
            previewSelection();
            refreshCollectionCards();
        }
        updateCollectionButtons();
    }

    private void updateCollectionButtons() {
        if (cacheSelectedButton == null) {
            return;
        }
        var model = selectedCollectionModel();
        boolean available = !removed && client != null && client.current() && collection != null
                && collection.available() && !collectionBusy();
        cacheSelectedButton.setActive(available && model != null && model.allows("download"));
        if (selected != null && selected.cloud() != null && collection != null) {
            cacheSelectedButton.setMessage(text("cache_selected").copy().append(" · ").append(text(collection.cacheState(selected.cloud()))));
        }
        boolean owner = ownsCollectionModel(model);
        removeCollectionButton.setActive(available && selected != null && selected.cloud() != null
                && (!owner || model.allows("delete")));
        removeCollectionButton.setMessage(owner ? collectionRemovalLabel(model.modelId())
                : text("remove_collection"));
        editCollectionButton.setVisible(client != null && LibraryModelEdits.available(model, client.session().player()));
        editCollectionButton.setActive(available && editCollectionButton.isVisible());
        collectionGrantsButton.setVisible(LibraryAuthorization.available(model));
        collectionGrantsButton.setActive(available && collectionGrantsButton.isVisible());
        boolean patch = owner && model.allows("patch");
        collectionAuthorizationButton.setVisible(patch);
        collectionAuthorizationButton.setActive(available && patch);
        collectionAuthorizationButton.setMessage(FiguraText.of(model != null && model.requiresAuthorization()
                ? "gui.library.needs_authorization" : "gui.library.value.free"));
        collectionPublicationButton.setVisible(patch);
        collectionPublicationButton.setActive(available && patch);
        collectionPublicationButton.setMessage(FiguraText.of(model != null && model.visibility().equals("PUBLIC")
                ? "gui.library.make_private" : "gui.library.make_public"));
    }

    private Component collectionRemovalLabel(UUID id) {
        return id.equals(deleteCollectionArmed) ? FiguraText.of("gui.library.delete_confirm") : text("delete_submission");
    }

    private void buildRightPane() {
        int x = layout.treeX();
        int w = layout.treeWidth();
        syncHierarchy();
        addRenderableWidget(new BoneTreeWidget(x, layout.top(), w, Math.max(64, height - 140), bones));
        addRenderableOnly((gui, mouseX, mouseY, delta) -> UIHelper.renderScrollingText(gui,
                text("bones_readonly"), x + 4, height - 52, w - 8, 0xffaaaaaa));
    }

    private boolean selectionPreview() {
        return tab == Tab.COLLECTION || selected != null;
    }

    private void syncHierarchy() {
        if (!selectionPreview()) {
            var current = AvatarManager.getAvatarForPlayer(FiguraMod.getLocalPlayerUUID());
            CompoundTag nbt = current == null ? null : current.nbt;
            boolean ready = current != null && current.loaded;
            if (!liveHierarchy || current != observedAvatar || nbt != observedNbt || ready != observedReady) {
                observedAvatar = current;
                observedNbt = nbt;
                observedReady = ready;
                liveHierarchy = true;
                bones.replace(ready ? PreviewHierarchy.fromAvatar(current) : PreviewHierarchy.EMPTY);
            }
            return;
        }
        if (liveHierarchy) {
            liveHierarchy = false;
            boneSession = null;
        }
        if (preview != boneSession || preview.hierarchyRevision() != boneRevision) {
            boneSession = preview;
            boneRevision = preview.hierarchyRevision();
            bones.replace(preview.hierarchy());
        }
    }

    private void buildInformation() {
        int x = layout.treeX();
        int w = layout.treeWidth();
        int infoHeight = Math.min(Math.max(108, layout.height() / 2), Math.max(40, layout.height() - 56));
        infoWidget = addRenderableOnly(new AvatarInfoWidget(x, layout.top(), w, infoHeight));
        updateMotdWidget();
    }

    private void updateMotdWidget() {
        if (infoWidget == null) {
            return;
        }
        int y = infoWidget.getY() + infoWidget.getHeight() + 4;
        int available = Math.max(0, layout.top() + layout.height() - y);
        if (NetworkStuff.motd == null) {
            if (motdWidget != null) {
                removeWidget(motdWidget);
                motdWidget = null;
            }
            observedMotd = null;
            return;
        }
        if (motdWidget == null && available >= 28) {
            motdWidget = addRenderableWidget(new BackendMotdWidget(layout.treeX(), y, layout.treeWidth(), available,
                    NetworkStuff.motd, font));
            motdWidget.setMessage(NetworkStuff.motd);
            observedMotd = NetworkStuff.motd;
        }
        if (motdWidget != null) {
            motdWidget.setY(y);
            motdWidget.setHeight(available);
            motdWidget.visible = available >= 28;
            if (NetworkStuff.motd != observedMotd) {
                motdWidget.setMessage(NetworkStuff.motd);
                observedMotd = NetworkStuff.motd;
            }
        }
    }

    private void buildToolbar() {
        addRenderableWidget(new Button(width - 90, height - 26, 84, 20, FiguraText.of("gui.done"), null,
                button -> onClose()));
    }

    private Button icon(int x, int y, String texture, String tooltip, Button.OnPress action) {
        return new Button(x, y, 24, 24, 0, 0, 24,
                new FiguraIdentifier("textures/gui/" + texture + ".png"), 72, 24, text(tooltip), action);
    }

    private void uploadCurrent() {
        var avatar = AvatarManager.getAvatarForPlayer(FiguraMod.getLocalPlayerUUID());
        if (avatar != null && avatar.loaded && avatar.nbt != null && NetworkStuff.canUpload() && !AvatarManager.localUploaded) {
            NetworkStuff.uploadAvatar(avatar);
        }
    }

    private void publishCurrent() {
        if (selected != null) {
            enableSelection();
        } else {
            uploadCurrent();
        }
    }

    private void openAppeals() {
        if (client != null && client.current()) {
            minecraft.setScreen(new ModelLibraryAppealsScreen(this, client, null));
        }
    }

    private void selectLocal(LocalAvatarFetcher.AvatarPath avatar) {
        deleteCollectionArmed = null;
        selected = avatar == null ? null : new Selection(avatar.getPath(), avatar.getTheActualPathForThis(), avatar.isYsm(), null,
                Component.literal(avatar.getName()));
        selections.put(context(), selected);
        previewSelection();
    }

    private void selectCloud(LibraryModels.CollectionEntry entry) {
        deleteCollectionArmed = null;
        if (client == null || collection == null || !collection.available()
                || entry.model() == null || !entry.model().allows("preview")) {
            message = text("unavailable");
            selected = new Selection(null, null, false, entry.modelId(), text("unavailable"));
            selections.put(context(), selected);
            preview.clear();
            syncHierarchy();
            refreshVisibility();
            return;
        }
        selected = new Selection(null, null, false, entry.modelId(), Component.literal(entry.model().displayName()));
        selections.put(context(), selected);
        previewSelection();
    }

    private void previewSelection() {
        long generation = ++selectionGeneration;
        message = Component.empty();
        LibraryClient requiredClient = selected != null && selected.cloud() != null ? client : null;
        bindPreview(requiredClient);
        if (previewWidget != null) {
            previewWidget.setLibraryPreview(selectionPreview() ? preview : null);
        }
        if (selected == null) {
            preview.clear();
        } else if (selected.cloud() != null) {
            if (client != null && client.current()) {
                UUID model = selected.cloud();
                boolean downloadable = collection.entries().stream().anyMatch(entry -> entry.modelId().equals(model)
                        && entry.model() != null && entry.model().allows("download"));
                if (downloadable) {
                    preview.clear();
                    message = text("downloading");
                    cachingModel = model;
                    selectionCaching = collection.add(model);
                    Tab requestedTab = tab;
                    selectionCaching.request().future().whenComplete((value, failure) -> {
                        if (!removed && generation == selectionGeneration && tab == requestedTab
                                && client == requiredClient && requiredClient.current()) {
                            message = Component.empty();
                            if (failure != null) {
                                error(failure);
                            }
                            // Fresh preview permission is still required; a valid formal cache avoids another body.
                            preview.select(model, transfers);
                        }
                    });
                } else {
                    preview.select(model, transfers);
                }
            } else {
                preview.clear();
                message = FiguraText.of("gui.library.state.offline");
            }
        } else {
            var selection = selected;
            UUID owner = FiguraMod.getLocalPlayerUUID();
            preview.selectDraft(() -> {
                try {
                    if (!selection.ysm()) {
                        return LocalAvatarLoader.prepareFiguraAvatar(selection.path(), owner);
                    }
                    try (var data = YsmModelData.read(selection.path(), () -> generation != selectionGeneration || removed)) {
                        var manifest = data.manifest();
                        var texture = manifest.textures().stream()
                                .filter(option -> option.id().equals(manifest.defaultTexture())).findFirst()
                                .orElse(manifest.textures().isEmpty() ? null : manifest.textures().getFirst());
                        return YsmAvatarLoader.toAvatarNbt(data, texture, owner, null);
                    }
                } catch (Exception failure) {
                    throw new java.util.concurrent.CompletionException(failure);
                }
            });
        }
        syncHierarchy();
        refreshVisibility();
    }

    private void bindPreview(LibraryClient requiredClient) {
        if (previewClient != requiredClient || preview.status().stage() == LibraryPreviewSession.Stage.CLOSED) {
            preview.close();
            preview = new LibraryPreviewSession(requiredClient);
            previewClient = requiredClient;
            if (previewWidget != null) {
                previewWidget.setLibraryPreview(preview);
            }
        }
    }

    private boolean candidate() {
        if (selected == null) {
            return false;
        }
        return selected.path() != null ? localCandidates.contains(selected.reference()) : client != null && collection != null
                && collection.available()
                && collection.entries().stream().anyMatch(entry -> entry.modelId().equals(selected.cloud()) && entry.inOutfit());
    }

    private void changeCandidate() {
        if (selected == null || parts) {
            return;
        }
        boolean included = candidate();
        if (selected.path() != null) {
            localCandidates.set(selected.reference(), !included).whenComplete((saved, failure) -> minecraft.execute(() -> {
                if (!removed && (failure != null || !Boolean.TRUE.equals(saved))) {
                    message = text("save_failed");
                }
            }));
            rebuild();
        } else if (client != null && client.current() && collection.available()) {
            client.collectionOutfit(selected.cloud(), !included).future().whenComplete((value, failure) -> {
                if (!removed) {
                    if (failure == null) {
                        collection.refresh();
                    } else {
                        error(failure);
                    }
                }
            });
        }
    }

    private void enableSelection() {
        if (selected == null || parts || tab != Tab.OUTFIT || !candidate()) {
            return;
        }
        if (selectionIsCaching()) {
            return;
        }
        if (selected.path() != null) {
            AvatarManager.loadLocalAvatar(selected.path());
            AvatarList.selectedEntry = selected.reference();
            message = text("enabling");
        } else if (equipment != null && client.current()) {
            boolean showing = libraryVisibility != null && selected.cloud().equals(libraryVisibility.model())
                    && libraryVisibility.showing();
            enabling = equipment.enable(selected.cloud(), showing);
            enabling.future().whenComplete((result, failure) -> {
                if (!removed) {
                    if (failure != null) {
                        error(failure);
                    } else {
                        message = FiguraText.of("gui.library.enabled");
                    }
                    refreshVisibility();
                }
            });
        }
    }

    private boolean selectionIsCaching() {
        return selected != null && selected.cloud() != null && selected.cloud().equals(cachingModel)
                && selectionCaching != null && !selectionCaching.request().future().isDone();
    }

    private void refreshCloudRows() {
        if (cloudList == null && (collectionGrid == null || source != Source.CLOUD)) {
            return;
        }
        shownEntries = client == null ? offlineEntries : collection.entries();
        if (collectionGrid != null) {
            refreshCollectionCards();
            return;
        }
        cloudList.entries(shownEntries.stream().filter(LibraryModels.CollectionEntry::inOutfit).toList(),
                selected == null ? null : selected.cloud());
    }

    private void refreshCollectionCards() {
        if (collectionGrid == null) {
            return;
        }
        var entries = new ArrayList<WardrobeCollectionGrid.Entry>();
        if (source == Source.LOCAL) {
            LocalAvatarFetcher.reloadAvatars();
            var pending = new ArrayDeque<>(List.copyOf(LocalAvatarFetcher.ALL_AVATARS));
            while (!pending.isEmpty()) {
                var entry = pending.removeFirst();
                if (entry instanceof LocalAvatarFetcher.FolderPath folder) {
                    pending.addAll(List.copyOf(folder.getChildren()));
                } else {
                    entries.add(new WardrobeCollectionGrid.Entry(entry, null));
                }
            }
            entries.sort(java.util.Comparator.comparing(entry -> entry.name().getString(), String.CASE_INSENSITIVE_ORDER));
        } else {
            shownEntries = client == null ? offlineEntries : collection.entries();
            for (var entry : shownEntries) {
                entries.add(new WardrobeCollectionGrid.Entry(null, entry));
            }
        }
        String selectedKey = selected == null ? null : selected.reference() != null
                ? selected.reference().toUri().toString() : selected.cloud().toString();
        collectionGrid.entries(entries, selectedKey);
    }

    private void refreshVisibility() {
        libraryVisibility = null;
        localVisibility = null;
        if (showModel == null) {
            return;
        }
        showModel.setToggled(false);
        showModel.setActive(false);
        if (visibilityRequest != null) {
            visibilityRequest.cancel();
        }
        long expected = ++visibilityGeneration;
        if (client == null || !client.current()) {
            return;
        }
        var request = client.sessionInfo();
        visibilityRequest = request;
        request.future().whenComplete((state, failure) -> {
            if (!removed && expected == visibilityGeneration && failure == null) {
                if (state.equipmentModelId() != null && state.equipmentModelRevision() != null) {
                    libraryVisibility = new LibraryEquipmentVisibility.State(state.equipmentModelId(),
                            state.equipmentModelRevision(), state.equipmentRevision(), true, state.showPrivateEquipment());
                    showModel.setToggled(state.showPrivateEquipment());
                } else {
                    readLocalVisibility(expected);
                }
            }
        });
    }

    private void readLocalVisibility(long expected) {
        var request = client.submit(LibraryClient.Lane.CONTROL, exchange -> exchange.json("GET",
                "/api/library/v1/wardrobe", null, LibraryModels.WardrobeState.class));
        visibilityRequest = request;
        request.future().whenComplete((state, failure) -> {
            if (!removed && expected == visibilityGeneration && failure == null) {
                localVisibility = state;
                showModel.setToggled(state.showing());
            }
        });
    }

    private void changeVisibility() {
        if (libraryVisibility != null) {
            if (!libraryVisibility.equipped()) {
                return;
            }
            visibilityRequest = visibility.save(libraryVisibility, showModel.isToggled());
        } else if (localVisibility != null && localVisibility.canChangeVisibility()) {
            var state = localVisibility;
            boolean showing = showModel.isToggled();
            visibilityRequest = client.submit(LibraryClient.Lane.CONTROL, exchange -> exchange.json("PATCH",
                    "/api/library/v1/wardrobe", Map.of("operation_id", UUID.randomUUID().toString(),
                            "expected_equipment_revision", state.equipmentRevision(), "asset_id", state.assetId(),
                            "asset_revision", state.assetRevision(), "show_equipment", showing), Map.class));
        } else {
            return;
        }
        long expected = visibilityGeneration;
        visibilityRequest.future().whenComplete((value, failure) -> {
            if (!removed && expected == visibilityGeneration) {
                if (failure != null) {
                    error(failure);
                }
                refreshVisibility();
            }
        });
    }

    private void error(Throwable failure) {
        message = FiguraText.of("gui.library.failure", failure instanceof LibraryClient.Failure typed
                ? typed.code : "LIBRARY_UNAVAILABLE");
    }

    @Override
    public void tick() {
        super.tick();
        preview.tick();
        if (cloudAccount != null && !cloudAccount.equals(FiguraMod.getLocalPlayerUUID())) {
            if (selected != null && selected.cloud() != null) {
                selected = null;
                preview.clear();
            }
            selections.entrySet().removeIf(entry -> entry.getValue() != null && entry.getValue().cloud() != null);
            offlineEntries = List.of();
            cloudAccount = FiguraMod.getLocalPlayerUUID();
        }
        if (client != null && !client.current()) {
            closeThumbnails();
            client = null;
            offlineScope = "";
            loadOfflineMetadata();
            if (selected != null && selected.cloud() != null) {
                preview.clear();
            }
            libraryVisibility = null;
            localVisibility = null;
        }
        if (client == null) {
            if (NetworkStuff.isConnected()) {
                connect();
                refreshVisibility();
            } else {
                loadOfflineMetadata();
            }
        }
        if (client != null && collection != null && shownEntries != collection.entries()) {
            refreshCloudRows();
        }
        if (collectionGrid != null && source == Source.LOCAL) {
            refreshCollectionCards();
        }
        if (tab == Tab.OUTFIT) {
            var current = AvatarManager.getAvatarForPlayer(FiguraMod.getLocalPlayerUUID());
            if (current != observedAvatar || (current != null && current.nbt != observedNbt)) {
                refreshVisibility();
            }
            if (previewWidget != null) {
                previewWidget.setEntity(minecraft.player);
            }
        }
        boolean busy = enabling != null && !enabling.future().isDone();
        if (candidateButton != null) {
            candidateButton.setActive(!parts && !busy && !collectionBusy() && (selected == null && tab == Tab.OUTFIT
                    || selected != null && (selected.path() != null
                    || client != null && client.current() && collection.available())));
            candidateButton.setMessage(text(selected == null && tab == Tab.OUTFIT ? "go_collection"
                    : candidate() ? "remove_outfit" : "add_outfit"));
        }
        if (showModel != null) {
            boolean pending = visibilityRequest != null && !visibilityRequest.future().isDone();
            boolean localTarget = localVisibility != null && localVisibility.canChangeVisibility()
                    && tab == Tab.OUTFIT;
            boolean privateTarget = libraryVisibility != null && collection != null
                    && collection.entries().stream().anyMatch(entry -> entry.modelId().equals(libraryVisibility.model())
                    && entry.model() != null && entry.model().visibility().equals("PRIVATE") && entry.model().allows("equip"));
            showModel.setActive(!parts && !busy && !pending && client != null && client.current() && (localTarget || privateTarget));
        }
        boolean uploading = NetworkStuff.getUploadProgress() != null;
        if (wasUploading && !uploading) {
            refreshVisibility();
        }
        wasUploading = uploading;
        updateMotdWidget();
        if (uploadButton != null) {
            var current = AvatarManager.getAvatarForPlayer(FiguraMod.getLocalPlayerUUID());
            boolean canPublish = selected != null ? !parts && candidate() && !busy && !selectionIsCaching()
                    && (selected.path() != null || client != null && client.current())
                    : NetworkStuff.canUpload() && !AvatarManager.localUploaded
                        && current != null && current.loaded && current.nbt != null;
            uploadButton.setActive(canPublish);
        }
        if (deleteButton != null) {
            deleteButton.setActive(NetworkStuff.isConnected() && AvatarManager.localUploaded && !uploading);
        }
        syncHierarchy();
        if (previewWidget != null) {
            previewWidget.setSelectedBone(tab == Tab.OUTFIT ? bones.selectedPart() : null);
        }
        updateCollectionButtons();
    }

    @Override
    protected void clearWidgets() {
        saveView();
        if (collectionGrid != null) {
            collectionGrid.close();
            collectionGrid = null;
        }
        if (cloudList != null) {
            cloudList.close();
        }
        super.clearWidgets();
    }

    private void closeThumbnails() {
        if (collectionGrid != null) {
            collectionGrid.close();
            collectionGrid.thumbnails(null);
        }
        if (cloudList != null) {
            cloudList.thumbnails(null);
        }
        if (thumbnails != null) {
            thumbnails.close();
            thumbnails = null;
        }
    }

    @Override
    public void removed() {
        saveView();
        removed = true;
        selectionGeneration++;
        visibilityGeneration++;
        if (visibilityRequest != null) {
            visibilityRequest.cancel();
        }
        if (preview != null && !preview.isExpanded()) {
            preview.close();
        }
        if (equipment != null) {
            equipment.close();
        }
        closeThumbnails();
        super.removed();
        LocalAvatarFetcher.save();
    }

    @Override
    public void onFilesDrop(List<Path> paths) {
        super.onFilesDrop(paths);
        String names = paths.stream().map(IOUtils::getFileNameOrEmpty).collect(java.util.stream.Collectors.joining("\n"));
        minecraft.setScreen(new FiguraConfirmScreen(confirmed -> {
            if (confirmed) {
                try {
                    LocalAvatarFetcher.loadExternal(paths);
                    FiguraToast.sendToast(FiguraText.of("toast.wardrobe_copy.success", paths.size()));
                } catch (Exception failure) {
                    FiguraToast.sendToast(FiguraText.of("toast.wardrobe_copy.error"), FiguraToast.ToastType.ERROR);
                    FiguraMod.LOGGER.error("Failed to copy files", failure);
                }
            }
            minecraft.setScreen(this);
        }, FiguraText.of("gui.wardrobe.drop_files"), names, this));
    }
}
