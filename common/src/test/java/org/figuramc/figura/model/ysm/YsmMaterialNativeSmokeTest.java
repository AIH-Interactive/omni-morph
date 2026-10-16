package org.figuramc.figura.model.ysm;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.SharedConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.resources.Identifier;
import net.minecraft.server.Bootstrap;
import org.figuramc.figura.avatar.Avatar;
import org.figuramc.figura.avatar.control.AvatarControlRuntime;
import org.figuramc.figura.avatar.ysm.YsmAvatarLoader;
import org.figuramc.figura.avatar.ysm.YsmMemoryResourceSource;
import org.figuramc.figura.avatar.ysm.YsmModelData;
import org.figuramc.figura.avatar.ysm.YsmSourceIdentity;
import org.figuramc.figura.avatar.ysm.YsmTextureOption;
import org.figuramc.figura.avatar.ysm.YsmTextureSelectionStore;
import org.figuramc.figura.mixin.render.TextureManagerAccessor;
import org.figuramc.figura.model.rendering.texture.FiguraTexture;
import sun.misc.Unsafe;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;

/** Native image allocation/lifecycle only: no GPU device, GL context, framebuffer or window is created. */
public final class YsmMaterialNativeSmokeTest {
    public static void main(String[] args) throws Exception {
        Path directory = Files.createTempDirectory("figura-native-material-").toAbsolutePath().normalize();
        String previousDirectory = System.getProperty("figura.test.directory");
        System.setProperty("figura.test.directory", directory.toString());
        Field instance = Minecraft.class.getDeclaredField("instance");
        instance.setAccessible(true);
        Object previousClient = instance.get(null);
        Field renderThread = RenderSystem.class.getDeclaredField("renderThread");
        renderThread.setAccessible(true);
        Object previousThread = renderThread.get(null);
        Field unsafeField = Unsafe.class.getDeclaredField("theUnsafe");
        unsafeField.setAccessible(true);
        Unsafe unsafe = (Unsafe) unsafeField.get(null);
        RecordingClient client = (RecordingClient) unsafe.allocateInstance(RecordingClient.class);
        client.queue = new ConcurrentLinkedQueue<>();
        var options = (net.minecraft.client.Options) unsafe.allocateInstance(net.minecraft.client.Options.class);
        Field cameraType = net.minecraft.client.Options.class.getDeclaredField("cameraType");
        cameraType.setAccessible(true);
        cameraType.set(options, net.minecraft.client.CameraType.FIRST_PERSON);
        Field clientOptions = Minecraft.class.getDeclaredField("options");
        clientOptions.setAccessible(true);
        clientOptions.set(client, options);
        client.manager = (RecordingTextures) unsafe.allocateInstance(RecordingTextures.class);
        client.manager.textures = new HashMap<>();
        try {
            SharedConstants.tryDetectVersion();
            Bootstrap.bootStrap();
            RenderSystem.initRenderThread();
            instance.set(null, client);
            org.figuramc.figura.permissions.PermissionManager.init();
            if (args.length > 0) {
                require(args.length == 4 && args[0].equals("--stability"), "explicit stability mode arguments");
                client.sounds = YsmStabilitySmokeTest.soundManager(unsafe);
                YsmStabilitySmokeTest.run(unsafe, client::drain, Path.of(args[1]), Path.of(args[2]), Path.of(args[3]));
                return;
            }
            testScaleAndDefaultTexture(unsafe, client);
            testPreviewDefaultPose(unsafe, client);
            testPreviewLighting(unsafe, client);
            testGuiResources(unsafe, client);
            testLayerOrder(unsafe, client);
            testHandLocators(unsafe, client);
            testSubEntityInstances(unsafe, client);
            testSyncModel(unsafe, client);
            testSyncConnection(unsafe, client);
            testCullingProperty(unsafe, client);
            testModelSelection(unsafe, client);
            testSelectionRestore(unsafe, client, directory);
            testOwnershipAndDeferredClose(unsafe, client);
            require(client.queue.isEmpty(), "all scheduled native cleanup completed");
            System.out.println("M5 native material objects: formal assembly, paired selection, model isolation, channel recreation and deferred NativeImage release PASS; GPU/shader/GUI pending.");
        } finally {
            client.drain();
            clearSelections();
            instance.set(null, previousClient);
            renderThread.set(null, previousThread);
            if (previousDirectory == null) {
                System.clearProperty("figura.test.directory");
            } else {
                System.setProperty("figura.test.directory", previousDirectory);
            }
            try (var paths = Files.walk(directory)) {
                for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
                    if (!path.toAbsolutePath().normalize().startsWith(directory)) {
                        throw new IllegalStateException("Unexpected native material cleanup path");
                    }
                    Files.delete(path);
                }
            }
        }
    }

    private static void testScaleAndDefaultTexture(Unsafe unsafe, RecordingClient client) throws Exception {
        Fixture fixture = new Fixture(unsafe, 36);
        try {
            var runtime = fixture.runtime;
            require(Math.abs(runtime.widthScale() - 0.6f) < 0.0001 && Math.abs(runtime.heightScale() - 1.2f) < 0.0001,
                    "declared width and height pass through loader, defaults and restored runtime controls");
            var stack = new com.mojang.blaze3d.vertex.PoseStack();
            runtime.captureRenderPose(false).applyModelScale(stack);
            require(Math.abs(stack.last().pose().m00() - 0.5625f) < 0.0001
                            && Math.abs(stack.last().pose().m11() - 1.125f) < 0.0001
                            && Math.abs(stack.last().pose().m22() - 0.5625f) < 0.0001,
                    "submission scale consumes the distinct width and height values with the existing player scale factor");
            var select = YsmAvatarLoader.class.getDeclaredMethod("selectTexture", Path.class, List.class, String.class);
            select.setAccessible(true);
            var textures = List.of(new YsmTextureOption("a", "A", "a.png"), new YsmTextureOption("c", "C", "c.png"));
            require(((YsmTextureOption) select.invoke(null, null, textures, "c")).id().equals("c"),
                    "model default selects an available non-first skin");
            require(((YsmTextureOption) select.invoke(null, null, textures, "missing")).id().equals("a"),
                    "an unavailable default falls back to the first available skin");
            require(((YsmTextureOption) select.invoke(null, null, textures, "")).id().equals("a"),
                    "an omitted default follows the same stable fallback");
        } finally {
            fixture.runtime.close();
            client.drain();
        }
    }

    private static void testPreviewDefaultPose(Unsafe unsafe, RecordingClient client) throws Exception {
        Fixture fixture = new Fixture(unsafe, 35);
        var runtime = fixture.runtime;
        var world = runtime.owner().getMolangContext();
        runtime.animations().play("idle", true, 1f);
        runtime.animations().getActiveAnimation("idle").time = 0.25f;
        world.variables.setPath("pose", 32f);
        world.variables.setPath("nested.child.value", 3f);
        world.variables.setPath("self", world.variables.evaluate(null));
        try {
            require(runtime.forRenderPass() == runtime, "world drawing keeps its original evaluator");
            runtime.owner().renderMode = org.figuramc.figura.model.rendering.EntityRenderMode.FIGURA_GUI;
            var preview = runtime.forRenderPass();
            require(preview != runtime && preview.isPreviewRuntime(), "GUI default pose gets a detached evaluator");
            require(preview.owner().getYsmRuntime() == preview, "preview fn/ysm bindings resolve the detached runtime");
            require(!preview.animations().isActive("gui") && !preview.animations().isActive("idle"),
                    "preview ignores the declared GUI clip and world idle animation");
            require(runtime.animations().isActive("idle") && !runtime.animations().isActive("gui"),
                    "preview selection never changes world playback slots");
            require(runtime.animations().getActiveAnimation("idle").time == 0.25f,
                    "preview does not advance the world's existing animation clock");
            var context = preview.owner().getMolangContext();
            require(((Number) context.variables.getPath("pose")).floatValue() == 32f, "preview starts with current model inputs");
            require(Float.valueOf(0f).equals(context.variables.getPath("roaming.missing")),
                    "preview retains roaming's typed zero fallback");
            context.variables.setPath("nested.child.value", 9f);
            context.variables.setPath("self.only_preview", 4f);
            require(((Number) world.variables.getPath("nested.child.value")).floatValue() == 3f
                            && world.variables.getPath("only_preview") == null,
                    "nested values and cyclic storage views cannot write into world variables");
            require(preview.geometry() != runtime.geometry(), "alpha/geometry state is independent");
            var state = new net.minecraft.client.renderer.entity.state.LivingEntityRenderState();
            for (float age : new float[] {20f, 22f, 40f}) {
                state.ageInTicks = age;
                org.figuramc.figura.model.ysm.animation.YsmRenderFrameClock.beginFrame();
                preview.updateAnimations(state, null);
                var pose = preview.captureRenderPose(false);
                require(pose.part("root").animPosRaw().lengthSquared() == 0d,
                        "GUI rendering retains the default pose across animation frames");
            }
            require(!preview.animations().isActive("gui") && !preview.animations().isActive("idle")
                            && runtime.animations().getActiveAnimation("idle").time == 0.25f,
                    "GUI passes do not start preview clips or advance world playback");
            require(context.variables.getPath("preview_calls") == null
                            && context.variables.getPath("preview_events") == null
                            && world.variables.getPath("preview_calls") == null
                            && world.variables.getPath("preview_events") == null,
                    "default-pose preview never executes animation functions or timeline events");
            require(preview.setTexture("clear"), "preview accepts a transparent private skin");
            require(preview.geometry().bones.get("root").cubes.getFirst().quads.stream().noneMatch(YsmGeometry.Quad::visible)
                            && runtime.geometry().bones.get("root").cubes.getFirst().quads.stream().anyMatch(YsmGeometry.Quad::visible),
                    "preview alpha updates never hide the world's geometry");
            var borrowed = runtime.texture();
            require(preview.setTexture("c"), "preview may select a private material through its existing model API");
            require(!image(borrowed).isClosed() && runtime.textureId().equals("a"),
                    "preview texture selection does not close or switch the world material");
            var owned = preview.texture();
            runtime.owner().controls.get("ysm.width_scale").setValue(0.9d);
            var recreated = runtime.forRenderPass();
            require(recreated != preview && preview.isClosed() && image(owned).isClosed(), "changed model controls recreate and close the old preview");
            require(Math.abs(recreated.widthScale() - 0.9f) < 0.0001, "preview imports current control values without persisting them");
            require(recreated.texture() == runtime.texture(), "new preview borrows the current selected material");
            require(runtime.setTexture("clear"), "world material may change while preview remains open");
            require(runtime.forRenderPass() == recreated && recreated.texture() == runtime.texture(),
                    "world skin changes update the borrowed preview material without changing the default pose");
            require(recreated.geometry().bones.get("root").cubes.getFirst().quads.stream().noneMatch(YsmGeometry.Quad::visible),
                    "borrowed material changes refresh the preview's own alpha metadata");
            var previousScreen = client.screen;
            try {
                var settings = (org.figuramc.figura.gui.screens.AvatarControlsScreen) unsafe.allocateInstance(
                        org.figuramc.figura.gui.screens.AvatarControlsScreen.class);
                client.screen = settings;
                require(runtime.forRenderPass() == runtime,
                        "model settings preview renders the live runtime and its current action playback");
                runtime.owner().controls.get("ysm.width_scale").setValue(1.1d);
                require(Math.abs(runtime.forRenderPass().widthScale() - 1.1f) < 0.0001,
                        "settings preview consumes changed controls immediately");
                var sample = org.figuramc.figura.model.ysm.animation.YsmAnimationPlayer.class.getDeclaredMethod(
                        "sampleAnimations", org.figuramc.figura.molang.runtime.ExpressionEvaluator.class, boolean.class);
                sample.setAccessible(true);
                sample.invoke(runtime.animations(),
                        org.figuramc.figura.molang.runtime.ExpressionEvaluator.evaluator(world), false);
                require(runtime.forRenderPass().captureRenderPose(false).part("root").animPosRaw().y == 16d,
                        "settings preview reflects the live idle animation rather than the default or GUI clip");
                client.screen = new org.figuramc.figura.gui.screens.AvatarScreen(1f, 0f, 0f, null, settings);
                require(runtime.forRenderPass() == runtime,
                        "expanded settings preview keeps the live runtime");
            } finally {
                client.screen = previousScreen;
            }
            for (String declaration : List.of("", "missing_preview_clip")) {
                var input = fixture.runtimeTag.copy();
                if (declaration.isEmpty()) {
                    input.remove("preview_animation");
                } else {
                    input.putString("preview_animation", declaration);
                }
                try (var alternative = YsmModelRuntime.fromNbt(owner(unsafe, 55), input)) {
                    alternative.owner().renderMode = org.figuramc.figura.model.rendering.EntityRenderMode.FIGURA_GUI;
                    var alternativePreview = alternative.forRenderPass();
                    require(alternativePreview != alternative && alternativePreview.isPreviewRuntime(),
                            "omitted or invalid preview declarations still select the detached default pose");
                    alternativePreview.updateAnimations(state, null);
                    require(alternativePreview.captureRenderPose(false).part("root").animPosRaw().lengthSquared() == 0d,
                            "default pose does not depend on an available preview animation");
                }
            }
            runtime.close();
            require(image(borrowed).isClosed() && runtime.forRenderPass() == runtime,
                    "model unload closes its preview and cannot recreate a retained preview evaluator");
        } finally {
            runtime.close();
            client.drain();
        }
    }

    private static void testPreviewLighting(Unsafe unsafe, RecordingClient client) throws Exception {
        Fixture fixture = new Fixture(unsafe, 34);
        var runtime = fixture.runtime;
        var renderType = YsmRenderer.class.getDeclaredMethod("renderType");
        renderType.setAccessible(true);
        var renderer = runtime.renderer();
        try {
            require(runtime.isGuiNoLighting(), "formal loader retains gui_no_lighting");
            runtime.owner().renderMode = org.figuramc.figura.model.rendering.EntityRenderMode.FIGURA_GUI;
            var opaque = (net.minecraft.client.renderer.rendertype.RenderType) renderType.invoke(renderer);
            var source = net.minecraft.client.renderer.RenderPipelines.ENTITY_TRANSLUCENT_EMISSIVE;
            require(opaque.pipeline().getVertexShader().equals(source.getVertexShader())
                            && opaque.pipeline().getFragmentShader().equals(source.getFragmentShader())
                            && opaque.pipeline().getShaderDefines().values().equals(source.getShaderDefines().values())
                            && opaque.pipeline().getUniforms().equals(source.getUniforms()),
                    "unlit GUI material uses the native emissive shaders, numeric defines and uniforms");
            var unlitDefines = opaque.pipeline().getShaderDefines();
            require(unlitDefines.flags().contains("EMISSIVE") && unlitDefines.flags().contains("NO_CARDINAL_LIGHTING")
                            && !unlitDefines.flags().contains("PER_FACE_LIGHTING"),
                    "GUI disables both lightmap and directional shading in the actual native shader branches");
            require(opaque.pipeline().getDepthStencilState().equals(net.minecraft.client.renderer.RenderPipelines.ENTITY_CUTOUT.getDepthStencilState()),
                    "unlit opaque material retains ordinary depth writes rather than overlay depth policy");
            require(opaque.pipeline().isCull() && !opaque.hasBlending(), "unlit GUI preserves opaque material and all_cutout");
            Field translucent = YsmModelRuntime.class.getDeclaredField("textureTranslucent");
            translucent.setAccessible(true);
            translucent.setBoolean(runtime, true);
            var blended = (net.minecraft.client.renderer.rendertype.RenderType) renderType.invoke(renderer);
            require(blended.pipeline().getDepthStencilState().equals(net.minecraft.client.renderer.RenderPipelines.ENTITY_TRANSLUCENT.getDepthStencilState()),
                    "unlit translucent material retains the ordinary translucent depth policy");
            require(blended.pipeline().isCull() && blended.hasBlending() && blended.sortOnUpload(),
                    "unlit translucent GUI retains blending, sorting and face culling");
            var guiPose = runtime.captureRenderPose(false);
            var submittedType = YsmRenderer.class.getDeclaredMethod("renderType", YsmRenderPose.class);
            submittedType.setAccessible(true);
            runtime.owner().renderMode = org.figuramc.figura.model.rendering.EntityRenderMode.WORLD;
            var deferredGui = (net.minecraft.client.renderer.rendertype.RenderType) submittedType.invoke(renderer, guiPose);
            require(deferredGui.pipeline().getShaderDefines().equals(unlitDefines),
                    "deferred GUI drawing retains submission-time unlit material after the avatar mode is restored");
            var worldPose = runtime.captureRenderPose(false);
            runtime.owner().renderMode = org.figuramc.figura.model.rendering.EntityRenderMode.FIGURA_GUI;
            var deferredWorld = (net.minecraft.client.renderer.rendertype.RenderType) submittedType.invoke(renderer, worldPose);
            require(deferredWorld.pipeline() == net.minecraft.client.renderer.RenderPipelines.ENTITY_TRANSLUCENT_CULL,
                    "later GUI extraction cannot change a submitted world material");
            runtime.owner().renderMode = org.figuramc.figura.model.rendering.EntityRenderMode.WORLD;
            var world = (net.minecraft.client.renderer.rendertype.RenderType) renderType.invoke(renderer);
            require(world.pipeline() == net.minecraft.client.renderer.RenderPipelines.ENTITY_TRANSLUCENT_CULL,
                    "gui_no_lighting never changes the world material pipeline");
        } finally {
            runtime.close();
            client.drain();
        }
    }

    private static void testGuiResources(Unsafe unsafe, RecordingClient client) throws Exception {
        Fixture first = new Fixture(unsafe, 32);
        Fixture second = new Fixture(unsafe, 33);
        try {
            var gui = first.runtime.guiResources();
            FiguraTexture front = gui.foreground();
            FiguraTexture back = gui.background();
            pixel(front, 0x80334455, "foreground resolves a named GUI image and retains alpha");
            pixel(back, 0xff667788, "background resolves a direct resource path");
            require(front == gui.get("front") && front == gui.get("textures/gui_front.png"),
                    "GUI aliases reuse one model-owned native texture");
            require(front != second.runtime.guiResources().foreground(), "same GUI names in two models remain isolated");
            var entries = first.runtimeTag.getListOrEmpty("gui_entries");
            require(entries.size() == 2, "name table and direct selection deduplicate their resource paths");
            for (var value : entries) {
                var entry = (net.minecraft.nbt.CompoundTag) value;
                byte[] expected = entry.getStringOr("path", "").endsWith("gui_front.png")
                        ? png(0x80334455) : png(0xff667788);
                require(java.util.Arrays.equals(expected, entry.getByteArray("data").orElseThrow()),
                        "GUI decoding owns a copy and does not erase caller runtime NBT");
            }
            var invalid = first.runtimeTag.copy();
            var broken = new net.minecraft.nbt.CompoundTag();
            broken.putString("path", "broken-private-name.png");
            broken.putByteArray("data", new byte[]{1, 2, 3});
            invalid.getListOrEmpty("gui_entries").add(broken);
            gui.load(invalid);
            require(image(front).isClosed() && image(back).isClosed(), "resource reload closes old GUI native images");
            require(gui.invalidImages() == 1 && gui.get("broken-private-name.png") == null,
                    "an invalid optional GUI image is counted without breaking valid resources");
            FiguraTexture replacement = gui.foreground();
            require(replacement != front, "reload creates a fresh GUI image owner");
            pixel(replacement, 0x80334455, "valid GUI image survives another optional resource failure");
            first.runtime.close();
            require(image(replacement).isClosed() && gui.foreground() == null && gui.get("front") == null,
                    "model close releases GUI native images and prevents retained getters from reviving them");
            pixel(second.runtime.guiResources().foreground(), 0x80334455, "closing one model preserves another model's GUI image");
        } finally {
            first.runtime.close();
            second.runtime.close();
            client.drain();
        }
    }

    private static void testLayerOrder(Unsafe unsafe, RecordingClient client) throws Exception {
        Fixture fixture = new Fixture(unsafe, 31);
        var runtime = fixture.runtime;
        List<String> trace = new ArrayList<>();
        YsmRenderer renderer = new YsmRenderer(runtime) {
            @Override
            public boolean render(com.mojang.blaze3d.vertex.PoseStack stack,
                                  net.minecraft.client.renderer.MultiBufferSource buffers, int light,
                                  YsmPartMask mask, YsmRenderPose pose) {
                trace.add("body");
                return true;
            }

            @Override
            public boolean renderAttachments(com.mojang.blaze3d.vertex.PoseStack stack,
                                             net.minecraft.client.renderer.MultiBufferSource buffers,
                                             int light, YsmRenderPose pose) {
                trace.add("attachments");
                return false;
            }
        };
        try {
            require(runtime.isRenderLayersFirst(), "formal loader retains render_layers_first");
            assertNativeOrder(renderer, true);
            require(runtime.isPreviewRotationDisabled(), "formal loader retains disable_preview_rotation");
            require(renderer.renderWithAttachments(null, null, 0, null), "combined pass preserves body result");
            require(trace.equals(List.of("attachments", "body")), "enabled flag submits attachments before body");
            Field flag = YsmModelRuntime.class.getDeclaredField("renderLayersFirst");
            flag.setAccessible(true);
            flag.setBoolean(runtime, false);
            assertNativeOrder(renderer, false);
            trace.clear();
            require(renderer.renderWithAttachments(null, null, 0, null), "default combined pass preserves body result");
            require(trace.equals(List.of("body", "attachments")), "disabled flag retains body-before-attachments order");
        } finally {
            runtime.close();
            client.drain();
        }
    }

    private static void assertNativeOrder(YsmRenderer renderer, boolean layersFirst) {
        var storage = new net.minecraft.client.renderer.SubmitNodeStorage();
        var body = storage.order(renderer.bodySubmitOrder());
        var nativeItems = storage.order(0);
        var buckets = new ArrayList<>(storage.getSubmitsPerOrder().values());
        require(buckets.equals(layersFirst ? List.of(nativeItems, body) : List.of(body, nativeItems)),
                "native sorted feature buckets place regular items and special model parts on the requested side of the body");
        require(body == storage.order(renderer.bodySubmitOrder()) && body != nativeItems,
                "body uses the stable native bucket independently of collection time");
        storage.clear();
    }

    private static void testHandLocators(Unsafe unsafe, RecordingClient client) throws Exception {
        String geometry = """
                {"minecraft:geometry":[{"description":{"texture_width":1,"texture_height":1},"bones":[
                  {"name":"root"}, {"name":"middle","parent":"root"},
                  {"name":"RightHandLocator","parent":"middle","pivot":[16,0,0]},
                  {"name":"LeftHandLocator","parent":"middle","pivot":[-16,0,0]}]}]}
                """;
        Fixture fixture = new Fixture(unsafe, 35, null, geometry);
        var runtime = fixture.runtime;
        try {
            runtime.getPart("root").setPos(16d, 0d, 0d);
            runtime.getPart("root").setRot(0d, 0d, 90d);
            runtime.getPart("middle").setPos(0d, 16d, 0d);
            for (boolean left : new boolean[]{false, true}) {
                var stack = new com.mojang.blaze3d.vertex.PoseStack();
                require(runtime.applyHandItemTransform(stack, left), "visible authored hand locator is accepted");
                var expected = new org.joml.Matrix4f().scaling(0.5625f, 1.125f, 0.5625f)
                        .translate(-1, 0, 0).rotateZ((float) (Math.PI / 2)).translate(0, 1, 0)
                        .translate(left ? 1 : -1, 0, 0).translate(0, -0.0625f, -0.1f)
                        .rotateX((float) (-Math.PI / 2));
                require(stack.last().pose().equals(expected, 0.00001f),
                        "both hand matrices include model scale, all ancestors, locator pivot and native item basis");
            }
            runtime.getPart("middle").setVisible(false);
            var hidden = new com.mojang.blaze3d.vertex.PoseStack();
            require(!runtime.applyHandItemTransform(hidden, false) && hidden.last().pose().equals(new org.joml.Matrix4f()),
                    "hidden hand ancestor suppresses submission without mutating the caller pose");
            runtime.getPart("middle").setVisible(true);
            runtime.getPart("LeftHandLocator").setScale(0d, 0d, 0d);
            require(!runtime.applyHandItemTransform(new com.mojang.blaze3d.vertex.PoseStack(), true),
                    "zero-scale authored locator suppresses its native hand item");
            require(runtime.applyHandItemTransform(new com.mojang.blaze3d.vertex.PoseStack(), false),
                    "hiding the left locator preserves the independent right-hand item");
        } finally {
            runtime.close();
            client.drain();
        }
    }

    private static void testSubEntityInstances(Unsafe unsafe, RecordingClient client) throws Exception {
        Fixture fixture = new Fixture(unsafe, 36);
        try {
            YsmSubEntitySmokeTest.run(unsafe, fixture.runtime, fixture.runtimeTag.getByteArray("texture").orElseThrow());
        } finally {
            fixture.runtime.close();
            client.drain();
        }
    }

    private static void testSyncModel(Unsafe unsafe, RecordingClient client) throws Exception {
        Fixture source = new Fixture(unsafe, 47);
        Fixture observer = new Fixture(unsafe, 48);
        try {
            install(source.runtime.owner(), "isHost", true);
            YsmSyncModelSmokeTest.run(source.runtime, observer.runtime);
        } finally {
            source.runtime.close();
            observer.runtime.close();
            client.drain();
        }
    }

    private static void testSyncConnection(Unsafe unsafe, RecordingClient client) throws Exception {
        Fixture source = new Fixture(unsafe, 49);
        Fixture observer = new Fixture(unsafe, 49);
        Fixture reloadedObserver = new Fixture(unsafe, 49);
        Fixture reloadedSource = new Fixture(unsafe, 49);
        try {
            install(source.runtime.owner(), "isHost", true);
            install(reloadedSource.runtime.owner(), "isHost", true);
            YsmSyncConnectionSmokeTest.run(source.runtime, observer.runtime, reloadedObserver.runtime, reloadedSource.runtime);
        } finally {
            source.runtime.close();
            observer.runtime.close();
            reloadedObserver.runtime.close();
            reloadedSource.runtime.close();
            client.drain();
        }
    }

    private static void testCullingProperty(Unsafe unsafe, RecordingClient client) throws Exception {
        Fixture fixture = new Fixture(unsafe, 30);
        YsmModelRuntime runtime = fixture.runtime;
        try {
            require(runtime.isAllCutout(), "formal loader/runtime assembly retains all_cutout");
            var renderType = YsmRenderer.class.getDeclaredMethod("renderType");
            renderType.setAccessible(true);
            var renderer = new YsmRenderer(runtime);
            var opaque = (net.minecraft.client.renderer.rendertype.RenderType) renderType.invoke(renderer);
            require(opaque.pipeline().isCull() && !opaque.hasBlending(), "opaque all_cutout uses native culled cutout");
            Field translucent = YsmModelRuntime.class.getDeclaredField("textureTranslucent");
            translucent.setAccessible(true);
            translucent.setBoolean(runtime, true);
            var blended = (net.minecraft.client.renderer.rendertype.RenderType) renderType.invoke(renderer);
            var nativeBlended = net.minecraft.client.renderer.rendertype.RenderTypes.entityTranslucent(runtime.texture().getLocation());
            require(blended.pipeline().isCull() && blended.hasBlending(), "all_cutout preserves translucent alpha blending");
            require(blended.outputTarget() == nativeBlended.outputTarget() && blended.sortOnUpload(),
                    "culled translucent models retain the world output target and transparency sorting");
            require(renderType.invoke(renderer) == blended, "repeated render passes reuse the model-owned render type");
            require(runtime.setTexture("c"), "skin switch succeeds under all_cutout");
            translucent.setBoolean(runtime, true);
            require(renderType.invoke(renderer) != blended, "skin switching invalidates the cached texture binding");
            Field allCutout = YsmModelRuntime.class.getDeclaredField("allCutout");
            allCutout.setAccessible(true);
            allCutout.setBoolean(runtime, false);
            require(renderType.invoke(renderer) == net.minecraft.client.renderer.rendertype.RenderTypes.entityTranslucent(runtime.texture().getLocation()),
                    "disabled all_cutout retains the existing native translucent path");
            translucent.setBoolean(runtime, false);
            require(renderType.invoke(renderer) == net.minecraft.client.renderer.rendertype.RenderTypes.entityCutout(runtime.texture().getLocation()),
                    "disabled all_cutout retains the existing native opaque path");
        } finally {
            runtime.close();
            client.drain();
        }
    }

    private static void testModelSelection(Unsafe unsafe, RecordingClient client) throws Exception {
        Fixture first = new Fixture(unsafe, 1);
        Fixture second = new Fixture(unsafe, 2);
        List<NativeImage> firstImages = new ArrayList<>();
        try {
            YsmMaterialTexture firstMaterial = (YsmMaterialTexture) first.runtime.texture();
            YsmMaterialTexture secondMaterial = (YsmMaterialTexture) second.runtime.texture();
            firstImages.add(image(firstMaterial));
            FiguraTexture normal = firstMaterial.normalTexture();
            FiguraTexture specular = firstMaterial.specularTexture();
            firstImages.add(image(normal));
            firstImages.add(image(specular));
            pixel(firstMaterial, 0xff112233, "native base comes from selected material");
            pixel(normal, 0xff8080ff, "native normal remains paired with the selected base after source close");
            pixel(specular, 0xff090a0b, "native specular remains paired with the selected base after source close");
            require(normal == firstMaterial.normalTexture(), "repeated channel requests reuse the live object");
            require(normal != secondMaterial.normalTexture(), "two models with the same material ID own separate channel objects");
            pixel(secondMaterial.normalTexture(), 0xff8080ff, "second model channel is independently decoded");

            normal.close();
            require(image(normal).isClosed(), "Iris-style channel release closes its native image");
            FiguraTexture recreated = firstMaterial.normalTexture();
            require(recreated != normal, "cleared PBR holder gets a fresh channel object");
            firstImages.add(image(recreated));
            pixel(recreated, 0xff8080ff, "recreated channel retains original decoded pixels");
            require(first.runtime.setTexture("c"), "runtime switches skin through its public selector");
            for (NativeImage image : firstImages) {
                require(image.isClosed() && image.getPointer() == 0, "switch releases every old native image");
            }
            YsmMaterialTexture next = (YsmMaterialTexture) first.runtime.texture();
            firstImages.add(image(next));
            firstImages.add(image(next.normalTexture()));
            firstImages.add(image(next.specularTexture()));
            pixel(next.normalTexture(), 0xffabcdef, "new skin selects its own normal");
            pixel(next.specularTexture(), 0xff998877, "new skin selects its own specular");
            require(!first.runtime.setTexture("missing") && first.runtime.texture() == next, "invalid selection preserves the current material");
            require(first.runtime.setTexture("textures/plain.png"), "existing path alias remains selectable");
            YsmMaterialTexture plain = (YsmMaterialTexture) first.runtime.texture();
            firstImages.add(image(plain));
            require(plain.normalTexture() == null && plain.specularTexture() == null, "plain skin never inherits the previous skin channels");
            require(!image(secondMaterial.normalTexture()).isClosed(), "first model switching does not release second model resources");
        } finally {
            first.runtime.close();
            second.runtime.close();
            client.drain();
        }
        for (NativeImage image : firstImages) {
            require(image.isClosed() && image.getPointer() == 0, "model close releases all observed native images");
        }
    }

    private static void testOwnershipAndDeferredClose(Unsafe unsafe, RecordingClient client) throws Exception {
        Avatar owner = owner(unsafe, 3);
        byte[] normalData = png(0xff667788);
        YsmMaterialTexture material = new YsmMaterialTexture(owner, "deferred", png(0xff010203), normalData, new byte[0]);
        java.util.Arrays.fill(normalData, (byte) 0);
        FiguraTexture normal = material.normalTexture();
        pixel(normal, 0xff667788, "native material owns its channel input independently of the caller");
        NativeImage baseImage = image(material);
        NativeImage normalImage = image(normal);
        Thread close = new Thread(material::close, "material-owner-close");
        close.start();
        close.join();
        require(!baseImage.isClosed() && !normalImage.isClosed(), "background unload queues native release onto its designated thread");
        require(material.normalTexture() == null && material.specularTexture() == null, "closed model cannot recreate channels while cleanup is queued");
        client.drain();
        require(baseImage.isClosed() && normalImage.isClosed(), "queued native cleanup closes base and channel");
        material.close();
        require(baseImage.getPointer() == 0 && normalImage.getPointer() == 0, "repeated cleanup remains safe");
    }

    private static void testSelectionRestore(Unsafe unsafe, RecordingClient client, Path directory) throws Exception {
        Path firstPath = directory.resolve("model-a");
        Path secondPath = directory.resolve("model-b");
        YsmTextureSelectionStore.set(firstPath, "c");
        YsmTextureSelectionStore.set(secondPath, "plain");
        require(Files.isRegularFile(org.figuramc.figura.FiguraMod.getCacheDirectory().resolve("ysm_textures.nbt")),
                "skin selections use the actual disk cache");
        clearSelections();
        Fixture first = new Fixture(unsafe, 4, firstPath);
        Fixture second = new Fixture(unsafe, 5, secondPath);
        try {
            YsmMaterialTexture paired = (YsmMaterialTexture) first.runtime.texture();
            require(first.runtime.textureId().equals("c"), "formal loader restores the persisted skin ID");
            pixel(paired.normalTexture(), 0xffabcdef, "restored skin receives its own normal");
            pixel(paired.specularTexture(), 0xff998877, "restored skin receives its own specular");
            YsmMaterialTexture plain = (YsmMaterialTexture) second.runtime.texture();
            require(second.runtime.textureId().equals("plain") && plain.normalTexture() == null
                    && plain.specularTexture() == null, "other model path restores its independent plain skin");
        } finally {
            first.runtime.close();
            second.runtime.close();
            client.drain();
        }
    }

    private static void clearSelections() throws Exception {
        Field values = YsmTextureSelectionStore.class.getDeclaredField("SELECTIONS");
        values.setAccessible(true);
        ((Map<?, ?>) values.get(null)).clear();
        Field loaded = YsmTextureSelectionStore.class.getDeclaredField("loaded");
        loaded.setAccessible(true);
        loaded.setBoolean(null, false);
    }

    private static final class Fixture {
        private final YsmModelRuntime runtime;
        private final net.minecraft.nbt.CompoundTag runtimeTag;

        private Fixture(Unsafe unsafe, int id) throws Exception {
            this(unsafe, id, null);
        }

        private Fixture(Unsafe unsafe, int id, Path selectionPath) throws Exception {
            this(unsafe, id, selectionPath, null);
        }

        private Fixture(Unsafe unsafe, int id, Path selectionPath, String geometry) throws Exception {
            Avatar owner = owner(unsafe, id);
            Map<String, byte[]> resources = new HashMap<>();
            resources.put("ysm.json", bytes("""
                    {"files":{"player":{"model":{"main":"main.json"},"texture":[
                     {"id":"a","uv":"textures/a.png","normal":"textures/a_n.png","specular":"textures/a_s.png"},
                     {"id":"plain","uv":"textures/plain.png"},
                     {"id":"clear","uv":"textures/clear.png"},
                     {"id":"c","uv":"textures/c.png","normal":"textures/c_n.png","specular":"textures/c_s.png"}]}},
                     "properties":{"width_scale":0.6,"height_scale":1.2,"preview_animation":"gui","default_texture":"a","all_cutout":true,"render_layers_first":true,"disable_preview_rotation":true,"gui_no_lighting":true,
                      "gui_foreground":"front","gui_background":"textures/gui_back.png",
                      "ysm_background_images":{"front":"textures/gui_front.png"}}}
                    """));
            resources.put("main.json", bytes("""
                    {"minecraft:geometry":[{"description":{"texture_width":1,"texture_height":1},"bones":[{"name":"root","cubes":[{"origin":[0,0,0],"size":[1,1,1],"uv":[0,0]}]}]}]}
                    """));
            if (geometry != null) {
                resources.put("main.json", bytes(geometry));
            }
            resources.put("animations/main.animation.json", bytes("""
                    {"animations":{
                      "idle":{"loop":true,"animation_length":1,"bones":{"root":{"position":[0,16,0]}}},
                      "gui":{"loop":true,"animation_length":2,"bones":{"root":{"position":["fn.preview_value()+q.anim_time*16",0,0]}},
                        "timeline":{"0":"v.preview_events=v.preview_events+1;"}}
                    }}
                    """));
            resources.put("functions/preview_value.molang", bytes("v.preview_calls=v.preview_calls+1; return v.pose;"));
            resources.put("textures/gui_front.png", png(0x80334455));
            resources.put("textures/gui_back.png", png(0xff667788));
            resources.put("textures/a.png", png(0xff112233));
            resources.put("textures/a_n.png", png(0xff8080ff));
            resources.put("textures/a_s.png", png(0xff090a0b));
            resources.put("textures/plain.png", png(0xff445566));
            resources.put("textures/clear.png", png(0x00000000));
            resources.put("textures/c.png", png(0xffffffff));
            resources.put("textures/c_n.png", png(0xffabcdef));
            resources.put("textures/c_s.png", png(0xff998877));
            try (var source = new YsmMemoryResourceSource("native-material", resources);
                 var data = YsmModelData.read(source, YsmSourceIdentity.ContainerKind.DIRECTORY, () -> false)) {
                var select = YsmAvatarLoader.class.getDeclaredMethod("selectTexture", Path.class, List.class, String.class);
                select.setAccessible(true);
                YsmTextureOption selected = (YsmTextureOption) select.invoke(null, selectionPath,
                        data.manifest().textures(), data.manifest().defaultTexture());
                runtimeTag = YsmAvatarLoader.toRuntimeTag(data, selected);
                runtime = YsmModelRuntime.fromData(owner, data, runtimeTag);
                install(owner, "ysmRuntime", runtime);
                runtime.restoreControls();
            }
        }
    }

    private static Avatar owner(Unsafe unsafe, int id) throws Exception {
        Avatar owner = (Avatar) unsafe.allocateInstance(Avatar.class);
        install(owner, "owner", new UUID(0, id));
        install(owner, "controls", new AvatarControlRuntime());
        install(owner, "molangContext", new Avatar.MolangContext(owner));
        return owner;
    }

    private static void install(Avatar owner, String name, Object value) throws Exception {
        Field field = Avatar.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(owner, value);
    }

    private static NativeImage image(FiguraTexture texture) throws Exception {
        Field field = FiguraTexture.class.getDeclaredField("nativeImageTexture");
        field.setAccessible(true);
        return (NativeImage) field.get(texture);
    }

    private static void pixel(FiguraTexture texture, int expected, String message) throws Exception {
        NativeImage image = image(texture);
        require(!image.isClosed() && image.getWidth() == 1 && image.getHeight() == 1 && image.getPixel(0, 0) == expected, message);
    }

    private static byte[] png(int color) throws Exception {
        BufferedImage image = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
        image.setRGB(0, 0, color);
        try (var output = new ByteArrayOutputStream()) {
            require(ImageIO.write(image, "PNG", output), "PNG writer available");
            return output.toByteArray();
        } finally {
            image.flush();
        }
    }

    private static byte[] bytes(String value) {
        return value.getBytes(StandardCharsets.UTF_8);
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    private static final class RecordingClient extends Minecraft {
        private RecordingTextures manager;
        private net.minecraft.client.sounds.SoundManager sounds;
        private ConcurrentLinkedQueue<Runnable> queue;

        private RecordingClient() {
            super(null);
        }

        @Override
        public net.minecraft.world.entity.Entity getCameraEntity() {
            return null;
        }

        @Override
        public net.minecraft.client.User getUser() {
            return new net.minecraft.client.User("CPU preview fixture", new UUID(0, 999), "",
                    java.util.Optional.empty(), java.util.Optional.empty());
        }

        @Override
        public TextureManager getTextureManager() {
            return manager;
        }

        @Override
        public net.minecraft.client.sounds.SoundManager getSoundManager() {
            return sounds;
        }

        @Override
        public void execute(Runnable task) {
            if (RenderSystem.isOnRenderThread()) {
                task.run();
            } else {
                queue.add(task);
            }
        }

        private void drain() {
            Runnable task;
            while ((task = queue.poll()) != null) {
                task.run();
            }
        }
    }

    private static final class RecordingTextures extends TextureManager implements TextureManagerAccessor {
        private Map<Identifier, AbstractTexture> textures;

        private RecordingTextures() {
            super(null);
        }

        @Override
        public Map<Identifier, AbstractTexture> getByPath() {
            return textures;
        }
    }
}
