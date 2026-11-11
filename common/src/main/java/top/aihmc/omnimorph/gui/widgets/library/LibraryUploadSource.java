package top.aihmc.omnimorph.gui.widgets.library;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.figuramc.figura.gui.widgets.AbstractContainerElement;
import org.figuramc.figura.gui.widgets.Button;
import org.figuramc.figura.utils.FiguraIdentifier;
import org.figuramc.figura.utils.FiguraText;
import org.figuramc.figura.utils.ui.UIHelper;

import java.util.function.Supplier;

/** Reference upload drop area and cover chooser, composed from existing Figura buttons. */
public final class LibraryUploadSource extends AbstractContainerElement {
    private final Button source;
    private final Button image;
    private final Button clear;
    private final Supplier<ResourceLocation> cover;
    private Component sourceFormat = Component.literal(".zip / .ysm");

    public LibraryUploadSource(int width, long maximumBytes, Supplier<ResourceLocation> cover,
                               Runnable chooseSource, Runnable chooseImage, Runnable clearImage) {
        super(0, 0, width, 84);
        this.cover = cover;
        int imageWidth = Math.max(52, width / 3);
        int sourceWidth = width - imageWidth - 6;
        setHeight(imageWidth + 22);
        source = new Button(0, 0, sourceWidth, imageWidth, FiguraText.of("gui.library.choose_model"),
                FiguraText.of("gui.library.upload_drop_hint", LibraryLabels.fileSize(maximumBytes)), ignored -> chooseSource.run()) {
            @Override
            protected void renderText(GuiGraphics gui, float delta) {
                int lineHeight = Math.min(22, getHeight() / 3);
                int contentY = getY() + (getHeight() - lineHeight * 3) / 2;
                UIHelper.renderCenteredScrollingText(gui, Component.literal("+"), getX(), contentY,
                        getWidth(), lineHeight, getTextColor() | 0xff000000);
                UIHelper.renderCenteredScrollingText(gui, getMessage(), getX() + 4, contentY + lineHeight,
                        getWidth() - 8, lineHeight, getTextColor() | 0xff000000);
                UIHelper.renderCenteredScrollingText(gui, sourceFormat, getX() + 4,
                        contentY + lineHeight * 2, getWidth() - 8, lineHeight, 0xffbac6d8);
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

    public void sourceFormat(Component label, Component tooltip) {
        sourceFormat = label;
        source.setTooltip(tooltip);
    }

    public Button imageButton() {
        return image;
    }

    public Button clearButton() {
        return clear;
    }

    @Override
    public void render(GuiGraphics gui, int mouseX, int mouseY, float delta) {
        source.setX(getX());
        source.setY(getY());
        int imageX = getX() + source.getWidth() + 6;
        int imageWidth = getWidth() - source.getWidth() - 6;
        image.setX(imageX);
        image.setY(getY() + imageWidth + 4);
        clear.setX(imageX + imageWidth - 18);
        clear.setY(getY() + imageWidth + 4);
        ResourceLocation texture = cover.get();
        if (texture == null) {
            texture = FiguraIdentifier.of("textures/gui/library_placeholder.png");
        }
        UIHelper.blitSliced(gui, imageX, getY(), imageWidth, imageWidth, UIHelper.OUTLINE_FILL);
        int size = imageWidth - 4;
        gui.blit(texture, imageX + (imageWidth - size) / 2,
                getY() + (imageWidth - size) / 2, 0, 0, size, size, size, size);
        super.render(gui, mouseX, mouseY, delta);
    }
}
