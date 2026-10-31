package top.aihmc.omnimorph.render.acceleration;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.opengl.GlBackend;
import com.mojang.blaze3d.pipeline.ColorTargetState;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.shaders.GpuDebugOptions;
import com.mojang.blaze3d.shaders.ShaderType;
import com.mojang.blaze3d.shaders.UniformType;
import com.mojang.blaze3d.systems.GpuDevice;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.GpuTexture;
import com.mojang.blaze3d.textures.TextureFormat;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.resources.ResourceLocation;
import org.joml.Matrix4f;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.glfw.GLFWErrorCallback;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.OptionalInt;

/** H0 experiment only. Draws through Minecraft's device in a visible desktop window. */
public final class AccelerationGpuRouteProbe {
    private static final int SIZE = 384;
    private static final String VERTEX_SHADER = """
            #version 330 core
            in vec3 Position;
            layout(std140) uniform Pose { mat4 Transform; };
            void main() {
                gl_Position = Transform * vec4(Position, 1.0);
            }
            """;
    private static final String FRAGMENT_SHADER = """
            #version 330 core
            out vec4 fragColor;
            void main() {
                fragColor = vec4(0.125, 0.875, 0.25, 1.0);
            }
            """;

    private AccelerationGpuRouteProbe() {
    }

    public static void main(String[] args) throws Exception {
        Path report = Path.of(args[0]);
        RenderSystem.initRenderThread();
        try (GLFWErrorCallback errors = GLFWErrorCallback.createPrint(System.err).set()) {
            if (!GLFW.glfwInit()) {
                throw new IllegalStateException("GLFW initialization failed; real-window validation is pending");
            }
            GlBackend backend = new GlBackend();
            backend.setWindowHints();
            long window = GLFW.glfwCreateWindow(SIZE, SIZE, "OmniMorph H0 GPU route probe", 0, 0);
            if (window == 0) {
                throw new IllegalStateException("Could not create a real desktop window");
            }
            try {
                GLFW.glfwShowWindow(window);
                GpuDevice device = backend.createDevice(window,
                        (id, type) -> type == ShaderType.VERTEX ? VERTEX_SHADER : FRAGMENT_SHADER,
                        new GpuDebugOptions(1, true, true));
                try {
                    RenderSystem.initRenderer(device);
                    run(device, window, report);
                } finally {
                    device.close();
                }
            } finally {
                GLFW.glfwDestroyWindow(window);
                GLFW.glfwTerminate();
                GLFW.glfwSetErrorCallback(null);
            }
        }
    }

