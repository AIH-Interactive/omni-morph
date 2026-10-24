package top.aihmc.omnimorph.gui.widgets.wardrobe;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Renderable;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.input.MouseButtonEvent;
import org.figuramc.figura.gui.widgets.FiguraWidget;
import org.figuramc.figura.gui.widgets.lists.AbstractList;

import java.util.ArrayList;
import java.util.List;

/** Keeps instance fields inside the right pane when GUI scaling leaves little vertical space. */
public final class WardrobeEditorPanel extends AbstractList {
    private record Entry(FiguraWidget widget, int x, int y) {}

    private final List<Entry> entries = new ArrayList<>();
    private final List<GuiEventListener> content = new ArrayList<>();
    private int contentHeight;

    public WardrobeEditorPanel(int x, int y, int width, int height) {
        super(x, y, width, height);
    }

    public <T extends FiguraWidget & GuiEventListener> T add(T widget, int x, int y) {
        entries.add(new Entry(widget, x, y));
        content.add(widget);
        children.add(widget);
        contentHeight = Math.max(contentHeight, y + widget.getHeight());
        arrange();
        return widget;
    }

    @Override
    public List<? extends GuiEventListener> contents() {
        return content;
    }

    private void arrange() {
        int overflow = Math.max(0, contentHeight + 4 - getHeight());
        scrollBar.setVisible(overflow > 0);
        scrollBar.setScrollRatio(20, Math.max(1, overflow));
        scrollBar.setX(getX() + getWidth() - 12);
        scrollBar.setY(getY() + 2);
        scrollBar.setHeight(Math.max(12, getHeight() - 4));
        int offset = (int) Math.round(scrollBar.getScrollProgress() * overflow);
        for (Entry entry : entries) {
            entry.widget().setX(getX() + entry.x());
            entry.widget().setY(getY() + entry.y() - offset);
        }
    }

    @Override
    public void tick() {
        arrange();
        super.tick();
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor gui, int mouseX, int mouseY, float delta) {
        arrange();
        super.extractRenderState(gui, mouseX, mouseY, delta);
        enableScissors(gui);
        try {
            for (GuiEventListener widget : content) {
                if (widget instanceof Renderable renderable) {
                    renderable.extractRenderState(gui, mouseX, mouseY, delta);
                }
            }
        } finally {
            gui.disableScissor();
        }
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        return isInsideScissors(event.x(), event.y()) && super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontal, double vertical) {
        return isMouseOver(mouseX, mouseY) && super.mouseScrolled(mouseX, mouseY, horizontal, vertical);
    }
}
