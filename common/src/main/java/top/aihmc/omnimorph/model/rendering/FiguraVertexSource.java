package top.aihmc.omnimorph.model.rendering;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.rendertype.RenderType;

/** Material-specific ordinary vertex emission during native feature preparation. */
@FunctionalInterface
public interface FiguraVertexSource {
    VertexConsumer getBuffer(RenderType material);
}
