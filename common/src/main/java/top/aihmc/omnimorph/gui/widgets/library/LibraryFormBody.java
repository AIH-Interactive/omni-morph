package top.aihmc.omnimorph.gui.widgets.library;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import top.aihmc.omnimorph.gui.widgets.FiguraWidget;
import top.aihmc.omnimorph.gui.widgets.lists.AbstractList;
import top.aihmc.omnimorph.utils.ui.UIHelper;

import java.util.ArrayList;
import java.util.List;

/** Scrolls existing labeled controls, including keyboard focus, within a form's viewport. */
public final class LibraryFormBody extends AbstractList {
    private record Row(Component label, FiguraWidget widget, GuiEventListener input, int minimumHeight, int labelWidth) {
        int height(boolean labeled) {
            int padding = labeled && labelWidth == 0 ? 17 : 4;
            return Math.max(minimumHeight, widget.getHeight() + padding);
        }
    }
    private final List<Row> rows = new ArrayList<>();
    private final boolean labeled;
    private GuiEventListener previousFocus;
    private int previousMaximum;

    public LibraryFormBody(int x, int y, int width, int height) {
        this(x, y, width, height, true);
    }

    public LibraryFormBody(int x, int y, int width, int height, boolean labeled) {
        super(x, y, width, height);
        this.labeled = labeled;
    }

    public <T extends FiguraWidget & GuiEventListener> T row(Component label, T widget) {
        return row(label, widget, labeled ? 40 : 24, 0);
    }

    public <T extends FiguraWidget & GuiEventListener> T row(Component label, T widget, int height, int labelWidth) {
        rows.add(new Row(label, widget, widget, height, labelWidth));
        children.add(widget);
        return widget;
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor gui, int mouseX, int mouseY, float delta) {
        var visibleRows = rows.stream().filter(row -> row.widget().isVisible()).toList();
        int spacing = labeled ? 40 : 24;
        int maximum = Math.max(0, visibleRows.stream().mapToInt(row -> row.height(labeled)).sum() - getHeight() + 4);
        int offset = Math.clamp((int) Math.round(scrollBar.getScrollProgress() * previousMaximum), 0, maximum);
        if (maximum != previousMaximum) {
            scrollBar.setScrollProgressNoAnim(maximum == 0 ? 0 : (double) offset / maximum);
            previousMaximum = maximum;
        }
        if (visibleRows.stream().noneMatch(row -> row.input().isFocused())) {
            previousFocus = null;
        }
        for (int i = 0; i < visibleRows.size(); i++) {
            var input = visibleRows.get(i).input();
            if (input.isFocused() && input != previousFocus) {
                int top = visibleRows.subList(0, i).stream().mapToInt(row -> row.height(labeled)).sum();
                if (top < offset || top + visibleRows.get(i).height(labeled) - 2 > offset + getHeight()) {
                    offset = Math.clamp(top, 0, maximum);
                    scrollBar.setScrollProgressNoAnim(maximum == 0 ? 0 : (double) offset / maximum);
                }
                previousFocus = input;
            }
        }
        scrollBar.setVisible(maximum > 0);
        scrollBar.setActive(maximum > 0);
        if (maximum > 0) {
            scrollBar.setScrollRatio(spacing, maximum);
        }
        enableScissors(gui);
        try {
            int top = 0;
            for (int i = 0; i < visibleRows.size(); i++) {
                Row row = visibleRows.get(i);
                int y = getY() + 4 + top - offset;
                boolean inline = row.labelWidth() > 0;
                row.widget().setX(getX() + 4 + row.labelWidth());
                row.widget().setY(y + (labeled && !inline ? 13 : 0));
                if (labeled) {
                    UIHelper.renderScrollingText(gui, row.label(), getX() + 4, y + (inline ? 6 : 0),
                            inline ? row.labelWidth() - 4 : getWidth() - 22, 0xffbac6d8);
                }
                row.widget().extractRenderState(gui, isInsideScissors(mouseX, mouseY) ? mouseX : -1,
                        isInsideScissors(mouseX, mouseY) ? mouseY : -1, delta);
                top += row.height(labeled);
            }
        } finally {
            gui.disableScissor();
        }
        scrollBar.extractRenderState(gui, mouseX, mouseY, delta);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        return isInsideScissors(event.x(), event.y()) && super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseScrolled(double x, double y, double horizontal, double vertical) {
        return isMouseOver(x, y) && super.mouseScrolled(x, y, horizontal, vertical);
    }
}
