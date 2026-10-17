package top.aihmc.omnimorph.gui.screens;

import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import top.aihmc.omnimorph.FiguraMod;
import top.aihmc.omnimorph.avatar.Avatar;
import top.aihmc.omnimorph.avatar.AvatarManager;
import top.aihmc.omnimorph.gui.widgets.Button;
import top.aihmc.omnimorph.gui.widgets.PianoWidget;
import top.aihmc.omnimorph.gui.widgets.lists.SoundsList;
import top.aihmc.omnimorph.utils.FiguraText;

public class SoundScreen extends AbstractPanelScreen {

    private final Screen sourcePanel;
    private PianoWidget piano;

    public SoundScreen(AbstractPanelScreen parentScreen) {
        super(parentScreen.parentScreen, FiguraText.of("gui.panels.title.sound"));
        sourcePanel = parentScreen;
    }

    @Override
    public Class<? extends Screen> getSelectedPanel() {
        return sourcePanel.getClass();
    }

    @Override
    protected void init() {
        super.init();

        Avatar owner = AvatarManager.getAvatarForPlayer(FiguraMod.getLocalPlayerUUID());

        // list
        int listWidth = Math.min(this.width - 8, 420);
        int listX = (this.width - listWidth) / 2;
        SoundsList list;
        addRenderableWidget(list = new SoundsList(listX, 28, listWidth, height - 120, owner));

        // keys
        addRenderableWidget(piano = new PianoWidget(listX, height - 88, listWidth, 60, list::getSound));

        // back
        addRenderableWidget(new Button(width / 2 - 60, height - 24, 120, 20, FiguraText.of("gui.done"), null, bx -> onClose()));
    }

    @Override
    public void onClose() {
        this.minecraft.setScreen(sourcePanel);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent mouseButtonEvent, boolean bl) {
        piano.pressed = mouseButtonEvent.button() == 0;
        return super.mouseClicked(mouseButtonEvent, bl);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent mouseButtonEvent) {
        piano.pressed = false;
        return super.mouseReleased(mouseButtonEvent);
    }
}
