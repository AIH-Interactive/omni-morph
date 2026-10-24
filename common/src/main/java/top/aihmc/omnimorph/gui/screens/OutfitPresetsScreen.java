package top.aihmc.omnimorph.gui.screens;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import top.aihmc.omnimorph.avatar.AvatarLoadExecutor;
import top.aihmc.omnimorph.backend2.library.LibraryClient;
import org.figuramc.figura.gui.widgets.Button;
import org.figuramc.figura.gui.widgets.EntityPreview;
import org.figuramc.figura.gui.widgets.TextField;
import top.aihmc.omnimorph.gui.widgets.library.LibraryDropdown;
import top.aihmc.omnimorph.gui.widgets.library.LibraryRowList;
import top.aihmc.omnimorph.model.library.LibraryPreviewSession;
import top.aihmc.omnimorph.model.library.CloudOutfitPresets;
import top.aihmc.omnimorph.model.library.LibraryTransferService;
import top.aihmc.omnimorph.model.library.OutfitPresetResources;
import top.aihmc.omnimorph.model.outfit.LocalOutfitPresets;
import top.aihmc.omnimorph.model.outfit.OutfitDraft;
import top.aihmc.omnimorph.model.outfit.OutfitLimits;
import org.figuramc.figura.permissions.PermissionManager;
import org.figuramc.figura.utils.FiguraText;
import org.figuramc.figura.utils.ui.UIHelper;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.function.Consumer;

import org.figuramc.figura.gui.screens.AbstractPanelScreen;
import org.figuramc.figura.gui.screens.WardrobeScreen;
/** Local preset documents and an independent private preview. Import edits the retained wardrobe draft only. */
public final class OutfitPresetsScreen extends AbstractPanelScreen {
    private final WardrobeScreen wardrobe;
    private final OutfitDraft draft;
    private final long draftGeneration;
    private final UUID account;
    private final UUID previewIdentity = UUID.randomUUID();
    private final Path directory;
    private final OutfitLimits limits;
    private final LibraryClient client;
    private final LibraryTransferService transfers;
    private final LocalOutfitPresets store;
    private final CloudOutfitPresets cloudStore;
    private final LibraryPreviewSession preview;
    private final Object world;
    private List<LocalOutfitPresets.Item> items = List.of();
    private LocalOutfitPresets.Preset selected;
    private UUID selectedId;
    private String name = "";
    private Component message = Component.empty();
    private LibraryDropdown selector;
    private LibraryDropdown storage;
    private TextField nameField;
    private LibraryRowList issues;
    private Button saveNew;
    private Button update;
    private Button refresh;
    private Button retry;
    private Button importPreset;
    private CompletableFuture<?> work;
    private OutfitPresetResources dependencies;
    private LibraryClient.Request<?> cloudRequest;
    private CloudOutfitPresets.Save cloudSave;
    private OutfitPresetResources.Prepared prepared;
    private CompoundTag preparedBase;
    private volatile long generation;
    private volatile boolean closed;
    private boolean initialized;
    private boolean busy;
    private boolean previewReady;
    private boolean returning;
    private boolean cloud;

    public OutfitPresetsScreen(WardrobeScreen wardrobe, OutfitDraft draft, UUID account, Path directory,
                               OutfitLimits limits, LibraryClient client, LibraryTransferService transfers) {
        super(wardrobe, text("outfit_presets"));
        this.wardrobe = wardrobe;
        this.draft = draft;
        draftGeneration = draft.generation();
        this.account = account;
        this.directory = directory;
        this.limits = limits;
        this.client = client;
        this.transfers = transfers;
        store = new LocalOutfitPresets(directory);
        cloudStore = client == null ? null : new CloudOutfitPresets(client, limits);
        preview = new LibraryPreviewSession();
        world = net.minecraft.client.Minecraft.getInstance().level;
    }

    private static Component text(String key) {
        return FiguraText.of("gui.wardrobe." + key);
    }

    private boolean active() {
        return !closed && net.minecraft.client.Minecraft.getInstance().level == world
                && wardrobe.outfitPresetsActive(draft, draftGeneration, account);
    }

    private int modelWidth() {
        return Math.max(80, Math.min(220, width / 3));
    }

