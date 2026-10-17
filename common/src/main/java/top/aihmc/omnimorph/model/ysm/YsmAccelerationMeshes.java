package top.aihmc.omnimorph.model.ysm;

import top.aihmc.omnimorph.render.acceleration.AccelerationService;
import top.aihmc.omnimorph.render.acceleration.MeshHandle;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/** Runtime-owned immutable snapshots; mutable authored arrays are checked before reuse. */
final class YsmAccelerationMeshes {
    private static final int MAXIMUM_FACES = AccelerationService.MAX_BATCH_VERTICES / 4;
    private static final long MEMORY_BUDGET = 64L << 20;
    private final Map<List<YsmGeometry.Cube>, List<MeshHandle>> snapshots = new IdentityHashMap<>();
    private long bytes;

    synchronized List<MeshHandle> get(List<YsmGeometry.Cube> cubes) {
        List<MeshHandle> existing = snapshots.get(cubes);
        if (existing != null && matches(cubes, existing)) {
            return existing;
        }
        if (existing != null) {
            snapshots.remove(cubes);
            bytes -= bytes(existing);
        }
        List<YsmGeometry.Quad> faces = new ArrayList<>();
        for (YsmGeometry.Cube cube : cubes) {
            for (YsmGeometry.Quad quad : cube.quads) {
                if (quad.visible()) {
                    faces.add(quad);
                }
            }
        }
        List<MeshHandle> meshes = new ArrayList<>();
        for (int first = 0; first < faces.size(); first += MAXIMUM_FACES) {
            int count = Math.min(MAXIMUM_FACES, faces.size() - first);
            double[] vertices = new double[count * 4 * MeshHandle.STRIDE];
            for (int face = 0; face < count; face++) {
                YsmGeometry.Quad quad = faces.get(first + face);
                for (int vertex = 0; vertex < 4; vertex++) {
                    int offset = (face * 4 + vertex) * MeshHandle.STRIDE;
                    for (int component = 0; component < 3; component++) {
                        vertices[offset + component] = quad.positions()[vertex * 3 + component];
                        vertices[offset + 4 + component] = quad.normal()[component];
                    }
                    vertices[offset + 3] = 1;
                    vertices[offset + 7] = quad.uvs()[vertex * 2];
                    vertices[offset + 8] = quad.uvs()[vertex * 2 + 1];
                }
            }
            meshes.add(new MeshHandle(vertices, 4));
        }
        List<MeshHandle> result = List.copyOf(meshes);
        long size = bytes(result);
        if (size <= MEMORY_BUDGET && !result.isEmpty()) {
            if (bytes + size > MEMORY_BUDGET || snapshots.size() >= 4096) {
                clear();
            }
            snapshots.put(cubes, result);
            bytes += size;
        }
        return result;
    }

    synchronized void clear() {
        if (!snapshots.isEmpty()) {
            snapshots.clear();
        }
        bytes = 0;
    }

    private static boolean matches(List<YsmGeometry.Cube> cubes, List<MeshHandle> meshes) {
        int face = 0;
        int expected = meshes.stream().mapToInt(MeshHandle::faceCount).sum();
        for (YsmGeometry.Cube cube : cubes) {
            for (YsmGeometry.Quad quad : cube.quads) {
                if (!quad.visible()) {
                    continue;
                }
                if (face >= expected) {
                    return false;
                }
                MeshHandle mesh = meshes.get(face / MAXIMUM_FACES);
                int firstVertex = face % MAXIMUM_FACES * 4;
                for (int vertex = 0; vertex < 4; vertex++) {
                    for (int component = 0; component < 3; component++) {
                        if (!same(mesh.component(firstVertex + vertex, component), quad.positions()[vertex * 3 + component])) {
                            return false;
                        }
                    }
                    if (!same(mesh.component(firstVertex + vertex, 7), quad.uvs()[vertex * 2])
                            || !same(mesh.component(firstVertex + vertex, 8), quad.uvs()[vertex * 2 + 1])) {
                        return false;
                    }
                }
                for (int component = 0; component < 3; component++) {
                    if (!same(mesh.component(firstVertex, 4 + component), quad.normal()[component])) {
                        return false;
                    }
                }
                face++;
            }
        }
        return face == expected;
    }

    private static boolean same(double snapshot, float current) {
        return Double.doubleToLongBits(snapshot) == Double.doubleToLongBits(current);
    }

    private static long bytes(List<MeshHandle> meshes) {
        return meshes.stream().mapToLong(mesh -> (long) mesh.vertexCount() * MeshHandle.STRIDE * Double.BYTES).sum();
    }
}
