package top.aihmc.omnimorph.model.rendering.material;

import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.vertex.VertexFormatElement;
import org.lwjgl.system.MemoryUtil;

/** Additional floating-point attributes; native overlay and lightmap channels stay intact. */
public final class MaterialVertexFormat {
    public static final VertexFormatElement SECONDARY_UV = register(2);
    public static final VertexFormatElement TIME = register(1);
    public static final VertexFormatElement ORIGIN = register(3);
    public static final VertexFormat FORMAT = VertexFormat.builder()
            .add("Position", VertexFormatElement.POSITION)
            .add("Color", VertexFormatElement.COLOR)
            .add("UV0", VertexFormatElement.UV0)
            .add("UV1", VertexFormatElement.UV1)
            .add("UV2", VertexFormatElement.UV2)
            .add("Normal", VertexFormatElement.NORMAL)
            .padding(1)
            .add("SecondaryUV", SECONDARY_UV)
            .add("MaterialTime", TIME)
            .add("MaterialOrigin", ORIGIN)
            .build();

    /** Explicit no-draw sink: custom attributes are discarded together with ordinary vertex fields. */
    public interface DiscardingConsumer extends VertexConsumer {
    }

    private MaterialVertexFormat() {
    }

    private static VertexFormatElement register(int components) {
        for (int id = 0; id < VertexFormatElement.MAX_COUNT; id++) {
            if (VertexFormatElement.byId(id) == null) {
                return VertexFormatElement.register(id, 0, VertexFormatElement.Type.FLOAT,
                        VertexFormatElement.Usage.GENERIC, components);
            }
        }
        throw new IllegalStateException("No free native vertex attribute identifier");
    }

    public static void write(VertexConsumer consumer, float u, float v, float time,
                             float originX, float originY, float originZ) {
        if (consumer instanceof DiscardingConsumer) {
            return;
        }
        if (!(consumer instanceof BufferBuilder builder)) {
            throw new IllegalStateException("Shader material requires the native BufferBuilder");
        }
        long uv = builder.beginElement(SECONDARY_UV);
        long clock = builder.beginElement(TIME);
        long origin = builder.beginElement(ORIGIN);
        if (uv == -1 || clock == -1 || origin == -1) {
            throw new IllegalStateException("Shader vertex attributes are missing or already written");
        }
        MemoryUtil.memPutFloat(uv, u);
        MemoryUtil.memPutFloat(uv + Float.BYTES, v);
        MemoryUtil.memPutFloat(clock, time);
        MemoryUtil.memPutFloat(origin, originX);
        MemoryUtil.memPutFloat(origin + Float.BYTES, originY);
        MemoryUtil.memPutFloat(origin + 2 * Float.BYTES, originZ);
    }
}
