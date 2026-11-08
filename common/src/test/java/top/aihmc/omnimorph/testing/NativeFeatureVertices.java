package top.aihmc.omnimorph.testing;

import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.StagedVertexBuffer;
import net.minecraft.client.renderer.rendertype.PreparedRenderType;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import top.aihmc.omnimorph.model.rendering.FiguraVertexSource;

import java.util.IdentityHashMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;

/** CPU vertex storage and a recording platform boundary; real prepared records use the production Mixin. */
public final class NativeFeatureVertices {
    private static final Map<RenderType, RenderType> MATERIALS = new IdentityHashMap<>();
    private static final ThreadLocal<Runnable> JAVA_DRAW = new ThreadLocal<>();
    private final ByteBufferBuilder memory;
    private record Draw(RenderType material, MeshData geometry) { }
    private final List<Draw> draws = new ArrayList<>();
    private RenderType activeMaterial;
    private BufferBuilder activeBuilder;
    private final FiguraVertexSource source = this::getBuffer;
    private final BiConsumer<RenderType, MeshData> javaDraw;

    public NativeFeatureVertices(ByteBufferBuilder memory) {
        this(memory, (material, geometry) -> geometry.close());
    }

    public NativeFeatureVertices(ByteBufferBuilder memory, BiConsumer<RenderType, MeshData> javaDraw) {
        this.memory = memory;
        this.javaDraw = javaDraw;
    }

    public FiguraVertexSource source() {
        return source;
    }

    public VertexConsumer getBuffer(RenderType material) {
        if (activeMaterial != material) {
            finishGeometry();
            activeMaterial = material;
            activeBuilder = new BufferBuilder(memory, material.primitiveTopology(), material.format());
        }
        return activeBuilder;
    }

    private void finishGeometry() {
        if (activeBuilder != null) {
            MeshData geometry = activeBuilder.build();
            if (geometry != null) {
                draws.add(new Draw(activeMaterial, geometry));
            }
            activeMaterial = null;
            activeBuilder = null;
        }
    }

    public void execute() {
        finishGeometry();
        var pending = List.copyOf(draws);
        draws.clear();
        for (Draw draw : pending) {
            MeshData geometry = draw.geometry();
            boolean[] javaIssued = {false};
            Runnable previous = JAVA_DRAW.get();
            JAVA_DRAW.set(() -> {
                javaIssued[0] = true;
                javaDraw.accept(draw.material(), geometry);
            });
            try {
                PreparedRenderType prepared = draw.material().prepare();
                prepared.drawFromBuffer(new StagedVertexBuffer.ExecuteInfo(
                        null, null, null, 0, 0, geometry.drawState().indexCount(),
                        draw.material().primitiveTopology()), null);
            } finally {
                if (previous == null) {
                    JAVA_DRAW.remove();
                } else {
                    JAVA_DRAW.set(previous);
                }
                if (!javaIssued[0]) {
                    geometry.close();
                }
            }
        }
    }

    public static void drawJava() {
        java.util.Objects.requireNonNull(JAVA_DRAW.get(), "No offline Java draw is active").run();
    }

    /** Replace only platform state preparation. Keep the original pipeline, format and sorting policy. */
    public static RenderType material(RenderType original) {
        return MATERIALS.computeIfAbsent(original, RecordingMaterial::new);
    }

    private static final class RecordingMaterial extends RenderType {
        RecordingMaterial(RenderType original) {
            super("offline_recording_" + original, setup(original));
        }

        private static RenderSetup setup(RenderType original) {
            try {
                var field = RenderType.class.getDeclaredField("state");
                field.setAccessible(true);
                return (RenderSetup) field.get(original);
            } catch (ReflectiveOperationException failure) {
                throw new AssertionError("Cannot capture original material setup", failure);
            }
        }

        @Override
        public PreparedRenderType prepare() {
            return new PreparedRenderType("offline", pipeline(), null, null, null, List.of());
        }
    }
}
