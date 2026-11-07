package top.aihmc.omm.preview;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import top.aihmc.omnimorph.model.ysm.animation.YsmAnimationChannel;
import top.aihmc.omnimorph.model.ysm.animation.YsmAnimationParser;
import top.aihmc.omnimorph.model.ysm.animation.YsmAnimationSampler;
import top.aihmc.omnimorph.model.ysm.controller.YsmAnimationController;
import top.aihmc.omnimorph.model.ysm.controller.YsmAnimationControllerParser;
import top.aihmc.omnimorph.molang.MolangEngine;

/** OMP YsmAnimationPlayer's constant-zero base-scale defaults, without deleting attachment geometry. */
final class YsmVisibilityDefaults {
    private YsmVisibilityDefaults() {}

    static Set<String> collect(List<String> sources) {
        return collect(sources, null);
    }

    static Set<String> collect(List<String> sources, PreviewMolang host) {
        Set<String> hidden = new HashSet<>();
        var merged = new java.util.LinkedHashMap<String, JsonElement>();
        var documents = new java.util.ArrayList<JsonObject>();
        for (String source : sources) {
            JsonObject root = JsonParser.parseString(source).getAsJsonObject();
            if (root.has("animation_controllers")) {
                documents.add(root);
            }
            if (root.has("animations")) {
                for (var entry : root.getAsJsonObject("animations").entrySet()) {
                    // Ordered putAll matches OMP's resource precedence.
                    merged.put(entry.getKey(), entry.getValue());
                }
            }
        }
        var zeros = new java.util.LinkedHashMap<String, Set<String>>();
        for (var entry : merged.entrySet()) {
            if (!entry.getValue().isJsonObject()) {
                continue;
            }
            Set<String> zeroBones = new HashSet<>();
            JsonObject input = new JsonObject(), animations = new JsonObject(), clip = new JsonObject(),
                       bones = new JsonObject();
            JsonObject original = entry.getValue().getAsJsonObject();
            if (!original.has("bones")) {
                continue;
            }
            for (var bone : original.getAsJsonObject("bones").entrySet()) {
                JsonElement scale = bone.getValue().getAsJsonObject().get("scale");
                // Dynamic scale is never a default-hidden rule in the original player.
                if (scale != null && literalScale(scale)) {
                    JsonObject channels = new JsonObject();
                    channels.add("scale", scale);
                    bones.add(bone.getKey(), channels);
                }
            }
            clip.add("bones", bones);
            animations.add(entry.getKey(), clip);
            input.add("animations", animations);
            var parsed = YsmAnimationParser.parse(input.toString(), MolangEngine.createEmpty()).get(entry.getKey());
            for (var bone : parsed.boneAnimations.values()) {
                if (isZeroScaleChannel(bone.scale)) {
                    zeroBones.add(bone.boneName.toLowerCase(Locale.ROOT));
                }
            }
            if (!zeroBones.isEmpty()) {
                zeros.put(normalizeReference(entry.getKey()), Set.copyOf(zeroBones));
                if (isBaseAnimation(entry.getKey())) {
                    hidden.addAll(zeroBones);
                }
            }
        }
        var controllers = new java.util.LinkedHashMap<String, YsmAnimationController>();
        PreviewMolang compiler = host == null ? new PreviewMolang(new JsonObject(), 0) : host;
        for (JsonObject document : documents) {
            controllers.putAll(YsmAnimationControllerParser.parse(
                initialVisibilityDocument(document, zeros.keySet(), host != null), compiler.engine()));
        }
        for (var controller : controllers.values()) {
            var state = controller.initialStateDefinition();
            if (state == null) {
                continue;
            }
            for (var reference : state.animations()) {
                if (reference.condition() == null || host.evaluator().evalAsBoolean(reference.condition())) {
                    hidden.addAll(zeros.getOrDefault(normalizeReference(reference.animation()), Set.of()));
                }
            }
        }
        return Set.copyOf(hidden);
    }

    private static String normalizeReference(String name) {
        String normalized = name == null ? "" : name;
        if (normalized.startsWith("animation.")) {
            normalized = normalized.substring("animation.".length());
        }
        int namespace = normalized.indexOf(':');
        if (namespace >= 0) {
            normalized = normalized.substring(namespace + 1);
        }
        return normalized.toLowerCase(Locale.ROOT);
    }

    private static String initialVisibilityDocument(JsonObject root, Set<String> candidates, boolean conditional) {
        JsonObject document = root.deepCopy();
        JsonElement definitions = document.get("animation_controllers");
        if (definitions.isJsonObject()) {
            for (var entry : definitions.getAsJsonObject().entrySet()) {
                if (entry.getValue().isJsonObject()) {
                    retainInitialReferences(entry.getValue().getAsJsonObject(), candidates, conditional);
                }
            }
        } else if (definitions.isJsonArray()) {
            for (var definition : definitions.getAsJsonArray()) {
                if (definition.isJsonObject()) {
                    retainInitialReferences(definition.getAsJsonObject(), candidates, conditional);
                }
            }
        }
        return document.toString();
    }

