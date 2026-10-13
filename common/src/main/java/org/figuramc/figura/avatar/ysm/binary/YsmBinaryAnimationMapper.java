package org.figuramc.figura.avatar.ysm.binary;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import org.figuramc.figura.avatar.ysm.crypto.YsmDecodeBudget;
import org.figuramc.figura.avatar.ysm.crypto.YsmDecodeException;

import java.io.IOException;
import java.util.List;
import java.util.Map;

/** Lossless resource adaptation to the shared animation/controller input vocabulary. */
public final class YsmBinaryAnimationMapper {
    private YsmBinaryAnimationMapper() {
    }

    public static JsonObject animations(YsmBinaryModel.RawAnimationFile file,
                                        YsmDecodeBudget budget) throws IOException {
        JsonObject animations = new JsonObject();
        for (var raw : file.animations.values()) {
            budget.check("mapping/animation", 0);
            JsonObject animation = new JsonObject();
            if (Float.isFinite(raw.length)) {
                animation.addProperty("animation_length", raw.length);
            } else {
                animation.addProperty("ysm_duration_unbounded", true);
            }
            switch (raw.loopMode) {
                case 0 -> animation.addProperty("loop", false);
                case 1 -> animation.addProperty("loop", true);
                case 2 -> {
                    // The reference encodes an absent JSON loop property as 2.
                }
                case 3 -> animation.addProperty("loop", "hold_on_last_frame");
                default -> throw invalid("mapping/loop-mode");
            }
            if (raw.blendWeight != null) {
                animation.add("blend_weight", value(raw.blendWeight));
            }
            JsonObject bones = new JsonObject();
            for (var rawBone : raw.boneAnimations) {
                JsonObject bone = new JsonObject();
                channel(bone, "rotation", rawBone.rotation, budget);
                channel(bone, "position", rawBone.position, budget);
                channel(bone, "scale", rawBone.scale, budget);
                unique(bones, rawBone.boneName, bone);
            }
            animation.add("bones", bones);
            JsonObject timeline = new JsonObject();
            for (var event : raw.timelineEvents) {
                JsonArray events = eventsAt(timeline, event.timestamp);
                for (String expression : event.events) {
                    events.add(expression);
                }
            }
            animation.add("timeline", timeline);
            JsonObject sounds = new JsonObject();
            for (var sound : raw.soundEffects) {
                JsonObject effect = new JsonObject();
                effect.addProperty("effect", sound.effectName);
                eventsAt(sounds, sound.timestamp).add(effect);
            }
            animation.add("sound_effects", sounds);
            JsonArray unknownFields = new JsonArray();
            unknownFields.add(raw.unkInt1);
            unknownFields.add(raw.unkInt2);
            unknownFields.add(raw.unkInt4);
            animation.add("ysm_binary_fields", unknownFields);
            unique(animations, raw.name, animation);
        }
        JsonObject result = new JsonObject();
        result.add("animations", animations);
        result.addProperty("ysm_animation_type", file.animType);
        result.addProperty("ysm_resource_hash", file.fileHash);
        return result;
    }

    public static JsonObject controllers(YsmBinaryModel.RawAnimationControllerFile file,
                                         YsmDecodeBudget budget) throws IOException {
        JsonObject controllers = new JsonObject();
        for (var raw : file.controllers.values()) {
            budget.check("mapping/controller", 0);
            JsonObject controller = new JsonObject();
            controller.addProperty("initial_state", raw.initialState);
            JsonObject states = new JsonObject();
            for (var rawState : raw.states) {
                JsonObject state = new JsonObject();
                state.add("animations", object(rawState.animations));
                state.add("transitions", object(rawState.transitions));
                state.add("on_entry", strings(rawState.onEntry));
                state.add("on_exit", strings(rawState.onExit));
                state.add("sound_effects", strings(rawState.soundEffects));
                state.addProperty("blend_via_shortest_path", rawState.blendViaShortestPath);
                if (rawState.blendTransitions.isEmpty()) {
                    state.addProperty("blend_transition", rawState.blendTransitionValue);
                } else {
                    JsonObject curve = new JsonObject();
                    for (var point : rawState.blendTransitions.entrySet()) {
                        unique(curve, Float.toString(point.getKey()), new JsonPrimitive(point.getValue()));
                    }
                    state.add("blend_transition", curve);
                }
                unique(states, rawState.name, state);
            }
            controller.add("states", states);
            unique(controllers, raw.animationName, controller);
        }
        JsonObject result = new JsonObject();
        result.add("animation_controllers", controllers);
        result.addProperty("ysm_resource_hash", file.hash);
        result.addProperty("ysm_legacy_unknown", file.legacyUnknownInt);
        return result;
    }

