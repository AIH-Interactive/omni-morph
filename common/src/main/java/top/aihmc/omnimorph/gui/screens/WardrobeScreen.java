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
import top.aihmc.omnimorph.backend2.library.OutfitApplyResult;
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
import top.aihmc.omnimorph.gui.widgets.TextField;
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
import top.aihmc.omnimorph.gui.widgets.wardrobe.WardrobeEditorPanel;
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
import top.aihmc.omnimorph.model.library.LocalCosmeticCatalog;
import top.aihmc.omnimorph.model.library.CloudCosmeticSource;
import top.aihmc.omnimorph.model.library.OutfitPresetResources;
import top.aihmc.omnimorph.model.library.OutfitApplyResources;
import top.aihmc.omnimorph.model.library.OutfitApplyService;
import top.aihmc.omnimorph.model.outfit.CosmeticMetadata;
import top.aihmc.omnimorph.model.outfit.LocalCosmeticInventory;
import top.aihmc.omnimorph.model.outfit.LocalBaseOutfits;
import top.aihmc.omnimorph.model.outfit.LocalCosmeticSource;
import top.aihmc.omnimorph.model.outfit.OutfitConfig;
import top.aihmc.omnimorph.model.outfit.OutfitComposition;
import top.aihmc.omnimorph.model.outfit.OutfitCodec;
import top.aihmc.omnimorph.model.outfit.OutfitLimits;
import top.aihmc.omnimorph.model.outfit.OutfitDraft;
import top.aihmc.omnimorph.model.outfit.OutfitPreparation;
import top.aihmc.omnimorph.permissions.PermissionManager;
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
    private enum Source { ALL, LOCAL, CLOUD }
    private enum Mode { ALL, AVATAR, COSMETIC }
    private enum Pane { RESOURCES, PREVIEW, TREE, INFO }

    private record Selection(Path path, Path reference, boolean ysm, UUID cloud, Component name,
                             LocalCosmeticInventory.Entry cosmetic) {
        private Selection(Path path, Path reference, boolean ysm, UUID cloud, Component name) {
            this(path, reference, ysm, cloud, name, null);
        }
    }
    private record Context(Tab tab, Source source) {}

    private Tab tab = Tab.OUTFIT;
    private Source source = Source.ALL;
    private Mode mode = Mode.ALL;
    private Pane pane = Pane.RESOURCES;
    private boolean bonesShown;
    private boolean parts;
    private boolean resourceFiltersExpanded;
    private final Map<Context, AvatarList.View> localViews = new HashMap<>();
    private final Map<Context, WardrobeCloudList.View> cloudViews = new HashMap<>();
    private final Map<Context, WardrobeCollectionGrid.View> cardViews = new HashMap<>();
    private final Map<Tab, EntityPreview.View> previewViews = new HashMap<>();
    private final Map<Context, Selection> selections = new HashMap<>();
    private final Map<Tab, Source> tabSources = new HashMap<>();
    private final Map<Tab, Mode> tabModes = new HashMap<>();
    private Context localContext;
    private LocalOutfitCandidates localCandidates;
    private LocalCosmeticCatalog cosmetics;
    private List<LocalCosmeticInventory.Entry> observedCosmetics = List.of();
    private final java.util.Set<UUID> autoCachedModels = new java.util.HashSet<>();
    private LibraryClient.Request<LibraryModels.Capabilities> outfitCapabilitiesRequest;
    private OutfitLimits outfitLimits = OutfitLimits.defaults();
    private OutfitDraft outfitDraft;
    private UUID draftIdentity = UUID.randomUUID();
    private UUID draftAccount;
    private final Map<OutfitConfig.ResourceRef, LocalCosmeticInventory.Entry> draftResources = new HashMap<>();
    private final Map<OutfitConfig.ResourceRef, CloudCosmeticSource.Descriptor> draftCloudResources = new HashMap<>();
    private Map<OutfitConfig.ResourceRef, CompoundTag> draftPreparedResources = new java.util.concurrent.ConcurrentHashMap<>();
    private CloudCosmeticSource.Operation cloudCosmeticSelection;
    private CloudCosmeticSource.Prepared selectedCloudCosmetic;
    private CloudCosmeticSource.Batch draftCloudBatch;
    private CompoundTag draftBase;
    private CompoundTag committedDraftBase;
    private boolean draftBasePersistent;
    private boolean committedDraftBasePersistent;
    private java.util.concurrent.CompletableFuture<OutfitDraft> draftStart;
    private long draftPreviewGeneration;
    private long resourceSelectionGeneration;
    private boolean draftPreviewActive;
    private long baseSelectionGeneration;
    private java.util.concurrent.CompletableFuture<Void> changingDraftBase;
    private OutfitConfig.ResourceRef draftPreviewBase;
    private final List<Button> avatarActions = new ArrayList<>();
    private Button addInstanceButton;
    private Button editInstancesButton;
    private Button cancelDraftButton;
    private Button resetDraftButton;
    private Button retryDraftButton;
    private Button refreshDraftButton;
    private OutfitApplyService outfitApplications;
    private OutfitApplyService.Operation applyingOutfit;
    private OutfitDraft applyingDraft;
    private long applyingDraftGeneration;
    private OutfitApplyResult.Conflict outfitConflict;
    private LibraryClient.Request<OutfitConfig> draftConfigurationRequest;
    private OutfitApplyResources pendingDraftSources;
    private OutfitApplyResources draftSourceLoader;
    private OutfitPresetResources.Prepared draftFixedResources;
    private LibraryClient draftAuthority;
    private List<OutfitPresetResources.Issue> outfitIssues = List.of();
    private boolean editingInstance;
    private UUID editingVirtualAnchor;
    private OutfitConfig.BoneRef editingBone;
    private UUID virtualRemovalArmed;
    private long virtualRemovalGeneration;
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
    private top.aihmc.omnimorph.avatar.Avatar visibilityAvatar;
    private CompoundTag visibilityNbt;
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
    private List<BoneTreeState.ViewChoice> renderedTreeChoices = List.of();
    private OutfitConfig.AnchorRef pendingAnchorFocus;
    private UUID automaticAnchorFocus;
    private boolean boneHelpersShown = true;
    private boolean cosmeticControlsOpen;
    private boolean cosmeticAnimationsOpen;
    private boolean outfitPresetsOpen;
    private boolean outfitRefreshConfirmOpen;
    private Button cosmeticSettingsButton;
    private UUID attachingInstance;
    private OutfitDraft attachmentDraft;
    private long attachmentSelection;
    private top.aihmc.omnimorph.model.outfit.OutfitComposition.MountTarget observedAutomaticTarget;
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
    private String publishStateTrace;
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
        discardOldAccountDraft();
        localList = null;
        cloudList = null;
        infoWidget = null;
        motdWidget = null;
        avatarActions.clear();
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
        addInstanceButton = null;
        editInstancesButton = null;
        cancelDraftButton = null;
        resetDraftButton = null;
        retryDraftButton = null;
        refreshDraftButton = null;
        cosmeticSettingsButton = null;
        previewWidget = null;
        if (localCandidates == null) {
            localCandidates = LocalOutfitCandidates.forDirectory(FiguraMod.getFiguraDirectory());
        }
        if (cosmetics == null) {
            cosmetics = new LocalCosmeticCatalog(FiguraMod.getFiguraDirectory(), minecraft::execute);
            observedCosmetics = List.of();
            cosmetics.refresh();
        }
        connect();
        if (returning && collection != null && client != null) {
            collection.refresh();
        }
        if (preview == null || preview.status().stage() == LibraryPreviewSession.Stage.CLOSED) {
            preview = new LibraryPreviewSession();
            previewClient = null;
            if (draftPreviewActive) {
                previewOutfitDraft();
            } else if (selected != null) {
                previewSelection();
            } else {
                showLoadedOutfitDraft();
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
                        button -> switchRightPane(false))).setActive(bonesShown || editingInstance);
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
        if (!bones) {
            clearAnchorSelection();
        }
        editingInstance = false;
        editingVirtualAnchor = null;
        editingBone = null;
        bonesShown = bones;
        pane = bones ? Pane.TREE : Pane.INFO;
        rebuild();
    }

    private void connect() {
        try {
            LibraryClient next = NetworkStuff.libraryClient();
            if (client != next) {
                closeOutfitOperations();
                if (equipment != null) {
                    equipment.close();
                }
                closeThumbnails();
                client = next;
                cloudAccount = client.session().player();
                collection = NetworkStuff.wardrobeCollection();
                transfers = new LibraryTransferService(client, FiguraMod.getCacheDirectory());
                equipment = new LibraryEquipmentService(client, transfers);
                outfitApplications = new OutfitApplyService(client, transfers,
                        FiguraMod.getFiguraDirectory(), FiguraMod.getCacheDirectory());
                visibility = new LibraryEquipmentVisibility(client);
                readOutfitLimits(client);
                collection.refresh();
            }
            if (thumbnails == null) {
                thumbnails = new LibraryThumbnailService(client, FiguraMod.getCacheDirectory());
            }
            if (outfitCapabilitiesRequest == null) {
                readOutfitLimits(client);
            }
            if (collectionGrid != null) {
                collectionGrid.thumbnails(thumbnails);
            }
            if (cloudList != null) {
                cloudList.thumbnails(thumbnails);
            }
        } catch (java.io.IOException offline) {
            closeOutfitOperations();
            client = null;
            loadOfflineMetadata();
        }
    }

    private void readOutfitLimits(LibraryClient expectedClient) {
        var request = expectedClient.capabilities();
        outfitCapabilitiesRequest = request;
        request.future().whenComplete((capabilities, failure) -> {
            if (removed || client != expectedClient || outfitCapabilitiesRequest != request || !expectedClient.current()) {
                return;
            }
            if (failure == null) {
                outfitLimits = capabilities.outfitsV1() == null
                        ? OutfitLimits.defaults() : capabilities.outfitsV1().limits();
            }
            if (draftPreviewActive) {
                previewOutfitDraft();
            } else if (selectedIsAccessory()) {
                previewSelection();
            }
        });
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
        resourceSelectionGeneration++;
        if (preview != null) {
            preview.clear();
        }
        tabSources.put(tab, source);
        tabModes.put(tab, mode);
        tab = next;
        source = tabSources.getOrDefault(next, Source.ALL);
        mode = tabModes.getOrDefault(next, Mode.ALL);
        parts = mode == Mode.COSMETIC;
        selected = selections.get(context());
        if (tab == Tab.COLLECTION && (pane == Pane.TREE || pane == Pane.INFO)) {
            pane = Pane.PREVIEW;
        }
        if (tab == Tab.OUTFIT && outfitDraft != null && outfitDraft.dirty()) {
            previewOutfitDraft();
        } else {
            draftPreviewActive = false;
            draftPreviewGeneration++;
        }
        if (!draftPreviewActive && selected != null) {
            previewSelection();
        } else if (!draftPreviewActive) {
            showLoadedOutfitDraft();
        }
        syncHierarchy();
        rebuildWidgets();
    }

    /**
     * The outfit tab opens on the committed outfit already loaded for this player, so its accessories appear in the
     * preview and bone tree without first selecting an entry. Editing stays local; nothing is read from the server.
     */
    private void showLoadedOutfitDraft() {
        if (tab != Tab.OUTFIT || selected != null || draftPreviewActive || minecraft == null) {
            return;
        }
        boolean loadedOutfit = AvatarManager.getLoadedOutfit(FiguraMod.getLocalPlayerUUID()) != null;
        boolean localPreview = top.aihmc.omnimorph.model.outfit.LocalOutfitPreview.state(minecraft.player) != null;
        if (!loadedOutfit && !localPreview) {
            return;
        }
        beginOutfitDraft().whenComplete((draft, failure) -> {
            if (removed || failure != null || tab != Tab.OUTFIT || selected != null || draftPreviewActive) {
                return;
            }
            previewOutfitDraft();
        });
    }

    private void buildResources() {
        int x = layout.leftX();
        int w = layout.leftWidth();
        var sourceFilter = new LibraryDropdown(0, 0, 80,
                () -> List.of(text("all"), text("local"), text("cloud")), () -> source.ordinal(), index -> {
            saveView();
            source = Source.values()[index];
            if (tab == Tab.COLLECTION && source != Source.LOCAL && collection != null) {
                collection.refresh();
            }
            rebuildWidgets();
        });
        var modeFilter = new LibraryDropdown(0, 0, 80,
                () -> List.of(text("all"), text("avatars"), text("parts")), () -> mode.ordinal(), index -> {
            mode = Mode.values()[index];
            parts = mode == Mode.COSMETIC;
            rebuild();
        });
        int listHeight = Math.max(88, height - 88);
        if (tab == Tab.COLLECTION) {
            var view = cardViews.computeIfAbsent(context(), ignored -> new WardrobeCollectionGrid.View());
            collectionGrid = addRenderableWidget(new WardrobeCollectionGrid(x, 54, w,
                    source != Source.LOCAL ? listHeight - 14 : listHeight, view,
                    client == null ? null : thumbnails, entry -> {
                if (entry.cosmetic() != null) {
                    selectCosmetic(entry.cosmetic());
                } else if (entry.local() != null) {
                    selectLocal(entry.local());
                } else {
                    selectCloud(entry.cloud());
                }
            }, id -> client == null ? FiguraText.of("gui.library.state.offline") : text(collection.cacheState(id))));
            collectionGrid.setResourceFilters(sourceFilter, modeFilter, resourceFiltersExpanded,
                    expanded -> resourceFiltersExpanded = expanded);
            refreshCollectionCards();
            if (source != Source.LOCAL) {
                addRenderableOnly((gui, mouseX, mouseY, delta) -> UIHelper.renderScrollingText(gui,
                        client == null ? FiguraText.of("gui.library.state.offline")
                                : collection.failure().isEmpty() ? text("cloud")
                                : FiguraText.of("gui.library.failure", collection.failure()),
                        x + 2, height - 46, w - 4, 0xffffdd88));
            }
        } else if (source == Source.LOCAL && mode != Mode.COSMETIC
                && (mode == Mode.AVATAR || localCosmetics(true).isEmpty())) {
            localList = new AvatarList(x, 54, w, listHeight, this, this::selectLocal,
                    avatar -> tab == Tab.COLLECTION || localCandidates.contains(avatar.getTheActualPathForThis()));
            localList.showSourceBadge(true);
            localList.setOutfitRemoval(this::removeLocalOutfit);
            localList.setActionsVisible(false);
            var saved = selections.get(context());
            localContext = context();
            localList.restore(localViews.get(context()), saved == null ? null : saved.reference());
            localList.setResourceFilters(sourceFilter, modeFilter, resourceFiltersExpanded,
                    expanded -> resourceFiltersExpanded = expanded);
            addRenderableWidget(localList);
        } else {
            var view = cloudViews.computeIfAbsent(context(), ignored -> new WardrobeCloudList.View());
            cloudList = addRenderableWidget(new WardrobeCloudList(x, 54, w, Math.max(72, listHeight - 14), view,
                    this::selectCloud, id -> client == null ? FiguraText.of("gui.library.state.offline")
                    : text(collection.cacheState(id))));
            cloudList.setResourceFilters(sourceFilter, modeFilter, resourceFiltersExpanded,
                    expanded -> resourceFiltersExpanded = expanded);
            cloudList.setOutfitRemoval(this::removeCloudOutfit);
            cloudList.setAccessoryActions(this::addQuickMountMenu);
            cloudList.thumbnails(client == null ? null : thumbnails);
            refreshCloudRows();
            addRenderableOnly((gui, mouseX, mouseY, delta) -> UIHelper.renderScrollingText(gui,
                    resourceStatus(),
                    x + 2, height - 46, w - 4, 0xffffdd88));
        }
    }

    private Component resourceStatus() {
        if (source == Source.LOCAL) {
            return cosmetics.failure().isEmpty() ? text("local")
                    : FiguraText.of("gui.library.failure", cosmetics.failure());
        }
        if (client == null) {
            return FiguraText.of("gui.library.state.offline");
        }
        return collection.failure().isEmpty() ? text("cloud")
                : FiguraText.of("gui.library.failure", collection.failure());
    }

    private void buildPreview() {
        int x = layout.previewX();
        int w = layout.previewWidth();
        boolean cloudActions = source == Source.CLOUD || source == Source.ALL && selected != null && selected.cloud() != null;
        int actionCount = tab == Tab.OUTFIT ? 16 : cloudActions ? 8 : 4;
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
        previewWidget.setDependencyIssues(tab == Tab.OUTFIT ? outfitIssues : List.of());
        previewWidget.setSelectedBone(previewBoneSelection());
        addRenderableWidget(previewWidget);
        addRenderableOnly((gui, mouseX, mouseY, delta) -> {
            Component name;
            if (tab == Tab.OUTFIT && draftPreviewActive) {
                name = text("draft_preview");
            } else if (tab == Tab.OUTFIT && selected == null) {
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
            if (state.getString().isEmpty() && draftPreviewActive && outfitDraft != null
                    && outfitDraft.selectedInstance() != null) {
                var composition = preview.outfitComposition();
                UUID instance = outfitDraft.selectedInstance();
                String status = composition == null ? "preview_loading"
                        : !composition.runtimeInstances().containsKey(instance) ? "preview_unavailable"
                        : !composition.hasPreviewFrame() ? "preview_waiting"
                        : composition.previewMountTarget(instance) == null ? "preview_unresolved" : "preview_mounted";
                footer = text(status).copy().append(" · ").append(footer);
            }
            UIHelper.renderScrollingText(gui, footer, x + 4, actionsY - 12, w - 8, 0xffd3e7e1);
            if (AvatarManager.panic) {
                gui.text(font, FiguraText.of("gui.panic", Configs.PANIC_BUTTON.keyBind.getTranslatedKeyMessage()),
                        x + 4, layout.top() + 4, 0xffffff55);
            }
        });
        var actions = addRenderableWidget(new LibraryActionBar(x, actionsY, w, actionsHeight));
        if (tab == Tab.COLLECTION) {
            candidateButton = actions.action(new LibraryIconButton("wardrobe_outfit", 2,
                    () -> candidate() ? 1 : 0, text("add_outfit"), null, button -> changeCandidate()));
            if (cloudActions) {
                buildCloudCollectionActions(actions);
            } else {
                actions.separator();
                actions.action(icon(0, 0, "new_avatar", "create_resource",
                        this::openCreateMenu));
                actions.action(icon(0, 0, "folder", "local", button ->
                        net.minecraft.util.Util.getPlatform().openUri(LocalAvatarFetcher.getLocalAvatarDirectory().toUri())));
            }
        }
        if (tab == Tab.OUTFIT) {
            // Shared by avatar and accessory modes: with outfit support it commits the base and accessories together.
            uploadButton = actions.action(icon(0, 0, "upload", "publish_current", button -> publishCurrent()));
            avatarActions.add(actions.action(icon(0, 0, "reload", "reload_current",
                    button -> AvatarManager.reloadCloudAvatar())));
            showModel = actions.action(new SwitchButton(0, 0, 24, 24, 0, 0, 24,
                    new FiguraIdentifier("textures/gui/wardrobe_visibility.png"), 72, 48, text("show_others"),
                    button -> changeVisibility()));
            avatarActions.add(showModel);
            var appeals = actions.action(icon(0, 0, "wardrobe_appeal", "info", button -> openAppeals()));
            appeals.setTooltip(FiguraText.of("gui.library.my_appeals"));
            avatarActions.add(appeals);
            deleteButton = actions.action(icon(0, 0, "delete", "delete_current", button -> {
                if (NetworkStuff.isConnected() && AvatarManager.localUploaded && NetworkStuff.getUploadProgress() == null) {
                    NetworkStuff.deleteAvatar(null);
                }
            }));
            avatarActions.add(deleteButton);
            actions.separator();
            avatarActions.add(actions.action(icon(0, 0, "sound", "sound",
                    button -> minecraft.setScreen(new SoundScreen(this)))));
            avatarActions.add(actions.action(icon(0, 0, "keybind", "keybinds",
                    button -> minecraft.setScreen(new KeybindScreen(this)))));
            avatarActions.add(actions.action(icon(0, 0, "unselect", "clear_current", button -> {
                AvatarManager.loadLocalAvatar(null);
                AvatarList.selectedEntry = null;
            })));
            actions.separator();
            addInstanceButton = actions.action(outfitIcon("wardrobe_instance_add", "instance_add", button -> addSelectedInstance()));
            editInstancesButton = actions.action(outfitIcon("wardrobe_instances", "instances", button -> openInstances(button)));
            cosmeticSettingsButton = actions.action(new LibraryIconButton("cosmetic_settings", 1, () -> 0,
                    text("cosmetic_settings"), null, button -> openCosmeticControls()));
            refreshCosmeticSettings();
            actions.separator();
            retryDraftButton = actions.action(outfitIcon("wardrobe_draft_retry", "draft_retry", button -> retryOutfitDraft()));
            refreshDraftButton = actions.action(outfitIcon("wardrobe_draft_refresh", "draft_refresh", button -> refreshOutfitDraft()));
            uploadButton.setActive(false);
            retryDraftButton.setActive(false);
            refreshDraftButton.setActive(outfitApplyAvailable() && !outfitOperationBusy());
            cancelDraftButton = actions.action(outfitIcon("wardrobe_draft_cancel", "draft_cancel", button -> cancelOutfitDraft()));
            resetDraftButton = actions.action(outfitIcon("wardrobe_draft_reset", "draft_reset", button -> resetOutfitDraft()));
            actions.action(outfitIcon("wardrobe_outfit_presets", "outfit_presets", button -> openOutfitPresets()));
            refreshActionGroups();
        }
    }

    private Button outfitIcon(String icon, String tooltip, Button.OnPress action) {
        return new LibraryIconButton(icon, 1, () -> 0, text(tooltip), null, action);
    }

    private void refreshActionGroups() {
        boolean accessory = selectedIsAccessory() || selected == null && (parts || editingInstance);
        for (Button button : avatarActions) {
            button.setVisible(!accessory);
        }
        if (addInstanceButton != null) {
            addInstanceButton.setVisible(accessory);
            editInstancesButton.setVisible(accessory);
            cosmeticSettingsButton.setVisible(accessory);
        }
    }

    private void openOutfitPresets() {
        if (removed || outfitPresetsOpen || !outfitLimitsReady()) {
            return;
        }
        long selection = resourceSelectionGeneration;
        beginOutfitDraft().whenComplete((draft, failure) -> {
            if (removed || outfitPresetsOpen || selection != resourceSelectionGeneration) {
                return;
            }
            if (failure != null) {
                error(failure);
                return;
            }
            outfitPresetsOpen = true;
            minecraft.setScreen(new OutfitPresetsScreen(this, draft, FiguraMod.getLocalPlayerUUID(),
                    FiguraMod.getFiguraDirectory(), client == null ? OutfitLimits.defaults() : outfitLimits, client, transfers));
        });
    }

    boolean outfitPresetsActive(OutfitDraft draft, long generation, UUID account) {
        return outfitPresetsOpen && !removed && outfitDraft == draft && draft.generation() == generation
                && account.equals(FiguraMod.getLocalPlayerUUID()) && account.equals(draftAccount);
    }

    boolean presetBaseCanPersist() {
        return outfitDraft != null && (outfitDraft.configuration().base() == null || draftBasePersistent);
    }

    void importOutfitPreset(OutfitDraft draft, long generation, UUID account,
                           OutfitConfig configuration, OutfitPresetResources.Prepared prepared, CompoundTag base) {
        if (!outfitPresetsActive(draft, generation, account) || !prepared.ready()) {
            throw new IllegalStateException("OUTFIT_PRESET_RETIRED");
        }
        installOutfitPreset(draft, configuration, prepared, base);
        previewOutfitDraft();
    }

    private void installOutfitPreset(OutfitDraft draft, OutfitConfig configuration,
                                    OutfitPresetResources.Prepared prepared, CompoundTag base) {
        if (configuration.base() != null && base == null) {
            throw new IllegalStateException("OUTFIT_BASE_NOT_READY");
        }
        var local = prepared.localAccessories();
        var cloud = new HashMap<OutfitConfig.ResourceRef, CloudCosmeticSource.Descriptor>();
        for (var instance : configuration.instances()) {
            if (instance.resource().source() == OutfitConfig.Source.CLOUD) {
                var metadata = prepared.metadata(instance.resource());
                cloud.put(instance.resource(), new CloudCosmeticSource.Descriptor(instance.resource(), metadata, metadata.common().name));
            }
        }
        draft.importPreset(configuration);
        draftBase = base;
        draftBasePersistent = true;
        draftResources.putAll(local);
        draftCloudResources.putAll(cloud);
        pruneDraftResources();
        clearAnchorSelection();
        virtualRemovalArmed = null;
        editingInstance = false;
        editingBone = null;
        editingVirtualAnchor = null;
        pendingAnchorFocus = null;
        automaticAnchorFocus = null;
        observedAutomaticTarget = null;
        selected = null;
        selections.put(context(), null);
    }

    void closeOutfitPresets(boolean returning) {
        outfitPresetsOpen = false;
        if (!returning) {
            removed();
        }
    }

    private void refreshCosmeticSettings() {
        if (cosmeticSettingsButton != null) {
            var composition = preview.outfitComposition();
            boolean available = draftPreviewActive && outfitDraft != null && composition != null
                    && !composition.controlGroups().isEmpty();
            cosmeticSettingsButton.setActive(available);
            cosmeticSettingsButton.setTooltip(text(available ? "cosmetic_settings" : "cosmetic_settings_empty"));
        }
    }

    private void openCosmeticControls() {
        var composition = preview.outfitComposition();
        if (!draftPreviewActive || outfitDraft == null || composition == null || composition.controlGroups().isEmpty()) {
            return;
        }
        cosmeticControlsOpen = true;
        minecraft.setScreen(new CosmeticControlsScreen(this, preview, composition, outfitDraft,
                FiguraMod.getLocalPlayerUUID()));
    }

    boolean cosmeticControlsActive(OutfitComposition composition,
                                   OutfitDraft expectedDraft, UUID account,
                                   OutfitComposition.ControlGroup group) {
        return (cosmeticControlsOpen || cosmeticAnimationsOpen) && draftPreviewActive && outfitDraft == expectedDraft
                && account.equals(FiguraMod.getLocalPlayerUUID()) && preview.outfitComposition() == composition
                && composition.controlGroups().contains(group)
                && outfitDraft.configuration().instances().stream().anyMatch(instance ->
                        instance.instanceId().equals(group.instance().instanceId()) && instance.enabled()
                                && instance.resource().equals(group.instance().resource()));
    }

    Component cosmeticResourceName(OutfitConfig.ResourceRef resource) {
        return resourceName(resource);
    }

    void saveCosmeticControls(OutfitComposition.ControlGroup group) {
        outfitDraft.setControls(group.instance().instanceId(), group.avatar().cosmeticContext.controls());
    }

    void closeCosmeticControls(boolean returning) {
        cosmeticControlsOpen = false;
        if (!returning) {
            removed();
        }
    }

    private void openCosmeticAnimations() {
        var composition = preview.outfitComposition();
        if (!draftPreviewActive || outfitDraft == null || composition == null || !composition.actionWheel().available()) {
            return;
        }
        cosmeticAnimationsOpen = true;
        minecraft.setScreen(new CosmeticAnimationScreen(this, preview, composition, outfitDraft,
                FiguraMod.getLocalPlayerUUID()));
    }

    boolean cosmeticAnimationsActive(OutfitComposition composition, OutfitDraft draft, UUID account) {
        return cosmeticAnimationsOpen && draftPreviewActive && outfitDraft == draft
                && account.equals(FiguraMod.getLocalPlayerUUID()) && preview.outfitComposition() == composition;
    }

    void saveAnimationControls(OutfitComposition composition, OutfitDraft draft, UUID account,
                               top.aihmc.omnimorph.avatar.Avatar owner) {
        if (owner == null || owner.cosmeticContext == null
                || !animationOwnerActive(composition, draft, account, owner)) {
            return;
        }
        UUID id = owner.cosmeticContext.instanceId();
        var controls = owner.cosmeticContext.controls();
        if (!draft.instance(id).controls().equals(controls)) {
            draft.setControls(id, controls);
        }
    }

    boolean animationOwnerActive(OutfitComposition composition, OutfitDraft draft, UUID account,
                                  top.aihmc.omnimorph.avatar.Avatar owner) {
        if (!cosmeticAnimationsActive(composition, draft, account)) {
            return false;
        }
        if (owner == null || owner == preview.outfitBase()) {
            return true;
        }
        var context = owner.cosmeticContext;
        return context != null && draft.configuration().instances().stream().anyMatch(instance ->
                instance.instanceId().equals(context.instanceId()) && instance.enabled()
                        && instance.resource().equals(context.resource()));
    }

    void closeCosmeticAnimations(boolean returning) {
        cosmeticAnimationsOpen = false;
        if (!returning) {
            removed();
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
        actions.separator();
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
        renderedTreeChoices = bones.viewChoices();
        int treeTop = layout.top();
        if (preview != null && preview.outfitHierarchy() != top.aihmc.omnimorph.model.library.OutfitHierarchy.EMPTY) {
            var choices = bones.viewChoices();
            addRenderableWidget(new LibraryDropdown(x, treeTop, w,
                    () -> choices.stream().<Component>map(choice -> choice.view().equals(BoneTreeState.View.ALL)
                            ? text("tree_all") : Component.literal(choice.label())).toList(),
                    () -> {
                        for (int index = 0; index < choices.size(); index++) {
                            if (choices.get(index).view().equals(bones.view())) {
                                return index;
                            }
                        }
                        return 0;
                    }, index -> bones.view(choices.get(index).view()), 6));
            treeTop += 24;
        }
        addRenderableWidget(new BoneTreeWidget(x, treeTop, w, Math.max(40, height - 140 - (treeTop - layout.top())), bones,
                this::refreshBones, this::selectTreeTarget)).setContextAction(
                        (target, event) -> openBoneContext(target, event.x(), event.y()));
        if (outfitDraft != null && draftPreviewActive) {
            addRenderableWidget(new Button(x, height - 60, w, 20,
                    text(attachingInstance == null ? "anchor_actions" : "anchor_target_actions"), null,
                    this::openAnchorActions));
        } else {
            addRenderableOnly((gui, mouseX, mouseY, delta) -> UIHelper.renderScrollingText(gui,
                    text("bones_readonly"), x + 4, height - 52, w - 8, 0xffaaaaaa));
        }
    }

    private void refreshBones() {
        if (selectionPreview()) {
            preview.refreshHierarchy();
        } else {
            liveHierarchy = false;
        }
        syncHierarchy();
    }

    private void selectTreeTarget(top.aihmc.omnimorph.model.library.OutfitHierarchy.Target target) {
        automaticAnchorFocus = null;
        observedAutomaticTarget = null;
        if (outfitDraft == null) {
            return;
        }
        if (attachingInstance != null && attachmentDraft == outfitDraft
                && outfitDraft.configuration().instances().stream().anyMatch(value -> value.instanceId().equals(attachingInstance))) {
            outfitDraft.select(attachingInstance);
            return;
        }
        clearAnchorSelection();
        UUID instance = target == null ? null : target.instanceId();
        if (instance == null || outfitDraft.configuration().instances().stream()
                .anyMatch(value -> value.instanceId().equals(instance))) {
            outfitDraft.select(instance);
        }
    }

    private boolean selectionPreview() {
        return tab == Tab.COLLECTION || selected != null || draftPreviewActive;
    }

    private void syncHierarchy() {
        if (!selectionPreview()) {
            var current = loadedDraftBase(FiguraMod.getLocalPlayerUUID());
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
            if (preview.outfitHierarchy() == top.aihmc.omnimorph.model.library.OutfitHierarchy.EMPTY) {
                bones.replace(preview.hierarchy());
            } else {
                bones.replace(preview.outfitHierarchy());
                if (pendingAnchorFocus != null) {
                    bones.locate(pendingAnchorFocus);
                    pendingAnchorFocus = null;
                }
            }
        }
    }

    private void buildInformation() {
        if (editingBone != null) {
            if (outfitDraft != null && editingBone.instanceId().equals(outfitDraft.selectedInstance())
                    && editableBone(editingBone)) {
                buildBoneOverrideEditor(editingBone);
                return;
            }
            editingBone = null;
        }
        if (outfitDraft != null && editingVirtualAnchor != null) {
            var anchor = outfitDraft.configuration().virtualAnchors().stream()
                    .filter(value -> value.id().equals(editingVirtualAnchor)).findFirst();
            if (anchor.isPresent()) {
                buildVirtualAnchorEditor(anchor.get());
                return;
            }
            editingVirtualAnchor = null;
        }
        if (editingInstance && outfitDraft != null && outfitDraft.selectedInstance() != null) {
            buildInstanceTransform();
            return;
        }
        int x = layout.treeX();
        int w = layout.treeWidth();
        int infoHeight = Math.min(Math.max(108, layout.height() / 2), Math.max(40, layout.height() - 56));
        infoWidget = addRenderableOnly(new AvatarInfoWidget(x, layout.top(), w, infoHeight));
        updateMotdWidget();
    }

    private void buildInstanceTransform() {
        UUID id = outfitDraft.selectedInstance();
        var instance = outfitDraft.instance(id);
        int x = layout.treeX();
        int width = layout.treeWidth();
        int top = layout.top();
        addRenderableOnly((gui, mouseX, mouseY, delta) -> UIHelper.renderScrollingText(gui,
                instanceName(instance), x + 2, top + 4, width - 4, 0xffffffff));
        var anchors = new ArrayList<OutfitConfig.AnchorRef>();
        anchors.add(instance.anchor());
        for (String name : OutfitConfig.STANDARD_ANCHORS.stream().sorted().toList()) {
            var anchor = OutfitConfig.AnchorRef.authorDefault(name);
            if (!anchors.contains(anchor)) {
                anchors.add(anchor);
            }
        }
        var automatic = new OutfitConfig.AnchorRef(OutfitConfig.AnchorType.AUTO_TRAIL, null, null, null, null, null);
        if (!anchors.contains(automatic)) {
            anchors.add(automatic);
        }
        var anchorNames = new java.util.LinkedHashMap<OutfitConfig.AnchorRef, Component>();
        for (var choice : preview.outfitHierarchy().anchors(id)) {
            if (!anchors.contains(choice.anchor())) {
                anchors.add(choice.anchor());
            }
            anchorNames.put(choice.anchor(), Component.literal(choice.label()));
        }
        OutfitDraft expectedDraft = outfitDraft;
        addRenderableWidget(new LibraryDropdown(x, top + 18, width,
                () -> anchors.stream().map(anchor -> anchorNames.getOrDefault(anchor, anchorLabel(anchor))).toList(),
                () -> anchors.indexOf(expectedDraft.instance(id).anchor()), index -> {
            if (outfitDraft != expectedDraft || !id.equals(outfitDraft.selectedInstance())) {
                return;
            }
            try {
                outfitDraft.setAnchor(id, anchors.get(index));
                automaticAnchorFocus = anchors.get(index).type() == OutfitConfig.AnchorType.AUTO_TRAIL ? id : null;
                observedAutomaticTarget = null;
                pendingAnchorFocus = anchors.get(index);
                previewOutfitDraft();
            } catch (IllegalArgumentException invalid) {
                message = FiguraText.of("gui.library.failure", "OUTFIT_DRAFT_LIMIT_OR_BINDING");
            }
        }, 6));
        addRenderableWidget(new Button(x, top + 42, width, 20, text("anchor_pick"), null, button -> {
            if (outfitDraft == expectedDraft && id.equals(outfitDraft.selectedInstance())) {
                beginAnchorSelection(id);
            }
        }));
        var transform = instance.transform();
        var vectors = List.of(transform.position(), transform.rotation(), transform.scale());
        var labels = List.of("instance_position", "instance_rotation", "instance_scale");
        var fields = addRenderableWidget(new WardrobeEditorPanel(x, top + 66, width, Math.max(24, layout.height() - 68)));
        int fieldWidth = Math.max(1, (width - 24) / 3);
        for (int row = 0; row < vectors.size(); row++) {
            int y = 2 + row * 38;
            fields.add(new Label(text(labels.get(row)), 0, 0, TextUtils.Alignment.LEFT), 2, y);
            var vector = vectors.get(row);
            double[] values = {vector.x(), vector.y(), vector.z()};
            for (int axis = 0; axis < 3; axis++) {
                int component = axis;
                int property = row;
                var input = fields.add(new TextField(0, 0, fieldWidth, 20, null, ignored -> {}),
                        2 + axis * (fieldWidth + 4), y + 12);
                input.getField().setValue(Double.toString(values[axis]));
                input.getField().setResponder(value -> updateInstanceTransform(id, property, component, value, input));
            }
        }
    }

    private static Component anchorLabel(OutfitConfig.AnchorRef anchor) {
        return switch (anchor.type()) {
            case STANDARD -> Component.literal(anchor.name());
            case AUTO_TRAIL -> text("instance_auto_trail");
            case UNRESOLVED -> text("instance_unbound").copy().append(" · ").append(anchor.symbol());
            default -> text("instance_bound_target");
        };
    }

    private void updateInstanceTransform(UUID id, int property, int component, String value, TextField input) {
        if (outfitDraft == null || !id.equals(outfitDraft.selectedInstance())) {
            return;
        }
        try {
            double number = Double.parseDouble(value);
            var current = outfitDraft.instance(id).transform();
            var vector = switch (property) {
                case 0 -> current.position();
                case 1 -> current.rotation();
                case 2 -> current.scale();
                default -> throw new IllegalArgumentException("INVALID_TRANSFORM_PROPERTY");
            };
            double[] values = {vector.x(), vector.y(), vector.z()};
            values[component] = number;
            var replacement = new OutfitConfig.Vector(values[0], values[1], values[2]);
            outfitDraft.setTransform(id, new OutfitConfig.Transform(property == 0 ? replacement : current.position(),
                    property == 1 ? replacement : current.rotation(), property == 2 ? replacement : current.scale()));
            input.setBorderColour(0xffffffff);
            previewOutfitDraft();
        } catch (IllegalArgumentException invalid) {
            input.setBorderColour(0xffff5555);
            message = text("instance_transform_invalid");
        }
    }

    private void openBoneContext(top.aihmc.omnimorph.model.library.OutfitHierarchy.Target target, double x, double y) {
        if (outfitDraft == null || !draftPreviewActive || target == null || target.instanceId() == null
                || target.kind() != top.aihmc.omnimorph.model.library.OutfitHierarchy.Kind.INSTANCE
                && target.kind() != top.aihmc.omnimorph.model.library.OutfitHierarchy.Kind.INSTANCE_BONE) {
            return;
        }
        var expected = outfitDraft;
        UUID instance = target.instanceId();
        var menu = new top.aihmc.omnimorph.gui.widgets.ContextMenu();
        menu.addAction(text("instance_unmount"), null, button -> {
            if (!removed && outfitDraft == expected && expected.configuration().instances().stream()
                    .anyMatch(value -> value.instanceId().equals(instance))) {
                removeDraftInstance(instance);
            }
        });
        menu.setX((int) x);
        menu.setY((int) y);
        menu.setVisible(true);
        UIHelper.setContext(menu);
    }

    private void openAnchorActions(net.minecraft.client.gui.components.Button parent) {
        if (outfitDraft == null || !draftPreviewActive) {
            return;
        }
        if (attachingInstance != null && !validAnchorSelection()) {
            clearAnchorSelection();
        }
        var target = bones.selectedTarget();
        var menu = new top.aihmc.omnimorph.gui.widgets.ContextMenu(parent);
        menu.addAction(text(boneHelpersShown ? "helpers_hide" : "helpers_show"), text("helpers_tooltip"),
                button -> {
                    toggleBoneHelpers();
                    menu.setVisible(false);
                });
        UUID child = attachingInstance;
        long selection = attachmentSelection;
        if (child != null) {
            if (target != null && target.mountable()) {
                String key = target.kind() == top.aihmc.omnimorph.model.library.OutfitHierarchy.Kind.INSTANCE_BONE
                        ? "anchor_bind_accessory" : "anchor_bind_target";
                menu.addAction(text(key), instanceName(outfitDraft.instance(child)), button -> {
                    bindTreeTarget(child, selection, target);
                    menu.setVisible(false);
                });
            }
            menu.addAction(text("anchor_pick_cancel"), null, button -> {
                if (selection == attachmentSelection && child.equals(attachingInstance)) {
                    clearAnchorSelection();
                    selectDraftInstance(child);
                }
                menu.setVisible(false);
            });
        }
        if (child == null && target != null && target.bone() != null && target.mountable()) {
            menu.addAction(text("anchor_create"), null, button -> createVirtualAnchor(target.bone()));
        }
        if (child == null && target != null && target.anchorId() != null) {
            menu.addAction(text("anchor_edit"), null, button -> editVirtualAnchor(target.anchorId()));
        }
        if (child == null && target != null && target.editable() && target.bone() != null) {
            menu.addAction(text("bone_edit"), null, button -> editBoneOverride(target.bone()));
        }
        menu.setX(parent.getX());
        menu.setY(parent.getY() + parent.getHeight());
        menu.setVisible(true);
        UIHelper.setContext(menu);
    }

    private void clearAnchorSelection() {
        attachingInstance = null;
        attachmentDraft = null;
        attachmentSelection++;
    }

    private boolean validAnchorSelection() {
        return attachingInstance != null && attachmentDraft == outfitDraft && outfitDraft != null
                && attachingInstance.equals(outfitDraft.selectedInstance())
                && outfitDraft.configuration().instances().stream()
                        .anyMatch(instance -> attachingInstance.equals(instance.instanceId()));
    }

    private void beginAnchorSelection(UUID id) {
        if (outfitDraft == null || !draftPreviewActive || !id.equals(outfitDraft.selectedInstance())) {
            return;
        }
        outfitDraft.instance(id);
        clearAnchorSelection();
        attachingInstance = id;
        attachmentDraft = outfitDraft;
        automaticAnchorFocus = null;
        observedAutomaticTarget = null;
        pendingAnchorFocus = null;
        editingInstance = false;
        editingBone = null;
        editingVirtualAnchor = null;
        bonesShown = true;
        pane = Pane.TREE;
        syncHierarchy();
        bones.view(BoneTreeState.View.ALL);
        bones.select(-1);
        if (layout != null) {
            rebuild();
        }
    }

    private void bindTreeTarget(UUID child, long selection,
                                top.aihmc.omnimorph.model.library.OutfitHierarchy.Target target) {
        if (outfitDraft == null || attachmentDraft != outfitDraft || !child.equals(attachingInstance)
                || selection != attachmentSelection || !child.equals(outfitDraft.selectedInstance())) {
            return;
        }
        try {
            if (!target.mountable() || !preview.outfitHierarchy().targets().containsValue(target)) {
                throw new IllegalArgumentException("OUTFIT_TARGET_UNAVAILABLE");
            }
            var type = switch (target.kind()) {
                case BASE_BONE -> OutfitConfig.AnchorType.BASE_BONE;
                case INSTANCE_BONE -> OutfitConfig.AnchorType.ACCESSORY_BONE;
                case STANDARD -> OutfitConfig.AnchorType.STANDARD;
                case VIRTUAL -> OutfitConfig.AnchorType.VIRTUAL;
                default -> throw new IllegalArgumentException("OUTFIT_TARGET_UNAVAILABLE");
            };
            var parentBone = target.bone();
            if (type == OutfitConfig.AnchorType.VIRTUAL) {
                parentBone = outfitDraft.configuration().virtualAnchors().stream()
                        .filter(anchor -> target.anchorId().equals(anchor.id())).findFirst()
                        .orElseThrow(() -> new IllegalArgumentException("OUTFIT_TARGET_UNAVAILABLE")).parent();
                if (!validVirtualParent(parentBone)) {
                    throw new IllegalArgumentException("OUTFIT_TARGET_UNAVAILABLE");
                }
            }
            UUID owner = parentBone == null ? null : parentBone.instanceId();
            if (owner != null && outfitDraft.configuration().instances().stream()
                    .noneMatch(instance -> owner.equals(instance.instanceId()) && instance.enabled())) {
                throw new IllegalArgumentException("OUTFIT_TARGET_UNAVAILABLE");
            }
            var anchor = new OutfitConfig.AnchorRef(type, target.standard(), target.bone(), target.anchorId(), null, null);
            outfitDraft.setAnchor(child, anchor);
            clearAnchorSelection();
            pendingAnchorFocus = anchor;
            automaticAnchorFocus = null;
            observedAutomaticTarget = null;
            editingInstance = true;
            bonesShown = false;
            pane = Pane.INFO;
            previewOutfitDraft();
            if (layout != null) {
                rebuild();
            }
        } catch (IllegalArgumentException invalid) {
            message = text("anchor_bind_invalid");
        }
    }

    private boolean validVirtualParent(OutfitConfig.BoneRef parent) {
        return preview != null && preview.outfitHierarchy().targets().values().stream()
                .anyMatch(target -> target.mountable() && parent.equals(target.bone()));
    }

    private boolean editableBone(OutfitConfig.BoneRef bone) {
        if (outfitDraft == null || preview == null || bone.instanceId() == null) {
            return false;
        }
        boolean active = outfitDraft.configuration().instances().stream()
                .anyMatch(instance -> instance.instanceId().equals(bone.instanceId()) && instance.enabled());
        return active && preview.outfitHierarchy().targets().values().stream()
                .anyMatch(target -> target.editable() && bone.equals(target.bone()));
    }

    private void editBoneOverride(OutfitConfig.BoneRef bone) {
        if (!editableBone(bone)) {
            message = text("bone_readonly");
            return;
        }
        clearAnchorSelection();
        outfitDraft.select(bone.instanceId());
        automaticAnchorFocus = null;
        observedAutomaticTarget = null;
        editingBone = bone;
        editingVirtualAnchor = null;
        editingInstance = false;
        bonesShown = false;
        pane = Pane.INFO;
        if (layout != null) {
            rebuild();
        }
    }

    private OutfitConfig.Transform boneOverride(OutfitConfig.BoneRef bone) {
        return outfitDraft.instance(bone.instanceId()).boneOverrides().stream()
                .filter(override -> override.bone().equals(bone)).map(OutfitConfig.BoneOverride::transform)
                .findFirst().orElse(OutfitConfig.Transform.identity());
    }

    private boolean currentBoneEditor(OutfitDraft expected, OutfitConfig.BoneRef bone) {
        return outfitDraft == expected && bone.equals(editingBone)
                && bone.instanceId().equals(outfitDraft.selectedInstance()) && editableBone(bone);
    }

    private void buildBoneOverrideEditor(OutfitConfig.BoneRef bone) {
        OutfitDraft expected = outfitDraft;
        int x = layout.treeX();
        int width = layout.treeWidth();
        int top = layout.top();
        var fields = addRenderableWidget(new WardrobeEditorPanel(x, top, width, Math.max(24, layout.height() - 24)));
        Component title = instanceName(outfitDraft.instance(bone.instanceId())).copy();
        for (var segment : bone.path()) {
            title = title.copy().append(" / " + segment.name() + "[" + segment.siblingOrdinal() + "]");
        }
        fields.add(new Label(title, 0, 0, TextUtils.Alignment.LEFT), 2, 2);
        var transform = boneOverride(bone);
        var vectors = List.of(transform.position(), transform.rotation(), transform.scale());
        var labels = List.of("instance_position", "instance_rotation", "instance_scale");
        int fieldWidth = Math.max(1, (width - 24) / 3);
        var inputs = new ArrayList<TextField>();
        for (int row = 0; row < 3; row++) {
            int y = 20 + row * 38;
            fields.add(new Label(text(labels.get(row)), 0, 0, TextUtils.Alignment.LEFT), 2, y);
            var vector = vectors.get(row);
            double[] values = {vector.x(), vector.y(), vector.z()};
            for (int axis = 0; axis < 3; axis++) {
                int property = row;
                int component = axis;
                var input = fields.add(new TextField(0, 0, fieldWidth, 20, null, ignored -> {}),
                        2 + axis * (fieldWidth + 4), y + 12);
                inputs.add(input);
                input.getField().setValue(Double.toString(values[axis]));
                input.getField().setResponder(value -> {
                    if (!currentBoneEditor(expected, bone)) {
                        return;
                    }
                    try {
                        var before = boneOverride(bone);
                        var original = property == 0 ? before.position() : property == 1 ? before.rotation() : before.scale();
                        double[] updated = {original.x(), original.y(), original.z()};
                        updated[component] = Double.parseDouble(value);
                        var replacement = new OutfitConfig.Vector(updated[0], updated[1], updated[2]);
                        outfitDraft.setBoneOverride(bone.instanceId(), bone, new OutfitConfig.Transform(
                                property == 0 ? replacement : before.position(),
                                property == 1 ? replacement : before.rotation(), property == 2 ? replacement : before.scale()));
                        input.setBorderColour(0xffffffff);
                        previewOutfitDraft();
                    } catch (IllegalArgumentException invalid) {
                        input.setBorderColour(0xffff5555);
                        message = text("instance_transform_invalid");
                    }
                });
            }
        }
        fields.add(new Button(0, 0, Math.max(1, width - 18), 20, text("bone_reset"), null, button -> {
            if (!currentBoneEditor(expected, bone)) {
                return;
            }
            outfitDraft.clearBoneOverride(bone.instanceId(), bone);
            editingBone = null;
            // Prevent the reset fields' own responders from immediately recreating an identity override.
            for (int index = 0; index < inputs.size(); index++) {
                inputs.get(index).getField().setValue(index < 6 ? "0.0" : "1.0");
                inputs.get(index).setBorderColour(0xffffffff);
            }
            editingBone = bone;
            previewOutfitDraft();
        }), 2, 138);
        fields.add(new Button(0, 0, Math.max(1, width - 18), 20, text("anchor_back"), null,
                button -> switchRightPane(true)), 2, 162);
    }

    private void createVirtualAnchor(OutfitConfig.BoneRef parent) {
        if (outfitDraft == null || !validVirtualParent(parent)) {
            message = text("unavailable");
            return;
        }
        try {
            UUID id = outfitDraft.addVirtualAnchor(text("anchor_default_name").getString(), parent,
                    OutfitConfig.Transform.identity());
            previewOutfitDraft();
            editVirtualAnchor(id);
        } catch (IllegalArgumentException invalid) {
            message = text("anchor_invalid");
        }
    }

    private void editVirtualAnchor(UUID id) {
        if (outfitDraft == null || outfitDraft.configuration().virtualAnchors().stream()
                .noneMatch(anchor -> anchor.id().equals(id))) {
            return;
        }
        clearAnchorSelection();
        editingVirtualAnchor = id;
        automaticAnchorFocus = null;
        observedAutomaticTarget = null;
        editingBone = null;
        virtualRemovalArmed = null;
        editingInstance = false;
        bonesShown = false;
        pane = Pane.INFO;
        if (layout != null) {
            rebuild();
        }
    }

    private OutfitConfig.VirtualAnchor editingAnchor(UUID id) {
        if (outfitDraft == null || !id.equals(editingVirtualAnchor)) {
            throw new IllegalArgumentException("OUTFIT_ANCHOR_EDITOR_EXPIRED");
        }
        return outfitDraft.configuration().virtualAnchors().stream().filter(anchor -> anchor.id().equals(id))
                .findFirst().orElseThrow(() -> new IllegalArgumentException("OUTFIT_ANCHOR_MISSING"));
    }

    private void changeVirtualAnchor(UUID id, String name, OutfitConfig.BoneRef parent, OutfitConfig.Transform transform) {
        editingAnchor(id);
        if (!validVirtualParent(parent)) {
            throw new IllegalArgumentException("OUTFIT_ANCHOR_PARENT_UNAVAILABLE");
        }
        outfitDraft.updateVirtualAnchor(id, name, parent, transform);
        virtualRemovalArmed = null;
        previewOutfitDraft();
    }

    private void buildVirtualAnchorEditor(OutfitConfig.VirtualAnchor anchor) {
        UUID id = anchor.id();
        OutfitDraft expected = outfitDraft;
        int x = layout.treeX();
        int width = layout.treeWidth();
        int top = layout.top();
        var fields = addRenderableWidget(new WardrobeEditorPanel(x, top, width, Math.max(24, layout.height() - 24)));
        int contentWidth = Math.max(1, width - 18);
        fields.add(new Label(text("anchor_name"), 0, 0, TextUtils.Alignment.LEFT), 2, 2);
        var name = fields.add(new TextField(0, 0, contentWidth, 20, null, ignored -> {}), 2, 14);
        name.getField().setMaxLength(128);
        name.getField().setValue(anchor.name());
        name.getField().setResponder(value -> {
            if (outfitDraft != expected) {
                return;
            }
            try {
                var current = editingAnchor(id);
                changeVirtualAnchor(id, value, current.parent(), current.transform());
                name.setBorderColour(0xffffffff);
            } catch (IllegalArgumentException invalid) {
                name.setBorderColour(0xffff5555);
                message = text("anchor_invalid");
            }
        });
        var parents = new ArrayList<>(preview.outfitHierarchy().anchors(null).stream()
                .filter(choice -> choice.anchor().bone() != null).toList());
        if (parents.stream().noneMatch(choice -> anchor.parent().equals(choice.anchor().bone()))) {
            var type = anchor.parent().instanceId() == null
                    ? OutfitConfig.AnchorType.BASE_BONE : OutfitConfig.AnchorType.ACCESSORY_BONE;
            parents.addFirst(new top.aihmc.omnimorph.model.library.OutfitHierarchy.AnchorChoice(
                    text("unavailable").getString(), new OutfitConfig.AnchorRef(type, null, anchor.parent(), null, null, null)));
        }
        fields.add(new Label(text("anchor_parent"), 0, 0, TextUtils.Alignment.LEFT), 2, 40);
        fields.add(new LibraryDropdown(0, 0, contentWidth,
                () -> parents.stream().<Component>map(choice -> Component.literal(choice.label())).toList(),
                () -> {
                    for (int index = 0; index < parents.size(); index++) {
                        if (parents.get(index).anchor().bone().equals(anchorParent(id))) {
                            return index;
                        }
                    }
                    return 0;
                }, index -> {
            if (outfitDraft != expected) {
                return;
            }
            try {
                var current = editingAnchor(id);
                changeVirtualAnchor(id, current.name(), parents.get(index).anchor().bone(), current.transform());
            } catch (IllegalArgumentException invalid) {
                message = text("anchor_invalid");
            }
        }, 6), 2, 52);
        var transform = anchor.transform();
        var vectors = List.of(transform.position(), transform.rotation(), transform.scale());
        var labels = List.of("instance_position", "instance_rotation", "instance_scale");
        int fieldWidth = Math.max(1, (width - 24) / 3);
        for (int row = 0; row < 3; row++) {
            int y = 80 + row * 38;
            fields.add(new Label(text(labels.get(row)), 0, 0, TextUtils.Alignment.LEFT), 2, y);
            var vector = vectors.get(row);
            double[] values = {vector.x(), vector.y(), vector.z()};
            for (int axis = 0; axis < 3; axis++) {
                int property = row;
                int component = axis;
                var input = fields.add(new TextField(0, 0, fieldWidth, 20, null, ignored -> {}),
                        2 + axis * (fieldWidth + 4), y + 12);
                input.getField().setValue(Double.toString(values[axis]));
                input.getField().setResponder(value -> {
                    if (outfitDraft != expected) {
                        return;
                    }
                    try {
                        var current = editingAnchor(id);
                        var before = current.transform();
                        var original = property == 0 ? before.position() : property == 1 ? before.rotation() : before.scale();
                        double[] updated = {original.x(), original.y(), original.z()};
                        updated[component] = Double.parseDouble(value);
                        var replacement = new OutfitConfig.Vector(updated[0], updated[1], updated[2]);
                        changeVirtualAnchor(id, current.name(), current.parent(), new OutfitConfig.Transform(
                                property == 0 ? replacement : before.position(),
                                property == 1 ? replacement : before.rotation(), property == 2 ? replacement : before.scale()));
                        input.setBorderColour(0xffffffff);
                    } catch (IllegalArgumentException invalid) {
                        input.setBorderColour(0xffff5555);
                        message = text("anchor_invalid");
                    }
                });
            }
        }
        var remove = fields.add(new Button(0, 0, contentWidth, 20, text("anchor_remove"), null, button -> {
            if (outfitDraft == expected && id.equals(editingVirtualAnchor)) {
                removeVirtualAnchor(id, button);
            }
        }), 2, 198);
        remove.setTooltip(virtualAnchorDependents(id));
        fields.add(new Button(0, 0, contentWidth, 20, text("anchor_back"), null, button -> switchRightPane(true)), 2, 222);
    }

    private OutfitConfig.BoneRef anchorParent(UUID id) {
        if (outfitDraft == null) {
            return null;
        }
        return outfitDraft.configuration().virtualAnchors().stream().filter(anchor -> anchor.id().equals(id))
                .map(OutfitConfig.VirtualAnchor::parent).findFirst().orElse(null);
    }

    private Component virtualAnchorDependents(UUID id) {
        var names = text("anchor_affected").copy();
        for (var instance : outfitDraft.configuration().instances()) {
            if (id.equals(instance.anchor().anchorId())) {
                names.append("\n").append(instanceName(instance));
            }
        }
        return names;
    }

    private void removeVirtualAnchor(UUID id, net.minecraft.client.gui.components.Button button) {
        if (outfitDraft == null || anchorParent(id) == null) {
            return;
        }
        if (!id.equals(virtualRemovalArmed) || virtualRemovalGeneration != outfitDraft.generation()) {
            virtualRemovalArmed = id;
            virtualRemovalGeneration = outfitDraft.generation();
            message = virtualAnchorDependents(id);
            button.setMessage(text("anchor_remove_confirm"));
            return;
        }
        outfitDraft.removeVirtualAnchor(id);
        virtualRemovalArmed = null;
        editingVirtualAnchor = null;
        bonesShown = true;
        pane = Pane.TREE;
        previewOutfitDraft();
        if (layout != null) {
            rebuild();
        }
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
        if (outfitApplyAvailable()) {
            // The draft already holds the selected base and its accessories; Apply commits them as one outfit.
            applyOutfitDraft();
            return;
        }
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
        if (source == Source.ALL && tab == Tab.COLLECTION) {
            rebuild();
        }
    }

    private void selectCosmetic(LocalCosmeticInventory.Entry entry) {
        deleteCollectionArmed = null;
        selected = new Selection(entry.path(), entry.path(), entry.source() != null
                && entry.source().format() != OutfitConfig.Format.FIGURA, null,
                new WardrobeCollectionGrid.Entry(null, null, entry).name(), entry);
        selections.put(context(), selected);
        previewSelection();
        if (source == Source.ALL && tab == Tab.COLLECTION) {
            rebuild();
        }
    }

    private void selectCloud(LibraryModels.CollectionEntry entry) {
        deleteCollectionArmed = null;
        if (selected != null && entry.modelId().equals(selected.cloud())
                && entry.model() != null && entry.model().allows("preview")
                && preview != null && preview.status().stage() == LibraryPreviewSession.Stage.READY) {
            return;
        }
        if (client == null || collection == null || !collection.available()
                || entry.model() == null || !entry.model().allows("preview")) {
            message = text("unavailable");
            selectedCloudCosmetic = null;
            selected = new Selection(null, null, false, entry.modelId(), text("unavailable"));
            selections.put(context(), selected);
            preview.clear();
            syncHierarchy();
            refreshVisibility();
            if (source == Source.ALL && tab == Tab.COLLECTION) {
                rebuild();
            }
            return;
        }
        selected = new Selection(null, null, false, entry.modelId(), Component.literal(entry.model().displayName()));
        selections.put(context(), selected);
        previewSelection();
        if (source == Source.ALL && tab == Tab.COLLECTION) {
            rebuild();
        }
    }

    private void previewSelection() {
        if (tab == Tab.OUTFIT && selectedIsAvatar()) {
            selectOutfitBase();
            return;
        }
        if (tab == Tab.OUTFIT && selectedIsAccessory()) {
            selectOutfitAccessory();
            return;
        }
        setOutfitIssues(List.of());
        resourceSelectionGeneration++;
        draftPreviewGeneration++;
        draftPreviewActive = false;
        long generation = ++selectionGeneration;
        selectedCloudCosmetic = null;
        if (cloudCosmeticSelection != null) {
            cloudCosmeticSelection.close();
            cloudCosmeticSelection = null;
        }
        if (draftCloudBatch != null) {
            draftCloudBatch.close();
            draftCloudBatch = null;
        }
        message = Component.empty();
        LibraryClient requiredClient = selected != null && selected.cloud() != null ? client : null;
        bindPreview(requiredClient);
        if (previewWidget != null) {
            previewWidget.setLibraryPreview(selectionPreview() ? preview : null);
        }
        if (selected == null) {
            preview.clear();
        } else if (selected.cosmetic() != null) {
            previewCosmetic(selected.cosmetic(), generation);
        } else if (selected.cloud() != null) {
            var selectedModel = selectedCollectionModel();
            if (selectedModel != null && selectedModel.resourceKind().equals("ACCESSORY")) {
                previewCloudCosmetic(selectedModel, generation);
                syncHierarchy();
                refreshVisibility();
                return;
            }
            if (!selectedIsAvatar() && !selectedIsOutfit()) {
                preview.clear();
                message = text("unavailable");
                syncHierarchy();
                refreshVisibility();
                return;
            }
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

    private void selectOutfitBase() {
        var selection = selected;
        resourceSelectionGeneration++;
        long selectionToken = ++baseSelectionGeneration;
        var model = selectedCollectionModel();
        var transferService = transfers;
        UUID owner = FiguraMod.getLocalPlayerUUID();
        var world = minecraft.level;
        var starting = beginOutfitDraft();
        changingDraftBase = starting.thenCompose(draft -> AvatarLoadExecutor.submit(() -> {
            try {
                CompoundTag data;
                OutfitConfig.ResourceRef reference;
                if (selection.cloud() != null) {
                    var cached = transferService.cachedPreview(model);
                    data = cached.nbt();
                    reference = new OutfitConfig.ResourceRef(OutfitConfig.Source.CLOUD, null, client.origin(),
                            model.modelId(), model.revision(), cached.rawHash(), OutfitConfig.Format.valueOf(model.modelType()));
                } else {
                    var sources = new top.aihmc.omnimorph.avatar.local.LocalAvatarSource(FiguraMod.getFiguraDirectory());
                    reference = sources.describe(selection.path(), () -> removed || selectionToken != baseSelectionGeneration);
                    data = sources.prepare(reference, owner, () -> removed || selectionToken != baseSelectionGeneration);
                }
                return captureLocalDraft(data, reference, draft.configuration().showEquipment());
            } catch (Exception failure) {
                throw new java.util.concurrent.CompletionException(failure);
            }
        })).thenAcceptAsync(captured -> {
            if (removed || selectionToken != baseSelectionGeneration || minecraft.level != world
                    || !owner.equals(FiguraMod.getLocalPlayerUUID())) {
                return;
            }
            switchDraftBase(captured.configuration().base());
            draftBase = captured.base();
            draftBasePersistent = captured.persistent();
            previewOutfitDraft();
        }, minecraft::execute);
        changingDraftBase.whenComplete((ignored, failure) -> minecraft.execute(() -> {
            if (!removed && selectionToken == baseSelectionGeneration && failure != null
                    && !cacheThenRetry(failure, selection.cloud(), () -> selectionToken == baseSelectionGeneration,
                            () -> {
                                if (selected == selection) {
                                    selectOutfitBase();
                                }
                            })) {
                error(failure);
            }
        }));
    }

    /**
     * Anchors and bone references are tied to one base skeleton, so each base keeps its own accessory set locally.
     * Leaving a base remembers its set; entering a base wears its remembered set, the committed outfit when that
     * base is the committed one, or nothing for a base never dressed on this device.
     */
    private void switchDraftBase(OutfitConfig.ResourceRef base) {
        var current = outfitDraft.configuration();
        if (current.base() != null && base != null && current.base().identity().equals(base.identity())) {
            outfitDraft.setBase(base);
            return;
        }
        var outfits = LocalBaseOutfits.forDirectory(FiguraMod.getFiguraDirectory());
        outfits.remember(current);
        var remembered = outfits.get(base);
        var committed = outfitDraft.committed();
        if (remembered == null && committed.base() != null && base != null
                && committed.base().identity().equals(base.identity())) {
            remembered = committed;
        }
        outfitDraft.replaceBase(base, remembered == null ? OutfitConfig.empty() : remembered);
        clearAnchorSelection();
        bindLocalDraftResources();
    }

    /**
     * Selecting an uncached cloud entry acquires it once through the collection, then repeats the local selection.
     * Editing itself still reads only the local cache; a second miss is reported normally.
     */
    private boolean cacheThenRetry(Throwable failure, UUID model, java.util.function.BooleanSupplier current,
                                   Runnable retry) {
        while (failure instanceof java.util.concurrent.CompletionException && failure.getCause() != null) {
            failure = failure.getCause();
        }
        if (model == null || collection == null || client == null || !client.current()
                || !(failure instanceof java.io.IOException) || !"LOCAL_PREVIEW_CACHE_MISS".equals(failure.getMessage())
                || !autoCachedModels.add(model)) {
            return false;
        }
        message = text("downloading");
        cachingModel = model;
        var operation = collection.add(model);
        selectionCaching = operation;
        operation.request().future().whenComplete((value, error) -> minecraft.execute(() -> {
            if (removed || !current.getAsBoolean()) {
                return;
            }
            message = Component.empty();
            if (error != null) {
                error(error);
            } else {
                retry.run();
            }
        }));
        return true;
    }

    private void selectOutfitAccessory() {
        long generation = ++resourceSelectionGeneration;
        selectionGeneration++;
        message = Component.empty();
        selectedCloudCosmetic = null;
        if (cloudCosmeticSelection != null) {
            cloudCosmeticSelection.close();
            cloudCosmeticSelection = null;
        }
        if (selected.cloud() != null) {
            var expectedClient = client;
            var selection = selected;
            cloudCosmeticSelection = new CloudCosmeticSource(client, transfers, minecraft::execute)
                    .selectCached(selectedCollectionModel());
            cloudCosmeticSelection.future().whenComplete((prepared, failure) -> {
                if (!removed && generation == resourceSelectionGeneration && client == expectedClient
                        && expectedClient.current()) {
                    if (failure != null) {
                        if (!cacheThenRetry(failure, selection.cloud(), () -> generation == resourceSelectionGeneration,
                                () -> {
                                    if (selected == selection) {
                                        selectOutfitAccessory();
                                    }
                                })) {
                            error(failure);
                        }
                    } else {
                        selectedCloudCosmetic = prepared;
                        draftPreparedResources.put(prepared.descriptor().reference(), prepared.nbt());
                        draftCloudResources.put(prepared.descriptor().reference(), prepared.descriptor());
                    }
                }
            });
        }
        beginOutfitDraft().whenComplete((draft, failure) -> {
            if (removed || generation != resourceSelectionGeneration) {
                return;
            }
            if (failure != null) {
                error(failure);
            } else if (!draftPreviewActive) {
                previewOutfitDraft();
            }
        });
    }

    private void previewCosmetic(LocalCosmeticInventory.Entry entry, long generation) {
        preview.clear();
        if (entry.source() == null) {
            message = text("unavailable");
            return;
        }
        if (client != null && client.current() && (outfitCapabilitiesRequest == null
                || !outfitCapabilitiesRequest.future().isDone())) {
            message = text("downloading");
            return;
        }
        if (client != null && client.current() && outfitCapabilitiesRequest.future().isCompletedExceptionally()) {
            message = text("unavailable");
            return;
        }
        UUID owner = FiguraMod.getLocalPlayerUUID();
        var reference = entry.reference();
        prepareSingleCosmetic(reference, entry.source().metadata(), (resource, cancelled) -> {
            if (!resource.equals(reference)) {
                throw new java.io.IOException("COSMETIC_RESOURCE_MISMATCH");
            }
            return LocalCosmeticSource.prepare(entry.path(), owner, reference.contentHash(), cancelled).nbt();
        }, generation);
    }

    private void previewCloudCosmetic(LibraryModels.Model model, long generation) {
        preview.clear();
        if (client == null || !client.current() || !outfitLimitsReady()) {
            message = text("unavailable");
            return;
        }
        var expectedClient = client;
        var session = preview;
        cloudCosmeticSelection = new CloudCosmeticSource(client, transfers, minecraft::execute).select(model);
        cloudCosmeticSelection.future().whenComplete((prepared, failure) -> {
            if (removed || generation != selectionGeneration || preview != session
                    || client != expectedClient || !expectedClient.current()) {
                return;
            }
            if (failure != null) {
                error(failure);
                return;
            }
            selectedCloudCosmetic = prepared;
            var declaration = prepared.descriptor();
            prepareSingleCosmetic(declaration.reference(), declaration.metadata(), (reference, cancelled) -> {
                if (!reference.equals(declaration.reference())) {
                    throw new java.io.IOException("COSMETIC_RESOURCE_REFERENCE_MISMATCH");
                }
                return prepared.nbt();
            }, generation);
        });
    }

    private void prepareSingleCosmetic(OutfitConfig.ResourceRef reference, CosmeticMetadata metadata,
                                        OutfitPreparation.Resources resources, long generation) {
        if (!preview.selectVanilla()) {
            return;
        }
        UUID owner = FiguraMod.getLocalPlayerUUID();
        OutfitLimits limits = client == null ? OutfitLimits.defaults() : outfitLimits;
        try {
            var anchor = metadata.category().equals("Trail")
                    ? new OutfitConfig.AnchorRef(OutfitConfig.AnchorType.AUTO_TRAIL, null, null, null, null, null)
                    : metadata.defaultAnchor();
            var instance = new OutfitConfig.Instance(UUID.randomUUID(), reference, 1, true, anchor,
                    metadata.defaultTransform(), List.of(), Map.of(), Map.of());
            var configuration = new OutfitConfig(1, 0, null, List.of(instance), List.of(),
                    Map.of(reference.identity(), 1), false);
            OutfitCodec.encode(configuration, limits);
            preview.prepareComposition(configuration, limits, owner, UUID.randomUUID(), PermissionManager.get(owner),
                    resources).whenComplete((ready, failure) -> {
                        if (!removed && generation == selectionGeneration && failure != null) {
                            message = FiguraText.of("gui.library.failure", "COSMETIC_PREPARATION_FAILED");
                        }
                    });
        } catch (IllegalArgumentException unavailable) {
            message = text("unavailable");
        }
    }

    private boolean selectedIsAvatar() {
        if (selected == null || selected.cosmetic() != null) {
            return false;
        }
        if (selected.cloud() == null) {
            return true;
        }
        var model = selectedCollectionModel();
        return model != null && model.resourceKind().equals("AVATAR");
    }

    private boolean selectedIsOutfit() {
        var model = selectedCollectionModel();
        return model != null && model.resourceKind().equals("OUTFIT");
    }

    private boolean selectedIsAccessory() {
        if (selected == null) {
            return false;
        }
        if (selected.cosmetic() != null) {
            return true;
        }
        var model = selectedCollectionModel();
        return model != null && model.resourceKind().equals("ACCESSORY");
    }

    private record CapturedDraft(OutfitConfig configuration, CompoundTag base, boolean persistent) {}

    private java.util.concurrent.CompletableFuture<OutfitDraft> beginOutfitDraft() {
        if (changingDraftBase != null && !changingDraftBase.isDone()) {
            return changingDraftBase.thenApply(ignored -> outfitDraft);
        }
        if (outfitDraft != null) {
            return java.util.concurrent.CompletableFuture.completedFuture(outfitDraft);
        }
        if (draftStart != null && !draftStart.isDone()) {
            return draftStart;
        }
        return beginLocalOutfitDraft();
    }

    private boolean outfitApplyAvailable() {
        if (client == null || !client.current() || !outfitLimitsReady()) {
            return false;
        }
        var capabilities = outfitCapabilitiesRequest.future().getNow(null);
        var outfits = capabilities == null ? null : capabilities.outfitsV1();
        return capabilities != null && capabilities.enabled() && outfits != null
                && outfits.configuration() && outfits.apply();
    }

    /** Read the whole committed outfit, including unworn local dependencies, without changing equipment. */
    private java.util.concurrent.CompletableFuture<OutfitDraft> readServerOutfitDraft() {
        var result = new java.util.concurrent.CompletableFuture<OutfitDraft>();
        draftStart = result;
        var expectedClient = client;
        UUID owner = FiguraMod.getLocalPlayerUUID();
        var world = minecraft.level;
        java.util.function.BooleanSupplier current = () -> !removed && draftStart == result
                && client == expectedClient && expectedClient.current() && minecraft.level == world
                && owner.equals(FiguraMod.getLocalPlayerUUID());
        result.whenComplete((value, error) -> {
            if (result.isCancelled() && draftStart == result) {
                if (draftConfigurationRequest != null) {
                    draftConfigurationRequest.cancel();
                }
                if (pendingDraftSources != null) {
                    pendingDraftSources.close();
                    pendingDraftSources = null;
                }
            }
        });
        var request = expectedClient.currentOutfit();
        draftConfigurationRequest = request;
        request.future().whenComplete((configuration, failure) -> minecraft.execute(() -> {
            if (!current.getAsBoolean()) {
                result.cancel(false);
                return;
            }
            if (failure != null) {
                result.completeExceptionally(failure);
                return;
            }
            var resident = AvatarManager.getAvatarForPlayer(owner);
            // Pre-outfit accounts still have their original single-avatar path. Do not fabricate provenance.
            if (configuration.revision() == 0 && configuration.base() == null && configuration.instances().isEmpty()
                    && configuration.virtualAnchors().isEmpty() && resident != null && resident.loaded && resident.nbt != null) {
                CompoundTag data = resident.nbt;
                var source = resident.outfitSource();
                AvatarLoadExecutor.submit(() -> captureLocalDraft(data, source, configuration.showEquipment()))
                        .whenComplete((captured, error) -> minecraft.execute(() -> {
                            if (!current.getAsBoolean() || AvatarManager.getAvatarForPlayer(owner) != resident
                                    || resident.nbt != data || !java.util.Objects.equals(source, resident.outfitSource())) {
                                result.cancel(false);
                            } else if (error != null) {
                                result.completeExceptionally(error);
                            } else {
                                draftAuthority = expectedClient;
                                installCapturedDraft(captured, outfitLimits, owner);
                                result.complete(outfitDraft);
                            }
                        }));
                return;
            }
            OutfitApplyResources sources;
            try {
                sources = new OutfitApplyResources(configuration, expectedClient, transfers,
                        FiguraMod.getFiguraDirectory(), FiguraMod.getCacheDirectory(), minecraft::execute,
                        () -> !removed && client == expectedClient && expectedClient.current()
                                && minecraft.level == world && owner.equals(FiguraMod.getLocalPlayerUUID()));
            } catch (RuntimeException invalid) {
                result.completeExceptionally(invalid);
                return;
            }
            pendingDraftSources = sources;
            sources.future().whenComplete((prepared, error) -> minecraft.execute(() -> {
                if (!current.getAsBoolean()) {
                    sources.close();
                    result.cancel(false);
                    return;
                }
                if (error != null || !prepared.ready()) {
                    setOutfitIssues(prepared == null ? List.of() : prepared.resources().issues());
                    sources.close();
                    pendingDraftSources = null;
                    result.completeExceptionally(error == null
                            ? new IllegalStateException("OUTFIT_DEPENDENCY_UNAVAILABLE") : error);
                    return;
                }
                AvatarLoadExecutor.submit(() -> {
                    try {
                        return new CapturedDraft(configuration, prepared.resources().base(), true);
                    } catch (java.io.IOException unavailable) {
                        throw new java.util.concurrent.CompletionException(unavailable);
                    }
                }).thenComposeAsync(captured -> {
                    if (!current.getAsBoolean()) {
                        throw new java.util.concurrent.CancellationException("OUTFIT_DRAFT_RETIRED");
                    }
                    var recheck = expectedClient.currentOutfit();
                    draftConfigurationRequest = recheck;
                    return recheck.future().thenApply(fresh -> {
                        if (!configuration.equals(fresh)) {
                            throw new java.util.concurrent.CompletionException(new IllegalStateException("OUTFIT_CHANGED"));
                        }
                        return captured;
                    });
                }, minecraft::execute).whenCompleteAsync((captured, invalid) -> {
                    if (!current.getAsBoolean() || invalid != null) {
                        sources.close();
                        if (current.getAsBoolean()) {
                            pendingDraftSources = null;
                            result.completeExceptionally(invalid);
                        } else {
                            result.cancel(false);
                        }
                        return;
                    }
                    if (draftSourceLoader != null) {
                        draftSourceLoader.close();
                    }
                    draftSourceLoader = sources;
                    pendingDraftSources = null;
                    draftFixedResources = prepared.resources();
                    draftAuthority = expectedClient;
                    installCapturedDraft(captured, prepared.limits(), owner);
                    setOutfitIssues(List.of());
                    result.complete(outfitDraft);
                }, minecraft::execute);
            }));
        }));
        return result;
    }

    private void installCapturedDraft(CapturedDraft captured, OutfitLimits limits, UUID owner) {
        outfitDraft = new OutfitDraft(captured.configuration(), limits);
        // The committed (or currently loaded) outfit is the latest set for its base.
        LocalBaseOutfits.forDirectory(FiguraMod.getFiguraDirectory()).remember(captured.configuration());
        draftBase = captured.base();
        committedDraftBase = draftBase;
        draftBasePersistent = captured.persistent();
        committedDraftBasePersistent = draftBasePersistent;
        draftAccount = owner;
        outfitLimits = limits;
        outfitConflict = null;
    }

    private java.util.concurrent.CompletableFuture<OutfitDraft> beginLocalOutfitDraft() {
        if (outfitDraft != null) {
            return java.util.concurrent.CompletableFuture.completedFuture(outfitDraft);
        }
        if (draftStart != null && !draftStart.isDone()) {
            return draftStart;
        }
        UUID owner = FiguraMod.getLocalPlayerUUID();
        var current = loadedDraftBase(owner);
        CompoundTag data = current == null ? null : current.nbt;
        var source = current == null ? null : current.outfitSource();
        var localState = top.aihmc.omnimorph.model.outfit.LocalOutfitPreview.state(minecraft.player);
        var loadedOutfit = localState != null ? localState : AvatarManager.getLoadedOutfit(owner);
        if (loadedOutfit != null) {
            var state = loadedOutfit;
            var base = state.base();
            var captured = new CapturedDraft(state.configuration(), base == null ? null : base.nbt.copy(), true);
            for (var instance : state.configuration().instances()) {
                var avatar = state.composition().runtimeInstances().get(instance.instanceId());
                if (avatar != null && avatar.nbt != null) {
                    draftPreparedResources.put(instance.resource(), avatar.nbt.copy());
                    if (instance.resource().source() == OutfitConfig.Source.CLOUD) {
                        var metadata = CosmeticMetadata.read(avatar.nbt.getCompoundOrEmpty("metadata")
                                .getStringOr("cosmetic_json", ""));
                        draftCloudResources.put(instance.resource(), new CloudCosmeticSource.Descriptor(
                                instance.resource(), metadata, metadata.common().name));
                    }
                }
            }
            draftAuthority = client;
            installCapturedDraft(captured, outfitLimits, owner);
            bindLocalDraftResources();
            draftStart = java.util.concurrent.CompletableFuture.completedFuture(outfitDraft);
            return draftStart;
        }
        if (current != null && (!current.loaded || data == null)) {
            return java.util.concurrent.CompletableFuture.failedFuture(new IllegalStateException("OUTFIT_BASE_NOT_READY"));
        }
        var world = minecraft.level;
        OutfitLimits limits = client == null ? OutfitLimits.defaults() : outfitLimits;
        boolean showing = libraryVisibility != null ? libraryVisibility.showing()
                : localVisibility != null && localVisibility.showing();
        var result = new java.util.concurrent.CompletableFuture<OutfitDraft>();
        draftStart = result;
        AvatarLoadExecutor.submit(() -> captureLocalDraft(data, source, showing)).whenComplete((captured, failure) -> minecraft.execute(() -> {
            if (removed || result.isCancelled() || draftStart != result || minecraft.level != world
                    || !owner.equals(FiguraMod.getLocalPlayerUUID())
                    || loadedDraftBase(owner) != current
                    || current != null && (current.nbt != data || !current.loaded
                    || !java.util.Objects.equals(current.outfitSource(), source))) {
                result.cancel(false);
            } else if (failure != null) {
                result.completeExceptionally(failure);
            } else {
                draftAuthority = client;
                installCapturedDraft(captured, limits, owner);
                result.complete(outfitDraft);
            }
        }));
        return result;
    }

    /**
     * A draft rebuilt from the loaded outfit carries local accessory references but not their inventory entries.
     * Bind each exact local source (same local id and content) so names, metadata and preview reads resolve.
     */
    private boolean bindLocalDraftResources() {
        if (outfitDraft == null || observedCosmetics.isEmpty()) {
            return false;
        }
        boolean bound = false;
        for (var instance : outfitDraft.configuration().instances()) {
            var reference = instance.resource();
            if (reference.source() != OutfitConfig.Source.LOCAL || draftResources.containsKey(reference)) {
                continue;
            }
            for (var entry : observedCosmetics) {
                if (entry.source() != null && reference.equals(entry.reference())) {
                    draftResources.put(reference, entry);
                    bound = true;
                    break;
                }
            }
        }
        return bound;
    }

    private static top.aihmc.omnimorph.avatar.Avatar loadedDraftBase(UUID owner) {
        var resident = AvatarManager.getLoadedUser(owner);
        return resident == null ? top.aihmc.omnimorph.model.library.LibraryPreviewBinding.avatar(owner) : resident.getMainAvatar();
    }

    private static CapturedDraft captureLocalDraft(CompoundTag data, OutfitConfig.ResourceRef source, boolean showing) {
        CompoundTag snapshot = data == null ? null : data.copy();
        OutfitConfig.ResourceRef base = source;
        if (snapshot != null && base == null) {
            try {
                var hash = java.security.MessageDigest.getInstance("SHA-256");
                try (var output = new java.security.DigestOutputStream(java.io.OutputStream.nullOutputStream(), hash)) {
                    net.minecraft.nbt.NbtIo.writeCompressed(snapshot, output);
                }
                boolean ysm = snapshot.getCompoundOrEmpty("metadata").getStringOr("format", "").equals("ysm-native");
                boolean encrypted = ysm && top.aihmc.omnimorph.avatar.ysm.YsmSourcePayload.isEncrypted(snapshot.getCompoundOrEmpty("ysm"));
                OutfitConfig.Format format = !ysm ? OutfitConfig.Format.FIGURA
                        : encrypted ? OutfitConfig.Format.YSM_ENCRYPTED : OutfitConfig.Format.YSM_PLAIN;
                // Temporary editor identity is never sufficient for Apply or export.
                base = new OutfitConfig.ResourceRef(OutfitConfig.Source.LOCAL, UUID.randomUUID(), null, null, null,
                        java.util.HexFormat.of().formatHex(hash.digest()), format);
            } catch (java.io.IOException | java.security.NoSuchAlgorithmException failure) {
                throw new java.util.concurrent.CompletionException(failure);
            }
        }
        return new CapturedDraft(new OutfitConfig(1, 0, base, List.of(), List.of(), Map.of(), showing), snapshot,
                snapshot == null || source != null);
    }

    private void addSelectedInstance() {
        addSelectedInstance(null);
    }

    private void addQuickMountMenu(WardrobeCollectionGrid.Entry entry,
                                  top.aihmc.omnimorph.gui.widgets.ContextMenu parent) {
        var tree = preview.outfitHierarchy();
        PreviewHierarchy hierarchy;
        var anchors = new HashMap<Integer, OutfitConfig.AnchorRef>();
        if (draftPreviewActive && tree != top.aihmc.omnimorph.model.library.OutfitHierarchy.EMPTY) {
            hierarchy = tree.hierarchy();
            for (var node : hierarchy.nodes()) {
                var target = tree.target(node.id());
                if (target != null && target.mountable()) {
                    OutfitConfig.AnchorRef anchor = switch (target.kind()) {
                        case BASE_BONE -> new OutfitConfig.AnchorRef(OutfitConfig.AnchorType.BASE_BONE,
                                null, target.bone(), null, null, null);
                        case INSTANCE_BONE -> new OutfitConfig.AnchorRef(OutfitConfig.AnchorType.ACCESSORY_BONE,
                                null, target.bone(), null, null, null);
                        case STANDARD -> OutfitConfig.AnchorRef.authorDefault(target.standard());
                        case VIRTUAL -> new OutfitConfig.AnchorRef(OutfitConfig.AnchorType.VIRTUAL,
                                null, null, target.anchorId(), null, null);
                        default -> null;
                    };
                    if (anchor != null) {
                        anchors.put(node.id(), anchor);
                    }
                }
            }
        } else {
            var base = AvatarManager.getLoadedAvatar(FiguraMod.getLocalPlayerUUID());
            top.aihmc.omnimorph.model.outfit.SkeletonIndex.Binding<?> binding;
            if (base == null) {
                binding = top.aihmc.omnimorph.model.outfit.VanillaSkeletonAdapter.describe(outfitLimits);
            } else if (base.isYsmNative()) {
                binding = top.aihmc.omnimorph.model.outfit.SkeletonIndex.capture(null,
                        base.getYsmRuntime().rootParts(), top.aihmc.omnimorph.model.ysm.YsmModelPart::getName,
                        top.aihmc.omnimorph.model.ysm.YsmModelPart::getChildren, outfitLimits);
            } else {
                binding = top.aihmc.omnimorph.model.outfit.SkeletonIndex.capture(null, List.of(base.renderer.root),
                        part -> part.name, part -> part.children, outfitLimits);
            }
            var nodes = new ArrayList<PreviewHierarchy.Node>();
            var definition = binding.definition();
            for (int index = 0; index < definition.nodes().size(); index++) {
                var node = definition.nodes().get(index);
                nodes.add(new PreviewHierarchy.Node(index, node.parent(), node.name(), node.children()));
                anchors.put(index, new OutfitConfig.AnchorRef(OutfitConfig.AnchorType.BASE_BONE, null,
                        definition.reference(null, index), null, null, null));
            }
            hierarchy = new PreviewHierarchy(nodes, definition.roots(), new ArrayList<>(binding.parts()));
        }
        var menu = new top.aihmc.omnimorph.gui.widgets.ContextMenu();
        for (int root : hierarchy.roots()) {
            addQuickMountNode(menu, entry, hierarchy, anchors, root);
        }
        parent.addTab(text("quick_mount"), text("quick_mount_help"), menu);
    }

    private void addQuickMountNode(top.aihmc.omnimorph.gui.widgets.ContextMenu menu,
                                  WardrobeCollectionGrid.Entry entry, PreviewHierarchy hierarchy,
                                  Map<Integer, OutfitConfig.AnchorRef> anchors, int id) {
        var node = hierarchy.nodes().get(id);
        var anchor = anchors.get(id);
        if (node.children().isEmpty()) {
            if (anchor != null) {
                menu.addAction(Component.literal(node.name()), null, button -> quickMount(entry, anchor));
            }
            return;
        }
        var children = new top.aihmc.omnimorph.gui.widgets.ContextMenu();
        if (anchor != null) {
            children.addAction(text("quick_mount_here"), Component.literal(node.name()),
                    button -> quickMount(entry, anchor));
        }
        for (int child : node.children()) {
            addQuickMountNode(children, entry, hierarchy, anchors, child);
        }
        if (!children.getEntries().isEmpty()) {
            menu.addTab(Component.literal(node.name()), null, children);
        }
    }

    private void quickMount(WardrobeCollectionGrid.Entry entry, OutfitConfig.AnchorRef anchor) {
        if (entry.cosmetic() != null) {
            selectCosmetic(entry.cosmetic());
            addSelectedInstance(anchor);
        } else if (entry.cloud() != null) {
            selectCloud(entry.cloud());
            if (selectedCloudCosmetic != null) {
                addSelectedInstance(anchor);
            } else if (cloudCosmeticSelection != null) {
                long generation = resourceSelectionGeneration;
                cloudCosmeticSelection.future().thenAccept(prepared -> {
                    if (!removed && generation == resourceSelectionGeneration && selected != null
                            && entry.cloud().modelId().equals(selected.cloud())) {
                        addSelectedInstance(anchor);
                    }
                });
            }
        }
    }

    private void addSelectedInstance(OutfitConfig.AnchorRef requestedAnchor) {
        if (tab != Tab.OUTFIT || !selectedIsAccessory()) {
            return;
        }
        var entry = selected.cosmetic();
        var cloud = selectedCloudCosmetic;
        if ((entry != null && entry.source() == null)
                || (entry == null && (cloud == null || client == null || !client.current()))) {
            return;
        }
        var reference = entry == null ? cloud.descriptor().reference() : entry.reference();
        var metadata = entry == null ? cloud.descriptor().metadata() : entry.source().metadata();
        if (!outfitLimitsReady()) {
            message = text("unavailable");
            return;
        }
        long selection = resourceSelectionGeneration;
        beginOutfitDraft().whenComplete((draft, failure) -> {
            if (removed || selection != resourceSelectionGeneration || tab != Tab.OUTFIT) {
                return;
            }
            if (failure != null) {
                message = FiguraText.of("gui.library.failure", "OUTFIT_DRAFT_PREPARATION_FAILED");
                return;
            }
            try {
                var anchor = requestedAnchor != null ? requestedAnchor : metadata.category().equals("Trail")
                        ? new OutfitConfig.AnchorRef(OutfitConfig.AnchorType.AUTO_TRAIL, null, null, null, null, null)
                        : metadata.defaultAnchor();
                clearAnchorSelection();
                UUID added = draft.add(reference, anchor, metadata.defaultTransform());
                automaticAnchorFocus = anchor.type() == OutfitConfig.AnchorType.AUTO_TRAIL ? added : null;
                observedAutomaticTarget = null;
                pendingAnchorFocus = anchor;
                if (entry != null) {
                    draftResources.put(reference, entry);
                } else {
                    draftCloudResources.put(reference, cloud.descriptor());
                }
                previewOutfitDraft();
            } catch (IllegalArgumentException invalid) {
                message = FiguraText.of("gui.library.failure", "OUTFIT_DRAFT_LIMIT_OR_BINDING");
            }
        });
    }

    private Component instanceName(OutfitConfig.Instance instance) {
        return resourceName(instance.resource()).copy().append(" #" + instance.displayOrdinal());
    }

    private Component resourceName(OutfitConfig.ResourceRef reference) {
        var resource = draftResources.get(reference);
        var cloud = draftCloudResources.get(reference);
        if (resource != null) {
            return new WardrobeCollectionGrid.Entry(null, null, resource).name();
        }
        var metadata = draftMetadata(reference);
        return cloud != null ? Component.literal(cloud.name())
                : metadata == null ? text("unavailable") : Component.literal(metadata.common().name);
    }

    private CosmeticMetadata draftMetadata(OutfitConfig.ResourceRef reference) {
        if (draftFixedResources != null && draftFixedResources.ready() && draftFixedResources.contains(reference)) {
            return draftFixedResources.metadata(reference);
        }
        var local = draftResources.get(reference);
        if (local != null && local.source() != null) {
            return local.source().metadata();
        }
        var cloud = draftCloudResources.get(reference);
        return cloud == null ? null : cloud.metadata();
    }

    private void openCreateMenu(net.minecraft.client.gui.components.Button parent) {
        var menu = new top.aihmc.omnimorph.gui.widgets.ContextMenu(parent);
        menu.addAction(text("create_figura"), null,
                button -> minecraft.setScreen(new AvatarWizardScreen(this)));
        menu.addAction(text("create_ysm"), null, button -> minecraft.setScreen(new AvatarWizardScreen(this,
                new top.aihmc.omnimorph.wizards.YsmAvatarWizard())));
        menu.addAction(text("create_cosmetic"), null,
                button -> minecraft.setScreen(new CosmeticWizardScreen(this)));
        menu.setX(parent.getX());
        menu.setY(parent.getY() + parent.getHeight());
        menu.setVisible(true);
        UIHelper.setContext(menu);
    }

    private void openInstances(net.minecraft.client.gui.components.Button parent) {
        if (outfitDraft == null) {
            return;
        }
        var menu = new top.aihmc.omnimorph.gui.widgets.ContextMenu(parent);
        OutfitDraft expected = outfitDraft;
        var composition = preview.outfitComposition();
        if (draftPreviewActive && composition != null && composition.actionWheel().available()) {
            menu.addAction(text("cosmetic_animations"), text("cosmetic_animations_help"), button -> {
                if (outfitDraft == expected) {
                    menu.setVisible(false);
                    openCosmeticAnimations();
                }
            });
        }
        for (var instance : outfitDraft.configuration().instances()) {
            var options = new top.aihmc.omnimorph.gui.widgets.ContextMenu();
            options.addAction(text("instance_select"), null, button -> {
                if (outfitDraft == expected) {
                    selectDraftInstance(instance.instanceId());
                }
            });
            options.addAction(text(instance.enabled() ? "instance_disable" : "instance_enable"), null, button -> {
                if (outfitDraft != expected) {
                    return;
                }
                outfitDraft.select(instance.instanceId());
                outfitDraft.setEnabled(instance.instanceId(), !outfitDraft.instance(instance.instanceId()).enabled());
                previewOutfitDraft();
            });
            options.addAction(text("instance_reset"), null, button -> {
                if (outfitDraft != expected) {
                    return;
                }
                var metadata = draftMetadata(instance.resource());
                if (metadata != null) {
                    outfitDraft.select(instance.instanceId());
                    var anchor = metadata.category().equals("Trail")
                            ? new OutfitConfig.AnchorRef(OutfitConfig.AnchorType.AUTO_TRAIL, null, null, null, null, null)
                            : metadata.defaultAnchor();
                    outfitDraft.resetInstance(instance.instanceId(), anchor, metadata.defaultTransform());
                    automaticAnchorFocus = anchor.type() == OutfitConfig.AnchorType.AUTO_TRAIL ? instance.instanceId() : null;
                    observedAutomaticTarget = null;
                    pendingAnchorFocus = anchor;
                    previewOutfitDraft();
                    if (editingInstance) {
                        rebuild();
                    }
                }
            });
            options.addAction(text("instance_remove"), null, button -> {
                if (outfitDraft == expected) {
                    removeDraftInstance(instance.instanceId());
                }
            });
            Component label = instanceName(instance);
            if (!instance.enabled()) {
                label = label.copy().append(" · ").append(text("instance_disabled"));
            }
            menu.addTab(label, null, options);
        }
        menu.setX(parent.getX());
        menu.setY(parent.getY() + parent.getHeight());
        menu.setVisible(true);
        UIHelper.setContext(menu);
    }

    private void removeDraftInstance(UUID id) {
        if (id.equals(attachingInstance)) {
            clearAnchorSelection();
        }
        OutfitDraft expected = outfitDraft;
        var affected = outfitDraft.dependents(id);
        if (affected.isEmpty()) {
            outfitDraft.remove(id);
            pruneDraftResources();
            previewOutfitDraft();
            if (editingInstance && outfitDraft.selectedInstance() == null && layout != null) {
                editingInstance = false;
                rebuild();
            }
            return;
        }
        Component names = Component.empty();
        for (UUID dependent : affected) {
            names = names.copy().append(instanceName(outfitDraft.instance(dependent))).append("\n");
        }
        minecraft.setScreen(new FiguraConfirmScreen(confirmed -> {
            if (confirmed && outfitDraft == expected) {
                outfitDraft.remove(id);
                pruneDraftResources();
            }
            minecraft.setScreen(this);
            previewOutfitDraft();
        }, text("instance_remove_dependents"), names.getString(), this));
    }

    private void selectDraftInstance(UUID id) {
        if (outfitDraft == null) {
            return;
        }
        clearAnchorSelection();
        outfitDraft.select(id);
        editingInstance = true;
        editingVirtualAnchor = null;
        editingBone = null;
        bonesShown = false;
        pane = Pane.INFO;
        var anchor = outfitDraft.instance(id).anchor();
        automaticAnchorFocus = anchor.type() == OutfitConfig.AnchorType.AUTO_TRAIL ? id : null;
        observedAutomaticTarget = null;
        if (draftPreviewActive) {
            pendingAnchorFocus = null;
            syncHierarchy();
            if (automaticAnchorFocus == null) {
                bones.locate(anchor);
            } else {
                bones.select(-1);
                updateAutomaticAnchorFocus();
            }
        } else {
            pendingAnchorFocus = anchor;
            previewOutfitDraft();
        }
        if (layout != null) {
            rebuild();
        }
    }

    private void cancelOutfitDraft() {
        baseSelectionGeneration++;
        changingDraftBase = null;
        top.aihmc.omnimorph.model.outfit.LocalOutfitPreview.clear(this);
        if (applyingOutfit != null) {
            applyingOutfit.close();
            applyingOutfit = null;
        }
        if (draftStart != null && !draftStart.isDone()) {
            draftStart.cancel(false);
        }
        setOutfitIssues(List.of());
        if (outfitDraft != null) {
            clearAnchorSelection();
            editingBone = null;
            editingVirtualAnchor = null;
            virtualRemovalArmed = null;
            pendingAnchorFocus = null;
            automaticAnchorFocus = null;
            observedAutomaticTarget = null;
            outfitDraft.cancel();
            draftBase = committedDraftBase;
            draftBasePersistent = committedDraftBasePersistent;
            pruneDraftResources();
            selected = null;
            selections.put(context(), null);
            previewSelection();
            editingInstance = false;
            if (layout != null) {
                rebuild();
            }
        }
    }

    private void resetOutfitDraft() {
        if (outfitOperationBusy()) {
            return;
        }
        if (outfitDraft != null) {
            clearAnchorSelection();
            editingBone = null;
            editingVirtualAnchor = null;
            virtualRemovalArmed = null;
            pendingAnchorFocus = null;
            automaticAnchorFocus = null;
            observedAutomaticTarget = null;
            outfitDraft.reset();
            draftBase = null;
            draftBasePersistent = true;
            pruneDraftResources();
            previewOutfitDraft();
            editingInstance = false;
            if (layout != null) {
                rebuild();
            }
        }
    }

    private boolean outfitLimitsReady() {
        return client == null || client.current() && outfitCapabilitiesRequest != null
                && outfitCapabilitiesRequest.future().isDone()
                && !outfitCapabilitiesRequest.future().isCompletedExceptionally();
    }

    private boolean outfitOperationBusy() {
        return applyingOutfit != null && !applyingOutfit.future().isDone()
                || changingDraftBase != null && !changingDraftBase.isDone()
                || draftStart != null && !draftStart.isDone();
    }

    private void setOutfitIssues(List<OutfitPresetResources.Issue> issues) {
        outfitIssues = List.copyOf(issues);
        if (previewWidget != null && tab == Tab.OUTFIT) {
            previewWidget.setDependencyIssues(outfitIssues);
        }
    }

    private void applyOutfitDraft() {
        if (!outfitApplyAvailable() || outfitOperationBusy() || outfitDraft == null || !outfitDraft.dirty()
                || draftAuthority != client || outfitConflict != null || !presetBaseCanPersist() || minecraft.player == null) {
            return;
        }
        if (outfitApplications == null) {
            outfitApplications = new OutfitApplyService(client, transfers,
                    FiguraMod.getFiguraDirectory(), FiguraMod.getCacheDirectory());
        }
        var draft = outfitDraft;
        long generation = draft.generation();
        var expectedClient = client;
        applyingDraft = draft;
        applyingDraftGeneration = generation;
        setOutfitIssues(List.of());
        applyingOutfit = outfitApplications.apply(draft.configuration(), minecraft.player,
                () -> !removed && client == expectedClient && outfitDraft == draft && draft.generation() == generation);
        watchOutfitApply(applyingOutfit, applyingOutfit.future(), draft, generation);
    }

    /** The first action only submits an unsaved draft created under the current library session. */
    private String outfitPublishBlocker() {
        if (outfitOperationBusy()) {
            return "an outfit operation is still running";
        }
        if (outfitDraft == null) {
            return "there is no draft";
        }
        if (!outfitDraft.dirty()) {
            return "the draft has no unsaved changes";
        }
        if (draftAuthority != client) {
            return "the draft was created under another library session";
        }
        if (outfitConflict != null) {
            return "a conflict is still pending";
        }
        if (!presetBaseCanPersist()) {
            return "the draft base cannot be persisted";
        }
        return null;
    }

    /** Diagnostic only: one line per distinct publish state explains a disabled first action without spam. */
    private void tracePublishBlocker(String blocker, boolean apply) {
        String state = (blocker == null ? "enabled" : "disabled: " + blocker) + " (apply=" + apply
                + ", dirty=" + (outfitDraft != null && outfitDraft.dirty())
                + ", authority=" + (draftAuthority == client) + ", conflict=" + (outfitConflict != null)
                + ", basePersistent=" + presetBaseCanPersist() + ", busy=" + outfitOperationBusy() + ")";
        if (state.equals(publishStateTrace)) {
            return;
        }
        publishStateTrace = state;
        if (blocker == null) {
            FiguraMod.LOGGER.info("Outfit publish {}", state);
        } else {
            FiguraMod.LOGGER.warn("Outfit publish {}", state);
        }
    }

    private void retryOutfitDraft() {
        if (!outfitApplyAvailable() || outfitOperationBusy() || applyingOutfit == null
                || outfitDraft != applyingDraft || outfitDraft.generation() != applyingDraftGeneration
                || !applyingOutfit.future().isCompletedExceptionally() || applyingOutfit.future().isCancelled()) {
            return;
        }
        try {
            setOutfitIssues(List.of());
            watchOutfitApply(applyingOutfit, applyingOutfit.retry(), applyingDraft, applyingDraftGeneration);
        } catch (IllegalStateException retired) {
            message = text("unavailable");
        }
    }

    private void watchOutfitApply(OutfitApplyService.Operation operation,
                                  java.util.concurrent.CompletableFuture<OutfitApplyResult> completion,
                                  OutfitDraft draft, long generation) {
        message = text("draft_applying");
        completion.whenComplete((response, failure) -> minecraft.execute(() -> {
            if (removed || applyingOutfit != operation || outfitDraft != draft || draft.generation() != generation) {
                return;
            }
            if (failure != null) {
                setOutfitIssues(operation.issues());
                Throwable cause = failure;
                while (cause instanceof java.util.concurrent.CompletionException && cause.getCause() != null) {
                    cause = cause.getCause();
                }
                error(cause);
            } else if (response instanceof OutfitApplyResult.Conflict conflict) {
                outfitConflict = conflict;
                message = text("draft_conflict").copy().append(" · " + conflict.currentRevision());
            } else {
                // Equipment is already committed. A subsequent editor refresh failure cannot undo that success.
                draftAuthority = null;
                readServerOutfitDraft().whenComplete((fresh, unavailable) -> {
                    if (removed) {
                        return;
                    }
                    if (unavailable != null) {
                        message = text("draft_applied_refresh");
                    } else {
                        selected = null;
                        selections.put(context(), null);
                        draftPreviewActive = false;
                        clearAnchorSelection();
                        previewOutfitDraft();
                        message = FiguraText.of("gui.library.enabled");
                    }
                    refreshVisibility();
                });
            }
        }));
    }

    private void refreshOutfitDraft() {
        if (outfitOperationBusy()) {
            return;
        }
        if (outfitDraft != null && outfitDraft.dirty()) {
            outfitRefreshConfirmOpen = true;
            minecraft.setScreen(new FiguraConfirmScreen(confirmed -> {
                outfitRefreshConfirmOpen = false;
                minecraft.setScreen(this);
                if (confirmed) {
                    reloadOutfitDraft();
                }
            }, text("draft_refresh"), text("draft_refresh_confirm").getString(), this));
        } else {
            reloadOutfitDraft();
        }
    }

    private void reloadOutfitDraft() {
        if (outfitConflict != null) {
            // This recovery belongs to an already submitted transaction.
            reloadServerOutfitDraft();
            return;
        }
        cancelOutfitDraft();
        outfitDraft = null;
        draftStart = null;
        draftFixedResources = null;
        beginOutfitDraft().whenComplete((draft, failure) -> {
            if (!removed && failure == null) {
                previewOutfitDraft();
            } else if (!removed) {
                error(failure);
            }
        });
    }

    private void reloadServerOutfitDraft() {
        if (!outfitApplyAvailable()) {
            return;
        }
        if (applyingOutfit != null) {
            applyingOutfit.close();
            applyingOutfit = null;
        }
        message = text("downloading");
        draftPreparedResources = new java.util.concurrent.ConcurrentHashMap<>();
        readServerOutfitDraft().whenComplete((draft, failure) -> {
            if (removed) {
                return;
            }
            if (failure != null) {
                error(failure);
            } else {
                selected = null;
                selections.put(context(), null);
                draftPreviewActive = false;
                clearAnchorSelection();
                previewOutfitDraft();
            }
        });
    }

    private void closeOutfitOperations() {
        if (outfitApplications != null) {
            outfitApplications.close();
            outfitApplications = null;
        }
        applyingOutfit = null;
        if (draftConfigurationRequest != null) {
            draftConfigurationRequest.cancel();
            draftConfigurationRequest = null;
        }
        if (pendingDraftSources != null) {
            pendingDraftSources.close();
            pendingDraftSources = null;
        }
        if (draftSourceLoader != null) {
            draftSourceLoader.close();
            draftSourceLoader = null;
        }
        draftFixedResources = null;
        draftAuthority = null;
    }

    private void pruneDraftResources() {
        var retained = new java.util.HashSet<OutfitConfig.ResourceRef>();
        for (var configuration : List.of(outfitDraft.configuration(), outfitDraft.committed())) {
            for (var instance : configuration.instances()) {
                retained.add(instance.resource());
            }
        }
        draftResources.keySet().retainAll(retained);
        draftCloudResources.keySet().retainAll(retained);
    }

    private boolean discardOldAccountDraft() {
        if (draftAccount == null || draftAccount.equals(FiguraMod.getLocalPlayerUUID())) {
            return false;
        }
        closeOutfitOperations();
        setOutfitIssues(List.of());
        outfitConflict = null;
        outfitDraft = null;
        clearAnchorSelection();
        draftBase = null;
        committedDraftBase = null;
        draftBasePersistent = false;
        committedDraftBasePersistent = false;
        draftAccount = null;
        draftResources.clear();
        draftCloudResources.clear();
        draftPreparedResources = new java.util.concurrent.ConcurrentHashMap<>();
        draftIdentity = UUID.randomUUID();
        draftPreviewGeneration++;
        draftPreviewActive = false;
        editingInstance = false;
        editingBone = null;
        editingVirtualAnchor = null;
        virtualRemovalArmed = null;
        pendingAnchorFocus = null;
        automaticAnchorFocus = null;
        observedAutomaticTarget = null;
        if (preview != null) {
            preview.close();
        }
        return true;
    }

    private void previewOutfitDraft() {
        if (outfitDraft == null || preview == null) {
            return;
        }
        long generation = ++draftPreviewGeneration;
        selectionGeneration++;
        message = Component.empty();
        setOutfitIssues(List.of());
        var configuration = outfitDraft.configuration();
        var resources = Map.copyOf(draftResources);
        var fixed = draftFixedResources;
        var cachedSources = draftPreparedResources;
        UUID owner = FiguraMod.getLocalPlayerUUID();
        OutfitLimits limits = outfitLimits;
        bindPreview(null);
        var session = preview;
        boolean reuseBase = draftPreviewActive && java.util.Objects.equals(draftPreviewBase, configuration.base())
                && session.status().stage() == LibraryPreviewSession.Stage.READY;
        draftPreviewActive = true;
        draftPreviewBase = configuration.base();
        if (previewWidget != null) {
            previewWidget.setLibraryPreview(session);
        }
        java.util.concurrent.CompletableFuture<Boolean> baseReady;
        if (reuseBase) {
            baseReady = java.util.concurrent.CompletableFuture.completedFuture(true);
        } else if (configuration.base() == null) {
            baseReady = java.util.concurrent.CompletableFuture.completedFuture(session.selectVanilla());
        } else {
            baseReady = session.selectDraft(draftBase);
        }
        baseReady.whenComplete((ready, failure) -> {
            if (removed || generation != draftPreviewGeneration || session != preview) {
                return;
            }
            if (failure != null) {
                error(failure);
                return;
            }
            if (!Boolean.TRUE.equals(ready)) {
                return;
            }
            session.prepareComposition(configuration, limits, owner, draftIdentity, PermissionManager.get(owner),
                    (reference, cancelled) -> {
                        CompoundTag resident = cachedSources.get(reference);
                        if (resident != null) {
                            return resident.copy();
                        }
                        CompoundTag data;
                        if (fixed != null && fixed.ready() && fixed.contains(reference)) {
                            data = fixed.read(reference, cancelled);
                        } else {
                            var entry = resources.get(reference);
                            if (entry == null || !entry.reference().equals(reference)) {
                                throw new java.io.IOException("LOCAL_PREVIEW_CACHE_MISS");
                            }
                            data = LocalCosmeticSource.prepare(entry.path(), owner, reference.contentHash(), cancelled).nbt();
                        }
                        cachedSources.put(reference, data.copy());
                        return data;
                    }).whenComplete((complete, error) -> {
                        if (!removed && generation == draftPreviewGeneration && error != null) {
                            message = FiguraText.of("gui.library.failure", "OUTFIT_DRAFT_PREPARATION_FAILED");
                        } else if (!removed && generation == draftPreviewGeneration && Boolean.TRUE.equals(complete)) {
                            top.aihmc.omnimorph.model.outfit.LocalOutfitPreview.show(this, session);
                        }
                    });
        });
    }

    private void bindPreview(LibraryClient requiredClient) {
        if (previewClient != requiredClient || preview.status().stage() == LibraryPreviewSession.Stage.CLOSED) {
            top.aihmc.omnimorph.model.outfit.LocalOutfitPreview.clear(this);
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

    private void removeLocalOutfit(LocalAvatarFetcher.AvatarPath avatar) {
        if (tab != Tab.OUTFIT || localCandidates == null) {
            return;
        }
        removeLocalCandidate(avatar.getTheActualPathForThis());
    }

    private void removeLocalCosmetic(LocalCosmeticInventory.Entry entry) {
        if (tab == Tab.OUTFIT && localCandidates != null) {
            removeLocalCandidate(entry.path());
        }
    }

    private void removeLocalCandidate(Path path) {
        var change = localCandidates.set(path, false);
        change.whenComplete((saved, failure) -> minecraft.execute(() -> {
            if (!removed) {
                if (failure != null || !Boolean.TRUE.equals(saved)) {
                    message = text("save_failed");
                } else {
                    rebuild();
                }
            }
        }));
    }

    private void removeCloudOutfit(LibraryModels.CollectionEntry entry) {
        if (tab != Tab.OUTFIT || client == null || !client.current()
                || collection == null || !collection.available()) {
            return;
        }
        client.collectionOutfit(entry.modelId(), false).future().whenComplete((value, failure) -> {
            if (!removed) {
                if (failure != null) {
                    error(failure);
                } else {
                    collection.refresh();
                }
            }
        });
    }

    private void changeCandidate() {
        if (selectedIsOutfit()) {
            importCollectionOutfit();
            return;
        }
        if (selected == null || selected.cosmetic() != null && selected.cosmetic().source() == null) {
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

    private void importCollectionOutfit() {
        if (removed || tab != Tab.COLLECTION || client == null || !client.current()
                || collection == null || !collection.available() || collectionBusy() || !outfitLimitsReady()) {
            return;
        }
        var document = preview.configurationPreview();
        if (document == null) {
            return;
        }
        var expectedClient = client;
        var expectedPreview = preview;
        var expectedSelection = selected;
        long selection = selectionGeneration;
        UUID account = FiguraMod.getLocalPlayerUUID();
        var result = new java.util.concurrent.CompletableFuture<Boolean>();
        collectionChange = result;
        java.util.function.BooleanSupplier active = () -> !removed && tab == Tab.COLLECTION
                && client == expectedClient && expectedClient.current() && account.equals(FiguraMod.getLocalPlayerUUID())
                && selected == expectedSelection && selection == selectionGeneration && preview == expectedPreview
                && expectedPreview.configurationPreview() == document && collectionChange == result;
        beginOutfitDraft().whenComplete((draft, failure) -> {
            if (!active.getAsBoolean() || failure != null) {
                if (active.getAsBoolean() && failure != null) {
                    error(failure);
                }
                result.complete(false);
                return;
            }
            long draftGeneration = draft.generation();
            AvatarLoadExecutor.submit(() -> {
                try {
                    return document.resources().base();
                } catch (java.io.IOException unavailable) {
                    throw new java.util.concurrent.CompletionException(unavailable);
                }
            }).whenComplete((base, problem) -> minecraft.execute(() -> {
                if (!active.getAsBoolean() || outfitDraft != draft || draft.generation() != draftGeneration) {
                    result.complete(false);
                    return;
                }
                if (problem != null) {
                    error(problem);
                    result.complete(false);
                    return;
                }
                try {
                    installOutfitPreset(draft, document.configuration(), document.resources(), base);
                    switchTab(Tab.OUTFIT);
                    if (!draftPreviewActive) {
                        previewOutfitDraft();
                    }
                    result.complete(true);
                } catch (RuntimeException invalid) {
                    error(invalid);
                    result.complete(false);
                }
            }));
        });
    }

    private void enableSelection() {
        if (!selectedIsAvatar() || tab != Tab.OUTFIT || !candidate()) {
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
        if (cloudList == null && (collectionGrid == null || source == Source.LOCAL)) {
            return;
        }
        shownEntries = client == null ? offlineEntries : collection.entries();
        if (collectionGrid != null) {
            refreshCollectionCards();
            return;
        }
        cloudList.entries(source == Source.LOCAL ? List.of() : shownEntries.stream()
                        .filter(LibraryModels.CollectionEntry::inOutfit)
                        .filter(entry -> matchesPurpose(new WardrobeCollectionGrid.Entry(null, entry))).toList(),
                selected == null ? null : selected.cloud());
        refreshLocalRows();
    }

    private boolean matchesPurpose(WardrobeCollectionGrid.Entry entry) {
        return mode == Mode.ALL || entry.resourceKind().equals(mode == Mode.AVATAR ? "AVATAR" : "ACCESSORY");
    }

    private List<LocalCosmeticInventory.Entry> localCosmetics(boolean outfitOnly) {
        if (cosmetics == null || mode == Mode.AVATAR) {
            return List.of();
        }
        return cosmetics.entries().stream()
                .filter(entry -> !outfitOnly || localCandidates.contains(entry.path())).toList();
    }

    private void refreshLocalRows() {
        if (cloudList == null) {
            return;
        }
        boolean includeLocal = source != Source.CLOUD;
        Path selectedPath = selected == null ? null : selected.reference();
        cloudList.localEntries(includeLocal ? localEntries(true) : List.of(), this::selectLocal,
                this::removeLocalOutfit, selectedPath);
        cloudList.cosmetics(includeLocal ? localCosmetics(true) : List.of(), this::selectCosmetic,
                this::removeLocalCosmetic, selectedPath);
    }

    private void refreshCollectionCards() {
        if (collectionGrid == null) {
            return;
        }
        var entries = new ArrayList<WardrobeCollectionGrid.Entry>();
        if (source != Source.CLOUD) {
            for (var entry : localEntries(false)) {
                entries.add(new WardrobeCollectionGrid.Entry(entry, null));
            }
            for (var entry : localCosmetics(false)) {
                entries.add(new WardrobeCollectionGrid.Entry(null, null, entry));
            }
        }
        if (source != Source.LOCAL) {
            shownEntries = client == null ? offlineEntries : collection.entries();
            for (var entry : shownEntries) {
                var card = new WardrobeCollectionGrid.Entry(null, entry);
                if (matchesPurpose(card)) {
                    entries.add(card);
                }
            }
        }
        String selectedKey = selected == null ? null : selected.reference() != null
                ? selected.reference().toUri().toString() : selected.cloud().toString();
        collectionGrid.entries(entries, selectedKey);
    }

    private List<LocalAvatarFetcher.AvatarPath> localEntries(boolean outfitOnly) {
        if (mode == Mode.COSMETIC) {
            return List.of();
        }
        LocalAvatarFetcher.reloadAvatars();
        var result = new ArrayList<LocalAvatarFetcher.AvatarPath>();
        var pending = new ArrayDeque<>(List.copyOf(LocalAvatarFetcher.ALL_AVATARS));
        while (!pending.isEmpty()) {
            var entry = pending.removeFirst();
            if (entry instanceof LocalAvatarFetcher.FolderPath folder) {
                pending.addAll(List.copyOf(folder.getChildren()));
            } else if (!outfitOnly || localCandidates.contains(entry.getTheActualPathForThis())) {
                result.add(entry);
            }
        }
        result.sort(java.util.Comparator.comparing(LocalAvatarFetcher.AvatarPath::getName, String.CASE_INSENSITIVE_ORDER));
        return result;
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
                            state.equipmentModelRevision(), state.equipmentRevision(), true, state.showing());
                    showModel.setToggled(state.showing());
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
        while (failure instanceof java.util.concurrent.CompletionException && failure.getCause() != null) {
            failure = failure.getCause();
        }
        if (failure instanceof java.io.IOException && "LOCAL_PREVIEW_CACHE_MISS".equals(failure.getMessage())) {
            message = text("local_preview_missing");
            return;
        }
        message = FiguraText.of("gui.library.failure", failure instanceof LibraryClient.Failure typed
                ? typed.code : "LIBRARY_UNAVAILABLE");
    }

    private void updateVisibilityButton(boolean busy) {
        if (showModel == null) {
            return;
        }
        boolean pending = visibilityRequest != null && !visibilityRequest.future().isDone();
        boolean localTarget = localVisibility != null && localVisibility.canChangeVisibility();
        boolean libraryTarget = libraryVisibility != null && libraryVisibility.equipped();
        showModel.setActive(tab == Tab.OUTFIT && !parts && !busy && !pending
                && client != null && client.current() && (localTarget || libraryTarget));
    }

    @Override
    public void tick() {
        super.tick();
        preview.tick();
        refreshCosmeticSettings();
        refreshActionGroups();
        if (discardOldAccountDraft()) {
            previewSelection();
        }
        if (addInstanceButton != null) {
            boolean available = selected != null && (selected.cosmetic() != null && selected.cosmetic().source() != null
                    || selected.cloud() != null && selectedCloudCosmetic != null && client != null && client.current());
            addInstanceButton.setActive(available
                    && outfitLimitsReady() && (draftStart == null || draftStart.isDone()));
            editInstancesButton.setActive(outfitDraft != null && !outfitDraft.configuration().instances().isEmpty());
            cancelDraftButton.setActive(outfitOperationBusy() || outfitDraft != null && outfitDraft.dirty());
            resetDraftButton.setActive(outfitDraft != null && !outfitOperationBusy());
            boolean canApply = outfitApplyAvailable() && !outfitOperationBusy();
            retryDraftButton.setActive(canApply && applyingOutfit != null && outfitDraft == applyingDraft
                    && outfitDraft.generation() == applyingDraftGeneration && applyingOutfit.future().isCompletedExceptionally()
                    && !applyingOutfit.future().isCancelled());
            refreshDraftButton.setActive(!outfitOperationBusy());
        }
        if (cosmetics != null) {
            cosmetics.tick();
            if (observedCosmetics != cosmetics.entries()) {
                observedCosmetics = cosmetics.entries();
                if (selected != null && selected.cosmetic() != null) {
                    var replacement = observedCosmetics.stream()
                            .filter(entry -> entry.path().equals(selected.reference())).findFirst().orElse(null);
                    if (replacement == null) {
                        selected = null;
                        selections.put(context(), null);
                        previewSelection();
                    } else if (!replacement.equals(selected.cosmetic())) {
                        selectCosmetic(replacement);
                    }
                }
                if (localList != null && mode == Mode.ALL && !localCosmetics(true).isEmpty()) {
                    rebuild();
                }
                refreshLocalRows();
                if (bindLocalDraftResources() && draftPreviewActive) {
                    previewOutfitDraft();
                }
            }
        }
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
            selectionGeneration++;
            draftPreviewGeneration++;
            selectedCloudCosmetic = null;
            if (cloudCosmeticSelection != null) {
                cloudCosmeticSelection.close();
                cloudCosmeticSelection = null;
            }
            if (draftCloudBatch != null) {
                draftCloudBatch.close();
                draftCloudBatch = null;
            }
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
        if (collectionGrid != null && source != Source.CLOUD) {
            refreshCollectionCards();
        }
        if (cloudList != null && source != Source.CLOUD) {
            refreshLocalRows();
        }
        if (tab == Tab.OUTFIT) {
            var current = AvatarManager.getAvatarForPlayer(FiguraMod.getLocalPlayerUUID());
            // Bone observations follow the selected preview; visibility always follows actual equipment.
            if (current != visibilityAvatar || (current != null && current.nbt != visibilityNbt)) {
                visibilityAvatar = current;
                visibilityNbt = current == null ? null : current.nbt;
                refreshVisibility();
            }
            if (previewWidget != null) {
                previewWidget.setEntity(minecraft.player);
            }
        }
        boolean busy = enabling != null && !enabling.future().isDone();
        if (candidateButton != null) {
            boolean validSelection = selected == null || selected.cosmetic() == null || selected.cosmetic().source() != null;
            boolean canManageCandidate = selected == null ? tab == Tab.OUTFIT : selected.path() != null
                    || client != null && client.current() && collection.available();
            candidateButton.setActive(validSelection && canManageCandidate && !busy && !collectionBusy());
            if (selectedIsOutfit()) {
                candidateButton.setActive(candidateButton.isActive() && preview != null
                        && preview.configurationPreview() != null);
            }
            candidateButton.setMessage(text(selectedIsOutfit() ? "preset_import"
                    : selected == null && tab == Tab.OUTFIT ? "go_collection"
                    : candidate() ? "remove_outfit" : "add_outfit"));
        }
        updateVisibilityButton(busy);
        boolean uploading = NetworkStuff.getUploadProgress() != null;
        if (wasUploading && !uploading) {
            refreshVisibility();
        }
        wasUploading = uploading;
        updateMotdWidget();
        if (uploadButton != null) {
            var current = AvatarManager.getAvatarForPlayer(FiguraMod.getLocalPlayerUUID());
            boolean canPublish = selected != null ? selectedIsAvatar() && candidate() && !busy && !selectionIsCaching()
                    && (selected.path() != null || client != null && client.current())
                    : NetworkStuff.canUpload() && !AvatarManager.localUploaded
                        && current != null && current.loaded && current.nbt != null;
            // Legacy enable/upload would replace the committed outfit with one base, so outfit servers publish via Apply.
            boolean apply = outfitApplyAvailable();
            String blocker = apply ? outfitPublishBlocker() : canPublish ? null : "legacy publish is unavailable";
            uploadButton.setActive(blocker == null);
            tracePublishBlocker(blocker, apply);
        }
        if (deleteButton != null) {
            deleteButton.setActive(NetworkStuff.isConnected() && AvatarManager.localUploaded && !uploading);
        }
        syncHierarchy();
        if (attachingInstance != null && !validAnchorSelection()) {
            clearAnchorSelection();
        }
        if (bonesShown && layout != null && !renderedTreeChoices.equals(bones.viewChoices())) {
            rebuild();
        }
        updateAutomaticAnchorFocus();
        if (previewWidget != null) {
            previewWidget.setSelectedBone(previewBoneSelection());
        }
        updateCollectionButtons();
    }

    private Object previewBoneSelection() {
        if (tab != Tab.OUTFIT || !boneHelpersShown) {
            return null;
        }
        if (automaticAnchorFocus != null) {
            return validAutomaticAnchorFocus()
                    ? new top.aihmc.omnimorph.model.library.PreviewBoneHighlight.Mount(automaticAnchorFocus) : null;
        }
        var target = bones.selectedTarget();
        if (outfitDraft != null && target != null && target.instanceId() != null
                && outfitDraft.configuration().instances().stream().noneMatch(instance ->
                        instance.instanceId().equals(target.instanceId()) && instance.enabled())) {
            return null;
        }
        return bones.selectedPart();
    }

    private void toggleBoneHelpers() {
        boneHelpersShown = !boneHelpersShown;
        Object selection = previewBoneSelection();
        if (previewWidget != null) {
            previewWidget.setSelectedBone(selection);
        }
        if (preview != null) {
            preview.selectedBone(selection);
        }
    }

    private void updateAutomaticAnchorFocus() {
        if (automaticAnchorFocus == null) {
            return;
        }
        if (!validAutomaticAnchorFocus()) {
            automaticAnchorFocus = null;
            observedAutomaticTarget = null;
            bones.select(-1);
            return;
        }
        var target = preview.previewMountTarget(automaticAnchorFocus);
        if (java.util.Objects.equals(target, observedAutomaticTarget)) {
            return;
        }
        observedAutomaticTarget = target;
        if (target == null) {
            bones.select(-1);
        } else if (target.bone() == null) {
            bones.locateBaseRoot();
        } else {
            var type = target.bone().instanceId() == null
                    ? OutfitConfig.AnchorType.BASE_BONE : OutfitConfig.AnchorType.ACCESSORY_BONE;
            bones.locate(new OutfitConfig.AnchorRef(type, null, target.bone(), null, null, null));
        }
    }

    private boolean validAutomaticAnchorFocus() {
        return automaticAnchorFocus != null && outfitDraft != null && draftPreviewActive
                && automaticAnchorFocus.equals(outfitDraft.selectedInstance())
                && outfitDraft.configuration().instances().stream().anyMatch(instance ->
                        instance.instanceId().equals(automaticAnchorFocus) && instance.enabled()
                                && instance.anchor().type() == OutfitConfig.AnchorType.AUTO_TRAIL);
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
        if (cosmeticControlsOpen || cosmeticAnimationsOpen || outfitPresetsOpen || outfitRefreshConfirmOpen) {
            return;
        }
        removed = true;
        closeOutfitOperations();
        selectionGeneration++;
        visibilityGeneration++;
        draftPreviewGeneration++;
        if (draftStart != null && !draftStart.isDone()) {
            draftStart.cancel(false);
        }
        if (cloudCosmeticSelection != null) {
            cloudCosmeticSelection.close();
            cloudCosmeticSelection = null;
        }
        selectedCloudCosmetic = null;
        if (draftCloudBatch != null) {
            draftCloudBatch.close();
            draftCloudBatch = null;
        }
        if (visibilityRequest != null) {
            visibilityRequest.cancel();
        }
        if (preview != null && !preview.isExpanded()) {
            clearAnchorSelection();
            preview.selectedBone(null);
            if (previewWidget != null) {
                previewWidget.setSelectedBone(null);
            }
            if (draftPreviewActive
                    && top.aihmc.omnimorph.model.outfit.LocalOutfitPreview.uses(preview)) {
                // Closing the wardrobe ends the world preview. Keeping the draft alive in the world also kept
                // its preview-only particle scope, so trails froze and particle dynamic lights stopped working.
                top.aihmc.omnimorph.model.outfit.LocalOutfitPreview.clear(this);
            }
            preview.close();
        }
        if (equipment != null) {
            equipment.close();
        }
        if (cosmetics != null) {
            cosmetics.close();
            cosmetics = null;
        }
        if (outfitCapabilitiesRequest != null) {
            outfitCapabilitiesRequest.cancel();
            outfitCapabilitiesRequest = null;
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
