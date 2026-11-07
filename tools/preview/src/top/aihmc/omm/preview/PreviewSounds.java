package top.aihmc.omm.preview;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import org.figuramc.figura.lua.api.sound.LuaSound;
import org.luaj.vm2.LuaError;

/** Avatar-local sound registry and voice ownership, backed by the shared actual playback device. */
public final class PreviewSounds implements AutoCloseable {
    public record Clip(byte[] bytes, String subtitle, boolean modelAudio) {
        public Clip(byte[] bytes, String subtitle) {
            this(bytes, subtitle, false);
        }
    }
    private record Choice(String name, int weight) {}
    private final PreviewAudio audio;
    private PreviewLua owner;
    private final Random random;
    private final Map<String, byte[]> customSounds = new LinkedHashMap<>();
    private final Map<String, byte[]> gameSounds = new LinkedHashMap<>();
    private final Map<LuaSound, PreviewAudio.Voice> voices = new LinkedHashMap<>();
    private JsonObject catalog;
    private final boolean modelAudio;

    PreviewSounds(PreviewAudio audio, PreviewLua owner, Map<String, byte[]> resources, long seed) {
        this(audio, owner, resources, seed, false);
    }

    PreviewSounds(PreviewAudio audio, PreviewLua owner, Map<String, byte[]> resources, long seed, boolean modelAudio) {
        this.audio = audio;
        this.owner = owner;
        random = new Random(seed);
        customSounds.putAll(resources);
        this.modelAudio = modelAudio;
    }

    void attachLua(PreviewLua owner) {
        this.owner = owner;
    }

    private JsonObject catalog() {
        if (catalog == null) {
            try (var input = PreviewSounds.class.getResourceAsStream("/assets/minecraft/sounds.json")) {
                if (input == null)
                    throw new IOException("Packaged Minecraft sound catalog missing");
                catalog =
                    JsonParser.parseString(new String(input.readAllBytes(), StandardCharsets.UTF_8)).getAsJsonObject();
            } catch (IOException failure) {
                throw new LuaError(failure);
            }
        }
        return catalog;
    }

    private static String eventName(String id) {
        return id != null && id.startsWith("minecraft:") ? id.substring(10) : id;
    }

    public boolean isPresent(String id) {
        return id != null && (customSounds.containsKey(id) || catalog().has(eventName(id)));
    }

    public Set<String> customNames() {
        return Set.copyOf(customSounds.keySet());
    }

    public void loadSound(String name, byte[] bytes) {
        audio.buffer(bytes);
        customSounds.put(name, bytes.clone());
    }

    public Clip resolve(String id) {
        byte[] custom = customSounds.get(id);
        if (custom != null)
            return new Clip(custom, id, modelAudio);
        String name = eventName(id);
        if (name == null || !catalog().has(name)) {
            throw new LuaError("Sound not available in the preview assets: " + id);
        }
        List<Choice> choices = new ArrayList<>();
        collect(name, 1, choices);
        int totalWeight = choices.stream().mapToInt(Choice::weight).sum();
        if (totalWeight <= 0)
            throw new LuaError("Sound event has no playable variants: " + id);
        int selected = random.nextInt(totalWeight);
        String path = null;
        for (var choice : choices) {
            selected -= choice.weight;
            if (selected < 0) {
                path = choice.name;
                break;
            }
        }
        String resource =
            "/assets/" + (path.contains(":") ? path : "minecraft:" + path).replace(":", "/sounds/") + ".ogg";
        byte[] bytes = gameSounds.get(resource);
        if (bytes == null) {
            try (var input = PreviewSounds.class.getResourceAsStream(resource)) {
                if (input == null)
                    throw new IOException("Missing packaged sound resource: " + resource);
                bytes = input.readAllBytes();
                gameSounds.put(resource, bytes);
            } catch (IOException failure) {
                throw new LuaError(failure);
            }
        }
        var definition = catalog().getAsJsonObject(name);
        return new Clip(bytes, definition.has("subtitle") ? definition.get("subtitle").getAsString() : null);
    }

    private void collect(String event, int weight, List<Choice> choices) {
        for (var value : catalog().getAsJsonObject(eventName(event)).getAsJsonArray("sounds")) {
            String name;
            int entryWeight = weight;
            if (value.isJsonPrimitive()) {
                name = value.getAsString();
            } else {
                var definition = value.getAsJsonObject();
                name = definition.get("name").getAsString();
                if (definition.has("weight"))
                    entryWeight *= definition.get("weight").getAsInt();
                if (definition.has("type") && definition.get("type").getAsString().equals("event")) {
                    collect(name, entryWeight, choices);
                    continue;
                }
            }
            choices.add(new Choice(name, entryWeight));
        }
    }

    public PreviewAudio.Voice play(LuaSound sound, Clip clip) {
        var result = owner == null ? org.luaj.vm2.LuaValue.NONE
                                   : owner.event("ON_PLAY_SOUND", sound.getId(), sound.getPos(), sound.getVolume(),
                                         sound.getPitch(), sound.isLooping(), "PLAYERS", null);
        for (int i = 1; i <= result.narg(); i++) {
            if (result.arg(i).isboolean() && result.arg(i).toboolean())
                return null;
        }
        PreviewAudio.Voice voice = audio.voice(clip.bytes, clip.modelAudio);
        voices.put(sound, voice);
        return voice;
    }

    public void stopSound(String id) {
        for (var entry : List.copyOf(voices.entrySet())) {
            if (id == null || id.equals(entry.getKey().getId())) {
                entry.getKey().stop();
                voices.remove(entry.getKey());
            }
        }
    }

    void update() {
        voices.entrySet().removeIf(entry -> entry.getValue().stopped());
        audio.update();
    }

    @Override
    public void close() {
        stopSound(null);
    }
}
