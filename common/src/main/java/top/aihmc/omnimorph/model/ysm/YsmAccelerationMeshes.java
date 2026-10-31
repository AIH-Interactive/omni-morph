package top.aihmc.omnimorph.model.ysm;

import top.aihmc.omnimorph.render.acceleration.AccelerationService;
import top.aihmc.omnimorph.render.acceleration.MeshHandle;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/**
 * Runtime-owned immutable snapshots; mutable authored arrays are checked before reuse. Each snapshot keeps a flat
 * float copy of its visible faces (positions, UVs, normal), compared range by range with {@link Arrays#equals}, so
 * the per-frame check reads the authored arrays directly instead of going through the mesh one value at a time.
 */
final class YsmAccelerationMeshes {
    private static final int MAXIMUM_FACES = AccelerationService.MAX_BATCH_VERTICES / 4;
    private static final long MEMORY_BUDGET = 64L << 20;
    private static final int POSITIONS = 12;
    private static final int UVS = 8;
    private static final int NORMAL = 3;
    private static final int FACE_FLOATS = POSITIONS + UVS + NORMAL;

    private record Snapshot(List<MeshHandle> meshes, float[] source, int faces) {
    }

    private final Map<List<YsmGeometry.Cube>, Snapshot> snapshots = new IdentityHashMap<>();
    private long bytes;

    synchronized List<MeshHandle> get(List<YsmGeometry.Cube> cubes) {
        Snapshot existing = snapshots.get(cubes);
        if (existing != null && matches(cubes, existing)) {
            return existing.meshes();
        }
        if (existing != null) {
            snapshots.remove(cubes);
            bytes -= bytes(existing.meshes());
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
            snapshots.put(cubes, new Snapshot(result, source(faces), faces.size()));
            bytes += size;
        }
        return result;
    }

    private static float[] source(List<YsmGeometry.Quad> faces) {
        float[] source = new float[Math.multiplyExact(faces.size(), FACE_FLOATS)];
        for (int face = 0; face < faces.size(); face++) {
            YsmGeometry.Quad quad = faces.get(face);
            int offset = face * FACE_FLOATS;
            System.arraycopy(quad.positions(), 0, source, offset, POSITIONS);
            System.arraycopy(quad.uvs(), 0, source, offset + POSITIONS, UVS);
            System.arraycopy(quad.normal(), 0, source, offset + POSITIONS + UVS, NORMAL);
        }
        return source;
    }

    synchronized void clear() {
        if (!snapshots.isEmpty()) {
            snapshots.clear();
        }
        bytes = 0;
    }

    /** The visible faces, in order, still hold exactly the floats the meshes were built from. */
    private static boolean matches(List<YsmGeometry.Cube> cubes, Snapshot snapshot) {
        float[] source = snapshot.source();
        int face = 0;
        for (YsmGeometry.Cube cube : cubes) {
            for (YsmGeometry.Quad quad : cube.quads) {
                if (!quad.visible()) {
                    continue;
                }
                if (face >= snapshot.faces()) {
                    return false;
                }
                int offset = face * FACE_FLOATS;
                if (!Arrays.equals(source, offset, offset + POSITIONS, quad.positions(), 0, POSITIONS)
                        || !Arrays.equals(source, offset + POSITIONS, offset + POSITIONS + UVS, quad.uvs(), 0, UVS)
                        || !Arrays.equals(source, offset + POSITIONS + UVS, offset + FACE_FLOATS,
                        quad.normal(), 0, NORMAL)) {
                    return false;
                }
                face++;
            }
        }
        return face == snapshot.faces();
    }

    private static long bytes(List<MeshHandle> meshes) {
        return meshes.stream().mapToLong(mesh -> (long) mesh.vertexCount() * MeshHandle.STRIDE * Double.BYTES).sum();
    }
}
