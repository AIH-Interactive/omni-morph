package org.figuramc.figura.model.ysm;

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
import org.figuramc.figura.avatar.control.AvatarControlRuntime;
import org.figuramc.figura.ducks.SoundEngineAccessor;
import org.figuramc.figura.lua.api.sound.LuaSound;
import org.figuramc.figura.mixin.sound.SoundManagerAccessor;
import org.figuramc.figura.model.rendering.texture.FiguraTexture;
import org.figuramc.figura.molang.MolangEngine;
import org.figuramc.figura.molang.runtime.ExpressionEvaluator;
import org.figuramc.figura.molang.runtime.Function;
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
        client.manager.engine = engine;
        instance.set(null, client);
        Fixture first = new Fixture();
        Fixture second = new Fixture();
        var bindings = new org.figuramc.figura.molang.runtime.binding.MolangBindings.YsmBinding();
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
        System.out.println("M4 sound binding native dispatch, ID/flags, scopes, real owner quota, stop and close passed; audible playback manual.");
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

    private static final class RecordingClient extends Minecraft implements org.figuramc.figura.mixin.MinecraftAccesor {
        RecordingManager manager;

        private RecordingClient() {
            super(null);
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
