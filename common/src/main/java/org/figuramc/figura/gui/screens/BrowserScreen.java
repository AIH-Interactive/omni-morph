package org.figuramc.figura.gui.screens;

import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.figuramc.figura.gui.FiguraToast;
import org.figuramc.figura.gui.widgets.Button;
import org.figuramc.figura.utils.FiguraText;

public class BrowserScreen extends AbstractPanelScreen {

    public BrowserScreen(Screen parentScreen) {
        super(parentScreen, FiguraText.of("gui.panels.title.browser"));
    }

    @Override
    public void init() {
        super.init();

        int y = -84;
        this.addRenderableWidget(new Button(width / 2 - 50, height / 2 + (y += 24), 100, 20, FiguraText.of("gui.debug.toast.default"), FiguraText.of("backend.error"), button -> {
            FiguraToast.sendToast(FiguraText.of("gui.debug.toast.default"), FiguraText.of("gui.debug.test"), FiguraToast.ToastType.DEFAULT);
        }));
        this.addRenderableWidget(new Button(width / 2 - 50, height / 2 + (y += 24), 100, 20, FiguraText.of("gui.debug.toast.error"), FiguraText.of("gui.debug.test_number", 2), button -> {
            FiguraToast.sendToast(FiguraText.of("gui.debug.toast.error"), FiguraText.of("gui.debug.test"), FiguraToast.ToastType.ERROR);
        }));
        this.addRenderableWidget(new Button(width / 2 - 50, height / 2 + (y += 24), 100, 20, FiguraText.of("gui.debug.toast.warning"), FiguraText.of("gui.debug.test_number", 3), button -> {
            FiguraToast.sendToast(FiguraText.of("gui.debug.toast.warning"), FiguraText.of("gui.debug.test"), FiguraToast.ToastType.WARNING);
        }));
        this.addRenderableWidget(new Button(width / 2 - 50, height / 2 + (y += 24), 100, 20, FiguraText.of("gui.debug.toast.cheese"), FiguraText.of("gui.debug.test_number", 4), button -> {
            FiguraToast.sendToast(FiguraText.of("gui.debug.toast.cheese"), FiguraText.of("gui.debug.test"), FiguraToast.ToastType.CHEESE);
        }));
    }
}
