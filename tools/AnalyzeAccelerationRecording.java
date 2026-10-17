import jdk.jfr.consumer.RecordedFrame;
import jdk.jfr.consumer.RecordedStackTrace;
import jdk.jfr.consumer.RecordingFile;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

/** Summarizes sampled stacks, without exporting model data or network events. */
public final class AnalyzeAccelerationRecording {
    private AnalyzeAccelerationRecording() {
    }

    public static void main(String[] args) throws Exception {
        Map<String, Long> counts = new LinkedHashMap<>();
        for (String category : new String[]{"animation", "vertices", "upload", "draw", "loading", "other"}) {
            counts.put(category, 0L);
        }
        Map<String, Long> hotFrames = new LinkedHashMap<>();
        try (RecordingFile recording = new RecordingFile(Path.of(args[0]))) {
            while (recording.hasMoreEvents()) {
                var event = recording.readEvent();
                String type = event.getEventType().getName();
                if (!type.equals("jdk.ExecutionSample") && !type.equals("jdk.NativeMethodSample")) {
                    continue;
                }
                RecordedStackTrace stack = event.getStackTrace();
                if (stack == null) {
                    continue;
                }
                String category = "other";
                for (RecordedFrame frame : stack.getFrames()) {
                    String name = frame.getMethod().getType().getName() + "." + frame.getMethod().getName();
                    String classified = classify(name);
                    if (!classified.equals("other")) {
                        category = classified;
                        hotFrames.merge(name, 1L, Long::sum);
                        break;
                    }
                }
                counts.merge(category, 1L, Long::sum);
            }
        }
        StringBuilder report = new StringBuilder("JFR sampled stacks; counts are not frame durations or GPU timings.\n");
        counts.forEach((name, count) -> report.append(name).append('=').append(count).append('\n'));
        report.append("\nHot classified frames:\n");
        hotFrames.entrySet().stream().sorted(Map.Entry.<String, Long>comparingByValue().reversed()).limit(30)
                .forEach(entry -> report.append(entry.getValue()).append(' ').append(entry.getKey()).append('\n'));
        Files.writeString(Path.of(args[1]), report);
        System.out.print(report);
    }

    private static String classify(String name) {
        if (name.contains("YsmAnimation") || name.contains("YsmController") || name.contains("Molang")
                || name.contains("AnimationPlayer") || name.contains("lua.LuaRuntime")) {
            return "animation";
        }
        if (name.contains("ImmediateFiguraRenderer") || name.contains("YsmRenderer")
                || name.contains("FiguraMat") || name.contains("FiguraVec")) {
            return "vertices";
        }
        if (name.contains("writeToBuffer") || name.contains("writeToTexture") || name.contains("upload")
                || name.contains("glBufferData") || name.contains("glBufferSubData")) {
            return "upload";
        }
        if (name.contains("GlRenderPass") || name.contains("glDraw") || name.contains("presentFrame")
                || name.contains("swapBuffers")) {
            return "draw";
        }
        if (name.contains("YsmAvatarLoader") || name.contains("LocalAvatarLoader")
                || name.contains("YsmSourcePayload") || name.contains("YsmGeometryParser")
                || name.contains("YsmBinaryParser") || name.contains("BlockbenchModelParser")) {
            return "loading";
        }
        return "other";
    }
}
