package top.aihmc.omnimorph.model.rendering;

import org.figuramc.figura.model.rendering.Vertex;

import java.util.ArrayList;
import java.util.List;

/** Content-checked reuse of rigid Figura meshes; no GPU or frame-time conclusions. */
public final class FiguraStaticMeshCacheSmokeTest {
    public static void main(String[] args) {
        var cache = new FiguraStaticMeshCache();
        List<Vertex> vertices = quad();

        var first = cache.mesh(vertices, 4, 16, 16);
        require(cache.mesh(vertices, 4, 16, 16) == first, "unchanged vertex list reuses the same immutable mesh");

        vertices.get(2).y = 9;
        var moved = cache.mesh(vertices, 4, 16, 16);
        require(moved != first && moved.component(2, 1) == 9 && first.component(2, 1) == 3,
                "an in-place position edit yields a new mesh and leaves the old one untouched");
        require(cache.mesh(vertices, 4, 16, 16) == moved, "the edited content is reused afterwards");

        vertices.get(1).nx = 1;
        var normal = cache.mesh(vertices, 4, 16, 16);
        require(normal != moved && normal.component(1, 4) == 1, "normal edits are detected");

        vertices.get(3).v = 4;
        var uv = cache.mesh(vertices, 4, 16, 16);
        require(uv != normal && uv.component(3, 8) == 0.25, "UV edits are detected");

        var resized = cache.mesh(vertices, 4, 32, 32);
        require(resized != uv && resized.component(3, 8) == 0.125, "a texture size change rebuilds normalized UVs");

        List<Vertex> twoQuads = new ArrayList<>(quad());
        twoQuads.addAll(quad());
        var complete = cache.mesh(twoQuads, 8, 16, 16);
        var truncated = cache.mesh(twoQuads, 4, 16, 16);
        require(complete.vertexCount() == 8 && truncated.vertexCount() == 4 && truncated != complete,
                "a smaller remaining face budget does not reuse the complete mesh");

        var copy = quad();
        require(cache.mesh(copy, 4, 16, 16) != cache.mesh(quad(), 4, 16, 16),
                "separate vertex lists keep separate entries");

        var bounded = new FiguraStaticMeshCache();
        for (int index = 0; index <= FiguraStaticMeshCache.MAX_ENTRIES; index++) {
            bounded.mesh(quad(), 4, 16, 16);
        }
        require(bounded.size() <= FiguraStaticMeshCache.MAX_ENTRIES, "replaced vertex lists cannot grow the cache unbounded");
        System.out.println("PASS Figura static mesh reuse with immediate position/normal/UV/texture/face-budget changes");
    }

    private static List<Vertex> quad() {
        return new ArrayList<>(List.of(new Vertex(1, 2, 3, 8, 4, 0, 1, 0),
                new Vertex(2, 2, 3, 16, 4, 0, 1, 0), new Vertex(2, 3, 3, 16, 8, 0, 1, 0),
                new Vertex(1, 3, 3, 8, 8, 0, 1, 0)));
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