    private static void retainInitialReferences(JsonObject controller, Set<String> candidates, boolean conditional) {
        // This pass examines initial visibility, not transitions or entry/exit execution. Do not compile
        // unreachable runtime expressions before their controller host has been connected.
        JsonObject states = new JsonObject();
        JsonElement original = controller.get("states");
        if (original != null && original.isJsonObject()) {
            for (var entry : original.getAsJsonObject().entrySet()) {
                if (entry.getValue().isJsonObject()) {
                    states.add(entry.getKey(), entry.getValue());
                }
            }
        } else if (original != null && original.isJsonArray()) {
            for (var state : original.getAsJsonArray()) {
                if (!state.isJsonObject()) {
                    continue;
                }
                JsonObject object = state.getAsJsonObject();
                String name = "state_" + states.size();
                for (String field : List.of("name", "id", "state")) {
                    if (object.has(field) && !object.get(field).isJsonNull()) {
                        name = object.get(field).getAsString();
                        break;
                    }
                }
                states.add(name.isBlank() ? "state_" + states.size() : name, object);
            }
        }
        String initial = controller.has("initial_state") ? controller.get("initial_state").getAsString()
            : controller.has("initialState")             ? controller.get("initialState").getAsString()
                                                         : "default";
        if (!states.has(initial) && !states.isEmpty()) {
            initial = states.keySet().iterator().next();
        }
        JsonObject retained = new JsonObject();
        if (states.has(initial)) {
            JsonObject references = new JsonObject();
            JsonObject state = states.getAsJsonObject(initial);
            if (state.has("animations")) {
                references.add("animations", visibilityReferences(state.get("animations"), candidates, conditional));
            }
            retained.add(initial, references);
        }
        controller.addProperty("initial_state", initial);
        controller.add("states", retained);
    }

    private static com.google.gson.JsonArray visibilityReferences(
        JsonElement input, Set<String> candidates, boolean conditional) {
        var retained = new com.google.gson.JsonArray();
        var values = new java.util.ArrayList<JsonElement>();
        if (input.isJsonArray()) {
            input.getAsJsonArray().forEach(values::add);
        } else if (input.isJsonObject()) {
            values.add(input);
        }
        for (JsonElement value : values) {
            if (value.isJsonPrimitive() && candidates.contains(normalizeReference(value.getAsString()))) {
                retained.add(value);
            } else if (value.isJsonObject()) {
                JsonObject selected = new JsonObject();
                for (var reference : value.getAsJsonObject().entrySet()) {
                    JsonElement condition = reference.getValue();
                    boolean unconditional =
                        condition.isJsonNull() || condition.isJsonPrimitive() && condition.getAsString().isBlank();
                    if (candidates.contains(normalizeReference(reference.getKey())) && (conditional || unconditional)) {
                        selected.add(reference.getKey(), reference.getValue());
                    }
                }
                retained.add(selected);
            }
        }
        return retained;
    }

    static boolean[] visibility(PreviewMesh mesh, PreviewMolang host, boolean inherited) {
        var skeleton = mesh.skeleton;
        if (mesh.figuraParts != null) {
            return (inherited ? skeleton.restVisible : skeleton.defaultVisible).clone();
        }
        boolean[] visible = new boolean[skeleton.names.length];
        Set<String> hidden;
        try (var scope = host.beginFrame(0, 0, 0)) {
            hidden = collect(mesh.animationSources, host);
        }
        for (int index : skeleton.order) {
            int parent = skeleton.parents[index];
            visible[index] = !hidden.contains(skeleton.names[index].toLowerCase(Locale.ROOT))
                && (!inherited || parent < 0 || visible[parent]);
        }
        return visible;
    }

    private static boolean literalScale(JsonElement value) {
        if (value.isJsonPrimitive()) {
            if (value.getAsJsonPrimitive().isBoolean()) {
                return false;
            }
            try {
                Float.parseFloat(value.getAsString());
                return true;
            } catch (NumberFormatException dynamic) {
                return false;
            }
        }
        if (value.isJsonArray()) {
            for (JsonElement component : value.getAsJsonArray()) {
                if (!literalScale(component)) {
                    return false;
                }
            }
            return true;
        }
        if (value.isJsonObject()) {
            for (var entry : value.getAsJsonObject().entrySet()) {
                // Editor metadata does not participate in the original parser's scale values.
                if (entry.getKey().equals("lerp_mode") || entry.getKey().equals("easing")
                    || entry.getKey().equals("bezier_linked")) {
                    continue;
                }
                if (!literalScale(entry.getValue())) {
                    return false;
                }
            }
            return true;
        }
        return false;
    }

    // Slot matching and zero-scale sampling follow OMP YsmAnimationPlayer (including pre and post values).
    static boolean isBaseAnimation(String name) {
        String normalized = name.toLowerCase(Locale.ROOT);
        int separator =
            Math.max(Math.max(normalized.lastIndexOf('/'), normalized.lastIndexOf('\\')), normalized.lastIndexOf('.'));
        String simple = normalized.substring(separator + 1);
        return isParallelSlot(simple, "pre_parallel") || isParallelSlot(simple, "parallel");
    }

    private static boolean isParallelSlot(String name, String prefix) {
        if (!name.startsWith(prefix)) {
            return false;
        }
        int index = prefix.length();
        if (index < name.length() && name.charAt(index) == '_') {
            index++;
        }
        return name.length() == index + 1 && name.charAt(index) >= '0' && name.charAt(index) <= '7';
    }

    private static boolean isZeroScaleChannel(YsmAnimationChannel channel) {
        if (channel == null) {
            return false;
        }
        if (channel.keyframes.isEmpty()) {
            return scaleMagnitude(YsmAnimationSampler.sampleStatic(
                       channel.staticValue, channel.staticExpressions, null, new float[] {1, 1, 1}))
                < 0.0001f;
        }
        for (var keyframe : channel.keyframes) {
            if (scaleMagnitude(YsmAnimationSampler.samplePost(keyframe, null, new float[] {1, 1, 1})) >= 0.0001f
                || scaleMagnitude(YsmAnimationSampler.samplePre(keyframe, null, new float[] {1, 1, 1})) >= 0.0001f) {
                return false;
            }
        }
        return true;
    }

    private static float scaleMagnitude(float[] values) {
        float maximum = 0;
        if (values != null) {
            for (float value : values) {
                maximum = Math.max(maximum, Math.abs(value));
            }
        }
        return maximum;
    }
}
