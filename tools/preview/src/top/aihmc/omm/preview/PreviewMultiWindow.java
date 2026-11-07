package top.aihmc.omm.preview;

import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL33C.*;

import com.google.gson.JsonObject;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import org.joml.FrustumIntersection;
import org.joml.Matrix4f;
import org.lwjgl.glfw.GLFWErrorCallback;
import org.lwjgl.opengl.GL;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;

/** Fixed-layout, real-window same-model/mixed scenes with shared resources and owned playback state. */
final class PreviewMultiWindow {
    private static final class Source implements AutoCloseable {
        final String name;
        final PreviewConfig config;
        final PreviewMesh mesh;
        final PreviewModelRenderer renderer;
        final Map<String, PreviewAnimation> animations = new LinkedHashMap<>();
        final List<String> clips;

        Source(String name, PreviewConfig config, PreviewNativeVertices runtime, List<String> clips) throws Exception {
            this.name = name;
            this.config = config;
            this.clips = clips;
            mesh = PreviewMesh.load(config);
            renderer = new PreviewModelRenderer(mesh, config, runtime);
        }

        PreviewAnimation.State newState(int index) {
            if (!config.animation.enabled)
                return null;
            String clip = clips.isEmpty() ? config.animation.clip : clips.get(index % clips.size());
            var animation = animations.computeIfAbsent(clip, key -> {
                var selected = PreviewConfig.JSON.fromJson(PreviewConfig.JSON.toJson(config), PreviewConfig.class);
                selected.states = config.states.deepCopy();
                selected.configFile = config.configFile;
                selected.outputDirectory = config.outputDirectory;
                selected.animation.clip = key;
                return new PreviewAnimation(mesh, selected);
            });
            return animation.newState();
        }

        @Override
        public void close() {
            for (var animation : animations.values()) animation.close();
            renderer.close();
        }
    }

    private record Instance(Source source, PreviewAnimation.State state, Matrix4f placement, float offset) {}

    private static void merge(JsonObject target, JsonObject patch) {
        for (var entry : patch.entrySet()) {
            if (entry.getValue().isJsonObject() && target.has(entry.getKey())
                && target.get(entry.getKey()).isJsonObject())
                merge(target.getAsJsonObject(entry.getKey()), entry.getValue().getAsJsonObject());
            else
                target.add(entry.getKey(), entry.getValue().deepCopy());
        }
    }

    private static PreviewConfig sourceConfig(PreviewConfig config, String model, JsonObject options) {
        var json = PreviewConfig.JSON.toJsonTree(config).getAsJsonObject();
        json.add("states", config.states.deepCopy());
        for (var entry : options.entrySet()) {
            if (entry.getKey().equals("clips"))
                continue;
            if (entry.getKey().equals("parser")) {
                String parser = entry.getValue().getAsString();
                if (!List.of("java", "native").contains(parser))
                    throw new IllegalArgumentException("modelOptions.parser must be java/native");
                json.getAsJsonObject("execution").addProperty("parser", parser);
                continue;
            }
            if (!List.of("animation", "scripts", "states").contains(entry.getKey()) || !entry.getValue().isJsonObject())
                throw new IllegalArgumentException(
                    "scene.modelOptions supports animation/scripts/states objects, clips and parser");
            merge(json.getAsJsonObject(entry.getKey()), entry.getValue().getAsJsonObject());
        }
        var selected = PreviewConfig.JSON.fromJson(json, PreviewConfig.class);
        selected.configFile = config.configFile;
        selected.outputDirectory = config.outputDirectory;
        selected.scene.type = "single";
        selected.scene.count = 1;
        selected.scene.models = List.of(model);
        return selected;
    }

