package top.aihmc.omnimorph.render.acceleration;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import top.aihmc.omnimorph.avatar.ysm.YsmAvatarDetector;
import org.figuramc.figura.avatar.Avatar;
import org.figuramc.figura.math.matrix.FiguraMat3;
import org.figuramc.figura.math.matrix.FiguraMat4;
import org.figuramc.figura.model.rendering.EntityRenderMode;
import org.figuramc.figura.model.rendering.PartFilterScheme;
import top.aihmc.omnimorph.model.ysm.YsmMaterialNativeSmokeTest;
import top.aihmc.omnimorph.model.ysm.YsmModelRuntime;
import top.aihmc.omnimorph.model.ysm.animation.YsmRenderFrameClock;

import java.net.URLClassLoader;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.function.Consumer;

/** Diagnoses CPU preparation only. Fake draw commands cannot establish GPU or total-frame performance. */
public final class AccelerationPreparationBenchmark {
    private static final AccelerationService SERVICE = AccelerationService.INSTANCE;
    private static final List<String> CSV = new ArrayList<>();
    private static final boolean QUICK = Boolean.getBoolean("omnimorph.preparation.quick");

    public static void main(String[] args) throws Exception {
        RenderSystem.initRenderThread();
        try (var resources = new URLClassLoader(new java.net.URL[]{Path.of(args[0]).toUri().toURL()}, null)) {
            NativeLibraryLoader.load(resources, Files.createTempDirectory("omnimorph-preparation-").toRealPath());
        }
        CSV.add("scope,sample,round,mode,frame,cpu_ns,vertices,gpu_draws_cumulative,geometry_uploads_cumulative");
        Path corpus = Path.of(args[1]);
        List<Path> models;
        try (var paths = Files.list(corpus)) {
            models = paths.sorted().filter(YsmAvatarDetector::isYsmAvatar).toList();
        }
        // Same deterministic corpus ordering as the full renderer/JNI replay.
        Path output = Path.of(args[2]);
        try {
            if (args.length > 3) {
                for (int argument = 3; argument < args.length; argument++) {
                    measureExplicit(Path.of(args[argument]).toRealPath());
                }
            } else {
                for (int index : QUICK ? new int[]{35} : new int[]{0, 35}) {
                    String sample = index == 0 ? "ordinary" : "heavy";
                    YsmMaterialNativeSmokeTest.replayAcceleration(models.get(index),
                            runtime -> measure(source -> renderYsm(runtime, source), sample));
                }
            }
        } finally {
            Files.createDirectories(output.getParent());
            Files.write(output, CSV);
            Files.write(output.resolveSibling("h7-preparation-" + System.currentTimeMillis() + ".csv"), CSV);
        }
        System.out.println("CPU preparation raw samples saved; actual GPU/whole-frame acceptance is still pending.");
    }

    private static void measureExplicit(Path selected) throws Exception {
        String sample = selected.getFileName().toString();
        if (YsmAvatarDetector.isYsmAvatar(selected)) {
            System.out.println("Explicit YSM CPU preparation sample: " + selected);
            YsmMaterialNativeSmokeTest.replayAcceleration(selected,
                    runtime -> measure(source -> renderYsm(runtime, source), sample));
            return;
        }
        System.out.println("Explicit Figura CPU preparation sample: " + selected);
        YsmMaterialNativeSmokeTest.replayFiguraAcceleration(selected, avatar -> {
            avatar.renderMode = EntityRenderMode.FIGURA_GUI;
            if (avatar.scriptError) {
                // Some scripts need a client world during init; the authored geometry still renders without them.
                System.out.println("Figura sample script init unavailable without a client world: " + scriptError(avatar));
                measure(source -> renderFigura(avatar, source), sample + " static-no-script");
                return;
            }
            measure(source -> renderFigura(avatar, source), sample);
            // Lua edits every frame: part transforms plus a UV change on actual authored geometry.
            var edit = avatar.loadScript("preparation dynamic edit", """
                    local frame = 0
                    return function()
                        frame = frame + 1
                        for _, part in ipairs(models:getChildren()) do
                            for _, child in ipairs(part:getChildren()) do
                                child:setPos(0, math.sin(frame * 0.1), 0)
                                child:setUV(frame % 16 / 256, 0)
                            end
                        end
                    end
                    """).call().checkfunction();
            measure(source -> {
                avatar.render.reset(Integer.MAX_VALUE);
                avatar.run(edit, avatar.render);
                renderFigura(avatar, source);
            }, sample + " lua-dynamic");
        });
    }

