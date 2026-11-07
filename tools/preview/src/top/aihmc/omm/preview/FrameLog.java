package top.aihmc.omm.preview;

import static org.lwjgl.opengl.GL33C.*;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/** Delays rows until their own timer query is available; never assigns a later frame's GPU result. */
final class FrameLog implements AutoCloseable {
    static final class Frame {
        long id;
        long timestamp;
        int query;
        boolean sample;
        boolean record;
        boolean focused;
        double animationMs;
        double bonesMs = -1;
        double verticesMs = -1;
        double submitMs;
        double presentMs;
        double frameMs;
        long peakRssBytes;
        double animationTime;
        int round;
        int configured = 1;
        int visible = 1;
        int submitted = 1;
        final List<Integer> modelQueries = new ArrayList<>();
        boolean completeModelQueries = true;
        int drawCalls;
        int nativeDrawCalls;
        int groundDrawCalls;
        int groundQuery;
        long groundGpuNs = -1;
        double groundSubmitMs;
        int skyDrawCalls;
        int skyQuery;
        long skyGpuNs = -1;
        double skySubmitMs;
        long gpuNs = -1;
        int particles;
        int particleDrawCalls;
        int particleQuery;
        long particleGpuNs = -1;
        double particleSubmitMs;
    }

    private final BufferedWriter output;
    private final PreviewMemory memory = new PreviewMemory();
    private final Path directory;
    private final PreviewMesh mesh;
    private final PreviewConfig.Animation animation;
    private final PreviewConfig.Execution execution;
    private final ArrayDeque<Frame> pending = new ArrayDeque<>();
    private int outstandingQueries;
    private boolean closed;
    String sceneLabel;

    FrameLog(Path directory, PreviewMesh mesh, PreviewConfig.Animation animation, PreviewConfig.Execution execution)
        throws IOException {
        this.directory = directory;
        this.mesh = mesh;
        sceneLabel = mesh.label;
        this.animation = animation;
        this.execution = execution;
        Files.createDirectories(directory);
        output = Files.newBufferedWriter(directory.resolve("frames.csv"), StandardCharsets.UTF_8);
        output.write("frame_id,unix_ms,phase,implementation,path,threads,configured,visible,submitted,draw_calls,"
            + "animation_ms,bones_ms,vertices_ms,submit_ms,gpu_ms,present_ms,frame_ms,fps,peak_rss_bytes,animation_"
            + "time,round,ground_draw_calls,total_draw_calls,ground_submit_ms,ground_gpu_ms,sky_draw_calls,sky_submit_"
            + "ms,sky_gpu_ms,particles,particle_draw_calls,particle_submit_ms,particle_gpu_ms,native_draw_calls,window_"
              + "focused\n");
    }

    long peakRssBytes() {
        return memory.peakRssBytes();
    }

    int beginQuery() {
        if (outstandingQueries >= 4096) {
            return 0;
        }
        int query = glGenQueries();
        outstandingQueries++;
        glBeginQuery(GL_TIME_ELAPSED, query);
        return query;
    }

    void enqueue(Frame frame) {
        pending.addLast(frame);
    }

    double poll() throws IOException {
        double latestGpuMs = -1;
        while (!pending.isEmpty()) {
            Frame frame = pending.getFirst();
            if (frame.modelQueries.stream().anyMatch(query -> !available(query)) || !available(frame.query)
                || !available(frame.groundQuery) || !available(frame.skyQuery) || !available(frame.particleQuery)) {
                break;
            }
            if (frame.query != 0) {
                frame.gpuNs = glGetQueryObjectui64(frame.query, GL_QUERY_RESULT);
                glDeleteQueries(frame.query);
                outstandingQueries--;
                latestGpuMs = frame.gpuNs / 1e6;
            }
            if (!frame.modelQueries.isEmpty()) {
                long total = 0;
                for (int query : frame.modelQueries) {
                    total += glGetQueryObjectui64(query, GL_QUERY_RESULT);
                    glDeleteQueries(query);
                    outstandingQueries--;
                }
                frame.gpuNs = frame.completeModelQueries ? total : -1;
                latestGpuMs = frame.gpuNs < 0 ? -1 : frame.gpuNs / 1e6;
            }
            if (frame.groundQuery != 0) {
                frame.groundGpuNs = glGetQueryObjectui64(frame.groundQuery, GL_QUERY_RESULT);
                glDeleteQueries(frame.groundQuery);
                outstandingQueries--;
            }
            if (frame.skyQuery != 0) {
                frame.skyGpuNs = glGetQueryObjectui64(frame.skyQuery, GL_QUERY_RESULT);
                glDeleteQueries(frame.skyQuery);
                outstandingQueries--;
            }
            if (frame.particleQuery != 0) {
                frame.particleGpuNs = glGetQueryObjectui64(frame.particleQuery, GL_QUERY_RESULT);
                glDeleteQueries(frame.particleQuery);
                outstandingQueries--;
            }
            pending.removeFirst();
            if (frame.record) {
                output.write(String.format(Locale.ROOT,
                    "%d,%d,%s,%s,%s,%d,%d,%d,%d,%d,%.6f,%s,%s,%.6f,%s,%.6f,%.6f,%.3f,%d,%.6f,%d,%d,%d,%.6f,%s,%d,%.6f,%"
                        + "s,%d,%d,%.6f,%s,%d,%s%n",
                    frame.id, frame.timestamp, frame.sample ? "sample" : "warmup", execution.implementation,
                    execution.path, execution.threads, frame.configured, frame.visible, frame.submitted,
                    frame.drawCalls, frame.animationMs,
                    frame.bonesMs < 0 ? "NA" : String.format(Locale.ROOT, "%.6f", frame.bonesMs),
                    frame.verticesMs < 0 ? "NA" : String.format(Locale.ROOT, "%.6f", frame.verticesMs), frame.submitMs,
                    frame.gpuNs < 0 ? "NA" : String.format(Locale.ROOT, "%.6f", frame.gpuNs / 1e6), frame.presentMs,
                    frame.frameMs, 1000 / frame.frameMs, frame.peakRssBytes, frame.animationTime, frame.round,
                    frame.groundDrawCalls,
                    frame.drawCalls + frame.groundDrawCalls + frame.skyDrawCalls + frame.particleDrawCalls,
                    frame.groundSubmitMs,
                    frame.groundGpuNs < 0 ? "NA" : String.format(Locale.ROOT, "%.6f", frame.groundGpuNs / 1e6),
                    frame.skyDrawCalls, frame.skySubmitMs,
                    frame.skyGpuNs < 0 ? "NA" : String.format(Locale.ROOT, "%.6f", frame.skyGpuNs / 1e6),
                    frame.particles, frame.particleDrawCalls, frame.particleSubmitMs,
                    frame.particleGpuNs < 0 ? "NA" : String.format(Locale.ROOT, "%.6f", frame.particleGpuNs / 1e6),
                    frame.nativeDrawCalls, frame.focused));
            }
        }
        return latestGpuMs;
    }

