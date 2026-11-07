package top.aihmc.omm.preview;

import static org.lwjgl.glfw.Callbacks.glfwFreeCallbacks;
import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL33C.*;
import static org.lwjgl.stb.STBEasyFont.stb_easy_font_print;

import com.google.gson.JsonParser;
import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.Locale;
import org.joml.Matrix4f;
import org.lwjgl.glfw.GLFWErrorCallback;
import org.lwjgl.opengl.GL;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;

/** Visible-window bootstrap; pending paths fail explicitly instead of producing misleading benchmark data. */
public final class PreviewMain {
    static final String CAMERA_RAY_VERTEX = """
        #version 330 core
        uniform mat4 inverseCamera;
        out vec3 nearWorld;
        out vec3 farWorld;
        void main() {
            vec2 corner = vec2((gl_VertexID << 1) & 2, gl_VertexID & 2);
            vec2 clip = corner * 2.0 - 1.0;
            vec4 nearPoint = inverseCamera * vec4(clip, -1.0, 1.0);
            vec4 farPoint = inverseCamera * vec4(clip, 1.0, 1.0);
            nearWorld = nearPoint.xyz / nearPoint.w;
            farWorld = farPoint.xyz / farPoint.w;
            gl_Position = vec4(clip, 0.0, 1.0);
        }
        """;
    static final String MODEL_VERTEX = """
                #version 330 core
                layout(location=0) in vec3 position;
                layout(location=1) in vec2 uv;
                layout(location=2) in uint part;
                layout(location=3) in vec3 bindPosition;
                uniform bool cpuPrepared;
                uniform mat4 transform;
                uniform samplerBuffer boneMatrices;
                out vec3 color;
                out vec2 textureUv;
                void main() {
                    int first = int(part) * 4;
                    mat4 bone = mat4(texelFetch(boneMatrices, first), texelFetch(boneMatrices, first+1),
                        texelFetch(boneMatrices, first+2), texelFetch(boneMatrices, first+3));
                    color = bindPosition * 0.45 + 0.55;
                    textureUv = uv;
                    gl_Position = transform * (cpuPrepared ? vec4(position,1) : bone * vec4(position,1));
                }
                """;
    static final String MODEL_FRAGMENT = """
                #version 330 core
                in vec3 color; in vec2 textureUv; out vec4 pixel;
                uniform sampler2D modelTexture;
                uniform bool textured;
                void main() {
                    pixel = textured ? texture(modelTexture, textureUv) : vec4(color,1);
                    if (pixel.a < 0.1) discard;
                }
                """;
    private static final String HELP = """
            omm-core standalone preview (Java 25+, actual Windows GLFW/OpenGL window)
              java -jar preview.jar --config scene.json [--frames N] [--state name=JSON] [--no-record]
              java -jar preview.jar --help
            Config paths are relative to the configuration file; CLI overrides its values.
            Escape: close. R: toggle frame recording outside benchmark mode.
            Current paths: Java/native CPU/GPU cube, YSM folder/ZIP/encrypted .ysm and Figura folder/ZIP bind-pose preview.
            YSM named clip or declared-controller Molang playback is available through animation config.
            Optional scripts.enabled uses actual Figura LuaJ and script-defined avatar_controls with YSM playback.
            interaction.enabled opens the model wheel/settings window; F1 toggles it. Inputs are recorded/replayable.
            animation.actions enables YSM model schemas, default wheel, forms and actual declared action playback.
            animation.mode=figura with scripts.enabled connects Lua model-part transforms to the actual palette.
            YSM named functions, args and controller stage events execute in isolated Molang hosts.
            scene.type=single/same-model/mixed supports up to 100 models. Host states come from configuration.
            """;

    private PreviewMain() {}

    public static void main(String[] args) {
        int exitCode;
        try {
            run(args);
            exitCode = 0;
        } catch (Exception failure) {
            failure.printStackTrace(System.err);
            exitCode = 1;
        }
        // This standalone executable owns its process. All resource cleanup and final writes
        // finish inside run() before requesting an orderly JVM shutdown, including its hooks.
        // Disposing the last Swing window alone does not reliably stop the Windows AWT toolkit.
        System.exit(exitCode);
    }

