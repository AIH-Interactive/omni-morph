package top.aihmc.omnimorph.gui.widgets.library;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.MultiLineEditBox;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.PreeditEvent;
import net.minecraft.network.chat.Component;
import top.aihmc.omnimorph.gui.widgets.AbstractContainerElement;
import top.aihmc.omnimorph.utils.ui.UIHelper;

import java.util.function.Consumer;

/** Figura-skinned multiline input; Minecraft supplies only text editing and IME behavior. */
public final class LibraryMultilineField extends AbstractContainerElement {
    private final MultiLineEditBox input;
    private boolean enabled = true;

    public LibraryMultilineField(int width, int height, String value, Consumer<String> changed) {
        super(0, 0, width, height);
        input = MultiLineEditBox.builder().setShowBackground(false).setShowDecorations(false)
                .setTextColor(0xffeeeeee).setCursorColor(0xffffffff)
                .build(Minecraft.getInstance().font, width - 4, height - 4, Component.empty());
        input.setCharacterLimit(4096);
        input.setValue(value);
        input.setValueListener(changed);
        children.add(input);
    }

    public void setValue(String value) {
        input.setValue(value);
    }

    public void setCharacterLimit(int limit) {
        input.setCharacterLimit(limit);
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
        input.active = enabled;
        if (!enabled) {
            setFocused(false);
        }
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        return enabled && isMouseOver(event.x(), event.y()) && super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        return enabled && super.keyPressed(event);
    }

    @Override
    public boolean charTyped(CharacterEvent event) {
        return enabled && super.charTyped(event);
    }

    @Override
    public boolean preeditUpdated(PreeditEvent event) {
        return enabled && super.preeditUpdated(event);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor gui, int mouseX, int mouseY, float delta) {
        UIHelper.blitSliced(gui, getX(), getY(), getWidth(), getHeight(), UIHelper.OUTLINE_FILL);
        input.setX(getX() + 2);
        input.setY(getY() + 2);
        super.extractRenderState(gui, mouseX, mouseY, delta);
    }
}
