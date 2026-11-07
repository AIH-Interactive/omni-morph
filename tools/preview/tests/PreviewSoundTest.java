package top.aihmc.omm.preview;

import static org.lwjgl.openal.AL10.*;

import com.google.gson.JsonObject;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.function.BooleanSupplier;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/** Tests decoded PCM and actual OpenAL source state/cursor, not a simulated playing flag. */
public final class PreviewSoundTest {
    private static void await(BooleanSupplier condition, String failure) throws Exception {
        long deadline = System.nanoTime() + 2_000_000_000L;
        while (!condition.getAsBoolean()) {
            if (System.nanoTime() >= deadline)
                throw new AssertionError(failure);
            Thread.sleep(10);
        }
    }

    private static PreviewLua host(PreviewAudio audio, Map<String, byte[]> sounds) throws Exception {
        var state = new JsonObject();
        var lua = new PreviewLua(
            new PreviewLua.Program(Map.of(), List.of(), sounds), state, 42, new PreviewMolang(state, 42), audio);
        lua.initialize();
        return lua;
    }

    public static void main(String[] args) throws Exception {
        byte[] ogg;
        try (var input = PreviewSoundTest.class.getResourceAsStream("/assets/minecraft/sounds/mob/squid/death1.ogg")) {
            if (input == null)
                throw new AssertionError("Real sound asset not packaged");
            ogg = input.readAllBytes();
        }
        var audio = new PreviewAudio();
        try (audio; var a = host(audio, Map.of("nested.test", ogg)); var b = host(audio, Map.of("nested.test", ogg))) {
            var buffer = audio.buffer(ogg);
            if (buffer.frames() <= 0 || buffer.sampleRate() <= 0
                || alGetBufferi(buffer.handle(), AL_SIZE) != buffer.frames() * buffer.channels() * 2) {
                throw new AssertionError("Decoded PCM was not actually uploaded to the OpenAL buffer");
            }
            alListenerf(AL_GAIN, 0); // Mute test output while retaining the actual device/mixer/source path.
            try (var voice = audio.voice(ogg)) {
                voice.loop(true);
                voice.play();
                await(() -> voice.sampleOffset() > 0, "Real mixer playback cursor did not advance");
                voice.pause();
                int paused = voice.sampleOffset();
                Thread.sleep(40);
                if (voice.playing() || voice.sampleOffset() != paused) {
                    throw new AssertionError("Paused source kept advancing");
                }
                voice.play();
                await(() -> voice.sampleOffset() != paused, "Unpaused source cursor did not resume");
                voice.loop(false);
                voice.pitch(8);
                await(voice::stopped, "Non-looping actual source did not finish");
            }
            a.evaluate("""
                assert(sounds:isPresent('minecraft:entity.squid.death'))
                assert(sounds:isPresent('nested.test'))
                assert(not sounds:isPresent(nil) and not sounds:isPresent('unknown'))
                assert(not pcall(function() return sounds['unknown'] end))
                calls = 0
                events.on_play_sound = function(id,pos,volume,pitch,loop,category,file)
                  assert(id == 'nested.test' and category == 'PLAYERS' and file == nil)
                  calls = calls + 1
                end
                s = sounds:playSound('nested.test',vec(1,2,3),0.2,1,true)
                assert(s:isPlaying() and s:isLooping() and calls == 1)
                assert((s:getPos()-vec(1,2,3)):length() < 0.0001)
                s:play()
                assert(calls == 1)
                s:pause()
                assert(not s:isPlaying())
                s:play()
                assert(s:isPlaying() and calls == 1)
                s:volume(4):pitch(1.5):attenuation(2):pos(3,2,1):subtitle('test caption')
                assert(s:getVolume() == 1 and s:getPitch() == 1.5 and s:getAttenuation() == 2)
                assert(s:getSubtitle() == 'test caption')
                """);
            b.evaluate("s = sounds:playSound('nested.test',0,0,0,0.1,1,true); assert(s:isPlaying())");
            a.evaluate("sounds:stopSound('nested.test'); assert(not s:isPlaying())");
            b.evaluate("assert(s:isPlaying())");
            a.evaluate("""
                events.on_play_sound = function() return true end
                local cancelled = sounds['nested.test']:play()
                assert(not cancelled:isPlaying())
                """);
            long before = ((Number) audio.snapshot().get("playCalls")).longValue();
            a.evaluate("sounds['nested.test']:play()");
            if (((Number) audio.snapshot().get("playCalls")).longValue() != before) {
                throw new AssertionError("Cancelled callback still played a native source");
            }
            a.evaluate("sounds:newSound('runtime','" + Base64.getEncoder().encodeToString(ogg) + "')");
            a.evaluate("""
                assert(sounds:isPresent('runtime') and #sounds:getCustomSounds() == 2)
                assert(not pcall(function() sounds:newSound('invalid','bm90LW9nZw==') end))
                assert(not sounds:isPresent('invalid'))
                """);
            var table = new StringBuilder("sounds:newSound('table',{");
            for (byte value : ogg) table.append(value & 255).append(',');
            table.append("}); assert(sounds:isPresent('table') and #sounds:getCustomSounds() == 3)");
            a.evaluate(table.toString());
            b.evaluate("assert(not sounds:isPresent('table'))");
            a.close();
            b.evaluate("assert(s:isPlaying()); sounds:stopSound(); assert(not s:isPlaying())");
            var ca = a.sounds.resolve("entity.squid.death");
            var cb = b.sounds.resolve("entity.squid.death");
            if (!PreviewTextures.sha256(ca.bytes()).equals(PreviewTextures.sha256(cb.bytes()))) {
                throw new AssertionError("Seeded sound variant choice is not reproducible");
            }
            if (((Number) audio.snapshot().get("decodedBufferLoads")).intValue() != 1) {
                throw new AssertionError("Same custom audio was decoded separately for each instance");
            }
            System.out.println("Real audio device: " + PreviewConfig.JSON.toJson(audio.snapshot()));
        }
        if (!Boolean.TRUE.equals(audio.snapshot().get("closed"))
            || ((Number) audio.snapshot().get("activeSources")).intValue() != 0) {
            throw new AssertionError("Device shutdown left active playback sources");
        }

        Path root = Path.of(args[0]).toAbsolutePath();
        Path folder = root.resolve("figura-sound");
        Files.createDirectories(folder.resolve("nested"));
        for (String name : List.of("blue.png", "orange.png", "main.bbmodel")) {
            Files.copy(
                root.resolve("figura-v4").resolve(name), folder.resolve(name), StandardCopyOption.REPLACE_EXISTING);
        }
        Files.write(folder.resolve("nested/test.ogg"), ogg);
        Files.writeString(folder.resolve("avatar.json"), "{\"autoScripts\":[\"script\"]}");
        Files.writeString(folder.resolve("script.lua"), """
            events.entity_init = function()
              s = sounds:playSound('nested.test',0,0,0,0.05,1,true)
              assert(s:isPlaying())
            end
            """);
        var config = new PreviewConfig();
        config.settings.enabled = false;
        config.configFile = root.resolve("figura-sound.json");
        config.window.title = "omm-core real OpenAL avatar sound";
        config.scene.models = List.of(folder.toString());
        config.animation.enabled = true;
        config.animation.mode = "figura";
        config.scripts.enabled = true;
        config.vanilla.enabled = false;
        config.recording.outputDirectory = "runs";
        Files.writeString(config.configFile, PreviewConfig.JSON.toJson(config));
        Path zip = root.resolve("figura-sound.zip");
        try (var output = new ZipOutputStream(Files.newOutputStream(zip)); var paths = Files.walk(folder)) {
            for (Path path : paths.filter(Files::isRegularFile).sorted().toList()) {
                output.putNextEntry(new ZipEntry("avatar/" + folder.relativize(path).toString().replace('\\', '/')));
                Files.copy(path, output);
                output.closeEntry();
            }
        }
        var zipProgram = PreviewLua.Program.load(zip);
        if (!PreviewTextures.sha256(zipProgram.sounds().get("nested.test")).equals(PreviewTextures.sha256(ogg))) {
            throw new AssertionError("Nested ZIP custom sound ID or bytes differ from folder");
        }
        try (var animated = new PreviewAnimation(PreviewMesh.load(config), config)) {
            var state = animated.newState();
            state.sample(0, 1);
            state.sample(1, 0.25f);
            state.lua().evaluate("assert(s:isPlaying())");
            if (((Number) animated.audio.snapshot().get("activeSources")).intValue() != 1) {
                throw new AssertionError("Backward seek left the old avatar voice playing");
            }
        }
        config.scene.models = List.of(zip.toString());
        config.configFile = root.resolve("figura-sound-zip.json");
        Files.writeString(config.configFile, PreviewConfig.JSON.toJson(config));
        System.out.println("Sound PASS: real decoded PCM/mixer cursor, Lua API, pause/resume/finish, callbacks, shared "
            + "buffers and owner/seek cleanup");
    }
}
