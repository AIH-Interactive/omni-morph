package top.aihmc.omm;

import com.mojang.blaze3d.ProjectionType;
import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.Std140Builder;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.GpuDevice;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.AddressMode;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuTexture;
import com.mojang.blaze3d.textures.TextureFormat;
import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.OptionalDouble;
import java.util.function.BooleanSupplier;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.resources.Identifier;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.lwjgl.system.MemoryUtil;

import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL33C.*;

/** Supplies window targets and Minecraft render inputs to the unmodified MOD GPU backend. */
final class OmniMorphGpuWindow {
    @FunctionalInterface
    interface FrameSource {
        List<Object> sample(int frame) throws Exception;
    }
    private static final String ACCELERATION = "top.aihmc.omnimorph.render.acceleration.";
    private static final int WIDTH = 960;
    private static final int HEIGHT = 640;

    private OmniMorphGpuWindow() {
    }

    static void run(List<Object> batches, List<double[]> boundsVertices, byte[] texture) throws Exception {
        run(batches, boundsVertices, texture, null);
    }

    static void run(List<Object> batches, List<double[]> boundsVertices, byte[] texture,
                    FrameSource frames) throws Exception {
        run(batches, boundsVertices, java.util.Collections.nCopies(batches.size(), texture), frames);
    }

    static void run(List<Object> batches, List<double[]> boundsVertices, List<byte[]> textures,
                    FrameSource frames) throws Exception {
        if (textures.size() != batches.size()) {
            throw new IllegalArgumentException("GPU batch texture count mismatch");
        }
        RenderInputs inputs = RenderInputs.read();
        OmniMorphGpuRuntime.run((window, device, adapter) ->
            render(window, device, adapter, batches, boundsVertices, textures, inputs, frames));
    }

