package top.aihmc.omnimorph.render.acceleration;

import com.mojang.blaze3d.vertex.PoseStack;
import org.joml.Vector3f;
import top.aihmc.omnimorph.math.matrix.FiguraMat3;
import top.aihmc.omnimorph.math.matrix.FiguraMat4;
import top.aihmc.omnimorph.math.vector.FiguraVec3;
import top.aihmc.omnimorph.math.vector.FiguraVec4;

import java.nio.ByteBuffer;

/** Copies matrices, including vanilla's normal trust/normalization semantics. */
public final class PoseSnapshot {
    private final FiguraMat4 position;
    private final FiguraMat3 normal;
    private final PoseStack.Pose floatPose;
    private final FiguraMat3 uv;
    private final FiguraMat4[] skinDeltas;
    private final int normalPolicy;

    public PoseSnapshot(FiguraMat4 position, FiguraMat3 normal) {
        this(position, normal, FiguraMat3.of(), null);
    }

    public PoseSnapshot(FiguraMat4 position, FiguraMat3 normal, FiguraMat3 uv, FiguraMat4[] skinDeltas) {
        this.position = position.copy();
        this.normal = normal.copy();
        this.floatPose = null;
        this.uv = uv.copy();
        this.skinDeltas = copyDeltas(skinDeltas);
        this.normalPolicy = 0;
    }

    public PoseSnapshot(PoseStack.Pose pose) {
        this.position = null;
        this.normal = null;
        this.floatPose = pose.copy();
        this.uv = FiguraMat3.of();
        this.skinDeltas = new FiguraMat4[0];
        this.normalPolicy = detectNormalPolicy(this.floatPose);
    }

    /** 0 preserves raw normals, 1 normalizes, -1 requires Java for degenerate/non-finite vanilla state. */
    public int normalPolicy() {
        return normalPolicy;
    }

    private static int detectNormalPolicy(PoseStack.Pose pose) {
        // Public transformNormal retains vanilla's private trusted-normal flag. Homogeneity distinguishes
        // raw transformation (doubling the input doubles length) from normalization (length stays fixed).
        for (int axis = 0; axis < 3; axis++) {
            Vector3f input = new Vector3f(axis == 0 ? 1 : 0, axis == 1 ? 1 : 0, axis == 2 ? 1 : 0);
            Vector3f first = pose.transformNormal(input, new Vector3f());
            Vector3f second = pose.transformNormal(input.mul(2), new Vector3f());
            double firstLength = Math.sqrt((double) first.x * first.x + (double) first.y * first.y + (double) first.z * first.z);
            double secondLength = Math.sqrt((double) second.x * second.x + (double) second.y * second.y + (double) second.z * second.z);
            if (Double.isFinite(firstLength) && Double.isFinite(secondLength) && firstLength > 0) {
                return secondLength / firstLength < 1.5 ? 1 : 0;
            }
        }
        return -1;
    }

    void transform(MeshHandle mesh, double[] output) {
        if (floatPose != null) {
            transformFloat(mesh, output);
            return;
        }
        FiguraVec4 point = FiguraVec4.of();
        FiguraVec3 direction = FiguraVec3.of();
        FiguraVec3 texture = FiguraVec3.of();
        FiguraVec4 skinPoint = FiguraVec4.of();
        for (int vertex = 0; vertex < mesh.vertexCount(); vertex++) {
            point.set(mesh.component(vertex, 0), mesh.component(vertex, 1),
                    mesh.component(vertex, 2), mesh.component(vertex, 3));
            applySkinning(point, skinPoint, mesh.influences(vertex));
            point.transform(position);
            direction.set(mesh.component(vertex, 4), mesh.component(vertex, 5),
                    mesh.component(vertex, 6)).transform(normal);
            int offset = vertex * MeshHandle.STRIDE;
            output[offset] = point.x;
            output[offset + 1] = point.y;
            output[offset + 2] = point.z;
            output[offset + 3] = point.w;
            output[offset + 4] = direction.x;
            output[offset + 5] = direction.y;
            output[offset + 6] = direction.z;
            texture.set(mesh.component(vertex, 7), mesh.component(vertex, 8), 1).transform(uv);
            output[offset + 7] = texture.x;
            output[offset + 8] = texture.y;
        }
    }

    /** CPU sorting still needs authored face centers; this never queries GPU results. */
    void transformedPosition(MeshHandle mesh, int vertex, double[] output) {
        if (floatPose != null) {
            Vector3f value = floatPose.pose().transformPosition((float) mesh.component(vertex, 0),
                    (float) mesh.component(vertex, 1), (float) mesh.component(vertex, 2), new Vector3f());
            output[0] = value.x;
            output[1] = value.y;
            output[2] = value.z;
            output[3] = 1;
            return;
        }
        FiguraVec4 point = FiguraVec4.of(mesh.component(vertex, 0), mesh.component(vertex, 1),
                mesh.component(vertex, 2), mesh.component(vertex, 3));
        applySkinning(point, FiguraVec4.of(), mesh.influences(vertex));
        point.transform(position);
        output[0] = point.x;
        output[1] = point.y;
        output[2] = point.z;
        output[3] = point.w;
    }

