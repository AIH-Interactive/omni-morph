package top.aihmc.omnimorph.render.acceleration;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import top.aihmc.omnimorph.model.ysm.YsmMaterialNativeSmokeTest;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.nio.ByteBuffer;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** CPU wire-layout decoder through both production emitters; this test makes no GPU execution claim. */
public final class DualEngineGpuReplay {
    public static void main(String[] args) throws Exception {
        RenderSystem.initRenderThread();
        var service = AccelerationService.INSTANCE;
        var decoder = new DecodingBackend();
        boolean driver = java.util.Arrays.asList(args).contains("--gl");
        top.aihmc.omnimorph.test.NativeGlWindow window = null;
        OpenGlAccelerationBackend gpu = null;
        int program = 0;
        if (driver) {
            window = top.aihmc.omnimorph.test.NativeGlWindow.open();
            gpu = new OpenGlAccelerationBackend();
            program = GpuDriverProbe.program(GpuDriverProbe.VERTEX, GpuDriverProbe.FRAGMENT);
            org.lwjgl.opengl.GL20.glUseProgram(program);
            org.lwjgl.opengl.GL20.glUniform1f(org.lwjgl.opengl.GL20.glGetUniformLocation(program, "ProbeScale"), .05f);
            decoder.gpu = gpu;
            decoder.program = program;
        }
        service.useBackend(decoder);
        try {
            FiguraAccelerationReplay.replay(sink -> material -> sink);
            require(decoder.vertices == 16,
                    "Figura rigid, edited, restored and weighted draws reach the CPU layout decoder");
            for (String argument : args) {
                if (argument.equals("--gl")) {
                    continue;
                }
                YsmMaterialNativeSmokeTest.replayAcceleration(Path.of(argument), runtime -> {
                    service.useBackend(null);
                    var expected = new ArrayList<float[]>();
                    runtime.renderer().renderWithAttachments(new PoseStack(), material -> recorder(expected),
                            0x00f000a0, runtime.captureRenderPose(false));
                    service.useBackend(decoder);
                    var actual = new ArrayList<float[]>();
                    long before = decoder.vertices;
                    runtime.renderer().renderWithAttachments(new PoseStack(), material -> recorder(actual),
                            0x00f000a0, runtime.captureRenderPose(false));
                    require(decoder.vertices > before, "YSM model reaches the CPU layout decoder");
                    compare(expected, actual);
                    require(!service.gpuDrawEnabled(), "CPU decoder replay never enables GPU drawing");
                    System.out.println("YSM production CPU layout replay vertices=" + actual.size() + " PASS");
                });
            }
        } finally {
            service.useBackend(null);
            if (gpu != null) {
                require(gpu.draws() > 4, "both production emitters submit real GPU indexed draws");
                require(org.lwjgl.opengl.GL11.glGetError() == org.lwjgl.opengl.GL11.GL_NO_ERROR,
                        "production model driver replay has no GL errors");
                System.out.println("PASS actual GL draws from both production emitters=" + gpu.draws());
                gpu.close();
                org.lwjgl.opengl.GL20.glUseProgram(0);
                org.lwjgl.opengl.GL20.glDeleteProgram(program);
            }
            if (window != null) {
                window.close();
            }
        }
        System.out.println("PASS dual-engine CPU wire-layout numerical replay; actual GPU execution is a separate test");
    }

    private static VertexConsumer recorder(List<float[]> vertices) {
        return (VertexConsumer) Proxy.newProxyInstance(VertexConsumer.class.getClassLoader(),
                new Class<?>[]{VertexConsumer.class}, (proxy, method, arguments) -> {
                    if (method.isDefault()) {
                        return InvocationHandler.invokeDefault(proxy, method, arguments);
                    }
                    if (method.getName().equals("vertex")) {
                        vertices.add(new float[8]);
                        for (int component = 0; component < 3; component++) {
                            com.google.common.collect.Iterables.getLast(vertices)[component] = ((Number) arguments[component]).floatValue();
                        }
                    } else if (method.getName().equals("normal") || method.getName().equals("uv")) {
                        int offset = method.getName().equals("normal") ? 3 : 6;
                        for (int component = 0; component < arguments.length; component++) {
                            com.google.common.collect.Iterables.getLast(vertices)[offset + component] = ((Number) arguments[component]).floatValue();
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

    private static final class DecodingBackend implements AccelerationBackend {
        private OpenGlAccelerationBackend gpu;
        private int program;
        private VertexConsumer sink;
        private long vertices;
        private float lastW;

        @Override
        public boolean supports(Operation operation, DrawBatch batch) {
            return operation == Operation.VERTEX_TRANSFORM && GpuBatchData.supports(batch);
        }

        @Override
        public double[] transform(DrawBatch batch) {
            if (gpu != null) {
                require(gpu.drawActive(batch, program), "production engine batch accepted by real GL backend");
            }
            ByteBuffer geometry = GpuBatchData.vertices(batch.mesh(), batch.pose().skinJointCount(),
                    batch.pose().usesFloatPrecision());
            ByteBuffer matrices = GpuBatchData.pose(batch);
            var decoded = new ArrayList<float[]>();
            double[] result = new double[batch.mesh().vertexCount() * MeshHandle.STRIDE];
            sink = recorder(decoded);
            try {
                for (int vertex = 0; vertex < batch.mesh().vertexCount(); vertex++) {
                    emit(geometry, matrices, vertex, 0, false);
                    result[vertex * MeshHandle.STRIDE + 3] = lastW;
                }
            } finally {
                sink = null;
            }
            double[] expected = new JavaAccelerationBackend().transform(batch);
            for (int vertex = 0; vertex < decoded.size(); vertex++) {
                int offset = vertex * MeshHandle.STRIDE;
                float[] value = decoded.get(vertex);
                for (int component = 0; component < 3; component++) {
                    result[offset + component] = value[component];
                    result[offset + 4 + component] = value[3 + component];
                }
                result[offset + 7] = value[6];
                result[offset + 8] = value[7];
            }
            for (int component = 0; component < result.length; component++) {
                require(Math.abs(result[component] - expected[component]) <= 1e-4 + 1e-5 * Math.abs(expected[component]),
                        "CPU layout decoder differs from Java at component " + component);
            }
            return result;
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
            lastW = position[3];
            sink.vertex(position[0], position[1], position[2]).normal(normal[0], normal[1], normal[2]).uv(uv[0], uv[1]).endVertex();
            vertices++;
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
