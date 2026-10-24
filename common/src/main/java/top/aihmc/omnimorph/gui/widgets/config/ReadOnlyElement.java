package top.aihmc.omnimorph.gui.widgets.config;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.MutableComponent;
import org.figuramc.figura.config.ConfigType;
import org.figuramc.figura.gui.widgets.ParentedButton;
import org.figuramc.figura.gui.widgets.lists.ConfigList;

import org.figuramc.figura.gui.widgets.config.AbstractConfigElement;
import org.figuramc.figura.gui.widgets.config.CategoryWidget;
/** Uses the existing row/button layout; there is no editable or persisted status value. */
public final class ReadOnlyElement extends AbstractConfigElement {
    private final ConfigType.ReadOnlyConfig status;
    private final ParentedButton display;

    public ReadOnlyElement(int width, ConfigType.ReadOnlyConfig status, ConfigList parent, CategoryWidget category) {
        super(width, status, parent, category);
        this.status = status;
        children.remove(resetButton);
        display = new ParentedButton(0, 0, 154, 20, status.text.get(), this, ignored -> {});
        display.setActive(false);
        children.add(display);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor gui, int mouseX, int mouseY, float delta) {
        display.setMessage(status.text.get());
        display.setTooltip(status.text.get());
        super.extractRenderState(gui, mouseX, mouseY, delta);
    }

    @Override
    public MutableComponent getTooltip() {
        return status.tooltip.copy().append("\n").append(status.text.get());
    }

    @Override
    public void setX(int x) {
        super.setX(x);
        display.setX(x + getWidth() - 154);
    }

    @Override
    public void setY(int y) {
        super.setY(y);
        display.setY(y);
    }
}
