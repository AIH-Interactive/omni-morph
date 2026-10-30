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

    static int remapOffset(int tier) {
        return GpuBatchData.poseBytes(tier);
    }

    static int poseBytes(int tier) {
        return remapOffset(tier) + MAX_BATCHES;
    }
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
    private boolean nativePacked;
    private RuntimeException nativePackFailure;

    GpuRigidData(List<DrawBatch> batches, long[] order) {
        this(batches, order, null);
    }

    GpuRigidData(List<DrawBatch> batches, long[] order, Layout previous) {
        this(batches, order, previous, null);
    }

    /**
     * {@code packer} converts the part values with native SIMD when present; any native rejection falls back
     * to Java casts for this group, which produce the same bytes, and is reported by {@link #nativePackFailure()}.
     */
    GpuRigidData(List<DrawBatch> batches, long[] order, Layout previous, NativeSimdBackend packer) {
        this.batches = List.copyOf(batches);
        batchParts = new int[batches.size()];
        ByteBuffer packed = packParts(this.batches, packer);
        Map<ByteBuffer, Integer> unique = new HashMap<>();
        for (int index = 0; index < batches.size(); index++) {
            ByteBuffer part = packed.slice(index * GpuBatchData.PART_BYTES, GpuBatchData.PART_BYTES)
                    .order(ByteOrder.nativeOrder());
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

    private ByteBuffer packParts(List<DrawBatch> source, NativeSimdBackend packer) {
        int count = source.size() * GpuBatchData.PART_VALUES;
        double[] values = new double[count];
        for (int index = 0; index < source.size(); index++) {
            DrawBatch batch = source.get(index);
            int offset = index * GpuBatchData.PART_VALUES;
            GpuBatchData.partValues(values, offset, batch.pose());
            // std140 padding carries options specific to this rigid part; xyz matrix components stay unchanged.
            values[offset + 19] = batch.pose().normalPolicy();
            values[offset + 23] = batch.pose().usesFloatPrecision() ? 1 : 0;
            values[offset + 27] = batch.semantics().vertexOffset();
        }
        ByteBuffer packed = ByteBuffer.allocateDirect(count * Float.BYTES).order(ByteOrder.nativeOrder());
        if (packer != null) {
            try {
                packer.packPoses(values, count, packed);
                nativePacked = true;
                return packed.flip();
            } catch (RuntimeException failure) {
                nativePackFailure = failure;
                packed.clear();
            }
        }
        for (double value : values) {
            packed.putFloat((float) value);
        }
        return packed.flip();
    }

    boolean nativePacked() {
        return nativePacked;
    }

    RuntimeException nativePackFailure() {
        return nativePackFailure;
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
        return pose(range, GpuBatchData.MAX_PARTS);
    }

    /** Packs one range for a pose block variant of {@code tier} parts; the tier must hold the range palette. */
    ByteBuffer pose(Range range, int tier) {
        if (range.parts().size() > tier) {
            throw new IllegalArgumentException("Pose block variant too small for the range palette");
        }
        ByteBuffer data = direct(poseBytes(tier));
        for (int slot = 0; slot < range.parts().size(); slot++) {
            data.position(slot * GpuBatchData.PART_BYTES);
            data.put(parts.get(range.parts().get(slot)).duplicate());
        }
        data.position(GpuBatchData.parameterOffset(tier));
        GpuBatchData.writeParameters(data, batches.getFirst());
        // Four byte-sized slots share one integer; four integers share each std140 ivec4.
        int[] packed = packedRemap(range, MAX_BATCHES / 4);
        data.position(remapOffset(tier));
        for (int value : packed) {
            data.putInt(value);
        }
        return data.flip();
    }

    long storagePoseBytes(Range range) {
        return GpuBatchData.storagePoseBytes(range.parts().size());
    }

    /** The storage remap block holds only the integers this group's batches occupy. */
    long storageRemapBytes() {
        return (long) remapInts() * Integer.BYTES;
    }

    /** Writes the std430 pose block of one range (header, then its palette) and advances past it. */
    void writeStoragePose(ByteBuffer target, Range range) {
        GpuBatchData.writeParameters(target, batches.getFirst());
        for (int slot = 0; slot < range.parts().size(); slot++) {
            target.put(parts.get(range.parts().get(slot)).duplicate());
        }
    }

    /** Writes the packed batch-to-slot remap of one range and advances past it. */
    void writeStorageRemap(ByteBuffer target, Range range) {
        for (int value : packedRemap(range, remapInts())) {
            target.putInt(value);
        }
    }

    private int remapInts() {
        return (batchParts.length + 3) / 4;
    }

    private int[] packedRemap(Range range, int ints) {
        int[] slots = new int[parts.size()];
        for (int slot = 0; slot < range.parts().size(); slot++) {
            slots[range.parts().get(slot)] = slot;
        }
        int[] packed = new int[ints];
        for (int batch = 0; batch < batchParts.length; batch++) {
            packed[batch / 4] |= slots[batchParts[batch]] << ((batch % 4) * 8);
        }
        return packed;
    }

    private static boolean same(float first, float second) {
        return Float.floatToIntBits(first) == Float.floatToIntBits(second);
    }

    private static ByteBuffer direct(int bytes) {
        return ByteBuffer.allocateDirect(bytes).order(ByteOrder.nativeOrder());
    }
}
