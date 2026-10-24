package top.aihmc.omnimorph.model.ysm;

import com.mojang.blaze3d.audio.Channel;
import com.mojang.blaze3d.audio.Library;
import com.mojang.blaze3d.audio.SoundBuffer;
import net.minecraft.SharedConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.sounds.ChannelAccess;
import net.minecraft.client.sounds.SoundBufferLibrary;
import net.minecraft.client.sounds.SoundEngine;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.server.Bootstrap;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.phys.Vec3;
import org.figuramc.figura.avatar.Avatar;
import top.aihmc.omnimorph.avatar.control.AvatarControlRuntime;
import org.figuramc.figura.ducks.SoundEngineAccessor;
import org.figuramc.figura.lua.api.sound.LuaSound;
import org.figuramc.figura.mixin.sound.SoundManagerAccessor;
import org.figuramc.figura.model.rendering.texture.FiguraTexture;
import top.aihmc.omnimorph.molang.MolangEngine;
import top.aihmc.omnimorph.molang.runtime.ExpressionEvaluator;
import top.aihmc.omnimorph.molang.runtime.Function;
import org.figuramc.figura.permissions.PermissionPack;
import org.figuramc.figura.permissions.Permissions;
import org.figuramc.figura.utils.RefilledNumber;
import sun.misc.Unsafe;

import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

/** Uses real LuaSound with a recording native engine; no OpenAL or audible playback claim. */
public final class YsmSoundNativeSmokeTest {
    private static Unsafe unsafe;

    public static void main(String[] arguments) throws Exception {
        Path directory = Files.createTempDirectory("figura-native-sound-").toAbsolutePath().normalize();
        String previousDirectory = System.getProperty("figura.test.directory");
        System.setProperty("figura.test.directory", directory.toString());
        Field instance = Minecraft.class.getDeclaredField("instance");
        instance.setAccessible(true);
        Object previousClient = instance.get(null);
        try {
            SharedConstants.tryDetectVersion();
            Bootstrap.bootStrap();
            Field field = Unsafe.class.getDeclaredField("theUnsafe");
            field.setAccessible(true);
            unsafe = (Unsafe) field.get(null);
            runTests(instance);
        } finally {
            instance.set(null, previousClient);
            if (previousDirectory == null) {
                System.clearProperty("figura.test.directory");
            } else {
                System.setProperty("figura.test.directory", previousDirectory);
            }
            try (var files = Files.walk(directory)) {
                for (Path path : files.sorted(Comparator.reverseOrder()).toList()) {
                    if (!path.toAbsolutePath().normalize().startsWith(directory)) {
                        throw new IllegalStateException("Unexpected fixture cleanup path");
                    }
                    Files.delete(path);
                }
            }
        }
    }

