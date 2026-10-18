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
import top.aihmc.omnimorph.utils.FiguraText;
import top.aihmc.omnimorph.utils.ui.UIHelper;

import java.util.function.Consumer;
import java.util.function.IntConsumer;

/** Figura-skinned multiline input; Minecraft supplies only text editing and IME behavior. */
public final class LibraryMultilineField extends AbstractContainerElement {
    private final MultiLineEditBox input;
    private boolean enabled = true;
    private int minimumHeight;
    private int maximumHeight;
    private boolean resizing;
    private double resizeStartY;
    private int resizeStartHeight;
    private IntConsumer heightChanged;

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

    public LibraryMultilineField resizable(int minimumHeight, int maximumHeight, IntConsumer changed) {
        this.minimumHeight = minimumHeight;
        this.maximumHeight = Math.max(minimumHeight, maximumHeight);
        this.heightChanged = changed;
        setHeight(Math.clamp(getHeight(), this.minimumHeight, this.maximumHeight));
        return this;
    }

    @Override
    public void setHeight(int height) {
        super.setHeight(height);
        input.setHeight(height - (heightChanged == null ? 4 : 12));
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
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (enabled && event.button() == 0 && overResizeHandle(event.x(), event.y())) {
            resizing = true;
            resizeStartY = event.y();
            resizeStartHeight = getHeight();
            return true;
        }
        return enabled && isMouseOver(event.x(), event.y()) && super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double deltaX, double deltaY) {
        if (resizing && enabled && event.button() == 0) {
            int height = Math.clamp(resizeStartHeight + (int) Math.round(event.y() - resizeStartY),
                    minimumHeight, maximumHeight);
            if (height != getHeight()) {
                setHeight(height);
                heightChanged.accept(height);
            }
            return true;
        }
        return enabled && super.mouseDragged(event, deltaX, deltaY);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        if (resizing && event.button() == 0) {
            resizing = false;
            return true;
        }
        return super.mouseReleased(event);
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
