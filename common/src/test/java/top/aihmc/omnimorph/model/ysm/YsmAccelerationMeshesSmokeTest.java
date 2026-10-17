package top.aihmc.omnimorph.model.ysm;

import top.aihmc.omnimorph.render.acceleration.MeshHandle;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.List;

/** Checks mutable source data cannot make an immutable renderer snapshot stale. */
public final class YsmAccelerationMeshesSmokeTest {
    public static void main(String[] args) throws Exception {
        var quad = new YsmGeometry.Quad(new float[12], new float[8], new float[]{0, 1, 0});
        var faces = new ArrayList<>(List.of(quad));
        var cubes = new ArrayList<>(List.of(new YsmGeometry.Cube(faces)));
        var cache = new YsmAccelerationMeshes();
        List<MeshHandle> original = cache.get(cubes);
        require(cache.get(cubes) == original, "unchanged geometry reuses the immutable snapshot");
        quad.positions()[0] = 2;
        List<MeshHandle> moved = cache.get(cubes);
        require(moved != original && moved.getFirst().component(0, 0) == 2
                && original.getFirst().component(0, 0) == 0, "direct position edits replace only the new snapshot");
        quad.normal()[1] = -1;
        quad.uvs()[0] = .5f;
        List<MeshHandle> changed = cache.get(cubes);
        require(changed != moved && changed.getFirst().component(0, 5) == -1
                && changed.getFirst().component(0, 7) == .5, "normal and UV edits are visible immediately");
        faces.add(quad);
        require(cache.get(cubes).getFirst().faceCount() == 2, "mutable face lists invalidate the snapshot");
        faces.clear();
        require(cache.get(cubes).isEmpty(), "removed faces cannot remain in cached rendering");
        faces.add(quad);
        cache.get(cubes);
        var geometry = new YsmGeometry();
        var bone = new YsmGeometry.Bone("test", null, new float[3], new float[3]);
        bone.cubes.addAll(cubes);
        geometry.bones.put(bone.name, bone);
        var image = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
        var png = new ByteArrayOutputStream();
        ImageIO.write(image, "png", png);
        geometry.applyTextureAlpha(png.toByteArray());
        require(cache.get(cubes).isEmpty(), "texture alpha visibility changes remove cached faces");
        geometry.applyTextureAlpha(null);
        require(cache.get(cubes).getFirst().faceCount() == 1, "faces can become visible and accelerate again");
        faces.clear();
        for (int index = 0; index < 16_385; index++) {
            faces.add(quad);
        }
        List<MeshHandle> chunks = cache.get(cubes);
        require(chunks.size() == 2 && chunks.get(0).vertexCount() == 65_536
                && chunks.get(1).vertexCount() == 4 && cache.get(cubes) == chunks,
                "batch limits split only complete quads and retain a reusable tail");
        cache.clear();
        require(cache.get(cubes) != chunks, "disable drops runtime-owned snapshots");
        System.out.println("PASS YSM immutable mesh reuse, direct mutable edits, alpha visibility, batch tails and clear");
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