    static void run(PreviewConfig config) throws Exception {
        if (config.execution.implementation.equals("java") && config.execution.threads != 1)
            throw new IllegalArgumentException("Java baseline uses one CPU thread");
        if (config.interaction.enabled)
            throw new IllegalArgumentException(
                "Multi-model benchmark uses configured controls; interactive wheel is single-model");
        if (config.scene.type.equals("same-model") && config.scene.models.size() != 1)
            throw new IllegalArgumentException("same-model needs exactly one model source");
        String runId = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss-SSS").format(LocalDateTime.now()) + "-"
            + ProcessHandle.current().pid();
        config.outputDirectory = config.outputDirectory.resolve(runId);
        Files.createDirectories(config.outputDirectory);
        GLFWErrorCallback errors = GLFWErrorCallback.createPrint(System.err).set();
        if (!glfwInit())
            throw new IllegalStateException("GLFW initialization failed");
        long window = 0;
        var sources = new LinkedHashMap<String, Source>();
        try {
            glfwWindowHint(GLFW_CONTEXT_VERSION_MAJOR, 3);
            glfwWindowHint(GLFW_CONTEXT_VERSION_MINOR, 3);
            glfwWindowHint(GLFW_OPENGL_PROFILE, GLFW_OPENGL_CORE_PROFILE);
            window = glfwCreateWindow(config.window.width, config.window.height, config.window.title, 0, 0);
            if (window == 0)
                throw new IllegalStateException("Could not create visible multi-model window");
            glfwMakeContextCurrent(window);
            GL.createCapabilities();
            glfwSwapInterval(config.window.vsync);
            System.out.println("OpenGL: " + glGetString(GL_VERSION) + " / " + glGetString(GL_RENDERER));
            Files.writeString(config.outputDirectory.resolve("graphics.txt"),
                glGetString(GL_VERSION) + "\n" + glGetString(GL_RENDERER) + "\nVSync=" + config.window.vsync + "\n");
            PreviewNativeVertices sharedRuntime = null;
            for (String model : config.scene.models) {
                if (sources.containsKey(model))
                    continue;
                JsonObject options = config.scene.modelOptions.has(model)
                    ? config.scene.modelOptions.getAsJsonObject(model)
                    : new JsonObject();
                List<String> clips = new ArrayList<>(config.scene.clips);
                if (options.has("clips")) {
                    clips.clear();
                    options.getAsJsonArray("clips").forEach(value -> clips.add(value.getAsString()));
                }
                var source = new Source(model, sourceConfig(config, model, options), sharedRuntime, clips);
                sources.put(model, source);
                if (sharedRuntime == null && source.renderer.nativeVertices != null) {
                    sharedRuntime = source.renderer.nativeVertices;
                    config.execution.threads = sharedRuntime.threads;
                }
            }
            Files.writeString(
                config.outputDirectory.resolve("resolved-config.json"), PreviewConfig.JSON.toJson(config));
            var assets = new ArrayList<>(sources.values());
            float radius = (float) assets.stream().mapToDouble(source -> source.mesh.radius).max().orElseThrow();
            var instances = new ArrayList<Instance>();
            var placements = new ArrayList<Map<String, Object>>();
            Random random = new Random(config.scene.seed);
            var cells = new ArrayList<int[]>();
            cells.add(new int[] {0, 0});
            for (int z = -5; z < 5; z++)
                for (int x = -5; x < 5; x++)
                    if (x != 0 || z != 0)
                        cells.add(new int[] {x, z});
            for (int index = 0; index < config.scene.count; index++) {
                Source source = assets.get(index % assets.size());
                int[] cell = cells.get(index);
                float x = (cell[0] + (random.nextFloat() - 0.5f) * 0.2f) * radius * 4;
                float z = (cell[1] + (random.nextFloat() - 0.5f) * 0.2f) * radius * 4;
                float offset = index * config.scene.timeOffsetStepSeconds;
                var placement = new Matrix4f().translation(
                    x - source.mesh.center.x, -source.mesh.minimumY, z - source.mesh.center.z);
                var state = source.newState(index / assets.size());
                instances.add(new Instance(source, state, placement, offset));
                placements.add(Map.of("instance", index, "model", source.name, "x", x, "z", z, "timeOffset", offset,
                    "stateIdentity", state == null ? 0 : System.identityHashCode(state)));
            }
            Files.writeString(config.outputDirectory.resolve("scene.json"),
                PreviewConfig.JSON.toJson(Map.of("type", config.scene.type, "seed", config.scene.seed, "configured",
                    instances.size(), "uniqueModelResources", sources.size(), "geometrySets", sources.size(),
                    "textureUploads", assets.stream().mapToInt(source -> source.renderer.textures.length).sum(),
                    "nativeRuntimes", sharedRuntime == null ? 0 : 1, "placements", placements)));
            Files.writeString(config.outputDirectory.resolve("parse.json"),
                PreviewConfig.JSON.toJson(assets.stream()
                        .map(source -> Map.of("model", source.name, "parser", source.mesh.parserInfo))
                        .toList()));
            loop(window, config, assets, instances, radius);
        } finally {
            var reverse = new ArrayList<>(sources.values());
            java.util.Collections.reverse(reverse);
            // Shared native runtime owner is the first source, so release it last.
            for (var source : reverse) {
                source.close();
            }
            var audio = new ArrayList<Map<String, Object>>();
            for (var source : sources.values()) {
                for (var entry : source.animations.entrySet()) {
                    audio.add(Map.of(
                        "model", source.name, "clip", entry.getKey(), "audio", entry.getValue().audio.snapshot()));
                }
            }
            Files.writeString(config.outputDirectory.resolve("audio.json"), PreviewConfig.JSON.toJson(audio));
            if (window != 0) {
                org.lwjgl.glfw.Callbacks.glfwFreeCallbacks(window);
                glfwDestroyWindow(window);
            }
            glfwTerminate();
            errors.free();
        }
        System.out.println("Preview output: " + config.outputDirectory);
    }

