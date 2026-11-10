package top.aihmc.omnimorph.gui.widgets.library;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import org.figuramc.figura.gui.widgets.AbstractContainerElement;
import org.figuramc.figura.gui.widgets.Button;
import org.figuramc.figura.utils.FiguraIdentifier;
import org.figuramc.figura.utils.FiguraText;
import org.figuramc.figura.utils.ui.UIHelper;

import java.util.function.Supplier;

/** Existing submitted cover with explicit replacement and removal controls. */
public final class LibraryCoverChooser extends AbstractContainerElement {
    private final Supplier<Identifier> texture;
    private final Button choose;
    private final Button clear;

    public LibraryCoverChooser(int width, Supplier<Identifier> texture, Runnable chooseImage, Runnable clearImage,
                               boolean editable) {
        super(0, 0, width, 64);
        this.texture = texture;
        int actionsWidth = Math.min(90, width - 72);
        choose = new Button(0, 0, actionsWidth, 18, FiguraText.of("gui.library.choose_image"), null,
                ignored -> chooseImage.run());
        clear = new Button(0, 0, actionsWidth, 18, FiguraText.of("gui.library.clear_image"), null,
                ignored -> clearImage.run());
        choose.setActive(editable);
        clear.setActive(editable);
        children.add(choose);
        children.add(clear);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor gui, int mouseX, int mouseY, float delta) {
        UIHelper.blitSliced(gui, getX(), getY(), 64, 64, UIHelper.OUTLINE_FILL);
        Identifier image = texture.get();
        if (image == null) {
            image = new FiguraIdentifier("textures/gui/library_placeholder.png");
        }
        gui.blit(RenderPipelines.GUI_TEXTURED, image, getX() + 2, getY() + 2, 0, 0, 60, 60, 60, 60);
        choose.setX(getX() + 72);
        choose.setY(getY() + 12);
        clear.setX(getX() + 72);
        clear.setY(getY() + 36);
        super.extractRenderState(gui, mouseX, mouseY, delta);
    }
}
