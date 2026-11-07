package top.aihmc.omm;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Each MOD executes in a separate JVM with its own installed dependencies. */
public final class PreviewLauncher {
    private PreviewLauncher() {
    }

    public static void main(String[] args) throws Exception {
        if (args.length == 0 || List.of(args).contains("--help")) {
            System.out.println("""
                Usage: java -jar preview-host.jar [options]
                  --backend spm|oysm          Select an original MOD runtime
                  --config FILE               Local runtime JSON (default: tools/preview/runtime.local.json)
                  --output DIRECTORY          Dependency/record cache (default: build/preview/jar-host)
                  --fixture DIRECTORY         Two-bones diagnostic fixture directory
                  --model PATH                Model path (currently two-bones diagnostic fixture only)
                  --mode gpu|java|simd         Requested rendering mode (GPU window currently wired)
                  --skin FILE                 Skin path override (binding implemented in stage 3)
                  --skin-model auto|alex|steve Skin type override (binding implemented in stage 3)
                  --frames NUMBER             Positive bounded GPU frame count (default: 120)
                  --probe parse|bake|gpu       Diagnostic scope (default: gpu)
                  --help                      Show this help

                Development entry point: GPU opens a real, visible window.
                General model preview, OMP and interactive controls are in development.
                """);
            return;
        }
        Map<String, String> options = parse(args);
        if (options.containsKey("--fixture") && options.containsKey("--model")) {
            throw new IllegalArgumentException("Choose --model or --fixture, not both");
        }
        Path config = Path.of(options.getOrDefault("--config", "tools/preview/runtime.local.json")).toAbsolutePath();
        Map<String, String> overrides = new LinkedHashMap<>();
        Map<String, String> fields = Map.of(
            "--backend", "backend", "--model", "modelPath", "--fixture", "modelPath",
            "--mode", "mode", "--skin", "skinPath", "--skin-model", "skinModel",
            "--frames", "frames", "--output", "outputDirectory"
        );
        fields.forEach((argument, field) -> {
            if (options.containsKey(argument)) {
                overrides.put(field, options.get(argument));
            }
        });
        var settings = RuntimeConfiguration.read(config, overrides);
        String backend = RuntimeConfiguration.text(settings, "backend");
        if (!List.of("spm", "oysm").contains(backend)) {
            throw new IllegalArgumentException("Currently supported diagnostic backends: spm, oysm");
        }
        String probe = options.getOrDefault("--probe", "gpu");
        if (!List.of("parse", "bake", "gpu").contains(probe)) {
            throw new IllegalArgumentException("--probe must be parse, bake or gpu");
        }
        String modelPath = RuntimeConfiguration.text(settings, "modelPath");
        if (modelPath.isBlank()) {
            throw new IllegalArgumentException("Set modelPath in configuration or pass --model");
        }
        Path fixture = Path.of(modelPath).toAbsolutePath();
        if (!Files.isDirectory(fixture)) {
            throw new IllegalArgumentException("Fixture directory does not exist: " + fixture);
        }
        String mode = settings.has("mode") ? settings.get("mode").getAsString() : "gpu";
        if (!List.of("gpu", "java", "simd").contains(mode)) {
            throw new IllegalArgumentException("--mode must be gpu, java or simd");
        }
        if (probe.equals("gpu") && !mode.equals("gpu")) {
            throw new UnsupportedOperationException("CPU windows are not connected yet; --probe bake checks both original CPU renderers");
        }
        int frames = settings.has("frames") ? Integer.parseInt(settings.get("frames").getAsString()) : 120;
        if (frames <= 0) {
            throw new IllegalArgumentException("--frames must be positive");
        }
        String outputPath = settings.has("outputDirectory")
            ? settings.get("outputDirectory").getAsString() : "build/preview/jar-host";
        Path output = Path.of(outputPath).toAbsolutePath();
        settings.addProperty("mode", mode);
        settings.addProperty("frames", frames);
        settings.addProperty("modelPath", fixture.toString());
        settings.addProperty("outputDirectory", output.toString());
        Files.createDirectories(output);
        Files.writeString(output.resolve("effective-config.json"),
            new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(settings));
        String classpath = RuntimeDependencies.prepare(backend, settings, output.resolve(backend));
        Path host = Path.of(PreviewLauncher.class.getProtectionDomain().getCodeSource().getLocation().toURI());
        Path java = Path.of(System.getProperty("java.home"), "bin", "java.exe");
        List<String> command = new ArrayList<>(List.of(
            java.toString(), "--enable-native-access=ALL-UNNAMED", "-Domm.preview.frames=" + frames, "-cp",
            classpath + File.pathSeparator + host, JarEntryProbe.class.getName(),
            backend, fixture.toString()
        ));
        if (!probe.equals("parse")) {
            command.add(probe);
        }
        Process child = new ProcessBuilder(command).inheritIO().start();
        int exitCode = child.waitFor();
        if (exitCode != 0) {
            System.exit(exitCode);
        }
    }

    private static Map<String, String> parse(String[] args) {
        var result = new LinkedHashMap<String, String>();
        List<String> names = List.of("--backend", "--config", "--output", "--fixture", "--probe",
            "--model", "--mode", "--skin", "--skin-model", "--frames");
        for (int index = 0; index < args.length; index += 2) {
            String name = args[index];
            if (!names.contains(name) || index + 1 == args.length || args[index + 1].startsWith("--")) {
                throw new IllegalArgumentException("Unknown option or missing value: " + name);
            }
            if (result.putIfAbsent(name, args[index + 1]) != null) {
                throw new IllegalArgumentException("Duplicate option: " + name);
            }
        }
        return result;
    }

}
