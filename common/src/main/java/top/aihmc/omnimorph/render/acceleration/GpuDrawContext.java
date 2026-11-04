package top.aihmc.omnimorph.render.acceleration;

import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.textures.GpuTextureView;
import net.minecraft.client.renderer.rendertype.PreparedRenderType;

import java.util.List;

/** Captured at Minecraft's actual material draw boundary; target/texture ownership remains with Minecraft. */
public record GpuDrawContext(GpuTextureView color, GpuTextureView depth,
                             List<PreparedRenderType.Texture> textures,
                             GpuBufferSlice dynamicTransform, boolean scissorEnabled,
                             int scissorX, int scissorY, int scissorWidth, int scissorHeight) {
    public GpuDrawContext {
        textures = List.copyOf(textures);
    }
}