    @Override
    protected void init() {
        super.init();
        removeWidget(panels);
        int left = modelWidth() + 12;
        int area = Math.max(1, width - left - 12);
        int half = Math.max(1, (area - 4) / 2);
        var model = new EntityPreview(8, 34, modelWidth() - 12, Math.max(24, height - 76),
                Math.min(modelWidth() * .35f, Math.max(24, height - 76) * .35f), 0, 0, preview.entity(), this);
        model.setLibraryPreview(preview);
        addRenderableWidget(model);
        int storageWidth = Math.max(1, Math.min(88, area / 3));
        storage = addRenderableWidget(new LibraryDropdown(left, 34, storageWidth,
                () -> List.of(text("local"), text("preset_personal_cloud")), () -> cloud ? 1 : 0, this::changeStorage));
        storage.setTooltip(text("preset_storage_help"));
        selector = addRenderableWidget(new LibraryDropdown(left + storageWidth + 4, 34, Math.max(1, area - storageWidth - 4), this::choices,
                this::selectedIndex, this::select, 6));
        nameField = addRenderableWidget(new TextField(left, 68, area, 20, TextField.HintType.ANY, value -> {
            name = value;
            updateButtons();
        }));
        nameField.getField().setMaxLength(cloud ? 96 : 80);
        nameField.getField().setValue(name);
        saveNew = addRenderableWidget(new Button(left, 94, half, 20, text("preset_save_new"),
                text("preset_save_help"), button -> save(false)));
        update = addRenderableWidget(new Button(left + half + 4, 94, half, 20, text("preset_update"),
                text("preset_update_help"), button -> save(true)));
        refresh = addRenderableWidget(new Button(left, 118, half, 20, text("preset_refresh"), null,
                button -> refresh(selectedId)));
        retry = addRenderableWidget(new Button(left + half + 4, 118, half, 20, text("preset_preview"), null,
                button -> retry()));
        issues = addRenderableWidget(new LibraryRowList(left, 146, area, Math.max(24, height - 190)));
        importPreset = addRenderableWidget(new Button(left, height - 28, half, 20, text("preset_import"),
                text("preset_import_help"), button -> importSelected()));
        addRenderableWidget(new Button(left + half + 4, height - 28, half, 20,
                Component.translatable("gui.done"), null, button -> onClose()));
        showIssues();
        updateButtons();
        if (!initialized) {
            initialized = true;
            refresh(null);
        }
    }

    private List<Component> choices() {
        var choices = new ArrayList<Component>();
        choices.add(text("preset_choose"));
        for (var item : items) {
            choices.add(Component.literal(item.name()).append(item.error().isEmpty() ? "" : " · " + item.error()));
        }
        return choices;
    }

    private boolean cloudAvailable() {
        return client != null && client.current() && account.equals(client.session().player());
    }

    private void changeStorage(int selected) {
        if (!active() || busy || selected < 0 || selected > 1 || cloud == (selected == 1)) {
            return;
        }
        cloud = selected == 1;
        selectedId = null;
        this.selected = null;
        items = List.of();
        name = "";
        nameField.getField().setMaxLength(cloud ? 96 : 80);
        nameField.getField().setValue(name);
        refresh(null);
    }

    private int selectedIndex() {
        for (int index = 0; index < items.size(); index++) {
            if (items.get(index).id().equals(selectedId)) {
                return index + 1;
            }
        }
        return 0;
    }

    private void select(int index) {
        if (!active() || busy) {
            return;
        }
        if (index == 0) {
            clearSelection();
            selectedId = null;
            selected = null;
            message = Component.empty();
            updateButtons();
        } else if (index <= items.size()) {
            load(items.get(index - 1).id());
        }
    }

    private void refresh(UUID selection) {
        if (!active() || busy) {
            return;
        }
        clearSelection();
        Consumer<List<LocalOutfitPresets.Item>> accept = values -> {
            items = values;
            if (selection != null && items.stream().anyMatch(item -> item.id().equals(selection))) {
                load(selection);
            } else {
                selectedId = null;
                selected = null;
                updateButtons();
            }
        };
        if (cloud) {
            if (!cloudAvailable()) {
                failed(new IllegalStateException("LIBRARY_UNAVAILABLE"));
                return;
            }
            var request = cloudStore.list();
            cloudRequest = request;
            remote(request.future(), values -> accept.accept(values.stream()
                    .map(value -> new LocalOutfitPresets.Item(value.id(), value.revision(), value.name(), "")).toList()));
        } else {
            run(() -> store.list(limits), accept);
        }
    }

    private void load(UUID id) {
        if (!active() || busy || id == null) {
            return;
        }
        clearSelection();
        selected = null;
        selectedId = id;
        Consumer<LocalOutfitPresets.Preset> accept = value -> {
            selected = value;
            name = value.name();
            nameField.getField().setValue(name);
            resolve(value);
        };
        if (cloud) {
            if (!cloudAvailable()) {
                failed(new IllegalStateException("LIBRARY_UNAVAILABLE"));
                return;
            }
            var request = cloudStore.read(id);
            cloudRequest = request;
            remote(request.future(), value -> accept.accept(view(value)));
        } else {
            run(() -> store.read(id, limits), accept);
        }
    }

    /** The page uses one immutable view shape; storage scope is selected separately and never inferred from a UUID. */
    private static LocalOutfitPresets.Preset view(CloudOutfitPresets.Preset preset) {
        return new LocalOutfitPresets.Preset(preset.id(), preset.revision(), preset.name(), preset.configuration());
    }

