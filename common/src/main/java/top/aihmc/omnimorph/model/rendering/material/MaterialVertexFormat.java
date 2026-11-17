package top.aihmc.omnimorph.model.rendering.material;

import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.renderpearl.api.vertex.VertexFormat;
import com.mojang.renderpearl.api.vertex.VertexFormatElement;
import org.lwjgl.system.MemoryUtil;
import com.mojang.renderpearl.api.GpuFormat;

/** Additional floating-point attributes; native overlay and lightmap channels stay intact. */
public final class MaterialVertexFormat {
    public static final VertexFormat FORMAT = VertexFormat.builder(0)
            .addAttribute("Position", GpuFormat.RGB32_FLOAT)
            .addAttribute("Color", GpuFormat.RGBA8_UNORM)
            .addAttribute("UV0", GpuFormat.RG32_FLOAT)
            .addAttribute("UV1", GpuFormat.RG16_SINT)
            .addAttribute("UV2", GpuFormat.RG16_SINT)
            .addAttribute("Normal", 4, GpuFormat.RGB8_SNORM)
            .addAttribute("SecondaryUV", GpuFormat.RG32_FLOAT)
            .addAttribute("MaterialTime", GpuFormat.R32_FLOAT)
            .addAttribute("MaterialOrigin", GpuFormat.RGB32_FLOAT)
            .build();
    public static final VertexFormatElement SECONDARY_UV = FORMAT.getElement("SecondaryUV");
    public static final VertexFormatElement TIME = FORMAT.getElement("MaterialTime");
    public static final VertexFormatElement ORIGIN = FORMAT.getElement("MaterialOrigin");

    /** Explicit no-draw sink: custom attributes are discarded together with ordinary vertex fields. */
    public interface DiscardingConsumer extends VertexConsumer {
    }

    private MaterialVertexFormat() {
    }

    public static void write(VertexConsumer consumer, float u, float v, float time,
                             float originX, float originY, float originZ) {
        if (consumer instanceof DiscardingConsumer) {
            return;
        }
        if (!(consumer instanceof BufferBuilder builder)) {
            throw new IllegalStateException("Shader material requires the native BufferBuilder");
        }
        if (builder.format != FORMAT || builder.vertexPointer == -1) {
            throw new IllegalStateException("Shader vertex attributes are missing or already written");
        }
        long uv = builder.vertexPointer + SECONDARY_UV.offset();
        long clock = builder.vertexPointer + TIME.offset();
        long origin = builder.vertexPointer + ORIGIN.offset();
        MemoryUtil.memPutFloat(uv, u);
        MemoryUtil.memPutFloat(uv + Float.BYTES, v);
        MemoryUtil.memPutFloat(clock, time);
        MemoryUtil.memPutFloat(origin, originX);
        MemoryUtil.memPutFloat(origin + Float.BYTES, originY);
        MemoryUtil.memPutFloat(origin + 2 * Float.BYTES, originZ);
    }
}
