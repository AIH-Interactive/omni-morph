package top.aihmc.omnimorph.gui.widgets.library;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.MouseButtonEvent;
import org.figuramc.figura.gui.widgets.AbstractContainerElement;
import org.figuramc.figura.gui.widgets.Button;
import org.figuramc.figura.utils.FiguraText;
import org.figuramc.figura.FiguraMod;
import top.aihmc.omnimorph.gui.widgets.wardrobe.VerticalWardrobeTab;

import java.util.List;
import java.util.function.IntConsumer;

/**
 * The five reference sections as a wardrobe-style vertical tab column, using Figura buttons and their
 * normal focus/keyboard behavior. The column scrolls on its own when the window is too short.
 */
public final class LibrarySectionTabs extends AbstractContainerElement {
    private static final List<String> KEYS = List.of("scope.mine", "scope.favorites", "scope.public", "upload", "manage");
    private boolean administrator;
    private int scroll;

    public LibrarySectionTabs(int x, int y, int height, int selected, boolean administrator, IntConsumer navigate) {
        super(x, y, LibrarySectionLayout.WIDTH, height);
        this.administrator = administrator;
        for (int index = 0; index < KEYS.size(); index++) {
            int section = index;
            children.add(new VerticalWardrobeTab(x, y, LibrarySectionLayout.TAB_HEIGHT,
                    FiguraText.of("gui.library." + KEYS.get(index)), ignored -> {
                        if (section != selected) {
                            navigate.accept(section);
                        }
                    }) {
                @Override
                protected void renderDefaultTexture(GuiGraphicsExtractor gui, float delta) {
                    super.renderDefaultTexture(gui, delta);
                    if (section == selected) {
                        gui.fill(getX(), getY() + 1, getX() + 1, getY() + getHeight() - 1,
                                FiguraMod.getAccentColor().getColor().getValue() | 0xff000000);
                    }
                }
            });
        }
        // Keep the current section visible when the column opens scrolled.
        int selectedBottom = selected * (LibrarySectionLayout.TAB_HEIGHT + LibrarySectionLayout.GAP)
                + LibrarySectionLayout.TAB_HEIGHT;
        scroll = layout().clampScroll(selectedBottom - height);
    }

    public Button button(int section) {
        return (Button) children.get(section);
    }

    public void administrator(boolean available) {
        administrator = available;
    }

    public void setEnabled(boolean enabled) {
        children.forEach(child -> ((Button) child).setActive(enabled));
    }

    private LibrarySectionLayout layout() {
        return new LibrarySectionLayout(getX(), getY(), getWidth(), getHeight(), administrator ? 5 : 4);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor gui, int mouseX, int mouseY, float delta) {
        var layout = layout();
        scroll = layout.clampScroll(scroll);
        for (int index = 0; index < children.size(); index++) {
            Button button = button(index);
            int tabY = layout.tabY(index, scroll);
            button.setVisible(index < layout.count() && tabY + layout.tabHeight() > getY() && tabY < getY() + getHeight());
            button.setX(layout.tabX(index));
            button.setY(tabY);
        }
        gui.enableScissor(getX(), getY(), getX() + getWidth(), getY() + getHeight());
        try {
            super.extractRenderState(gui, mouseX, mouseY, delta);
        } finally {
            gui.disableScissor();
        }
        if (layout.maximumScroll() > 0) {
            renderScrollbar(gui, layout);
        }
    }

    private void renderScrollbar(GuiGraphicsExtractor gui, LibrarySectionLayout layout) {
        int trackX = getX() + LibrarySectionLayout.TAB_WIDTH + 4;
        gui.fill(trackX, getY(), trackX + 3, getY() + getHeight(), 0x66000000);
        int thumbHeight = Math.max(8, getHeight() * getHeight() / layout.contentHeight());
        int thumbY = getY() + (getHeight() - thumbHeight) * scroll / layout.maximumScroll();
        gui.fill(trackX + 1, thumbY, trackX + 2, thumbY + thumbHeight, 0xffcccccc);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        // Clipped tabs keep their widget bounds, so only the shared geometry decides a hit.
        if (layout().hit(event.x(), event.y(), scroll) == -1) {
            return false;
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontal, double vertical) {
        var layout = layout();
        if (!isMouseOver(mouseX, mouseY) || layout.maximumScroll() == 0) {
            return false;
        }
        scroll = layout.clampScroll(scroll - (int) Math.round(vertical * 16));
        return true;
    }
}
