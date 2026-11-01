package top.aihmc.omnimorph.gui.widgets.library;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.MultiLineEditBox;
import net.minecraft.network.chat.Component;
import org.figuramc.figura.gui.widgets.AbstractContainerElement;
import org.figuramc.figura.utils.FiguraText;
import org.figuramc.figura.utils.ui.UIHelper;

import java.util.function.Consumer;
import java.util.function.IntConsumer;

/** Figura-skinned multiline input backed by Minecraft's native text editing. */
public final class LibraryMultilineField extends AbstractContainerElement {
    private final ResizableInput input;
    private boolean enabled = true;
    private int minimumHeight;
    private int maximumHeight;
    private boolean resizing;
    private double resizeStartY;
    private int resizeStartHeight;
    private IntConsumer heightChanged;

    public LibraryMultilineField(int width, int height, String value, Consumer<String> changed) {
        super(0, 0, width, height);
        input = new ResizableInput(width - 4, height - 4);
        input.setCharacterLimit(4096);
        input.setValue(value);
        input.setValueListener(changed);
        children.add(input);
    }

    public LibraryMultilineField resizable(int minimumHeight, int maximumHeight, IntConsumer changed) {
        this.minimumHeight = minimumHeight;
        this.maximumHeight = Math.max(minimumHeight, maximumHeight);
        this.heightChanged = changed;
        setHeight(net.minecraft.util.Mth.clamp(getHeight(), this.minimumHeight, this.maximumHeight));
        return this;
    }

    @Override
    public void setHeight(int height) {
        super.setHeight(height);
        input.resizeHeight(height - (heightChanged == null ? 4 : 12));
    }

    private static final class ResizableInput extends MultiLineEditBox {
        private ResizableInput(int width, int height) {
            super(Minecraft.getInstance().font, 0, 0, width, height, Component.empty(), Component.empty());
        }

        private void resizeHeight(int height) {
            this.height = height;
        }

        @Override
        protected void renderBackground(GuiGraphics gui) {
            // The outer Figura control draws the field skin.
        }

        @Override
        protected void renderDecorations(GuiGraphics gui) {
            // The outer control owns decorations and the resize handle.
        }
    }

    private boolean overResizeHandle(double x, double y) {
        return heightChanged != null && x >= getX() + getWidth() - 14 && x < getX() + getWidth()
                && y >= getY() + getHeight() - 10 && y < getY() + getHeight();
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
            resizing = false;
            setFocused(false);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (enabled && button == 0 && overResizeHandle(mouseX, mouseY)) {
            resizing = true;
            resizeStartY = mouseY;
            resizeStartHeight = getHeight();
            return true;
        }
        return enabled && isMouseOver(mouseX, mouseY) && super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        if (resizing && enabled && button == 0) {
            int height = net.minecraft.util.Mth.clamp(resizeStartHeight + (int) Math.round(mouseY - resizeStartY),
                    minimumHeight, maximumHeight);
            if (height != getHeight()) {
                setHeight(height);
                heightChanged.accept(height);
            }
            return true;
        }
        return enabled && super.mouseDragged(mouseX, mouseY, button, deltaX, deltaY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (resizing && button == 0) {
            resizing = false;
        } else if (getFocused() != null) {
            getFocused().mouseReleased(mouseX, mouseY, button);
        }
        setDragging(false);
        // Figura containers clear focus when a child consumes release. Text editing
        // must retain both the native input focus and the screen's focused field.
        return false;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        return enabled && super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char character, int modifiers) {
        return enabled && super.charTyped(character, modifiers);
    }

    @Override
    public void render(GuiGraphics gui, int mouseX, int mouseY, float delta) {
        UIHelper.blitSliced(gui, getX(), getY(), getWidth(), getHeight(), UIHelper.OUTLINE_FILL);
        input.setX(getX() + 2);
        input.setY(getY() + 2);
        super.render(gui, mouseX, mouseY, delta);
        if (heightChanged != null) {
            if (enabled && overResizeHandle(mouseX, mouseY)) {
                UIHelper.setTooltip(FiguraText.of("gui.library.resize_description"));
            }
            int color = !enabled ? 0xff555555
                    : resizing || overResizeHandle(mouseX, mouseY) ? 0xff55ffff : 0xffaaaaaa;
            for (int step = 0; step < 3; step++) {
                int x = getX() + getWidth() - 4 - step * 3;
                int y = getY() + getHeight() - 3;
                gui.fill(x, y - step * 2 - 1, x + 1, y, color);
            }
        }
    }
}
