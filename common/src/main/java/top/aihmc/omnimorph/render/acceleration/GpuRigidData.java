package top.aihmc.omnimorph.render.acceleration;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Packs consecutive, globally sorted rigid faces into bounded palettes without changing their order. */
final class GpuRigidData {
    static final int MAX_BATCHES = 4096;
    static final int REMAP_OFFSET = GpuBatchData.POSE_BYTES;
    static final int POSE_BYTES = REMAP_OFFSET + MAX_BATCHES;
    private static final int[] ATTRIBUTES = {0, 1, 2, 4, 5, 6, 7, 8};

    record Range(int firstFace, int faceCount, List<Integer> parts) {
        Range {
            parts = List.copyOf(parts);
        }
    }

    /**
     * Pose-independent structure of one cached group. It stays valid while the group geometry (enforced by
     * the cache key) and the sorted face order are unchanged; ranges are reused while parts are shared the
     * same way. Owned by a single render-thread cache entry.
     */
    static final class Layout {
        private final long[] order;
        private final int[] vertexOffsets;
        private final int vertices;
        private final boolean authored;
        private int[] batchParts;
        private List<Range> ranges;

        private Layout(List<DrawBatch> batches, long[] order) {
            this.order = order.clone();
            vertexOffsets = new int[batches.size()];
            int vertexCount = 0;
            for (int index = 0; index < batches.size(); index++) {
                vertexOffsets[index] = vertexCount;
                vertexCount = Math.addExact(vertexCount, batches.get(index).mesh().vertexCount());
            }
            vertices = vertexCount;
            for (long face : order) {
                int batchIndex = (int) (face >>> 32);
                int localFace = (int) face;
                if (batchIndex < 0 || batchIndex >= batches.size() || localFace < 0
                        || localFace >= batches.get(batchIndex).mesh().faceCount()) {
                    throw new IllegalArgumentException("Sorted rigid face outside its authored batch");
                }
            }
            authored = authoredOrder(batches, order);
        }

        private boolean matches(long[] candidate) {
            return Arrays.equals(order, candidate);
        }
    }

    private final List<DrawBatch> batches;
    private final Layout layout;
    private final int[] batchParts;
    private final List<ByteBuffer> parts = new ArrayList<>();
    private final boolean layoutReused;
    private final boolean rangesReused;

    GpuRigidData(List<DrawBatch> batches, long[] order) {
        this(batches, order, null);
    }

    GpuRigidData(List<DrawBatch> batches, long[] order, Layout previous) {
        this.batches = List.copyOf(batches);
        batchParts = new int[batches.size()];
        Map<ByteBuffer, Integer> unique = new HashMap<>();
        for (int index = 0; index < batches.size(); index++) {
            DrawBatch batch = batches.get(index);
            ByteBuffer part = ByteBuffer.allocate(GpuBatchData.PART_BYTES).order(ByteOrder.nativeOrder());
            GpuBatchData.writePart(part, batch.pose());
            // std140 padding carries options specific to this rigid part; xyz matrix components stay unchanged.
            part.putFloat(76, batch.pose().normalPolicy());
            part.putFloat(92, batch.pose().usesFloatPrecision() ? 1 : 0);
            part.putFloat(108, (float) batch.semantics().vertexOffset());
            part.flip();
            Integer id = unique.get(part);
            if (id == null) {
                id = parts.size();
                parts.add(part.asReadOnlyBuffer());
                unique.put(part, id);
            }
            batchParts[index] = id;
        }
        layoutReused = previous != null && previous.vertexOffsets.length == batches.size() && previous.matches(order);
        layout = layoutReused ? previous : new Layout(batches, order);
        rangesReused = Arrays.equals(layout.batchParts, batchParts);
        if (!rangesReused) {
            layout.ranges = buildRanges(layout.order, batchParts);
            layout.batchParts = batchParts.clone();
        }
    }

    private static List<Range> buildRanges(long[] order, int[] batchParts) {
        List<Range> ranges = new ArrayList<>();
        Map<Integer, Integer> palette = new LinkedHashMap<>();
        int first = 0;
        for (int face = 0; face < order.length; face++) {
            int part = batchParts[(int) (order[face] >>> 32)];
            if (!palette.containsKey(part) && palette.size() == GpuBatchData.MAX_PARTS) {
                ranges.add(new Range(first, face - first, new ArrayList<>(palette.keySet())));
                palette.clear();
                first = face;
            }
            palette.putIfAbsent(part, palette.size());
        }
        if (!palette.isEmpty()) {
            ranges.add(new Range(first, order.length - first, new ArrayList<>(palette.keySet())));
        }
        return List.copyOf(ranges);
    }

    Layout layout() {
        return layout;
    }

    boolean layoutReused() {
        return layoutReused;
    }

