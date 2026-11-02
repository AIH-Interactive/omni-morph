package top.aihmc.omnimorph.render.acceleration;

import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import com.mojang.blaze3d.systems.RenderSystem;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/** Owned by one BufferSource; GPU work shares that source's existing material flush boundary. */
public final class GpuBufferQueue {
    public interface Owner {
        GpuBufferQueue omniMorph$gpuQueue();
    }

    private record Entry(DrawBatch batch, GpuSubmission.JavaEmitter fallback) {}

    private final Map<RenderType, List<Entry>> pending = new IdentityHashMap<>();

    public boolean contains(RenderType material) {
        return pending.containsKey(material);
    }

    boolean hasCapacity(RenderType material, DrawBatch batch) {
        List<Entry> entries = pending.getOrDefault(material, List.of());
        long vertices = entries.stream().mapToLong(entry -> entry.batch().mesh().vertexCount()).sum();
        return entries.size() < 256 && vertices + batch.mesh().vertexCount() <= 262_144;
    }

    void add(DrawBatch batch, GpuSubmission.JavaEmitter fallback) {
        pending.values().forEach(entries -> entries.removeIf(entry ->
                !AccelerationService.INSTANCE.isCurrent(entry.batch().lease())));
        pending.computeIfAbsent(batch.semantics().material(), ignored -> new ArrayList<>())
                .add(new Entry(batch, fallback));
    }

    public void emitNative(MultiBufferSource source, RenderType material) {
        List<Entry> entries = pending.remove(material);
        if (entries == null) {
            return;
        }
        emit(entries, source.getBuffer(material));
    }

    public void emitNative(VertexConsumer vertices, RenderType material) {
        List<Entry> entries = pending.remove(material);
        if (entries != null) {
            emit(entries, vertices);
        }
    }

    private static void emit(List<Entry> entries, VertexConsumer vertices) {
        for (Entry entry : entries) {
            if (AccelerationService.INSTANCE.isCurrent(entry.batch().lease())) {
                entry.fallback().emit(vertices, 0, entry.batch().mesh().vertexCount());
            }
        }
    }

    public void flush(RenderType material, BufferBuilder vertices) {
        List<Entry> entries = pending.remove(material);
        if (entries == null) {
            return;
        }
        AccelerationService service = AccelerationService.INSTANCE;
        entries.removeIf(entry -> !service.isCurrent(entry.batch().lease()));
        if (entries.isEmpty()) {
            return;
        }
        if (!service.drawBatches(entries.stream().map(Entry::batch).toList())) {
            // Re-enter the original material renderer without recursing into BufferSource hooks.
            // BufferSource still owns this builder; the preceding native batch has ended.
            vertices.begin(material.mode(), material.format());
            try {
                emit(entries, vertices);
                material.end(vertices, RenderSystem.getVertexSorting());
            } finally {
                if (vertices.building()) {
                    // An emitter failure must not leave the borrowed builder inside begin/end.
                    vertices.end().release();
                }
            }
        }
    }
}