    private static void runTests(Field instance) throws Exception {
        RecordingClient client = allocate(RecordingClient.class);
        client.manager = allocate(RecordingManager.class);
        RecordingEngine engine = allocate(RecordingEngine.class);
        engine.submitted = new ArrayList<>();
        engine.handles = new ArrayList<>();
        engine.released = new ArrayList<>();
        client.manager.engine = engine;
        instance.set(null, client);
        Fixture first = new Fixture();
        Fixture second = new Fixture();
        var bindings = new top.aihmc.omnimorph.molang.runtime.binding.MolangBindings.YsmBinding();
        Function play = (Function) bindings.getProperty("play_sound");
        Function stop = (Function) bindings.getProperty("stop_sound");
        Function all = (Function) bindings.getProperty("stop_all_sounds");
        require(!play.validateArgumentSize(1) && play.validateArgumentSize(2)
                        && play.validateArgumentSize(5) && !play.validateArgumentSize(6), "play arity 2..5");
        require(!stop.validateArgumentSize(0) && stop.validateArgumentSize(1)
                        && stop.validateArgumentSize(2) && !stop.validateArgumentSize(3), "stop arity 1..2");
        require(all.validateArgumentSize(0) && all.validateArgumentSize(1)
                        && !all.validateArgumentSize(2), "stop all arity 0..1");
        require(Boolean.FALSE.equals(first.eval("ysm.play_sound(v.ignored=1,'tone');"))
                        && first.context.variables.getPath("ignored") == null,
                "outside client effects returns Boolean false before evaluating arguments");
        try (var effects = first.runtime.functions().beginClientEffects(null);
             var otherEffects = second.runtime.functions().beginClientEffects(null)) {
            first.context.controller.setPath("controller", "first");
            second.context.controller.setPath("controller", "first");
            require(Boolean.TRUE.equals(first.eval("ysm.play_sound('slot','tone',4,12,0);")), "real custom-buffer dispatch");
            LuaSound original = engine.submitted.getLast();
            RecordingHandle originalHandle = engine.handles.getLast();
            require(original.getVolume() == 12 && original.getPitch() == 0.001f && original.isLooping(),
                    "volume, pitch minimum and loop reach the actual sound object");
            require(original.getPos().x == 1 && original.getPos().y == 2 && original.getPos().z == 3,
                    "entity position reaches native playback");
            require(engine.owner.equals(first.owner.owner) && first.owner.soundsRemaining.peek() == 99,
                    "actual Avatar UUID and quota are used");
            require(Boolean.FALSE.equals(first.eval("ysm.play_sound('slot','tone');")) && engine.submitted.size() == 1,
                    "duplicate ID does not play again");
            require(Boolean.TRUE.equals(second.eval("ysm.play_sound('slot','tone');")), "other Avatar accepts the same ID");
            LuaSound otherAvatar = engine.submitted.getLast();
            first.context.controller.setPath("controller", "other");
            require(Boolean.TRUE.equals(first.eval("ysm.play_sound('slot','tone');")), "other controller accepts the same ID");
            LuaSound otherController = engine.submitted.getLast();
            first.context.controller.setPath("controller", "first");
            require(Boolean.TRUE.equals(first.eval("ysm.play_sound('slot','tone',1,2000,2000);")), "replace flag restarts owned ID");
            require(original.getHandle() == null && originalHandle.commands > 0, "old native handle receives stop");
            require(engine.submitted.getLast().getVolume() == 1000 && engine.submitted.getLast().getPitch() == 1000,
                    "maximum volume and pitch are 1000");
            require(otherAvatar.getHandle() != null && otherController.getHandle() != null, "replacement is isolated");
            require(Boolean.TRUE.equals(first.eval("ysm.stop_sound('slot');"))
                            && Boolean.FALSE.equals(first.eval("ysm.stop_sound('slot');")), "stop reports ID presence");
            require(Boolean.TRUE.equals(first.eval("ysm.play_sound(7,'tone');")), "numeric ID accepted");
            require(Boolean.FALSE.equals(first.eval("ysm.stop_sound('7');")), "numeric and string IDs differ");
            require(Boolean.TRUE.equals(first.eval("ysm.stop_sound(7);")), "numeric ID resolves its own handle");
            require(Boolean.FALSE.equals(first.eval("ysm.play_sound(-1,v.skipped=5);"))
                            && first.context.variables.getPath("skipped") == null, "negative ID short circuit");
            require(Boolean.FALSE.equals(first.eval("ysm.play_sound(1,'tone',8);")), "invalid flags rejected");
            require(Boolean.TRUE.equals(first.eval("ysm.play_sound(0,'tone');"))
                            && Boolean.TRUE.equals(first.eval("ysm.play_sound(0,'tone');")), "zero IDs are anonymous");
            LuaSound anonymous = engine.submitted.getLast();
            require(Boolean.FALSE.equals(first.eval("ysm.stop_sound(0);")), "zero cannot select anonymous playback");
            require(Boolean.TRUE.equals(first.eval("ysm.play_sound('global','tone',6);")), "global loop created");
            LuaSound global = engine.submitted.getLast();
            require(Boolean.FALSE.equals(first.eval("ysm.stop_sound('global');")), "local stop preserves global ID");
            require(Boolean.TRUE.equals(first.eval("ysm.stop_all_sounds();"))
                            && anonymous.getHandle() == null && global.getHandle() != null,
                    "local stop all includes anonymous sounds and preserves global");
            first.runtime.animations().stopControllerSounds("other");
            require(otherController.getHandle() == null && otherAvatar.getHandle() != null, "controller exit isolation");
            require(Boolean.TRUE.equals(first.eval("ysm.stop_sound('global',1);")) && global.getHandle() == null,
                    "global flag selects global bank");
            first.context.controller.setPath("controller", "");
            Object playbackA = new Object();
            Object playbackB = new Object();
            try (var playback = first.runtime.functions().beginClientEffects(playbackA)) {
                require(Boolean.TRUE.equals(first.eval("ysm.play_sound('same','tone');")), "animation playback A scope");
            }
            try (var playback = first.runtime.functions().beginClientEffects(playbackB)) {
                require(Boolean.FALSE.equals(first.eval("ysm.stop_sound('same');")), "animation B cannot stop A");
            }
            try (var playback = first.runtime.functions().beginClientEffects(playbackA)) {
                require(Boolean.TRUE.equals(first.eval("ysm.stop_sound('same');")), "animation A restores its bank");
            }
            first.context.controller.setPath("controller", "first");
            require(Boolean.TRUE.equals(first.eval("ysm.play_sound(9,'tone');")), "recovery after stop");
            LuaSound moving = engine.submitted.getLast();
            set(Entity.class, first.context.entity, "position", new Vec3(4, 5, 6));
            first.runtime.sounds().tick();
            require(moving.getPos().x == 4 && moving.getPos().y == 5 && moving.getPos().z == 6, "native entity following");
            first.runtime.sounds().close();
            require(moving.getHandle() == null && otherAvatar.getHandle() != null, "close preserves other Avatar");
            require(Boolean.FALSE.equals(first.eval("ysm.play_sound(9,'tone');")), "closed adapter cannot restart");
            second.runtime.sounds().close();
        }
        first.context.entity = null;
        require(first.eval("ysm.stop_all_sounds();") == null, "missing Entity is raw null");
        LuaSound lua = new LuaSound((SoundBuffer) null, "lua-volume", first.owner);
        lua.setVolume(12);
        require(lua.getVolume() == 1, "original Figura Lua volume limit preserved");
        testAvatarVolumeGain(engine);
        testAnimationBgmLifetime(engine);
        testModelAudio(engine);
        System.out.println("M4 sound binding native dispatch, ID/flags, scopes, real owner quota, stop and close passed; audible playback manual.");
    }