    private static float animationTime(Instance instance, float sceneTime) {
        var animation = instance.source.config.animation;
        float clock = animation.fixedTimeSeconds == null ? sceneTime * animation.speed + animation.timeOffsetSeconds
                                                         : animation.fixedTimeSeconds;
        return Math.max(0, clock + instance.offset);
    }

    private static void loop(long window, PreviewConfig config, List<Source> sources, List<Instance> instances,
        float modelRadius) throws Exception {
        int program = PreviewMain.program(PreviewMain.MODEL_VERTEX, PreviewMain.MODEL_FRAGMENT);
        int hud = PreviewMain.program("""
            #version 330 core
            layout(location=0) in vec3 position;
            uniform mat4 transform;
            void main() { gl_Position = transform * vec4(position,1); }
            """, """
            #version 330 core
            out vec4 pixel; void main() { pixel=vec4(0.95,0.96,1,1); }
            """);
        int hudVao = glGenVertexArrays(), hudVbo = glGenBuffers(), hudEbo = glGenBuffers();
        ByteBuffer glyphs = MemoryUtil.memAlloc(262144);
        IntBuffer indices = MemoryUtil.memAllocInt(24576);
        glBindVertexArray(hudVao);
        glBindBuffer(GL_ARRAY_BUFFER, hudVbo);
        glBufferData(GL_ARRAY_BUFFER, glyphs.capacity(), GL_STREAM_DRAW);
        glEnableVertexAttribArray(0);
        glVertexAttribPointer(0, 3, GL_FLOAT, false, 16, 0);
        glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, hudEbo);
        glBufferData(GL_ELEMENT_ARRAY_BUFFER, (long) indices.capacity() * 4, GL_STREAM_DRAW);
        boolean[] recording = {config.recording.enabled || config.benchmark.enabled};
        glfwSetKeyCallback(window, (handle, key, scan, action, mods) -> {
            if (action == GLFW_PRESS && key == GLFW_KEY_ESCAPE)
                glfwSetWindowShouldClose(handle, true);
            if (action == GLFW_PRESS && key == GLFW_KEY_R && !config.benchmark.enabled)
                recording[0] = !recording[0];
        });
        long started = System.nanoTime(), fpsStarted = started, fpsFrames = 0;
        double fps = 0;
        Matrix4f camera = new Matrix4f(), transform = new Matrix4f();
        FrustumIntersection frustum = new FrustumIntersection();
        try (var log = new FrameLog(config.outputDirectory, sources.get(0).mesh, config.animation, config.execution);
            var ground = new GrassGround(config); var sky = new MinecraftSky(config);
            var particles = new ParticleRenderer()) {
            log.sceneLabel = config.scene.type + " / " + instances.size() + " models";
            for (long frameId = 0; !glfwWindowShouldClose(window); frameId++) {
                long frameStart = System.nanoTime();
                glfwPollEvents();
                var frame = new FrameLog.Frame();
                frame.id = frameId;
                frame.timestamp = System.currentTimeMillis();
                frame.focused = glfwGetWindowAttrib(window, GLFW_FOCUSED) == GLFW_TRUE;
                frame.record = recording[0];
                frame.configured = instances.size();
                frame.visible = 0;
                frame.submitted = 0;
                long sampleFrame = frameId - config.benchmark.warmupFrames;
                frame.sample = !config.benchmark.enabled || sampleFrame >= 0;
                frame.round = config.benchmark.enabled && sampleFrame >= 0
                    ? (int) (sampleFrame / config.benchmark.sampleFrames)
                    : -1;
                float time = config.benchmark.enabled
                    ? (sampleFrame < 0 ? frameId : sampleFrame % config.benchmark.sampleFrames) / 60f
                    : (System.nanoTime() - started) / 1e9f;
                frame.animationTime = config.animation.fixedTimeSeconds == null
                    ? time * config.animation.speed + config.animation.timeOffsetSeconds
                    : config.animation.fixedTimeSeconds;
                int width, height;
                try (var stack = MemoryStack.stackPush()) {
                    IntBuffer w = stack.mallocInt(1), h = stack.mallocInt(1);
                    glfwGetFramebufferSize(window, w, h);
                    width = w.get(0);
                    height = h.get(0);
                }
                if (width == 0 || height == 0)
                    throw new IllegalStateException("Window minimized during multi-model measurement");
                float sceneRadius = modelRadius * 34;
                float distance = sceneRadius * config.camera.distanceScale * Math.max(1f, (float) height / width);
                float yaw = (float) Math.toRadians(config.camera.yawDegrees);
                float pitch = (float) Math.toRadians(config.camera.pitchDegrees);
                camera.identity()
                    .perspective((float) Math.toRadians(config.camera.fieldOfViewDegrees), (float) width / height,
                        Math.max(0.001f, modelRadius / 100), distance + sceneRadius * 4)
                    .lookAt((float) Math.sin(yaw) * (float) Math.cos(pitch) * distance,
                        modelRadius + (float) Math.sin(pitch) * distance,
                        -(float) Math.cos(yaw) * (float) Math.cos(pitch) * distance, 0, modelRadius, 0, 0, 1, 0);
                frustum.set(camera);
                for (var instance : instances) {
                    if (instance.state == null)
                        continue;
                    long start = System.nanoTime();
                    instance.state.sample(frameId, animationTime(instance, time));
                    frame.animationMs += (System.nanoTime() - start) / 1e6;
                    start = System.nanoTime();
                    instance.state.updateBonesForRender(instance.source.renderer.nativeVertices);
                    frame.bonesMs = Math.max(0, frame.bonesMs) + (System.nanoTime() - start) / 1e6;
                }
                glViewport(0, 0, width, height);
                glClearColor(0.055f, 0.07f, 0.1f, 1);
                glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);
                glEnable(GL_DEPTH_TEST);
                long start = System.nanoTime();
                frame.skyQuery = config.sky.enabled ? log.beginQuery() : 0;
                frame.skyDrawCalls = sky.draw(camera);
                if (frame.skyQuery != 0)
                    glEndQuery(GL_TIME_ELAPSED);
                frame.skySubmitMs = (System.nanoTime() - start) / 1e6;
                start = System.nanoTime();
                frame.groundQuery = config.ground.enabled ? log.beginQuery() : 0;
                frame.groundDrawCalls = ground.draw(camera, 0);
                if (frame.groundQuery != 0)
                    glEndQuery(GL_TIME_ELAPSED);
                frame.groundSubmitMs = (System.nanoTime() - start) / 1e6;
                for (var instance : instances) {
                    start = System.nanoTime();
                    boolean visible = instance.source.renderer.visible(frustum, instance.placement, instance.state);
                    frame.bonesMs = Math.max(0, frame.bonesMs) + (System.nanoTime() - start) / 1e6;
                    if (!visible)
                        continue;
                    frame.visible++;
                    transform.set(camera).mul(instance.placement);
                    if (instance.source.renderer.draw(program, transform, instance.state, log, frame) > 0)
                        frame.submitted++;
                }
                for (var instance : instances) {
                    var host = instance.state == null ? null : instance.state.particles();
                    if (host == null || host.active().isEmpty())
                        continue;
                    if (frame.particleQuery == 0)
                        frame.particleQuery = log.beginQuery();
                    transform.set(camera).mul(instance.placement);
                    start = System.nanoTime();
                    var draws = particles.draw(host, transform,
                        animationTime(instance, time) * 20 - (float) Math.floor(animationTime(instance, time) * 20));
                    frame.particleDrawCalls += draws;
                    frame.particles += particles.submitted;
                    frame.particleSubmitMs += (System.nanoTime() - start) / 1e6;
                }
                if (frame.particleQuery != 0)
                    glEndQuery(GL_TIME_ELAPSED);
                for (var instance : instances) {
                    if (instance.state != null) {
                        start = System.nanoTime();
                        instance.state.postRender(animationTime(instance, time));
                        frame.animationMs += (System.nanoTime() - start) / 1e6;
                    }
                }
                for (var source : sources)
                    for (var animation : source.animations.values()) animation.audio.update();
                PreviewMain.drawHud(
                    String.format(Locale.ROOT,
                        "FPS %.1f  %s %s THREADS %d\nMODELS %d  VISIBLE %d  SUBMITTED %d  DRAW %d\n%s  RECORD %s", fps,
                        config.execution.implementation, config.execution.path, config.execution.threads,
                        frame.configured, frame.visible, frame.submitted, frame.drawCalls, config.scene.type,
                        recording[0]),
                    width, height, hud, hudVao, hudVbo, hudEbo, glyphs, indices);
                start = System.nanoTime();
                glfwSwapBuffers(window);
                frame.presentMs = (System.nanoTime() - start) / 1e6;
                log.poll();
                frame.peakRssBytes = log.peakRssBytes();
                frame.frameMs = (System.nanoTime() - frameStart) / 1e6;
                frame.focused &= glfwGetWindowAttrib(window, GLFW_FOCUSED) == GLFW_TRUE;
                log.enqueue(frame);
                long now = System.nanoTime();
                fpsFrames++;
                if (now - fpsStarted >= 500000000L) {
                    fps = fpsFrames * 1e9 / (now - fpsStarted);
                    fpsFrames = 0;
                    fpsStarted = now;
                    glfwSetWindowTitle(
                        window, config.window.title + " | " + String.format(Locale.ROOT, "%.1f FPS", fps));
                }
                int limit = config.benchmark.enabled
                    ? config.benchmark.warmupFrames + config.benchmark.sampleFrames * config.benchmark.rounds
                    : config.benchmark.exitAfterFrames;
                if (limit > 0 && frameId + 1 >= limit)
                    glfwSetWindowShouldClose(window, true);
            }
            log.drain();
            log.summary();
        } finally {
            glDeleteProgram(program);
            glDeleteProgram(hud);
            glDeleteVertexArrays(hudVao);
            glDeleteBuffers(new int[] {hudVbo, hudEbo});
            MemoryUtil.memFree(glyphs);
            MemoryUtil.memFree(indices);
        }
        var snapshots = new ArrayList<Map<String, Object>>();
        for (int index = 0; index < instances.size(); index++) {
            var instance = instances.get(index);
            var matrices = new ArrayList<float[]>();
            if (instance.state != null)
                for (var matrix : instance.state.paletteMatrices()) matrices.add(matrix.get(new float[16]));
            snapshots.add(Map.of("instance", index, "palette", matrices));
        }
        Files.writeString(
            config.outputDirectory.resolve("instance-palettes.json"), PreviewConfig.JSON.toJson(snapshots));
    }
}
