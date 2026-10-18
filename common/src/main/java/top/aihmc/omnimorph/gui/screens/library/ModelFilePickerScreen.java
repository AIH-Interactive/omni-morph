package top.aihmc.omnimorph.gui.screens.library;

import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import top.aihmc.omnimorph.avatar.local.LocalAvatarFetcher;
import top.aihmc.omnimorph.backend2.library.LibraryClient;
import top.aihmc.omnimorph.gui.screens.AbstractPanelScreen;
import top.aihmc.omnimorph.gui.screens.ModelLibraryScreen;
import top.aihmc.omnimorph.gui.widgets.Button;
import top.aihmc.omnimorph.gui.widgets.TextField;
import top.aihmc.omnimorph.gui.widgets.library.LibraryRowList;
import top.aihmc.omnimorph.utils.FiguraText;
import top.aihmc.omnimorph.utils.ui.UIHelper;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

/** A bounded asynchronous local file chooser built from the existing Figura widgets. */
public final class ModelFilePickerScreen extends AbstractPanelScreen {
    public enum Kind { MODEL, IMAGE }
    private record Item(Path path, boolean directory) {}
    private record Listing(List<Item> items, boolean truncated) {}
    private final LibraryClient client;
    private final Kind kind;
    private final Consumer<Path> selected;
    private Path directory;
    private Listing listing;
    private LibraryClient.Request<Listing> request;
    private LibraryRowList rows;
    private Component status = Component.empty();
    private long generation;
    private boolean closed;

    public ModelFilePickerScreen(Screen parent, LibraryClient client, Path directory, Kind kind,
                                 Consumer<Path> selected) {
        super(parent, FiguraText.of("gui.library.file_picker"));
        this.client = client;
        this.directory = (kind == Kind.MODEL ? LocalAvatarFetcher.getLocalAvatarDirectory() : directory)
                .toAbsolutePath().normalize();
        this.kind = kind;
        this.selected = selected;
    }

    @Override
    public Class<? extends Screen> getSelectedPanel() {
        return ModelLibraryScreen.class;
    }

    @Override
    protected void init() {
        super.init();
        if (width < 300 || height < 220) {
            addRenderableOnly((gui, x, y, delta) -> UIHelper.renderCenteredScrollingText(gui,
                    FiguraText.of("gui.library.scale"), 8, height / 2, width - 16, 20, 0xffffffff));
            addRenderableWidget(new Button(width - 84, height - 24, 80, 20,
                    FiguraText.of("gui.done"), null, button -> onClose()));
            return;
        }
        if (kind == Kind.MODEL) {
            addRenderableOnly((gui, x, y, delta) -> UIHelper.renderScrollingText(gui,
                    Component.literal(directory.toString()), 8, 36, width - 16, 0xffbac6d8));
        } else {
            var path = addRenderableWidget(new TextField(8, 30, Math.max(80, width - 104), 20,
                    TextField.HintType.FOLDER_PATH, ignored -> {}));
            path.getField().setValue(directory.toString());
            addRenderableWidget(new Button(width - 92, 30, 40, 20, FiguraText.of("gui.library.open"), null, button -> {
                try {
                    navigate(Path.of(path.getField().getValue()));
                } catch (RuntimeException invalid) {
                    status = FiguraText.of("gui.library.file_error");
                }
            }));
            addRenderableWidget(new Button(width - 48, 30, 40, 20, Component.literal(".."), null, button -> {
                if (directory.getParent() != null) {
                    navigate(directory.getParent());
                }
            }));
        }
        rows = addRenderableWidget(new LibraryRowList(8, 54, width - 16, Math.max(32, height - 104)));
        addRenderableOnly((gui, x, y, delta) -> UIHelper.renderScrollingText(gui, status,
                8, height - 46, width - 16, 0xffeeeeee));
        addRenderableWidget(new Button(width - 84, height - 24, 80, 20, FiguraText.of("gui.done"),
                null, button -> onClose()));
        if (listing == null) {
            load();
        } else {
            showListing();
        }
    }

    private void navigate(Path target) {
        if (kind == Kind.MODEL) {
            return;
        }
        directory = target.toAbsolutePath().normalize();
        listing = null;
        generation++;
        if (request != null) {
            request.cancel();
        }
        rebuildWidgets();
    }

    private void load() {
        long current = ++generation;
        if (request != null) {
            request.cancel();
        }
        status = FiguraText.of("gui.library.state.loading");
        Path source = directory;
        request = client.submit(LibraryClient.Lane.CONTROL, exchange -> {
            try (var paths = Files.list(source)) {
                var entries = paths.limit(4097).toList();
                var items = new java.util.ArrayList<Item>();
                for (Path path : entries.stream().limit(4096).toList()) {
                    exchange.requireCurrent();
                    boolean folder = Files.isDirectory(path);
                    if (kind == Kind.MODEL ? Files.isRegularFile(path) && accepts(path, kind)
                            : folder || accepts(path, kind)) {
                        items.add(new Item(path, folder));
                    }
                }
                items.sort(Comparator.comparing(Item::directory).reversed()
                        .thenComparing(item -> item.path().getFileName().toString(), String.CASE_INSENSITIVE_ORDER));
                return new Listing(List.copyOf(items), entries.size() > 4096);
            }
        });
        request.future().whenComplete((value, failure) -> {
            if (closed || current != generation) {
                return;
            }
            if (failure != null) {
                status = FiguraText.of("gui.library.file_error");
            } else {
                listing = value;
                String hint = kind == Kind.MODEL
                        ? value.truncated() ? "gui.library.model_file_limit" : "gui.library.model_file_hint"
                        : value.truncated() ? "gui.library.file_limit" : "gui.library.file_hint";
                status = FiguraText.of(hint);
                showListing();
            }
        });
    }

    private void showListing() {
        rows.rows(listing.items().stream().map(item -> new LibraryRowList.Row(
                Component.literal((item.directory() ? "[+] " : "") + item.path().getFileName()),
                Component.literal(item.path().toString()), true, () -> {
                    if (item.directory()) {
                        navigate(item.path());
                    } else {
                        minecraft.setScreen(parentScreen);
                        selected.accept(item.path());
                    }
                })).toList());
    }

    public static boolean accepts(Path path, Kind kind) {
        if (path.getFileName() == null) {
            return false;
        }
        String filename = path.getFileName().toString().toLowerCase(Locale.ROOT);
        return kind == Kind.MODEL ? filename.endsWith(".zip") || filename.endsWith(".ysm")
                : filename.endsWith(".png") || filename.endsWith(".jpg") || filename.endsWith(".jpeg");
    }

    @Override
    public void removed() {
        closed = true;
        generation++;
        if (request != null) {
            request.cancel();
        }
        super.removed();
    }
}
