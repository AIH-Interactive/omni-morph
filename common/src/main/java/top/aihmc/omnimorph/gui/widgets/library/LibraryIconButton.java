package top.aihmc.omnimorph.gui.widgets.library;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.figuramc.figura.gui.widgets.Button;
import org.figuramc.figura.utils.FiguraIdentifier;

import java.util.function.IntSupplier;

/** Figura button behavior and narration with generated, three-state pixel artwork. */
public final class LibraryIconButton extends Button {
    private final Identifier icon;
    private final int variants;
    private final IntSupplier variant;
    private final Component detail;

    public LibraryIconButton(String icon, int variants, IntSupplier variant, Component text,
                             Component detail, OnPress action) {
        super(0, 0, 24, 24, text, detail == null ? text : text.copy().append("\n").append(detail), action);
        this.icon = new FiguraIdentifier("textures/gui/" + icon + ".png");
        this.variants = variants;
        this.variant = variant;
        this.detail = detail;
    }

    @Override
    public void setMessage(Component message) {
        super.setMessage(message);
        setTooltip(detail == null ? message : message.copy().append("\n").append(detail));
    }

    @Override
    protected void renderDefaultTexture(GuiGraphicsExtractor gui, float delta) {
        super.renderDefaultTexture(gui, delta);
        int row = Math.clamp(variant.getAsInt(), 0, variants - 1);
        gui.blit(RenderPipelines.GUI_TEXTURED, icon, getX() + (getWidth() - 24) / 2,
                getY() + (getHeight() - 24) / 2, getU() * 24, row * 24,
                24, 24, 72, variants * 24);
    }

    @Override
    protected void renderText(GuiGraphicsExtractor gui, float delta) {
        // The label remains available for keyboard narration and hover hints.
    }
}
