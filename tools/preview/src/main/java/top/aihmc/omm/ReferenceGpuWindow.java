package top.aihmc.omm;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Path;
import javax.imageio.ImageIO;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL;
import org.lwjgl.system.MemoryUtil;

import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL43C.*;

/** Visible host for the MOD's original GPU mesh, bone computation and shaders. */
final class ReferenceGpuWindow {
    private ReferenceGpuWindow() {
    }

    static void run(String backend, Object model, Path texturePath) throws Exception {
        run(backend, model, texturePath, null);
    }

    static void run(String backend, Object model, Path texturePath, ReferenceModelAnimation animation) throws Exception {
        var inputs = new java.util.ArrayList<ModelInstance>();
        for (SceneLayout.Placement placement : SceneLayout.read().instances()) {
            inputs.add(new ModelInstance(model, texturePath, animation, placement));
        }
        run(backend, inputs);
    }

    record ModelInstance(Object model, Path texture, ReferenceModelAnimation animation, SceneLayout.Placement placement) {
    }

    private record PreparedInstance(ModelInstance input, Object mesh, int texture,
                                    ReferenceModelAnimation animation, ReferenceCpuScene cpuFallback) {
    }

    static void run(String backend, java.util.List<ModelInstance> inputs) throws Exception {
        RenderInputs renderInputs = RenderInputs.read();
        int frameLimit = Integer.parseInt(System.getProperty("omm.preview.frames", "120"));
        if (frameLimit <= 0) {
            throw new IllegalArgumentException("Frame limit must be positive");
        }
        if (!glfwInit()) {
            throw new IllegalStateException("GLFW initialization failed");
        }
        long window = 0;
        var meshes = new java.util.IdentityHashMap<Object, Object>();
        var modelTextures = new java.util.LinkedHashMap<Path, Integer>();
        int[] textures = new int[3];
        CpuVertexPass cpuPass = null;
        SuperflatBackground background = null;
        try (var cleanup = new ResourceCleanup()) {
            try {
                glfwDefaultWindowHints();
                glfwWindowHint(GLFW_CONTEXT_VERSION_MAJOR, 4);
                glfwWindowHint(GLFW_CONTEXT_VERSION_MINOR, 3);
                glfwWindowHint(GLFW_OPENGL_PROFILE, GLFW_OPENGL_CORE_PROFILE);
                window = glfwCreateWindow(960, 640, backend + " original JAR GPU", 0, 0);
                if (window == 0) {
                    throw new IllegalStateException("OpenGL 4.3 window creation failed");
                }
                glfwMakeContextCurrent(window);
                GL.createCapabilities();
                background = new SuperflatBackground();
                Class<?> renderSystem = Class.forName("com.mojang.blaze3d.systems.RenderSystem");
                if (!(boolean) renderSystem.getMethod("isOnRenderThread").invoke(null)) {
                    renderSystem.getMethod("initRenderThread").invoke(null);
                }
                glfwSwapInterval(1);
                String prefix = backend.equals("spm") ? "com.micaftic.morpher.core.gpu" : "rip.ysm.gpu";
                Class<?> shader = Class.forName(prefix + ".BoneSkinShader");
                boolean gpuCompiled = false;
                var instances = new java.util.ArrayList<PreparedInstance>();
                for (ModelInstance input : inputs) {
                    boolean cpuFallback = backend.equals("spm")
                        && (boolean) input.model().getClass().getMethod("isTranslucentTexture", int.class).invoke(input.model(), 0);
                    Object mesh = meshes.get(input.model());
                    if (!cpuFallback && mesh == null) {
                        if (!gpuCompiled) {
                            if (!(boolean) shader.getMethod("ensureCompiled").invoke(null)) {
                                throw new IllegalStateException("Original MOD shader compilation failed");
                            }
                            gpuCompiled = true;
                        }
                        mesh = Class.forName(prefix + ".GpuMeshBuilder").getMethod("build", input.model().getClass())
                            .invoke(null, input.model());
                        if (mesh == null) {
                            throw new IllegalStateException("Original MOD GPU mesh creation failed");
                        }
                        meshes.put(input.model(), mesh);
                    }
                    Integer texture = modelTextures.get(input.texture());
                    if (texture == null) {
                        texture = texture(input.texture());
                        modelTextures.put(input.texture(), texture);
                    }
                    ReferenceModelAnimation animation = cpuFallback || input.animation() == null ? null
                        : input.animation().independent(input.placement().animationOffsetSeconds());
                    ReferenceCpuScene cpuScene = null;
                    if (cpuFallback) {
                        if (cpuPass == null) {
                            cpuPass = new CpuVertexPass();
                        }
                        cpuScene = new ReferenceCpuScene(backend, input.model(), false, input.animation(),
                            new SceneLayout(0, java.util.List.of(input.placement())));
                        System.out.println("BACKEND instance=" + input.placement().index()
                            + " requested=gpu actual=java reason=original-spm-translucent-gpu-declined");
                    }
                    instances.add(new PreparedInstance(input, mesh, texture, animation, cpuScene));
                }
                // The MOD shader mixes overlay.rgb -> model.rgb using overlay.a.
                textures[1] = CpuPreviewWindow.texture(LightingTextureInputs.read("overlayTexture"));
                textures[2] = CpuPreviewWindow.texture(LightingTextureInputs.read("lightmapTexture"));
                Matrix4f root = new Matrix4f().translate(0, -0.6f, -3.5f).rotateY(0.35f);
                Matrix4f projection = new Matrix4f().perspective((float) Math.toRadians(50), 1.5f, 0.05f, 100);
                PreviewCamera configuredCamera = PreviewCamera.configured();
                if (configuredCamera != null) {
                    root = configuredCamera.view();
                    projection = configuredCamera.projection(1.5f);
                }
                if (instances.size() > 1 && instances.getFirst().animation() != null && instances.get(1).animation() != null) {
                    float[] first = instances.getFirst().animation().sample(0);
                    float[] saved = first.clone();
                    float[] second = instances.get(1).animation().sample(1);
                    if (first == second || !java.util.Arrays.equals(first, saved)) {
                        throw new IllegalStateException("Original animation instances share mutable pose storage");
                    }
                    System.out.println("ORIGINAL INSTANCE ISOLATION PASS independent pose storage");
                }
                Matrix4f cpuCamera = new Matrix4f(projection).mul(root);
                for (PreparedInstance instance : instances) {
                    if (instance.cpuFallback() != null) {
                        instance.cpuFallback().camera(cpuCamera);
                    }
                }
                int program = gpuCompiled ? (int) shader.getMethod("program").invoke(null) : 0;
                glEnable(GL_DEPTH_TEST);
                glDepthMask(true);
                glDisable(GL_BLEND);
                glViewport(0, 0, 960, 640);
                int nonBackground = 0;
                float[][] firstParameters = new float[instances.size()][];
                float maximumPoseChange = 0;
                long drawCalls = 0;
                long submittedIndices = 0;
                long cpuDrawCalls = 0;
                long cpuSubmittedIndices = 0;
                for (int frame = 0; frame < frameLimit && !glfwWindowShouldClose(window); frame++) {
                    glClearColor(0.53f, 0.75f, 1f, 1);
                    glDepthMask(true);
                    glColorMask(true, true, true, true);
                    glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);
                    background.draw(cpuCamera);
                    byte[] backgroundPixels = frame == frameLimit - 1 ? WindowPixels.capture(960, 640) : null;
                    for (int instance = 0; instance < instances.size(); instance++) {
                        PreparedInstance prepared = instances.get(instance);
                        if (prepared.cpuFallback() != null) {
                            double[] vertices = prepared.cpuFallback().sample(frame).getFirst();
                            int count = cpuPass.draw(vertices, prepared.cpuFallback().colors().getFirst(),
                                prepared.cpuFallback().lightingCoordinates().getFirst(), prepared.texture(), cpuCamera);
                            drawCalls++;
                            submittedIndices += count;
                            cpuDrawCalls++;
                            cpuSubmittedIndices += count;
                            continue;
                        }
                        ModelInstance input = prepared.input();
                        SceneLayout.Placement placement = input.placement();
                        Object model = input.model();
                        Object mesh = prepared.mesh();
                        textures[0] = prepared.texture();
                        ByteBuffer bones = (ByteBuffer) mesh.getClass().getField("perFrameBoneBuffer").get(mesh);
                        int vao = mesh.getClass().getField("vao").getInt(mesh);
                        int count = (int) mesh.getClass().getMethod("indexDrawCount", int.class).invoke(mesh, 0);
                        int offset = (int) mesh.getClass().getMethod("indexOffsetBytes", int.class).invoke(mesh, 0);
                        if (count <= 0) {
                            throw new AssertionError("Original GPU mesh has no drawable indices for instance " + instance);
                        }
                        boolean translucentPass = backend.equals("oysm")
                            && (boolean) model.getClass().getMethod("isTranslucentTexture", int.class).invoke(model, 0);
                        float[] parameters = (float[]) model.getClass().getMethod("getBoneTransformData").invoke(model);
                        Matrix4f instanceRoot = new Matrix4f(root).translate(placement.x(), placement.y(), placement.z());
                        Matrix3f normal = instanceRoot.normal(new Matrix3f());
                        if (prepared.animation() != null) {
                            parameters = prepared.animation().sample(frame);
                            if (firstParameters[instance] == null) {
                                firstParameters[instance] = parameters.clone();
                            }
                            for (int index = 0; index < parameters.length; index++) {
                                maximumPoseChange = Math.max(maximumPoseChange, Math.abs(parameters[index] - firstParameters[instance][index]));
                            }
                        }
                        bones.clear();
                        if (backend.equals("spm")) {
                            Class<?> computer = Class.forName(prefix + ".BoneMatrixComputer");
                            boolean computed = (boolean) computer.getMethod("compute", model.getClass(), Matrix4f.class,
                                Matrix3f.class, float[].class, float[].class, int.class, ByteBuffer.class)
                                .invoke(null, model, instanceRoot, normal, parameters, new float[parameters.length / 3], renderInputs.packedLight(), bones);
                            if (!computed) {
                                throw new IllegalStateException("Original bone computation declined the model");
                            }
                        } else {
                            long pointer = mesh.getClass().getField("pointer").getLong(mesh);
                            model.getClass().getMethod("nComputeBoneMatrices", long.class, float[].class, float[].class,
                                float[].class, int.class, ByteBuffer.class)
                                .invoke(null, pointer, instanceRoot.get(new float[16]), normal.get(new float[9]), parameters, renderInputs.packedLight(), bones);
                        }
                        bones.position(0);
                        bones.limit(mesh.getClass().getField("boneCount").getInt(mesh) * 144);
                        int ssbo = backend.equals("spm")
                            ? (int) mesh.getClass().getMethod("nextBoneSsbo").invoke(mesh)
                            : mesh.getClass().getField("boneSsbo").getInt(mesh);
                        glBindBuffer(GL_SHADER_STORAGE_BUFFER, ssbo);
                        if (backend.equals("spm")) {
                            glBufferData(GL_SHADER_STORAGE_BUFFER, bones, GL_STREAM_DRAW);
                        } else {
                            glBufferSubData(GL_SHADER_STORAGE_BUFFER, 0, bones);
                        }
                        glBindBufferBase(GL_SHADER_STORAGE_BUFFER, 0, ssbo);
                        glUseProgram(program);
                        glUniformMatrix4fv(location(shader, "locProj"), false,
                            projection.get(new float[16]));
                        glUniform4f(location(shader, "locColor"), renderInputs.tint().x, renderInputs.tint().y,
                            renderInputs.tint().z, renderInputs.tint().w);
                        glUniform1i(location(shader, "locOverlay"), renderInputs.packedOverlay());
                        glUniform1i(location(shader, "locAlphaMode"), 1);
                        glUniform1f(location(shader, "locFogStart"), renderInputs.fogStart());
                        glUniform1f(location(shader, "locFogEnd"), renderInputs.fogEnd());
                        glUniform4f(location(shader, "locFogColor"), renderInputs.fogColor().x,
                            renderInputs.fogColor().y, renderInputs.fogColor().z, renderInputs.fogColor().w);
                        glUniform3f(location(shader, "locLight0"), renderInputs.light0().x, renderInputs.light0().y, renderInputs.light0().z);
                        glUniform3f(location(shader, "locLight1"), renderInputs.light1().x, renderInputs.light1().y, renderInputs.light1().z);
                        for (int unit = 0; unit < textures.length; unit++) {
                            glActiveTexture(GL_TEXTURE0 + unit);
                            glBindTexture(GL_TEXTURE_2D, textures[unit]);
                        }
                        glBindVertexArray(vao);
                        glDrawElements(GL_TRIANGLES, count, GL_UNSIGNED_INT, (long) offset);
                        drawCalls++;
                        submittedIndices += count;
                        if (translucentPass) {
                            // Match OpenYSM GpuRenderPath: opaque first, then its original alpha=2 shader pass.
                            glEnable(GL_BLEND);
                            renderSystem.getMethod("defaultBlendFunc").invoke(null);
                            glUniform1i(location(shader, "locAlphaMode"), 2);
                            glDrawElements(GL_TRIANGLES, count, GL_UNSIGNED_INT, (long) offset);
                            drawCalls++;
                            submittedIndices += count;
                            glDisable(GL_BLEND);
                        }
                    }
                    if (frame == frameLimit - 1) {
                        nonBackground = WindowPixels.changed(backgroundPixels, WindowPixels.capture(960, 640));
                        System.out.println("REFERENCE MODEL PIXELS versusBackground=" + nonBackground);
                    }
                    int error = glGetError();
                    if (error != GL_NO_ERROR) {
                        throw new IllegalStateException("OpenGL error " + error);
                    }
                    glfwSwapBuffers(window);
                    glfwPollEvents();
                }
                if (nonBackground < 100) {
                    throw new AssertionError("GPU window incomplete or model not visible: pixels=" + nonBackground);
                }
                String actualMode = cpuDrawCalls == 0 ? "gpu" : cpuDrawCalls == drawCalls ? "java" : "gpu+java";
                System.out.println("REFERENCE WINDOW PASS requested=gpu actual=" + actualMode + " submittedIndices=" + submittedIndices
                    + " pixels=" + nonBackground + " frames=" + frameLimit
                    + " maximumGpuPoseChange=" + (drawCalls == cpuDrawCalls ? "NA" : maximumPoseChange)
                    + " submittedInstances=" + instances.size() + " drawCalls=" + drawCalls
                    + " gpuDrawCalls=" + (drawCalls - cpuDrawCalls) + " cpuDrawCalls=" + cpuDrawCalls
                    + " cpuSubmittedIndices=" + cpuSubmittedIndices);
            } finally {
                if (background != null) {
                    cleanup.add(background);
                }
                if (cpuPass != null) {
                    cleanup.add(cpuPass);
                }
                for (Object mesh : meshes.values()) {
                    cleanup.add(() -> mesh.getClass().getMethod("dispose").invoke(mesh));
                }
                for (int texture : modelTextures.values()) {
                    cleanup.add(() -> glDeleteTextures(texture));
                }
                textures[0] = 0;
                if (window != 0) {
                    for (int texture : textures) {
                        cleanup.add(() -> glDeleteTextures(texture));
                    }
                    long createdWindow = window;
                    cleanup.add(() -> glfwDestroyWindow(createdWindow));
                }
                cleanup.add(() -> glfwTerminate());
            }
        }
    }

    private static int location(Class<?> shader, String method) throws Exception {
        return (int) shader.getMethod(method).invoke(null);
    }

    private static int texture(Path path) throws Exception {
        var image = ImageIO.read(path.toFile());
        ByteBuffer pixels = MemoryUtil.memAlloc(image.getWidth() * image.getHeight() * 4);
        try {
            for (int y = 0; y < image.getHeight(); y++) {
                for (int x = 0; x < image.getWidth(); x++) {
                    int argb = image.getRGB(x, y);
                    pixels.put((byte) (argb >> 16)).put((byte) (argb >> 8)).put((byte) argb).put((byte) (argb >> 24));
                }
            }
            pixels.flip();
            return uploadTexture(image.getWidth(), image.getHeight(), pixels);
        } finally {
            MemoryUtil.memFree((java.nio.Buffer) pixels);
        }
    }

    private static int solidTexture(int rgba) {
        ByteBuffer pixels = ByteBuffer.allocateDirect(16 * 16 * 4).order(ByteOrder.BIG_ENDIAN);
        while (pixels.hasRemaining()) {
            pixels.putInt(rgba);
        }
        pixels.flip();
        return uploadTexture(16, 16, pixels);
    }

    private static int uploadTexture(int width, int height, ByteBuffer pixels) {
        int texture = glGenTextures();
        glBindTexture(GL_TEXTURE_2D, texture);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
        glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA8, width, height, 0, GL_RGBA, GL_UNSIGNED_BYTE, pixels);
        return texture;
    }

    private static int nonBackgroundPixels() {
        ByteBuffer pixels = MemoryUtil.memAlloc(960 * 640 * 4);
        try {
            glReadPixels(0, 0, 960, 640, GL_RGBA, GL_UNSIGNED_BYTE, pixels);
            int count = 0;
            long red = 0;
            long green = 0;
            long blue = 0;
            for (int index = 0; index < pixels.capacity(); index += 4) {
                red += pixels.get(index) & 255;
                green += pixels.get(index + 1) & 255;
                blue += pixels.get(index + 2) & 255;
                if ((pixels.get(index) & 255) + (pixels.get(index + 1) & 255) + (pixels.get(index + 2) & 255) > 12) {
                    count++;
                }
            }
            System.out.println("REFERENCE WINDOW RGB SUM red=" + red + " green=" + green + " blue=" + blue);
            return count;
        } finally {
            MemoryUtil.memFree((java.nio.Buffer) pixels);
        }
    }
}
