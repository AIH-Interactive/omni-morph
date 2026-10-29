package top.aihmc.omnimorph.render.acceleration;

import net.minecraft.SharedConstants;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.resources.Identifier;
import net.minecraft.server.Bootstrap;
import org.figuramc.figura.math.matrix.FiguraMat3;
import org.figuramc.figura.math.matrix.FiguraMat4;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** CPU packing checks; shader execution and real GPU timing require the final window tests. */
public final class GpuRigidDataSmokeTest {
    public static void main(String[] args) {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        var service = new AccelerationService();
        var owner = new AccelerationOwner();
        var material = RenderTypes.entityTranslucent(Identifier.parse("omni-morph:rigid_contract"));
        double[] vertices = new double[4 * MeshHandle.STRIDE];
        for (int vertex = 0; vertex < 4; vertex++) {
            vertices[vertex * MeshHandle.STRIDE + 3] = 1;
            vertices[vertex * MeshHandle.STRIDE + 5] = 1;
        }
        var mesh = new MeshHandle(vertices, 4);
        List<DrawBatch> batches = new ArrayList<>();
        long[] order = new long[130];
        for (int index = 0; index < order.length; index++) {
            int pose = index % 65;
            var semantics = new DrawSemantics(material, Identifier.parse("omni-morph:rigid_contract"),
                    DrawSemantics.Layer.PRIMARY, "world", 0, 1, 0x00f000a0, 0, 1, 1, 1, 1, pose * .001, true);
            batches.add(new DrawBatch(service.capture(owner, "world"), mesh,
                    new PoseSnapshot(FiguraMat4.of().translate(pose, 0, 0), FiguraMat3.of()), semantics));
            order[index] = (long) (order.length - index - 1) << 32;
        }
        require(GpuRigidData.supports(batches), "eligible rigid batches share a material and owner");
        var data = new GpuRigidData(batches, order);
        order[0] = 0;
        require(data.ranges().size() == 3 && GpuRigidData.POSE_BYTES <= 16 * 1024,
                "65 distinct poses split sorted ranges while retaining the minimum UBO limit");
        var geometry = data.vertices();
        var indices = data.indices();
        require(indices.getInt(0) == 129 * 4 && indices.getInt(3 * Integer.BYTES) == 129 * 4
                && indices.getInt(5 * Integer.BYTES) == 129 * 4 + 3,
                "owned face order retains the original quad fan winding");
        int faces = 0;
        for (GpuRigidData.Range range : data.ranges()) {
            require(range.parts().size() <= 64, "a range never exceeds its matrix palette");
            var pose = data.pose(range);
            require(pose.remaining() == GpuRigidData.POSE_BYTES, "uniform payload size matches the declared layout");
            for (int face = range.firstFace(); face < range.firstFace() + range.faceCount(); face++) {
                int vertex = indices.getInt(face * 6 * Integer.BYTES);
                int batch = geometry.getInt(vertex * GpuBatchData.VERTEX_BYTES + 48);
                int packed = pose.getInt(GpuRigidData.REMAP_OFFSET + batch / 4 * Integer.BYTES);
                int slot = packed >>> ((batch % 4) * 8) & 0xff;
                require(slot < range.parts().size(), "every indexed face resolves to a present local part");
                require(pose.getFloat(slot * GpuBatchData.PART_BYTES + 48) == batch % 65,
                        "remapping selects the authored transform across palette boundaries");
                require(pose.getFloat(slot * GpuBatchData.PART_BYTES + 108) == (float) (batch % 65 * .001),
                        "part-specific vertex offset survives palette deduplication");
                faces++;
            }
        }
        require(faces == 130 && data.frameBytes() == 130L * 6 * Integer.BYTES + 3L * GpuRigidData.POSE_BYTES,
                "every sorted face is covered exactly once and frame accounting includes indices");
        require(!GpuRigidData.supports(Collections.nCopies(4097, batches.getFirst())), "part remap bound is enforced");
        var foreign = new DrawBatch(service.capture(new AccelerationOwner(), "world"), mesh,
                batches.getFirst().pose(), batches.getFirst().semantics());
        require(!GpuRigidData.supports(List.of(batches.getFirst(), foreign)), "owners retain separate resource lifetimes");
        try {
            new GpuRigidData(batches, new long[]{(long) batches.size() << 32});
            throw new AssertionError("Invalid sorted face must reject");
        } catch (IllegalArgumentException expected) {
            // Invalid indices cannot reach a GPU upload.
        }
        System.out.println("PASS rigid palette deduplication, 64-part ranges, exact face order, packed remap, bounds and frame accounting");
        testLayoutReuse(batches, data);
        testPoseViews(batches.get(1));
        testRangeVariants(batches);
        testStaticValidationReuse(service, owner, material);
    }

