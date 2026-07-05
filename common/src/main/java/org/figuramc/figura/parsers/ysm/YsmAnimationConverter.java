package org.figuramc.figura.parsers.ysm;

import com.google.gson.*;
import org.figuramc.figura.avatar.ysm.YsmAvatarMetadataParser;
import org.figuramc.figura.utils.IOUtils;

import java.nio.file.Path;
import java.util.*;

public final class YsmAnimationConverter {
    private static final Set<String> CHANNELS = Set.of("position", "rotation", "scale");

    private YsmAnimationConverter() {
    }

    public static Result convert(Path avatarPath, JsonObject player, Map<String, String> boneUuids) {
        JsonArray out = new JsonArray();
        Set<String> names = new LinkedHashSet<>();
        JsonObject animationFiles = YsmAvatarMetadataParser.obj(player, "animation");

        for (String fileKey : animationFiles.keySet()) {
            JsonElement pathElement = animationFiles.get(fileKey);
            if (pathElement == null || !pathElement.isJsonPrimitive())
                continue;
            try {
                JsonObject root = JsonParser.parseString(IOUtils.readFile(avatarPath.resolve(pathElement.getAsString()))).getAsJsonObject();
                JsonObject animations = YsmAvatarMetadataParser.obj(root, "animations");
                for (String animationName : animations.keySet()) {
                    JsonElement animationElement = animations.get(animationName);
                    if (!animationElement.isJsonObject())
                        continue;
                    JsonObject converted = convertAnimation(animationName, animationElement.getAsJsonObject(), boneUuids);
                    if (converted != null) {
                        out.add(converted);
                        names.add(animationName);
                    }
                }
            } catch (Exception ignored) {
            }
        }

        return new Result(out, names);
    }

    private static JsonObject convertAnimation(String name, JsonObject source, Map<String, String> boneUuids) {
        JsonObject animators = new JsonObject();
        JsonObject bones = YsmAvatarMetadataParser.obj(source, "bones");
        for (String boneName : bones.keySet()) {
            String uuid = boneUuids.get(boneName);
            if (uuid == null)
                continue;
            JsonObject boneAnimation = bones.getAsJsonObject(boneName);
            JsonArray keyframes = new JsonArray();
            for (String channel : CHANNELS) {
                JsonElement channelData = boneAnimation.get(channel);
                if (channelData != null && !channelData.isJsonNull())
                    convertChannel(channel, channelData, keyframes);
            }
            if (!keyframes.isEmpty()) {
                JsonObject animator = new JsonObject();
                animator.addProperty("name", boneName);
                animator.addProperty("type", "bone");
                animator.add("keyframes", keyframes);
                animators.add(uuid, animator);
            }
        }

        if (animators.isEmpty())
            return null;

        JsonObject animation = new JsonObject();
        animation.addProperty("name", name);
        animation.addProperty("loop", loopMode(source.get("loop")));
        animation.addProperty("override", false);
        animation.addProperty("length", floatValue(source, "animation_length", inferLength(animators)));
        copyDynamicField(source, animation, "anim_time_update");
        copyDynamicField(source, animation, "blend_weight");
        copyDynamicField(source, animation, "start_delay");
        copyDynamicField(source, animation, "loop_delay");
        animation.add("animators", animators);
        return animation;
    }

    private static void convertChannel(String channel, JsonElement source, JsonArray target) {
        if (source.isJsonObject()) {
            List<Map.Entry<String, JsonElement>> entries = new ArrayList<>(source.getAsJsonObject().entrySet());
            entries.sort(Comparator.comparingDouble(entry -> parseTime(entry.getKey())));
            for (Map.Entry<String, JsonElement> entry : entries) {
                JsonObject keyframe = keyframe(channel, parseTime(entry.getKey()), entry.getValue(), interpolation(entry.getValue()));
                if (keyframe != null)
                    target.add(keyframe);
            }
        } else {
            JsonObject keyframe = keyframe(channel, 0f, source, "linear");
            if (keyframe != null)
                target.add(keyframe);
        }
    }

    private static JsonObject keyframe(String channel, float time, JsonElement value, String interpolation) {
        JsonArray dataPoints = dataPoints(channel, value);
        if (dataPoints.isEmpty())
            return null;
        JsonObject keyframe = new JsonObject();
        keyframe.addProperty("channel", channel);
        keyframe.addProperty("interpolation", interpolation);
        keyframe.addProperty("time", time);
        keyframe.add("data_points", dataPoints);
        return keyframe;
    }