    boolean rangesReused() {
        return rangesReused;
    }
    static boolean supports(List<DrawBatch> batches) {
        if (batches.isEmpty() || batches.size() > MAX_BATCHES) {
            return false;
        }
        DrawBatch first = batches.getFirst();
        long vertices = 0;
        for (DrawBatch batch : batches) {
            DrawSemantics draw = batch.semantics();
            DrawSemantics reference = first.semantics();
            if (batch.lease().ownerGeneration() != first.lease().ownerGeneration()
                    || batch.pose().skinJointCount() != 0 || batch.mesh().verticesPerFace() != 4
                    || draw.material() != reference.material() || draw.light() != reference.light()
                    || draw.overlay() != reference.overlay() || !same(draw.red(), reference.red())
                    || !same(draw.green(), reference.green()) || !same(draw.blue(), reference.blue())
                    || !same(draw.alpha(), reference.alpha()) || !GpuBatchData.supports(batch)) {
                return false;
            }
            vertices += batch.mesh().vertexCount();
        }
        return vertices > 0 && vertices <= Integer.MAX_VALUE / GpuBatchData.VERTEX_BYTES;
    }

    List<Range> ranges() {
        return layout.ranges;
    }

    long geometryBytes() {
        return (long) layout.vertices * GpuBatchData.VERTEX_BYTES + 6 * Integer.BYTES;
    }

    long frameBytes() {
        return indexBytes() + poseBytes();
    }

    long indexBytes() {
        return (long) layout.order.length * 6 * Integer.BYTES;
    }

    long poseBytes() {
        return (long) layout.ranges.size() * POSE_BYTES;
    }

    /** True when faces follow batch then local face order, which the group geometry alone determines. */
    boolean authoredOrder() {
        return layout.authored;
    }

    private static boolean authoredOrder(List<DrawBatch> batches, long[] order) {
        int index = 0;
        for (int batch = 0; batch < batches.size(); batch++) {
            int faceCount = batches.get(batch).mesh().faceCount();
            for (int face = 0; face < faceCount; face++) {
                if (index >= order.length || order[index] != ((long) batch << 32 | Integer.toUnsignedLong(face))) {
                    return false;
                }
                index++;
            }
        }
        return index == order.length;
    }

    ByteBuffer vertices() {
        ByteBuffer data = direct(Math.multiplyExact(layout.vertices, GpuBatchData.VERTEX_BYTES));
        for (int index = 0; index < batches.size(); index++) {
            MeshHandle mesh = batches.get(index).mesh();
            for (int vertex = 0; vertex < mesh.vertexCount(); vertex++) {
                for (int component : ATTRIBUTES) {
                    data.putFloat((float) mesh.component(vertex, component));
                }
                data.putFloat(0).putFloat(0).putFloat(0).putFloat(0);
                data.putInt(index).putInt(0).putInt(0).putInt(0);
            }
        }
        return data.flip();
    }

    ByteBuffer indices() {
        ByteBuffer data = direct(Math.multiplyExact(layout.order.length, 6 * Integer.BYTES));
        for (long face : layout.order) {
            int vertex = layout.vertexOffsets[(int) (face >>> 32)] + (int) face * 4;
            data.putInt(vertex).putInt(vertex + 1).putInt(vertex + 2);
            data.putInt(vertex).putInt(vertex + 2).putInt(vertex + 3);
        }
        return data.flip();
    }

    /** The cached mesh owns a valid placeholder IBO; grouped draws always bind the frame's sorted IBO. */
    static ByteBuffer initialIndices() {
        return direct(6 * Integer.BYTES).putInt(0).putInt(1).putInt(2).putInt(0).putInt(2).putInt(3).flip();
    }

    ByteBuffer pose(Range range) {
        ByteBuffer data = direct(POSE_BYTES);
        int[] slots = new int[parts.size()];
        for (int slot = 0; slot < range.parts().size(); slot++) {
            int part = range.parts().get(slot);
            slots[part] = slot;
            data.position(slot * GpuBatchData.PART_BYTES);
            data.put(parts.get(part).duplicate());
        }
        data.position(GpuBatchData.PARAMETER_OFFSET);
        GpuBatchData.writeParameters(data, batches.getFirst());
        // Four byte-sized slots share one integer; four integers share each std140 ivec4.
        int[] packed = new int[MAX_BATCHES / 4];
        for (int batch = 0; batch < batchParts.length; batch++) {
            packed[batch / 4] |= slots[batchParts[batch]] << ((batch % 4) * 8);
        }
        data.position(REMAP_OFFSET);
        for (int value : packed) {
            data.putInt(value);
        }
        return data.flip();
    }

    private static boolean same(float first, float second) {
        return Float.floatToIntBits(first) == Float.floatToIntBits(second);
    }

    private static ByteBuffer direct(int bytes) {
        return ByteBuffer.allocateDirect(bytes).order(ByteOrder.nativeOrder());
    }
}
