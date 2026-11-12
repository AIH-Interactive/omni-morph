package top.aihmc.omnimorph.model.rendering.texture;

import com.mojang.blaze3d.textures.GpuSampler;
import com.mojang.blaze3d.textures.GpuTexture;
import com.mojang.blaze3d.textures.GpuTextureView;
import net.minecraft.client.renderer.texture.AbstractTexture;
import org.figuramc.figura.model.rendering.texture.FiguraTexture;

/** Iris may close its holders; the model remains the owner of these live authored pixels. */
public final class BorrowedPbrTexture extends AbstractTexture {
    private final FiguraTexture source;

    public BorrowedPbrTexture(FiguraTexture source) {
        this.source = source;
    }

    @Override
    public GpuTexture getTexture() {
        return source.getTexture();
    }

    @Override
    public GpuTextureView getTextureView() {
        return source.getTextureView();
    }

    @Override
    public GpuSampler getSampler() {
        return source.getSampler();
    }

    @Override
    public void close() {
        // This view allocates nothing. FiguraTexture handles upload, reload and retirement.
    }
}
