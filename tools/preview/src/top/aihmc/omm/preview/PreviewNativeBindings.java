package top.aihmc.omm.preview;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/** Adapts native normalized records for the original host-side Molang compiler/runtime. */
final class PreviewNativeBindings {
    private PreviewNativeBindings() {}

    private static JsonElement component(JsonObject value) {
        if (value.has("expression") && !value.get("expression").isJsonNull())
            return value.get("expression").deepCopy();
        if (value.has("program") && !value.get("program").isJsonNull()) {
            var program = value.getAsJsonObject("program");
            if (program.has("source") && !program.get("source").isJsonNull())
                return program.get("source").deepCopy();
            throw new UnsupportedOperationException("Native animation binding needs a readable Molang program source");
        }
        if (value.get("value").isJsonNull())
            throw new UnsupportedOperationException(
                "Native JSON binding cannot represent a nonfinite animation constant");
        return value.get("value").deepCopy();
    }

    private static void readablePrograms(JsonObject record, String... names) {
        for (String name : names) {
            if (record.has(name) && !record.get(name).isJsonNull()) {
                var program = record.getAsJsonObject(name);
                if (!program.has("source") || program.get("source").isJsonNull())
                    throw new UnsupportedOperationException(
                        "Native host binding needs readable Molang source: " + name);
            }
        }
    }

    private static JsonElement expression(JsonObject record, String field) {
        readablePrograms(record, "program");
        if (!record.get(field).isJsonNull())
            return record.get(field).deepCopy();
        if (record.has("program") && !record.get("program").isJsonNull())
            return record.getAsJsonObject("program").get("source").deepCopy();
        return com.google.gson.JsonNull.INSTANCE;
    }

    private static JsonArray vector(JsonArray source) {
        var result = new JsonArray();
        source.forEach(value -> result.add(component(value.getAsJsonObject())));
        return result;
    }

    private static String timeKey(float value) {
        return Float.floatToRawIntBits(value) == 0x80000000 ? "-0.0"
                                                            : new BigDecimal(Float.toString(value)).toPlainString();
    }

    private static String uniqueTimeKey(JsonObject target, float value) {
        String key = timeKey(value);
        // Different source spellings may share one float time. Preserve their stable order.
        while (target.has(key)) key += key.contains(".") ? "0" : ".0";
        return key;
    }

    private static JsonElement channel(JsonObject source) {
        if (source.has("constant"))
            return vector(source.getAsJsonArray("constant"));
        var frames = new JsonObject();
        for (var value : source.getAsJsonArray("keyframes")) {
            var frame = value.getAsJsonObject();
            var result = new JsonObject();
            result.add("pre", vector(frame.getAsJsonArray("pre")));
            result.add("post", vector(frame.getAsJsonArray("post")));
            result.add("lerp_mode", frame.get("interpolation").deepCopy());
            frames.add(uniqueTimeKey(frames, frame.get("time").getAsFloat()), result);
        }
        return frames;
    }