    private static void channel(JsonObject bone, String name, List<YsmBinaryModel.RawKeyframe> frames,
                                 YsmDecodeBudget budget) throws IOException {
        if (frames.isEmpty()) {
            return;
        }
        JsonObject channel = new JsonObject();
        for (var raw : frames) {
            budget.check("mapping/keyframe", 0);
            JsonObject frame = new JsonObject();
            frame.addProperty("lerp_mode", switch (raw.interpolationMode) {
                case 0 -> "linear";
                case 1 -> "step";
                case 2 -> "catmullrom";
                case 3 -> "bezier";
                default -> throw invalid("mapping/interpolation");
            });
            frame.add("post", vector(raw.postData));
            if (raw.hasPreData) {
                frame.add("pre", vector(raw.preData));
            }
            floats(frame, "bezier_left_value", raw.bezierLeftValue);
            floats(frame, "bezier_right_value", raw.bezierRightValue);
            floats(frame, "bezier_left_time", raw.bezierLeftTime);
            floats(frame, "bezier_right_time", raw.bezierRightTime);
            frame.addProperty("bezier_linked", raw.bezierLinked);
            unique(channel, Float.toString(raw.timestamp), frame);
        }
        bone.add(name, channel);
    }

    private static JsonArray eventsAt(JsonObject events, float time) throws IOException {
        if (!Float.isFinite(time) || time < 0) {
            throw invalid("mapping/event-time");
        }
        String key = Float.toString(time);
        if (!events.has(key)) {
            events.add(key, new JsonArray());
        }
        return events.getAsJsonArray(key);
    }

    private static JsonArray vector(Object[] values) throws IOException {
        if (values == null || values.length != 3) {
            throw invalid("mapping/vector");
        }
        JsonArray result = new JsonArray();
        for (Object item : values) {
            result.add(value(item));
        }
        return result;
    }

    private static JsonElement value(Object value) throws IOException {
        if (value instanceof String text) {
            return new JsonPrimitive(text);
        }
        if (value instanceof Number number && Double.isFinite(number.doubleValue())) {
            return new JsonPrimitive(number);
        }
        throw invalid("mapping/value-type");
    }

    private static void floats(JsonObject target, String name, float[] values) {
        if (values != null) {
            JsonArray array = new JsonArray();
            for (float value : values) {
                array.add(value);
            }
            target.add(name, array);
        }
    }

    private static JsonArray strings(List<String> values) {
        JsonArray array = new JsonArray();
        values.forEach(array::add);
        return array;
    }

    private static JsonObject object(Map<String, String> values) {
        JsonObject result = new JsonObject();
        values.forEach(result::addProperty);
        return result;
    }

    private static void unique(JsonObject target, String name, JsonElement value) throws IOException {
        if (name == null || name.isBlank() || target.has(name)) {
            throw invalid("mapping/duplicate-or-empty-name");
        }
        target.add(name, value);
    }

    private static YsmDecodeException invalid(String stage) {
        return new YsmDecodeException(YsmDecodeException.Code.INVALID_DATA, stage, 0);
    }
}