    private static void render(long window, GpuDevice device, Object adapter, List<Object> source,
                               List<double[]> boundsVertices, List<byte[]> textureBytes, RenderInputs inputs,
                               FrameSource frameSource) throws Exception {
        Class<?> backendType = Class.forName(ACCELERATION + "GpuAccelerationBackend");
        Class<?> retirementType = Class.forName(ACCELERATION + "GpuRetirement");
        var retirementConstructor = retirementType.getDeclaredConstructor();
        retirementConstructor.setAccessible(true);
        Object retirement = retirementConstructor.newInstance();
        var constructor = backendType.getDeclaredConstructor(Class.forName(ACCELERATION + "GpuDeviceAdapter"),
            retirementType, long.class, long.class);
        constructor.setAccessible(true);
        Object backend = constructor.newInstance(adapter, retirement, 256L << 20, 64L << 20);
        Method initialize = method(backendType, "initializeStep");
        Method beginFrame = backendType.getMethod("beginFrame");
        Class<?> contextType = Class.forName(ACCELERATION + "GpuDrawContext");
        Method prepare = method(backendType, "prepare", Class.forName(ACCELERATION + "DrawBatch"),
            BooleanSupplier.class);
        Method drawPrepared = method(backendType, "drawPrepared", Class.forName(ACCELERATION + "GpuAccelerationBackend$Prepared"),
            contextType, int.class, int.class, BooleanSupplier.class);
        var drawOrder = new OmniMorphDrawOrder();
        try {
            boolean ready = false;
            for (int step = 0; step < 3; step++) {
                ready = (boolean) initialize.invoke(backend);
            }
            if (!ready) {
                throw new IllegalStateException("Original GPU backend did not finish initialization");
            }
            List<Object> batches = withMaterial(source, inputs);
            Class<?> operation = Class.forName(ACCELERATION + "AccelerationBackend$Operation");
            Method supports = backendType.getMethod("supports", operation, Class.forName(ACCELERATION + "DrawBatch"));
            var pipelines = java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<Object, Boolean>());
            for (Object batch : batches) {
                Object semantics = batch.getClass().getMethod("semantics").invoke(batch);
                Object material = semantics.getClass().getMethod("material").invoke(semantics);
                pipelines.add(material.getClass().getMethod("pipeline").invoke(material));
                supports.invoke(backend, operation.getField("GPU_DRAW").get(null), batch);
            }
            // The original backend queues unfamiliar pipelines and compiles one per beginFrame.
            for (int index = 0; index < pipelines.size(); index++) {
                beginFrame.invoke(backend);
            }
            System.out.println("OMP GPU ORIGINAL MATERIAL WARMUP pipelines=" + pipelines.size());
            int targetUsage = GpuTexture.USAGE_RENDER_ATTACHMENT | GpuTexture.USAGE_COPY_SRC | GpuTexture.USAGE_COPY_DST
                | GpuTexture.USAGE_TEXTURE_BINDING;
            try (var color = device.createTexture("Preview color", targetUsage, TextureFormat.RGBA8, WIDTH, HEIGHT, 1, 1);
                 var depth = device.createTexture("Preview depth", GpuTexture.USAGE_RENDER_ATTACHMENT | GpuTexture.USAGE_COPY_DST,
                     TextureFormat.DEPTH32, WIDTH, HEIGHT, 1, 1);
                 var colorView = device.createTextureView(color);
                 var depthView = device.createTextureView(depth);
                 var scenery = new GpuScenery(color, depth);
                 var modelTextures = new ModelTextures(device);
                 var lightmapImage = NativeImage.read(LightingTextureInputs.read("lightmapTexture"));
                 var overlayImage = NativeImage.read(LightingTextureInputs.read("overlayTexture"));
                 var sampler = device.createSampler(AddressMode.CLAMP_TO_EDGE, AddressMode.CLAMP_TO_EDGE,
                     FilterMode.NEAREST, FilterMode.NEAREST, 1, OptionalDouble.empty())) {
                PreviewCamera configuredCamera = PreviewCamera.configured();
                Matrix4f projectionMatrix = configuredCamera == null
                    ? new Matrix4f().perspective((float) Math.toRadians(45), 1.5f, 0.01f, 1000)
                    : configuredCamera.projection(1.5f);
                Matrix4f viewMatrix = configuredCamera == null ? camera(source, boundsVertices) : configuredCamera.view();
                try (var lightmap = upload(device, lightmapImage);
                     var lightmapView = device.createTextureView(lightmap);
                     var overlay = upload(device, overlayImage);
                     var overlayView = device.createTextureView(overlay);
                     var projection = uniform(device, "Preview projection", builder(64)
                         .putMat4f(projectionMatrix).get());
                     var fog = uniform(device, "Preview fog", builder(48).putVec4(inputs.fogColor())
                         .putFloat(inputs.fogStart()).putFloat(inputs.fogEnd())
                         .putFloat(inputs.fogStart()).putFloat(inputs.fogEnd())
                         .putFloat(inputs.fogEnd()).putFloat(inputs.fogEnd()).align(16).get());
                     var lights = uniform(device, "Preview lights", builder(32)
                         .putVec3(inputs.light0()).putVec3(inputs.light1()).align(16).get());
                     var globals = uniform(device, "Preview globals", builder(64)
                         .putIVec3(0, 0, 0).putVec3(0, 0, 0).putVec2(WIDTH, HEIGHT)
                         .putFloat(1).putFloat(inputs.gameTime()).putInt(0).putInt(0).align(16).get())) {
                    RenderSystem.setProjectionMatrix(projection.slice(), ProjectionType.PERSPECTIVE);
                    RenderSystem.setShaderFog(fog.slice());
                    RenderSystem.setShaderLights(lights.slice());
                    RenderSystem.setGlobalSettingsUniform(globals);
                    var dynamic = RenderSystem.getDynamicUniforms().writeTransform(viewMatrix,
                        new Vector4f(1), new Vector3f(), new Matrix4f());
                    var contexts = new ArrayList<Object>();
                    for (byte[] bytes : textureBytes) {
                        var textures = Map.of(
                            "Sampler0", new RenderSetup.TextureAndSampler(modelTextures.view(bytes), sampler),
                            "Sampler1", new RenderSetup.TextureAndSampler(overlayView, sampler),
                            "Sampler2", new RenderSetup.TextureAndSampler(lightmapView, sampler));
                        contexts.add(contextType.getConstructors()[0].newInstance(colorView, depthView,
                            textures, dynamic, false, 0, 0, WIDTH, HEIGHT));
                    }
                    int frames = Integer.parseInt(System.getProperty("omm.preview.frames", "120"));
                    int pixels = 0;
                    long accepted = 0;
                    device.setVsync(true);
                    for (int frame = 0; frame < frames && !glfwWindowShouldClose(window); frame++) {
                        if (frameSource != null) {
                            batches = withMaterial(frameSource.sample(frame), inputs);
                        }
                        if (batches.size() != contexts.size()) {
                            throw new IllegalStateException("Animated GPU batch texture topology changed");
                        }
                        beginFrame.invoke(backend);
                        device.createCommandEncoder().clearColorAndDepthTextures(color, 0xff87bfff, depth, 1);
                        scenery.draw(new Matrix4f(projectionMatrix).mul(viewMatrix));
                        byte[] backgroundPixels = frame == frames - 1 ? scenery.capture() : null;
                        Object[] prepared = new Object[batches.size()];
                        for (var range : drawOrder.ranges(batches)) {
                            int index = range.batch();
                            if (prepared[index] == null) {
                                prepared[index] = prepare.invoke(backend, batches.get(index), (BooleanSupplier) () -> true);
                                if (prepared[index] == null) {
                                    throw new IllegalStateException("Original GPU prepare rejected batch: " + index);
                                }
                            }
                            Object result = drawPrepared.invoke(backend, prepared[index], contexts.get(index),
                                range.firstIndex(), range.indexCount(), (BooleanSupplier) () -> true);
                            if (!result.toString().equals("DRAWN")) {
                                throw new IllegalStateException("Original GPU draw rejected batch: " + result);
                            }
                            accepted++;
                        }
                        if (backgroundPixels != null) {
                            pixels = WindowPixels.changed(backgroundPixels, scenery.capture());
                            System.out.println("OMP GPU MODEL PIXELS versusBackground=" + pixels);
                        }
                        device.createCommandEncoder().presentTexture(colorView);
                        int error = glGetError();
                        if (error != GL_NO_ERROR) {
                            throw new IllegalStateException("Original GPU window GL error: " + error);
                        }
                        device.presentFrame();
                        glfwPollEvents();
                    }
                    if (pixels < 100) {
                        throw new AssertionError("Model absent or window closed before validation: " + pixels);
                    }
                    System.out.println("OMP ORIGINAL GPU MODEL PASS batches=" + batches.size()
                        + " draws=" + accepted + " frames=" + frames + " pixels=" + pixels
                        + " uploads=" + method(backendType, "geometryUploads").invoke(backend)
                        + " cacheHits=" + method(backendType, "cacheHits").invoke(backend));
                }
            }
        } finally {
            backendType.getMethod("close").invoke(backend);
            if (!(boolean) method(retirementType, "awaitRemaining", long.class)
                    .invoke(retirement, 1_000_000_000L)) {
                throw new IllegalStateException("GPU resources did not retire before context shutdown");
            }
        }
    }

    /** Uses the original target attachments; raw GL bindings are restored before MOD rendering. */
    private static final class GpuScenery implements AutoCloseable {
        private final SuperflatBackground background;
        private final int framebuffer;

        GpuScenery(GpuTexture color, GpuTexture depth) throws Exception {
            background = new SuperflatBackground();
            framebuffer = glGenFramebuffers();
            int previous = glGetInteger(GL_DRAW_FRAMEBUFFER_BINDING);
            try {
                glBindFramebuffer(GL_DRAW_FRAMEBUFFER, framebuffer);
                glFramebufferTexture2D(GL_DRAW_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_TEXTURE_2D,
                    ((com.mojang.blaze3d.opengl.GlTexture) color).glId(), 0);
                glFramebufferTexture2D(GL_DRAW_FRAMEBUFFER, GL_DEPTH_ATTACHMENT, GL_TEXTURE_2D,
                    ((com.mojang.blaze3d.opengl.GlTexture) depth).glId(), 0);
                if (glCheckFramebufferStatus(GL_DRAW_FRAMEBUFFER) != GL_FRAMEBUFFER_COMPLETE) {
                    throw new IllegalStateException("Preview scenery target is incomplete");
                }
            } catch (RuntimeException | Error failure) {
                close();
                throw failure;
            } finally {
                glBindFramebuffer(GL_DRAW_FRAMEBUFFER, previous);
            }
        }

        void draw(Matrix4f camera) {
            int previous = glGetInteger(GL_DRAW_FRAMEBUFFER_BINDING);
            int[] viewport = new int[4];
            glGetIntegerv(GL_VIEWPORT, viewport);
            try {
                glBindFramebuffer(GL_DRAW_FRAMEBUFFER, framebuffer);
                glViewport(0, 0, WIDTH, HEIGHT);
                background.draw(camera);
            } finally {
                glBindFramebuffer(GL_DRAW_FRAMEBUFFER, previous);
                glViewport(viewport[0], viewport[1], viewport[2], viewport[3]);
            }
        }

        byte[] capture() {
            int previous = glGetInteger(GL_READ_FRAMEBUFFER_BINDING);
            try {
                glBindFramebuffer(GL_READ_FRAMEBUFFER, framebuffer);
                return WindowPixels.capture(WIDTH, HEIGHT);
            } finally {
                glBindFramebuffer(GL_READ_FRAMEBUFFER, previous);
            }
        }

        @Override
        public void close() {
            glDeleteFramebuffers(framebuffer);
            background.close();
        }
    }

    private static final class ModelTextures implements AutoCloseable {
        private final GpuDevice device;
        private final Map<byte[], com.mojang.blaze3d.textures.GpuTextureView> views = new java.util.IdentityHashMap<>();
        private final List<GpuTexture> textures = new ArrayList<>();

        ModelTextures(GpuDevice device) {
            this.device = device;
        }

        com.mojang.blaze3d.textures.GpuTextureView view(byte[] bytes) throws Exception {
            var view = views.get(bytes);
            if (view == null) {
                try (var image = NativeImage.read(bytes)) {
                    var texture = upload(device, image);
                    textures.add(texture);
                    view = device.createTextureView(texture);
                    views.put(bytes, view);
                }
            }
            return view;
        }

        @Override
        public void close() {
            views.values().forEach(com.mojang.blaze3d.textures.GpuTextureView::close);
            textures.forEach(GpuTexture::close);
        }
    }

    private static List<Object> withMaterial(List<Object> source, RenderInputs inputs) throws Exception {
        Class<?> semanticsType = Class.forName(ACCELERATION + "DrawSemantics");
        Class<?> layerType = Class.forName(ACCELERATION + "DrawSemantics$Layer");
        Identifier texture = Identifier.fromNamespaceAndPath("omm-preview", "model");
        Object semantics = semanticsType.getConstructor(RenderType.class, Identifier.class, layerType,
            String.class, int.class, int.class, int.class, int.class, float.class, float.class,
            float.class, float.class, double.class, boolean.class).newInstance(
                RenderTypes.entityCutout(texture), texture, layerType.getField("PRIMARY").get(null),
                "preview", 0, 0, inputs.packedLight(), inputs.packedOverlay(),
                inputs.tint().x, inputs.tint().y, inputs.tint().z, inputs.tint().w, 0d, true);
        List<Object> result = new ArrayList<>();
        for (Object batch : source) {
            Class<?> type = batch.getClass();
            Object existing = type.getMethod("semantics").invoke(batch);
            if (existing.getClass().getMethod("material").invoke(existing) != null) {
                result.add(batch);
                continue;
            }
            Object lease = type.getMethod("lease").invoke(batch);
            Object mesh = type.getMethod("mesh").invoke(batch);
            Object pose = type.getMethod("pose").invoke(batch);
            result.add(type.getConstructor(lease.getClass(), mesh.getClass(), pose.getClass(), semanticsType)
                .newInstance(lease, mesh, pose, semantics));
        }
        return result;
    }

    private static Matrix4f camera(List<Object> source, List<double[]> batches) throws Exception {
        Vector3f low = new Vector3f(Float.POSITIVE_INFINITY);
        Vector3f high = new Vector3f(Float.NEGATIVE_INFINITY);
        for (int batchIndex = 0; batchIndex < batches.size(); batchIndex++) {
            Object draw = source.get(batchIndex);
            Object semantics = draw.getClass().getMethod("semantics").invoke(draw);
            if (!(boolean) semantics.getClass().getMethod("visible").invoke(semantics)) {
                continue;
            }
            double[] batch = batches.get(batchIndex);
            for (int index = 0; index < batch.length; index += 9) {
                Vector3f position = new Vector3f((float) batch[index], (float) batch[index + 1], (float) batch[index + 2]);
                low.min(position);
                high.max(position);
            }
        }
        Vector3f size = new Vector3f(high).sub(low);
        Vector3f center = new Vector3f(low).add(high).mul(0.5f);
        float extent = Math.max(size.x, Math.max(size.y, size.z));
        return new Matrix4f().lookAt(new Vector3f(center).add(0, 0, extent * 1.7f), center, new Vector3f(0, 1, 0));
    }

    private static GpuTexture upload(GpuDevice device, NativeImage image) {
        GpuTexture texture = device.createTexture("Preview model texture",
            GpuTexture.USAGE_COPY_DST | GpuTexture.USAGE_TEXTURE_BINDING,
            TextureFormat.RGBA8, image.getWidth(), image.getHeight(), 1, 1);
        try {
            device.createCommandEncoder().writeToTexture(texture, image);
            return texture;
        } catch (RuntimeException | Error failure) {
            texture.close();
            throw failure;
        }
    }

    private static Std140Builder builder(int size) {
        return Std140Builder.intoBuffer(ByteBuffer.allocateDirect(size).order(ByteOrder.nativeOrder()));
    }

    private static GpuBuffer uniform(GpuDevice device, String name, ByteBuffer bytes) {
        return device.createBuffer(() -> name, GpuBuffer.USAGE_UNIFORM, bytes);
    }

    private static Method method(Class<?> type, String name, Class<?>... parameters) throws Exception {
        Method method = type.getDeclaredMethod(name, parameters);
        method.setAccessible(true);
        return method;
    }

    private static int visiblePixels(long window) {
        int[] width = new int[1];
        int[] height = new int[1];
        glfwGetFramebufferSize(window, width, height);
        ByteBuffer pixels = MemoryUtil.memAlloc(width[0] * height[0] * 4);
        int rowLength = glGetInteger(GL_PACK_ROW_LENGTH);
        try {
            glPixelStorei(GL_PACK_ROW_LENGTH, 0);
            glBindFramebuffer(GL_READ_FRAMEBUFFER, 0);
            glReadBuffer(GL_BACK);
            glReadPixels(0, 0, width[0], height[0], GL_RGBA, GL_UNSIGNED_BYTE, pixels);
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
            System.out.println("GPU WINDOW RGB SUM red=" + red + " green=" + green + " blue=" + blue);
            return count;
        } finally {
            glPixelStorei(GL_PACK_ROW_LENGTH, rowLength);
            MemoryUtil.memFree((java.nio.Buffer) pixels);
        }
    }

}
