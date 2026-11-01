package top.aihmc.omnimorph.lua.api.particle;

import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.vertex.VertexFormatElement;
import net.minecraft.world.phys.Vec3;

import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/** Captures the actual 1.20.1 native PARTICLE buffer, without reconstructing removed render states. */
public final class ParticleVertexCapture implements
        BiConsumer<CustomParticlePipelines.Material, Consumer<VertexConsumer>> {
    record Vertex(Vec3 position, float u, float v, int color, int light) {
    }

    record Batch(CustomParticlePipelines.Material material, List<Vertex> vertices) {
        Vec3 center() {
            Vec3 total = Vec3.ZERO;
            for (Vertex vertex : vertices) {
                total = total.add(vertex.position());
            }
            return total.scale(1.0 / vertices.size());
        }

        float u0() {
            return (float) vertices.stream().mapToDouble(Vertex::u).min().orElseThrow();
        }

        float u1() {
            return (float) vertices.stream().mapToDouble(Vertex::u).max().orElseThrow();
        }

        float v0() {
            return (float) vertices.stream().mapToDouble(Vertex::v).min().orElseThrow();
        }

        float v1() {
            return (float) vertices.stream().mapToDouble(Vertex::v).max().orElseThrow();
        }
    }

    final List<Batch> batches = new ArrayList<>();

    public int quadCount() {
        return batches.size();
    }

    @Override
    public void accept(CustomParticlePipelines.Material material, Consumer<VertexConsumer> writer) {
        var format = DefaultVertexFormat.PARTICLE;
        var builder = new BufferBuilder(256);
        builder.begin(VertexFormat.Mode.QUADS, format);
        writer.accept(builder);
        var mesh = builder.end();
        try {
            if (mesh == null || mesh.drawState().vertexCount() != 4) {
                throw new AssertionError("A billboard layer must emit four native vertices");
            }
            var bytes = mesh.vertexBuffer().order(ByteOrder.nativeOrder());
            var vertices = new ArrayList<Vertex>(4);
            for (int index = 0; index < 4; index++) {
                int base = index * format.getVertexSize();
                int position = base + offset(format, DefaultVertexFormat.ELEMENT_POSITION);
                int uv = base + offset(format, DefaultVertexFormat.ELEMENT_UV0);
                int color = base + offset(format, DefaultVertexFormat.ELEMENT_COLOR);
                int light = base + offset(format, DefaultVertexFormat.ELEMENT_UV2);
                int argb = (bytes.get(color + 3) & 255) << 24 | (bytes.get(color) & 255) << 16
                        | (bytes.get(color + 1) & 255) << 8 | bytes.get(color + 2) & 255;
                vertices.add(new Vertex(new Vec3(bytes.getFloat(position), bytes.getFloat(position + 4),
                        bytes.getFloat(position + 8)), bytes.getFloat(uv), bytes.getFloat(uv + 4),
                        argb, (bytes.getShort(light) & 65535) | (bytes.getShort(light + 2) & 65535) << 16));
            }
            batches.add(new Batch(material, List.copyOf(vertices)));
        } finally {
            mesh.release();
        }
    }

    static int offset(VertexFormat format, VertexFormatElement target) {
        int offset = 0;
        for (VertexFormatElement element : format.getElements()) {
            if (element.equals(target)) {
                return offset;
            }
            offset += element.getByteSize();
        }
        throw new AssertionError("Missing native particle element: " + target);
    }
}
