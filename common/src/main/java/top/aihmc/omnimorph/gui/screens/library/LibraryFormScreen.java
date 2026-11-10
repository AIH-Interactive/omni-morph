package top.aihmc.omnimorph.gui.screens.library;

import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.figuramc.figura.gui.screens.AbstractPanelScreen;
import org.figuramc.figura.gui.widgets.Button;
import org.figuramc.figura.utils.FiguraText;
import org.figuramc.figura.utils.ui.UIHelper;
import top.aihmc.omnimorph.gui.screens.ModelLibraryScreen;
import top.aihmc.omnimorph.gui.widgets.navigation.NavigationRoute;

/** Forms keep an explicit return path and use the same supported minimum logical size. */
abstract class LibraryFormScreen extends AbstractPanelScreen {
    /** Width reserved beyond the left inset for the navigation rail on pages that show it. */
    protected static final int NAVIGATION_INSET = 30;

    protected boolean formReady;

    protected LibraryFormScreen(Screen parent, Component title) {
        super(parent, title);
    }

    protected boolean initForm() {
        super.init();
        if (!usesSideNavigation()) {
            // Finish or cancel the form before changing panels, preserving its parent's lifecycle.
            // Modal forms keep the reference header visible without abandoning a
            // retained parent and its preview/network resources through another tab.
            removeWidget(panels);
            addRenderableOnly(panels);
        }
        formReady = width >= 300 && height >= 220;
        if (!formReady) {
            addRenderableOnly((gui, x, y, delta) -> UIHelper.renderCenteredScrollingText(gui,
                    FiguraText.of("gui.library.scale"), 8, height / 2, Math.max(16, width - 16), 20, 0xffffffff));
            addRenderableWidget(new Button(Math.max(0, width - 84), Math.max(0, height - 24), 80, 20,
                    FiguraText.of("gui.done"), null, ignored -> onClose()));
        }
        return formReady;
    }

    /** Forms with navigation ignore it while their own request is still running. */
    protected boolean navigationAllowed() {
        return false;
    }

    /** Navigation returns through the retained catalog, which then opens the destination. */
    @Override
    public void navigate(NavigationRoute route) {
        if (!navigationAllowed()) {
            return;
        }
        if (parentScreen instanceof ModelLibraryScreen catalog) {
            minecraft.gui.setScreen(catalog);
            catalog.navigate(route);
        } else {
            super.navigate(route);
        }
    }
}
