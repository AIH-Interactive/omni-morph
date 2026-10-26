package top.aihmc.omnimorph.model.ysm.animation;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import top.aihmc.omnimorph.molang.MolangEngine;
import top.aihmc.omnimorph.model.ysm.YsmBoneMapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Pattern;

/** Immutable template selection; compiled Molang and mutable playback still belong to each native runtime. */
public final class YsmDefaultAnimationResolver {
    public static final String VERSION = "ysm-default-1";
    public static final List<String> MAIN_ACTIONS = List.of("idle", "walk", "run", "jump", "sneak",
            "sneaking", "swim", "swim_stand", "fly", "elytra_fly", "sit", "ride", "ride_pig", "boat",
            "climb", "climbing", "attacked", "death", "sleep", "riptide", "use_mainhand", "use_offhand",
            "swing_hand", "swing_offhand", "ladder_up", "ladder_stillness", "ladder_down");

    public enum Domain {
        MAIN("main.animation.json", "9114e10f69c50cd2c7a44719aa39c4882a82f8b5098a37bbbf8ba7e5ba4d04f2"),
        ARM("arm.animation.json", "e39935ced020d067b45e581ce11d64f9c576bdfbc8d78d278dd55e7999f483f5");

        private final String file;
        private final String hash;

        Domain(String file, String hash) {
            this.file = file;
            this.hash = hash;
        }
    }

    /** JSON remains immutable in the cache. Never share an owner's bound Molang expressions. */
    public record Definition(String clipName, String json, List<String> missingBones, int mappedBones) {
        public Definition {
            missingBones = List.copyOf(missingBones);
        }
    }

    public record Selection(String templateVersion, String signature, Map<String, Definition> definitions) {
        public Selection {
            definitions = Collections.unmodifiableMap(new LinkedHashMap<>(definitions));
        }

        public Map<String, YsmAnimationClip> compile(MolangEngine engine) throws IOException {
            var result = new LinkedHashMap<String, YsmAnimationClip>();
            for (var entry : definitions.entrySet()) {
                Definition definition = entry.getValue();
                if (definition.mappedBones() == 0) {
                    continue;
                }
                YsmAnimationClip clip = YsmAnimationParser.parse(definition.json(), true, engine, true)
                        .get(definition.clipName());
                if (clip == null) {
                    throw new IOException("DEFAULT_ANIMATION_PARSE_FAILED");
                }
                result.put(entry.getKey(), clip);
            }
            return Collections.unmodifiableMap(result);
        }
    }

    private record Key(String resource, Domain domain, List<String> bones, Map<String, String> aliases) {}
    private static final Pattern VARIABLES = Pattern.compile("\\b(?:v|variable)\\.([A-Za-z_][A-Za-z0-9_.]*)");
    private static final Map<Domain, JsonObject> SOURCES = new LinkedHashMap<>();
    private static final Map<Key, Map<String, Definition>> MAPPED = new LinkedHashMap<>(16, .75f, true);
    private static int sourceReads;
    private static int mappings;

    private YsmDefaultAnimationResolver() {}

    public static synchronized Selection resolve(String resource, Domain domain, Collection<String> targetBones,
                                                   Map<String, String> explicitAliases, Set<String> authoredActions,
                                                   Set<String> disabledActions, boolean enabled) throws IOException {
        if (!enabled) {
            return selection(Map.of());
        }
        var bones = targetBones.stream().distinct().sorted().toList();
        Map<String, String> aliases = Collections.unmodifiableMap(new TreeMap<>(explicitAliases));
        for (String target : aliases.values()) {
            if (!bones.contains(target)) {
                throw new IOException("DEFAULT_ANIMATION_ALIAS_TARGET_MISSING");
            }
        }
        Key key = new Key(resource, domain, bones, aliases);
        Map<String, Definition> definitions = MAPPED.get(key);
        if (definitions == null) {
            definitions = map(source(domain), domain, bones, aliases);
            MAPPED.put(key, definitions);
            mappings++;
            // Bound retained immutable JSON independently of the number of resources inspected over a session.
            if (MAPPED.size() > 128) {
                MAPPED.remove(MAPPED.keySet().iterator().next());
            }
        }
        var selected = new LinkedHashMap<String, Definition>();
        for (var entry : definitions.entrySet()) {
            if (!authoredActions.contains(entry.getKey()) && !disabledActions.contains(entry.getKey())) {
                selected.put(entry.getKey(), entry.getValue());
            }
        }
        return selection(selected);
    }

    private static Selection selection(Map<String, Definition> definitions) {
        var identity = new StringBuilder(VERSION);
        definitions.forEach((name, definition) -> identity.append('\n').append(name).append('\n').append(definition.json()));
        return new Selection(VERSION, hash(identity.toString().getBytes(StandardCharsets.UTF_8)), definitions);
    }