    private static void testAvatarVolumeGain(RecordingEngine engine) throws Exception {
        var config = org.figuramc.figura.config.Configs.AVATAR_VOLUME_GAIN;
        float previousValue = config.value;
        float previousTemp = config.tempValue;
        Fixture fixture = new Fixture();
        LuaSound sound = new LuaSound(fixture.owner.customSounds.get("tone"), "gain", fixture.owner);
        try {
            require(config.defaultValue == 1f && org.figuramc.figura.config.ConfigManager.REGISTRY.contains(config),
                    "shared gain defaults to unchanged volume and participates in ordinary config persistence");
            config.setTempValue("2");
            require(config.value == previousValue && config.tempValue == 2f, "editing gain waits for config apply");
            config.discardConfig();
            require(config.tempValue == config.value, "config cancel discards the staged gain");
            require(config.parseValue("-1") == 0f && config.parseValue("20") == 10f,
                    "gain is bounded to 0..10");
            for (String invalid : List.of("NaN", "Infinity")) {
                try {
                    config.parseValue(invalid);
                    throw new AssertionError("non-finite gain accepted");
                } catch (NumberFormatException expected) {
                    // Config/UI reject non-finite input rather than send it to the sound engine.
                }
            }
            config.value = 1f;
            sound.setVolume(0.5f).play();
            RecordingHandle handle = (RecordingHandle) sound.getHandle();
            int commands = handle.commands;
            var volumeEngine = new VolumeEngine();
            Field handlers = org.figuramc.figura.mixin.sound.SoundEngineMixin.class.getDeclaredField("figuraHandlers");
            handlers.setAccessible(true);
            @SuppressWarnings("unchecked")
            List<LuaSound> active = (List<LuaSound>) handlers.get(volumeEngine);
            active.add(sound);
            var tick = org.figuramc.figura.mixin.sound.SoundEngineMixin.class.getDeclaredMethod(
                    "tickNonPaused", org.spongepowered.asm.mixin.injection.callback.CallbackInfo.class);
            tick.setAccessible(true);
            config.value = 2f;
            tick.invoke(volumeEngine, new Object[] {null});
            require(sound.calculateVolume() == 2f && handle.commands == commands + 1,
                    "changing the shared gain updates an already playing Figura voice on the real mixin tick path");
            fixture.owner.permissions.getPermissions().put(Permissions.VOLUME, 50);
            require(sound.calculateVolume() == 1f, "gain multiplies the existing avatar permission volume");
            config.value = 0f;
            tick.invoke(volumeEngine, new Object[] {null});
            require(sound.calculateVolume() == 0f && handle.commands == commands + 2,
                    "zero gain dispatches an update to the active native channel");
            int submitted = engine.submitted.size();
            sound.stop().play();
            require(sound.getHandle() == null && engine.submitted.size() == submitted,
                    "zero gain suppresses new avatar playback");
        } finally {
            sound.stop();
            config.value = previousValue;
            config.tempValue = previousTemp;
            fixture.runtime.sounds().close();
        }
    }

