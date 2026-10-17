package top.aihmc.omnimorph.render.acceleration;

import java.util.List;
import java.util.Arrays;

/** Owned xyzw, normal xyz and UV input, in authored face order. */
public final class MeshHandle {
    public static final int STRIDE = 9;
    private static final Influences RIGID = new Influences(new int[0], new float[0]);
    private final double[] vertices;
    private final int verticesPerFace;
    private final List<Influences> influences;
    private final int geometryHash;

    /** Variable-length weights; no backend may silently truncate this list. */
    public static final class Influences {
        private final int[] joints;
        private final float[] weights;

        public Influences(int[] joints, float[] weights) {
            if (joints.length != weights.length) {
                throw new IllegalArgumentException("Joint and weight counts differ");
            }
            this.joints = joints.clone();
            this.weights = weights.clone();
        }

        public int size() {
            return joints.length;
        }

        public int joint(int influence) {
            return joints[influence];
        }

        public float weight(int influence) {
            return weights[influence];
        }
    }

    public MeshHandle(double[] vertices, int verticesPerFace) {
        this(vertices, verticesPerFace, List.of());
    }

    public MeshHandle(double[] vertices, int verticesPerFace, List<Influences> influences) {
        if (verticesPerFace < 1 || vertices.length % STRIDE != 0
                || vertices.length / STRIDE % verticesPerFace != 0) {
            throw new IllegalArgumentException("Incomplete vertex or face");
        }
        this.vertices = vertices.clone();
        this.verticesPerFace = verticesPerFace;
        if (!influences.isEmpty() && influences.size() != vertexCount()) {
            throw new IllegalArgumentException("Influence list must cover every vertex");
        }
        this.influences = List.copyOf(influences);
        int hash = 31 * Arrays.hashCode(this.vertices) + verticesPerFace;
        for (Influences influence : this.influences) {
            hash = 31 * hash + Arrays.hashCode(influence.joints);
            hash = 31 * hash + Arrays.hashCode(influence.weights);
        }
        geometryHash = hash;
    }

    public int geometryHash() {
        return geometryHash;
    }

    /** Collision-safe cache comparison of immutable geometry, independent of per-frame pose/material. */
    public boolean sameGeometry(MeshHandle other) {
        if (this == other) {
            return true;
        }
        if (geometryHash != other.geometryHash || verticesPerFace != other.verticesPerFace
                || !Arrays.equals(vertices, other.vertices) || influences.size() != other.influences.size()) {
            return false;
        }
        for (int index = 0; index < influences.size(); index++) {
            Influences first = influences.get(index);
            Influences second = other.influences.get(index);
            if (!Arrays.equals(first.joints, second.joints) || !Arrays.equals(first.weights, second.weights)) {
                return false;
            }
        }
        return true;
    }

    public int vertexCount() {
        return vertices.length / STRIDE;
    }

    public int verticesPerFace() {
        return verticesPerFace;
    }

    public double component(int vertex, int component) {
        if (vertex < 0 || vertex >= vertexCount() || component < 0 || component >= STRIDE) {
            throw new IndexOutOfBoundsException("Vertex component outside mesh");
        }
        return vertices[vertex * STRIDE + component];
    }

    public double[] copyVertices() {
        return vertices.clone();
    }

    public Influences influences(int vertex) {
        if (vertex < 0 || vertex >= vertexCount()) {
            throw new IndexOutOfBoundsException("Vertex outside mesh");
        }
        return influences.isEmpty() ? RIGID : influences.get(vertex);
    }

    /** Uniform pose part zero is the rigid association when no delta weights exist. */
    public int rigidPosePart() {
        return 0;
    }

    public int faceCount() {
        return vertexCount() / verticesPerFace;
    }

    public int[] copyTriangleIndices() {
        if (verticesPerFace < 3) {
            return new int[0];
        }
        int[] indices = new int[Math.multiplyExact(faceCount(), Math.multiplyExact(verticesPerFace - 2, 3))];
        int output = 0;
        for (int face = 0; face < faceCount(); face++) {
            int first = face * verticesPerFace;
            for (int corner = 1; corner < verticesPerFace - 1; corner++) {
                indices[output++] = first;
                indices[output++] = first + corner;
                indices[output++] = first + corner + 1;
            }
        }
        return indices;
    }
}
