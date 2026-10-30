package top.aihmc.omnimorph.render.acceleration;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.List;

/**
 * One material boundary of one model drawn with a single storage-block draw (R4-3). Unlike rigid groups, batches
 * may be skinned and may differ in tint, light and overlay: every batch carries its own 48-byte record (the same
 * bytes {@link GpuBatchData#writeParameters} writes for an ordinary batch), and the part table holds every batch
 * root followed by every skin delta at fixed offsets, so vertex data can be cached across frames.
 */
final class GpuModelData {
    static final int MAX_BATCHES = 0xFFFF;
    static final int MAX_PARTS = 0xFFFF;
    static final int RECORD_BYTES = 48;
    private static final int[] ATTRIBUTES = {0, 1, 2, 4, 5, 6, 7, 8};

    private final List<DrawBatch> batches;
    private final long[] order;
    private final int[] vertexOffsets;
    private final int[] deltaBases;
    private final boolean[] skinned;
    private final int partCount;
    private final int vertexCount;

    GpuModelData(List<DrawBatch> batches, long[] order) {
        if (!supports(batches)) {
            throw new IllegalArgumentException("Batches cannot share one model draw");
        }
        this.batches = List.copyOf(batches);
        this.order = order.clone();
        vertexOffsets = new int[batches.size()];
        deltaBases = new int[batches.size()];
        skinned = new boolean[batches.size()];
        int vertices = 0;
        int parts = batches.size();
        for (int index = 0; index < batches.size(); index++) {
            DrawBatch batch = batches.get(index);
            vertexOffsets[index] = vertices;
            vertices = Math.addExact(vertices, batch.mesh().vertexCount());
            deltaBases[index] = parts;
            skinned[index] = skinnedInShader(batch.pose());
            if (skinned[index]) {
                parts += batch.pose().skinJointCount();
            }
        }
        vertexCount = vertices;
        partCount = parts;
        for (long face : order) {
            int batchIndex = (int) (face >>> 32);
            int localFace = (int) face;
            if (batchIndex < 0 || batchIndex >= batches.size() || localFace < 0
                    || localFace >= batches.get(batchIndex).mesh().faceCount()) {
                throw new IllegalArgumentException("Sorted model face outside its authored batch");
            }
        }
    }

    /**
     * Same owner generation, material and visibility, quad faces, ordinary GPU eligibility and the 16-bit batch and
     * part encodings. Skinning and per-batch tint, light and overlay are allowed.
     */
    static boolean supports(List<DrawBatch> batches) {
        if (batches.isEmpty() || batches.size() > MAX_BATCHES) {
            return false;
        }
        DrawBatch first = batches.getFirst();
        long vertices = 0;
        long parts = batches.size();
        for (DrawBatch batch : batches) {
            if (batch.lease().ownerGeneration() != first.lease().ownerGeneration()
                    || batch.semantics().material() != first.semantics().material()
                    || !batch.semantics().visible() || batch.mesh().verticesPerFace() != 4
                    || !GpuBatchData.supports(batch)) {
                return false;
            }
            vertices += batch.mesh().vertexCount();
            if (skinnedInShader(batch.pose())) {
                parts += batch.pose().skinJointCount();
            }
        }
        return vertices > 0 && vertices <= Integer.MAX_VALUE / GpuBatchData.VERTEX_BYTES && parts <= MAX_PARTS;
    }

    /** Float-precision poses are already skinned on the CPU, exactly as for ordinary batches. */
    private static boolean skinnedInShader(PoseSnapshot pose) {
        return pose.skinJointCount() > 0 && !pose.usesFloatPrecision();
    }

    int batchCount() {
        return batches.size();
    }

    int partCount() {
        return partCount;
    }

    int faceCount() {
        return order.length;
    }

    /** Joint counts and precision decide the vertex encoding, so they belong to the geometry cache key. */
    int[] encodingSignature() {
        int[] signature = new int[batches.size()];
        for (int index = 0; index < batches.size(); index++) {
            signature[index] = skinned[index] ? batches.get(index).pose().skinJointCount() : 0;
        }
        return signature;
    }

    long geometryBytes() {
        return (long) vertexCount * GpuBatchData.VERTEX_BYTES + 6 * Integer.BYTES;
    }

    long indexBytes() {
        return (long) order.length * 6 * Integer.BYTES;
    }

    /**
     * Untransformed vertices. {@code OmniJoints.x} holds the batch index in its high 16 bits and the first joint's
     * global part index in its low 16 bits; {@code yzw} hold the other joints' global part indices. Joint indices
     * are 0 where the weight is 0, which the shader never reads.
     */
    ByteBuffer vertices() {
        ByteBuffer data = direct(Math.multiplyExact(vertexCount, GpuBatchData.VERTEX_BYTES));
        for (int index = 0; index < batches.size(); index++) {
            MeshHandle mesh = batches.get(index).mesh();
            for (int vertex = 0; vertex < mesh.vertexCount(); vertex++) {
                for (int component : ATTRIBUTES) {
                    data.putFloat((float) mesh.component(vertex, component));
                }
                MeshHandle.Influences influences = mesh.influences(vertex);
                int[] joints = new int[4];
                for (int influence = 0; influence < 4; influence++) {
                    boolean used = skinned[index] && influence < influences.size() && influences.weight(influence) > 0;
                    data.putFloat(used ? influences.weight(influence) : 0);
                    joints[influence] = used ? deltaBases[index] + influences.joint(influence) : 0;
                }
                data.putInt(index << 16 | joints[0]).putInt(joints[1]).putInt(joints[2]).putInt(joints[3]);
            }
        }
        return data.flip();
    }

    ByteBuffer indices() {
        ByteBuffer data = direct(Math.multiplyExact(order.length, 6 * Integer.BYTES));
        for (long face : order) {
            int vertex = vertexOffsets[(int) (face >>> 32)] + (int) face * 4;
            data.putInt(vertex).putInt(vertex + 1).putInt(vertex + 2);
            data.putInt(vertex).putInt(vertex + 2).putInt(vertex + 3);
        }
        return data.flip();
    }

    /** True when faces follow batch then local face order, which the cached geometry alone determines. */
    boolean authoredOrder() {
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

    long storagePoseBytes() {
        return GpuBatchData.storagePoseBytes(partCount);
    }

    /** Header (first batch's parameters), every batch root, then every skin delta, and advances past them. */
    void writeStoragePose(ByteBuffer target) {
        GpuBatchData.writeParameters(target, batches.getFirst());
        for (DrawBatch batch : batches) {
            int start = target.position();
            GpuBatchData.writePart(target, batch.pose());
            target.position(start + GpuBatchData.PART_BYTES);
        }
        for (int index = 0; index < batches.size(); index++) {
            if (!skinned[index]) {
                continue;
            }
            PoseSnapshot pose = batches.get(index).pose();
            for (int joint = 0; joint < pose.skinJointCount(); joint++) {
                GpuBatchData.writeDeltaPart(target, pose.skinDelta(joint));
            }
        }
    }

    long recordBytes() {
        return (long) batches.size() * RECORD_BYTES;
    }

    /** One record per batch: tint, overlay and light, then vertex offset, normal policy, skinning and precision. */
    void writeRecords(ByteBuffer target) {
        for (DrawBatch batch : batches) {
            GpuBatchData.writeParameters(target, batch);
        }
    }

    private static ByteBuffer direct(int bytes) {
        return ByteBuffer.allocateDirect(bytes).order(ByteOrder.nativeOrder());
    }
}
