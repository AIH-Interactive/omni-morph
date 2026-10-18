package top.aihmc.omnimorph.gui.widgets.library;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import top.aihmc.omnimorph.gui.widgets.Button;
import top.aihmc.omnimorph.gui.widgets.lists.AbstractList;

import java.util.List;

/** Shared clipped row selector for file, authorization and management lists. */
public final class LibraryRowList extends AbstractList {
    public record Row(Component text, Component tooltip, boolean enabled, Runnable action) {}
    private List<Row> rows = List.of();
    private int first = -1;

    public LibraryRowList(int x, int y, int width, int height) {
        super(x, y, width, height);
    }

    public void rows(List<Row> value) {
        rows = List.copyOf(value);
        first = -1;
        scrollBar.setScrollProgressNoAnim(0);
    }

    private void arrange() {
        int maximum = Math.max(0, rows.size() * 24 - getHeight() + 4);
        int offset = (int) Math.round(scrollBar.getScrollProgress() * maximum);
        scrollBar.setVisible(maximum > 0);
        scrollBar.setActive(maximum > 0);
        if (maximum > 0) {
            scrollBar.setScrollRatio(24, maximum);
        }
        int start = offset / 24;
        if (first != start) {
            first = start;
            children.clear();
            children.add(scrollBar);
            int end = Math.min(rows.size(), start + (getHeight() + 23) / 24 + 1);
            for (int i = start; i < end; i++) {
                Row row = rows.get(i);
                var button = new Button(getX() + 3, 0, getWidth() - 20, 20, row.text(), row.tooltip(),
                        ignored -> row.action().run());
                button.setActive(row.enabled());
                children.add(button);
            }
        }
        for (int i = 1; i < children.size(); i++) {
            ((Button) children.get(i)).setY(getY() + 2 + (first + i - 1) * 24 - offset);
        }
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor gui, int mouseX, int mouseY, float delta) {
        arrange();
        enableScissors(gui);
        try {
            for (int i = 1; i < children.size(); i++) {
                ((Button) children.get(i)).extractRenderState(gui,
                        isInsideScissors(mouseX, mouseY) ? mouseX : -1,
                        isInsideScissors(mouseX, mouseY) ? mouseY : -1, delta);
            }
        } finally {
            gui.disableScissor();
        }
        scrollBar.extractRenderState(gui, mouseX, mouseY, delta);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        arrange();
        return isInsideScissors(event.x(), event.y()) && super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseScrolled(double x, double y, double horizontal, double vertical) {
        return isMouseOver(x, y) && super.mouseScrolled(x, y, horizontal, vertical);
    }
}
