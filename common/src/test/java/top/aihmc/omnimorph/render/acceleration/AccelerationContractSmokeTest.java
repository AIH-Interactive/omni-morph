package top.aihmc.omnimorph.render.acceleration;

import com.mojang.blaze3d.vertex.PoseStack;
import top.aihmc.omnimorph.math.matrix.FiguraMat3;
import top.aihmc.omnimorph.math.matrix.FiguraMat4;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/** Contract probes use independent coefficients and intentionally stale submissions. */
public final class AccelerationContractSmokeTest {
    private AccelerationContractSmokeTest() {
    }

    public static void main(String[] args) {
        double[] input = {1, 2, 3, 1, 0, 1, 0, 0.25, 0.5};
        MeshHandle mesh = new MeshHandle(input, 1);
        Arrays.fill(input, 900);
        double[] exposed = mesh.copyVertices();
        Arrays.fill(exposed, -900);
        require(mesh.component(0, 0) == 1, "geometry owns both constructor and accessor arrays");

        AccelerationService service = new AccelerationService();
        AccelerationOwner owner = new AccelerationOwner();
        var lease = service.capture(owner, "contract-view");
        FiguraMat4 position = FiguraMat4.of();
        position.scale(-2, 3, 4);
        position.translate(5, 6, 7);
        FiguraMat3 normal = FiguraMat3.of();
        normal.v22 = 1d / 3d;
        PoseSnapshot pose = new PoseSnapshot(position, normal);
        position.resetIdentity();
        normal.resetIdentity();
        double[] result = service.transform(lease, token -> new DrawBatch(token, mesh, pose));
        assertValues(result, new double[]{3, 12, 19, 1, 0, 1d / 3d, 0, 0.25, 0.5}, 1e-12,
                "owned double position, separate inverse-transpose normal and negative scale");
        result[0] = 999;
        assertValues(service.transform(lease, token -> new DrawBatch(token, mesh, pose)),
                new double[]{3, 12, 19, 1, 0, 1d / 3d, 0, 0.25, 0.5}, 1e-12, "result ownership");
        require(pose.mirrored(), "negative determinant is preserved explicitly");
        testSkinAndUv(service, lease, mesh);

        PoseStack stack = new PoseStack();
        stack.translate(3, 4, 5);
        stack.scale(2, 3, 4);
        PoseSnapshot floatPose = new PoseSnapshot(stack.last());
        stack.setIdentity();
        assertValues(service.transform(lease, token -> new DrawBatch(token, mesh, floatPose)),
                new double[]{5, 10, 17, 1, 0, 1, 0, 0.25, 0.5}, 1e-6, "vanilla float/normal snapshot");

        AtomicInteger captures = new AtomicInteger();
        EngineAccelerationAdapter adapter = token -> {
            captures.incrementAndGet();
            return new DrawBatch(token, mesh, pose);
        };
        service.beginFrame();
        require(service.transform(lease, adapter) == null && captures.get() == 0, "old frame rejected before capture");
        var resourcesLease = service.capture(owner, "contract-view");
        service.invalidateResources();
        require(service.transform(resourcesLease, adapter) == null, "resource reload rejects old batches");
        var ownerLease = service.capture(owner, "contract-view");
        owner.retire();
        require(service.transform(ownerLease, adapter) == null, "model retirement rejects old batches");
        AccelerationOwner replacement = new AccelerationOwner();
        require(replacement.generation() != owner.generation(), "replacement has a distinct lifetime");
        var replacementLease = service.capture(replacement, "contract-view");
        require(service.transform(replacementLease, token -> {
            service.invalidateResources();
            return new DrawBatch(token, mesh, pose);
        }) == null, "invalidation during capture prevents publication");

        boolean rejected = false;
        try {
            new MeshHandle(new double[MeshHandle.STRIDE * 3], 4);
        } catch (IllegalArgumentException expected) {
            rejected = true;
        }
        require(rejected, "incomplete authored face rejected");
        MeshHandle quad = new MeshHandle(new double[4 * MeshHandle.STRIDE], 4);
        require(Arrays.equals(quad.copyTriangleIndices(), new int[]{0, 1, 2, 0, 2, 3}),
                "quad boundaries preserve triangle winding");
        System.out.println("Acceleration contract passed: immutable data, numeric conventions, frame/resource/model retirement.");
    }

    private static void testSkinAndUv(AccelerationService service, AccelerationService.Lease lease, MeshHandle mesh) {
        int[] joints = {0, 1, 2, 3, 4};
        float[] weights = {1, 1, 1, 1, 1};
        MeshHandle.Influences influences = new MeshHandle.Influences(joints, weights);
        MeshHandle weighted = new MeshHandle(mesh.copyVertices(), 1, List.of(influences));
        Arrays.fill(joints, 99);
        Arrays.fill(weights, 0);
        FiguraMat4[] deltas = new FiguraMat4[5];
        for (int index = 0; index < deltas.length; index++) {
            deltas[index] = FiguraMat4.of().translate(index * 2, 0, 0);
        }
        FiguraMat3 uv = FiguraMat3.of();
        uv.v11 = 2;
        uv.v23 = 0.125;
        PoseSnapshot pose = new PoseSnapshot(FiguraMat4.of(), FiguraMat3.of(), uv, deltas);
        deltas[4].resetIdentity();
        uv.resetIdentity();
        double[] actual = service.transform(lease, token -> new DrawBatch(token, weighted, pose));
        assertValues(actual, new double[]{5, 2, 3, 1, 0, 1, 0, 0.5, 0.625}, 1e-6,
                "all five influences, normalized weights and immutable UV/bone matrices");
        require(weighted.influences(0).size() == 5 && pose.skinJointCount() == 5,
                "public batch never truncates skin weights");
    }

    private static void assertValues(double[] actual, double[] expected, double tolerance, String message) {
        require(actual != null && actual.length == expected.length, message + " shape");
        for (int index = 0; index < expected.length; index++) {
            require(Double.isFinite(actual[index]) && Math.abs(actual[index] - expected[index]) <= tolerance,
                    message + " component " + index + ": " + actual[index]);
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
