package top.aihmc.omnimorph.render.acceleration;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.resources.ResourceLocation;
import org.figuramc.figura.math.matrix.FiguraMat3;
import org.figuramc.figura.math.matrix.FiguraMat4;
import top.aihmc.omnimorph.model.ysm.YsmMaterialNativeSmokeTest;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.nio.ByteBuffer;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

/** CPU decoder of the GPU wire layout, through both production emitters. This does not validate GPU execution. */
public final class DualEngineGpuReplay {
    public static void main(String[] args) throws Exception {
        RenderSystem.initRenderThread();
        var service = AccelerationService.INSTANCE;
        var device = new DecodingDevice();
        var retirement = new GpuRetirement();
        var gpu = new GpuAccelerationBackend(device, retirement, 64L << 20, 8L << 20);
        while (!gpu.initializeStep()) {
            // Match the bounded activation sequence, without creating a graphics context.
        }
        service.useBackend(gpu);
        try (ByteBufferBuilder memory = new ByteBufferBuilder(4096)) {
            FiguraAccelerationReplay.replay(sink -> {
                device.sink = sink;
                return MultiBufferSource.immediate(memory);
            });
            require(device.vertices == 16, "Figura rigid, edited, restored and weighted draws actually reach the GPU boundary");
            for (String argument : args) {
                YsmMaterialNativeSmokeTest.replayAcceleration(Path.of(argument), runtime -> {
                    var expected = new ArrayList<float[]>();
                    var materials = new LinkedHashSet<RenderType>();
                    VertexConsumer recorder = recorder(expected);
                    runtime.renderer().renderWithAttachments(new PoseStack(), material -> {
                        materials.add(material);
                        return recorder;
                    }, 0x00f000a0, runtime.captureRenderPose(false));
                    var warmOwner = new AccelerationOwner();
                    for (RenderType material : materials) {
                        gpu.supports(AccelerationBackend.Operation.GPU_DRAW, warmup(service, warmOwner, material));
                    }
                    for (int frame = 0; frame <= materials.size(); frame++) {
                        service.beginFrame();
                        GpuSubmission.beginFrame();
                    }
                    warmOwner.retire();
                    var actual = new ArrayList<float[]>();
                    device.sink = recorder(actual);
                    long before = device.vertices;
                    long beforeGroups = device.groupedDraws;
                    var source = MultiBufferSource.immediate(memory);
                    runtime.renderer().renderWithAttachments(new PoseStack(), source, 0x00f000a0,
                            runtime.captureRenderPose(false));
                    source.endBatch();
                    require(device.vertices > before, "YSM model must actually use GPU submission");
                    require(device.groupedDraws > beforeGroups, "real YSM replay must decode the grouped palette path");
                    compare(expected, actual);
                    System.out.println("YSM production GPU-layout replay vertices=" + actual.size()
                            + " groupedDraws=" + (device.groupedDraws - beforeGroups) + " PASS");
                });
            }
        } finally {
            service.useBackend(null);
            device.complete = true;
            retirement.poll();
            require(device.liveBytes == 0, "both renderer owners release GPU resources exactly once");
        }
        System.out.println("PASS dual-engine GPU wire-layout numerical replay; real GPU pixels MANUAL/PENDING");
    }

    private static DrawBatch warmup(AccelerationService service, AccelerationOwner owner, RenderType material) {
        double[] vertices = new double[36];
        for (int vertex = 0; vertex < 4; vertex++) {
            vertices[vertex * 9 + 3] = 1;
            vertices[vertex * 9 + 5] = 1;
        }
        var draw = new DrawSemantics(material, ResourceLocation.parse("omni-morph:replay"), DrawSemantics.Layer.PRIMARY,
                "world", 0, 1, 0, 0, 1, 1, 1, 1, 0, true);
        return new DrawBatch(service.capture(owner, "world"), new MeshHandle(vertices, 4),
                new PoseSnapshot(FiguraMat4.of(), FiguraMat3.of()), draw);
    }

