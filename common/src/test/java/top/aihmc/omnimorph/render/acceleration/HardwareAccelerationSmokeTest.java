package top.aihmc.omnimorph.render.acceleration;

import com.google.gson.JsonParser;
import org.figuramc.figura.config.ConfigManager;
import org.figuramc.figura.config.Configs;
import org.figuramc.figura.math.matrix.FiguraMat3;
import org.figuramc.figura.math.matrix.FiguraMat4;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static top.aihmc.omnimorph.render.acceleration.CapabilityReport.*;

public final class HardwareAccelerationSmokeTest {
    public static void main(String[] args) throws Exception {
        Path directory = Files.createTempDirectory("omnimorph-hardware-config-");
        System.setProperty("figura.test.directory", directory.toString());
        configurationTests(directory);
        controllerTests();
        runtimeFailureTest();
        localizationTests();
        require(!NativeLibraryLoader.isLoaded(), "disabled/configuration/fake probes must never load native code");
        System.out.println("PASS H3 config defaults/migration/save/undo, frame activation, requested/effective, deferred deduplicated chat, runtime Java replay");
    }

    private static void configurationTests(Path directory) throws Exception {
        ConfigManager.loadConfig();
        require(!Configs.HARDWARE_ACCELERATION_ENABLED.value && Configs.HARDWARE_ACCELERATION_MODE.value == 0,
                "fresh default is disabled/automatic");
        require(HardwareAcceleration.report().state() == State.DISABLED, "fresh actual Java disabled");
        Configs.HARDWARE_ACCELERATION_ENABLED.setTempValue("true");
        Configs.HARDWARE_ACCELERATION_MODE.setTempValue("1");
        require(HardwareAcceleration.report().state() == State.DISABLED && !NativeLibraryLoader.isLoaded(),
                "temporary edits do not probe/load");
        ConfigManager.discardConfig();
        require(!Configs.HARDWARE_ACCELERATION_ENABLED.tempValue && Configs.HARDWARE_ACCELERATION_MODE.tempValue == 0,
                "original undo discards edits");
        ConfigManager.applyConfig();
        require(HardwareAcceleration.report().state() == State.DISABLED, "undone apply stays disabled");
        Configs.HARDWARE_ACCELERATION_ENABLED.setTempValue("true");
        Configs.HARDWARE_ACCELERATION_MODE.setTempValue("2");
        ConfigManager.applyConfig();
        require(HardwareAcceleration.report().state() == State.PROBING && !NativeLibraryLoader.isLoaded(),
                "apply queues one frame-boundary probe");
        ConfigManager.saveConfig();
        Path file = directory.resolve("omni-morph.json");
        var saved = JsonParser.parseString(Files.readString(file)).getAsJsonObject();
        require(saved.get("hardware_acceleration.enabled").getAsBoolean()
                && saved.get("hardware_acceleration.mode").getAsInt() == 2
                && !saved.has("hardware_acceleration.status"), "preference persisted, actual status not persisted");
        Files.writeString(file, "{\"CONFIG_VERSION\":1}");
        ConfigManager.loadConfig();
        require(!Configs.HARDWARE_ACCELERATION_ENABLED.value && !Configs.HARDWARE_ACCELERATION_ENABLED.tempValue
                && Configs.HARDWARE_ACCELERATION_MODE.value == 0, "missing old/reloaded fields reset to disabled/automatic");
        require(HardwareAcceleration.report().state() == State.DISABLED, "old config disables pending activation");
        Configs.HARDWARE_ACCELERATION_ENABLED.setTempValue("true");
        ConfigManager.applyConfig();
        ConfigManager.setDefaults();
        require(!Configs.HARDWARE_ACCELERATION_ENABLED.value && !Configs.HARDWARE_ACCELERATION_ENABLED.tempValue
                && HardwareAcceleration.report().state() == State.DISABLED, "reset defaults also disables active/pending acceleration");
        require(Configs.HARDWARE_ACCELERATION_STATUS.isDefault()
                && Configs.HARDWARE_ACCELERATION.children.size() == 3, "same settings category with two controls and read-only status");
    }

