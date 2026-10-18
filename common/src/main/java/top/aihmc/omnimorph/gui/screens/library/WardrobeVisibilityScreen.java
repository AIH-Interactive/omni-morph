package top.aihmc.omnimorph.gui.screens.library;

import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import top.aihmc.omnimorph.backend2.library.LibraryClient;
import top.aihmc.omnimorph.gui.screens.WardrobeScreen;
import top.aihmc.omnimorph.gui.widgets.Button;
import top.aihmc.omnimorph.gui.widgets.SwitchButton;
import top.aihmc.omnimorph.utils.FiguraText;
import top.aihmc.omnimorph.utils.ui.UIHelper;

import java.util.Map;
import java.util.UUID;

/** An explicit choice for the current uploaded wardrobe resource, separate from library-private intent. */
public final class WardrobeVisibilityScreen extends LibraryFormScreen {
    public record State(boolean available, String assetId, long assetRevision, long equipmentRevision,
                        boolean showEquipment, boolean blocked) {
        public State {
            if (available && (assetId == null || assetRevision < 1 || equipmentRevision < 1)) {
                throw new IllegalArgumentException("INVALID_WARDROBE_STATE");
            }
        }
    }

    private final LibraryClient client;
    private LibraryClient.Request<?> pending;
    private State state;
    private Map<String, Object> mutation;
    private SwitchButton show;
    private Button apply;
    private long generation;
    private boolean closed;
    private Component message = Component.empty();

    public WardrobeVisibilityScreen(Screen parent, LibraryClient client) {
        super(parent, FiguraText.of("gui.library.wardrobe_visibility"));
        this.client = client;
    }

    @Override
    public Class<? extends Screen> getSelectedPanel() {
        return WardrobeScreen.class;
    }

    @Override
    protected void init() {
        if (!initForm()) {
            return;
        }
        int area = Math.max(80, width - 16);
        show = addRenderableWidget(new SwitchButton(8, 28, area, 20,
                FiguraText.of("gui.library.wardrobe_show"), FiguraText.of("gui.library.wardrobe_show_hint"), ignored -> {}));
        apply = addRenderableWidget(new Button(8, height - 72, area, 20,
                FiguraText.of("gui.library.apply_visibility"), null, ignored -> apply()));
        addRenderableWidget(new Button(8, height - 48, area, 20,
                FiguraText.of("gui.library.refresh"), null, ignored -> refresh()));
        addRenderableWidget(new Button(8, height - 24, area, 20,
                FiguraText.of("gui.done"), null, ignored -> onClose()));
        addRenderableOnly((gui, x, y, delta) -> UIHelper.renderScrollingText(gui, message, 8, 54, area, 0xffd3e7e1));
        refresh();
    }

    private boolean busy() {
        return pending != null && !pending.future().isDone();
    }

    private void refresh() {
        if (closed || !client.current()) {
            return;
        }
        long expected = ++generation;
        if (pending != null) {
            pending.cancel();
        }
        mutation = null;
        state = null;
        show.setActive(false);
        var request = client.submit(LibraryClient.Lane.CONTROL, exchange -> exchange.json("GET",
                "/api/library/v1/wardrobe", null, State.class));
        pending = request;
        request.future().whenComplete((value, failure) -> {
            if (closed || generation != expected) {
                return;
            }
            if (failure != null) {
                failed(failure);
            } else {
                state = value;
                show.setToggled(value.available() && value.showEquipment() && !value.blocked());
                message = FiguraText.of(!value.available() ? "gui.library.no_wardrobe_equipment"
                        : value.blocked() ? "gui.library.blocked_visibility" : "gui.library.wardrobe_show_hint");
            }
        });
    }

    private void apply() {
        if (closed || busy() || state == null || !state.available() || state.blocked()) {
            return;
        }
        if (mutation == null) {
            mutation = Map.of("operation_id", UUID.randomUUID().toString(),
                    "expected_equipment_revision", state.equipmentRevision(), "asset_id", state.assetId(),
                    "asset_revision", state.assetRevision(), "show_equipment", show.isToggled());
        }
        Map<String, Object> input = mutation;
        long expected = ++generation;
        var request = client.submit(LibraryClient.Lane.CONTROL, exchange -> exchange.json("PATCH",
                "/api/library/v1/wardrobe", input, Map.class));
        pending = request;
        request.future().whenComplete((value, failure) -> {
            if (!closed && expected == generation) {
                if (failure != null) {
                    failed(failure);
                } else {
                    refresh();
                }
            }
        });
    }

    private void failed(Throwable failure) {
        message = FiguraText.of("gui.library.failure", failure instanceof LibraryClient.Failure typed ? typed.code : "LIBRARY_UNAVAILABLE");
    }

    @Override
    public void tick() {
        super.tick();
        if (!formReady) {
            return;
        }
        if (!client.current()) {
            onClose();
            return;
        }
        boolean available = !closed && !busy() && state != null && state.available() && !state.blocked();
        show.setActive(available && mutation == null);
        apply.setActive(available && (mutation != null || show.isToggled() != state.showEquipment()));
        apply.setMessage(FiguraText.of(mutation == null ? "gui.library.apply_visibility" : "gui.library.retry_change"));
    }

    @Override
    public void removed() {
        closed = true;
        generation++;
        state = null;
        mutation = null;
        if (pending != null) {
            pending.cancel();
        }
        super.removed();
    }
}