    private static VertexConsumer recorder(List<float[]> vertices) {
        return (VertexConsumer) Proxy.newProxyInstance(VertexConsumer.class.getClassLoader(),
                new Class<?>[]{VertexConsumer.class}, (proxy, method, arguments) -> {
                    if (method.isDefault()) {
                        return InvocationHandler.invokeDefault(proxy, method, arguments);
                    }
                    if (method.getName().equals("addVertex")) {
                        vertices.add(new float[8]);
                        for (int component = 0; component < 3; component++) {
                            vertices.getLast()[component] = ((Number) arguments[component]).floatValue();
                        }
                    } else if (method.getName().equals("setNormal") || method.getName().equals("setUv")) {
                        int offset = method.getName().equals("setNormal") ? 3 : 6;
                        for (int component = 0; component < arguments.length; component++) {
                            vertices.getLast()[offset + component] = ((Number) arguments[component]).floatValue();
                        }
                    }
                    return method.getReturnType() == void.class ? null : proxy;
                });
    }

    private static void compare(List<float[]> expected, List<float[]> actual) {
        require(!expected.isEmpty() && expected.size() == actual.size(), "each authored vertex emitted once");
        // Transparent sorting may reorder faces. Bucket by position; retain duplicates and compare all channels.
        var buckets = new java.util.HashMap<String, List<float[]>>();
        for (float[] vertex : expected) {
            buckets.computeIfAbsent(key(vertex, 0, 0, 0), ignored -> new ArrayList<>()).add(vertex);
        }
        for (float[] vertex : actual) {
            boolean found = false;
            search:
            for (int x = -1; x <= 1; x++) {
                for (int y = -1; y <= 1; y++) {
                    for (int z = -1; z <= 1; z++) {
                        var candidates = buckets.get(key(vertex, x, y, z));
                        if (candidates == null) {
                            continue;
                        }
                        for (int index = 0; index < candidates.size(); index++) {
                            if (matches(candidates.get(index), vertex)) {
                                candidates.remove(index);
                                found = true;
                                break search;
                            }
                        }
                    }
                }
            }
            require(found, "GPU layout position/normal/UV differs from Java: " + java.util.Arrays.toString(vertex));
        }
    }

    private static String key(float[] vertex, int x, int y, int z) {
        return (Math.round(vertex[0] * 100) + x) + ":" + (Math.round(vertex[1] * 100) + y)
                + ":" + (Math.round(vertex[2] * 100) + z);
    }

    private static boolean matches(float[] expected, float[] actual) {
        for (int component = 0; component < expected.length; component++) {
            if (Math.abs(expected[component] - actual[component]) > 1e-4 + 1e-5 * Math.abs(expected[component])) {
                return false;
            }
        }
        return true;
    }

    private static final class DecodingDevice extends GpuAccelerationSmokeTest.RigidDevice {
        VertexConsumer sink;
        long vertices;
        long groupedDraws;

        @Override
        public void draw(Material material, Mesh mesh, Pose pose, GpuDrawContext context,
                         int firstIndex, int indexCount, Runnable issuingDraw) {
            issuingDraw.run();
            ByteBuffer geometry = ((GpuAccelerationSmokeTest.FakeResource) mesh).data;
            ByteBuffer matrices = ((GpuAccelerationSmokeTest.FakeResource) pose).data;
            for (int vertex = firstIndex / 6 * 4; vertex < (firstIndex + indexCount) / 6 * 4; vertex++) {
                emit(geometry, matrices, vertex, 0, false);
            }
        }

