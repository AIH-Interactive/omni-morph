package top.aihmc.omnimorph.render.acceleration;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexSorting;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import org.figuramc.figura.math.matrix.FiguraMat3;
import org.figuramc.figura.math.matrix.FiguraMat4;
import org.joml.Matrix4f;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.*;
import top.aihmc.omnimorph.test.NativeGlWindow;

import java.util.List;

/** Visible desktop GL context; production shader wrapper, cache, pose upload and indexed draws. */
public final class GpuOpenGlSmokeTest {
    public static void main(String[] args) throws Exception {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        RenderSystem.initRenderThread();
        RenderSystem.setProjectionMatrix(new Matrix4f(), VertexSorting.DISTANCE_TO_ORIGIN);
        try (var window = NativeGlWindow.open()) {
            require(OpenGlAccelerationBackend.available(), "real GL 3.2 context");
            GL11.glColorMask(false, true, false, true);
            GpuDriverProbe.verify();
            var mask = BufferUtils.createByteBuffer(4);
            GL11.glGetBooleanv(GL11.GL_COLOR_WRITEMASK, mask);
            require(mask.get(0) == 0 && mask.get(1) != 0 && mask.get(2) == 0 && mask.get(3) != 0,
                    "driver probe isolates and restores caller color write mask");
            GL11.glColorMask(true, true, true, true);
            pixelAndLifetimeTests();
            transparentPixels();
            attributePixels();
            compileVanillaMaterials();
            require(GL11.glGetError() == GL11.GL_NO_ERROR, "no GL errors after all production draws");
        }
        System.out.println("PASS real-window GL: driver self-test, YSM float pose, Figura rigid/weighted pose, "
                + "GPU pixels, active uniform transfer, cache reuse/reload, shader rejection and vanilla material compilation.");
        System.out.println("SKIPPED (MANUAL): full Minecraft client, shaderpack compatibility and performance A/B; unverified.");
    }