    private static void testRangeVariants(List<DrawBatch> batches) {
        var pair = new GpuRigidData(batches.subList(0, 2), new long[]{0, 1L << 32});
        var range = pair.ranges().getFirst();
        require(range.parts().size() == 2 && GpuBatchData.tierFor(range.parts().size()) == 8,
                "a two-part palette selects the eight-part variant");
        var full = pair.pose(range);
        var small = pair.pose(range, 8);
        require(small.remaining() == GpuRigidData.poseBytes(8) && small.remaining() < full.remaining(),
                "the eight-part variant uploads fewer bytes than the full block");
        for (int slot = 0; slot < range.parts().size(); slot++) {
            for (int offset = 0; offset < GpuBatchData.PART_BYTES; offset += Float.BYTES) {
                int position = slot * GpuBatchData.PART_BYTES + offset;
                require(small.getFloat(position) == full.getFloat(position), "palette parts are packed identically");
            }
        }
        for (int offset = 0; offset < 48; offset += Integer.BYTES) {
            require(small.getInt(GpuBatchData.parameterOffset(8) + offset)
                            == full.getInt(GpuBatchData.PARAMETER_OFFSET + offset),
                    "parameters follow the variant's last part");
        }
        for (int offset = 0; offset < GpuRigidData.MAX_BATCHES; offset += Integer.BYTES) {
            require(small.getInt(GpuRigidData.remapOffset(8) + offset) == full.getInt(GpuRigidData.REMAP_OFFSET + offset),
                    "the remap table follows the variant parameters unchanged");
        }
        try {
            pair.pose(range, 1);
            throw new AssertionError("A variant smaller than the palette must reject");
        } catch (IllegalArgumentException expected) {
            // The backend never selects a variant that cannot hold the palette.
        }
        require(GpuBatchData.tierFor(1) == 1 && GpuBatchData.tierFor(9) == 32 && GpuBatchData.tierFor(64) == 64,
                "tiers select the smallest holding variant");
        try {
            GpuBatchData.tierFor(65);
            throw new AssertionError("More than 64 parts must reject");
        } catch (IllegalArgumentException expected) {
            // Palettes are split at 64 parts before reaching a pose block.
        }
        String shader = GpuShaderProgram.wrapRigidEntitySource("""
                #version 330
                in vec3 Position;
                in vec3 Normal;
                in vec2 UV0;
                in vec4 Color;
                in ivec2 UV1;
                in ivec2 UV2;
                void main() { gl_Position = vec4(Position, 1.0); }
                """, 8);
        require(shader.contains("OmniParts[8];") && shader.contains("OmniRemap[256];"),
                "the variant shader declares exactly the packed part capacity");
        System.out.println("PASS pose block variants: tier selection, packed sizes, parameter/remap offsets and shader capacity");
    }

    private static void testPoseViews(DrawBatch batch) {
        PoseSnapshot pose = batch.pose();
        require(pose.positionMatrix() == pose.positionMatrix() && pose.normalMatrix() == pose.normalMatrix()
                        && pose.uvMatrix() == pose.uvMatrix(),
                "validation and packing read one cached matrix view per snapshot");
        double[] copy = pose.copyPositionMatrix();
        require(copy != pose.positionMatrix() && copy[12] == pose.positionMatrix()[12], "public copies stay detached");
        copy[12] = 1234;
        require(pose.positionMatrix()[12] != 1234 && pose.copyPositionMatrix()[12] != 1234,
                "modifying a public copy never reaches the immutable snapshot");

        var nonFinite = new DrawBatch(batch.lease(), batch.mesh(),
                new PoseSnapshot(FiguraMat4.of().translate(Double.NaN, 0, 0), FiguraMat3.of()), batch.semantics());
        var overflow = new DrawBatch(batch.lease(), batch.mesh(),
                new PoseSnapshot(FiguraMat4.of().translate(Float.MAX_VALUE, 0, 0), FiguraMat3.of()), batch.semantics());
        require(GpuBatchData.supports(batch) && !GpuBatchData.supports(nonFinite) && !GpuBatchData.supports(overflow),
                "NaN and out-of-range poses are still rejected through the cached views");
        System.out.println("PASS read-only pose views, detached public copies and non-finite/overflow rejection");
    }

    private static void testLayoutReuse(List<DrawBatch> batches, GpuRigidData previous) {
        long[] sorted = new long[batches.size()];
        for (int index = 0; index < sorted.length; index++) {
            sorted[index] = (long) (sorted.length - index - 1) << 32;
        }
        var stable = new GpuRigidData(batches, sorted, previous.layout());
        require(stable.layoutReused() && stable.rangesReused() && stable.ranges() == previous.ranges(),
                "an unchanged order and part sharing reuse layout and ranges");

        List<DrawBatch> shared = new ArrayList<>(batches);
        shared.set(1, new DrawBatch(batches.get(1).lease(), batches.get(1).mesh(),
                batches.get(0).pose(), batches.get(0).semantics()));
        var resharing = new GpuRigidData(shared, sorted, stable.layout());
        require(resharing.layoutReused() && !resharing.rangesReused(),
                "a changed part sharing pattern rebuilds ranges on the cached layout");
        var restored = new GpuRigidData(batches, sorted, resharing.layout());
        require(!restored.rangesReused() && restored.ranges().size() == 3,
                "restored sharing rebuilds ranges again instead of keeping stale palettes");

        long[] reordered = sorted.clone();
        long swap = reordered[0];
        reordered[0] = reordered[1];
        reordered[1] = swap;
        var moved = new GpuRigidData(batches, reordered, restored.layout());
        require(!moved.layoutReused() && !moved.rangesReused() && moved.layout() != restored.layout(),
                "a changed face order builds a new layout");
        var indices = moved.indices();
        require(indices.getInt(0) == 128 * 4, "the new layout drives index generation");
        System.out.println("PASS rigid group layout reuse and invalidation by face order and part sharing");
    }

