package top.aihmc.omnimorph.model.rendering;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.rendertype.RenderType;

import java.util.function.Supplier;

public final class ConsecutiveVertexBuffer implements Supplier<VertexConsumer> {
    private final MultiBufferSource source;
    private final RenderType type;
    private final boolean reusable;
    private VertexConsumer consumer;

    // Each instance belongs to one uninterrupted submission of one render type.
    public ConsecutiveVertexBuffer(MultiBufferSource source, RenderType type) {
        this.source = source;
        this.type = type;
        reusable = source.getClass() == MultiBufferSource.BufferSource.class
                && type.canConsolidateConsecutiveGeometry();
    }

    @Override
    public VertexConsumer get() {
        if (!reusable) {
            return source.getBuffer(type);
        }
        if (consumer == null) {
            consumer = source.getBuffer(type);
        }
        return consumer;
    }
}