    private static void controllerTests() throws Exception {
        var service = new AccelerationService();
        AtomicInteger probes = new AtomicInteger();
        List<CapabilityReport> changes = new ArrayList<>();
        List<CapabilityReport> messages = new ArrayList<>();
        var controller = new AccelerationController(service, mode -> {
            probes.incrementAndGet();
            if (mode == Mode.SIMD) {
                return new AccelerationController.Activation(new JavaAccelerationBackend(), Effective.SSE2, 3);
            }
            throw new AccelerationController.ProbeFailure(FallbackReason.GPU_UNAVAILABLE);
        }, changes::add);
        for (int frame = 0; frame < 10; frame++) {
            controller.beginFrame();
        }
        require(probes.get() == 0, "disabled never probes");
        controller.request(true, Mode.AUTO);
        require(probes.get() == 0 && controller.report().state() == State.PROBING, "request only queues activation");
        controller.beginFrame();
        require(probes.get() == 1 && controller.report().state() == State.FALLBACK_JAVA
                && controller.report().requested() == Mode.AUTO && controller.report().effective() == Effective.JAVA,
                "automatic GPU failure keeps preference and effective Java");
        controller.flushChat(false, messages::add);
        require(messages.isEmpty(), "title-screen fallback waits for a player");
        controller.flushChat(true, messages::add);
        controller.flushChat(true, messages::add);
        require(messages.size() == 1, "join-world chat exactly once");
        for (int frame = 0; frame < 100; frame++) {
            controller.request(true, Mode.AUTO);
            controller.beginFrame();
        }
        require(probes.get() == 1 && changes.size() == 2, "same preference/model/frame does not retry or spam logs");
        controller.request(false, Mode.AUTO);
        controller.beginFrame();
        controller.request(true, Mode.AUTO);
        controller.beginFrame();
        controller.flushChat(true, messages::add);
        require(probes.get() == 2 && messages.size() == 1, "explicit retry allowed; session mode/reason chat deduplicated");
        controller.request(true, Mode.GPU);
        controller.beginFrame();
        controller.flushChat(true, messages::add);
        require(messages.size() == 2, "new mode has a distinct notification key");
        controller.request(true, Mode.SIMD);
        controller.beginFrame();
        require(controller.report().state() == State.READY && controller.report().effective() == Effective.SSE2,
                "explicit SIMD can be ready independently of GPU");
        var lease = service.capture(new AccelerationOwner(), "first-person");
        controller.request(false, Mode.SIMD);
        require(service.isCurrent(lease), "disable retires at a frame boundary");
        controller.beginFrame();
        require(!service.isCurrent(lease) && controller.report().state() == State.DISABLED, "disable rejects old batches");
        int beforeReload = probes.get();
        controller.retryAfterResourceReload();
        controller.beginFrame();
        require(probes.get() == beforeReload, "disabled reload does not probe");
        controller.request(true, Mode.SIMD);
        controller.beginFrame();
        controller.retryAfterResourceReload();
        controller.beginFrame();
        require(probes.get() == beforeReload + 2, "enabled resource rebuild permits an explicit bounded retry");
        for (FallbackReason reason : FallbackReason.values()) {
            var failure = new AccelerationController(new AccelerationService(), mode -> {
                throw new AccelerationController.ProbeFailure(reason);
            }, ignored -> {});
            failure.request(true, Mode.SIMD);
            failure.beginFrame();
            require(failure.report().effective() == Effective.JAVA && failure.report().reason() == reason,
                    "structured Java fallback: " + reason);
        }
        var invalidAutomatic = new AccelerationController(new AccelerationService(), mode ->
                new AccelerationController.Activation(new JavaAccelerationBackend(), Effective.SSE2, 3), ignored -> {});
        invalidAutomatic.request(true, Mode.AUTO);
        invalidAutomatic.beginFrame();
        require(invalidAutomatic.report().state() == State.FALLBACK_JAVA, "SIMD-only cannot impersonate automatic GPU success");
        AtomicInteger attempts = new AtomicInteger();
        var rebuilt = new AccelerationController(new AccelerationService(), mode -> {
            if (attempts.getAndIncrement() == 0) {
                throw new AccelerationController.ProbeFailure(FallbackReason.GPU_UNAVAILABLE);
            }
            return new AccelerationController.Activation(new JavaAccelerationBackend(), Effective.GPU, 0);
        }, ignored -> {});
        rebuilt.request(true, Mode.AUTO);
        rebuilt.beginFrame();
        rebuilt.retryAfterResourceReload();
        rebuilt.beginFrame();
        int messageCount = messages.size();
        rebuilt.flushChat(true, messages::add);
        require(rebuilt.report().state() == State.READY && messages.size() == messageCount,
                "successful rebuild cancels a stale title-screen fallback notification");
    }

    private static void runtimeFailureTest() {
        var service = new AccelerationService();
        AtomicInteger failures = new AtomicInteger();
        AccelerationBackend broken = new AccelerationBackend() {
            @Override
            public boolean supports(Operation operation, DrawBatch batch) {
                return true;
            }

            @Override
            public double[] transform(DrawBatch batch) {
                failures.incrementAndGet();
                throw new UnsatisfiedLinkError("injected controlled failure");
            }
        };
        var controller = new AccelerationController(service, mode ->
                new AccelerationController.Activation(broken, Effective.SSE2, 3), ignored -> {});
        controller.request(true, Mode.SIMD);
        controller.beginFrame();
        var lease = service.capture(new AccelerationOwner(), "world");
        MeshHandle mesh = new MeshHandle(new double[]{1, 2, 3, 1, 0, 1, 0, 0.25, 0.75}, 1);
        EngineAccelerationAdapter adapter = captured -> new DrawBatch(captured, mesh,
                new PoseSnapshot(FiguraMat4.of().translate(4, 5, 6), FiguraMat3.of()));
        double[] result = service.transform(lease, adapter);
        require(result != null && result[0] == 5 && result[1] == 7 && result[2] == 9,
                "failed unsubmitted batch actually replays through Java without losing its model");
        service.transform(lease, adapter);
        require(failures.get() == 1 && controller.report().state() == State.FALLBACK_JAVA,
                "runtime circuit stops new backend calls without retrying every draw");
    }

    private static void localizationTests() throws Exception {
        for (String language : List.of("en_us", "zh_cn")) {
            try (var stream = HardwareAccelerationSmokeTest.class.getClassLoader()
                    .getResourceAsStream("assets/omni-morph/lang/" + language + ".json")) {
                var text = JsonParser.parseReader(new java.io.InputStreamReader(stream, java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();
                require(text.has("omni-morph.config.hardware_acceleration.enabled")
                        && text.has("omni-morph.config.hardware_acceleration.status"), "existing settings localization: " + language);
                require(text.get("omni-morph.config.hardware_acceleration.chat.fallback").getAsString().contains("%s"),
                        "localized chat includes exact reason");
                for (FallbackReason reason : FallbackReason.values()) {
                    require(text.has("omni-morph.config.hardware_acceleration.reason." + reason.name().toLowerCase(java.util.Locale.ROOT)),
                            "localized reason: " + language + "/" + reason);
                }
            }
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