    static List<String> animations(JsonArray files) {
        var output = new ArrayList<String>();
        for (var file : files) {
            var record = file.getAsJsonObject();
            if (record.get("first_person").getAsBoolean())
                continue;
            var root = new JsonObject();
            var clips = new JsonObject();
            for (var item : record.getAsJsonArray("clips")) {
                var clip = item.getAsJsonObject();
                var result = new JsonObject();
                result.add("loop", clip.get("loop").deepCopy());
                if (!clip.get("unbounded").getAsBoolean())
                    result.add("animation_length", clip.get("length").deepCopy());
                result.add("blend_weight", component(clip.getAsJsonObject("blend_weight")));
                var bones = new JsonObject();
                for (var boneValue : clip.getAsJsonArray("bones")) {
                    var bone = boneValue.getAsJsonObject();
                    var channels = new JsonObject();
                    for (String name : List.of("position", "rotation", "scale"))
                        if (bone.has(name) && !bone.get(name).isJsonNull())
                            channels.add(name, channel(bone.getAsJsonObject(name)));
                    bones.add(bone.get("name").getAsString(), channels);
                }
                result.add("bones", bones);
                for (var eventValue : clip.getAsJsonArray("events")) {
                    var event = eventValue.getAsJsonObject();
                    readablePrograms(event, "program");
                    String type = event.get("type").getAsString();
                    String field = type.equals("sound") ? "sound_effects"
                        : type.equals("particle")       ? "particle_effects"
                                                        : "timeline";
                    if (!result.has(field))
                        result.add(field, new JsonObject());
                    var times = result.getAsJsonObject(field);
                    String key = timeKey(event.get("time").getAsFloat());
                    if (!times.has(key))
                        times.add(key, new JsonArray());
                    JsonElement text = event.get("text");
                    if (text.getAsString().isBlank() && event.has("program") && !event.get("program").isJsonNull())
                        text = event.getAsJsonObject("program").get("source");
                    JsonObject params = event.getAsJsonObject("params");
                    times.getAsJsonArray(key).add(params.isEmpty() ? text.deepCopy() : params.deepCopy());
                }
                clips.add(clip.get("name").getAsString(), result);
            }
            root.add("animations", clips);
            output.add(root.toString());
        }
        return output;
    }

    static List<String> controllers(JsonArray files) {
        var output = new ArrayList<String>();
        for (var file : files) {
            var controllers = new JsonObject();
            for (var value : file.getAsJsonObject().getAsJsonArray("controllers")) {
                var controller = value.getAsJsonObject();
                var result = new JsonObject();
                result.add("initial_state", controller.get("initial_state").deepCopy());
                var states = new JsonObject();
                for (var stateValue : controller.getAsJsonArray("states")) {
                    var state = stateValue.getAsJsonObject();
                    readablePrograms(state, "entry_program", "exit_program");
                    var target = new JsonObject();
                    var animations = new JsonArray();
                    for (var reference : state.getAsJsonArray("animations")) {
                        var animation = reference.getAsJsonObject();
                        String name = animation.get("name").getAsString();
                        var weight = expression(animation, "weight");
                        if (weight.isJsonNull())
                            animations.add(name);
                        else {
                            var weighted = new JsonObject();
                            weighted.add(name, weight);
                            animations.add(weighted);
                        }
                    }
                    target.add("animations", animations);
                    var transitions = new JsonArray();
                    for (var transitionValue : state.getAsJsonArray("transitions")) {
                        var transition = transitionValue.getAsJsonObject();
                        var edge = new JsonObject();
                        var condition = expression(transition, "condition");
                        edge.add(transition.get("target").getAsString(),
                            condition.isJsonNull() ? new JsonPrimitive("1") : condition);
                        transitions.add(edge);
                    }
                    target.add("transitions", transitions);
                    for (String field : List.of("on_entry", "on_exit", "blend_via_shortest_path", "sound_effects"))
                        target.add(field, state.get(field).deepCopy());
                    for (String field : List.of("on_entry", "on_exit")) {
                        String program = field.equals("on_entry") ? "entry_program" : "exit_program";
                        if (target.getAsJsonArray(field).isEmpty() && state.has(program)
                            && !state.get(program).isJsonNull()) {
                            var source = new JsonArray();
                            source.add(state.getAsJsonObject(program).get("source").deepCopy());
                            target.add(field, source);
                        }
                    }
                    if (!state.get("blend_curve").isJsonNull()) {
                        var curve = new JsonObject();
                        for (var point : state.getAsJsonArray("blend_curve")) {
                            var pair = point.getAsJsonArray();
                            curve.add(uniqueTimeKey(curve, pair.get(0).getAsFloat()), pair.get(1).deepCopy());
                        }
                        target.add("blend_transition", curve);
                    } else
                        target.add("blend_transition", state.get("blend_transition").deepCopy());
                    states.add(state.get("name").getAsString(), target);
                }
                result.add("states", states);
                controllers.add(controller.get("name").getAsString(), result);
            }
            var root = new JsonObject();
            root.add("animation_controllers", controllers);
            output.add(root.toString());
        }
        return output;
    }
}