        @Override
        public void drawWithIndices(Material material, Mesh mesh, Pose pose, Indices indices, GpuDrawContext context,
                                    int firstIndex, int indexCount, Runnable issuingDraw) {
            issuingDraw.run();
            groupedDraws++;
            ByteBuffer geometry = ((GpuAccelerationSmokeTest.FakeResource) mesh).data;
            ByteBuffer matrices = ((GpuAccelerationSmokeTest.FakeResource) pose).data;
            ByteBuffer order = ((GpuAccelerationSmokeTest.FakeResource) indices).data;
            for (int face = firstIndex / 6; face < (firstIndex + indexCount) / 6; face++) {
                int first = order.getInt(face * 6 * Integer.BYTES);
                int batch = geometry.getInt(first * GpuBatchData.VERTEX_BYTES + 48);
                int packed = matrices.getInt(GpuRigidData.REMAP_OFFSET + batch / 4 * Integer.BYTES);
                int matrix = (packed >>> ((batch % 4) * 8) & 0xff) * GpuBatchData.PART_BYTES;
                for (int corner = 0; corner < 4; corner++) {
                    emit(geometry, matrices, first + corner, matrix, true);
                }
            }
        }

        private void emit(ByteBuffer geometry, ByteBuffer matrices, int vertex, int matrix, boolean grouped) {
            int offset = vertex * GpuBatchData.VERTEX_BYTES;
            float[] position = {geometry.getFloat(offset), geometry.getFloat(offset + 4), geometry.getFloat(offset + 8), 1};
            if (!grouped && matrices.getFloat(GpuBatchData.PARAMETER_OFFSET + 40) != 0) {
                float[] blended = new float[3];
                float total = 0;
                for (int influence = 0; influence < 4; influence++) {
                    float weight = geometry.getFloat(offset + 32 + influence * 4);
                    if (weight > 0) {
                        float[] joint = multiply(matrices, geometry.getInt(offset + 48 + influence * 4) * 160, position, 4);
                        for (int component = 0; component < 3; component++) {
                            blended[component] += joint[component] * weight;
                        }
                        total += weight;
                    }
                }
                if (total > 0) {
                    for (int component = 0; component < 3; component++) {
                        position[component] = blended[component] / total;
                    }
                }
            }
            position = multiply(matrices, matrix, position, 4);
            boolean floatPose = matrices.getFloat(grouped ? matrix + 92 : GpuBatchData.PARAMETER_OFFSET + 44) != 0;
            if (floatPose) {
                position[3] = 1;
            }
            float vertexOffset = matrices.getFloat(grouped ? matrix + 108 : GpuBatchData.PARAMETER_OFFSET + 32);
            if (vertexOffset != 0) {
                float length = (float) Math.sqrt(position[0] * position[0] + position[1] * position[1]
                        + position[2] * position[2] + position[3] * position[3]);
                for (int component = 0; component < 4; component++) {
                    position[component] += position[component] / length * vertexOffset;
                }
            }
            float[] normal = multiply(matrices, matrix + 64,
                    new float[]{geometry.getFloat(offset + 12), geometry.getFloat(offset + 16), geometry.getFloat(offset + 20)}, 3);
            if (matrices.getFloat(grouped ? matrix + 76 : GpuBatchData.PARAMETER_OFFSET + 36) == 1) {
                float length = (float) Math.sqrt(normal[0] * normal[0] + normal[1] * normal[1] + normal[2] * normal[2]);
                for (int component = 0; component < 3; component++) {
                    normal[component] /= length;
                }
            }
            float[] uv = multiply(matrices, matrix + 112, new float[]{geometry.getFloat(offset + 24), geometry.getFloat(offset + 28), 1}, 3);
            sink.addVertex(position[0], position[1], position[2]).setNormal(normal[0], normal[1], normal[2]).setUv(uv[0], uv[1]);
            vertices++;
        }

        @Override
        public void drawJava(RenderType material, MeshData geometry) {
            geometry.close();
            throw new AssertionError("GPU-layout replay unexpectedly used Java material fallback");
        }

        private static float[] multiply(ByteBuffer data, int offset, float[] vector, int size) {
            float[] result = new float[size];
            for (int row = 0; row < size; row++) {
                for (int column = 0; column < size; column++) {
                    result[row] += data.getFloat(offset + column * 16 + row * 4) * vector[column];
                }
            }
            return result;
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
