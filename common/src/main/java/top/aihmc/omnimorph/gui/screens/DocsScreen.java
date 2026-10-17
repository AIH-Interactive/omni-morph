package top.aihmc.omnimorph.gui.screens;

import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import top.aihmc.omnimorph.gui.widgets.Label;
import top.aihmc.omnimorph.utils.ColorUtils;
import top.aihmc.omnimorph.utils.FiguraText;
import top.aihmc.omnimorph.utils.TextUtils;

public class DocsScreen extends AbstractPanelScreen {

    private final Screen sourcePanel;

    public DocsScreen(AbstractPanelScreen parentScreen) {
        super(parentScreen.parentScreen, FiguraText.of("gui.panels.title.docs"));
        sourcePanel = parentScreen;
    }

    @Override
    public Class<? extends Screen> getSelectedPanel() {
        return sourcePanel.getClass();
    }

    @Override
    protected void init() {
        super.init();
        this.addRenderableWidget(new Label(Component.empty().append("Still not finished :s"), width / 2, height / 2, 3f, 200, true, TextUtils.Alignment.CENTER, ColorUtils.Colors.AWESOME_BLUE.hex));
    }
}
