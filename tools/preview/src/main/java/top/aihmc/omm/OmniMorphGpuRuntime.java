package top.aihmc.omm;

import com.mojang.blaze3d.shaders.GpuDebugOptions;
import com.mojang.blaze3d.shaders.ShaderSource;
import com.mojang.blaze3d.systems.GpuDevice;
import com.mojang.blaze3d.systems.GpuDeviceBackend;
import com.mojang.blaze3d.systems.RenderSystem;
import java.util.function.BiFunction;
import org.lwjgl.opengl.GL;

import static org.lwjgl.glfw.GLFW.*;

/** Starts the original MOD GPU adapter on a standalone, visible graphics context. */
final class OmniMorphGpuRuntime {
    private OmniMorphGpuRuntime() {
    }

    static void check() throws Exception {
        run(null);
    }

    @FunctionalInterface
    interface WindowWork {
        void render(long window, GpuDevice device, Object adapter) throws Exception;
    }

    static void run(WindowWork work) throws Exception {
        if (!glfwInit()) {
            throw new IllegalStateException("GLFW initialization failed");
        }
        long window = 0;
        GpuDevice device = null;
        try {
            glfwDefaultWindowHints();
            glfwWindowHint(GLFW_CONTEXT_VERSION_MAJOR, 4);
            glfwWindowHint(GLFW_CONTEXT_VERSION_MINOR, 3);
            glfwWindowHint(GLFW_OPENGL_PROFILE, GLFW_OPENGL_CORE_PROFILE);
            window = glfwCreateWindow(960, 640, "OMP original GPU preview", 0, 0);
            if (window == 0) {
                throw new IllegalStateException("Cannot create GPU check window");
            }
            glfwMakeContextCurrent(window);
            GL.createCapabilities();
            RenderSystem.initRenderThread();
            var constructor = Class.forName("com.mojang.blaze3d.opengl.GlDevice")
                .getDeclaredConstructor(long.class, ShaderSource.class, GpuDebugOptions.class);
            constructor.setAccessible(true);
            ShaderSource sources = new MinecraftShaderSources();
            device = new GpuDevice((GpuDeviceBackend) constructor.newInstance(window, sources,
                new GpuDebugOptions(1, true, true)));
            RenderSystem.initRenderer(device);
            Class<?> adapterType = Class.forName("top.aihmc.omnimorph.render.acceleration.MinecraftGpuDeviceAdapter");
            var adapterConstructor = adapterType.getDeclaredConstructor(GpuDevice.class, BiFunction.class);
            adapterConstructor.setAccessible(true);
            BiFunction<net.minecraft.resources.Identifier, com.mojang.blaze3d.shaders.ShaderType, String> lookup = sources::get;
            Object adapter = adapterConstructor.newInstance(device, lookup);
            var check = adapterType.getDeclaredMethod("selfTest");
            check.setAccessible(true);
            check.invoke(adapter);
            var compile = adapterType.getDeclaredMethod("compile", com.mojang.blaze3d.pipeline.RenderPipeline.class);
            compile.setAccessible(true);
            for (String name : java.util.List.of("ENTITY_CUTOUT", "ENTITY_TRANSLUCENT")) {
                Object pipeline = Class.forName("net.minecraft.client.renderer.RenderPipelines").getField(name).get(null);
                if (compile.invoke(adapter, pipeline) == null) {
                    throw new IllegalStateException("Original GPU adapter rejected material " + name);
                }
                System.out.println("OMP ORIGINAL GPU MATERIAL PASS " + name);
            }
            System.out.println("OMP ORIGINAL GPU ACTIVATION PASS renderer=" + device.getRenderer());
            if (work != null) {
                work.render(window, device, adapter);
            } else {
                glfwSwapBuffers(window);
                glfwPollEvents();
            }
        } finally {
            if (device != null) {
                RenderSystem.getDynamicUniforms().close();
                RenderSystem.getSamplerCache().close();
                device.close();
            }
            if (window != 0) {
                glfwDestroyWindow(window);
            }
            glfwTerminate();
        }
    }
}