    private void resolve(LocalOutfitPresets.Preset preset) {
        long ticket = ++generation;
        busy = true;
        message = text("preset_preparing");
        updateButtons();
        dependencies = new OutfitPresetResources(preset.configuration(), limits, account, directory, client,
                transfers, minecraft::execute, () -> active() && ticket == generation);
        dependencies.future().whenComplete((result, failure) -> {
            if (!active() || ticket != generation) {
                return;
            }
            if (failure != null) {
                failed(failure);
                return;
            }
            prepared = result;
            showIssues();
            if (!result.ready()) {
                busy = false;
                message = text("preset_dependencies_failed");
                updateButtons();
                return;
            }
            // Base copying and native parsing both run on the existing model worker.
            work = AvatarLoadExecutor.submit(() -> {
                try {
                    return result.base();
                } catch (java.io.IOException invalid) {
                    throw new CompletionException(invalid);
                }
            }).whenComplete((base, invalid) -> minecraft.execute(() -> {
                if (!active() || ticket != generation) {
                    return;
                }
                if (invalid != null) {
                    failed(invalid);
                    return;
                }
                preparedBase = base;
                var baseReady = base == null ? CompletableFuture.completedFuture(preview.selectVanilla())
                        : preview.selectDraft(() -> base.copy());
                baseReady.whenComplete((loaded, error) -> {
                    if (!active() || ticket != generation) {
                        return;
                    }
                    if (error != null || !Boolean.TRUE.equals(loaded)) {
                        failed(error == null ? new IllegalStateException(preview.status().errorCode()) : error);
                        return;
                    }
                    preview.prepareComposition(preset.configuration(), limits, account, previewIdentity,
                            PermissionManager.get(account), result::read).whenComplete((complete, problem) -> {
                        if (active() && ticket == generation) {
                            busy = false;
                            previewReady = Boolean.TRUE.equals(complete) && result.ready();
                            if (problem != null || !previewReady) {
                                failed(problem == null ? new IllegalStateException("OUTFIT_PRESET_RETIRED") : problem);
                            } else {
                                message = text("preset_ready");
                                updateButtons();
                            }
                        }
                    });
                });
            }));
        });
    }

    private void save(boolean replace) {
        if (!active() || busy || name.isBlank() || !wardrobe.presetBaseCanPersist() || replace && selected == null) {
            return;
        }
        UUID id = replace ? selected.id() : UUID.randomUUID();
        long revision = replace ? selected.revision() : 0;
        var configuration = draft.configuration();
        String label = name;
        clearSelection();
        if (cloud) {
            if (!cloudAvailable()) {
                failed(new IllegalStateException("LIBRARY_UNAVAILABLE"));
                return;
            }
            try {
                cloudSave = cloudStore.save(id, revision, label, configuration);
                remote(cloudSave.future(), saved -> refresh(saved.id()));
            } catch (Exception failure) {
                failed(failure);
            }
        } else {
            run(() -> store.save(id, revision, label, configuration, limits), saved -> refresh(saved.id()));
        }
    }

    private void retry() {
        if (!active() || busy) {
            return;
        }
        if (cloud && cloudSave != null && cloudSave.future().isCompletedExceptionally() && cloudAvailable()) {
            try {
                remote(cloudSave.retry(), saved -> refresh(saved.id()));
            } catch (RuntimeException failure) {
                failed(failure);
            }
        } else {
            load(selectedId);
        }
    }

    private void importSelected() {
        if (!active() || busy || cloud && !cloudAvailable() || !previewReady
                || selected == null || prepared == null || !prepared.ready()) {
            return;
        }
        try {
            wardrobe.importOutfitPreset(draft, draftGeneration, account, selected.configuration(), prepared, preparedBase);
            onClose();
        } catch (Exception failure) {
            failed(failure);
        }
    }

    private void showIssues() {
        if (issues == null) {
            return;
        }
        var rows = new ArrayList<LibraryRowList.Row>();
        if (prepared != null && selected != null) {
            for (var issue : prepared.issues()) {
                if (issue.base()) {
                    addIssue(rows, text("preset_base"), issue.code());
                }
                for (UUID id : issue.instances()) {
                    var instance = selected.configuration().instances().stream()
                            .filter(value -> value.instanceId().equals(id)).findFirst().orElseThrow();
                    addIssue(rows, text("preset_instance").copy().append(" #" + instance.displayOrdinal() + " · " + id), issue.code());
                }
            }
        }
        issues.rows(rows);
    }

    private static void addIssue(List<LibraryRowList.Row> rows, Component owner, String code) {
        Component label = owner.copy().append(" · ").append(FiguraText.of("gui.library.failure", code));
        rows.add(new LibraryRowList.Row(label, label, true, () -> {}));
    }

