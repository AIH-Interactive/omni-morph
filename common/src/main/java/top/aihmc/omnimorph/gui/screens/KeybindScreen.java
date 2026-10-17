package top.aihmc.omnimorph.gui.screens;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import top.aihmc.omnimorph.FiguraMod;
import top.aihmc.omnimorph.avatar.Avatar;
import top.aihmc.omnimorph.avatar.AvatarManager;
import top.aihmc.omnimorph.gui.widgets.Button;
import top.aihmc.omnimorph.gui.widgets.lists.KeybindList;
import top.aihmc.omnimorph.lua.api.keybind.FiguraKeybind;
import top.aihmc.omnimorph.utils.FiguraText;

public class KeybindScreen extends AbstractPanelScreen {

    private final Screen sourcePanel;

    private KeybindList list;

    public KeybindScreen(AbstractPanelScreen parentScreen) {
        super(parentScreen.parentScreen, FiguraText.of("gui.panels.title.keybind"));
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

        // -- bottom buttons -- // 

        // reset
        Button reset;
        this.addRenderableWidget(reset = new Button(width / 2 - 122, height - 24, 120, 20, FiguraText.of("gui.reset_all"), null, button -> {
            if (owner == null || owner.luaRuntime == null)
                return;

            for (FiguraKeybind keybind : owner.luaRuntime.keybinds.keyBindings)
                keybind.resetDefaultKey();
            list.updateBindings();
        }));
        reset.setActive(false);

        // back
        addRenderableWidget(new Button(width / 2 + 4, height - 24, 120, 20, FiguraText.of("gui.done"), null, bx -> onClose()));

        // -- list -- // 

        int listWidth = Math.min(this.width - 8, 420);
        this.addRenderableWidget(list = new KeybindList((this.width - listWidth) / 2, 28, listWidth, height - 56, owner, reset));
    }

    @Override
    public void onClose() {
        this.minecraft.setScreen(sourcePanel);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent mouseButtonEvent, boolean bl) {
        return list.updateKey(InputConstants.Type.MOUSE.getOrCreate(mouseButtonEvent.button())) || super.mouseClicked(mouseButtonEvent, bl);
    }

    @Override
    public boolean keyPressed(KeyEvent keyEvent) {
        return list.updateKey(keyEvent.key() == 256 ? InputConstants.UNKNOWN : InputConstants.getKey(keyEvent)) || super.keyPressed(keyEvent);
    }
}
