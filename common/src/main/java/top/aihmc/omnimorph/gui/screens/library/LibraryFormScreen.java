package top.aihmc.omnimorph.gui.screens.library;

import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.figuramc.figura.gui.screens.AbstractPanelScreen;
import org.figuramc.figura.gui.widgets.Button;
import org.figuramc.figura.utils.FiguraText;
import org.figuramc.figura.utils.ui.UIHelper;

/** Forms keep an explicit return path and use the same supported minimum logical size. */
abstract class LibraryFormScreen extends AbstractPanelScreen {
    protected boolean formReady;

    protected LibraryFormScreen(Screen parent, Component title) {
        super(parent, title);
    }

    protected boolean initForm() {
        super.init();
        // Finish or cancel the form before changing panels, preserving its parent's lifecycle.
        removeWidget(panels);
        // Modal forms keep the reference header visible without abandoning a
        // retained parent and its preview/network resources through another tab.
        addRenderableOnly(panels);
        formReady = width >= 300 && height >= 220;
        if (!formReady) {
            addRenderableOnly((gui, x, y, delta) -> UIHelper.renderCenteredScrollingText(gui,
                    FiguraText.of("gui.library.scale"), 8, height / 2, Math.max(16, width - 16), 20, 0xffffffff));
            addRenderableWidget(new Button(Math.max(0, width - 84), Math.max(0, height - 24), 80, 20,
                    FiguraText.of("gui.done"), null, ignored -> onClose()));
        }
        return formReady;
    }
}