    private static final class VolumeEngine extends org.figuramc.figura.mixin.sound.SoundEngineMixin {
        @Override
        public void addEventListener(net.minecraft.client.sounds.SoundEventListener listener) {
        }

        @Override
        public float figura$getVolume(SoundSource source) {
            return 1f;
        }
    }

    private static void testAnimationBgmLifetime(RecordingEngine engine) throws Exception {
        List<String> failures = new ArrayList<>();
        for (String interruption : List.of("movement", "switch", "restart", "stop", "fade", "finish", "remote")) {
            Fixture fixture = new Fixture();
            var player = fixture.runtime.animations();
            player.registerAnimations(top.aihmc.omnimorph.model.ysm.animation.YsmAnimationParser.parse("""
                    {"animations":{
                      "dance":{"loop":true,"animation_length":10,
                        "timeline":{"0":"ysm.play_sound('bgm','tone',4); ysm.play_sound('global_bgm','tone',6);"},
                        "sound_effects":{"0":{"effect":"tone","loop":true}}},
                      "other":{"loop":true,"animation_length":10},
                      "walk":{"loop":true,"animation_length":1}}}
                    """, fixture.engine));
            fixture.runtime.actions().buildDefaultsFromAnimations();
            set(player.getClass(), player, "currentEntity", fixture.context.entity);
            LuaSound ambient = new LuaSound(fixture.owner.customSounds.get("tone"), "unrelated", fixture.owner).play();
            try {
                require(fixture.runtime.actions().trigger("dance"), "BGM action starts");
                List<LuaSound> bgm = List.copyOf(engine.submitted.subList(engine.submitted.size() - 3, engine.submitted.size()));
                require(bgm.stream().allMatch(sound -> sound.getHandle() != null), "timeline and sound_effects BGM really start");
                if (interruption.equals("movement")) {
                    var state = new net.minecraft.client.renderer.entity.state.LivingEntityRenderState();
                    state.walkAnimationSpeed = 0.5f;
                    var update = player.getClass().getDeclaredMethod("updateNativeStateMachine",
                            state.getClass(), net.minecraft.world.entity.LivingEntity.class);
                    update.setAccessible(true);
                    update.invoke(player, state, null);
                    require(!fixture.runtime.actions().isActive("dance"), "movement cancels the action");
                } else if (interruption.equals("remote")) {
                    set(player.getClass(), player, "observerControlled", true);
                    var clips = player.getClips().values().stream().distinct()
                            .sorted(java.util.Comparator.comparing(clip -> clip.name)).toList();
                    player.applyRemotePlaybacks(player.captureRemotePlaybacks(clips, List.of()), clips, List.of());
                    require(bgm.stream().allMatch(sound -> sound.getHandle() != null),
                            "ordinary observer state refresh keeps the current BGM playing");
                    player.stop("dance");
                } else if (interruption.equals("stop")) {
                    fixture.runtime.actions().stop("dance");
                } else if (interruption.equals("fade") || interruption.equals("finish")) {
                    if (interruption.equals("fade")) {
                        player.fadeOut("dance", 0.1f);
                    } else {
                        player.getActiveAnimation("dance").loop = false;
                        player.getActiveAnimation("dance").loopMode =
                                top.aihmc.omnimorph.model.ysm.animation.YsmAnimationClip.LoopMode.ONCE;
                    }
                    var advance = player.getClass().getDeclaredMethod("advanceAnimationTimeline", float.class, ExpressionEvaluator.class);
                    advance.setAccessible(true);
                    advance.invoke(player, 11f, ExpressionEvaluator.evaluator(fixture.context));
                    advance.invoke(player, 1f, ExpressionEvaluator.evaluator(fixture.context));
                } else {
                    require(fixture.runtime.actions().trigger(interruption.equals("switch") ? "other" : "dance"),
                            "replacement action starts");
                }
                require(ambient.getHandle() != null, "action cleanup preserves unrelated sound from the same Avatar");
                long remaining = bgm.stream().filter(sound -> sound.getHandle() != null).count();
                if (remaining != 0) {
                    failures.add(interruption + " leaves " + remaining + " old BGM handles playing");
                }
            } finally {
                ambient.stop();
                player.closeSounds();
                fixture.runtime.sounds().close();
            }
        }
        require(failures.isEmpty(), String.join("; ", failures));
    }

