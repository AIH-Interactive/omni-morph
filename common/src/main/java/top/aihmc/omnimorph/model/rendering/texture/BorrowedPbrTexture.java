package top.aihmc.omnimorph.model.rendering.texture;

import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.server.packs.resources.ResourceManager;
import org.figuramc.figura.model.rendering.texture.FiguraTexture;

/** A non-owning Iris view of authored pixels, whose GL lifetime remains with FiguraTexture. */
public final class BorrowedPbrTexture extends AbstractTexture {
    private final FiguraTexture source;

    public BorrowedPbrTexture(FiguraTexture source) {
        this.source = source;
    }

    @Override
    public int getId() {
        source.uploadIfDirty(false, false);
        return source.getId();
    }

    @Override
    public void load(ResourceManager resources) {
        // The owning FiguraTexture loads its model resource.
    }

    @Override
    public void releaseId() {
        // Iris holder retirement must not delete the borrowed GL texture.
    }

    @Override
    public void close() {
        // The owner handles upload, reload and retirement.
    }
}