    public static String signature(Selection... selections) {
        var identity = new StringBuilder(VERSION);
        for (Selection selection : selections) {
            identity.append('\n').append(selection.signature());
        }
        return hash(identity.toString().getBytes(StandardCharsets.UTF_8));
    }

    private static JsonObject source(Domain domain) throws IOException {
        JsonObject source = SOURCES.get(domain);
        if (source != null) {
            return source;
        }
        String path = "/assets/omni-morph/default_animation/" + domain.file;
        try (var stream = YsmDefaultAnimationResolver.class.getResourceAsStream(path)) {
            if (stream == null) {
                throw new IOException("DEFAULT_ANIMATION_RESOURCE_MISSING");
            }
            byte[] bytes = stream.readAllBytes();
            if (!domain.hash.equals(hash(bytes))) {
                throw new IOException("DEFAULT_ANIMATION_TEMPLATE_HASH_MISMATCH");
            }
            source = JsonParser.parseString(new String(bytes, StandardCharsets.UTF_8)).getAsJsonObject()
                    .getAsJsonObject("animations");
            SOURCES.put(domain, source);
            sourceReads++;
            return source;
        }
    }

    private static Map<String, Definition> map(JsonObject source, Domain domain, List<String> targetBones,
                                                Map<String, String> aliases) throws IOException {
        var result = new LinkedHashMap<String, Definition>();
        for (String action : source.keySet().stream().sorted().toList()) {
            if (!allowed(domain, action)) {
                continue;
            }
            JsonObject clip = source.getAsJsonObject(action).deepCopy();
            var missing = new ArrayList<String>();
            var mapped = new JsonObject();
            JsonObject channels = clip.getAsJsonObject("bones");
            if (channels != null) {
                for (var entry : channels.entrySet()) {
                    String name = entry.getKey();
                    String target = targetBones.contains(name) ? name : aliases.get(name);
                    if (target == null) {
                        var matches = targetBones.stream().filter(candidate -> YsmBoneMapper.normalize(candidate)
                                .equals(YsmBoneMapper.normalize(name))).toList();
                        target = matches.size() == 1 ? matches.getFirst() : null;
                    }
                    if (target == null) {
                        // This assignment-only helper is consumed by excluded physics clips, not the main whitelist.
                        if (!name.equals("molang")) {
                            missing.add(name);
                        }
                    } else {
                        if (mapped.has(target)) {
                            throw new IOException("DEFAULT_ANIMATION_AMBIGUOUS_BONE_MAPPING");
                        }
                        mapped.add(target, entry.getValue());
                    }
                }
            }
            clip.add("bones", mapped);
            String clipName = "omnimorph.default/" + VERSION + "/" + domain.name().toLowerCase(Locale.ROOT) + "/" + action;
            var clips = new JsonObject();
            clips.add(clipName, privateVariables(clip));
            var root = new JsonObject();
            root.addProperty("format_version", "1.8.0");
            root.add("animations", clips);
            result.put(action, new Definition(clipName, root.toString(), missing, mapped.size()));
        }
        return Collections.unmodifiableMap(result);
    }

    private static boolean allowed(Domain domain, String action) {
        if (domain == Domain.MAIN) {
            return MAIN_ACTIONS.contains(action);
        }
        String semantic = action.split("[:$#]", 2)[0];
        return Set.of("hold_mainhand", "hold_offhand", "use_mainhand", "use_offhand", "swing", "swing_offhand")
                .contains(semantic);
    }

    private static JsonElement privateVariables(JsonElement value) {
        if (value.isJsonObject()) {
            var result = new JsonObject();
            value.getAsJsonObject().entrySet().forEach(entry -> result.add(entry.getKey(), privateVariables(entry.getValue())));
            return result;
        }
        if (value.isJsonArray()) {
            var result = new com.google.gson.JsonArray();
            value.getAsJsonArray().forEach(entry -> result.add(privateVariables(entry)));
            return result;
        }
        if (value.isJsonPrimitive() && value.getAsJsonPrimitive().isString()) {
            return new JsonPrimitive(VARIABLES.matcher(value.getAsString()).replaceAll("variable.omnimorph_default.$1"));
        }
        return value;
    }

    private static String hash(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException failure) {
            throw new IllegalStateException(failure);
        }
    }

    public static synchronized void clear() {
        SOURCES.clear();
        MAPPED.clear();
    }

    public static synchronized int sourceReads() {
        return sourceReads;
    }

    public static synchronized int mappings() {
        return mappings;
    }
}