    private static void pixelAndLifetimeTests() {
        int program = GpuDriverProbe.program(GpuDriverProbe.VERTEX, GpuDriverProbe.FRAGMENT);
        GL20.glUseProgram(program);
        GL20.glUniform1f(GL20.glGetUniformLocation(program, "ProbeScale"), .5f);
        GL11.glViewport(0, 0, 128, 128);
        try (OpenGlAccelerationBackend backend = new OpenGlAccelerationBackend()) {
            var stack = new PoseStack();
            stack.translate(.5f, 0, 0);
            DrawBatch ysm = GpuDriverProbe.batch(new PoseSnapshot(stack.last()));
            drawAndCheck(backend, ysm, program, 80, 64, true);
            drawAndCheck(backend, ysm, program, 20, 64, false);
            require(backend.uploads() == 1, "YSM immutable mesh uploads once across pose draws");
            DrawBatch figura = GpuDriverProbe.batch(new PoseSnapshot(FiguraMat4.of().translate(-.5, 0, 0),
                    FiguraMat3.of()));
            drawAndCheck(backend, figura, program, 40, 64, true);
            drawAndCheck(backend, figura, program, 108, 64, false);
            var influence = new MeshHandle.Influences(new int[] {0, 1}, new float[] {.25f, .75f});
            var weighted = new MeshHandle(figura.mesh().copyVertices(), 4,
                    List.of(influence, influence, influence, influence));
            var pose = new PoseSnapshot(FiguraMat4.of(), FiguraMat3.of(), FiguraMat3.of(),
                    new FiguraMat4[] {FiguraMat4.of().translate(0, -.5, 0), FiguraMat4.of().translate(0, .5, 0)});
            var skinned = new DrawBatch(figura.lease(), weighted, pose, figura.semantics());
            drawAndCheck(backend, skinned, program, 64, 80, true);
            drawAndCheck(backend, skinned, program, 64, 22, false);
            long uploads = backend.uploads();
            backend.invalidateResources();
            require(backend.geometryBytes() == 0, "reload releases all GPU geometry");
            drawAndCheck(backend, skinned, program, 64, 80, true);
            require(backend.uploads() == uploads + 1, "reload recreates live mesh and shader");
            require(GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM) == program, "original active program restored");
            require(backend.draws() == 7, "every accepted draw submitted exactly once");
            for (int frame = 0; frame < 121; frame++) {
                backend.beginFrame();
            }
            require(backend.geometryBytes() == 0, "unused GPU geometry expires after bounded frames");
            var retired = GpuDriverProbe.batch(new PoseSnapshot(FiguraMat4.of(), FiguraMat3.of()));
            drawAndCheck(backend, retired, program, 64, 64, true);
            retired.lease().owner().retire();
            backend.beginFrame();
            require(backend.geometryBytes() == 0, "retired owner geometry is released at the next frame");
        } finally {
            GL20.glUseProgram(0);
            GL20.glDeleteProgram(program);
        }
    }

    private static void drawAndCheck(OpenGlAccelerationBackend backend, DrawBatch batch, int program,
                                     int x, int y, boolean red) {
        GL11.glClearColor(0, 0, 0, 1);
        GL11.glClear(GL11.GL_COLOR_BUFFER_BIT);
        require(backend.drawActive(batch, program), "production backend accepts pose");
        var pixel = BufferUtils.createByteBuffer(4);
        GL11.glReadPixels(x, y, 1, 1, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, pixel);
        require((pixel.get(0) & 255) == (red ? 255 : 0), "expected GPU pose pixel at " + x + "," + y);
    }

    private static void compileVanillaMaterials() throws Exception {
        for (String material : List.of("entity_solid", "entity_cutout", "entity_cutout_no_cull",
                "entity_cutout_no_cull_z_offset", "entity_translucent", "entity_translucent_cull")) {
            String base = "assets/minecraft/shaders/core/rendertype_" + material;
            String vertex = resource(base + ".vsh");
            String fragment = resource(base + ".fsh");
            var preprocessor = new com.mojang.blaze3d.preprocessor.GlslPreprocessor() {
                @Override
                public String applyImport(boolean quoted, String name) {
                    try {
                        return resource("assets/minecraft/shaders/include/" + name);
                    } catch (Exception failure) {
                        throw new IllegalStateException(failure);
                    }
                }
            };
            String expanded = String.join("", preprocessor.process(vertex));
            String expandedFragment = String.join("", preprocessor.process(fragment));
            int wrapped = GpuDriverProbe.program(GpuShaderProgram.wrapEntitySource(expanded), expandedFragment);
            GL20.glDeleteProgram(wrapped);
        }
        int unsupported = GpuDriverProbe.program(GpuDriverProbe.VERTEX.replace("in vec3 Normal;", ""),
                GpuDriverProbe.FRAGMENT);
        try (OpenGlAccelerationBackend backend = new OpenGlAccelerationBackend()) {
            require(!backend.drawActive(GpuDriverProbe.batch(new PoseSnapshot(new PoseStack().last())), unsupported),
                    "changed shader input contract rejects before any GPU draw");
            require(backend.draws() == 0, "rejected shader never draws");
        } finally {
            GL20.glDeleteProgram(unsupported);
        }
    }

    private static void transparentPixels() {
        int program = GpuDriverProbe.program(GpuDriverProbe.VERTEX, GpuDriverProbe.FRAGMENT);
        GL20.glUseProgram(program);
        GL20.glUniform1f(GL20.glGetUniformLocation(program, "ProbeScale"), .5f);
        var material = net.minecraft.client.renderer.RenderType.entityTranslucent(
                net.minecraft.resources.ResourceLocation.parse("omni-morph:gpu-test"));
        var base = GpuDriverProbe.batch(new PoseSnapshot(FiguraMat4.of(), FiguraMat3.of()));
        var far = new DrawBatch(base.lease(), base.mesh(),
                new PoseSnapshot(FiguraMat4.of().translate(0, 0, .7), FiguraMat3.of()),
                new DrawSemantics(material, null, DrawSemantics.Layer.PRIMARY, "world", 0, 1,
                        0, 0, 1, 0, 0, .5f, 0, true));
        var near = new DrawBatch(base.lease(), base.mesh(),
                new PoseSnapshot(FiguraMat4.of().translate(0, 0, .2), FiguraMat3.of()),
                new DrawSemantics(material, null, DrawSemantics.Layer.PRIMARY, "world", 0, 1,
                        0, 0, 0, 1, 0, .5f, 0, true));
        try (var backend = new OpenGlAccelerationBackend()) {
            require(backend.supports(AccelerationBackend.Operation.GPU_DRAW, near),
                    "default Figura translucent material is supported");
            var cutout = net.minecraft.client.renderer.RenderType.entityCutoutNoCull(
                    net.minecraft.resources.ResourceLocation.parse("omni-morph:gpu-test"));
            var opaque = new DrawBatch(base.lease(), base.mesh(), base.pose(),
                    new DrawSemantics(cutout, null, DrawSemantics.Layer.PRIMARY, "world", 0, 1,
                            0, 0, 1, 1, 1, 1, 0, true));
            require(backend.supports(AccelerationBackend.Operation.GPU_DRAW, opaque),
                    "YSM cutout material is supported");
            long[] order = backend.faceOrder(List.of(near, far));
            require(order[0] >>> 32 == 1 && order[1] >>> 32 == 0,
                    "global transparent sort spans differently posed parts");
            GL11.glClear(GL11.GL_COLOR_BUFFER_BIT);
            GL11.glEnable(GL11.GL_BLEND);
            GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
            require(backend.drawActive(List.of(near, far), program), "sorted transparent GPU group");
            var pixel = BufferUtils.createByteBuffer(4);
            GL11.glReadPixels(64, 64, 1, 1, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, pixel);
            require(Math.abs((pixel.get(0) & 255) - 64) <= 2 && Math.abs((pixel.get(1) & 255) - 127) <= 2,
                    "GPU blending shows red far face beneath green near face");
        } finally {
            GL11.glDisable(GL11.GL_BLEND);
            GL20.glUseProgram(0);
            GL20.glDeleteProgram(program);
        }
    }

    private static void attributePixels() {
        String source = GpuDriverProbe.VERTEX.replace("probeColor = Color;",
                "probeColor = vec4(UV0, Normal.z, 1.0);");
        int program = GpuDriverProbe.program(source, GpuDriverProbe.FRAGMENT);
        GL20.glUseProgram(program);
        GL20.glUniform1f(GL20.glGetUniformLocation(program, "ProbeScale"), .5f);
        var base = GpuDriverProbe.batch(new PoseSnapshot(FiguraMat4.of(), FiguraMat3.of()));
        double[] input = base.mesh().copyVertices();
        for (int vertex = 0; vertex < 4; vertex++) {
            input[vertex * MeshHandle.STRIDE + 7] = .5;
            input[vertex * MeshHandle.STRIDE + 8] = .75;
        }
        var pose = new PoseSnapshot(FiguraMat4.of(), FiguraMat3.of().scale(1, 1, .5),
                FiguraMat3.of().scale(.25, .5, 1), null);
        var batch = new DrawBatch(base.lease(), new MeshHandle(input, 4), pose, base.semantics());
        try (var backend = new OpenGlAccelerationBackend()) {
            require(backend.drawActive(batch, program), "GPU normal and UV matrices accepted");
            var pixel = BufferUtils.createByteBuffer(4);
            GL11.glReadPixels(64, 64, 1, 1, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, pixel);
            require(Math.abs((pixel.get(0) & 255) - 32) <= 1
                    && Math.abs((pixel.get(1) & 255) - 96) <= 1
                    && Math.abs((pixel.get(2) & 255) - 126) <= 1,
                    "GPU UV transform and signed-byte normal quantization match native vertex storage");
        } finally {
            GL20.glUseProgram(0);
            GL20.glDeleteProgram(program);
        }
    }

    private static String resource(String name) throws Exception {
        try (var input = GpuOpenGlSmokeTest.class.getClassLoader().getResourceAsStream(name)) {
            if (input == null) {
                throw new IllegalStateException("Missing Minecraft shader " + name);
            }
            return new String(input.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        }
    }

    private static void require(boolean value, String message) {
        if (!value) {
            throw new AssertionError(message);
        }
    }
}
