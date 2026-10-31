package top.aihmc.omnimorph.model.rendering;

import org.figuramc.figura.model.rendering.Vertex;
import top.aihmc.omnimorph.render.acceleration.MeshHandle;

import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/**
 * Reuses the immutable rigid mesh of a Figura vertex list while its source content stays unchanged.
 * Vertices are public Lua-editable objects without change tracking, so every lookup compares the raw vertex
 * floats and texture size with the ones the mesh was packed from; any position, normal or UV edit, a texture
 * size change or a different face count yields a new handle immediately. Packing to doubles (and the UV
 * division) only happens on a mismatch: both are deterministic, so equal sources always pack to equal meshes.
 * Render thread only.
 */
final class FiguraStaticMeshCache {
    // Vertex lists are replaced only when geometry is rebuilt; the bound keeps replaced lists from piling up.
    static final int MAX_ENTRIES = 4096;
    private static final int SOURCE_FLOATS = 8;

    private record Entry(MeshHandle mesh, float[] source, int vertexCount, double textureWidth, double textureHeight) {
    }

    private final Map<List<Vertex>, Entry> meshes = new IdentityHashMap<>();

    MeshHandle mesh(List<Vertex> vertices, int vertexCount, double textureWidth, double textureHeight) {
        Entry cached = meshes.get(vertices);
        if (cached != null && cached.vertexCount() == vertexCount
                && sameBits(cached.textureWidth(), textureWidth) && sameBits(cached.textureHeight(), textureHeight)
                && sameSource(cached.source(), vertices, vertexCount)) {
            return cached.mesh();
        }
        float[] source = new float[Math.multiplyExact(vertexCount, SOURCE_FLOATS)];
        double[] packed = new double[Math.multiplyExact(vertexCount, MeshHandle.STRIDE)];
        for (int index = 0; index < vertexCount; index++) {
            Vertex vertex = vertices.get(index);
            int sourceOffset = index * SOURCE_FLOATS;
            source[sourceOffset] = vertex.x;
            source[sourceOffset + 1] = vertex.y;
            source[sourceOffset + 2] = vertex.z;
            source[sourceOffset + 3] = vertex.nx;
            source[sourceOffset + 4] = vertex.ny;
            source[sourceOffset + 5] = vertex.nz;
            source[sourceOffset + 6] = vertex.u;
            source[sourceOffset + 7] = vertex.v;
            int offset = index * MeshHandle.STRIDE;
            packed[offset] = vertex.x;
            packed[offset + 1] = vertex.y;
            packed[offset + 2] = vertex.z;
            packed[offset + 3] = 1;
            packed[offset + 4] = vertex.nx;
            packed[offset + 5] = vertex.ny;
            packed[offset + 6] = vertex.nz;
            packed[offset + 7] = vertex.u / textureWidth;
            packed[offset + 8] = vertex.v / textureHeight;
        }
        MeshHandle mesh = new MeshHandle(packed, 4);
        if (cached == null && meshes.size() >= MAX_ENTRIES) {
            meshes.clear();
        }
        meshes.put(vertices, new Entry(mesh, source, vertexCount, textureWidth, textureHeight));
        return mesh;
    }

    /** Bit-exact float comparison, reading only: no packing, division or writes on the unchanged path. */
    private static boolean sameSource(float[] source, List<Vertex> vertices, int vertexCount) {
        for (int index = 0; index < vertexCount; index++) {
            Vertex vertex = vertices.get(index);
            int offset = index * SOURCE_FLOATS;
            if (!sameBits(source[offset], vertex.x) || !sameBits(source[offset + 1], vertex.y)
                    || !sameBits(source[offset + 2], vertex.z) || !sameBits(source[offset + 3], vertex.nx)
                    || !sameBits(source[offset + 4], vertex.ny) || !sameBits(source[offset + 5], vertex.nz)
                    || !sameBits(source[offset + 6], vertex.u) || !sameBits(source[offset + 7], vertex.v)) {
                return false;
            }
        }
        return true;
    }

    private static boolean sameBits(float a, float b) {
        return Float.floatToRawIntBits(a) == Float.floatToRawIntBits(b);
    }

    private static boolean sameBits(double a, double b) {
        return Double.doubleToRawLongBits(a) == Double.doubleToRawLongBits(b);
    }

    int size() {
        return meshes.size();
    }
}
