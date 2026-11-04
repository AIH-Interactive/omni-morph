package top.aihmc.omnimorph.render.acceleration;

import com.mojang.renderpearl.api.buffers.GpuBuffer;
import com.mojang.renderpearl.api.GpuFormat;
import com.mojang.renderpearl.api.pipeline.IndexType;
import com.mojang.renderpearl.api.pipeline.PrimitiveTopology;
import com.mojang.renderpearl.api.pipeline.BindGroupLayout;
import com.mojang.renderpearl.backend.opengl.GlBackend;
import com.mojang.renderpearl.api.pipeline.ColorTargetState;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import com.mojang.renderpearl.api.device.GpuDebugOptions;
import com.mojang.renderpearl.api.pipeline.ShaderType;
import com.mojang.renderpearl.api.pipeline.UniformType;
import com.mojang.renderpearl.api.device.GpuDevice;
import com.mojang.renderpearl.api.device.GpuSurface;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.textures.GpuTexture;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.renderpearl.api.vertex.VertexFormat;
import net.minecraft.resources.Identifier;
import org.joml.Matrix4f;
import org.lwjgl.sdl.SDLInit;
import org.lwjgl.sdl.SDLVideo;
import org.lwjgl.sdl.SDLEvents;
import com.mojang.renderpearl.api.pipeline.ShaderSource;

import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import org.joml.Vector4f;

/** H0 experiment only. Draws through Minecraft's device in a visible desktop window. */
public final class AccelerationGpuRouteProbe {
    private static final int SIZE = 384;
    private static final String VERTEX_SHADER = """
            #version 330 core
            layout(location = 0) in vec3 Position;
            layout(std140) uniform Pose { mat4 Transform; };
            void main() {
                gl_Position = Transform * vec4(Position, 1.0);
            }
            """;
    private static final String FRAGMENT_SHADER = """
            #version 330 core
            layout(location = 0) out vec4 fragColor;
            void main() {
                fragColor = vec4(0.125, 0.875, 0.25, 1.0);
            }
            """;

    private AccelerationGpuRouteProbe() {
    }

    public static void main(String[] args) throws Exception {
        Path report = Path.of(args[0]);
        RenderSystem.initRenderThread();
        if (!SDLInit.SDL_Init(SDLInit.SDL_INIT_VIDEO)) {
            throw new IllegalStateException("SDL video initialization failed");
        }
        GlBackend backend = new GlBackend();
        try {
            backend.loadLibrary();
            long window = backend.createWindow("OmniMorph H0 GPU route probe", SIZE, SIZE, 0);
            if (window == 0) {
                throw new IllegalStateException("Could not create a real desktop window");
            }
            try {
                SDLVideo.SDL_ShowWindow(window);
                GpuDevice device = backend.createDevice(new GpuDebugOptions(1, true, true, true));
                try (var surface = device.createSurface(window, () -> false)) {
                    surface.configure(new GpuSurface.Configuration(SIZE, SIZE,
                            GpuSurface.PresentMode.getSupportedVsyncMode(surface.supportedPresentModes(), true)));
                    RenderSystem.initRenderer(device);
                    run(device, surface, window, report);
                } finally {
                    device.close();
                }
            } finally {
                SDLVideo.SDL_DestroyWindow(window);
            }
        } finally {
            backend.unloadLibrary();
            SDLInit.SDL_Quit();
        }
    }

