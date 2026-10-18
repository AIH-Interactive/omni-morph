package top.aihmc.omnimorph.gui.widgets.library;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import top.aihmc.omnimorph.gui.widgets.AbstractContainerElement;
import top.aihmc.omnimorph.gui.widgets.Button;
import top.aihmc.omnimorph.utils.FiguraIdentifier;
import top.aihmc.omnimorph.utils.FiguraText;
import top.aihmc.omnimorph.utils.ui.UIHelper;

import java.util.function.Supplier;

/** Reference upload drop area and cover chooser, composed from existing Figura buttons. */
public final class LibraryUploadSource extends AbstractContainerElement {
    private final Button source;
    private final Button image;
    private final Button clear;
    private final Supplier<Identifier> cover;

    public LibraryUploadSource(int width, long maximumBytes, Supplier<Identifier> cover,
                               Runnable chooseSource, Runnable chooseImage, Runnable clearImage) {
        super(0, 0, width, 84);
        this.cover = cover;
        int imageWidth = Math.max(52, width / 3);
        int sourceWidth = width - imageWidth - 6;
        source = new Button(0, 0, sourceWidth, 84, FiguraText.of("gui.library.choose_model"),
                FiguraText.of("gui.library.upload_drop_hint", maximumBytes), ignored -> chooseSource.run()) {
            @Override
            protected void renderText(GuiGraphicsExtractor gui, float delta) {
                UIHelper.renderCenteredScrollingText(gui, Component.literal("+"), getX(), getY() + 10,
                        getWidth(), 18, getTextColor() | 0xff000000);
                UIHelper.renderCenteredScrollingText(gui, getMessage(), getX() + 4, getY() + 34,
                        getWidth() - 8, 18, getTextColor() | 0xff000000);
                UIHelper.renderCenteredScrollingText(gui, Component.literal(".zip / .ysm"), getX() + 4,
                        getY() + 56, getWidth() - 8, 16, 0xffbac6d8);
            }
        };
        image = new Button(0, 0, imageWidth - 20, 18, FiguraText.of("gui.library.choose_image"), null,
                ignored -> chooseImage.run());
        clear = new Button(0, 0, 18, 18, Component.literal("×"), FiguraText.of("gui.library.clear_image"),
                ignored -> clearImage.run());
        children.add(source);
        children.add(image);
        children.add(clear);
    }

    public Button sourceButton() {
        return source;
    }

    public Button imageButton() {
        return image;
    }

    public Button clearButton() {
        return clear;
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor gui, int mouseX, int mouseY, float delta) {
        source.setX(getX());
        source.setY(getY());
        int imageX = getX() + source.getWidth() + 6;
        int imageWidth = getWidth() - source.getWidth() - 6;
        image.setX(imageX);
        image.setY(getY() + 66);
        clear.setX(imageX + imageWidth - 18);
        clear.setY(getY() + 66);
        Identifier texture = cover.get();
        if (texture == null) {
            texture = new FiguraIdentifier("textures/gui/library_placeholder.png");
        }
        UIHelper.blitSliced(gui, imageX, getY(), imageWidth, 64, UIHelper.OUTLINE_FILL);
        int size = Math.min(imageWidth - 4, 60);
        gui.blit(RenderPipelines.GUI_TEXTURED, texture, imageX + (imageWidth - size) / 2,
                getY() + (64 - size) / 2, 0, 0, size, size, size, size);
        super.extractRenderState(gui, mouseX, mouseY, delta);
    }
}