    private static void testModelAudio(RecordingEngine engine) throws Exception {
        Fixture first = new Fixture();
        Fixture second = new Fixture();
        var files = new HashMap<String, byte[]>();
        files.put("main.json", "{\"minecraft:geometry\":[{\"bones\":[{\"name\":\"root\"}]}]}"
                .getBytes(java.nio.charset.StandardCharsets.UTF_8));
        files.put("sounds/model_tone.ogg", YsmAudioDecodeSmokeTest.opus(1));
        try (var source = new top.aihmc.omnimorph.avatar.ysm.YsmMemoryResourceSource("audio-fixture", files);
             var data = top.aihmc.omnimorph.avatar.ysm.YsmModelData.read(source,
                     top.aihmc.omnimorph.avatar.ysm.YsmSourceIdentity.ContainerKind.MEMORY, () -> false)) {
            var tag = top.aihmc.omnimorph.avatar.ysm.YsmAvatarLoader.toRuntimeTag(data, null);
            first.runtime.audio().load(tag.getListOrEmpty("sound_entries"));
            second.runtime.audio().load(tag.getListOrEmpty("sound_entries"));
        }
        var firstBuffer = first.runtime.audio().get("model_tone");
        var secondBuffer = second.runtime.audio().get("model_tone");
        require(firstBuffer != null && secondBuffer != null && firstBuffer != secondBuffer
                        && firstBuffer.size() == 5360, "models own independent decoded native buffers");
        testSubEntityAudio(engine, first, firstBuffer);
        var api = new org.figuramc.figura.lua.api.sound.SoundAPI(first.owner);
        require(api.isPresent("model_tone") && api.getCustomSounds().contains("model_tone")
                        && !first.owner.customSounds.containsKey("model_tone"), "Lua sound API consumes model resources separately");
        var player = first.runtime.animations();
        var clip = new top.aihmc.omnimorph.model.ysm.animation.YsmAnimationClip("animation.parallel0", 1f, true);
        clip.events.add(new top.aihmc.omnimorph.model.ysm.animation.YsmAnimationEvent(0f, "sound", "model_tone"));
        player.registerAnimations(Map.of(clip.name, clip));
        int beforeInitial = engine.submitted.size();
        player.startBaseAnimations();
        require(engine.submitted.size() == beforeInitial, "base sound waits until the render subject exists");
        set(top.aihmc.omnimorph.model.ysm.animation.YsmAnimationPlayer.class, player, "currentEntity", first.context.entity);
        var advance = top.aihmc.omnimorph.model.ysm.animation.YsmAnimationPlayer.class.getDeclaredMethod(
                "advanceAnimationTimeline", float.class, top.aihmc.omnimorph.molang.runtime.ExpressionEvaluator.class);
        advance.setAccessible(true);
        var evaluator = ExpressionEvaluator.evaluator(first.context);
        advance.invoke(player, 0.1f, evaluator);
        require(engine.submitted.size() == beforeInitial + 1, "base zero-time sound reaches the first real entity update");
        advance.invoke(player, 0.1f, evaluator);
        require(engine.submitted.size() == beforeInitial + 1, "base zero-time sound does not repeat next frame");
        LuaSound retained = api.__index("model_tone");
        retained.play();
        require(retained.getHandle() != null, "Lua can play a real decoded model sound");
        try (var firstEffects = first.runtime.functions().beginClientEffects(null);
             var secondEffects = second.runtime.functions().beginClientEffects(null)) {
            require(Boolean.TRUE.equals(first.eval("ysm.play_sound('voice','model_tone',4);")), "decoded first-model playback");
            LuaSound firstSound = engine.submitted.getLast();
            require(Boolean.TRUE.equals(second.eval("ysm.play_sound('voice','model_tone',4);")), "same-name second-model playback");
            LuaSound secondSound = engine.submitted.getLast();
            Field buffer = LuaSound.class.getDeclaredField("buffer");
            buffer.setAccessible(true);
            require(buffer.get(firstSound) == firstBuffer && buffer.get(secondSound) == secondBuffer,
                    "actual native playback receives its own model buffer");
            first.runtime.sounds().close();
            first.runtime.audio().close();
            require(firstSound.getHandle() == null && secondSound.getHandle() != null
                            && engine.released.contains(firstBuffer) && !engine.released.contains(secondBuffer),
                    "closing one model stops and disposes only its own audio");
            require(retained.getHandle() == null && buffer.get(retained) == null && buffer.get(firstSound) == null,
                    "closing model stops Lua playback and releases retained PCM references");
            int submissions = engine.submitted.size();
            retained.play();
            require(retained.getHandle() == null && engine.submitted.size() == submissions,
                    "retained Lua sound cannot revive a closed model buffer");
            int releases = engine.released.size();
            first.runtime.audio().close();
            require(engine.released.size() == releases && first.runtime.audio().names().isEmpty(), "resource close is idempotent");
            second.runtime.sounds().close();
            second.runtime.audio().close();
        }
        System.out.println("M5 runtime audio assembly/Opus decode/SoundAPI/native playback and same-name model cleanup passed (CPU engine).");
    }