    private static void renderYsm(YsmModelRuntime runtime, MultiBufferSource source) {
        runtime.renderer().renderWithAttachments(new PoseStack(), source, 0x00f000a0, runtime.captureRenderPose(false));
    }

    private static void renderFigura(Avatar avatar, MultiBufferSource source) {
        boolean failedBefore = avatar.scriptError;
        avatar.render(null, 0, .5f, 1, new PoseStack(), source, 0x00f000a0, OverlayTexture.NO_OVERLAY,
                null, PartFilterScheme.MODEL, false, false);
        if (avatar.scriptError && !failedBefore) {
            throw new AssertionError("Figura preparation sample raised a script error: " + scriptError(avatar));
        }
    }

    private static String scriptError(Avatar avatar) {
        return avatar.errorText == null ? "unknown" : avatar.errorText.getString();
    }

    private static void measure(Consumer<MultiBufferSource> render, String sample) {
        try (ByteBufferBuilder memory = new ByteBufferBuilder(1 << 20)) {
            var recipes = new LinkedHashMap<RenderType, DiscardingMaterial>();
            var nativeSource = MultiBufferSource.immediate(memory);
            MultiBufferSource javaSource = original -> nativeSource.getBuffer(
                    recipes.computeIfAbsent(original, DiscardingMaterial::new));
            SERVICE.useBackend(null);
            frame(render, javaSource, nativeSource);
            long expectedVertices = recipes.values().stream().mapToLong(value -> value.vertices).sum();
            if (expectedVertices == 0) {
                throw new AssertionError("Benchmark model must produce actual geometry");
            }
            for (int round = 0; round < (QUICK ? 1 : 3); round++) {
                // Rotate order to expose warmup/order sensitivity rather than selecting a favorable run.
                String[] order = QUICK ? new String[]{"GPU_PREPARATION"} : new String[]{"JAVA", "SIMD", "GPU_PREPARATION"};
                for (int step = 0; step < order.length; step++) {
                    String mode = order[(round + step) % order.length];
                    var device = new CountingDevice();
                    device.complete = true;
                    var retirement = new GpuRetirement();
                    var gpu = new GpuAccelerationBackend(device, retirement, 64L << 20, 8L << 20);
                    if (mode.equals("GPU_PREPARATION")) {
                        while (!gpu.initializeStep()) {
                            // Same bounded production activation sequence, using a command recorder.
                        }
                        SERVICE.useBackend(gpu);
                        var owner = new AccelerationOwner();
                        for (RenderType material : recipes.keySet()) {
                            gpu.supports(AccelerationBackend.Operation.GPU_DRAW, warmup(owner, material));
                        }
                        for (int index = 0; index <= recipes.size(); index++) {
                            SERVICE.beginFrame();
                        }
                        owner.retire();
                    } else {
                        SERVICE.useBackend(mode.equals("SIMD") ? new NativeSimdBackend() : null);
                    }
                    try {
                        MultiBufferSource source = mode.equals("GPU_PREPARATION") ? nativeSource : javaSource;
                        for (int warmup = 0; warmup < (QUICK ? 1 : 30); warmup++) {
                            frame(render, source, nativeSource);
                        }
                        long[] times = new long[QUICK ? 2 : 90];
                        for (int index = 0; index < times.length; index++) {
                            long before = System.nanoTime();
                            frame(render, source, nativeSource);
                            times[index] = System.nanoTime() - before;
                            CSV.add("cpu_preparation_only," + sample + ',' + round + ',' + mode + ',' + index
                                    + ',' + times[index] + ',' + expectedVertices + ',' + device.drawCalls + ',' + device.uploads);
                        }
                        Arrays.sort(times);
                        System.out.println("CPU preparation only sample=" + sample + " round=" + round + " mode=" + mode
                                + " vertices=" + expectedVertices + " p50/p95/p99 us=" + percentile(times, .50)
                                + '/' + percentile(times, .95) + '/' + percentile(times, .99)
                                + " geometryUploads=" + device.uploads + " poses=" + device.poses
                                + " poseBytes=" + device.poseBytes + " gpuDraws=" + device.drawCalls);
                        if (mode.equals("GPU_PREPARATION") && device.drawCalls == 0) {
                            throw new AssertionError("GPU preparation benchmark must actually route GPU draw commands");
                        }
                    } finally {
                        SERVICE.useBackend(null);
                        gpu.close();
                        retirement.poll();
                        if (device.liveBytes != 0) {
                            throw new AssertionError("Repeated benchmark modes must release every GPU resource record");
                        }
                    }
                }
            }
        }
    }

