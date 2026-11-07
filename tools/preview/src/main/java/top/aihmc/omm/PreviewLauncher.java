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
                  --backend omp|spm|oysm      Select an original MOD runtime
                  --config FILE               Local runtime JSON (default: tools/preview/runtime.local.json)
                  --mod-jar FILE              Override the selected MOD JAR for build comparisons
                  --format ysm|figura          Model format (default: ysm; Figura requires OMP)
                  --output DIRECTORY          Dependency/record cache (default: build/preview/jar-host)
                  --fixture DIRECTORY         Two-bones diagnostic fixture directory
                  --model PATH                Original model path; alternatively set scene.models in JSON
                  --mode gpu|java|simd         Requested rendering mode
                  --skin FILE                 Skin path override (binding implemented in stage 3)
                  --skin-model auto|alex|steve Skin type override (binding implemented in stage 3)
                  --frames NUMBER             Positive bounded window frame count (default: 120)
                  --probe parse|bake|window|gpu|gpu-check  Diagnostic scope (default: gpu)
                  --help                      Show this help

                Development entry point: GPU opens a real, visible window.
                OMP: YSM/Figura Java/SIMD and GPU animated windows.
                Multi-model scenes use scene.models; any Figura entry selects OMP for the whole scene.
                Superflat scenery, HUD, original controls and sampling reports are still in development.
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
            "--frames", "frames", "--output", "outputDirectory", "--mod-jar", "modJar",
            "--format", "format"
        );
        fields.forEach((argument, field) -> {
            if (options.containsKey(argument)) {
                overrides.put(field, options.get(argument));
            }
        });
        var settings = RuntimeConfiguration.read(config, overrides);
        String backend = RuntimeConfiguration.text(settings, "backend");
        if (!List.of("omp", "spm", "oysm").contains(backend)) {
            throw new IllegalArgumentException("--backend must be omp, spm or oysm");
        }
        String probe = options.getOrDefault("--probe", "gpu");
        boolean modelList = settings.has("scene") && settings.getAsJsonObject("scene").has("models");
        if (modelList) {
            if (options.containsKey("--model") || options.containsKey("--fixture")) {
                throw new IllegalArgumentException("Choose scene.models or --model/--fixture, not both");
            }
            boolean hasFigura = normalizeModels(settings, config.getParent());
            settings.addProperty("requestedBackend", backend);
            if (hasFigura) {
                backend = "omp";
                settings.addProperty("backend", backend);
                System.out.println("SCENE BACKEND omp reason=figura-requires-unified-omp");
            }
            if (!List.of("window", "gpu").contains(probe)) {
                throw new IllegalArgumentException("scene.models requires --probe window or gpu");
            }
        }
        String format = settings.has("format") ? settings.get("format").getAsString() : "ysm";
        if (!List.of("ysm", "figura").contains(format)) {
            throw new IllegalArgumentException("--format must be ysm or figura");
        }
        if (format.equals("figura") && (!backend.equals("omp") || !List.of("parse", "bake", "window", "gpu").contains(probe))) {
            throw new UnsupportedOperationException("Figura requires --backend omp --probe parse, bake, window or gpu");
        }
        if (!List.of("parse", "bake", "gpu", "window", "gpu-check").contains(probe)) {
            throw new IllegalArgumentException("--probe must be parse, bake, gpu, window or gpu-check");
        }
        if (probe.equals("gpu-check") && !backend.equals("omp")) {
            throw new IllegalArgumentException("gpu-check is currently an OMP activation diagnostic");
        }
        String modelPath = RuntimeConfiguration.text(settings, "modelPath");
        if (modelPath.isBlank()) {
            throw new IllegalArgumentException("Set modelPath in configuration or pass --model");
        }
        Path fixture = Path.of(modelPath).toAbsolutePath();
        if (!Files.isDirectory(fixture) && !Files.isRegularFile(fixture)) {
            throw new IllegalArgumentException("Model path does not exist or is not supported yet: " + fixture);
        }
        String mode = settings.has("mode") ? settings.get("mode").getAsString() : "gpu";
        if (probe.equals("window") && mode.equals("gpu")) {
            throw new UnsupportedOperationException("Use --probe gpu for GPU mode");
        }
        if (!List.of("gpu", "java", "simd").contains(mode)) {
            throw new IllegalArgumentException("--mode must be gpu, java or simd");
        }
        if (probe.equals("gpu") && !mode.equals("gpu")) {
            throw new UnsupportedOperationException("Use --probe window for Java/SIMD mode");
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
        String localPlayerUuid = settings.has("localPlayerUuid")
            ? settings.get("localPlayerUuid").getAsString() : "00000000-0000-0000-0000-000000000000";
        java.util.UUID.fromString(localPlayerUuid);
        settings.addProperty("localPlayerUuid", localPlayerUuid);
        Files.createDirectories(output);
        Files.writeString(output.resolve("effective-config.json"),
            new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(settings));
        String classpath = RuntimeDependencies.prepare(backend, settings, output.resolve(backend));
        Path host = Path.of(PreviewLauncher.class.getProtectionDomain().getCodeSource().getLocation().toURI());
        Path javaExecutable = Path.of(System.getProperty("java.home"), "bin", "java.exe");
        List<String> command = new ArrayList<>(List.of(
            javaExecutable.toString(), "--enable-native-access=ALL-UNNAMED", "-Domm.preview.frames=" + frames,
            "-Domm.preview.mode=" + mode, "-Domm.preview.output=" + output,
            "-Domm.preview.fixture=" + options.containsKey("--fixture"),
            "-Domm.preview.localPlayerUuid=" + localPlayerUuid, "-cp",
            classpath + File.pathSeparator + host,
            modelList ? (backend.equals("omp") ? MixedModelWorker.class.getName() : ReferenceListWorker.class.getName())
                : format.equals("figura") ? FiguraWorker.class.getName()
                : backend.equals("omp") ? OmniMorphWorker.class.getName() : JarEntryProbe.class.getName(),
            backend, fixture.toString()
        ));
        if (!probe.equals("parse")) {
            command.add(probe);
        }
        if (backend.equals("omp")) {
            String selectedJar = classpath.split(java.util.regex.Pattern.quote(File.pathSeparator), 2)[0];
            command.add(1, "-javaagent:" + host + "=" + selectedJar);
        }
        Process child = new ProcessBuilder(command).inheritIO().start();
        int exitCode = child.waitFor();
        if (exitCode != 0) {
            System.exit(exitCode);
        }
    }

    private static boolean normalizeModels(com.google.gson.JsonObject settings, Path configurationDirectory) {
        var scene = settings.getAsJsonObject("scene");
        var models = scene.getAsJsonArray("models");
        if (models.isEmpty()) {
            throw new IllegalArgumentException("scene.models must contain at least one model");
        }
        if (scene.has("count") && scene.get("count").getAsInt() < models.size()) {
            throw new IllegalArgumentException("scene.count must cover every entry in scene.models");
        }
        boolean hasFigura = false;
        for (var value : models) {
            var model = value.getAsJsonObject();
            String pathText = RuntimeConfiguration.text(model, "path");
            String format = RuntimeConfiguration.text(model, "format");
            if (pathText.isBlank() || !List.of("ysm", "figura").contains(format)) {
                throw new IllegalArgumentException("Each scene.models entry requires path and format (ysm or figura)");
            }
            Path path = configurationDirectory.resolve(pathText).toAbsolutePath().normalize();
            if (!Files.isDirectory(path) && !Files.isRegularFile(path)) {
                throw new IllegalArgumentException("Scene model does not exist: " + path);
            }
            model.addProperty("path", path.toString());
            hasFigura |= format.equals("figura");
        }
        settings.addProperty("modelPath", models.get(0).getAsJsonObject().get("path").getAsString());
        return hasFigura;
    }

    private static Map<String, String> parse(String[] args) {
        var result = new LinkedHashMap<String, String>();
        List<String> names = List.of("--backend", "--config", "--output", "--fixture", "--probe",
            "--model", "--mode", "--skin", "--skin-model", "--frames", "--mod-jar", "--format");
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