    private void transformFloat(MeshHandle mesh, double[] output) {
        Vector3f point = new Vector3f();
        Vector3f direction = new Vector3f();
        for (int vertex = 0; vertex < mesh.vertexCount(); vertex++) {
            floatPose.pose().transformPosition((float) mesh.component(vertex, 0),
                    (float) mesh.component(vertex, 1), (float) mesh.component(vertex, 2), point);
            floatPose.transformNormal((float) mesh.component(vertex, 4),
                    (float) mesh.component(vertex, 5), (float) mesh.component(vertex, 6), direction);
            int offset = vertex * MeshHandle.STRIDE;
            output[offset] = point.x;
            output[offset + 1] = point.y;
            output[offset + 2] = point.z;
            output[offset + 3] = 1;
            output[offset + 4] = direction.x;
            output[offset + 5] = direction.y;
            output[offset + 6] = direction.z;
            output[offset + 7] = mesh.component(vertex, 7);
            output[offset + 8] = mesh.component(vertex, 8);
        }
    }

    private void applySkinning(FiguraVec4 point, FiguraVec4 temporary, MeshHandle.Influences influences) {
        float total = 0;
        float x = 0;
        float y = 0;
        float z = 0;
        for (int influence = 0; influence < influences.size(); influence++) {
            int joint = influences.joint(influence);
            float weight = influences.weight(influence);
            if (weight <= 0 || joint < 0 || joint >= skinDeltas.length) {
                continue;
            }
            temporary.set(point.x, point.y, point.z, 1).transform(skinDeltas[joint]);
            x += (float) temporary.x * weight;
            y += (float) temporary.y * weight;
            z += (float) temporary.z * weight;
            total += weight;
        }
        if (total > 0) {
            float inverse = 1f / total;
            point.x = x * inverse;
            point.y = y * inverse;
            point.z = z * inverse;
        }
    }

    public int skinJointCount() {
        return skinDeltas.length;
    }

    /** Packs a bounded ABI batch without exposing mutable matrices or truncating skin influences. */
    void writeNativeVertices(MeshHandle mesh, ByteBuffer destination) {
        FiguraVec4 point = FiguraVec4.of();
        FiguraVec4 temporary = FiguraVec4.of();
        Vector3f direction = new Vector3f();
        destination.clear();
        for (int vertex = 0; vertex < mesh.vertexCount(); vertex++) {
            point.set(mesh.component(vertex, 0), mesh.component(vertex, 1),
                    mesh.component(vertex, 2), floatPose == null ? mesh.component(vertex, 3) : 1);
            if (floatPose == null) {
                applySkinning(point, temporary, mesh.influences(vertex));
            }
            destination.putFloat((float) point.x).putFloat((float) point.y)
                    .putFloat((float) point.z).putFloat((float) point.w);
            if (floatPose != null) {
                // Preserve vanilla's private trusted-normal policy exactly, including its normalization.
                floatPose.transformNormal((float) mesh.component(vertex, 4),
                        (float) mesh.component(vertex, 5), (float) mesh.component(vertex, 6), direction);
                destination.putFloat(direction.x).putFloat(direction.y).putFloat(direction.z);
            } else {
                destination.putFloat((float) mesh.component(vertex, 4)).putFloat((float) mesh.component(vertex, 5))
                        .putFloat((float) mesh.component(vertex, 6));
            }
            destination.putFloat((float) mesh.component(vertex, 7)).putFloat((float) mesh.component(vertex, 8));
        }
        destination.flip();
    }

    public boolean usesFloatPrecision() {
        return floatPose != null;
    }

    public double[] copyPositionMatrix() {
        if (floatPose == null) {
            return matrix4(position);
        }
        float[] values = floatPose.pose().get(new float[16]);
        return widen(values);
    }

    public double[] copyNormalMatrix() {
        return floatPose == null ? matrix3(normal) : widen(floatPose.normal().get(new float[9]));
    }

    public double[] copyUvMatrix() {
        return matrix3(uv);
    }

    public double[] copySkinDelta(int joint) {
        return matrix4(skinDeltas[joint]);
    }

    /** A detached copy retains vanilla's normal normalization policy. */
    public PoseStack.Pose copyFloatPose() {
        return floatPose == null ? null : floatPose.copy();
    }

    public boolean mirrored() {
        if (floatPose != null) {
            return floatPose.pose().determinant3x3() < 0;
        }
        return position.v11 * (position.v22 * position.v33 - position.v23 * position.v32)
                - position.v12 * (position.v21 * position.v33 - position.v23 * position.v31)
                + position.v13 * (position.v21 * position.v32 - position.v22 * position.v31) < 0;
    }

    private static FiguraMat4[] copyDeltas(FiguraMat4[] source) {
        if (source == null) {
            return new FiguraMat4[0];
        }
        FiguraMat4[] result = new FiguraMat4[source.length];
        for (int index = 0; index < source.length; index++) {
            result[index] = source[index].copy();
        }
        return result;
    }

    private static double[] matrix4(FiguraMat4 matrix) {
        return new double[]{matrix.v11, matrix.v21, matrix.v31, matrix.v41,
                matrix.v12, matrix.v22, matrix.v32, matrix.v42,
                matrix.v13, matrix.v23, matrix.v33, matrix.v43,
                matrix.v14, matrix.v24, matrix.v34, matrix.v44};
    }

    private static double[] matrix3(FiguraMat3 matrix) {
        return new double[]{matrix.v11, matrix.v21, matrix.v31,
                matrix.v12, matrix.v22, matrix.v32, matrix.v13, matrix.v23, matrix.v33};
    }

    private static double[] widen(float[] values) {
        double[] result = new double[values.length];
        for (int index = 0; index < values.length; index++) {
            result[index] = values[index];
        }
        return result;
    }
}
