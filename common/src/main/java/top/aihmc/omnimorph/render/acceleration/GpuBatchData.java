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
    private static final int[] ATTRIBUTES = {0, 1, 2, 4, 5, 6, 7, 8};

    private GpuBatchData() {
    }

    static boolean supports(DrawBatch batch) {
        MeshHandle mesh = batch.mesh();
        PoseSnapshot pose = batch.pose();
        if (mesh.vertexCount() == 0 || mesh.vertexCount() > NativeBindings.MAX_VERTICES
                || mesh.verticesPerFace() < 3 || pose.skinJointCount() >= MAX_PARTS || pose.normalPolicy() < 0
                || !finiteMatrix(pose.copyPositionMatrix()) || !finiteMatrix(pose.copyNormalMatrix())
                || !finiteMatrix(pose.copyUvMatrix())) {
            return false;
        }
        for (int joint = 0; joint < pose.skinJointCount(); joint++) {
            if (!finiteMatrix(pose.copySkinDelta(joint))) {
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
            skinnedMagnitude = Math.max(skinnedMagnitude, bound(pose.copySkinDelta(joint), 4, positionMagnitude));
        }
        double positionBound = bound(pose.copyPositionMatrix(), 4, skinnedMagnitude);
        if (skinnedMagnitude * maximumWeight > Float.MAX_VALUE / 16.0
                || positionBound + Math.abs(batch.semantics().vertexOffset()) > Float.MAX_VALUE / 16.0
                || bound(pose.copyNormalMatrix(), 3, normalMagnitude) > Float.MAX_VALUE / 16.0
                || bound(pose.copyUvMatrix(), 3, uvMagnitude) > Float.MAX_VALUE / 16.0
                || (batch.semantics().vertexOffset() != 0 && positionBound > 1e15)) {
            return false;
        }
        if (pose.normalPolicy() == 1 && !normalizationSafe(mesh, pose.copyNormalMatrix())) {
            return false;
        }
        DrawSemantics draw = batch.semantics();
        if (!pose.usesFloatPrecision()) {
            double[] position = pose.copyPositionMatrix();
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
        ByteBuffer data = direct(POSE_BYTES);
        PoseSnapshot pose = batch.pose();
        writePart(data, pose);
        for (int joint = 0; joint < pose.skinJointCount(); joint++) {
            data.position((joint + 1) * PART_BYTES);
            write(data, pose.copySkinDelta(joint));
        }
        data.position(PARAMETER_OFFSET);
        writeParameters(data, batch);
        return data.flip();
    }

    static void writePart(ByteBuffer data, PoseSnapshot pose) {
        write(data, pose.copyPositionMatrix());
        writePadded3(data, pose.copyNormalMatrix());
        writePadded3(data, pose.copyUvMatrix());
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
