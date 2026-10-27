package top.aihmc.omnimorph.model.rendering;

import org.figuramc.figura.model.rendering.Vertex;
import top.aihmc.omnimorph.render.acceleration.MeshHandle;

import java.util.Arrays;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/**
 * Reuses the immutable rigid mesh of a Figura vertex list while its packed content stays unchanged.
 * Vertices are public Lua-editable objects without change tracking, so every lookup re-packs and compares
 * the content; any position, normal or UV edit, a texture size change or a different face count yields a
 * new handle immediately. Render thread only.
 */
final class FiguraStaticMeshCache {
    // Vertex lists are replaced only when geometry is rebuilt; the bound keeps replaced lists from piling up.
    static final int MAX_ENTRIES = 4096;

    private final Map<List<Vertex>, MeshHandle> meshes = new IdentityHashMap<>();
    private double[] scratch = new double[0];

    MeshHandle mesh(List<Vertex> vertices, int vertexCount, double textureWidth, double textureHeight) {
        int length = Math.multiplyExact(vertexCount, MeshHandle.STRIDE);
        if (scratch.length < length) {
            scratch = new double[length];
        }
        for (int index = 0; index < vertexCount; index++) {
            Vertex vertex = vertices.get(index);
            int offset = index * MeshHandle.STRIDE;
            scratch[offset] = vertex.x;
            scratch[offset + 1] = vertex.y;
            scratch[offset + 2] = vertex.z;
            scratch[offset + 3] = 1;
            scratch[offset + 4] = vertex.nx;
            scratch[offset + 5] = vertex.ny;
            scratch[offset + 6] = vertex.nz;
            scratch[offset + 7] = vertex.u / textureWidth;
            scratch[offset + 8] = vertex.v / textureHeight;
        }

        MeshHandle cached = meshes.get(vertices);
        if (cached != null && cached.sameRigidVertices(scratch, length)) {
            return cached;
        }
        MeshHandle mesh = new MeshHandle(Arrays.copyOf(scratch, length), 4);
        if (cached == null && meshes.size() >= MAX_ENTRIES) {
            meshes.clear();
        }
        meshes.put(vertices, mesh);
        return mesh;
    }

    int size() {
        return meshes.size();
    }
}
