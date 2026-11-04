package top.aihmc.omnimorph.model.rendering;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.rendertype.RenderType;

import java.util.function.Function;

/** Material-specific vertex emission during native feature preparation; owns no buffers or GPU resources. */
@FunctionalInterface
public interface FiguraVertexSource {
    VertexConsumer getBuffer(RenderType material);

    /** The delegate participates in native prepare/upload/execute, including deferred material draws. */
    static FiguraVertexSource nativePreparation(Function<RenderType, VertexConsumer> vertices) {
        return new NativePreparation(vertices);
    }

    final class NativePreparation implements FiguraVertexSource {
        private final Function<RenderType, VertexConsumer> vertices;

        private NativePreparation(Function<RenderType, VertexConsumer> vertices) {
            this.vertices = vertices;
        }

        @Override
        public VertexConsumer getBuffer(RenderType material) {
            return vertices.apply(material);
        }
    }
}