    private static void frame(Consumer<MultiBufferSource> render, MultiBufferSource source,
                              MultiBufferSource.BufferSource nativeSource) {
        YsmRenderFrameClock.beginFrame();
        GpuSubmission.beginFrame();
        SERVICE.beginFrame();
        render.accept(source);
        nativeSource.endBatch();
    }

    private static long percentile(long[] sorted, double fraction) {
        return sorted[(int) Math.ceil(sorted.length * fraction) - 1] / 1000;
    }

    private static DrawBatch warmup(AccelerationOwner owner, RenderType material) {
        double[] vertices = new double[36];
        for (int vertex = 0; vertex < 4; vertex++) {
            vertices[vertex * 9 + 3] = 1;
            vertices[vertex * 9 + 5] = 1;
        }
        var semantics = new DrawSemantics(material, ResourceLocation.parse("omni-morph:preparation"), DrawSemantics.Layer.PRIMARY,
                "world", 0, 1, 0, 0, 1, 1, 1, 1, 0, true);
        return new DrawBatch(SERVICE.capture(owner, "world"), new MeshHandle(vertices, 4),
                new PoseSnapshot(FiguraMat4.of(), FiguraMat3.of()), semantics);
    }

    private static final class DiscardingMaterial extends RenderType {
        long vertices;

        DiscardingMaterial(RenderType original) {
            super("omnimorph_cpu_preparation_measurement", original.state);
        }

        @Override
        public void draw(MeshData geometry) {
            vertices += geometry.drawState().vertexCount();
            geometry.close();
        }
    }

    private static final class CountingDevice extends GpuAccelerationSmokeTest.RigidDevice {
        long poses;
        long poseBytes;

        @Override
        public Pose uploadPose(ByteBuffer data) {
            poses++;
            poseBytes += data.remaining();
            return super.uploadPose(data);
        }

        @Override
        public void draw(Material material, Mesh mesh, Pose pose, GpuDrawContext context,
                         int firstIndex, int indexCount, Runnable issuingDraw) {
            issuingDraw.run();
            drawCalls++;
        }

        @Override
        public void drawWithIndices(Material material, Mesh mesh, Pose pose, Indices indices, GpuDrawContext context,
                                    int firstIndex, int indexCount, Runnable issuingDraw) {
            draw(material, mesh, pose, context, firstIndex, indexCount, issuingDraw);
        }

        @Override
        public void drawJava(RenderType material, MeshData geometry) {
            geometry.close();
        }
    }
}
