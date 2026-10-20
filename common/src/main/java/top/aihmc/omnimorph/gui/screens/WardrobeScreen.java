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
import top.aihmc.omnimorph.config.ConfigManager;
import top.aihmc.omnimorph.config.Configs;
import top.aihmc.omnimorph.gui.FiguraToast;
import top.aihmc.omnimorph.gui.screens.library.ModelLibraryAppealsScreen;
import top.aihmc.omnimorph.gui.widgets.AvatarInfoWidget;
import top.aihmc.omnimorph.gui.widgets.BackendMotdWidget;
import top.aihmc.omnimorph.gui.widgets.Button;
import top.aihmc.omnimorph.gui.widgets.EntityPreview;
import top.aihmc.omnimorph.gui.widgets.Label;
import top.aihmc.omnimorph.gui.widgets.SwitchButton;
import top.aihmc.omnimorph.gui.widgets.lists.AvatarList;
import top.aihmc.omnimorph.gui.widgets.wardrobe.VerticalWardrobeTab;
import top.aihmc.omnimorph.gui.widgets.wardrobe.BoneTreeWidget;
import top.aihmc.omnimorph.gui.widgets.wardrobe.WardrobeCloudList;
import top.aihmc.omnimorph.gui.widgets.wardrobe.WardrobeLayout;
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
    private boolean parts;
    private final Map<Context, AvatarList.View> localViews = new HashMap<>();
    private final Map<Context, WardrobeCloudList.View> cloudViews = new HashMap<>();
    private final Map<Context, Selection> selections = new HashMap<>();
    private final Map<Tab, Source> tabSources = new HashMap<>();
    private final Map<Tab, Boolean> tabModes = new HashMap<>();
    private final Map<UUID, Boolean> draftVisibility = new HashMap<>();
    private Context localContext;
    private LocalOutfitCandidates localCandidates;
    private Selection selected;
    private LibraryPreviewSession preview;
    private LibraryClient previewClient;
    private EntityPreview previewWidget;
    private EntityPreview.View previewView;
    private LibraryClient client;
    private LibraryCollectionService collection;
    private LibraryTransferService transfers;
    private LibraryEquipmentService equipment;
    private LibraryEquipmentService.Operation enabling;
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
    private final BoneTreeState bones = new BoneTreeState();
    private LibraryPreviewSession boneSession;
    private long boneRevision = -1;
    private WardrobeLayout layout;
    private AvatarList localList;
    private WardrobeCloudList cloudList;
    private Button enableButton;
    private Button candidateButton;
    private SwitchButton showModel;
    private Component message = Component.empty();

    public WardrobeScreen(Screen parentScreen) {
        super(parentScreen, FiguraText.of("gui.panels.title.wardrobe"));
    }

    private Context context() {
        return new Context(tab, source);
    }

    private void saveView() {
        if (previewWidget != null) {
            previewView = previewWidget.view();
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
        candidateButton = null;
        enableButton = null;
        showModel = null;
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
            preview.setIndependentFigura(true);
            if (selected != null) {
                previewSelection();
            } else {
                previewCurrent();
            }
        }
        layout = WardrobeLayout.of(width, height);
        addRenderableWidget(new VerticalWardrobeTab(4, 28, 66, text("outfit"), button -> switchTab(Tab.OUTFIT)));
        addRenderableWidget(new VerticalWardrobeTab(4, 98, 100, text("collection"), button -> switchTab(Tab.COLLECTION)));
        if (layout.compact()) {
            int sectionWidth = Math.max(1, (width - 36) / Pane.values().length);
            for (Pane target : Pane.values()) {
                addRenderableWidget(new Button(30 + target.ordinal() * sectionWidth, 28, sectionWidth - 2, 20,
                        text(target.name().toLowerCase(java.util.Locale.ROOT)), null, button -> {
                    pane = target;
                    rebuild();
                }));
            }
        } else {
            addRenderableOnly((gui, x, y, delta) -> gui.text(font,
                    tab == Tab.OUTFIT ? text("outfit") : text("collection"), 32, 32, 0xffffffff));
            addRenderableWidget(new Button(layout.treeX(), 28, layout.treeWidth(), 20,
                    text(pane == Pane.INFO ? "tree" : "info"), null, button -> {
                pane = pane == Pane.INFO ? Pane.TREE : Pane.INFO;
                rebuild();
            }));
        }
        if (!layout.compact() || pane == Pane.RESOURCES) {
            buildResources();
        }
        if (!layout.compact() || pane == Pane.PREVIEW) {
            buildPreview();
        }
        if (!layout.compact() || pane == Pane.TREE || pane == Pane.INFO) {
            if (pane == Pane.INFO) {
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

    private void connect() {
        try {
            LibraryClient next = NetworkStuff.libraryClient();
            if (client != next) {
                if (equipment != null) {
                    equipment.close();
                }
                client = next;
                cloudAccount = client.session().player();
                collection = NetworkStuff.wardrobeCollection();
                transfers = new LibraryTransferService(client, FiguraMod.getCacheDirectory());
                equipment = new LibraryEquipmentService(client, transfers);
                visibility = new LibraryEquipmentVisibility(client);
                collection.refresh();
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
        tabSources.put(tab, source);
        tabModes.put(tab, parts);
        tab = next;
        source = tabSources.getOrDefault(next, Source.LOCAL);
        parts = tabModes.getOrDefault(next, false);
        rebuildWidgets();
    }

    private void buildResources() {
        int x = layout.leftX();
        int w = layout.leftWidth();
        int half = w / 2;
        addRenderableWidget(new Button(x, 54, half - 2, 20,
                text(source == Source.LOCAL ? "local" : "cloud"), null, button -> {
            saveView();
            source = source == Source.LOCAL ? Source.CLOUD : Source.LOCAL;
            if (source == Source.CLOUD && collection != null) {
                collection.refresh();
            }
            rebuildWidgets();
        }));
        addRenderableWidget(new Button(x + half, 54, w - half, 20,
                text(parts ? "parts" : "avatars"), null, button -> {
            parts = !parts;
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
        int listHeight = Math.max(88, height - 140);
        if (source == Source.LOCAL) {
            localList = new AvatarList(x, 78, w, listHeight, this, this::selectLocal,
                    avatar -> tab == Tab.COLLECTION || localCandidates.contains(avatar.getTheActualPathForThis()));
            var saved = selections.get(context());
            localContext = context();
            localList.restore(localViews.get(context()), saved == null ? null : saved.reference());
            addRenderableWidget(localList);
        } else {
            var view = cloudViews.computeIfAbsent(context(), ignored -> new WardrobeCloudList.View());
            cloudList = addRenderableWidget(new WardrobeCloudList(x, 78, w, Math.max(72, listHeight - 14), view,
                    this::selectCloud, id -> client == null ? FiguraText.of("gui.library.state.offline")
                    : text(collection.cacheState(id))));
            refreshCloudRows();
            addRenderableOnly((gui, mouseX, mouseY, delta) -> UIHelper.renderScrollingText(gui,
                    client == null ? FiguraText.of("gui.library.state.offline")
                            : collection.failure().isEmpty() ? text("cloud")
                            : FiguraText.of("gui.library.failure", collection.failure()),
                    x + 2, height - 74, w - 4, 0xffffdd88));
        }
        candidateButton = addRenderableWidget(new Button(x, height - 56, w, 20, text("add_outfit"), null,
                button -> {
            if (selected == null && tab == Tab.OUTFIT) {
                switchTab(Tab.COLLECTION);
            } else {
                changeCandidate();
            }
        }));
        if (tab == Tab.COLLECTION && source == Source.CLOUD) {
            candidateButton.setWidth(w / 2 - 2);
            addRenderableWidget(new SwitchButton(x + w / 2, height - 56, w - w / 2, 20,
                    text("auto_cache"), null, button -> {
                Configs.WARDROBE_AUTO_CACHE.setValue(Boolean.toString(!Configs.WARDROBE_AUTO_CACHE.value));
                ConfigManager.saveConfig();
                if (collection != null) {
                    collection.refresh();
                }
            })).setToggled(Configs.WARDROBE_AUTO_CACHE.value);
        }
    }

    private void buildPreview() {
        int x = layout.previewX();
        int w = layout.previewWidth();
        int size = Math.max(24, Math.min(w, layout.height() - 20));
        previewWidget = new EntityPreview(x, layout.top(), w, size, size * 0.38f, -15, 30,
                Minecraft.getInstance().player, this);
        previewWidget.setLibraryPreview(preview);
        previewWidget.restoreView(previewView);
        addRenderableWidget(previewWidget);
        addRenderableOnly((gui, mouseX, mouseY, delta) -> {
            Component name = selected == null ? text("current_equipped") : text("previewing").copy()
                    .append(selected.name()).append(" · ").append(text(selected.cloud() == null ? "local" : "cloud"));
            UIHelper.renderScrollingText(gui, name, x + 4, layout.top() + size + 2, w - 8, 0xffffffff);
            Component state = preview.status().stage() == LibraryPreviewSession.Stage.FAILED
                    ? FiguraText.of("gui.library.failure", preview.status().errorCode()) : message;
            UIHelper.renderScrollingText(gui, state, x + 4, height - 98, w - 8, 0xffffdd88);
            if (!bones.selectedName().isEmpty()) {
                UIHelper.renderScrollingText(gui, text("selected_bone").copy().append(bones.selectedName()),
                        x + 4, height - 112, w - 8, 0xff88dddd);
            }
            if (AvatarManager.panic) {
                gui.text(font, FiguraText.of("gui.panic", Configs.PANIC_BUTTON.keyBind.getTranslatedKeyMessage()),
                        x + 4, layout.top() + 4, 0xffffff55);
            }
        });
        if (tab == Tab.OUTFIT) {
            enableButton = addRenderableWidget(new Button(x, height - 78, w, 20, text("enable"), null,
                    button -> enableSelection()));
            showModel = addRenderableWidget(new SwitchButton(x, height - 56, w, 20,
                    text("show_others"), null, button -> changeVisibility()));
        }
    }

    private void buildRightPane() {
        int x = layout.treeX();
        int w = layout.treeWidth();
        syncHierarchy();
        addRenderableWidget(new BoneTreeWidget(x, layout.top(), w, Math.max(64, height - 140), bones));
        addRenderableWidget(new Button(x, height - 78, w, 20, text("refresh_bones"), null, button -> {
            preview.refreshHierarchy();
            syncHierarchy();
        }));
        addRenderableOnly((gui, mouseX, mouseY, delta) -> UIHelper.renderScrollingText(gui,
                text("bones_readonly"), x + 4, height - 52, w - 8, 0xffaaaaaa));
    }

    private void syncHierarchy() {
        if (preview != boneSession || preview.hierarchyRevision() != boneRevision) {
            boneSession = preview;
            boneRevision = preview.hierarchyRevision();
            bones.replace(preview.hierarchy());
        }
    }

    private void buildInformation() {
        int x = layout.treeX();
        int w = layout.treeWidth();
        int infoHeight = Math.max(40, layout.height() / 2);
        addRenderableOnly(new AvatarInfoWidget(x, layout.top(), w, infoHeight));
        if (NetworkStuff.motd != null && layout.height() - infoHeight > 28) {
            addRenderableWidget(new BackendMotdWidget(x, layout.top() + infoHeight + 4, w,
                    layout.height() - infoHeight - 4, NetworkStuff.motd, font));
        }
    }

    private void buildToolbar() {
        int x = 30;
        int step = Math.max(1, (width - 36) / 8);
        addRenderableWidget(new Button(x, height - 28, step - 2, 20, text("upload_current"),
                FiguraText.of("gui.wardrobe.upload.tooltip"), button -> {
            var avatar = AvatarManager.getAvatarForPlayer(FiguraMod.getLocalPlayerUUID());
            if (avatar != null && NetworkStuff.canUpload() && !AvatarManager.localUploaded) {
                NetworkStuff.uploadAvatar(avatar);
            }
        }));
        addRenderableWidget(new Button(x + step, height - 28, step - 2, 20, text("reload_current"), null,
                button -> AvatarManager.reloadCloudAvatar()));
        addRenderableWidget(new Button(x + step * 2, height - 28, step - 2, 20, text("delete_current"), null,
                button -> {
            if (NetworkStuff.isConnected() && AvatarManager.localUploaded && NetworkStuff.getUploadProgress() == null) {
                NetworkStuff.deleteAvatar(null);
            }
        }));
        addRenderableWidget(new Button(x + step * 3, height - 28, step - 2, 20, text("sound"), null,
                button -> minecraft.setScreen(new SoundScreen(this))));
        addRenderableWidget(new Button(x + step * 4, height - 28, step - 2, 20, text("keybinds"), null,
                button -> minecraft.setScreen(new KeybindScreen(this))));
        addRenderableWidget(new Button(x + step * 5, height - 28, step - 2, 20,
                FiguraText.of("gui.library.my_appeals"), null, button -> {
            if (client != null && client.current()) {
                minecraft.setScreen(new ModelLibraryAppealsScreen(this, client, null));
            }
        }));
        addRenderableWidget(new Button(x + step * 6, height - 28, step - 2, 20, text("clear_current"), null,
                button -> {
            AvatarManager.loadLocalAvatar(null);
            AvatarList.selectedEntry = null;
        }));
        addRenderableWidget(new Button(x + step * 7, height - 28, step - 2, 20, FiguraText.of("gui.done"), null,
                button -> onClose()));
    }

    private void selectLocal(LocalAvatarFetcher.AvatarPath avatar) {
        selected = avatar == null ? null : new Selection(avatar.getPath(), avatar.getTheActualPathForThis(), avatar.isYsm(), null,
                Component.literal(avatar.getName()));
        selections.put(context(), selected);
        previewSelection();
    }

    private void selectCloud(LibraryModels.CollectionEntry entry) {
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
        LibraryClient requiredClient = selected != null && selected.cloud() != null ? client : null;
        bindPreview(requiredClient);
        message = Component.empty();
        if (selected == null) {
            previewCurrent();
        } else if (selected.cloud() != null) {
            if (client != null && client.current()) {
                preview.select(selected.cloud(), transfers);
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
            preview.setIndependentFigura(true);
            previewClient = requiredClient;
            if (previewWidget != null) {
                previewWidget.setLibraryPreview(preview);
            }
        }
    }

    private void previewCurrent() {
        observedAvatar = AvatarManager.getAvatarForPlayer(FiguraMod.getLocalPlayerUUID());
        preview.selectDraft(observedAvatar != null && observedAvatar.nbt != null
                ? observedAvatar.nbt.copy() : new CompoundTag());
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
        if (selected.path() != null) {
            AvatarManager.loadLocalAvatar(selected.path());
            AvatarList.selectedEntry = selected.reference();
            message = text("enabling");
        } else if (equipment != null && client.current()) {
            enabling = equipment.enable(selected.cloud(), showModel != null && showModel.isToggled());
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

    private void refreshCloudRows() {
        if (cloudList == null) {
            return;
        }
        shownEntries = client == null ? offlineEntries : collection.entries();
        cloudList.entries(shownEntries.stream().filter(entry -> tab == Tab.COLLECTION || entry.inOutfit()).toList(),
                selected == null ? null : selected.cloud());
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
        if (selected != null && selected.cloud() != null) {
            var request = visibility.read(selected.cloud());
            visibilityRequest = request;
            request.future().whenComplete((state, failure) -> {
                if (!removed && expected == visibilityGeneration && failure == null) {
                    libraryVisibility = state;
                    showModel.setToggled(state.equipped() ? state.showing()
                            : draftVisibility.getOrDefault(state.model(), false));
                }
            });
        } else if (selected == null) {
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
        } else {
            readLocalVisibility(expected);
        }
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
                draftVisibility.put(libraryVisibility.model(), showModel.isToggled());
                return; // Draft intent is passed only by the explicit enable action.
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
            draftVisibility.clear();
            offlineEntries = List.of();
            cloudAccount = FiguraMod.getLocalPlayerUUID();
        }
        if (client != null && !client.current()) {
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
        if (selected == null) {
            var current = AvatarManager.getAvatarForPlayer(FiguraMod.getLocalPlayerUUID());
            if (current != observedAvatar) {
                bindPreview(null);
                previewCurrent();
                refreshVisibility();
            }
        }
        boolean busy = enabling != null && !enabling.future().isDone();
        if (candidateButton != null) {
            candidateButton.setActive(!parts && !busy && (selected == null && tab == Tab.OUTFIT
                    || selected != null && (selected.path() != null
                    || client != null && client.current() && collection.available())));
            candidateButton.setMessage(text(selected == null && tab == Tab.OUTFIT ? "go_collection"
                    : candidate() ? "remove_outfit" : "add_outfit"));
        }
        if (enableButton != null) {
            enableButton.setActive(!parts && candidate() && !busy && preview.status().stage() == LibraryPreviewSession.Stage.READY
                    && (selected.path() != null || client != null && client.current()));
        }
        if (showModel != null) {
            boolean pending = visibilityRequest != null && !visibilityRequest.future().isDone();
            boolean localTarget = localVisibility != null && localVisibility.canChangeVisibility()
                    && (selected == null || selected.reference() != null && selected.reference().equals(AvatarList.selectedEntry));
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
        syncHierarchy();
    }

    @Override
    protected void clearWidgets() {
        saveView();
        super.clearWidgets();
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
