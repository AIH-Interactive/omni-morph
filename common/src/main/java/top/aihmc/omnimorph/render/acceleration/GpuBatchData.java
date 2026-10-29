package top.aihmc.omnimorph.render.acceleration;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/** Shared graphics layout. Geometry stays untransformed; only the pose block changes each frame. */
final class GpuBatchData {
    static final int MAX_PARTS = 64;
    static final int PART_BYTES = 160;
    static final int VERTEX_BYTES = 64;
    static final int PARAMETER_OFFSET = MAX_PARTS * PART_BYTES;
    static final int POSE_BYTES = PARAMETER_OFFSET + 48;
    /** Part capacities of the pose block variants; the last one is the full layout every material has. */
    static final int[] PART_TIERS = {1, 8, 32, MAX_PARTS};
    private static final int[] ATTRIBUTES = {0, 1, 2, 4, 5, 6, 7, 8};

    private GpuBatchData() {
    }

    /** Smallest variant holding the given number of parts. */
    static int tierFor(int parts) {
        for (int tier : PART_TIERS) {
            if (parts <= tier) {
                return tier;
            }
        }
        throw new IllegalArgumentException("Pose block exceeds " + MAX_PARTS + " parts: " + parts);
    }

    static int parameterOffset(int tier) {
        return tier * PART_BYTES;
    }

    static int poseBytes(int tier) {
        return parameterOffset(tier) + 48;
    }

    /** std430 storage block header: the same 48 parameter bytes, placed before the unsized part array. */
    static final int STORAGE_HEADER_BYTES = 48;

    static int storagePoseBytes(int parts) {
        return STORAGE_HEADER_BYTES + parts * PART_BYTES;
    }

    /** Writes the storage block of one batch at the target position and advances past it. */
    static void writeStoragePose(ByteBuffer target, DrawBatch batch) {
        PoseSnapshot pose = batch.pose();
        int start = target.position();
        writeParameters(target, batch);
        writePart(target, pose);
        int joints = partsUsed(pose) - 1;
        for (int joint = 0; joint < joints; joint++) {
            target.position(start + STORAGE_HEADER_BYTES + (joint + 1) * PART_BYTES);
            write(target, pose.skinDelta(joint));
        }
        target.position(start + storagePoseBytes(partsUsed(pose)));
    }

    /** Parts one ordinary batch reads: the model transform plus, when skinned in double precision, its joints. */
    static int partsUsed(PoseSnapshot pose) {
        return pose.skinJointCount() > 0 && !pose.usesFloatPrecision() ? pose.skinJointCount() + 1 : 1;
    }

    static boolean supports(DrawBatch batch) {
        MeshHandle mesh = batch.mesh();
        PoseSnapshot pose = batch.pose();
        if (mesh.vertexCount() == 0 || mesh.vertexCount() > NativeBindings.MAX_VERTICES
                || mesh.verticesPerFace() < 3 || pose.skinJointCount() >= MAX_PARTS || pose.normalPolicy() < 0
                || !finiteMatrix(pose.positionMatrix()) || !finiteMatrix(pose.normalMatrix())
                || !finiteMatrix(pose.uvMatrix())) {
            return false;
        }
        for (int joint = 0; joint < pose.skinJointCount(); joint++) {
            if (!finiteMatrix(pose.skinDelta(joint))) {
                return false;
            }
        }
        // Static vertex facts are cached on the immutable mesh; only the pose-dependent checks run every time.
        MeshHandle.StaticSummary geometry = mesh.staticSummary();
        if (!geometry.componentsValid()) {
            return false;
        }
        double positionMagnitude = geometry.positionMagnitude();
        double normalMagnitude = geometry.normalMagnitude();
        double uvMagnitude = geometry.uvMagnitude();
        float maximumWeight = 1;
        if (!pose.usesFloatPrecision() && pose.skinJointCount() > 0) {
            if (geometry.maxInfluences() > 4 || !geometry.weightsValid() || !geometry.totalsFinite()
                    || geometry.minWeightedJoint() < 0 || geometry.maxWeightedJoint() >= pose.skinJointCount()) {
                return false;
            }
            maximumWeight = geometry.maxTotalWeight();
        }
        double skinnedMagnitude = positionMagnitude;
        for (int joint = 0; joint < pose.skinJointCount(); joint++) {
            skinnedMagnitude = Math.max(skinnedMagnitude, bound(pose.skinDelta(joint), 4, positionMagnitude));
        }
        double positionBound = bound(pose.positionMatrix(), 4, skinnedMagnitude);
        if (skinnedMagnitude * maximumWeight > Float.MAX_VALUE / 16.0
                || positionBound + Math.abs(batch.semantics().vertexOffset()) > Float.MAX_VALUE / 16.0
                || bound(pose.normalMatrix(), 3, normalMagnitude) > Float.MAX_VALUE / 16.0
                || bound(pose.uvMatrix(), 3, uvMagnitude) > Float.MAX_VALUE / 16.0
                || (batch.semantics().vertexOffset() != 0 && positionBound > 1e15)) {
            return false;
        }
        if (pose.normalPolicy() == 1 && !normalizationSafe(mesh, pose.normalMatrix())) {
            return false;
        }
        DrawSemantics draw = batch.semantics();
        if (!pose.usesFloatPrecision()) {
            double[] position = pose.positionMatrix();
            if (position[3] != 0 || position[7] != 0 || position[11] != 0 || Math.abs(position[15]) < 1e-10) {
                // Figura's final vertex-offset normalization includes homogeneous w even for zero offset.
                // Keep projective/zero-w cases on Java rather than relying on undefined GPU normalize(0).
                return false;
            }
        }
        return color(draw.red()) && color(draw.green()) && color(draw.blue()) && color(draw.alpha())
                && Float.isFinite((float) draw.vertexOffset());
    }

