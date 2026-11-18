package top.aihmc.omnimorph.model.rendering.material;

import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.vertex.VertexFormatElement;
import com.google.common.collect.ImmutableMap;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;

/** Additional floating-point attributes; native overlay and lightmap channels stay intact. */
public final class MaterialVertexFormat {
    public static final VertexFormatElement SECONDARY_UV = register(3, 2);
    public static final VertexFormatElement TIME = register(4, 1);
    public static final VertexFormatElement ORIGIN = register(5, 3);
    public static final VertexFormat FORMAT = new VertexFormat(ImmutableMap.<String, VertexFormatElement>builder()
            .put("Position", DefaultVertexFormat.ELEMENT_POSITION)
            .put("Color", DefaultVertexFormat.ELEMENT_COLOR)
            .put("UV0", DefaultVertexFormat.ELEMENT_UV0)
            .put("UV1", DefaultVertexFormat.ELEMENT_UV1)
            .put("UV2", DefaultVertexFormat.ELEMENT_UV2)
            .put("Normal", DefaultVertexFormat.ELEMENT_NORMAL)
            .put("Padding", DefaultVertexFormat.ELEMENT_PADDING)
            .put("SecondaryUV", SECONDARY_UV)
            .put("MaterialTime", TIME)
            .put("MaterialOrigin", ORIGIN)
            .build());

    /** Explicit no-draw sink: custom attributes are discarded together with ordinary vertex fields. */
    public interface DiscardingConsumer extends VertexConsumer {
    }

    private MaterialVertexFormat() {
    }

    private static VertexFormatElement register(int index, int components) {
        return new VertexFormatElement(index, VertexFormatElement.Type.FLOAT,
                VertexFormatElement.Usage.UV, components);
    }

    public static void write(VertexConsumer consumer, float u, float v, float time,
                             float originX, float originY, float originZ) {
        if (consumer instanceof DiscardingConsumer) {
            return;
        }
        if (!(consumer instanceof BufferBuilder builder)) {
            throw new IllegalStateException("Shader material requires the native BufferBuilder");
        }
        writeElement(builder, SECONDARY_UV, u, v);
        writeElement(builder, TIME, time);
        writeElement(builder, ORIGIN, originX, originY, originZ);
    }

    private static void writeElement(BufferBuilder builder, VertexFormatElement element, float... values) {
        if (!element.equals(builder.currentElement())) {
            throw new IllegalStateException("Shader vertex attributes are missing or already written");
        }
        for (int index = 0; index < values.length; index++) {
            builder.putFloat(index * Float.BYTES, values[index]);
        }
        builder.nextElement();
    }
}
