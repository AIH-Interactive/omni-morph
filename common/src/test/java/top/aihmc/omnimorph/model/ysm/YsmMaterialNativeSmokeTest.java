package top.aihmc.omnimorph.model.ysm;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.SharedConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.resources.Identifier;
import net.minecraft.server.Bootstrap;
import top.aihmc.omnimorph.avatar.Avatar;
import top.aihmc.omnimorph.avatar.control.AvatarControlRuntime;
import top.aihmc.omnimorph.avatar.ysm.YsmAvatarLoader;
import top.aihmc.omnimorph.avatar.ysm.YsmMemoryResourceSource;
import top.aihmc.omnimorph.avatar.ysm.YsmModelData;
import top.aihmc.omnimorph.avatar.ysm.YsmSourceIdentity;
import top.aihmc.omnimorph.avatar.ysm.YsmTextureOption;
import top.aihmc.omnimorph.avatar.ysm.YsmTextureSelectionStore;
import top.aihmc.omnimorph.mixin.render.TextureManagerAccessor;
import top.aihmc.omnimorph.math.vector.FiguraVec3;
import top.aihmc.omnimorph.model.rendering.texture.FiguraTexture;
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
        run(args, null);
    }

    /** Reuses the real loader/runtime/native-image scope for acceleration renderer contract tests. */
    public static void replayAcceleration(Path path, java.util.function.Consumer<YsmModelRuntime> replay) throws Exception {
        run(new String[]{"--acceleration", path.toString()}, replay);
    }

    /** Texture allocation recorder for native vertex tests; supplies no real GPU or visual evidence. */
    public static AutoCloseable recordingTextureDevice() throws ReflectiveOperationException {
        Field device = RenderSystem.class.getDeclaredField("DEVICE");
        device.setAccessible(true);
        Object previous = device.get(null);
        device.set(null, new PublicationDevice());
        return () -> device.set(null, previous);
    }

    /** Existing native sound fixture, needed when bounded callback errors perform ordinary Avatar cleanup. */
    public static AutoCloseable recordingSoundManager(Unsafe unsafe) throws Exception {
        var client = (RecordingClient) Minecraft.getInstance();
        var previous = client.sounds;
        client.sounds = YsmStabilitySmokeTest.soundManager(unsafe, true);
        return () -> client.sounds = previous;
    }

    private static void run(String[] args, java.util.function.Consumer<YsmModelRuntime> replay) throws Exception {
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
        var toasts = (RecordingToasts) unsafe.allocateInstance(RecordingToasts.class);
        toasts.entries = new ArrayList<>();
        client.toasts = toasts;
        client.manager = (RecordingTextures) unsafe.allocateInstance(RecordingTextures.class);
        client.manager.textures = new HashMap<>();
        Field byPath = TextureManager.class.getDeclaredField("byPath");
        byPath.setAccessible(true);
        byPath.set(client.manager, client.manager.textures);
        Field tickableTextures = TextureManager.class.getDeclaredField("tickableTextures");
        tickableTextures.setAccessible(true);
        tickableTextures.set(client.manager, new java.util.HashSet<>());
        try {
            SharedConstants.tryDetectVersion();
            Bootstrap.bootStrap();
            if (previousThread == null) {
                RenderSystem.initRenderThread();
            } else {
                RenderSystem.assertOnRenderThread();
            }
            instance.set(null, client);
            top.aihmc.omnimorph.permissions.PermissionManager.init();
            if ((args.length == 2 && args[0].equals("--library-preview"))
                    || (args.length == 2 && args[0].equals("--cosmetic-sources"))
                    || (args.length == 1 && args[0].equals("--library-equipment"))
                    || (args.length == 1 && args[0].equals("--library-preparation"))
                    || (args.length == 1 && args[0].equals("--library-catalog"))
                    || (args.length == 1 && args[0].equals("--library-upload"))) {
                client.sounds = YsmStabilitySmokeTest.soundManager(unsafe, true);
                Field particles = Minecraft.class.getDeclaredField("particleEngine");
                particles.setAccessible(true);
                Object previousParticles = particles.get(client);
                try {
                    particles.set(client, unsafe.allocateInstance(AvatarAsyncLoadSmokeTest.RecordingParticles.class));
                    if (args[0].equals("--cosmetic-sources")) {
                        top.aihmc.omnimorph.model.outfit.CosmeticSourceSmokeTest.run(Path.of(args[1]), client::drain);
                    } else if (args[0].equals("--library-upload")) {
                        top.aihmc.omnimorph.model.library.LibraryUploadSmokeTest.run(client::drain);
                    } else if (args[0].equals("--library-catalog")) {
                        top.aihmc.omnimorph.model.library.ModelLibraryCatalogSmokeTest.run(client::drain);
                    } else if (args[0].equals("--library-preparation")) {
                        top.aihmc.omnimorph.model.library.LibraryPackagePreparationSmokeTest.run();
                    } else if (args[0].equals("--library-equipment")) {
                        top.aihmc.omnimorph.model.library.LibraryEquipmentSmokeTest.run(client::drain);
                    } else {
                        top.aihmc.omnimorph.model.library.LibraryPreviewRuntimeSmokeTest.run(
                                unsafe, client::drain, Path.of(args[1]));
                    }
                } finally {
                    client.drain();
                    particles.set(client, previousParticles);
                }
                return;
            }
            if (args.length == 1 && args[0].equals("--cloud-refresh")) {
                client.sounds = YsmStabilitySmokeTest.soundManager(unsafe, true);
                AvatarAsyncLoadSmokeTest.run(unsafe, client, client::drain);
                return;
            }
            if (args.length == 2 && args[0].equals("--acceleration")) {
                client.sounds = YsmStabilitySmokeTest.soundManager(unsafe);
                Field device = RenderSystem.class.getDeclaredField("DEVICE");
                device.setAccessible(true);
                Object previousDevice = device.get(null);
                try {
                    device.set(null, new PublicationDevice());
                    Avatar avatar = owner(unsafe, 918);
                    try (var data = YsmModelData.read(Path.of(args[1]), () -> false);
                         var runtime = YsmModelRuntime.fromData(avatar, data,
                                 YsmAvatarLoader.toRuntimeTag(data, data.manifest().textures().getFirst()))) {
                        install(avatar, "ysmRuntime", runtime);
                        java.util.Objects.requireNonNull(replay).accept(runtime);
                    }
                } finally {
                    client.drain();
                    device.set(null, previousDevice);
                }
                return;
            }
            if (args.length == 2 && args[0].equals("--async-encrypted")) {
                client.sounds = YsmStabilitySmokeTest.soundManager(unsafe, true);
                AvatarAsyncLoadSmokeTest.run(unsafe, client, client::drain, Path.of(args[1]));
                return;
            }
            if (args.length == 2 && args[0].equals("--held-items")) {
                client.sounds = YsmStabilitySmokeTest.soundManager(unsafe);
                Field device = RenderSystem.class.getDeclaredField("DEVICE");
                device.setAccessible(true);
                Object previousDevice = device.get(null);
                try {
                    device.set(null, new PublicationDevice());
                    testAttachmentGeometry(unsafe, client);
                    Path root = Path.of(args[1]);
                    int checked = 0;
                    int encrypted = 0;
                    try (var paths = Files.list(root)) {
                        for (Path path : paths.sorted().toList()) {
                            if (top.aihmc.omnimorph.avatar.ysm.YsmAvatarDetector.isYsmAvatar(path)) {
                                if (testCorpusHeldItems(unsafe, client, path, checked++)) {
                                    encrypted++;
                                }
                            }
                        }
                    }
                    require(checked > 0 && encrypted > 0, "corpus includes plain and encrypted runtime inputs");
                    System.out.println("World attachment corpus: models=" + checked + " encrypted=" + encrypted + " PASS");
                } finally {
                    client.drain();
                    device.set(null, previousDevice);
                }
                return;
            }
            if (args.length == 2 && args[0].equals("--controlled-base-geometry")) {
                client.sounds = YsmStabilitySmokeTest.soundManager(unsafe);
                testControlledBaseGeometry(unsafe, client, Path.of(args[1]));
                return;
            }
            if (args.length > 0) {
                require(args.length == 4 && args[0].equals("--stability"), "explicit stability mode arguments");
                client.sounds = YsmStabilitySmokeTest.soundManager(unsafe);
                YsmStabilitySmokeTest.run(unsafe, client::drain, Path.of(args[1]), Path.of(args[2]), Path.of(args[3]));
                return;
            }
            testControlledBasePlayback(unsafe, client);
            testControllerChannelOwnership(unsafe, client);
            testRunningControllerPhase(unsafe, client);
            testWalkingUsesAvailableLocomotion(unsafe, client);
            Field device = RenderSystem.class.getDeclaredField("DEVICE");
            device.setAccessible(true);
            Object previousDevice = device.get(null);
            Field samplersField = com.mojang.blaze3d.systems.SamplerCache.class.getDeclaredField("samplers");
            samplersField.setAccessible(true);
            var samplers = (com.mojang.blaze3d.textures.GpuSampler[]) samplersField.get(RenderSystem.getSamplerCache());
            var previousSamplers = samplers.clone();
            var publicationDevice = new PublicationDevice();
            try {
                device.set(null, publicationDevice);
                RenderSystem.getSamplerCache().initialize();
                testTexturePublication(unsafe, client);
                testLateTextureBindings(unsafe, client);
                testTextureUploadLifetime(unsafe, client, publicationDevice);
                top.aihmc.omnimorph.lua.api.particle.CustomParticleNativeSmokeTest.run(
                        unsafe, owner(unsafe, 690), owner(unsafe, 691), client::drain);
                testYsmComplexity(unsafe, client);
                testBakedPreviewGeometry(unsafe, client);
                testPreviewBoneFade(unsafe, client);
                testStandingPreviewVertices(unsafe, client);
            } finally {
                client.drain();
                RenderSystem.getSamplerCache().close();
                System.arraycopy(previousSamplers, 0, samplers, 0, samplers.length);
                device.set(null, previousDevice);
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
            client.sounds = YsmStabilitySmokeTest.soundManager(unsafe, true);
            AvatarAsyncLoadSmokeTest.run(unsafe, client, client::drain);
            AvatarLoadProgressSmokeTest.run();
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

    private static void testControlledBasePlayback(Unsafe unsafe, RecordingClient client) throws Exception {
        Fixture fixture = new Fixture(unsafe, 59);
        try {
            for (boolean controlled : new boolean[] {false, true}) {
                var input = fixture.runtimeTag.copy();
                var animations = new net.minecraft.nbt.ListTag();
                var clip = new net.minecraft.nbt.CompoundTag();
                clip.putByteArray("data", bytes("""
                        {"animations":{"parallel_0":{"loop":true,"animation_length":1,
                          "bones":{"root":{"rotation":[0,0,45]}}}}}
                        """));
                animations.add(clip);
                input.put("animations", animations);
                var controllers = new net.minecraft.nbt.ListTag();
                if (controlled) {
                    var controller = new net.minecraft.nbt.CompoundTag();
                    controller.putByteArray("data", bytes("""
                            {"animation_controllers":{"parallel0":{"states":{"default":{
                              "animations":[{"parallel_0":"v.weight"}]}}}}}
                            """));
                    controllers.add(controller);
                }
                input.put("animation_controllers", controllers);
                Avatar avatar = owner(unsafe, controlled ? 61 : 60);
                try (var runtime = YsmModelRuntime.fromNbt(avatar, input)) {
                    install(avatar, "ysmRuntime", runtime);
                    var context = avatar.getMolangContext();
                    context.variables.setPath("weight", 1f);
                    var evaluator = top.aihmc.omnimorph.molang.runtime.ExpressionEvaluator.evaluator(context);
                    runtime.animations().controllerRuntime().update(evaluator, 0f, false);
                    var sample = top.aihmc.omnimorph.model.ysm.animation.YsmAnimationPlayer.class.getDeclaredMethod(
                            "sampleAnimations", top.aihmc.omnimorph.molang.runtime.ExpressionEvaluator.class, boolean.class);
                    sample.setAccessible(true);
                    sample.invoke(runtime.animations(), evaluator, false);
                    require(Math.abs(runtime.getPart("root").animRotRaw().z - 45d) < 0.0001,
                            "a 45 degree parallel clip must apply once, controlled=" + controlled
                                    + " actual=" + runtime.getPart("root").animRotRaw().z);
                    if (controlled) {
                        context.variables.setPath("weight", 0f);
                        runtime.animations().controllerRuntime().update(evaluator, 0f, false);
                        sample.invoke(runtime.animations(), evaluator, false);
                        require(runtime.getPart("root").animRotRaw().z == 0d,
                                "a controller's zero weight cannot be bypassed by legacy base autoplay");
                    }
                }
            }
        } finally {
            fixture.runtime.close();
            client.drain();
        }
    }

    private static void testControllerChannelOwnership(Unsafe unsafe, RecordingClient client) throws Exception {
        Fixture fixture = new Fixture(unsafe, 62);
        try {
            var input = fixture.runtimeTag.copy();
            var animations = new net.minecraft.nbt.ListTag();
            var clip = new net.minecraft.nbt.CompoundTag();
            clip.putByteArray("data", bytes("""
                    {"animations":{
                      "earlier":{"loop":true,"bones":{"root":{
                        "rotation":[10,20,30],"position":[1,2,3],"scale":[2,3,4]}}},
                      "later":{"loop":true,"bones":{"root":{
                        "rotation":[40,50,60],"position":[4,5,6],"scale":[1,1,1]}}}
                    }}
                    """));
            animations.add(clip);
            input.put("animations", animations);
            var controllers = new net.minecraft.nbt.ListTag();
            var controller = new net.minecraft.nbt.CompoundTag();
            controller.putByteArray("data", bytes("""
                    {"animation_controllers":{
                      "pre_parallel0":{"states":{"default":{"animations":["earlier"]}}},
                      "parallel0":{"states":{"default":{"animations":["later"]}}}
                    }}
                    """));
            controllers.add(controller);
            input.put("animation_controllers", controllers);
            Avatar avatar = owner(unsafe, 63);
            try (var runtime = YsmModelRuntime.fromNbt(avatar, input)) {
                install(avatar, "ysmRuntime", runtime);
                var evaluator = top.aihmc.omnimorph.molang.runtime.ExpressionEvaluator.evaluator(avatar.getMolangContext());
                runtime.animations().controllerRuntime().update(evaluator, 0f, false);
                var sample = top.aihmc.omnimorph.model.ysm.animation.YsmAnimationPlayer.class.getDeclaredMethod(
                        "sampleAnimations", top.aihmc.omnimorph.molang.runtime.ExpressionEvaluator.class, boolean.class);
                sample.setAccessible(true);
                sample.invoke(runtime.animations(), evaluator, false);
                var root = runtime.getPart("root");
                require(root.animRotRaw().x == 40d && root.animRotRaw().y == 50d && root.animRotRaw().z == 60d,
                        "later controller owns rotation instead of adding earlier pose: " + root.animRotRaw());
                require(root.animPosRaw().x == 4d && root.animPosRaw().y == 5d && root.animPosRaw().z == 6d,
                        "later controller owns position");
                require(root.animScaleRaw().x == 1d && root.animScaleRaw().y == 1d && root.animScaleRaw().z == 1d,
                        "later controller owns scale instead of multiplying earlier pose");
            }
        } finally {
            fixture.runtime.close();
            client.drain();
        }
    }

    public static final class RunningRenderState extends net.minecraft.client.renderer.entity.state.LivingEntityRenderState {
        public boolean isSprinting = true;
    }

    private static void testRunningControllerPhase(Unsafe unsafe, RecordingClient client) throws Exception {
        Fixture fixture = new Fixture(unsafe, 70);
        try {
            var input = fixture.runtimeTag.copy();
            var animations = new net.minecraft.nbt.ListTag();
            var clip = new net.minecraft.nbt.CompoundTag();
            clip.putByteArray("data", bytes("""
                    {"animations":{
                      "run":{"loop":true,"bones":{"root":{"rotation":[0,0,45]}}},
                      "setup":{"loop":true,"bones":{"root":{"rotation":[0,0,10]}}},
                      "walk":{"loop":true,"bones":{"root":{"rotation":[0,0,15]}}},
                      "idle":{"loop":true,"bones":{"root":{"rotation":[0,0,5]}}}
                    }}
                    """));
            animations.add(clip);
            input.put("animations", animations);
            var controllers = new net.minecraft.nbt.ListTag();
            var controller = new net.minecraft.nbt.CompoundTag();
            controller.putByteArray("data", bytes("""
                    {"animation_controllers":{"player.pre_parallel0":{
                      "states":{"default":{"animations":["setup"]}}}}}
                    """));
            controllers.add(controller);
            input.put("animation_controllers", controllers);
            Avatar avatar = owner(unsafe, 71);
            try (var runtime = YsmModelRuntime.fromNbt(avatar, input)) {
                install(avatar, "ysmRuntime", runtime);
                var state = new RunningRenderState();
                String[] expectedClips = {"walk", "run", "idle"};
                double[] expectedAngles = {15d, 45d, 5d};
                for (int mode = 0; mode < expectedClips.length; mode++) {
                    state.isSprinting = mode == 1;
                    state.walkAnimationSpeed = mode == 2 ? 0f : 1f;
                    for (int tick = 0; tick <= 20; tick++) {
                        state.ageInTicks = mode * 21 + tick;
                        top.aihmc.omnimorph.model.ysm.animation.YsmRenderFrameClock.beginFrame();
                        runtime.updateAnimations(state, null);
                    }
                    require(runtime.animations().getActiveAnimation(expectedClips[mode]) != null,
                            "movement input selects " + expectedClips[mode]);
                    require(Math.abs(runtime.getPart("root").animRotRaw().z - expectedAngles[mode]) < 0.0001,
                            "pre_parallel setup must precede " + expectedClips[mode] + ": " + runtime.getPart("root").animRotRaw());
                }
            }
        } finally {
            fixture.runtime.close();
            client.drain();
        }
    }

    private static void testWalkingUsesAvailableLocomotion(Unsafe unsafe, RecordingClient client) throws Exception {
        Fixture fixture = new Fixture(unsafe, 72);
        try {
            var input = fixture.runtimeTag.copy();
            var animations = new net.minecraft.nbt.ListTag();
            var clip = new net.minecraft.nbt.CompoundTag();
            clip.putByteArray("data", bytes("""
                    {"animations":{"run":{"loop":true,"animation_length":1,
                      "bones":{"root":{"rotation":["math.sin(q.anim_time*360)*30",0,0]}}}}}
                    """));
            animations.add(clip);
            input.put("animations", animations);
            Avatar avatar = owner(unsafe, 73);
            try (var runtime = YsmModelRuntime.fromNbt(avatar, input)) {
                install(avatar, "ysmRuntime", runtime);
                var state = new net.minecraft.client.renderer.entity.state.LivingEntityRenderState();
                state.walkAnimationSpeed = 1f;
                for (int tick = 0; tick <= 25; tick++) {
                    state.ageInTicks = tick;
                    top.aihmc.omnimorph.model.ysm.animation.YsmRenderFrameClock.beginFrame();
                    runtime.updateAnimations(state, null);
                }
                require(runtime.animations().getActiveAnimation("run") != null,
                        "walking must use the available run clip when the model has no walk clip");
                require(Math.abs(runtime.getPart("root").animRotRaw().x) > 1,
                        "walking fallback must sample a changing locomotion pose");
            }
        } finally {
            fixture.runtime.close();
            client.drain();
        }
    }

    private static void auditMovementBindings(net.minecraft.nbt.CompoundTag tag, Avatar avatar) {
        var bindings = Avatar.createMolangBindings(avatar.getMolangContext());
        var engine = top.aihmc.omnimorph.molang.MolangEngine.fromCustomBinding(bindings);
        int expressionCount = 0;
        var interpolationModes = new java.util.TreeSet<String>();
        var used = new java.util.TreeSet<String>();
        var missing = new java.util.TreeSet<String>();
        var pattern = java.util.regex.Pattern.compile("\\b(q|query|math|ysm|ctrl)\\.([a-zA-Z_][a-zA-Z_0-9]*)");
        for (String kind : List.of("animations", "animation_controllers", "functions")) {
            for (var value : tag.getListOrEmpty(kind)) {
                if (!(value instanceof net.minecraft.nbt.CompoundTag entry)) {
                    continue;
                }
                String source = new String(entry.getByteArray("data").orElse(new byte[0]), StandardCharsets.UTF_8);
                if ("animations".equals(kind)) {
                    var animations = com.google.gson.JsonParser.parseString(source).getAsJsonObject().getAsJsonObject("animations");
                    for (var animation : animations.entrySet()) {
                        var bones = animation.getValue().getAsJsonObject().getAsJsonObject("bones");
                        if (bones != null) {
                            expressionCount += auditChannelExpressions(bones, engine, interpolationModes);
                        }
                    }
                }
                var matcher = pattern.matcher(source);
                while (matcher.find()) {
                    String namespace = matcher.group(1);
                    String name = matcher.group(2);
                    used.add(namespace + "." + name);
                    Object binding = bindings.getProperty(namespace);
                    if (!(binding instanceof top.aihmc.omnimorph.molang.runtime.binding.ObjectBinding object)
                            || object.getProperty(name) == null) {
                        missing.add(namespace + "." + name);
                    }
                }
            }
        }
        require(missing.isEmpty(), "reported model uses no unresolved query/math/ysm/ctrl bindings: " + missing);
        System.out.println("Movement binding audit: used=" + used.size() + " unresolved=" + missing
                + " channelExpressionsParsed=" + expressionCount + " interpolationModes=" + interpolationModes);
    }

    private static int auditChannelExpressions(com.google.gson.JsonElement value,
            top.aihmc.omnimorph.molang.MolangEngine engine, java.util.Set<String> interpolationModes) {
        int count = 0;
        if (value.isJsonObject()) {
            for (var entry : value.getAsJsonObject().entrySet()) {
                if ("lerp_mode".equals(entry.getKey()) || "easing".equals(entry.getKey())) {
                    interpolationModes.add(entry.getValue().getAsString());
                } else {
                    count += auditChannelExpressions(entry.getValue(), engine, interpolationModes);
                }
            }
        } else if (value.isJsonArray()) {
            for (var component : value.getAsJsonArray()) {
                count += auditChannelExpressions(component, engine, interpolationModes);
            }
        } else if (value.isJsonPrimitive() && value.getAsJsonPrimitive().isString() && !value.getAsString().isBlank()) {
            try {
                require(!engine.parse(value.getAsString()).isEmpty(), "model channel expression must parse");
            } catch (Exception failure) {
                // Do not include decrypted expression text in errors or logs.
                throw new AssertionError("reported model contains an unparseable channel expression");
            }
            count++;
        }
        return count;
    }

    private static void testControlledBaseGeometry(Unsafe unsafe, RecordingClient client, Path path) throws Exception {
        Avatar avatar = owner(unsafe, 58);
        try (var data = top.aihmc.omnimorph.avatar.ysm.YsmModelData.read(path, () -> false)) {
            var tag = YsmAvatarLoader.toRuntimeTag(data, data.manifest().textures().getFirst());
            try (var runtime = YsmModelRuntime.fromData(avatar, data, tag)) {
                install(avatar, "ysmRuntime", runtime);
                auditMovementBindings(tag, avatar);
                var run = runtime.animations().getClips().get("run");
                long setupBones = run == null ? 0 : runtime.animations().getControllers().values().stream()
                        .filter(controller -> top.aihmc.omnimorph.model.ysm.controller.YsmControllerSlot.executionOrder(controller.name()) < 20)
                        .flatMap(controller -> controller.states().values().stream())
                        .flatMap(state -> state.animations().stream())
                        .map(reference -> runtime.animations().getClips().get(reference.animation()))
                        .filter(java.util.Objects::nonNull)
                        .flatMap(clip -> clip.boneAnimations.keySet().stream())
                        .filter(run.boneAnimations::containsKey).distinct().count();
                System.out.println("Movement overlap audit: nativeRun=" + (run != null) + " preMainRunBones=" + setupBones);
                System.out.println("Controlled base geometry: encrypted=" + data.encrypted() + " bones=" + runtime.geometry().bones.size()
                        + " clips=" + runtime.animations().getClips().values().stream().distinct().count());
                reportGeometryBounds(runtime, "bind");
                for (float time : new float[] {0f, 1f, 20f, 60f, 200f}) {
                    var state = new net.minecraft.client.renderer.entity.state.LivingEntityRenderState();
                    state.ageInTicks = time;
                    top.aihmc.omnimorph.model.ysm.animation.YsmRenderFrameClock.beginFrame();
                    runtime.updateAnimations(state, null);
                    assertControllerChannelOwnership(runtime);
                    reportGeometryBounds(runtime, "frame=" + time + " active=" + runtime.animations().getActiveAnimations().size());
                }
                var running = new RunningRenderState();
                running.walkAnimationSpeed = 1f;
                for (int tick = 201; tick <= 240; tick++) {
                    running.ageInTicks = tick;
                    top.aihmc.omnimorph.model.ysm.animation.YsmRenderFrameClock.beginFrame();
                    runtime.updateAnimations(running, null);
                }
                require(runtime.animations().getActiveAnimation("run") != null, "reported model selects run while sprinting");
                assertControllerChannelOwnership(runtime);
                reportGeometryBounds(runtime, "running");
            }
        } finally {
            client.drain();
        }
    }

    private static void assertControllerChannelOwnership(YsmModelRuntime runtime) throws Exception {
        var layersField = top.aihmc.omnimorph.model.ysm.animation.YsmAnimationPlayer.class
                .getDeclaredField("controllerPoseLayers");
        layersField.setAccessible(true);
        var poseField = top.aihmc.omnimorph.model.ysm.controller.YsmControllerPoseLayer.class.getDeclaredField("lastPose");
        poseField.setAccessible(true);
        var expected = new java.util.LinkedHashMap<String, float[][]>();
        Map<?, ?> layers = (Map<?, ?>) layersField.get(runtime.animations());
        for (boolean beforeMain : new boolean[] {true, false}) {
            for (var layer : layers.entrySet()) {
                if ((top.aihmc.omnimorph.model.ysm.controller.YsmControllerSlot.executionOrder((String) layer.getKey()) < 20)
                        != beforeMain) {
                    continue;
                }
                for (var entry : ((Map<?, ?>) poseField.get(layer.getValue())).entrySet()) {
                    recordExpectedChannels(expected, (String) entry.getKey(),
                            (top.aihmc.omnimorph.model.ysm.animation.YsmBonePose) entry.getValue());
                }
            }
            if (beforeMain) {
                var nativePoses = new java.util.LinkedHashMap<String, top.aihmc.omnimorph.model.ysm.animation.YsmBonePose>();
                var context = runtime.owner().getMolangContext();
                var evaluator = top.aihmc.omnimorph.molang.runtime.ExpressionEvaluator.evaluator(context);
                float previousTime = context.anim_time;
                try {
                    for (var animation : runtime.animations().getActiveAnimations().values()) {
                        if (!animation.isNative || animation.weight <= 0f) {
                            continue;
                        }
                        context.anim_time = animation.time;
                        float weight = animation.weight * animation.clip.evaluateBlendWeight(evaluator);
                        for (var bone : animation.clip.boneAnimations.values()) {
                            var pose = top.aihmc.omnimorph.model.ysm.animation.YsmAnimationSampler.sampleBone(bone, animation.time, evaluator);
                            nativePoses.put(bone.boneName, top.aihmc.omnimorph.model.ysm.animation.YsmBonePose.accumulate(
                                    nativePoses.get(bone.boneName), pose, weight));
                        }
                    }
                } finally {
                    context.anim_time = previousTime;
                }
                nativePoses.forEach((bone, pose) -> recordExpectedChannels(expected, bone, pose));
            }
        }
        int mismatches = 0;
        for (var entry : expected.entrySet()) {
            var part = runtime.getPart(entry.getKey());
            if (part == null) {
                continue;
            }
            FiguraVec3[] actual = {part.animPosRaw(), part.animRotRaw(), part.animScaleRaw()};
            for (int channel = 0; channel < 3; channel++) {
                float[] value = entry.getValue()[channel];
                if (value != null && (Math.abs(value[0] - actual[channel].x) > 0.0001
                        || Math.abs(value[1] - actual[channel].y) > 0.0001
                        || Math.abs(value[2] - actual[channel].z) > 0.0001)) {
                    mismatches++;
                }
            }
        }
        require(mismatches == 0, "controller channel ownership mismatches on the reported model: " + mismatches);
    }

    private static void recordExpectedChannels(Map<String, float[][]> expected, String bone,
            top.aihmc.omnimorph.model.ysm.animation.YsmBonePose pose) {
        float[][] channels = expected.computeIfAbsent(bone, ignored -> new float[3][]);
        float[][] output = {pose.position(), pose.rotation(), pose.scale()};
        for (int channel = 0; channel < 3; channel++) {
            if (output[channel] != null) {
                channels[channel] = output[channel];
            }
        }
    }

    private static void reportGeometryBounds(YsmModelRuntime runtime, String phase) throws Exception {
        var transform = YsmRenderer.class.getDeclaredMethod("applyBoneTransform",
                YsmGeometry.Bone.class, YsmModelPart.class, com.mojang.blaze3d.vertex.PoseStack.class);
        transform.setAccessible(true);
        double[] bounds = {Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY,
                Double.NEGATIVE_INFINITY, Double.NEGATIVE_INFINITY, Double.NEGATIVE_INFINITY};
        for (var root : runtime.geometry().roots) {
            collectGeometryBounds(runtime, root, new com.mojang.blaze3d.vertex.PoseStack(), transform, bounds);
        }
        for (int axis = 0; axis < 3; axis++) {
            require(Double.isFinite(bounds[axis]) && Double.isFinite(bounds[axis + 3])
                            && bounds[axis] <= bounds[axis + 3],
                    "the reported model must have finite rendered vertex bounds at " + phase);
        }
        var active = runtime.animations().getActiveAnimations().values();
        var counts = active.stream().collect(java.util.stream.Collectors.groupingBy(
                animation -> animation.clip, java.util.stream.Collectors.counting()));
        require(counts.values().stream().noneMatch(count -> count > 1),
                "the reported model must not play its controller-owned parallel clips twice");
        System.out.println("Geometry bounds " + phase + ": " + java.util.Arrays.toString(bounds)
                + " distinctActiveClips=" + counts.size() + " duplicated="
                + counts.values().stream().filter(count -> count > 1).count()
                + " maxCopies=" + counts.values().stream().mapToLong(Long::longValue).max().orElse(0));
    }

    private static void collectGeometryBounds(YsmModelRuntime runtime, YsmGeometry.Bone bone,
                                              com.mojang.blaze3d.vertex.PoseStack stack,
                                              java.lang.reflect.Method transform, double[] bounds) throws Exception {
        var part = runtime.getPart(bone.name);
        if (part != null && (!part.visibleRaw() || part.hiddenScaleRaw())) {
            return;
        }
        stack.pushPose();
        transform.invoke(runtime.renderer(), bone, part, stack);
        for (var cube : bone.cubes) {
            for (var quad : cube.quads) {
                if (!quad.visible()) {
                    continue;
                }
                var points = quad.positions();
                for (int index = 0; index < points.length; index += 3) {
                    var point = new org.joml.Vector3f(points[index], points[index + 1], points[index + 2])
                            .mulPosition(stack.last().pose());
                    bounds[0] = Math.min(bounds[0], point.x);
                    bounds[1] = Math.min(bounds[1], point.y);
                    bounds[2] = Math.min(bounds[2], point.z);
                    bounds[3] = Math.max(bounds[3], point.x);
                    bounds[4] = Math.max(bounds[4], point.y);
                    bounds[5] = Math.max(bounds[5], point.z);
                }
            }
        }
        for (var child : bone.children) {
            collectGeometryBounds(runtime, child, stack, transform, bounds);
        }
        stack.popPose();
    }

    private static void testTexturePublication(Unsafe unsafe, RecordingClient client) throws Exception {
        var texture = new PublicationTexture(owner(unsafe, 56));
        client.manager.inspectRegistration = true;
        try {
            texture.uploadIfDirty(false, false);
            require(texture.loads == 1 && texture.getTextureView() != null,
                    "texture registration consumers receive an initialized view");
            texture.uploadIfDirty(false, false);
            require(texture.loads == 1, "clean registered textures do not upload again");
            texture.getTextureView().close();
            texture.uploadIfDirty(false, false);
            require(texture.loads == 2 && !texture.getTextureView().isClosed(),
                    "a retired view is recreated before the registered texture is reused");
            texture.close();
            texture.uploadIfDirty(false, false);
            client.drain();
            require(!client.manager.textures.containsKey(texture.getLocation()),
                    "late render requests cannot republish a closed texture without a view");

            var retry = new PublicationTexture(owner(unsafe, 57));
            try {
                retry.failUpload = true;
                try {
                    retry.uploadIfDirty(false, false);
                    throw new AssertionError("fixture upload should fail");
                } catch (IllegalStateException expected) {
                    require(!client.manager.textures.containsKey(retry.getLocation()),
                            "failed initialization never publishes a broken texture");
                }
                retry.failUpload = false;
                retry.uploadIfDirty(false, false);
                require(retry.loads == 2 && retry.getTextureView() != null,
                        "a failed upload is retried instead of being incorrectly marked clean");
            } finally {
                retry.close();
            }
        } finally {
            client.manager.inspectRegistration = false;
            texture.close();
            client.drain();
        }
    }

    private static void testLateTextureBindings(Unsafe unsafe, RecordingClient client) throws Exception {
        var fallback = new PublicationTexture(owner(unsafe, 64));
        var texture = new PublicationTexture(owner(unsafe, 65));
        var missing = net.minecraft.client.renderer.texture.MissingTextureAtlasSprite.getLocation();
        try {
            fallback.uploadIfDirty(false, false);
            client.manager.textures.put(missing, fallback);
            texture.uploadIfDirty(false, false);
            var setup = net.minecraft.client.renderer.rendertype.RenderSetup.builder(
                            net.minecraft.client.renderer.RenderPipelines.GUI_TEXTURED)
                    .withTexture("Sampler0", texture.getLocation()).createRenderSetup();
            texture.getTextureView().close();
            texture.clearView();
            require(!setup.getTextures().get("Sampler0").textureView().isClosed(),
                    "the vanilla delayed read repairs a missing live view without an upload call at the consumer");
            int liveLoads = texture.loads;
            var pending = texture.loadContents(null);
            texture.apply(pending);
            require(pending.image().isClosed(), "resource reload copies are released after apply");
            texture.close();
            require(setup.getTextures().get("Sampler0").textureView() == fallback.getTextureView(),
                    "a batch can resolve a retired binding before queued removal runs");
            // The vanilla registration entry point can finish after the model has been retired.
            client.manager.registerAndLoad(texture.getLocation(), texture);
            require(setup.getTextures().get("Sampler0").textureView() == fallback.getTextureView(),
                    "late registration of a retired texture resolves to the initialized missing texture");
            require(texture.getTexture() == fallback.getTexture() && texture.getSampler() == fallback.getSampler(),
                    "all retired GPU accessors use the same live fallback binding");
            require(texture.loads == liveLoads, "retired textures never upload their released source again");
            client.drain();
            require(!client.manager.textures.containsKey(texture.getLocation()), "late publication is removed on the queue");

            for (int iteration = 0; iteration < 64; iteration++) {
                var old = new PublicationTexture(owner(unsafe, 66));
                var replacement = new PublicationTexture(owner(unsafe, 67));
                try {
                    old.uploadIfDirty(false, false);
                    replacement.uploadIfDirty(false, false);
                    client.manager.register(old.getLocation(), replacement);
                    client.drain();
                    require(client.manager.getTexture(old.getLocation()) == replacement,
                            "old cleanup cannot remove a replacement registered under the same location");
                } finally {
                    old.close();
                    replacement.close();
                    client.manager.textures.remove(old.getLocation(), replacement);
                    client.drain();
                }
            }
            client.manager.close();
            client.drain();
            require(client.manager.textures.isEmpty(), "manager shutdown does not mutate a map during its iteration");
        } finally {
            texture.close();
            client.manager.textures.remove(texture.getLocation());
            client.manager.textures.remove(missing, fallback);
            fallback.close();
            client.drain();
        }
    }

    private static void testTextureUploadLifetime(Unsafe unsafe, RecordingClient client, PublicationDevice device) throws Exception {
        var texture = new PublicationTexture(owner(unsafe, 68));
        try {
            for (boolean failView : new boolean[] {true, false}) {
                device.failView = failView;
                device.failWrite = !failView;
                int start = device.created.size();
                try {
                    texture.uploadIfDirty(false, false);
                    throw new AssertionError("fixture GPU operation must fail");
                } catch (IllegalStateException expected) {
                    require(device.created.size() == start + 1 && device.created.getLast().isClosed(),
                            "failed allocation/upload releases the unpublished GPU texture");
                    require(!client.manager.textures.containsKey(texture.getLocation()),
                            "failed allocation/upload does not publish a partial binding");
                }
            }
            device.failView = false;
            device.failWrite = false;
            texture.uploadIfDirty(true, true);
            var binding = texture.getTextureView();
            var gpuTexture = texture.getTexture();
            var sampler = texture.getSampler();
            require(sampler != null && sampler.getAddressModeU() == com.mojang.blaze3d.textures.AddressMode.CLAMP_TO_EDGE
                            && sampler.getMinFilter() == com.mojang.blaze3d.textures.FilterMode.LINEAR,
                    "the fixture uses the selected clamp/blur sampler");
            int allocated = device.created.size();
            for (int iteration = 0; iteration < 64; iteration++) {
                texture.update();
                require(texture.getTextureView() == binding && texture.getTexture() == gpuTexture,
                        "dirty pixel updates preserve the existing GPU binding for queued consumers");
                require(texture.getSampler() == sampler, "late reads preserve the selected clamp/blur sampler");
            }
            require(device.created.size() == allocated, "dirty updates do not allocate or leak replacement GPU textures");
            var particleLease = texture.retain();
            var secondParticleLease = particleLease.retain();
            texture.close();
            require(!gpuTexture.isClosed() && !binding.isClosed()
                            && texture.getTextureView() == binding,
                    "retiring author texture keeps existing custom particles' binding alive");
            try {
                texture.retain();
                throw new AssertionError("new particles must not acquire a retired author texture");
            } catch (IllegalStateException expected) {
                require(expected.getMessage().equals("TEXTURE_RETIRED"), "explicit retired texture rejection");
            }
            var extractedFrameLease = secondParticleLease.retain();
            particleLease.close();
            particleLease.close();
            secondParticleLease.close();
            require(!gpuTexture.isClosed() && !binding.isClosed(),
                    "in-flight extracted frame outlives both particle leases and repeated release");
            extractedFrameLease.close();
            require(gpuTexture.isClosed() && binding.isClosed(),
                    "final extracted frame release closes retired texture and GPU view");
        } finally {
            device.failView = false;
            device.failWrite = false;
            texture.close();
            client.drain();
        }
        require(device.created.stream().allMatch(PublicationGpuTexture::isClosed),
                "all GPU texture allocations from the lifetime checks are released");
        require(device.views.stream().allMatch(PublicationView::isClosed),
                "all GPU views from the lifetime checks are released");
    }

    /** Replaces GPU allocation only; publication and the vanilla view lookup remain real. */
    private static final class PublicationTexture extends top.aihmc.omnimorph.model.rendering.texture.FiguraTexture {
        private int loads;
        private boolean failUpload;

        private PublicationTexture(Avatar owner) {
            super(owner, "publication", new NativeImage(1, 1, true));
        }

        @Override
        protected void doLoad(NativeImage image) {
            loads++;
            if (failUpload) {
                throw new IllegalStateException("fixture GPU upload failure");
            }
            super.doLoad(image);
        }

        private void clearView() {
            textureView = null;
        }
    }

    private static final class PublicationView extends com.mojang.blaze3d.textures.GpuTextureView {
        private boolean closed;

        private PublicationView(com.mojang.blaze3d.textures.GpuTexture texture) {
            super(texture, 0, 1);
        }

        @Override
        public void close() {
            closed = true;
        }

        @Override
        public boolean isClosed() {
            return closed;
        }
    }

    /** Records GPU ownership and failure ordering; no graphics backend or window is created. */
    private static final class PublicationDevice extends com.mojang.blaze3d.systems.GpuDevice {
        private final List<PublicationGpuTexture> created = new ArrayList<>();
        private final List<PublicationView> views = new ArrayList<>();
        private boolean failView;
        private boolean failWrite;

        private PublicationDevice() {
            super(null);
        }

        @Override
        public com.mojang.blaze3d.textures.GpuSampler createSampler(
                com.mojang.blaze3d.textures.AddressMode u, com.mojang.blaze3d.textures.AddressMode v,
                com.mojang.blaze3d.textures.FilterMode min, com.mojang.blaze3d.textures.FilterMode mag,
                int anisotropy, java.util.OptionalDouble maxLod) {
            return new com.mojang.blaze3d.textures.GpuSampler() {
                @Override
                public com.mojang.blaze3d.textures.AddressMode getAddressModeU() {
                    return u;
                }

                @Override
                public com.mojang.blaze3d.textures.AddressMode getAddressModeV() {
                    return v;
                }

                @Override
                public com.mojang.blaze3d.textures.FilterMode getMinFilter() {
                    return min;
                }

                @Override
                public com.mojang.blaze3d.textures.FilterMode getMagFilter() {
                    return mag;
                }

                @Override
                public int getMaxAnisotropy() {
                    return anisotropy;
                }

                @Override
                public java.util.OptionalDouble getMaxLod() {
                    return maxLod;
                }

                @Override
                public void close() {
                }
            };
        }

        @Override
        public com.mojang.blaze3d.textures.GpuTexture createTexture(java.util.function.Supplier<String> label, int usage,
                com.mojang.blaze3d.textures.TextureFormat format, int width, int height, int layers, int levels) {
            var texture = new PublicationGpuTexture(usage, label.get(), format, width, height, layers, levels);
            created.add(texture);
            return texture;
        }

        @Override
        public com.mojang.blaze3d.textures.GpuTextureView createTextureView(com.mojang.blaze3d.textures.GpuTexture texture) {
            if (failView) {
                throw new IllegalStateException("fixture view creation failure");
            }
            var view = new PublicationView(texture);
            views.add(view);
            return view;
        }

        @Override
        public com.mojang.blaze3d.systems.CommandEncoder createCommandEncoder() {
            return new com.mojang.blaze3d.systems.CommandEncoder(null, null) {
                @Override
                public void writeToTexture(com.mojang.blaze3d.textures.GpuTexture texture, NativeImage image) {
                    require(!texture.isClosed() && !image.isClosed(), "uploads require live source and destination");
                    if (failWrite) {
                        throw new IllegalStateException("fixture pixel upload failure");
                    }
                }
            };
        }
    }

    private static final class PublicationGpuTexture extends com.mojang.blaze3d.textures.GpuTexture {
        private boolean closed;

        private PublicationGpuTexture(int usage, String label, com.mojang.blaze3d.textures.TextureFormat format,
                                      int width, int height, int layers, int levels) {
            super(usage, label, format, width, height, layers, levels);
        }

        @Override
        public void close() {
            closed = true;
        }

        @Override
        public boolean isClosed() {
            return closed;
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
        Fixture fixture = new Fixture(unsafe, 35, null, """
                {"minecraft:geometry":[{"description":{"texture_width":1,"texture_height":1},"bones":[
                  {"name":"root","cubes":[{"origin":[0,0,0],"size":[1,1,1],"uv":[0,0]}]},
                  {"name":"leftArm","parent":"root"},
                  {"name":"leftForearm","parent":"leftArm"},
                  {"name":"rightArm","parent":"root"}
                ]}]}
                """);
        var runtime = fixture.runtime;
        var world = runtime.owner().getMolangContext();
        runtime.animations().play("idle", true, 1f);
        runtime.animations().getActiveAnimation("idle").time = 0.25f;
        world.variables.setPath("pose", 32f);
        world.variables.setPath("nested.child.value", 3f);
        world.variables.setPath("self", world.variables.evaluate(null));
        try {
            require(runtime.forRenderPass() == runtime, "world drawing keeps its original evaluator");
            runtime.owner().renderMode = top.aihmc.omnimorph.model.rendering.EntityRenderMode.FIGURA_GUI;
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
                top.aihmc.omnimorph.model.ysm.animation.YsmRenderFrameClock.beginFrame();
                preview.updateAnimations(state, null);
                var pose = preview.captureRenderPose(false);
                require(pose.part("root").animPosRaw().x == 0d
                                && pose.part("root").animPosRaw().y >= 0d
                                && pose.part("root").animPosRaw().y <= 16d,
                        "GUI uses the model's default idle and never the GUI clip's X translation");
                require(pose.part("rightArm").animRotRaw().lengthSquared() == 0d
                                && pose.part("leftArm").animRotRaw().lengthSquared() == 0d,
                        "preview does not overwrite authored animation with procedural arm movement");
                require(pose.part("leftForearm").animRotRaw().lengthSquared() == 0d,
                        "nested arm segments inherit sway without receiving it twice");
                preview.updateAnimations(state, null);
                require(preview.captureRenderPose(false).part("root").animPosRaw().y == pose.part("root").animPosRaw().y,
                        "repeated extraction does not advance the animation twice");
            }
            require(!preview.animations().isActive("gui") && preview.animations().isActive("idle")
                            && runtime.animations().getActiveAnimation("idle").time == 0.25f,
                    "GUI passes run independent default idle without starting GUI clips or advancing world playback");
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
                var settings = (top.aihmc.omnimorph.gui.screens.AvatarControlsScreen) unsafe.allocateInstance(
                        top.aihmc.omnimorph.gui.screens.AvatarControlsScreen.class);
                client.screen = settings;
                require(runtime.forRenderPass() == recreated,
                        "model settings use the same isolated standing preview");
                runtime.owner().controls.get("ysm.width_scale").setValue(1.1d);
                require(Math.abs(runtime.forRenderPass().widthScale() - 1.1f) < 0.0001,
                        "settings preview consumes changed controls immediately");
                var sample = top.aihmc.omnimorph.model.ysm.animation.YsmAnimationPlayer.class.getDeclaredMethod(
                        "sampleAnimations", top.aihmc.omnimorph.molang.runtime.ExpressionEvaluator.class, boolean.class);
                sample.setAccessible(true);
                sample.invoke(runtime.animations(),
                        top.aihmc.omnimorph.molang.runtime.ExpressionEvaluator.evaluator(world), false);
                require(runtime.forRenderPass().captureRenderPose(false).part("root").animPosRaw().y == 0d,
                        "settings preview stays standing while the world samples its custom idle animation");
                client.screen = new top.aihmc.omnimorph.gui.screens.AvatarScreen(1f, 0f, 0f, null, settings);
                require(runtime.forRenderPass().isPreviewRuntime(),
                        "expanded settings preview also uses isolated standing playback");
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
                    alternative.owner().renderMode = top.aihmc.omnimorph.model.rendering.EntityRenderMode.FIGURA_GUI;
                    var alternativePreview = alternative.forRenderPass();
                    require(alternativePreview != alternative && alternativePreview.isPreviewRuntime(),
                            "omitted or invalid preview declarations still select the detached default pose");
                    alternativePreview.updateAnimations(state, null);
                    require(alternativePreview.animations().isActive("idle")
                                    && !alternativePreview.animations().isActive("gui")
                                    && alternativePreview.captureRenderPose(false).part("root").animPosRaw().x == 0d,
                            "default idle does not depend on a special preview animation declaration");
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
            runtime.owner().renderMode = top.aihmc.omnimorph.model.rendering.EntityRenderMode.FIGURA_GUI;
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
            runtime.owner().renderMode = top.aihmc.omnimorph.model.rendering.EntityRenderMode.WORLD;
            var deferredGui = (net.minecraft.client.renderer.rendertype.RenderType) submittedType.invoke(renderer, guiPose);
            require(deferredGui.pipeline().getShaderDefines().equals(unlitDefines),
                    "deferred GUI drawing retains submission-time unlit material after the avatar mode is restored");
            var worldPose = runtime.captureRenderPose(false);
            runtime.owner().renderMode = top.aihmc.omnimorph.model.rendering.EntityRenderMode.FIGURA_GUI;
            var deferredWorld = (net.minecraft.client.renderer.rendertype.RenderType) submittedType.invoke(renderer, worldPose);
            require(deferredWorld.pipeline() == net.minecraft.client.renderer.RenderPipelines.ENTITY_TRANSLUCENT_CULL,
                    "later GUI extraction cannot change a submitted world material");
            runtime.owner().renderMode = top.aihmc.omnimorph.model.rendering.EntityRenderMode.WORLD;
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

    private static boolean testCorpusHeldItems(Unsafe unsafe, RecordingClient client, Path path, int index) throws Exception {
        Avatar avatar = owner(unsafe, 100 + index);
        try (var data = YsmModelData.read(path, () -> false);
             var runtime = YsmModelRuntime.fromData(avatar, data,
                     YsmAvatarLoader.toRuntimeTag(data, data.manifest().textures().getFirst()))) {
            install(avatar, "ysmRuntime", runtime);
            var roles = java.util.EnumSet.allOf(YsmBoneRole.class);
            roles.removeAll(java.util.EnumSet.of(YsmBoneRole.GUI, YsmBoneRole.BACKGROUND, YsmBoneRole.FIRST_PERSON_ARM));
            var all = geometryVertices(runtime, new YsmPartMask(roles));
            var actual = worldVertices(runtime, null);
            require(actual.equals(all), "body plus attachments must render each visible vertex once: expected="
                    + all.size() + " actual=" + actual.size() + " corpusIndex=" + index);
            if (path.getFileName().toString().equals("芙宁娜v3.14中文配音.ysm")) {
                require(all.size() == 33360, "reported model retains every authored default-pose vertex");
            }
            require(worldVertices(runtime, runtime.captureRenderPose(false)).equals(all),
                    "deferred world pass preserves corpus geometry and matrices");
            System.out.println("World attachment corpus: index=" + index + " vertices=" + all.size() + " PASS");
            return runtime.encryptedSource();
        } finally {
            client.drain();
        }
    }

    private static List<String> worldVertices(YsmModelRuntime runtime, YsmRenderPose pose) {
        var vertices = new ArrayList<String>();
        var consumer = vertexRecorder(vertices);
        runtime.renderer().renderWithAttachments(new com.mojang.blaze3d.vertex.PoseStack(),
                type -> consumer, 0, pose);
        vertices.sort(String::compareTo);
        return vertices;
    }

    private static void testAttachmentGeometry(Unsafe unsafe, RecordingClient client) throws Exception {
        String geometry = """
                {"minecraft:geometry":[{"description":{"texture_width":1,"texture_height":1},"bones":[
                  {"name":"root"},
                  {"name":"BladeLocator","parent":"root"},
                  {"name":"RightSword","parent":"root","cubes":[{"origin":[0,0,0],"size":[1,1,1],"uv":[0,0]}]},
                  {"name":"detail","parent":"RightSword","cubes":[{"origin":[1,0,0],"size":[1,1,1],"uv":[0,0]}]},
                  {"name":"LeftSword","parent":"root","cubes":[{"origin":[2,0,0],"size":[1,1,1],"uv":[0,0]}]},
                  {"name":"WingLeft","parent":"root","cubes":[{"origin":[3,0,0],"size":[1,1,1],"uv":[0,0]}]},
                  {"name":"WingRight","parent":"root","cubes":[{"origin":[4,0,0],"size":[1,1,1],"uv":[0,0]}]},
                  {"name":"Backpack","parent":"root","cubes":[{"origin":[5,0,0],"size":[1,1,1],"uv":[0,0]}]},
                  {"name":"Sheath","parent":"root","cubes":[{"origin":[6,0,0],"size":[1,1,1],"uv":[0,0]}]}]}]}
                """;
        Fixture fixture = new Fixture(unsafe, 91, null, geometry);
        try {
            var runtime = fixture.runtime;
            runtime.getPart("root").setPos(16d, 32d, 0d);
            runtime.getPart("root").setRot(0d, 0d, 90d);
            var expected = geometryVertices(runtime, YsmPartMask.DEBUG_ALL);
            require(expected.size() == 7 * 24, "seven cubes provide an independent draw-count oracle");
            require(worldVertices(runtime, null).equals(expected),
                    "sword pairs, wing pairs, sheath, backpack and ordinary children draw exactly once with ancestor matrices");
            var named = new ArrayList<String>();
            var namedConsumer = vertexRecorder(named);
            require(runtime.renderer().renderAttachment(new com.mojang.blaze3d.vertex.PoseStack(),
                    type -> namedConsumer, 0, "Backpack"), "named backpack attachment renders");
            var backpack = geometryVertices(runtime, new YsmPartMask(java.util.EnumSet.of(YsmBoneRole.BACKPACK)));
            named.sort(String::compareTo);
            require(named.equals(backpack), "named attachment includes the full translated and rotated ancestor chain");
            var blades = new ArrayList<String>();
            var bladeConsumer = vertexRecorder(blades);
            runtime.renderer().renderAttachmentType(new com.mojang.blaze3d.vertex.PoseStack(),
                    type -> bladeConsumer, 0, YsmAttachmentType.BLADE);
            blades.sort(String::compareTo);
            require(blades.size() == 3 * 24 && blades.equals(geometryVertices(runtime,
                    new YsmPartMask(java.util.EnumSet.of(YsmBoneRole.BLADE)))),
                    "typed blade pass includes both swords and their ordinary children once");
            var snapshot = runtime.captureRenderPose(false);
            runtime.getPart("root").setVisible(false);
            require(worldVertices(runtime, null).isEmpty(), "hidden ancestor suppresses every attachment branch");
            require(worldVertices(runtime, snapshot).equals(expected), "deferred attachment pass retains ancestor snapshot");
            runtime.getPart("root").setVisible(true);
            runtime.getPart("root").setScale(0d, 0d, 0d);
            require(worldVertices(runtime, null).isEmpty(), "zero-scale ancestor suppresses attachments");
        } finally {
            fixture.runtime.close();
            client.drain();
        }
    }

    private static List<String> geometryVertices(YsmModelRuntime runtime, YsmPartMask mask) throws Exception {
        var vertices = new ArrayList<String>();
        var consumer = vertexRecorder(vertices);
        runtime.renderer().render(new com.mojang.blaze3d.vertex.PoseStack(), type -> consumer, 0, mask);
        vertices.sort(String::compareTo);
        return vertices;
    }

    private static com.mojang.blaze3d.vertex.VertexConsumer vertexRecorder(List<String> vertices) {
        return (com.mojang.blaze3d.vertex.VertexConsumer) java.lang.reflect.Proxy.newProxyInstance(
                YsmMaterialNativeSmokeTest.class.getClassLoader(),
                new Class<?>[]{com.mojang.blaze3d.vertex.VertexConsumer.class}, (proxy, method, arguments) -> {
                    if (method.getName().equals("addVertex") && arguments.length == 3) {
                        vertices.add(arguments[0] + "," + arguments[1] + "," + arguments[2]);
                    } else if (method.getName().equals("addVertex") && arguments.length == 4) {
                        var pose = (com.mojang.blaze3d.vertex.PoseStack.Pose) arguments[0];
                        var position = pose.pose().transformPosition(new org.joml.Vector3f(
                                (float) arguments[1], (float) arguments[2], (float) arguments[3]));
                        vertices.add(position.x + "," + position.y + "," + position.z);
                    }
                    return method.getReturnType() == void.class ? null : proxy;
                });
    }

    private static void testBakedPreviewGeometry(Unsafe unsafe, RecordingClient client) throws Exception {
        // Binary mapping supplies baked geometry without an exported main_model JSON resource.
        var fixture = new Fixture(unsafe, 702, null, null, true);
        try {
            fixture.runtime.owner().renderMode = top.aihmc.omnimorph.model.rendering.EntityRenderMode.FIGURA_GUI;
            var preview = fixture.runtime.forRenderPass();
            require(preview != null, "baked library preview must not disappear when source JSON is absent");
            require(!geometryVertices(preview, YsmPartMask.PLAYER_BODY).isEmpty(),
                    "baked library preview must emit visible vertices, not merely finish loading");
            require(preview.geometry() != fixture.runtime.geometry(), "preview geometry is independently owned");
        } finally {
            fixture.runtime.close();
            client.drain();
        }
    }

    private static void testPreviewBoneFade(Unsafe unsafe, RecordingClient client) throws Exception {
        var fixture = new Fixture(unsafe, 708, null, """
                {"minecraft:geometry":[{"description":{"texture_width":1,"texture_height":1},"bones":[
                  {"name":"root","cubes":[{"origin":[0,0,0],"size":[1,1,1],"uv":[0,0]}]},
                  {"name":"selected","parent":"root","cubes":[{"origin":[3,0,0],"size":[1,1,1],"uv":[0,0]}]},
                  {"name":"child","parent":"selected","cubes":[{"origin":[6,0,0],"size":[1,1,1],"uv":[0,0]}]}
                ]}]}
                """);
        var runtime = fixture.runtime;
        var service = top.aihmc.omnimorph.render.acceleration.AccelerationService.INSTANCE;
        Field backend = service.getClass().getDeclaredField("preferredBackend");
        backend.setAccessible(true);
        Object previousBackend = backend.get(service);
        boolean previousPaperdoll = top.aihmc.omnimorph.utils.ui.UIHelper.paperdoll;
        try {
            for (boolean accelerated : new boolean[]{false, true}) {
                // A distinct Java backend exercises production accelerated capture and fallback emission.
                if (accelerated) {
                    backend.set(service, new top.aihmc.omnimorph.render.acceleration.JavaAccelerationBackend());
                }
                top.aihmc.omnimorph.render.acceleration.FiguraAccelerationReplay.replay(null);
                var alphas = new ArrayList<Float>();
                var currentMaterial = new java.util.concurrent.atomic.AtomicReference<net.minecraft.client.renderer.rendertype.RenderType>();
                var consumer = (com.mojang.blaze3d.vertex.VertexConsumer) java.lang.reflect.Proxy.newProxyInstance(
                        YsmMaterialNativeSmokeTest.class.getClassLoader(),
                        new Class<?>[]{com.mojang.blaze3d.vertex.VertexConsumer.class}, (proxy, method, args) -> {
                    if (method.isDefault()) {
                        return java.lang.reflect.InvocationHandler.invokeDefault(proxy, method, args);
                    }
                    if (method.getName().equals("setColor")) {
                        float alpha = ((Number) args[3]).floatValue();
                        require(alpha == 255 || currentMaterial.get().hasBlending(),
                                "faded preview geometry uses an alpha-capable material");
                        alphas.add(alpha);
                    }
                    return method.getReturnType() == void.class ? null : proxy;
                });
                com.mojang.blaze3d.vertex.VertexConsumer ignored = vertexRecorder(new ArrayList<>());
                net.minecraft.client.renderer.MultiBufferSource source = material -> {
                    if (material.mode() == com.mojang.blaze3d.vertex.VertexFormat.Mode.LINES) {
                        return ignored;
                    }
                    currentMaterial.set(material);
                    return consumer;
                };
                top.aihmc.omnimorph.utils.ui.UIHelper.paperdoll = true;
                top.aihmc.omnimorph.model.library.PreviewBoneHighlight.capture(runtime.owner(), runtime.getPart("selected"));
                runtime.renderer().render(new com.mojang.blaze3d.vertex.PoseStack(), source, 0, null,
                        runtime.captureRenderPose(false));
                require(alphas.stream().filter(alpha -> alpha == 76).count() == 24
                                && alphas.stream().filter(alpha -> alpha == 255).count() == 48,
                        "YSM parent fades while selected bone and child retain opacity, including deferred pose copies");
                alphas.clear();
                runtime.owner().renderMode = top.aihmc.omnimorph.model.rendering.EntityRenderMode.FIGURA_GUI;
                var detached = runtime.forRenderPass();
                require(detached != runtime && detached.owner() != runtime.owner(),
                        "bone selection regression exercises the actual detached GUI runtime");
                detached.renderer().render(new com.mojang.blaze3d.vertex.PoseStack(), source, 0, null,
                        detached.captureRenderPose(false));
                require(alphas.stream().filter(alpha -> alpha == 76).count() == 24
                                && alphas.stream().filter(alpha -> alpha == 255).count() == 48,
                        "the tree's source selection highlights the detached preview and fades other bones");
                alphas.clear();
                top.aihmc.omnimorph.utils.ui.UIHelper.paperdoll = false;
                runtime.renderer().render(new com.mojang.blaze3d.vertex.PoseStack(), source, 0, null);
                require(alphas.size() == 72 && alphas.stream().allMatch(alpha -> alpha == 255),
                        "world rendering remains fully opaque while a GUI selection exists");
                top.aihmc.omnimorph.model.library.PreviewBoneHighlight.finishFrame();
                top.aihmc.omnimorph.utils.ui.UIHelper.paperdoll = true;
                require(top.aihmc.omnimorph.model.library.PreviewBoneHighlight.opacity(runtime.owner(), runtime.getPart("root")) == 1f,
                        "cleared selections restore the original material opacity");
                require(runtime.getPart("root").visibleRaw() && runtime.getPart("selected").visibleRaw(),
                        "preview feedback never edits source visibility");
            }
            System.out.println("PASS Figura/YSM bone fade: Java and accelerated emitters, selected subtree, deferred alpha and world isolation");
        } finally {
            backend.set(service, previousBackend);
            top.aihmc.omnimorph.model.library.PreviewBoneHighlight.finishFrame();
            top.aihmc.omnimorph.utils.ui.UIHelper.paperdoll = previousPaperdoll;
            runtime.close();
            client.drain();
        }
    }

    private static void testStandingPreviewVertices(Unsafe unsafe, RecordingClient client) throws Exception {
        var fixture = new Fixture(unsafe, 703, null, """
                {"minecraft:geometry":[{"description":{"texture_width":1,"texture_height":1},"bones":[
                  {"name":"root","cubes":[{"origin":[0,0,0],"size":[1,1,1],"uv":[0,0]}]},
                  {"name":"effect","parent":"root","cubes":[{"origin":[3,0,0],"size":[1,1,1],"uv":[0,0]}]}
                ]}]}
                """);
        var tag = fixture.runtimeTag.copy();
        var animations = new net.minecraft.nbt.ListTag();
        var animation = new net.minecraft.nbt.CompoundTag();
        animation.putString("path", "animations/standing.animation.json");
        animation.putByteArray("data", bytes("""
                {"animations":{
                  "pre_parallel0":{"loop":true,"bones":{"effect":{"scale":[0,0,0]}}},
                  "idle":{"loop":true,"animation_length":3,"bones":{"root":{"rotation":[0,"math.sin(q.anim_time*90)*15",0]}}},
                  "gui":{"loop":true,"bones":{"root":{"position":[400,0,0]}}}
                }}
                """));
        animations.add(animation);
        tag.put("animations", animations);
        var controllers = new net.minecraft.nbt.ListTag();
        var controller = new net.minecraft.nbt.CompoundTag();
        controller.putString("path", "controllers/standing.json");
        controller.putByteArray("data", bytes("""
                {"animation_controllers":{"controller.setup":{"initial_state":"default","states":{
                  "default":{"animations":["pre_parallel0"]}
                }}}}
                """));
        controllers.add(controller);
        tag.put("animation_controllers", controllers);
        var sourceOwner = owner(unsafe, 704);
        var source = YsmModelRuntime.fromNbt(sourceOwner, tag);
        install(sourceOwner, "ysmRuntime", source);
        try {
            sourceOwner.renderMode = top.aihmc.omnimorph.model.rendering.EntityRenderMode.FIGURA_GUI;
            var preview = source.forRenderPass();
            var state = new net.minecraft.client.renderer.entity.state.LivingEntityRenderState();
            top.aihmc.omnimorph.model.ysm.animation.YsmRenderFrameClock.beginFrame();
            preview.updateAnimations(state, null);
            var first = geometryVertices(preview, YsmPartMask.PLAYER_BODY);
            require(first.size() == 24 && !preview.getPart("effect").visibleRaw(),
                    "preview must retain controller/base visibility and omit the hidden effect geometry");
            require(!preview.animations().getControllers().isEmpty(), "preview keeps the model's controller definitions");
            state.ageInTicks = 24;
            top.aihmc.omnimorph.model.ysm.animation.YsmRenderFrameClock.beginFrame();
            preview.updateAnimations(state, null);
            var second = geometryVertices(preview, YsmPartMask.PLAYER_BODY);
            require(second.size() == 24 && !first.equals(second),
                    "model default idle moves visible vertices while hidden effects stay absent");
            require(preview.animations().isActive("idle") && !preview.animations().isActive("gui")
                            && source.getPart("root").animRotRaw().lengthSquared() == 0,
                    "preview starts only default standing playback and leaves the world pose independent");
            System.out.println("PASS standing preview: controllers and hidden geometry retained, default idle changes vertices, GUI clip inactive");
        } finally {
            source.close();
            fixture.runtime.close();
            client.drain();
        }
    }

    private static void testYsmComplexity(Unsafe unsafe, RecordingClient client) throws Exception {
        var fixture = new Fixture(unsafe, 701);
        var runtime = fixture.runtime;
        var owner = runtime.owner();
        install(owner, "complexity", new Avatar.Instructions(4));
        List<String> vertices = new ArrayList<>();
        var recorder = vertexRecorder(vertices);
        try {
            top.aihmc.omnimorph.model.ysm.animation.YsmRenderFrameClock.beginFrame();
            runtime.renderer().render(new com.mojang.blaze3d.vertex.PoseStack(), type -> recorder, 0, null);
            require(vertices.size() == 24 && owner.complexity.pre == 6 && owner.complexity.remaining == -2,
                    "six visible faces use six Figura complexity units, independent of vertices and permission display");
            runtime.renderer().render(new com.mojang.blaze3d.vertex.PoseStack(), type -> recorder, 0, null);
            require(owner.complexity.pre == 12, "separate draws share the same frame counter");
            runtime.getPart("root").setVisible(false);
            top.aihmc.omnimorph.model.ysm.animation.YsmRenderFrameClock.beginFrame();
            runtime.renderer().render(new com.mojang.blaze3d.vertex.PoseStack(), type -> recorder, 0, null);
            require(owner.complexity.pre == 0, "hidden hierarchy costs zero and a new frame resets counts");
            runtime.getPart("root").setVisible(true);
            owner.renderMode = top.aihmc.omnimorph.model.rendering.EntityRenderMode.FIGURA_GUI;
            top.aihmc.omnimorph.model.ysm.animation.YsmRenderFrameClock.beginFrame();
            var preview = runtime.forRenderPass();
            require(preview != runtime, "test exercises detached GUI preview");
            preview.renderer().render(new com.mojang.blaze3d.vertex.PoseStack(), type -> recorder, 0, null);
            require(owner.complexity.pre == 6, "detached preview reports complexity to the visible avatar information");
            System.out.println("YSM Figura face complexity: visible/hidden geometry, per-frame draws, permission budget and detached preview PASS");
            top.aihmc.omnimorph.model.outfit.OutfitParticleFaceBudgetSmokeTest.run(unsafe, owner, () -> {
                vertices.clear();
                runtime.renderer().render(new com.mojang.blaze3d.vertex.PoseStack(), type -> recorder, 0, null);
                return vertices.size();
            }, client::drain);
        } finally {
            runtime.close();
            client.drain();
        }
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
        require(Files.isRegularFile(top.aihmc.omnimorph.FiguraMod.getCacheDirectory().resolve("ysm_textures.nbt")),
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
            this(unsafe, id, selectionPath, geometry, false);
        }

        private Fixture(Unsafe unsafe, int id, Path selectionPath, String geometry, boolean bakedOnly) throws Exception {
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
                if (bakedOnly) {
                    runtimeTag.remove("main_model");
                    runtimeTag.remove("arm_model");
                }
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

    private static final class RecordingToasts extends net.minecraft.client.gui.components.toasts.ToastManager {
        private List<net.minecraft.client.gui.components.toasts.Toast> entries;

        private RecordingToasts() {
            super(null, null);
        }

        @Override
        public <T extends net.minecraft.client.gui.components.toasts.Toast> T getToast(Class<? extends T> type, Object token) {
            return entries.stream().filter(type::isInstance).map(type::cast)
                    .filter(toast -> java.util.Objects.equals(toast.getToken(), token)).findFirst().orElse(null);
        }

        @Override
        public void addToast(net.minecraft.client.gui.components.toasts.Toast toast) {
            entries.add(toast);
        }

        @Override
        public void clear() {
            entries.clear();
        }
    }

    /** CPU screen dispatch only. Native window creation, mixins and rendering still require the real client. */
    public static AutoCloseable recordingScreens() {
        var client = (RecordingClient) Minecraft.getInstance();
        var previous = client.screenDispatcher;
        var previousScreen = client.screen;
        client.screenDispatcher = next -> {
            if (client.screen != null) {
                client.screen.removed();
            }
            client.screen = next;
        };
        return () -> {
            client.screenDispatcher = previous;
            client.screen = previousScreen;
        };
    }

    private static final class RecordingClient extends Minecraft implements top.aihmc.omnimorph.mixin.MinecraftAccesor {
        private RecordingTextures manager;
        private net.minecraft.client.sounds.SoundManager sounds;
        private net.minecraft.client.gui.components.toasts.ToastManager toasts;
        private ConcurrentLinkedQueue<Runnable> queue;
        private java.util.function.Consumer<net.minecraft.client.gui.screens.Screen> screenDispatcher;

        private RecordingClient() {
            super(null);
        }

        @Override
        public void setScreen(net.minecraft.client.gui.screens.Screen screen) {
            if (screenDispatcher == null) {
                super.setScreen(screen);
            } else {
                screenDispatcher.accept(screen);
            }
        }

        @Override
        public float figura$invokeGetTickTargetMillis(float milliseconds) {
            return milliseconds;
        }

        @Override
        public net.minecraft.util.profiling.metrics.profiling.MetricsRecorder figura$invokeGetMetricsRecorder() {
            return net.minecraft.util.profiling.metrics.profiling.InactiveMetricsRecorder.INSTANCE;
        }

        @Override
        public void onTextInputFocusChange(net.minecraft.client.gui.components.events.GuiEventListener input, boolean focused) {
            // Native editing is exercised; OS text-input activation belongs to manual game validation.
        }

        @Override
        public net.minecraft.world.entity.Entity getCameraEntity() {
            return null;
        }

        @Override
        public net.minecraft.client.User getUser() {
            return new net.minecraft.client.User("CPU preview fixture", UUID.fromString("10000000-0000-4000-8000-000000000999"), "",
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
        public net.minecraft.client.gui.components.toasts.ToastManager getToastManager() {
            return toasts;
        }

        @Override
        public void execute(Runnable task) {
            if (RenderSystem.isOnRenderThread()) {
                task.run();
            } else {
                queue.add(task);
            }
        }

        @Override
        public void schedule(Runnable task) {
            queue.add(task);
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
        private boolean inspectRegistration;

        private RecordingTextures() {
            super(null);
        }

        @Override
        public void register(Identifier location, AbstractTexture texture) {
            super.register(location, texture);
            if (inspectRegistration) {
                net.minecraft.client.renderer.rendertype.RenderSetup.builder(
                                net.minecraft.client.renderer.RenderPipelines.GUI_TEXTURED)
                        .withTexture("Sampler0", location).createRenderSetup().getTextures();
            }
        }

        @Override
        public AbstractTexture getTexture(Identifier location) {
            return super.getTexture(location);
        }

        @Override
        public Map<Identifier, AbstractTexture> getByPath() {
            return textures;
        }
    }
}