    private static void testSubEntityAudio(RecordingEngine engine, Fixture parent, SoundBuffer shared) throws Exception {
        org.figuramc.figura.permissions.PermissionManager.init();
        var geometry = YsmGeometryParser.parse("{\"minecraft:geometry\":[{\"bones\":[{\"name\":\"root\"}]}]}");
        var constructor = YsmSubEntity.class.getDeclaredConstructor(String.class, String.class, List.class,
                String.class, String.class, YsmGeometry.class, FiguraTexture.class, boolean.class,
                String.class, String.class, String.class, String.class);
        constructor.setAccessible(true);
        // This audio-only fixture never renders a texture; native image ownership has its own real-object gate.
        var definition = constructor.newInstance("projectile", "minecraft:arrow", List.of("minecraft:arrow"),
                "", "", geometry, null, false, "", "", "", "");
        var add = YsmModelRuntime.class.getDeclaredMethod("addSubEntity", YsmSubEntity.class);
        add.setAccessible(true);
        add.invoke(parent.runtime, definition);
        parent.context.entity.setUUID(parent.owner.owner);
        AudioArrow first = audioArrow(parent.context.entity, 71);
        AudioArrow second = audioArrow(parent.context.entity, 72);
        YsmModelRuntime a = parent.runtime.subEntityInstances().acquire(first, "projectile", "minecraft:arrow");
        YsmModelRuntime b = parent.runtime.subEntityInstances().acquire(second, "projectile", "minecraft:arrow");
        a.owner().getMolangContext().entity = first;
        b.owner().getMolangContext().entity = second;
        require(a.audio() == parent.runtime.audio() && b.audio() == parent.runtime.audio(),
                "child voices borrow one decoded model PCM pool");
        try (var firstEffects = a.functions().beginClientEffects(null);
             var secondEffects = b.functions().beginClientEffects(null)) {
            require(Boolean.TRUE.equals(eval(a, "ysm.play_sound('voice','model_tone',4);")), "first physical instance loops its own voice");
            LuaSound firstSound = engine.submitted.getLast();
            require(Boolean.TRUE.equals(eval(b, "ysm.play_sound('voice','model_tone',4);")), "second physical instance loops the same sound name independently");
            LuaSound secondSound = engine.submitted.getLast();
            Field buffer = LuaSound.class.getDeclaredField("buffer");
            buffer.setAccessible(true);
            require(buffer.get(firstSound) == shared && buffer.get(secondSound) == shared,
                    "separate voices reuse immutable PCM without duplicate native allocations");
            set(Entity.class, first, "removalReason", Entity.RemovalReason.DISCARDED);
            parent.runtime.subEntityInstances().tick(null);
            require(a.isClosed() && firstSound.getHandle() == null && secondSound.getHandle() != null
                            && !engine.released.contains(shared),
                    "physical removal stops only that instance and preserves shared PCM and the other voice");
            int submissions = engine.submitted.size();
            firstSound.play();
            require(firstSound.getHandle() == null && engine.submitted.size() == submissions,
                    "retained sound cannot revive a removed instance while the parent PCM remains live");
            parent.runtime.subEntityInstances().close();
            require(b.isClosed() && secondSound.getHandle() == null && !engine.released.contains(shared),
                    "closing the instance container stops all voices before parent PCM disposal");
        }
        System.out.println("M6 native child audio: shared PCM, independent same-name loops, physical removal, retained-object guard and container cleanup PASS; no audible claim.");
    }

