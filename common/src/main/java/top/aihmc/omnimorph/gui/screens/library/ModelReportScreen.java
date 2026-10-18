package top.aihmc.omnimorph.gui.screens.library;

import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import top.aihmc.omnimorph.backend2.library.LibraryClient;
import top.aihmc.omnimorph.gui.screens.ModelLibraryScreen;
import top.aihmc.omnimorph.gui.widgets.Button;
import top.aihmc.omnimorph.gui.widgets.library.LibraryMultilineField;
import top.aihmc.omnimorph.utils.FiguraText;
import top.aihmc.omnimorph.utils.ui.UIHelper;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;

/** A report is sent only by the explicit submit action. Cancel/close never creates a report. */
public final class ModelReportScreen extends LibraryFormScreen {
    private final LibraryClient client;
    private final UUID model;
    private LibraryClient.Request<?> pending;
    private LibraryMultilineField reason;
    private Button submit;
    private UUID operation;
    private String submittedReason;
    private String value = "";
    private boolean closed;
    private boolean submitted;
    private Component message = Component.empty();

    public ModelReportScreen(Screen parent, LibraryClient client, UUID model) {
        super(parent, FiguraText.of("gui.library.report"));
        this.client = client;
        this.model = model;
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
        int area = Math.min(440, width - 16);
        int left = (width - area) / 2;
        int top = Math.max(30, (height - 180) / 2);
        addRenderableOnly((gui, x, y, delta) -> {
            UIHelper.blitSliced(gui, left, top, area, 180, UIHelper.OUTLINE_FILL);
            UIHelper.renderScrollingText(gui, FiguraText.of("gui.library.report_reason"), left + 8, top + 10,
                    area - 16, 0xffeeeeee);
            UIHelper.renderScrollingText(gui, Component.literal(model.toString()), left + 8, top + 26,
                    area - 16, 0xffbac6d8);
        });
        reason = addRenderableWidget(new LibraryMultilineField(area - 16, 72, value, text -> value = text));
        reason.setX(left + 8);
        reason.setY(top + 44);
        reason.setCharacterLimit(512);
        int actionWidth = (area - 20) / 2;
        submit = addRenderableWidget(new Button(left + actionWidth + 12, top + 152, actionWidth, 20,
                FiguraText.of("gui.library.submit_report"), null, ignored -> submit()));
        addRenderableWidget(new Button(left + 8, top + 152, actionWidth, 20,
                FiguraText.of("gui.done"), null, ignored -> onClose()));
        addRenderableOnly((gui, x, y, delta) -> UIHelper.renderScrollingText(gui, message, left + 8,
                top + 132, area - 16, 0xffd3e7e1));
    }

    private void submit() {
        String text = value.trim();
        if (closed || submitted || busy() || text.isEmpty() || text.getBytes(StandardCharsets.UTF_8).length > 512) {
            return;
        }
        if (operation == null) {
            operation = UUID.randomUUID();
            submittedReason = text;
        }
        var request = client.submit(LibraryClient.Lane.CONTROL, exchange -> exchange.json("POST",
                "/api/library/v1/models/" + model + "/reports", Map.of("operation_id", operation.toString(),
                        "reason", submittedReason), Map.class));
        pending = request;
        request.future().whenComplete((result, failure) -> {
            if (!closed) {
                submitted = failure == null;
                message = failure == null ? FiguraText.of("gui.library.report_submitted")
                        : FiguraText.of("gui.library.failure", failure instanceof LibraryClient.Failure typed ? typed.code : "LIBRARY_UNAVAILABLE");
            }
        });
    }

    private boolean busy() {
        return pending != null && !pending.future().isDone();
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
        reason.setEnabled(operation == null);
        submit.setActive(!closed && !submitted && !busy() && !value.isBlank()
                && value.trim().getBytes(StandardCharsets.UTF_8).length <= 512);
        submit.setMessage(FiguraText.of(operation == null ? "gui.library.submit_report" : "gui.library.retry_change"));
    }

    @Override
    public void removed() {
        closed = true;
        value = "";
        submittedReason = null;
        if (pending != null) {
            pending.cancel();
        }
        super.removed();
    }
}