    private static void run(GpuDevice device, long window, Path report) throws Exception {
        RenderPipeline pipeline = RenderPipeline.builder()
                .withLocation(ResourceLocation.parse("omni-morph:h0_probe"))
                .withVertexShader(ResourceLocation.parse("omni-morph:h0_probe"))
                .withFragmentShader(ResourceLocation.parse("omni-morph:h0_probe"))
                .withUniform("Pose", UniformType.UNIFORM_BUFFER)
                .withColorTargetState(ColorTargetState.DEFAULT)
                .withDepthStencilState(Optional.empty())
                .withCull(false)
                .withVertexFormat(DefaultVertexFormat.POSITION, VertexFormat.Mode.TRIANGLES)
                .build();
        if (!device.precompilePipeline(pipeline).isValid()) {
            throw new AssertionError("Minecraft rejected H0 matrix pipeline");
        }
        ByteBuffer vertices = MemoryUtil.memAlloc(9 * Float.BYTES);
        ByteBuffer indices = MemoryUtil.memAlloc(3 * Integer.BYTES);
        ByteBuffer pose = MemoryUtil.memAlloc(16 * Float.BYTES);
        try {
            vertices.asFloatBuffer().put(new float[]{-0.5f, -0.5f, 0, 0.5f, -0.5f, 0, 0, 0.5f, 0});
            indices.asIntBuffer().put(new int[]{0, 1, 2});
            new Matrix4f().translation(0.25f, 0, 0).get(pose);
            try (var vertexBuffer = device.createBuffer(() -> "H0 geometry", GpuBuffer.USAGE_VERTEX, vertices);
                 var indexBuffer = device.createBuffer(() -> "H0 indices", GpuBuffer.USAGE_INDEX, indices);
                 var poseBuffer = device.createBuffer(() -> "H0 pose", GpuBuffer.USAGE_UNIFORM, pose);
                 var target = device.createTexture("H0 target",
                         GpuTexture.USAGE_RENDER_ATTACHMENT | GpuTexture.USAGE_COPY_SRC,
                         TextureFormat.RGBA8, SIZE, SIZE, 1, 1);
                 var view = device.createTextureView(target)) {
                // Keep the actual window visible long enough for stable rendering and inspection.
                long deadline = System.nanoTime() + 5_000_000_000L;
                int draws = 0;
                while (System.nanoTime() < deadline && !GLFW.glfwWindowShouldClose(window)) {
                    var encoder = device.createCommandEncoder();
                    try (var pass = encoder.createRenderPass(() -> "H0 transformed triangle", view,
                            OptionalInt.of(0xff101020))) {
                        pass.setPipeline(pipeline);
                        pass.setUniform("Pose", poseBuffer);
                        pass.setVertexBuffer(0, vertexBuffer);
                        pass.setIndexBuffer(indexBuffer, VertexFormat.IndexType.INT);
                        pass.drawIndexed(0, 0, 3, 1);
                    }
                    encoder.presentTexture(view);
                    device.presentFrame();
                    GLFW.glfwPollEvents();
                    draws++;
                }
                if (draws == 0) {
                    throw new AssertionError("Window closed before an indexed draw was submitted");
                }
                verifyTransformedPixels(device, target);
                Files.createDirectories(report.toAbsolutePath().getParent());
                Files.writeString(report, "backend=" + device.getBackendName()
                        + "\nrenderer=" + device.getRenderer()
                        + "\nversion=" + device.getVersion()
                        + "\nuniformAlignment=" + device.getUniformOffsetAlignment()
                        + "\nindexedDraws=" + draws
                        + "\ntranslatedPixels=PASS (diagnostic readback only)"
                        + "\nroute=GpuDevice -> matrix UBO -> vertex shader -> RenderPass.drawIndexed -> presentTexture"
                        + "\nvisual=MANUAL/PENDING; this probe does not validate engine materials or game performance\n");
                System.out.println(Files.readString(report));
            }
        } finally {
            MemoryUtil.memFree(pose);
            MemoryUtil.memFree(indices);
            MemoryUtil.memFree(vertices);
        }
    }

    private static void verifyTransformedPixels(GpuDevice device, GpuTexture target) {
        var encoder = device.createCommandEncoder();
        try (var pixels = device.createBuffer(() -> "H0 diagnostic pixels",
                GpuBuffer.USAGE_COPY_DST | GpuBuffer.USAGE_MAP_READ, SIZE * SIZE * 4L)) {
            encoder.copyTextureToBuffer(target, pixels, 0, () -> { }, 0);
            try (var fence = encoder.createFence()) {
                if (!fence.awaitCompletion(5_000_000_000L)) {
                    throw new AssertionError("GPU readback fence timed out");
                }
            }
            RenderSystem.executePendingTasks();
            try (var mapped = encoder.mapBuffer(pixels, true, false)) {
                ByteBuffer data = mapped.data();
                // At y=0, the translated triangle spans x=[0, 0.5]. The original
                // untransformed geometry spans x=[-0.25, 0.25]. Check both regions.
                requireGreen(data, 264, 192, true);
                requireGreen(data, 168, 192, false);
            }
        }
    }

    private static void requireGreen(ByteBuffer pixels, int x, int y, boolean expected) {
        int offset = (y * SIZE + x) * 4;
        boolean green = Byte.toUnsignedInt(pixels.get(offset + 1)) > 180
                && Byte.toUnsignedInt(pixels.get(offset)) < 60;
        if (green != expected) {
            throw new AssertionError("Matrix shader pixel mismatch at " + x + ", " + y);
        }
    }
}