    private static boolean available(int query) {
        return query == 0 || glGetQueryObjecti(query, GL_QUERY_RESULT_AVAILABLE) != GL_FALSE;
    }

    void drain() throws IOException {
        glFinish();
        poll();
        output.flush();
    }

    void summary() throws IOException {
        List<Double> times = new ArrayList<>();
        List<Double> gpu = new ArrayList<>();
        long[] peak = {0};
        try (var lines = Files.lines(directory.resolve("frames.csv"), StandardCharsets.UTF_8)) {
            lines.skip(1).forEach(line -> {
                String[] fields = line.split(",");
                if (fields[2].equals("sample")) {
                    times.add(Double.parseDouble(fields[16]));
                    peak[0] = Math.max(peak[0], Long.parseLong(fields[18]));
                    if (!fields[14].equals("NA")) {
                        gpu.add(Double.parseDouble(fields[14]));
                    }
                }
            });
        }
        times.sort(Comparator.naturalOrder());
        gpu.sort(Comparator.naturalOrder());
        String report = "# Preview measurements\n\n"
            + "Actual visible GLFW/OpenGL window. "
            + (mesh.fixture ? "Built-in procedural cube."
                    : animation.enabled
                    ? "Actual configured model animation and script playback (mode=" + animation.mode + ")."
                    : "Static asset bind pose; no script or animation playback.")
            + "\n\n"
            + "GPU queries are associated with their original frame IDs. Present measures glfwSwapBuffers CPU "
            + "blocking. "
            + ("GPU ms and draw_calls cover model geometry; ground_draw_calls/ground_submit_ms/ground_gpu_ms are "
                + "separate, ")
            + ("sky_draw_calls/sky_submit_ms/sky_gpu_ms cover the sky; particle_* covers standalone particle "
                + "rendering. total_draw_calls adds environments and particles, "
                + "excluding HUD. ")
            + ("Frame time covers poll/render/swap/query logging. Peak RSS is Windows PeakWorkingSetSize (process "
                + "lifetime, bytes, including JVM/native/host driver allocations; excludes dedicated VRAM). "
                + "vertices_ms "
                + "records CPU "
                + "vertex work when selected; ")
            + "bone preparation is recorded in bones_ms for animated clips.\n\n"
            + "## "
            + (mesh.fixture             ? "Built-in cube fixture"
                    : animation.enabled ? sceneLabel.replace("\n", " ") + " / " + animation.mode
                                        : "Asset static inspection (not an animation benchmark)")
            + "\n\n"
            + "| Path | Samples | Average FPS | Frame p50 ms | p95 ms | p99 ms | GPU p50 ms | Peak RSS MiB |\n"
            + "|---|---:|---:|---:|---:|---:|---:|---:|\n";
        if (!times.isEmpty()) {
            double mean = times.stream().mapToDouble(Double::doubleValue).average().orElseThrow();
            report += String.format(Locale.ROOT,
                "| " + execution.implementation + " " + execution.path.toUpperCase(Locale.ROOT) + " / "
                    + execution.threads + " threads"
                    + " | %d | %.2f | %.4f | %.4f | %.4f | %s | %.2f |%n",
                times.size(), 1000 / mean, percentile(times, 0.5), percentile(times, 0.95), percentile(times, 0.99),
                gpu.isEmpty() ? "NA" : String.format(Locale.ROOT, "%.4f", percentile(gpu, 0.5)), peak[0] / 1048576.0);
        }
        Files.writeString(directory.resolve(execution.path + "-summary.md"), report, StandardCharsets.UTF_8);
    }

    private static double percentile(List<Double> sorted, double fraction) {
        return sorted.get((int) Math.ceil(fraction * (sorted.size() - 1)));
    }

    @Override
    public void close() throws IOException {
        if (!closed) {
            closed = true;
            try {
                output.close();
            } finally {
                memory.close();
            }
        }
    }
}
