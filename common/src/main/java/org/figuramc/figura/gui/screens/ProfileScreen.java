package org.figuramc.figura.gui.screens;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import org.figuramc.figura.gui.FiguraToast;
import org.figuramc.figura.gui.widgets.Button;
import org.figuramc.figura.utils.FiguraText;
import org.figuramc.figura.utils.TextUtils;

public class ProfileScreen extends AbstractPanelScreen {

    public ProfileScreen(Screen parentScreen) {
        super(parentScreen, FiguraText.of("gui.panels.title.profile"));
    }

    @Override
    public void init() {
        super.init();

        this.addRenderableWidget(new Button(width / 2 - 30, height / 2 - 30, 60, 20, FiguraText.of("gui.debug.meow"),
                FiguraText.of("gui.debug.multiline"), button -> {
            FiguraToast.sendToast(FiguraText.of("gui.debug.restarting").setStyle(Style.EMPTY.withColor(0x99BBEE)), FiguraText.of("gui.debug.ten_minutes"), FiguraToast.ToastType.DEFAULT);
        }));

        this.addRenderableWidget(new Button(width / 2 - 30, height / 2 + 10, 60, 20, FiguraText.of("gui.debug.meow"), TextUtils.tryParseJson(
                "{\"text\": \"△❗\n❌\uD83E\uDDC0\n\n☄❤\n\n\n☆★\",\"font\": \"omni-morph:badges\"}"), button -> {
            FiguraToast.sendToast(FiguraText.of("gui.debug.restarting").setStyle(Style.EMPTY.withColor(0x99BBEE)), FiguraText.of("gui.debug.ten_minutes"), FiguraToast.ToastType.DEFAULT);
        }));
    }

    @Override
    public void renderOverlays(GuiGraphicsExtractor gui, int mouseX, int mouseY, float delta) {
        // UIHelper.highlight(stack, button, TextUtils.tryParseJson("{\"text\":\"🦐🦐🦐🦐\",\"font\":\"omni-morph:emojis\"}"));
        super.renderOverlays(gui, mouseX, mouseY, delta);
    }
}
