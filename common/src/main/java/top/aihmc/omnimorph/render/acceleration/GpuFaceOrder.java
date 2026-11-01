package top.aihmc.omnimorph.render.acceleration;

import com.mojang.blaze3d.vertex.VertexSorting;
import org.joml.Vector3f;

import java.util.List;

/** Bounded render-thread workspace. Returned arrays are borrowed until the next prepare call. */
final class GpuFaceOrder {
    static final int MAX_FACES = 65_536;
    private long[] authored = new long[0];
    private long[] result = new long[0];
    private int[] order = new int[0];
    private int[] merge = new int[0];
    private float[] distances = new float[0];
    private final double[] first = new double[4];
    private final double[] third = new double[4];
    private final PoseSnapshot.PositionScratch position = new PoseSnapshot.PositionScratch();
    private final Vector3f center = new Vector3f();
    private final Vector3f origin = new Vector3f();
    private int size;

    static boolean supports(VertexSorting sorting) {
        return sorting == VertexSorting.DISTANCE_TO_ORIGIN || sorting == VertexSorting.ORTHOGRAPHIC_Z;
    }

    long[] prepare(List<DrawBatch> batches, VertexSorting sorting) {
        size = 0;
        for (DrawBatch batch : batches) {
            size = Math.addExact(size, batch.mesh().faceCount());
        }
        if (size > MAX_FACES) {
            throw new IllegalArgumentException("GPU face workspace limit exceeded");
        }
        reserve(size);
        boolean sorted = batches.get(0).semantics().sortOnUpload();
        if (sorted && !supports(sorting)) {
            throw new IllegalArgumentException("Custom vertex sorter must use the original Java buffer");
        }
        int index = 0;
        for (int batchIndex = 0; batchIndex < batches.size(); batchIndex++) {
            DrawBatch batch = batches.get(batchIndex);
            for (int face = 0; face < batch.mesh().faceCount(); face++, index++) {
                authored[index] = ((long) batchIndex << 32) | face;
                order[index] = index;
                if (sorted) {
                    batch.pose().transformedPosition(batch.mesh(), face * 4, first, position);
                    batch.pose().transformedPosition(batch.mesh(), face * 4 + 2, third, position);
                    offset(first, batch.semantics().vertexOffset());
                    offset(third, batch.semantics().vertexOffset());
                    center.set(((float) first[0] + (float) third[0]) * .5f,
                            ((float) first[1] + (float) third[1]) * .5f,
                            ((float) first[2] + (float) third[2]) * .5f);
                    distances[index] = sorting == VertexSorting.ORTHOGRAPHIC_Z
                            ? -center.z : origin.distanceSquared(center);
                }
            }
        }
        if (!sorted) {
            return authored;
        }
        // Stable descending Float.compare, exactly the vanilla distance sort including equal-key order.
        for (int width = 1; width < size; width *= 2) {
            for (int left = 0; left < size; left += width * 2) {
                int middle = Math.min(left + width, size);
                int end = Math.min(left + width * 2, size);
                int a = left;
                int b = middle;
                for (int output = left; output < end; output++) {
                    if (b >= end || a < middle && Float.compare(distances[order[a]], distances[order[b]]) >= 0) {
                        merge[output] = order[a++];
                    } else {
                        merge[output] = order[b++];
                    }
                }
            }
            int[] swap = order;
            order = merge;
            merge = swap;
        }
        for (int face = 0; face < size; face++) {
            result[face] = authored[order[face]];
        }
        return result;
    }

    private void reserve(int faces) {
        if (faces <= authored.length) {
            return;
        }
        int capacity = Math.min(MAX_FACES, Integer.highestOneBit(Math.max(1, faces - 1)) * 2);
        authored = new long[capacity];
        result = new long[capacity];
        order = new int[capacity];
        merge = new int[capacity];
        distances = new float[capacity];
    }

    int size() {
        return size;
    }

    void clear() {
        authored = new long[0];
        result = new long[0];
        order = new int[0];
        merge = new int[0];
        distances = new float[0];
        size = 0;
    }

    private static void offset(double[] point, double offset) {
        if (offset != 0) {
            double length = Math.sqrt(point[0] * point[0] + point[1] * point[1]
                    + point[2] * point[2] + point[3] * point[3]);
            for (int component = 0; component < 3; component++) {
                point[component] += point[component] / length * offset;
            }
        }
    }
}
