package top.aihmc.omnimorph.gui.widgets;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import org.figuramc.figura.gui.widgets.SliderWidget;
import org.figuramc.figura.gui.widgets.config.AbstractConfigElement;
import org.figuramc.figura.gui.widgets.config.CategoryWidget;
import org.figuramc.figura.gui.widgets.lists.ConfigList;
import org.figuramc.figura.utils.ui.UIHelper;
import top.aihmc.omnimorph.config.InstructionCeilingConfig;

/** The logarithmic range keeps small budgets usable alongside Integer.MAX_VALUE. */
public final class InstructionCeilingElement extends AbstractConfigElement {
    private final InstructionCeilingConfig ceiling;
    private final SliderWidget slider;

    public InstructionCeilingElement(int width, InstructionCeilingConfig config,
                                    ConfigList list, CategoryWidget category) {
        super(width, config, list, category);
        ceiling = config;
        slider = new SliderWidget(0, 0, 90, 11,
                InstructionCeilingConfig.sliderPosition(config.tempValue), 10001, false) {
            @Override
            protected void scroll(double amount) {
                super.scroll(amount);
                ceiling.tempValue = InstructionCeilingConfig.sliderValue(getScrollProgress());
            }
        };
        children.add(0, slider);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor gui, int mouseX, int mouseY, float delta) {
        if (!isVisible()) {
            return;
        }
        slider.setScrollProgress(InstructionCeilingConfig.sliderPosition(ceiling.tempValue), false);
        resetButton.setActive(!isDefault());
        super.extractRenderState(gui, mouseX, mouseY, delta);
        gui.text(Minecraft.getInstance().font, Integer.toString(ceiling.tempValue),
                slider.getX(), getY(), UIHelper.adjustColor(0xFFFFFF));
    }

    @Override
    public void setX(int x) {
        super.setX(x);
        slider.setX(x + getWidth() - 154);
    }

    @Override
    public void setY(int y) {
        super.setY(y);
        slider.setY(y + 9);
    }
}