    private static void testStaticValidationReuse(AccelerationService service, AccelerationOwner owner,
                                                  net.minecraft.client.renderer.rendertype.RenderType material) {
        var semantics = new DrawSemantics(material, Identifier.parse("omni-morph:static_validation"),
                DrawSemantics.Layer.PRIMARY, "world", 0, 1, 0x00f000a0, 0, 1, 1, 1, 1, 0, true);
        var mesh = new MeshHandle(quad(1), 4);
        var identity = new PoseSnapshot(FiguraMat4.of(), FiguraMat3.of());
        require(GpuBatchData.supports(new DrawBatch(service.capture(owner, "world"), mesh, identity, semantics)),
                "valid immutable mesh is accepted");
        var summary = mesh.staticSummary();
        var moved = new PoseSnapshot(FiguraMat4.of().translate(3, 0, 0), FiguraMat3.of());
        require(GpuBatchData.supports(new DrawBatch(service.capture(owner, "world"), mesh, moved, semantics))
                        && mesh.staticSummary() == summary,
                "repeated submissions of the same mesh reuse one static scan");

        // Dynamic checks still run against a cached valid mesh.
        var invalidPose = FiguraMat4.of();
        invalidPose.v11 = Double.NaN;
        require(!GpuBatchData.supports(new DrawBatch(service.capture(owner, "world"), mesh,
                        new PoseSnapshot(invalidPose, FiguraMat3.of()), semantics)),
                "non-finite pose is rejected even when static geometry is cached");

        // Invalid static data stays rejected.
        double[] badW = quad(1);
        badW[3] = 2;
        require(!GpuBatchData.supports(new DrawBatch(service.capture(owner, "world"), new MeshHandle(badW, 4),
                identity, semantics)), "non-unit w is rejected");
        double[] nan = quad(1);
        nan[MeshHandle.STRIDE + 7] = Double.NaN;
        require(!GpuBatchData.supports(new DrawBatch(service.capture(owner, "world"), new MeshHandle(nan, 4),
                identity, semantics)), "non-finite UV is rejected");

        // Joint bounds depend on the pose's joint count, so one cached mesh can pass or fail per submission.
        var weights = new ArrayList<MeshHandle.Influences>();
        for (int vertex = 0; vertex < 4; vertex++) {
            weights.add(new MeshHandle.Influences(new int[]{0, 1}, new float[]{.5f, .5f}));
        }
        var skinned = new MeshHandle(quad(1), 4, weights);
        var twoJoints = new PoseSnapshot(FiguraMat4.of(), FiguraMat3.of(), FiguraMat3.of(),
                new FiguraMat4[]{FiguraMat4.of(), FiguraMat4.of()});
        var oneJoint = new PoseSnapshot(FiguraMat4.of(), FiguraMat3.of(), FiguraMat3.of(),
                new FiguraMat4[]{FiguraMat4.of()});
        require(GpuBatchData.supports(new DrawBatch(service.capture(owner, "world"), skinned, twoJoints, semantics)),
                "weighted joints inside the pose palette are accepted");
        require(!GpuBatchData.supports(new DrawBatch(service.capture(owner, "world"), skinned, oneJoint, semantics)),
                "the same cached mesh rejects a pose without its weighted joint");
        var negative = new ArrayList<MeshHandle.Influences>();
        for (int vertex = 0; vertex < 4; vertex++) {
            negative.add(new MeshHandle.Influences(new int[]{0}, new float[]{-1}));
        }
        require(!GpuBatchData.supports(new DrawBatch(service.capture(owner, "world"),
                new MeshHandle(quad(1), 4, negative), twoJoints, semantics)), "negative weights are rejected");
        System.out.println("PASS static mesh validation reuse with per-submission pose and joint checks");
    }

    private static double[] quad(double size) {
        double[] vertices = new double[4 * MeshHandle.STRIDE];
        for (int vertex = 0; vertex < 4; vertex++) {
            vertices[vertex * MeshHandle.STRIDE] = vertex % 2 * size;
            vertices[vertex * MeshHandle.STRIDE + 1] = vertex / 2 * size;
            vertices[vertex * MeshHandle.STRIDE + 3] = 1;
            vertices[vertex * MeshHandle.STRIDE + 5] = 1;
        }
        return vertices;
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
