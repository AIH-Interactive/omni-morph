package top.aihmc.omm.preview;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

final class PreviewConfig {
    static final Gson JSON = new GsonBuilder().setPrettyPrinting().create();

    static final class Window {
        int width = 1280;
        int height = 720;
        int vsync = 1;
        String title = "omm-core preview";
    }

    static final class Execution {
        String implementation = "java";
        String path = "gpu";
        int threads;
        String nativeLibrary = "";
        String parser = "java";
        int parserThreads;
    }

    static final class Camera {
        float yawDegrees;
        float pitchDegrees;
        float fieldOfViewDegrees = 50;
        float distanceScale = 2.8f;
    }

    static final class Ground {
        boolean enabled = true;
        String texture = "";
        float red = 0.57f;
        float green = 0.74f;
        float blue = 0.35f;
    }

    static final class Sky {
        boolean enabled = true;
        float sunYawDegrees = -35;
        float sunPitchDegrees = 15;
        float cloudHeight = 128;
    }

    static final class Scene {
        String type = "single";
        int count = 1;
        long seed = 42;
        List<String> models = List.of("builtin:cube");
        List<String> clips = List.of();
        float timeOffsetStepSeconds = 0.137f;
        JsonObject modelOptions = new JsonObject();
    }

    static final class Vanilla {
        boolean enabled = true;
        String skinUrl = "";
        String skinCacheDirectory = "";
        String model = "auto";
    }

    static final class Animation {
        boolean enabled;
        boolean actions;
        String mode = "clip";
        String clip = "";
        float speed = 1;
        float timeOffsetSeconds;
        Float fixedTimeSeconds;
    }

    static final class Scripts {
        boolean enabled;
        JsonObject controls = new JsonObject();
    }

    static final class Settings {
        boolean enabled = true;
        String directory = "";
        String modelKey = "";
    }

    static final class Benchmark {
        boolean enabled;
        int warmupFrames = 120;
        int sampleFrames = 600;
        int rounds = 5;
        int exitAfterFrames;
    }

    static final class Interaction {
        boolean enabled;
        List<PreviewInteraction.Operation> actions = List.of();
        String replayFile = "";
    }

    static final class Recording {
        boolean enabled = true;
        String outputDirectory = "runs";
    }

    int schemaVersion = 1;
    Window window = new Window();
    Camera camera = new Camera();
    Ground ground = new Ground();
    Sky sky = new Sky();
    Execution execution = new Execution();
    Scene scene = new Scene();
    Vanilla vanilla = new Vanilla();
    Animation animation = new Animation();
    Scripts scripts = new Scripts();
    Settings settings = new Settings();
    Interaction interaction = new Interaction();
    Benchmark benchmark = new Benchmark();
    Recording recording = new Recording();
    JsonObject states = new JsonObject();
    transient Path configFile;
    transient Path outputDirectory;

    static PreviewConfig load(String[] args) throws IOException {
        Path file = null;
        for (int index = 0; index < args.length; index++) {
            if (args[index].equals("--config")) {
                file = Path.of(value(args, ++index, "--config")).toAbsolutePath().normalize();
            }
        }
        require(file != null, "--config is required; use --help for an example");
        PreviewConfig config = JSON.fromJson(Files.readString(file, StandardCharsets.UTF_8), PreviewConfig.class);
        require(config != null, "configuration must be an object");
        require(config.window != null && config.camera != null && config.ground != null && config.sky != null
                && config.animation != null && config.scripts != null && config.interaction != null
                && config.execution != null && config.scene != null && config.benchmark != null
                && config.recording != null && config.states != null,
            "window/camera/ground/sky/execution/scene/benchmark/recording/states cannot be null");
        config.configFile = file;
        for (int index = 0; index < args.length; index++) {
            switch (args[index]) {
                case "--config" -> index++;
                case "--frames" ->
                    config.benchmark.exitAfterFrames = Integer.parseInt(value(args, ++index, "--frames"));
                case "--state" -> {
                    String state = value(args, ++index, "--state");
                    int separator = state.indexOf('=');
                    require(separator > 0, "--state requires name=JSON-value");
                    config.states.add(
                        state.substring(0, separator), JsonParser.parseString(state.substring(separator + 1)));
                }
                case "--no-record" -> config.recording.enabled = false;
                default -> throw new IllegalArgumentException("unknown argument: " + args[index]);
            }
        }
        config.validate();
        config.outputDirectory = file.getParent().resolve(config.recording.outputDirectory).normalize();
        return config;
    }

    private static String value(String[] args, int index, String option) {
        require(index < args.length, option + " needs a value");
        return args[index];
    }

