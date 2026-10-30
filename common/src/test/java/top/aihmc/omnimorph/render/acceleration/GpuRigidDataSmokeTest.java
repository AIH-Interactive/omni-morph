package top.aihmc.omnimorph.render.acceleration;

import net.minecraft.SharedConstants;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.resources.Identifier;
import net.minecraft.server.Bootstrap;
import org.figuramc.figura.math.matrix.FiguraMat3;
import org.figuramc.figura.math.matrix.FiguraMat4;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
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
        testModelData(service, owner, material);
        testStorageLayout(batches);
        testStaticValidationReuse(service, owner, material);
    }

    private static final String PROBE_SOURCE = """
            #version 330
            in vec3 Position;
            in vec3 Normal;
            in vec2 UV0;
            in vec4 Color;
            in ivec2 UV1;
            in ivec2 UV2;
            void main() { gl_Position = vec4(Position, 1.0); }
            """;

    private static void testStorageLayout(List<DrawBatch> batches) {
        String enabled = GpuShaderProgram.enableStorage(PROBE_SOURCE);
        require(enabled.startsWith("#version 330\n#extension GL_ARB_shader_storage_buffer_object : require\n"
                        + "#extension GL_ARB_shading_language_420pack : require\n"),
                "storage extensions follow the original version directive before any declaration");
        String modern = PROBE_SOURCE.replace("#version 330", "#version 430 core");
        require(GpuShaderProgram.enableStorage(modern).equals(modern), "GLSL 4.30 sources need no extensions");
        try {
            GpuShaderProgram.enableStorage("void main() {}");
            throw new AssertionError("A source without #version must reject");
        } catch (IllegalArgumentException expected) {
            // Extension placement is only defined relative to the version directive.
        }

        String entity = GpuShaderProgram.wrapStorageEntitySource(PROBE_SOURCE);
        require(entity.contains("layout(std430, binding = 6) readonly buffer OmniMorphPoseBlock {")
                        && entity.contains("OmniPart OmniParts[];") && !entity.contains("uniform OmniMorphPose")
                        && !entity.contains("OmniMorphRemapBlock"),
                "the entity storage variant declares only the unsized pose block");
        String rigid = GpuShaderProgram.wrapStorageRigidEntitySource(PROBE_SOURCE);
        require(rigid.contains("layout(std430, binding = 7) readonly buffer OmniMorphRemapBlock {")
                        && rigid.contains("int part = int(OmniRemap[globalPart]);")
                        && !rigid.contains("packedRemap") && !rigid.contains("ivec4 OmniRemap[256]"),
                "the rigid storage variant reads each batch's global part index");
        String uniformRigid = GpuShaderProgram.wrapRigidEntitySource(PROBE_SOURCE);
        require(uniformRigid.contains("int packedRemap = OmniRemap[globalPart / 16][(globalPart / 4) % 4];")
                        && uniformRigid.contains("int part = (packedRemap >> ((globalPart % 4) * 8)) & 255;"),
                "the uniform rigid variant keeps its 64-part palette slots");

        DrawBatch batch = batches.get(3);
        ByteBuffer uniform = GpuBatchData.pose(batch);
        ByteBuffer storage = ByteBuffer.allocate(GpuBatchData.storagePoseBytes(1)).order(ByteOrder.nativeOrder());
        GpuBatchData.writeStoragePose(storage, batch);
        require(storage.position() == 48 + 160, "an unskinned storage block is 208 bytes");
        requireSame(storage, 0, uniform, GpuBatchData.PARAMETER_OFFSET, 48, "storage header holds the parameters");
        requireSame(storage, 48, uniform, 0, GpuBatchData.PART_BYTES, "storage part follows the header");

        var pair = new GpuRigidData(batches.subList(0, 2), new long[]{0, 1L << 32});
        ByteBuffer groupUniform = pair.pose(pair.ranges().getFirst());
        ByteBuffer groupStorage = ByteBuffer.allocate((int) pair.storagePoseBytes()).order(ByteOrder.nativeOrder());
        pair.writeStoragePose(groupStorage);
        require(groupStorage.position() == 48 + 2 * 160, "a two-part storage block holds exactly its distinct parts");
        requireSame(groupStorage, 0, groupUniform, GpuBatchData.PARAMETER_OFFSET, 48, "rigid header holds the parameters");
        requireSame(groupStorage, 48, groupUniform, 0, 2 * GpuBatchData.PART_BYTES, "rigid parts follow the header");
        System.out.println("PASS pose storage blocks: extension placement, std430 declarations, packed header and parts");
        testStorageSingleRange(batches);
    }

    /**
     * R4-1: a storage group of more than 64 distinct parts, sorted so its faces alternate between them, still
     * draws as one range, and every face's batch resolves through the remap to its own transform.
     */
    private static void testStorageSingleRange(List<DrawBatch> batches) {
        long[] interleaved = new long[batches.size()];
        for (int index = 0; index < interleaved.length; index++) {
            // Alternate ends of the batch list, the worst case for 64-part palettes.
            int batch = index % 2 == 0 ? index / 2 : batches.size() - 1 - index / 2;
            interleaved[index] = (long) batch << 32;
        }
        var group = new GpuRigidData(batches, interleaved);
        require(group.ranges().size() > 1, "the uniform path still splits more than 64 parts into palette ranges");
        require(group.partCount() == 65 && group.faceCount() == batches.size(),
                "the storage group covers every distinct part and face");

        ByteBuffer pose = ByteBuffer.allocate((int) group.storagePoseBytes()).order(ByteOrder.nativeOrder());
        group.writeStoragePose(pose);
        ByteBuffer remap = ByteBuffer.allocate((int) group.storageRemapBytes()).order(ByteOrder.nativeOrder());
        group.writeStorageRemap(remap);
        require(pose.position() == 48 + 65 * 160 && remap.position() == batches.size() * Integer.BYTES,
                "one pose block with every part and one uint per batch");

        var geometry = group.vertices();
        var indices = group.indices();
        for (int face = 0; face < group.faceCount(); face++) {
            int vertex = indices.getInt(face * 6 * Integer.BYTES);
            int batch = geometry.getInt(vertex * GpuBatchData.VERTEX_BYTES + 48);
            int part = remap.getInt(batch * Integer.BYTES);
            require(part >= 0 && part < group.partCount(), "every face resolves to a stored part");
            int partOffset = GpuBatchData.STORAGE_HEADER_BYTES + part * GpuBatchData.PART_BYTES;
            require(pose.getFloat(partOffset + 48) == batch % 65,
                    "the global remap selects the authored transform in the single range");
            require(pose.getFloat(partOffset + 108) == (float) (batch % 65 * .001),
                    "part-specific vertex offset survives the single range");
        }
        System.out.println("PASS R4-1 storage groups: 65 interleaved parts draw as one range with global part remap");
    }

    private static void requireSame(ByteBuffer actual, int actualOffset, ByteBuffer expected, int expectedOffset,
                                    int bytes, String message) {
        for (int offset = 0; offset < bytes; offset++) {
            require(actual.get(actualOffset + offset) == expected.get(expectedOffset + offset), message);
        }
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

    /**
     * R4-3 M1: a model boundary mixing skinned, tinted and plain batches keeps one vertex/part/record layout whose
     * decoded shader math reproduces the Java path for every vertex.
     */
    private static void testModelData(AccelerationService service, AccelerationOwner owner,
                                      net.minecraft.client.renderer.rendertype.RenderType material) {
        double[] vertices = new double[8 * MeshHandle.STRIDE];
        List<MeshHandle.Influences> influences = new ArrayList<>();
        for (int vertex = 0; vertex < 8; vertex++) {
            vertices[vertex * MeshHandle.STRIDE] = vertex % 3 - 1;
            vertices[vertex * MeshHandle.STRIDE + 1] = vertex * 0.25;
            vertices[vertex * MeshHandle.STRIDE + 2] = 0.5 - vertex % 2;
            vertices[vertex * MeshHandle.STRIDE + 3] = 1;
            vertices[vertex * MeshHandle.STRIDE + 5] = 1;
            influences.add(new MeshHandle.Influences(new int[]{0, 1}, new float[]{vertex % 4 + 1, 4 - vertex % 4}));
        }
        var skinnedMesh = new MeshHandle(vertices, 4, influences);
        var plainMesh = new MeshHandle(vertices.clone(), 4);
        FiguraMat4[] deltas = {FiguraMat4.of().translate(0, 1, 0), FiguraMat4.of().scale(1, 2, 1).translate(0.5, 0, 0)};
        List<DrawBatch> model = new ArrayList<>();
        model.add(modelBatch(service, owner, material, plainMesh,
                new PoseSnapshot(FiguraMat4.of().translate(1, 2, 3), FiguraMat3.of()), 0x00f000a0, 1));
        model.add(modelBatch(service, owner, material, skinnedMesh,
                new PoseSnapshot(FiguraMat4.of().scale(2, 1, 1).translate(-1, 0, 4), FiguraMat3.of(), FiguraMat3.of(), deltas),
                0x00f000a0, 1));
        model.add(modelBatch(service, owner, material, plainMesh,
                new PoseSnapshot(FiguraMat4.of().translate(0, -3, 1), FiguraMat3.of()), 0x000a00f0, 0.25f));
        require(!GpuRigidData.supports(model) && GpuModelData.supports(model),
                "skinning and differing light/tint reject rigid groups but not model draws");

        long[] order = new long[6];
        for (int index = 0; index < order.length; index++) {
            order[index] = (long) (index % 3) << 32 | (index / 3);
        }
        var data = new GpuModelData(model, order);
        require(data.partCount() == 3 + 2 && data.faceCount() == 6 && !data.authoredOrder()
                        && java.util.Arrays.equals(data.encodingSignature(), new int[]{0, 2, 0}),
                "three roots plus two skin deltas; interleaved order; joint counts in the cache signature");
        ByteBuffer geometry = data.vertices();
        ByteBuffer pose = ByteBuffer.allocate((int) data.storagePoseBytes()).order(ByteOrder.nativeOrder());
        data.writeStoragePose(pose);
        ByteBuffer records = ByteBuffer.allocate((int) data.recordBytes()).order(ByteOrder.nativeOrder());
        data.writeRecords(records);
        require(pose.position() == 48 + 5 * 160 && records.position() == 3 * 48, "pose and record block sizes");
        ByteBuffer ordinary = GpuBatchData.pose(model.get(2));
        requireSame(records, 2 * 48, ordinary, GpuBatchData.PARAMETER_OFFSET, 48,
                "each record is exactly the ordinary batch parameter block");

        int vertex = 0;
        for (int batch = 0; batch < model.size(); batch++) {
            double[] expected = new JavaAccelerationBackend().transform(model.get(batch));
            for (int local = 0; local < model.get(batch).mesh().vertexCount(); local++, vertex++) {
                int base = vertex * GpuBatchData.VERTEX_BYTES;
                int encoded = geometry.getInt(base + 48);
                require(encoded >>> 16 == batch, "vertex carries its batch index");
                int record = (encoded >>> 16) * 48;
                double[] point = {geometry.getFloat(base), geometry.getFloat(base + 4), geometry.getFloat(base + 8)};
                if (records.getFloat(record + 40) != 0) {
                    int[] joints = {encoded & 0xFFFF, geometry.getInt(base + 52), geometry.getInt(base + 56),
                            geometry.getInt(base + 60)};
                    double[] blended = new double[3];
                    double total = 0;
                    for (int influence = 0; influence < 4; influence++) {
                        float weight = geometry.getFloat(base + 32 + influence * 4);
                        if (weight > 0) {
                            double[] moved = apply(pose, 48 + joints[influence] * 160, point);
                            for (int axis = 0; axis < 3; axis++) {
                                blended[axis] += moved[axis] * weight;
                            }
                            total += weight;
                        }
                    }
                    for (int axis = 0; axis < 3; axis++) {
                        point[axis] = blended[axis] / total;
                    }
                }
                double[] transformed = apply(pose, 48 + batch * 160, point);
                for (int axis = 0; axis < 3; axis++) {
                    double reference = expected[local * MeshHandle.STRIDE + axis];
                    require(Math.abs(transformed[axis] - reference) <= 1e-4 + 1e-5 * Math.abs(reference),
                            "decoded model math matches the Java path: batch=" + batch + " vertex=" + local
                                    + " axis=" + axis + " expected=" + reference + " actual=" + transformed[axis]);
                }
            }
        }
        var indices = data.indices();
        require(indices.getInt(6 * Integer.BYTES) == 8, "interleaved order addresses the skinned batch's vertices");
        System.out.println("PASS R4-3 model data: skinned/tinted/plain batches decode to the Java result in one layout");
    }

    private static DrawBatch modelBatch(AccelerationService service, AccelerationOwner owner,
                                        net.minecraft.client.renderer.rendertype.RenderType material, MeshHandle mesh,
                                        PoseSnapshot pose, int light, float red) {
        var semantics = new DrawSemantics(material, Identifier.parse("omni-morph:rigid_contract"),
                DrawSemantics.Layer.PRIMARY, "world", 0, mesh.faceCount(), light, 0, red, 1, 1, 1, 0, true);
        return new DrawBatch(service.capture(owner, "world"), mesh, pose, semantics);
    }

    /** Column-major float mat4 at {@code offset} applied to (point, 1), as the shader does. */
    private static double[] apply(ByteBuffer block, int offset, double[] point) {
        double[] result = new double[3];
        for (int row = 0; row < 3; row++) {
            result[row] = block.getFloat(offset + row * 4) * point[0] + block.getFloat(offset + (4 + row) * 4) * point[1]
                    + block.getFloat(offset + (8 + row) * 4) * point[2] + block.getFloat(offset + (12 + row) * 4);
        }
        return result;
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
