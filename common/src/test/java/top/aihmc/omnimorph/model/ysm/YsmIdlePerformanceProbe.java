package top.aihmc.omnimorph.model.ysm;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import jdk.jfr.Recording;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import top.aihmc.omnimorph.model.ysm.animation.YsmRenderFrameClock;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Arrays;

/** Real model/controller CPU replay; no GPU or in-game FPS conclusions can be drawn from this probe. */
public final class YsmIdlePerformanceProbe {
    public static void main(String[] args) throws Exception {
        String models = System.getenv("OMNIMORPH_IDLE_MODELS");
        if (models == null || models.isBlank()) {
            throw new IllegalArgumentException("Set OMNIMORPH_IDLE_MODELS to model paths separated by semicolons");
        }
        Path report = Path.of("build/reports/ysm-idle-performance.jfr");
        Files.createDirectories(report.getParent());
        try (var recording = new Recording()) {
            recording.enable("jdk.ExecutionSample").withPeriod(Duration.ofMillis(5));
            recording.enable("jdk.ObjectAllocationSample");
            recording.start();
            for (String model : models.split(";")) {
                Path path = Path.of(model);
                long before = System.nanoTime();
                YsmMaterialNativeSmokeTest.replayAcceleration(path, runtime -> {
                    System.out.printf("MODEL %s load_ms=%.2f bones=%d cubes=%d clips=%d controllers=%d%n",
                            path.getFileName(), (System.nanoTime() - before) / 1e6,
                            runtime.geometry().bones.size(), runtime.geometry().bones.values().stream()
                                    .mapToInt(bone -> bone.cubes.size()).sum(),
                            runtime.animations().getClips().values().stream().distinct().count(),
                            runtime.animations().getControllers().size());
                    runtime.animations().getClips().values().stream().distinct()
                            .sorted(java.util.Comparator.comparingInt(
                                    (top.aihmc.omnimorph.model.ysm.animation.YsmAnimationClip clip) -> clip.events.size()).reversed())
                            .limit(5).forEach(clip -> System.out.println("EVENTS " + clip.name + "=" + clip.events.size()));
                    recording.enable("jdk.JavaExceptionThrow").withStackTrace();
                    measure(runtime);
                    recording.disable("jdk.JavaExceptionThrow");
                });
            }
            recording.stop();
            recording.dump(report);
        }
    }

    private static void measure(YsmModelRuntime runtime) {
        int warmup = 30;
        int frames = 300;
        long[][] times = new long[3][frames];
        long[] allocated = new long[3];
        var threads = (com.sun.management.ThreadMXBean) java.lang.management.ManagementFactory.getThreadMXBean();
        long thread = Thread.currentThread().threadId();
        var state = new LivingEntityRenderState();
        var vertices = new DiscardingVertices();
        for (int frame = 0; frame < warmup + frames; frame++) {
            state.ageInTicks = frame / 3f;
            YsmRenderFrameClock.beginFrame();
            long startBytes = threads.getThreadAllocatedBytes(thread);
            long start = System.nanoTime();
            runtime.updateAnimations(state, null);
            long animated = System.nanoTime();
            long animatedBytes = threads.getThreadAllocatedBytes(thread);
            var pose = runtime.captureRenderPose(false);
            long captured = System.nanoTime();
            long capturedBytes = threads.getThreadAllocatedBytes(thread);
            runtime.renderer().renderWithAttachments(new PoseStack(), type -> vertices, 0x00f000a0, pose);
            long rendered = System.nanoTime();
            long renderedBytes = threads.getThreadAllocatedBytes(thread);
            if (frame >= warmup) {
                times[0][frame - warmup] = animated - start;
                times[1][frame - warmup] = captured - animated;
                times[2][frame - warmup] = rendered - captured;
                allocated[0] += animatedBytes - startBytes;
                allocated[1] += capturedBytes - animatedBytes;
                allocated[2] += renderedBytes - capturedBytes;
            }
        }
        String[] phases = {"animation", "pose", "cpu_vertex_submission"};
        for (int index = 0; index < times.length; index++) {
            Arrays.sort(times[index]);
            System.out.printf("CPU %s p50_ms=%.3f p95_ms=%.3f max_ms=%.3f alloc_kb_per_frame=%.1f%n", phases[index],
                    times[index][frames / 2] / 1e6, times[index][(int) (frames * .95)] / 1e6,
                    times[index][frames - 1] / 1e6, allocated[index] / 1024.0 / frames);
        }
        System.out.println("ACTIVE " + runtime.animations().getActiveAnimations().keySet());
        runtime.animations().getActiveAnimations().values().forEach(animation ->
                System.out.println("ACTIVE_EVENTS " + animation.clip.name + "=" + animation.clip.events.size()));
        System.out.println("VERTICES " + vertices.count + "; GPU and live-game FPS MANUAL/PENDING");
    }

    private static final class DiscardingVertices implements VertexConsumer {
        private long count;

        @Override
        public VertexConsumer addVertex(float x, float y, float z) {
            count++;
            return this;
        }

        @Override
        public VertexConsumer setColor(int red, int green, int blue, int alpha) {
            return this;
        }

        @Override
        public VertexConsumer setColor(int color) {
            return this;
        }

        @Override
        public VertexConsumer setUv(float u, float v) {
            return this;
        }

        @Override
        public VertexConsumer setUv1(int u, int v) {
            return this;
        }

        @Override
        public VertexConsumer setUv2(int u, int v) {
            return this;
        }

        @Override
        public VertexConsumer setNormal(float x, float y, float z) {
            return this;
        }

        @Override
        public VertexConsumer setLineWidth(float width) {
            return this;
        }
    }
}
