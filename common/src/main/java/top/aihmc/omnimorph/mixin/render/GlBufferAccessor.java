package top.aihmc.omnimorph.mixin.render;

import com.mojang.blaze3d.opengl.GlBuffer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Exposes the GL buffer name so GPU acceleration can bind pose storage blocks, which Blaze3D cannot. */
@Mixin(GlBuffer.class)
public interface GlBufferAccessor {
    @Accessor("handle")
    int omnimorph$handle();
}