    private static AudioArrow audioArrow(Entity owner, int id) throws Exception {
        AudioArrow arrow = allocate(AudioArrow.class);
        arrow.sourceOwner = owner;
        arrow.setId(id);
        set(Entity.class, arrow, "type", net.minecraft.world.entity.EntityType.ARROW);
        set(Entity.class, arrow, "position", new Vec3(id, 2, 3));
        return arrow;
    }

    private static Object eval(YsmModelRuntime runtime, String source) throws Exception {
        var context = runtime.owner().getMolangContext();
        var engine = MolangEngine.fromCustomBinding(runtime.owner().getAvatarBindings());
        return ExpressionEvaluator.evaluator(context).eval(engine.parse(source).getFirst());
    }

    private static final class AudioArrow extends net.minecraft.world.entity.projectile.arrow.Arrow {
        private Entity sourceOwner;

        private AudioArrow() {
            super(net.minecraft.world.entity.EntityType.ARROW, null);
        }

        @Override
        public Entity getOwner() {
            return sourceOwner;
        }
    }

    private static final class Fixture {
        final Avatar owner;
        final Avatar.MolangContext context;
        final YsmModelRuntime runtime;
        final MolangEngine engine;

        Fixture() throws Exception {
            owner = allocate(Avatar.class);
            set(Avatar.class, owner, "owner", UUID.randomUUID());
            set(Avatar.class, owner, "customSounds", new HashMap<>(Map.of("tone", allocate(SoundBuffer.class))));
            var category = new PermissionPack.CategoryPermissionPack(Permissions.Category.MAX);
            set(Avatar.class, owner, "permissions", new PermissionPack.PlayerPermissionPack(category, owner.owner.toString()));
            owner.permissions.getPermissions().put(Permissions.CUSTOM_SOUNDS, 1);
            owner.permissions.getPermissions().put(Permissions.VOLUME, 100);
            set(Avatar.class, owner, "noPermissions", new HashSet<Permissions>());
            set(Avatar.class, owner, "soundsRemaining", new RefilledNumber(100, 100));
            set(Avatar.class, owner, "controls", new AvatarControlRuntime());
            context = new Avatar.MolangContext(owner);
            context.entity = allocate(ArmorStand.class);
            set(Entity.class, context.entity, "position", new Vec3(1, 2, 3));
            set(Avatar.class, owner, "molangContext", context);
            var constructor = YsmModelRuntime.class.getDeclaredConstructor(Avatar.class,
                    YsmGeometry.class, YsmGeometry.class, FiguraTexture.class, String.class, boolean.class,
                    String.class, String.class, Map.class, float.class, float.class, boolean.class);
            constructor.setAccessible(true);
            runtime = constructor.newInstance(owner, new YsmGeometry(), null, null,
                    "fixture", false, "NEW", "native-sound-fixture", Map.of(), 1f, 1f, false);
            set(Avatar.class, owner, "ysmRuntime", runtime);
            engine = MolangEngine.fromCustomBinding(owner.getAvatarBindings());
        }

