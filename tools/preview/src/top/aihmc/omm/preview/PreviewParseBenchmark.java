package top.aihmc.omm.preview;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

/** Warm-cache import measurements; start a fresh JVM for each parser/thread combination. */
public final class PreviewParseBenchmark {
    private PreviewParseBenchmark() {}

    public static void main(String[] args) throws Exception {
        if (args.length != 6) {
            throw new IllegalArgumentException(
                "Usage: PreviewParseBenchmark <config.json> <output.csv> <java|native> <threads> <warmups> <samples>");
        }
        var config = PreviewConfig.load(new String[] {"--config", args[0]});
        if (!config.scene.type.equals("single") || config.scene.models.size() != 1) {
            throw new IllegalArgumentException("Parse benchmark measures exactly one model per invocation");
        }
        String parser = args[2];
        int threads = Integer.parseInt(args[3]);
        int warmups = Integer.parseInt(args[4]);
        int samples = Integer.parseInt(args[5]);
        if (!java.util.List.of("java", "native").contains(parser) || threads < 1 || warmups < 0 || samples < 1
            || parser.equals("java") && threads != 1) {
            throw new IllegalArgumentException("Invalid parser, thread budget or sample counts");
        }
        config.execution.parser = parser;
        config.execution.parserThreads = threads;
        config.settings.enabled = false;
        Path output = Path.of(args[1]).toAbsolutePath();
        Files.createDirectories(output.getParent());
        var memory = new PreviewMemory();
        try (var writer = Files.newBufferedWriter(output, StandardCharsets.UTF_8)) {
            writer.write("sample,parser,threads,load_ms,native_core_ms,vertices,bones,peak_rss_bytes\n");
            for (int index = -warmups; index < samples; index++) {
                var mesh = PreviewMesh.load(config);
                if (index >= 0) {
                    var info = mesh.parserInfo;
                    writer.write(String.format(Locale.ROOT, "%d,%s,%d,%.6f,%s,%d,%d,%d%n", index, info.implementation(),
                        info.threads(), info.loadMs(),
                        info.nativeParseMs() < 0 ? "NA" : String.format(Locale.ROOT, "%.6f", info.nativeParseMs()),
                        mesh.vertices.length / 5, mesh.skeleton == null ? 0 : mesh.skeleton.names.length,
                        memory.peakRssBytes()));
                }
            }
        }
        System.out.println("Parse samples: " + output);
    }
}