    static void run(String[] args) throws Exception {
        if (Arrays.asList(args).contains("--help")) {
            System.out.print(HELP);
            return;
        }
        PreviewConfig config = PreviewConfig.load(args);
        if (!config.scene.type.equals("single")) {
            PreviewMultiWindow.run(config);
            return;
        }
        if (config.execution.implementation.equals("java") && config.execution.threads != 1 || config.scene.count != 1
            || config.scene.models.size() != 1) {
            throw new UnsupportedOperationException("Supported paths: Java CPU/GPU or native CPU/GPU, one model; "
                + "multi-model is not connected yet.");
        }
        PreviewMesh mesh = PreviewMesh.load(config);
        PreviewAnimation animation = config.animation.enabled ? new PreviewAnimation(mesh, config) : null;
        String runId = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss-SSS").format(LocalDateTime.now()) + "-"
            + ProcessHandle.current().pid();
        config.outputDirectory = config.outputDirectory.resolve(runId);
        Files.createDirectories(config.outputDirectory);
        Files.writeString(config.outputDirectory.resolve("parse.json"), PreviewConfig.JSON.toJson(mesh.parserInfo));
        var resolved = PreviewConfig.JSON.toJsonTree(config).getAsJsonObject();
        resolved.addProperty("configSource", config.configFile.toString());
        resolved.addProperty("runOutputDirectory", config.outputDirectory.toString());
        Files.writeString(config.outputDirectory.resolve("resolved-config.json"), PreviewConfig.JSON.toJson(resolved),
            StandardCharsets.UTF_8);
        Files.writeString(config.outputDirectory.resolve("model.txt"),
            mesh.label + "\nvertices=" + mesh.vertices.length / 5 + "\nindices=" + mesh.indices.length + "\nmode="
                + (mesh.fixture             ? "procedural fixture"
                        : animation == null ? "static bind pose; scripts/animation not executed"
                                            : animation.description())
                + "\n" + (mesh.vanilla == null ? "" : mesh.vanilla.description() + "\n"),
            StandardCharsets.UTF_8);
        GLFWErrorCallback errors = GLFWErrorCallback.createPrint(System.err).set();
        if (!glfwInit()) {
            errors.free();
            throw new IllegalStateException("GLFW initialization failed; inspect the Windows graphics/runtime log");
        }
        long window = 0;
        PreviewInteraction interaction = null;
        try {
            glfwWindowHint(GLFW_CONTEXT_VERSION_MAJOR, 3);
            glfwWindowHint(GLFW_CONTEXT_VERSION_MINOR, 3);
            glfwWindowHint(GLFW_OPENGL_PROFILE, GLFW_OPENGL_CORE_PROFILE);
            glfwWindowHint(GLFW_VISIBLE, GLFW_TRUE);
            window = glfwCreateWindow(config.window.width, config.window.height, config.window.title, 0, 0);
            if (window == 0) {
                throw new IllegalStateException("Could not create a visible OpenGL 3.3 window");
            }
            glfwMakeContextCurrent(window);
            GL.createCapabilities();
            glfwSwapInterval(config.window.vsync);
            System.out.println("OpenGL: " + glGetString(GL_VERSION) + " / " + glGetString(GL_RENDERER));
            Files.writeString(config.outputDirectory.resolve("graphics.txt"),
                glGetString(GL_VERSION) + "\n" + glGetString(GL_RENDERER) + "\nVSync=" + config.window.vsync + "\n",
                StandardCharsets.UTF_8);
            interaction = new PreviewInteraction(config);
            loop(window, config, mesh, animation, interaction);
        } finally {
            try {
                if (window != 0) {
                    glfwFreeCallbacks(window);
                    glfwDestroyWindow(window);
                }
                glfwTerminate();
                errors.free();
            } finally {
                // Release input while model callbacks can still save settings and use their audio host.
                try {
                    if (interaction != null)
                        interaction.close();
                } finally {
                    if (animation != null) {
                        animation.close();
                        Files.writeString(config.outputDirectory.resolve("audio.json"),
                            PreviewConfig.JSON.toJson(animation.audio.snapshot()), StandardCharsets.UTF_8);
                        Files.writeString(config.outputDirectory.resolve("settings.json"),
                            PreviewConfig.JSON.toJson(animation.settings.snapshot()), StandardCharsets.UTF_8);
                    }
                }
            }
        }
        System.out.println("Preview output: " + config.outputDirectory);
    }

