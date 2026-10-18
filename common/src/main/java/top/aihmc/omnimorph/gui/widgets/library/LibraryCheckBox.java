package top.aihmc.omnimorph.gui.widgets.library;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import top.aihmc.omnimorph.gui.widgets.SwitchButton;
import top.aihmc.omnimorph.utils.ui.UIHelper;

/** Compact checkbox that retains Figura's toggle, input and narration behavior. */
public final class LibraryCheckBox extends SwitchButton {
    public LibraryCheckBox(int x, int y, int width, Component label, Component tooltip, OnPress action) {
        super(x, y, width, 20, label, tooltip, action);
        setUnderline(false);
    }

    @Override
    protected void renderDefaultTexture(GuiGraphicsExtractor gui, float delta) {
        int color = isActive() ? isHoveredOrFocused() ? 0xff55ffff : 0xffbac6d8 : 0xff666666;
        UIHelper.fillOutline(gui, getX() + 2, getY() + 6, 8, 8, color);
        if (isToggled()) {
            gui.fill(getX() + 4, getY() + 8, getX() + 8, getY() + 12, color);
        }
    }

    @Override
    protected void renderText(GuiGraphicsExtractor gui, float delta) {
        UIHelper.renderScrollingText(gui, getMessage(), getX() + 15, getY() + 6,
                Math.max(1, getWidth() - 17), getTextColor() | 0xff000000);
    }
}