    private void validate() {
        require(schemaVersion == 1, "schemaVersion must be 1");
        require(window.width >= 320 && window.width <= 16384, "window.width must be 320..16384");
        require(window.height >= 240 && window.height <= 16384, "window.height must be 240..16384");
        require(window.vsync == 0 || window.vsync == 1, "window.vsync must be 0 or 1");
        require(Float.isFinite(camera.yawDegrees) && Float.isFinite(camera.pitchDegrees)
                && Math.abs(camera.pitchDegrees) < 89,
            "camera yaw must be finite and pitch must be inside -89..89 degrees");
        require(Float.isFinite(camera.fieldOfViewDegrees) && camera.fieldOfViewDegrees >= 10
                && camera.fieldOfViewDegrees <= 100,
            "camera.fieldOfViewDegrees must be 10..100");
        require(Float.isFinite(camera.distanceScale) && camera.distanceScale > 1,
            "camera.distanceScale must be finite and greater than 1");
        require(ground.texture != null, "ground.texture cannot be null");
        require(vanilla != null && vanilla.skinUrl != null && vanilla.skinCacheDirectory != null,
            "vanilla, skinUrl and skinCacheDirectory cannot be null");
        require(List.of("auto", "steve", "alex").contains(vanilla.model), "vanilla.model must be auto/steve/alex");
        require(Float.isFinite(sky.sunYawDegrees) && Float.isFinite(sky.sunPitchDegrees)
                && Math.abs(sky.sunPitchDegrees) < 89,
            "sky sun angles must be finite and pitch must be inside -89..89 degrees");
        require(Float.isFinite(sky.cloudHeight) && sky.cloudHeight > 0, "sky.cloudHeight must be finite and positive");
        for (float component : new float[] {ground.red, ground.green, ground.blue}) {
            require(Float.isFinite(component) && component >= 0 && component <= 1,
                "ground tint components must be inside 0..1");
        }
        require(List.of("java", "native").contains(execution.implementation),
            "execution.implementation must be java/native");
        require(List.of("cpu", "gpu").contains(execution.path), "execution.path must be cpu/gpu");
        require(execution.threads >= 0, "execution.threads must be 0 (automatic) or positive");
        require(execution.nativeLibrary != null, "execution.nativeLibrary cannot be null");
        require(List.of("java", "native").contains(execution.parser), "execution.parser must be java/native");
        require(execution.parserThreads >= 0, "execution.parserThreads must be 0 (CPU cores) or positive");
        if (execution.implementation.equals("java") && execution.threads == 0)
            execution.threads = 1;
        require(!(execution.implementation.equals("native") || execution.parser.equals("native"))
                || !execution.nativeLibrary.isBlank(),
            "execution.nativeLibrary is required for native execution");
        require(List.of("single", "same-model", "mixed").contains(scene.type),
            "scene.type must be single/same-model/mixed");
        require(scene.count > 0 && scene.models != null && !scene.models.isEmpty(),
            "scene needs models and a positive count");
        require(scene.clips != null && scene.modelOptions != null, "scene.clips/modelOptions cannot be null");
        require(Float.isFinite(scene.timeOffsetStepSeconds) && scene.timeOffsetStepSeconds >= 0,
            "scene.timeOffsetStepSeconds must be finite and nonnegative");
        require(scene.type.equals("single") || scene.count <= 100, "multi-model scenes support at most 100 instances");
        require(List.of("clip", "controller", "figura").contains(animation.mode),
            "animation.mode must be clip/controller/figura");
        require(scripts.controls != null && (!scripts.enabled || animation.enabled),
            "scripts.controls cannot be null; Lua currently requires animation.enabled=true");
        require(settings != null && settings.directory != null && settings.modelKey != null,
            "settings/directory/modelKey cannot be null");
        require(interaction.actions != null && interaction.replayFile != null,
            "interaction.actions/replayFile cannot be null");
        require((!interaction.enabled && interaction.actions.isEmpty() && interaction.replayFile.isBlank())
                || animation.enabled,
            "model interaction requires animation.enabled");
        require(!animation.actions || animation.enabled, "animation.actions requires animation.enabled=true");
        require(animation.clip != null
                && (!animation.enabled || !animation.mode.equals("clip") || !animation.clip.isBlank()),
            "animation.clip must name a clip when animation.enabled=true");
        require(Float.isFinite(animation.speed) && animation.speed >= 0 && Float.isFinite(animation.timeOffsetSeconds),
            "animation speed/offset must be finite and speed nonnegative");
        require(animation.fixedTimeSeconds == null || Float.isFinite(animation.fixedTimeSeconds),
            "animation.fixedTimeSeconds must be finite");
        require(benchmark.warmupFrames >= 0 && benchmark.sampleFrames > 0 && benchmark.rounds > 0
                && benchmark.exitAfterFrames >= 0,
            "benchmark frame counts are invalid");
        require((long) benchmark.sampleFrames * benchmark.rounds + benchmark.warmupFrames <= Integer.MAX_VALUE,
            "benchmark frame count overflows");
        require(recording.outputDirectory != null && !recording.outputDirectory.isBlank(),
            "recording.outputDirectory is empty");
        if (states.has("running")) {
            require(states.get("running").isJsonPrimitive() && states.getAsJsonPrimitive("running").isBoolean(),
                "states.running must be boolean");
        }
    }

    static void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalArgumentException(message);
        }
    }

    boolean running() {
        return !states.has("running") || states.get("running").getAsBoolean();
    }
}