    static ByteBuffer vertices(MeshHandle mesh, int skinJoints, boolean floatPose) {
        ByteBuffer data = direct(Math.multiplyExact(mesh.vertexCount(), VERTEX_BYTES));
        for (int vertex = 0; vertex < mesh.vertexCount(); vertex++) {
            for (int component : ATTRIBUTES) {
                data.putFloat((float) mesh.component(vertex, component));
            }
            MeshHandle.Influences influences = mesh.influences(vertex);
            boolean skinned = !floatPose && skinJoints > 0;
            for (int influence = 0; influence < 4; influence++) {
                data.putFloat(skinned && influence < influences.size() ? influences.weight(influence) : 0);
            }
            for (int influence = 0; influence < 4; influence++) {
                data.putInt(skinned && influence < influences.size() && influences.weight(influence) > 0
                        ? influences.joint(influence) + 1 : 0);
            }
        }
        return data.flip();
    }

    static ByteBuffer indices(MeshHandle mesh) {
        int[] indices = mesh.copyTriangleIndices();
        ByteBuffer data = direct(Math.multiplyExact(indices.length, Integer.BYTES));
        data.asIntBuffer().put(indices);
        return data;
    }

    static ByteBuffer pose(DrawBatch batch) {
        return pose(batch, MAX_PARTS);
    }

    /** Packs the pose block for a variant of {@code tier} parts; the tier must hold {@link #partsUsed}. */
    static ByteBuffer pose(DrawBatch batch, int tier) {
        PoseSnapshot pose = batch.pose();
        if (partsUsed(pose) > tier) {
            throw new IllegalArgumentException("Pose block variant too small for the batch joints");
        }
        ByteBuffer data = direct(poseBytes(tier));
        writePart(data, pose);
        // Float-precision joints are never read by the shader; smaller variants simply omit them.
        int joints = Math.min(pose.skinJointCount(), tier - 1);
        for (int joint = 0; joint < joints; joint++) {
            data.position((joint + 1) * PART_BYTES);
            write(data, pose.skinDelta(joint));
        }
        data.position(parameterOffset(tier));
        writeParameters(data, batch);
        return data.flip();
    }

    static void writePart(ByteBuffer data, PoseSnapshot pose) {
        write(data, pose.positionMatrix());
        writePadded3(data, pose.normalMatrix());
        writePadded3(data, pose.uvMatrix());
    }

    static void writeParameters(ByteBuffer data, DrawBatch batch) {
        DrawSemantics draw = batch.semantics();
        PoseSnapshot pose = batch.pose();
        data.putFloat(quantizeColor(draw.red())).putFloat(quantizeColor(draw.green()))
                .putFloat(quantizeColor(draw.blue())).putFloat(quantizeColor(draw.alpha()));
        data.putInt(draw.overlay() & 0xffff).putInt(draw.overlay() >>> 16)
                .putInt(draw.light() & 0xffff).putInt(draw.light() >>> 16);
        data.putFloat((float) draw.vertexOffset()).putFloat(pose.normalPolicy())
                .putFloat(pose.skinJointCount() > 0 && !pose.usesFloatPrecision() ? 1 : 0)
                .putFloat(pose.usesFloatPrecision() ? 1 : 0);
    }

    private static boolean finiteMatrix(double[] values) {
        for (double value : values) {
            if (!Float.isFinite((float) value)) {
                return false;
            }
        }
        return true;
    }

    private static double bound(double[] matrix, int dimension, double magnitude) {
        double maximum = 0;
        for (int row = 0; row < dimension; row++) {
            double sum = 0;
            for (int column = 0; column < dimension; column++) {
                sum += Math.abs(matrix[column * dimension + row]);
            }
            maximum = Math.max(maximum, sum * Math.max(1, magnitude));
        }
        return maximum;
    }

    private static boolean normalizationSafe(MeshHandle mesh, double[] matrix) {
        for (int vertex = 0; vertex < mesh.vertexCount(); vertex++) {
            double squaredLength = 0;
            for (int row = 0; row < 3; row++) {
                double value = 0;
                for (int column = 0; column < 3; column++) {
                    value += matrix[column * 3 + row] * mesh.component(vertex, column + 4);
                }
                squaredLength += value * value;
            }
            if (squaredLength < 1e-20 || squaredLength > 1e20) {
                return false;
            }
        }
        return true;
    }

    private static boolean color(float value) {
        return Float.isFinite(value) && value >= 0 && value <= 1;
    }

    private static float quantizeColor(float value) {
        return (int) (value * 255) / 255f;
    }

    private static void write(ByteBuffer data, double[] values) {
        for (double value : values) {
            data.putFloat((float) value);
        }
    }

    private static void writePadded3(ByteBuffer data, double[] matrix) {
        for (int column = 0; column < 3; column++) {
            for (int row = 0; row < 3; row++) {
                data.putFloat((float) matrix[column * 3 + row]);
            }
            data.putFloat(0);
        }
    }

    private static ByteBuffer direct(int bytes) {
        return ByteBuffer.allocateDirect(bytes).order(ByteOrder.nativeOrder());
    }
}