    @FunctionalInterface
    private interface Read<T> {
        T run() throws Exception;
    }

    private <T> void run(Read<T> read, Consumer<T> accept) {
        long ticket = ++generation;
        busy = true;
        message = text("preset_preparing");
        updateButtons();
        work = AvatarLoadExecutor.submit(() -> {
            try {
                return read.run();
            } catch (Exception failure) {
                throw new CompletionException(failure);
            }
        }).whenComplete((value, failure) -> minecraft.execute(() -> {
            if (!active() || ticket != generation) {
                return;
            }
            busy = false;
            if (failure != null) {
                failed(failure);
            } else {
                message = Component.empty();
                accept.accept(value);
                updateButtons();
            }
        }));
    }

    private <T> void remote(CompletableFuture<T> request, Consumer<T> accept) {
        long ticket = ++generation;
        busy = true;
        message = text("preset_preparing");
        updateButtons();
        // LibraryClient already delivers completions on the client thread after checking the authenticated session.
        work = request.whenComplete((value, failure) -> {
            if (!active() || ticket != generation) {
                return;
            }
            cloudRequest = null;
            busy = false;
            if (failure != null) {
                failed(failure);
            } else {
                message = Component.empty();
                accept.accept(value);
                updateButtons();
            }
        });
    }

    private void failed(Throwable failure) {
        busy = false;
        previewReady = false;
        preview.clear();
        Throwable cause = failure;
        while (cause instanceof CompletionException && cause.getCause() != null) {
            cause = cause.getCause();
        }
        String code = cause instanceof LibraryClient.Failure library ? library.code
                : cause.getMessage() == null ? "OUTFIT_PRESET_FAILED" : cause.getMessage();
        message = FiguraText.of("gui.library.failure", code);
        updateButtons();
    }

    private void clearSelection() {
        generation++;
        if (cloudRequest != null) {
            cloudRequest.cancel();
            cloudRequest = null;
        }
        if (cloudSave != null) {
            cloudSave.close();
            cloudSave = null;
        }
        if (dependencies != null) {
            dependencies.close();
            dependencies = null;
        }
        prepared = null;
        preparedBase = null;
        previewReady = false;
        preview.clear();
        showIssues();
    }

    private void updateButtons() {
        if (saveNew == null) {
            return;
        }
        boolean enabled = active() && !busy;
        storage.setActive(enabled);
        enabled = enabled && (!cloud || cloudAvailable());
        boolean save = enabled && !name.isBlank() && wardrobe.presetBaseCanPersist() && limits.maxPresets() > 0;
        selector.setActive(enabled);
        nameField.setEnabled(enabled);
        saveNew.setActive(save);
        update.setActive(save && selected != null);
        refresh.setActive(enabled);
        boolean retrySave = cloudSave != null && cloudSave.future().isCompletedExceptionally();
        retry.setMessage(text(retrySave ? "preset_retry_save" : "preset_preview"));
        retry.setTooltip(retrySave ? text("preset_retry_save_help") : null);
        retry.setActive(enabled && (retrySave || selectedId != null));
        importPreset.setActive(enabled && previewReady && prepared != null && prepared.ready());
    }

    @Override
    public void tick() {
        super.tick();
        if (!active()) {
            onClose();
            return;
        }
        preview.tick();
        if (cloud && !cloudAvailable() && (busy || previewReady)) {
            clearSelection();
            failed(new IllegalStateException("OUTFIT_PRESET_RETIRED"));
        }
        if (prepared != null && previewReady && !prepared.ready()) {
            failed(new IllegalStateException("OUTFIT_PRESET_RETIRED"));
        }
    }

    @Override
    public void renderOverlays(GuiGraphicsExtractor gui, int mouseX, int mouseY, float delta) {
        gui.text(font, getTitle(), 12, 12, 0xffffffff);
        gui.text(font, text("preset_name"), modelWidth() + 12, 57, 0xffd3e7e1);
        Component status = !message.getString().isEmpty() ? message : text(wardrobe.presetBaseCanPersist()
                ? "preset_help" : "preset_base_unresolved");
        UIHelper.renderScrollingText(gui, status, 12, height - 42, Math.max(1, width - 24), 0xffd3e7e1);
        super.renderOverlays(gui, mouseX, mouseY, delta);
    }

    @Override
    public void onClose() {
        returning = true;
        close();
        wardrobe.closeOutfitPresets(true);
        minecraft.setScreen(wardrobe);
    }

    private void close() {
        closed = true;
        clearSelection();
        preview.close();
    }

    @Override
    public void removed() {
        super.removed();
        if (!returning && !preview.isExpanded()) {
            close();
            wardrobe.closeOutfitPresets(false);
        }
    }

    public void previewAbandoned() {
        close();
        wardrobe.closeOutfitPresets(false);
    }
}