    static int program(String vertex, String fragment) {
        int vs = shader(GL_VERTEX_SHADER, vertex);
        int fs = shader(GL_FRAGMENT_SHADER, fragment);
        int program = glCreateProgram();
        glAttachShader(program, vs);
        glAttachShader(program, fs);
        glLinkProgram(program);
        glDeleteShader(vs);
        glDeleteShader(fs);
        if (glGetProgrami(program, GL_LINK_STATUS) == GL_FALSE) {
            String message = glGetProgramInfoLog(program);
            glDeleteProgram(program);
            throw new IllegalStateException(message);
        }
        return program;
    }

    private static int shader(int kind, String source) {
        int shader = glCreateShader(kind);
        glShaderSource(shader, source);
        glCompileShader(shader);
        if (glGetShaderi(shader, GL_COMPILE_STATUS) == GL_FALSE) {
            String message = glGetShaderInfoLog(shader);
            glDeleteShader(shader);
            throw new IllegalStateException(message);
        }
        return shader;
    }

    private static void loop(long window, PreviewConfig config, PreviewMesh mesh, PreviewAnimation animation,
        PreviewInteraction interaction) throws Exception {
        boolean cpu = config.execution.path.equals("cpu");
        int sceneProgram = program(MODEL_VERTEX, MODEL_FRAGMENT);
        int hudProgram = program("""
                #version 330 core
                layout(location=0) in vec3 position;
                uniform mat4 transform;
                void main() { gl_Position = transform * vec4(position,1); }
                """, """
                #version 330 core
                out vec4 pixel; void main() { pixel=vec4(0.95,0.96,1,1); }
                """);
        int[] textures = mesh.textures.stream().mapToInt(PreviewMain::uploadTexture).toArray();
        int sceneVao = glGenVertexArrays(), sceneVbo = glGenBuffers(), sceneEbo = glGenBuffers();
        glBindVertexArray(sceneVao);
        glBindBuffer(GL_ARRAY_BUFFER, sceneVbo);
        glBufferData(GL_ARRAY_BUFFER, mesh.vertices, cpu ? GL_STREAM_DRAW : GL_STATIC_DRAW);
        glEnableVertexAttribArray(0);
        glVertexAttribPointer(0, 3, GL_FLOAT, false, 20, 0);
        glEnableVertexAttribArray(1);
        glVertexAttribPointer(1, 2, GL_FLOAT, false, 20, 12);
        // Preserve procedural fixture colors from bind positions on both paths.
        int bindVbo = 0;
        if (cpu) {
            bindVbo = glGenBuffers();
            glBindBuffer(GL_ARRAY_BUFFER, bindVbo);
            glBufferData(GL_ARRAY_BUFFER, mesh.vertices, GL_STATIC_DRAW);
        }
        glEnableVertexAttribArray(3);
        glVertexAttribPointer(3, 3, GL_FLOAT, false, 20, 0);
        glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, sceneEbo);
        glBufferData(GL_ELEMENT_ARRAY_BUFFER, mesh.indices, GL_STATIC_DRAW);
        int ownersVbo = glGenBuffers();
        glBindBuffer(GL_ARRAY_BUFFER, ownersVbo);
        glBufferData(GL_ARRAY_BUFFER, mesh.owners, GL_STATIC_DRAW);
        glEnableVertexAttribArray(2);
        glVertexAttribIPointer(2, 1, GL_UNSIGNED_INT, 4, 0);
        int boneCount = mesh.skeleton == null ? 1 : mesh.skeleton.names.length;
        if ((long) boneCount * 4 > glGetInteger(GL_MAX_TEXTURE_BUFFER_SIZE)) {
            throw new UnsupportedOperationException("Skeleton exceeds the GPU texture-buffer palette capacity");
        }
        FloatBuffer paletteValues = MemoryUtil.memAllocFloat(boneCount * 16);
        FloatBuffer cpuVertices = cpu ? MemoryUtil.memAllocFloat(mesh.vertices.length) : null;
        boolean[] initialVisible = mesh.skeleton == null
            ? null
            : YsmVisibilityDefaults.visibility(mesh, new PreviewMolang(config.states, config.scene.seed), true);
        for (int bone = 0; bone < boneCount; bone++) {
            Matrix4f bind = new Matrix4f();
            if (initialVisible != null && !initialVisible[bone]) {
                bind.scaling(0);
            }
            bind.get(bone * 16, paletteValues);
        }
        int paletteBuffer = glGenBuffers(), paletteTexture = glGenTextures();
        glBindBuffer(GL_TEXTURE_BUFFER, paletteBuffer);
        glBufferData(GL_TEXTURE_BUFFER, paletteValues, GL_DYNAMIC_DRAW);
        glBindTexture(GL_TEXTURE_BUFFER, paletteTexture);
        glTexBuffer(GL_TEXTURE_BUFFER, GL_RGBA32F, paletteBuffer);
        PreviewAnimation.State animationState = animation == null ? null : animation.newState();
        int hudVao = glGenVertexArrays(), hudVbo = glGenBuffers(), hudEbo = glGenBuffers();
        ByteBuffer glyphs = MemoryUtil.memAlloc(262144);
        IntBuffer glyphIndices = MemoryUtil.memAllocInt(24576);
        glBindVertexArray(hudVao);
        glBindBuffer(GL_ARRAY_BUFFER, hudVbo);
        glBufferData(GL_ARRAY_BUFFER, glyphs.capacity(), GL_STREAM_DRAW);
        glEnableVertexAttribArray(0);
        glVertexAttribPointer(0, 3, GL_FLOAT, false, 16, 0);
        glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, hudEbo);
        glBufferData(GL_ELEMENT_ARRAY_BUFFER, (long) glyphIndices.capacity() * 4, GL_STREAM_DRAW);
        boolean[] recording = {config.recording.enabled || config.benchmark.enabled};
        long started = System.nanoTime(), fpsStart = started;
        long frameId = 0, fpsFrames = 0;
        double fps = 0, previousFrameMs = 0, gpuMs = -1;
        Matrix4f transform = new Matrix4f();
        Matrix4f camera = new Matrix4f();
        try (PreviewNativeVertices nativeVertices =
                 config.execution.implementation.equals("native") ? new PreviewNativeVertices(mesh, config) : null;
            PreviewNativeDraw nativeDraw = !cpu&& nativeVertices != null ? new PreviewNativeDraw(mesh) : null;
            FrameLog log = new FrameLog(config.outputDirectory, mesh, config.animation, config.execution);
            GrassGround ground = new GrassGround(config); MinecraftSky sky = new MinecraftSky(config);
            ParticleRenderer particles = new ParticleRenderer()) {
            if (nativeVertices != null) {
                config.execution.threads = nativeVertices.threads;
                var resolved =
                    JsonParser.parseString(Files.readString(config.outputDirectory.resolve("resolved-config.json")))
                        .getAsJsonObject();
                resolved.add("execution", PreviewConfig.JSON.toJsonTree(config.execution));
                Files.writeString(
                    config.outputDirectory.resolve("resolved-config.json"), PreviewConfig.JSON.toJson(resolved));
                Files.writeString(config.outputDirectory.resolve("native-build.txt"), nativeVertices.buildId);
            }
            glfwSetKeyCallback(window, (handle, key, scancode, action, mods) -> {
                interaction.keyInput(PreviewKeys.keyboardName(key, scancode), scancode, action != GLFW_RELEASE, mods,
                    action == GLFW_REPEAT);
                if (action == GLFW_PRESS && key == GLFW_KEY_ESCAPE) {
                    glfwSetWindowShouldClose(handle, true);
                }
                if (action == GLFW_PRESS && key == GLFW_KEY_R && !config.benchmark.enabled) {
                    recording[0] = !recording[0];
                    System.out.println("Frame recording: " + recording[0]);
                }
                if (action == GLFW_PRESS && key == GLFW_KEY_F1) {
                    interaction.toggleWindow();
                }
            });
            glfwSetMouseButtonCallback(window,
                (handle, button, action,
                    mods) -> interaction.keyInput(PreviewKeys.mouseName(button), -1, action != GLFW_RELEASE, mods));
            glfwSetWindowFocusCallback(window, (handle, focused) -> {
                interaction.inputReady(focused);
                if (!focused)
                    interaction.releaseKeys();
            });

            while (!glfwWindowShouldClose(window)) {
                long frameStart = System.nanoTime();
                glfwPollEvents();
                FrameLog.Frame frame = new FrameLog.Frame();
                frame.id = frameId;
                frame.timestamp = System.currentTimeMillis();
                frame.focused = glfwGetWindowAttrib(window, GLFW_FOCUSED) == GLFW_TRUE;
                frame.sample = !config.benchmark.enabled || frameId >= config.benchmark.warmupFrames;
                frame.record = recording[0];
                int width, height;
                try (MemoryStack stack = MemoryStack.stackPush()) {
                    IntBuffer w = stack.mallocInt(1), h = stack.mallocInt(1);
                    glfwGetFramebufferSize(window, w, h);
                    width = w.get(0);
                    height = h.get(0);
                }
                if (width == 0 || height == 0) {
                    throw new IllegalStateException("Window minimized during measured rendering; run invalidated");
                }
                long animationStart = System.nanoTime();
                long sampleFrame = frameId - config.benchmark.warmupFrames;
                float time = config.benchmark.enabled
                    ? (sampleFrame < 0 ? frameId : sampleFrame % config.benchmark.sampleFrames) / 60f
                    : (System.nanoTime() - started) / 1e9f;
                if (mesh.fixture && !config.running()) {
                    time = 0;
                }
                if (!mesh.fixture && animation == null) {
                    time = 0;
                }
                if (animation != null) {
                    time = config.animation.fixedTimeSeconds == null
                        ? time * config.animation.speed + config.animation.timeOffsetSeconds
                        : config.animation.fixedTimeSeconds;
                    time = Math.max(0, time);
                }
                frame.animationTime = time;
                frame.round = config.benchmark.enabled && sampleFrame >= 0
                    ? (int) (sampleFrame / config.benchmark.sampleFrames)
                    : -1;
                float distance = mesh.radius * config.camera.distanceScale * Math.max(1f, (float) height / width);
                float yaw = (float) Math.toRadians(config.camera.yawDegrees);
                float pitch = (float) Math.toRadians(config.camera.pitchDegrees);
                camera.identity()
                    .perspective((float) Math.toRadians(config.camera.fieldOfViewDegrees), (float) width / height,
                        Math.max(0.001f, mesh.radius / 100f), distance + mesh.radius * 4f)
                    .lookAt((float) Math.sin(yaw) * (float) Math.cos(pitch) * distance,
                        (float) Math.sin(pitch) * distance, -(float) Math.cos(yaw) * (float) Math.cos(pitch) * distance,
                        0, 0, 0, 0, 1, 0);
                transform.set(camera);
                if (mesh.fixture) {
                    transform.rotateY(time * 0.5f).rotateX(time * 0.2f);
                }
                transform.translate(-mesh.center.x, -mesh.center.y, -mesh.center.z);
                frame.animationMs = (System.nanoTime() - animationStart) / 1e6;
                FloatBuffer framePalette = paletteValues;
                if (animationState != null) {
                    long sampleStart = System.nanoTime();
                    animation.audio.listener(mesh.center.x + (float) Math.sin(yaw) * (float) Math.cos(pitch) * distance,
                        mesh.center.y + (float) Math.sin(pitch) * distance,
                        mesh.center.z - (float) Math.cos(yaw) * (float) Math.cos(pitch) * distance,
                        -(float) Math.sin(yaw) * (float) Math.cos(pitch), -(float) Math.sin(pitch),
                        (float) Math.cos(yaw) * (float) Math.cos(pitch));
                    interaction.update(animationState.lua(), animationState.molang(), frameId, time);
                    animationState.sample(frameId, time);
                    frame.animationMs = (System.nanoTime() - sampleStart) / 1e6;
                    long bonesStart = System.nanoTime();
                    animationState.updateBonesForRender(nativeVertices);
                    framePalette = animationState.paletteBuffer(paletteValues);
                    frame.bonesMs = (System.nanoTime() - bonesStart) / 1e6;
                }
                if (cpu) {
                    long verticesStart = System.nanoTime();
                    if (nativeVertices == null)
                        PreviewCpuVertices.transform(mesh.vertices, mesh.owners, framePalette, cpuVertices);
                    else
                        nativeVertices.transform(framePalette, cpuVertices);
                    frame.verticesMs = (System.nanoTime() - verticesStart) / 1e6;
                }
                glViewport(0, 0, width, height);
                glClearColor(0.055f, 0.07f, 0.1f, 1);
                glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);
                glEnable(GL_DEPTH_TEST);
                long skyStart = System.nanoTime();
                frame.skyQuery = config.sky.enabled ? log.beginQuery() : 0;
                frame.skyDrawCalls = sky.draw(camera);
                if (frame.skyQuery != 0) {
                    glEndQuery(GL_TIME_ELAPSED);
                }
                frame.skySubmitMs = (System.nanoTime() - skyStart) / 1e6;
                long groundStart = System.nanoTime();
                frame.groundQuery = config.ground.enabled ? log.beginQuery() : 0;
                frame.groundDrawCalls =
                    ground.draw(camera, mesh.fixture ? -mesh.radius : mesh.minimumY - mesh.center.y);
                if (frame.groundQuery != 0) {
                    glEndQuery(GL_TIME_ELAPSED);
                }
                frame.groundSubmitMs = (System.nanoTime() - groundStart) / 1e6;
                long submitStart = System.nanoTime();
                glUseProgram(sceneProgram);
                glUniform1i(glGetUniformLocation(sceneProgram, "cpuPrepared"), cpu ? 1 : 0);
                if (cpu) {
                    glBindBuffer(GL_ARRAY_BUFFER, sceneVbo);
                    glBufferSubData(GL_ARRAY_BUFFER, 0, cpuVertices);
                }
                glActiveTexture(GL_TEXTURE3);
                glBindTexture(GL_TEXTURE_BUFFER, paletteTexture);
                glUniform1i(glGetUniformLocation(sceneProgram, "boneMatrices"), 3);
                if (!cpu && animationState != null) {
                    glBindBuffer(GL_TEXTURE_BUFFER, paletteBuffer);
                    glBufferSubData(GL_TEXTURE_BUFFER, 0, framePalette);
                }
                glActiveTexture(GL_TEXTURE0);
                glUniform1i(glGetUniformLocation(sceneProgram, "modelTexture"), 0);
                try (MemoryStack stack = MemoryStack.stackPush()) {
                    FloatBuffer values = stack.mallocFloat(16);
                    glUniformMatrix4fv(glGetUniformLocation(sceneProgram, "transform"), false, transform.get(values));
                }
                glBindVertexArray(sceneVao);
                if (animationState != null)
                    animationState.recordRenderedPivots();
                frame.query = log.beginQuery();
                if (nativeDraw != null) {
                    frame.nativeDrawCalls =
                        nativeDraw.draw(sceneVao, glGetUniformLocation(sceneProgram, "textured"), mesh, textures,
                            animationState == null || animationState.lua() == null
                                || animationState.lua().vanillaVisible());
                    frame.drawCalls = frame.nativeDrawCalls;
                } else {
                    for (PreviewMesh.DrawRange draw : mesh.draws) {
                        if (mesh.vanilla != null && mesh.vanilla.owns(draw) && animationState != null
                            && !animationState.lua().vanillaVisible())
                            continue;
                        boolean textured = draw.textureIndex() >= 0;
                        glBindTexture(GL_TEXTURE_2D, textured ? textures[draw.textureIndex()] : 0);
                        glUniform1i(glGetUniformLocation(sceneProgram, "textured"), textured ? 1 : 0);
                        glDrawElements(GL_TRIANGLES, draw.indexCount(), GL_UNSIGNED_INT, (long) draw.firstIndex() * 4);
                        frame.drawCalls++;
                    }
                }
                if (frame.query != 0) {
                    glEndQuery(GL_TIME_ELAPSED);
                }
                frame.submitMs = (System.nanoTime() - submitStart) / 1e6;
                long particleStart = System.nanoTime();
                var particleHost = animationState == null ? null : animationState.particles();
                if (particleHost != null && !particleHost.active().isEmpty()) {
                    frame.particleQuery = log.beginQuery();
                    frame.particleDrawCalls =
                        particles.draw(particleHost, transform, time * 20 - (float) Math.floor(time * 20));
                    if (frame.particleQuery != 0)
                        glEndQuery(GL_TIME_ELAPSED);
                    frame.particles = particles.submitted;
                }
                frame.particleSubmitMs = (System.nanoTime() - particleStart) / 1e6;
                if (animationState != null) {
                    long scriptStart = System.nanoTime();
                    animationState.postRender(time);
                    animation.audio.update();
                    frame.animationMs += (System.nanoTime() - scriptStart) / 1e6;
                }
                String hud = String.format(Locale.ROOT,
                    "FPS %.1f  FRAME %.2f ms  MODEL GPU %s\n%s %s  THREADS %d  VISIBLE 1/1  MODEL DRAW %d  GROUND "
                        + "%d  SKY %d\n%s  "
                        + "RECORD %s  ESC CLOSE / R RECORD",
                    fps, previousFrameMs, gpuMs < 0 ? "pending" : String.format(Locale.ROOT, "%.3f ms", gpuMs),
                    config.execution.implementation.toUpperCase(Locale.ROOT),
                    config.execution.path.toUpperCase(Locale.ROOT), config.execution.threads, frame.drawCalls,
                    frame.groundDrawCalls, frame.skyDrawCalls,
                    mesh.fixture            ? "BUILT-IN FIXTURE"
                        : animation == null ? "STATIC ASSET / NO SCRIPT PLAYBACK"
                                            : animation.description(),
                    recording[0]);
                drawHud(hud, width, height, hudProgram, hudVao, hudVbo, hudEbo, glyphs, glyphIndices);
                long presentStart = System.nanoTime();
                glfwSwapBuffers(window);
                frame.presentMs = (System.nanoTime() - presentStart) / 1e6;
                double latest = log.poll();
                if (latest >= 0) {
                    gpuMs = latest;
                }
                frame.peakRssBytes = log.peakRssBytes();
                frame.frameMs = (System.nanoTime() - frameStart) / 1e6;
                previousFrameMs = frame.frameMs;
                frame.focused &= glfwGetWindowAttrib(window, GLFW_FOCUSED) == GLFW_TRUE;
                log.enqueue(frame);
                frameId++;
                fpsFrames++;
                long now = System.nanoTime();
                if (now - fpsStart >= 500000000L) {
                    fps = fpsFrames * 1e9 / (now - fpsStart);
                    fpsStart = now;
                    fpsFrames = 0;
                    glfwSetWindowTitle(window,
                        String.format(Locale.ROOT, "%s | %.1f FPS | %s", config.window.title, fps,
                            mesh.fixture            ? "Java " + config.execution.path + " fixture"
                                : animation == null ? "Asset bind pose"
                                                    : animation.description()));
                }
                int limit = config.benchmark.enabled
                    ? config.benchmark.warmupFrames + config.benchmark.sampleFrames * config.benchmark.rounds
                    : config.benchmark.exitAfterFrames;
                if (limit > 0 && frameId >= limit) {
                    glfwSetWindowShouldClose(window, true);
                }
            }
            log.drain();
            log.summary();
        } finally {
            MemoryUtil.memFree(glyphs);
            MemoryUtil.memFree(glyphIndices);
            MemoryUtil.memFree(paletteValues);
            if (cpuVertices != null)
                MemoryUtil.memFree(cpuVertices);
            if (bindVbo != 0)
                glDeleteBuffers(bindVbo);
            glDeleteBuffers(new int[] {sceneVbo, sceneEbo, ownersVbo, paletteBuffer, hudVbo, hudEbo});
            glDeleteVertexArrays(new int[] {sceneVao, hudVao});
            glDeleteProgram(sceneProgram);
            glDeleteProgram(hudProgram);
            glDeleteTextures(textures);
            glDeleteTextures(paletteTexture);
        }
    }

    static int uploadTexture(java.awt.image.BufferedImage image) {
        int width = image.getWidth(), height = image.getHeight();
        ByteBuffer pixels = MemoryUtil.memAlloc(Math.multiplyExact(Math.multiplyExact(width, height), 4));
        try {
            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++) {
                    int argb = image.getRGB(x, y);
                    pixels.put((byte) (argb >> 16)).put((byte) (argb >> 8)).put((byte) argb).put((byte) (argb >> 24));
                }
            }
            pixels.flip();
            int texture = glGenTextures();
            glBindTexture(GL_TEXTURE_2D, texture);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
            glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA8, width, height, 0, GL_RGBA, GL_UNSIGNED_BYTE, pixels);
            return texture;
        } finally {
            MemoryUtil.memFree(pixels);
        }
    }

    static void drawHud(String text, int width, int height, int program, int vao, int vbo, int ebo, ByteBuffer glyphs,
        IntBuffer indices) {
        // Keep the FPS and path readable against bright sky/cloud textures.
        glEnable(GL_SCISSOR_TEST);
        glScissor(0, Math.max(0, height - 88), width, 88);
        glClearColor(0.02f, 0.025f, 0.03f, 1);
        glClear(GL_COLOR_BUFFER_BIT);
        glDisable(GL_SCISSOR_TEST);
        glyphs.clear();
        int quads = stb_easy_font_print(6, 6, text, null, glyphs);
        indices.clear();
        for (int quad = 0; quad < quads; quad++) {
            indices.put(quad * 4).put(quad * 4 + 1).put(quad * 4 + 2);
            indices.put(quad * 4).put(quad * 4 + 2).put(quad * 4 + 3);
        }
        indices.flip();
        glyphs.limit(quads * 4 * 16);
        glDisable(GL_DEPTH_TEST);
        glUseProgram(program);
        try (MemoryStack stack = MemoryStack.stackPush()) {
            FloatBuffer values = stack.mallocFloat(16);
            new Matrix4f().ortho2D(0, width / 2f, height / 2f, 0).get(values);
            glUniformMatrix4fv(glGetUniformLocation(program, "transform"), false, values);
        }
        glBindVertexArray(vao);
        glBindBuffer(GL_ARRAY_BUFFER, vbo);
        glBufferSubData(GL_ARRAY_BUFFER, 0, glyphs);
        glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, ebo);
        glBufferSubData(GL_ELEMENT_ARRAY_BUFFER, 0, indices);
        glDrawElements(GL_TRIANGLES, quads * 6, GL_UNSIGNED_INT, 0);
    }
}