        Object eval(String source) throws Exception {
            return ExpressionEvaluator.evaluator(context).eval(engine.parse(source).getFirst());
        }
    }

    private static final class RecordingClient extends Minecraft implements top.aihmc.omnimorph.mixin.MinecraftAccesor {
        RecordingManager manager;

        private RecordingClient() {
            super(null);
        }

        @Override
        public net.minecraft.client.User getUser() {
            return new net.minecraft.client.User("CPU child audio", new UUID(0, 999), "",
                    java.util.Optional.empty(), java.util.Optional.empty());
        }

        @Override
        public SoundManager getSoundManager() {
            return manager;
        }

        @Override
        public float figura$invokeGetTickTargetMillis(float value) {
            return 50f;
        }

        @Override
        public net.minecraft.util.profiling.metrics.profiling.MetricsRecorder figura$invokeGetMetricsRecorder() {
            return net.minecraft.util.profiling.metrics.profiling.InactiveMetricsRecorder.INSTANCE;
        }
    }

    private static final class RecordingManager extends SoundManager implements SoundManagerAccessor {
        RecordingEngine engine;

        private RecordingManager() {
            super(null);
        }

        @Override
        public SoundEngine getSoundEngine() {
            return engine;
        }
    }

    private static final class RecordingEngine extends SoundEngine implements SoundEngineAccessor {
        List<LuaSound> submitted;
        List<RecordingHandle> handles;
        List<SoundBuffer> released;
        UUID owner;

        private RecordingEngine() {
            super(null, null, null);
        }

        @Override
        public void figura$addSound(LuaSound sound) {
            submitted.add(sound);
        }

        @Override
        public ChannelAccess.ChannelHandle figura$createHandle(UUID owner, String id, Library.Pool pool) {
            try {
                RecordingHandle handle = allocate(RecordingHandle.class);
                handles.add(handle);
                this.owner = owner;
                return handle;
            } catch (Exception failure) {
                throw new IllegalStateException(failure);
            }
        }

        @Override
        public void figura$releaseBuffer(SoundBuffer buffer) {
            released.add(buffer);
            buffer.discardAlBuffer();
        }

        @Override
        public float figura$getVolume(SoundSource source) {
            return 1f;
        }

        @Override
        public boolean figura$isPlaying(UUID owner) {
            return false;
        }

        @Override
        public boolean figura$isEngineActive() {
            return true;
        }

        @Override
        public SoundBufferLibrary figura$getSoundBuffers() {
            throw new AssertionError("Custom buffer does not load native resources");
        }

        @Override
        public void figura$stopSound(UUID owner, String id) {
            throw new AssertionError("Must stop individual handles");
        }

        @Override
        public void figura$stopAllSounds() {
            throw new AssertionError("Must preserve unrelated sounds");
        }
    }

    private static final class RecordingHandle extends ChannelAccess.ChannelHandle {
        int commands;

        private RecordingHandle(ChannelAccess access) {
            access.super(null);
        }

        @Override
        public boolean isStopped() {
            return false;
        }

        @Override
        public void execute(Consumer<Channel> operation) {
            commands++;
        }
    }

    private static <T> T allocate(Class<T> type) throws Exception {
        return type.cast(unsafe.allocateInstance(type));
    }

    private static void set(Class<?> type, Object target, String name, Object value) throws Exception {
        Field field = type.getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