    private static JsonArray dataPoints(String channel, JsonElement value) {
        JsonArray points = new JsonArray();
        if (value == null || value.isJsonNull())
            return points;

        if (value.isJsonObject()) {
            JsonObject object = value.getAsJsonObject();
            if (object.has("pre"))
                points.add(dataPoint(channel, object.get("pre")));
            if (object.has("post"))
                points.add(dataPoint(channel, object.get("post")));
            if (points.isEmpty() && object.has("vector"))
                points.add(dataPoint(channel, object.get("vector")));
            return points;
        }

        points.add(dataPoint(channel, value));
        return points;
    }

    private static JsonObject dataPoint(String channel, JsonElement value) {
        JsonObject point = new JsonObject();
        String[] values = vectorValue(value, "scale".equals(channel) ? "1" : "0");
        for (int i = 0; i < values.length; i++)
            values[i] = transform(channel, i, values[i]);
        point.addProperty("x", values[0]);
        point.addProperty("y", values[1]);
        point.addProperty("z", values[2]);
        return point;
    }

    private static String[] vectorValue(JsonElement value, String fallback) {
        if (value != null && value.isJsonArray()) {
            JsonArray array = value.getAsJsonArray();
            return new String[]{
                    array.size() > 0 ? primitiveString(array.get(0), fallback) : fallback,
                    array.size() > 1 ? primitiveString(array.get(1), fallback) : fallback,
                    array.size() > 2 ? primitiveString(array.get(2), fallback) : fallback
            };
        }
        String scalar = primitiveString(value, fallback);
        return new String[]{scalar, scalar, scalar};
    }

    private static String transform(String channel, int axis, String value) {
        if (value == null || value.isBlank()) {
            return value;
        }
        boolean negate = false;
        if ("rotation".equals(channel)) {
            if (axis == 0 || axis == 1) {
                negate = true;
            }
        } else if ("position".equals(channel)) {
            if (axis == 0) {
                negate = true;
            }
        }

        if (negate) {
            try {
                float val = Float.parseFloat(value);
                if (val == 0f) {
                    return "0";
                }
                return Float.toString(-val);
            } catch (NumberFormatException e) {
                return "-(" + value + ")";
            }
        }
        return value;
    }

    private static String primitiveString(JsonElement value, String fallback) {
        if (value == null || value.isJsonNull() || !value.isJsonPrimitive())
            return fallback;
        JsonPrimitive primitive = value.getAsJsonPrimitive();
        if (primitive.isNumber())
            return Float.toString(primitive.getAsFloat());
        return primitive.getAsString();
    }

    private static String interpolation(JsonElement value) {
        if (value != null && value.isJsonObject()) {
            String lerp = YsmAvatarMetadataParser.string(value.getAsJsonObject(), "lerp_mode", "");
            if ("catmullrom".equalsIgnoreCase(lerp))
                return "catmullrom";
            if ("step".equalsIgnoreCase(lerp))
                return "step";
        }
        return "linear";
    }

    private static void copyDynamicField(JsonObject source, JsonObject target, String key) {
        JsonElement value = source.get(key);
        if (value == null || value.isJsonNull() || !value.isJsonPrimitive())
            return;
        JsonPrimitive primitive = value.getAsJsonPrimitive();
        if (primitive.isNumber())
            target.addProperty(key, primitive.getAsFloat());
        else
            target.addProperty(key, primitive.getAsString());
    }

    private static String loopMode(JsonElement loop) {
        if (loop != null && loop.isJsonPrimitive()) {
            JsonPrimitive primitive = loop.getAsJsonPrimitive();
            if (primitive.isBoolean())
                return primitive.getAsBoolean() ? "loop" : "once";
            String value = primitive.getAsString();
            if ("hold_on_last_frame".equals(value))
                return "hold";
            if ("loop".equals(value) || "once".equals(value) || "hold".equals(value))
                return value;
        }
        return "once";
    }

    private static float inferLength(JsonObject animators) {
        float length = 0f;
        for (JsonElement animatorElement : animators.asMap().values()) {
            if (!animatorElement.isJsonObject())
                continue;
            JsonArray keyframes = YsmAvatarMetadataParser.arr(animatorElement.getAsJsonObject(), "keyframes");
            if (keyframes == null)
                continue;
            for (JsonElement keyframeElement : keyframes) {
                if (keyframeElement.isJsonObject())
                    length = Math.max(length, floatValue(keyframeElement.getAsJsonObject(), "time", 0f));
            }
        }
        return length;
    }

    private static float parseTime(String value) {
        try {
            return Float.parseFloat(value);
        } catch (NumberFormatException ignored) {
            return 0f;
        }
    }

    private static float floatValue(JsonObject object, String key, float fallback) {
        JsonElement element = object.get(key);
        return element != null && element.isJsonPrimitive() && element.getAsJsonPrimitive().isNumber() ? element.getAsFloat() : fallback;
    }

    public record Result(JsonArray animations, Set<String> names) {
    }
}
