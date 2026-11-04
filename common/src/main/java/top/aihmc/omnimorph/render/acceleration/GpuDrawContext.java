package top.aihmc.omnimorph.render.acceleration;

import com.mojang.renderpearl.api.buffers.GpuBufferSlice;
import com.mojang.renderpearl.api.commands.RenderPass;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import net.minecraft.client.renderer.rendertype.PreparedRenderType;

import java.util.List;

/** Captured at Minecraft's actual material draw boundary; target/texture ownership remains with Minecraft. */
public record GpuDrawContext(RenderPass pass, RenderPipeline pipeline,
                             List<PreparedRenderType.Texture> textures,
                             GpuBufferSlice dynamicTransform, boolean scissorEnabled,
                             int scissorX, int scissorY, int scissorWidth, int scissorHeight) {
    public GpuDrawContext {
        textures = List.copyOf(textures);
    }
}
