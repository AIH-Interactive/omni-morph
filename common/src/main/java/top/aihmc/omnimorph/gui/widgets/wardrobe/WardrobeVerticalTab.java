package top.aihmc.omnimorph.gui.widgets.wardrobe;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import org.figuramc.figura.gui.widgets.Button;

/** A narrow, keyboard-accessible tab with a vertical label and a persistent selected marker. */
public final class WardrobeVerticalTab extends Button {
    private final boolean selected;

    public WardrobeVerticalTab(int x, int y, int width, int height, Component label, boolean selected,
                               OnPress action) {
        super(x, y, width, height, label, label, action);
        this.selected = selected;
    }

    @Override
    protected void renderText(GuiGraphics gui, float delta) {
        var font = Minecraft.getInstance().font;
        int[] characters = getMessage().getString().codePoints().toArray();
        int lineHeight = font.lineHeight;
        int top = getY() + Math.max(1, (getHeight() - characters.length * lineHeight) / 2);
        gui.enableScissor(getX(), getY(), getX() + getWidth(), getY() + getHeight());
        for (int index = 0; index < characters.length; index++) {
            String character = new String(Character.toChars(characters[index]));
            gui.drawString(font, character, getX() + (getWidth() - font.width(character)) / 2,
                    top + index * lineHeight, selected ? 0xffb3f5df : 0xffbdc9d4);
        }
        gui.disableScissor();
        if (selected) {
            gui.fill(getX(), getY(), getX() + 2, getY() + getHeight(), 0xff6dd2ba);
        }
    }
}