    private static void run(GpuDevice device, GpuSurface surface, long window, Path report) throws Exception {
        RenderPipeline pipeline = RenderPipeline.builder()
                .withLocation(Identifier.parse("omni-morph:h0_probe"))
                .withVertexShader(Identifier.parse("omni-morph:h0_probe"))
                .withFragmentShader(Identifier.parse("omni-morph:h0_probe"))
                .withBindGroupLayout(BindGroupLayout.builder().withUniform("Pose", UniformType.UNIFORM_BUFFER).build())
                .withColorTargetState(ColorTargetState.DEFAULT)
                .withDepthStencilState(Optional.empty())
                .withCull(false)
                .withVertexBinding(0, DefaultVertexFormat.POSITION)
                .withPrimitiveTopology(PrimitiveTopology.TRIANGLES)
                .build();
        ShaderSource sources = new ShaderSource() {
            @Override
            public String getShader(Identifier id, ShaderType type) {
                return type == ShaderType.VERTEX ? VERTEX_SHADER : FRAGMENT_SHADER;
            }

            @Override public CachedIncludeSource getInclude(Identifier id) { return null; }
            @Override public void close() { }
        };
        var program = device.compilePipeline(pipeline, sources, net.minecraft.util.Util.backgroundExecutor())
                .join().finishCompile();
        if (program == null) {
            throw new AssertionError("Minecraft rejected H0 matrix pipeline");
        }
        ByteBuffer vertices = MemoryUtil.memAlloc(9 * Float.BYTES);
        ByteBuffer indices = MemoryUtil.memAlloc(3 * Integer.BYTES);
        ByteBuffer pose = MemoryUtil.memAlloc(16 * Float.BYTES);
        try (program; sources) {
            vertices.asFloatBuffer().put(new float[]{-0.5f, -0.5f, 0, 0.5f, -0.5f, 0, 0, 0.5f, 0});
            indices.asIntBuffer().put(new int[]{0, 1, 2});
            new Matrix4f().translation(0.25f, 0, 0).get(pose);
            try (var vertexBuffer = device.createBuffer(() -> "H0 geometry", GpuBuffer.USAGE_VERTEX, vertices);
                 var indexBuffer = device.createBuffer(() -> "H0 indices", GpuBuffer.USAGE_INDEX, indices);
                 var poseBuffer = device.createBuffer(() -> "H0 pose", GpuBuffer.USAGE_UNIFORM, pose);
                 var target = device.createTexture("H0 target",
                         GpuTexture.USAGE_RENDER_ATTACHMENT | GpuTexture.USAGE_COPY_SRC,
                         GpuFormat.RGBA8_UNORM, SIZE, SIZE, 1, 1);
                 var view = device.createTextureView(target)) {
                // Keep the actual window visible long enough for stable rendering and inspection.
                long deadline = System.nanoTime() + 5_000_000_000L;
                int draws = 0;
                while (System.nanoTime() < deadline) {
                    surface.acquireNextTexture();
                    var encoder = device.createCommandEncoder();
                    try (var pass = encoder.createRenderPass(() -> "H0 transformed triangle", view,
                            Optional.of(new Vector4f(16 / 255f, 16 / 255f, 32 / 255f, 1)))) {
                        pass.setPipeline(program);
                        pass.setUniform("Pose", poseBuffer);
                        pass.setVertexBuffer(0, vertexBuffer.slice());
                        pass.setIndexBuffer(indexBuffer, IndexType.INT);
                        pass.drawIndexed(3, 1, 0, 0, 0);
                    }
                    surface.blitFromTexture(encoder, view);
                    encoder.submit();
                    surface.present();
                    SDLEvents.SDL_PumpEvents();
                    draws++;
                }
                if (draws == 0) {
                    throw new AssertionError("Window closed before an indexed draw was submitted");
                }
                verifyTransformedPixels(device, target);
                Files.createDirectories(report.toAbsolutePath().getParent());
                Files.writeString(report, "backend=" + device.getDeviceInfo().backendName()
                        + "\nrenderer=" + device.getDeviceInfo().name()
                        + "\nversion=" + device.getDeviceInfo().driverInfo()
                        + "\nuniformAlignment=" + device.getDeviceInfo().limits().minUniformOffsetAlignment()
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
                encoder.submit();
                if (!fence.awaitCompletion(5_000_000_000L)) {
                    throw new AssertionError("GPU readback fence timed out");
                }
            }
            RenderSystem.executePendingTasks();
            try (var mapped = pixels.map(true, false)) {
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
