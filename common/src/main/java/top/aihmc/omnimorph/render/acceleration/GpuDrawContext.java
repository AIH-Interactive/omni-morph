package top.aihmc.omnimorph.render.acceleration;

import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.textures.GpuTextureView;
import net.minecraft.client.renderer.rendertype.RenderSetup;

import java.util.Map;

/** Captured at Minecraft's actual material draw boundary; target/texture ownership remains with Minecraft. */
public record GpuDrawContext(GpuTextureView color, GpuTextureView depth,
                             Map<String, RenderSetup.TextureAndSampler> textures,
                             GpuBufferSlice dynamicTransform, boolean scissorEnabled,
                             int scissorX, int scissorY, int scissorWidth, int scissorHeight) {
    public GpuDrawContext {
        textures = Map.copyOf(textures);
    }
}
