package top.aihmc.omnimorph.gui.widgets.wardrobe;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import org.figuramc.figura.gui.widgets.Button;
import org.figuramc.figura.utils.ui.UIHelper;

/** Rotates the complete label while keeping the native rectangular focus and hit target. */
public final class VerticalWardrobeTab extends Button {
    public VerticalWardrobeTab(int x, int y, int height, Component label, OnPress action) {
        super(x, y, 22, height, label, label, action);
    }

    @Override
    protected void renderText(GuiGraphicsExtractor gui, float delta) {
        gui.pose().pushMatrix();
        try {
            gui.pose().translate(getX() + getWidth() / 2f, getY() + getHeight() / 2f);
            // A negative scale reverses glyph winding and can cull the complete label.
            // Reverse the reading direction with a rotation that keeps glyphs front-facing.
            gui.pose().rotate(-(float) Math.PI / 2f);
            UIHelper.renderCenteredScrollingText(gui, getMessage(), -getHeight() / 2 + 2,
                    -getWidth() / 2, getHeight() - 4, getWidth(), getTextColor());
        } finally {
            gui.pose().popMatrix();
        }
    }
}
