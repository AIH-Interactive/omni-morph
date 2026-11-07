package top.aihmc.omm.preview;

import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import org.figuramc.figura.lua.api.sound.LuaSound;
import org.figuramc.figura.math.vector.FiguraVec3;
import top.aihmc.omnimorph.model.ysm.animation.YsmAnimationClip;
import top.aihmc.omnimorph.model.ysm.animation.YsmAnimationEvent;
import top.aihmc.omnimorph.model.ysm.animation.YsmAnimationEventWindow;
import top.aihmc.omnimorph.model.ysm.animation.YsmControllerSoundBank;

/** Real model-owned sounds and original event windows, independent of whether Lua scripts are enabled. */
final class PreviewYsmEffects implements AutoCloseable {
    final PreviewYsmParticles particles;
    private final PreviewSounds sounds;
    private final PreviewMolang host;
    private final FiguraVec3 position;
    private final PreviewYsmSoundFunctions functionSounds;
    private final YsmControllerSoundBank controllers = new YsmControllerSoundBank();
    private final Map<Object, List<LuaSound>> playbacks = new IdentityHashMap<>();

    PreviewYsmEffects(PreviewAudio audio, PreviewLua lua, PreviewMolang host, PreviewYsmResources resources,
        JsonObject states, long seed) {
        this.host = host;
        particles = new PreviewYsmParticles(host, states, seed);
        sounds = new PreviewSounds(audio, lua, resources.audio(), seed, true);
        position = FiguraVec3.of();
        if (states.has("player") && states.getAsJsonObject("player").has("pos")) {
            var coordinates = states.getAsJsonObject("player").getAsJsonArray("pos");
            if (coordinates.size() != 3)
                throw new IllegalArgumentException("states.player.pos must contain three coordinates");
            position.set(
                coordinates.get(0).getAsDouble(), coordinates.get(1).getAsDouble(), coordinates.get(2).getAsDouble());
        }
        functionSounds = new PreviewYsmSoundFunctions(host, sounds, position);
    }

    Object soundFunction(top.aihmc.omnimorph.molang.runtime.ExecutionContext<?> execution,
        top.aihmc.omnimorph.molang.runtime.Function.ArgumentCollection arguments, String operation) {
        return functionSounds.execute(host, execution, arguments, operation);
    }

    void event(YsmAnimationEvent event, String controller, Object playback) {
        if (event.type().equals("timeline")) {
            try (var scope = host.functions.beginClientEffects(playback)) {
                host.evaluator().evalAll(event.expressions(), false);
            }
            return;
        }
        if (!event.type().equals("sound")) {
            throw new UnsupportedOperationException("YSM " + event.type() + " event host is not connected");
        }
        var parameters = event.params();
        var sound = new LuaSound(sounds.resolve(event.value()), event.value(), sounds);
        sound.setPos(FiguraVec3.of(position.x + parameter(parameters, 0, "offset_x", "offsetX", "x"),
                         position.y + parameter(parameters, 0, "offset_y", "offsetY", "y"),
                         position.z + parameter(parameters, 0, "offset_z", "offsetZ", "z")),
            null, null);
        sound.setEngineVolume((float) Math.max(0.001, Math.min(1000, parameter(parameters, 1, "volume", "vol"))));
        sound.setPitch((float) Math.max(0.001, Math.min(1000, parameter(parameters, 1, "pitch"))));
        sound.setLoop(
            Boolean.parseBoolean(parameters.getOrDefault("loop", parameters.getOrDefault("looping", "false"))));
        sound.play();
        if (sound.getHandle() == null)
            return;
        if (playback != null)
            playbacks.computeIfAbsent(playback, ignored -> new ArrayList<>()).add(sound);
        if (controller != null) {
            controllers.add(controller, () -> sound.getHandle() == null || sound.getHandle().stopped(), sound::stop);
        }
    }

    void attachLua(PreviewLua lua) {
        sounds.attachLua(lua);
    }

    void events(
        YsmAnimationClip clip, double previous, double current, boolean loop, String controller, Object playback) {
        if (previous < 0) {
            for (var event : clip.orderedEvents()) {
                if (event.time() <= 0)
                    event(event, controller, playback);
            }
            previous = 0;
            if (current <= 0)
                return;
        }
        double cycleBase = loop && Float.isFinite(clip.length) && clip.length > 0
            ? Math.max(0, Math.ceil(previous / clip.length) - 1) * clip.length
            : 0;
        var window = YsmAnimationEventWindow.window(clip, previous - cycleBase, current - cycleBase, loop, true);
        for (var event : window.beforeWrap()) event(event, controller, playback);
        if (window.wrapped())
            stopPlayback(playback);
        for (var event : window.afterWrap()) event(event, controller, playback);
    }

    void stateSounds(String controller, List<String> names) {
        for (String name : names) event(new YsmAnimationEvent(0, "sound", name), controller, null);
    }

    void stopController(String controller) {
        controllers.stop(controller);
        functionSounds.stopController(controller);
    }

    void stopPlayback(Object playback) {
        functionSounds.stopPlayback(playback);
        var owned = playbacks.remove(playback);
        if (owned != null)
            owned.forEach(LuaSound::stop);
    }

    void tick() {
        functionSounds.tick();
        controllers.prune();
        playbacks.values().forEach(
            owned -> owned.removeIf(sound -> sound.getHandle() == null || sound.getHandle().stopped()));
        playbacks.values().removeIf(List::isEmpty);
        sounds.update();
    }

    private static double parameter(Map<String, String> parameters, double fallback, String... keys) {
        for (String key : keys) {
            String value = parameters.get(key);
            if (value != null && !value.isBlank()) {
                try {
                    return Double.parseDouble(value);
                } catch (NumberFormatException ignored) {
                    // Match the original event adapter's alias/default lookup.
                }
            }
        }
        return fallback;
    }

    @Override
    public void close() {
        particles.close();
        functionSounds.close();
        controllers.close();
        playbacks.values().forEach(owned -> owned.forEach(LuaSound::stop));
        playbacks.clear();
        sounds.close();
    }
}
