package org.figuramc.figura.model.ysm.animation;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.figuramc.figura.avatar.Avatar;
import org.figuramc.figura.molang.parser.ast.Expression;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class YsmAnimationParser {

    private static class ParseResult {
        final float[] value;
        final Expression[] expressions;

        ParseResult(float[] value, Expression[] expressions) {
            this.value = value;
            this.expressions = expressions;
        }
    }

    public static Map<String, YsmAnimationClip> parse(String jsonContent) {
        return parse(jsonContent, true);
    }

    public static Map<String, YsmAnimationClip> parse(String jsonContent, boolean compileExpressions) {
        Map<String, YsmAnimationClip> clips = new HashMap<>();
        try {
            JsonObject root = JsonParser.parseString(jsonContent).getAsJsonObject();
            if (!root.has("animations")) return clips;
            JsonObject animationsObj = root.getAsJsonObject("animations");
            for (String animName : animationsObj.keySet()) {
                JsonElement animElement = animationsObj.get(animName);
                if (!animElement.isJsonObject()) continue;
                JsonObject animObj = animElement.getAsJsonObject();

                float length = animObj.has("animation_length") ? animObj.get("animation_length").getAsFloat() : 0f;
                boolean loop = false;
                if (animObj.has("loop")) {
                    JsonElement loopElem = animObj.get("loop");
                    if (loopElem.isJsonPrimitive()) {
                        if (loopElem.getAsJsonPrimitive().isBoolean()) {
                            loop = loopElem.getAsBoolean();
                        } else {
                            String loopStr = loopElem.getAsString();
                            loop = "true".equalsIgnoreCase(loopStr) || "loop".equalsIgnoreCase(loopStr);
                        }
                    }
                }

                YsmAnimationClip clip = new YsmAnimationClip(animName, length, loop);

                if (animObj.has("bones")) {
                    JsonObject bonesObj = animObj.getAsJsonObject("bones");
                    for (String boneName : bonesObj.keySet()) {
                        JsonElement boneElement = bonesObj.get(boneName);
                        if (!boneElement.isJsonObject()) continue;
                        JsonObject boneObj = boneElement.getAsJsonObject();

                        YsmBoneAnimation boneAnim = new YsmBoneAnimation(boneName);
                        boneAnim.position = parseChannel(boneObj.get("position"), "position", compileExpressions);
                        boneAnim.rotation = parseChannel(boneObj.get("rotation"), "rotation", compileExpressions);
                        boneAnim.scale = parseChannel(boneObj.get("scale"), "scale", compileExpressions);

                        clip.boneAnimations.put(boneName, boneAnim);
                    }
                }

                clips.put(animName, clip);
            }
        } catch (Exception e) {
            // Ignore parse errors, downgrade gracefully
        }
        return clips;
    }

    private static YsmAnimationChannel parseChannel(JsonElement element, String type, boolean compileExpressions) {
        if (element == null) return null;

        if (element.isJsonPrimitive() || element.isJsonArray()) {
            ParseResult res = parseValueOrExpression(element, type, compileExpressions);
            float[] def = "scale".equals(type) ? new float[]{1f, 1f, 1f} : new float[]{0f, 0f, 0f};
            float[] val = res.value != null ? res.value : def;
            return new YsmAnimationChannel(type, val, res.expressions);
        }

        if (element.isJsonObject()) {
            JsonObject obj = element.getAsJsonObject();
            float[] def = "scale".equals(type) ? new float[]{1f, 1f, 1f} : new float[]{0f, 0f, 0f};
            YsmAnimationChannel channel = new YsmAnimationChannel(type, def, null);

            for (String timeStr : obj.keySet()) {
                try {
                    float time = Float.parseFloat(timeStr);
                    JsonElement kfVal = obj.get(timeStr);
                    JsonElement actualVal = kfVal;
                    String interpolation = "linear";
                    if (kfVal.isJsonObject()) {
                        JsonObject kfObj = kfVal.getAsJsonObject();
                        if (kfObj.has("vector")) {
                            actualVal = kfObj.get("vector");
                        } else if (kfObj.has("post")) {
                            actualVal = kfObj.get("post");
                        } else if (kfObj.has("pre")) {
                            actualVal = kfObj.get("pre");
                        }
                        if (kfObj.has("easing")) {
                            interpolation = kfObj.get("easing").getAsString();
                        }
                    }
                    ParseResult res = parseValueOrExpression(actualVal, type, compileExpressions);
                    float[] val = res.value != null ? res.value : def;
                    YsmKeyframe kf = new YsmKeyframe(time, val, res.expressions);
                    kf.interpolation = interpolation;
                    channel.keyframes.add(kf);
                } catch (NumberFormatException ignored) {}
            }
            channel.keyframes.sort((a, b) -> Float.compare(a.time, b.time));
            return channel;
        }

        return null;
    }

    private static ParseResult parseValueOrExpression(JsonElement element, String type, boolean compileExpressions) {
        if (element.isJsonPrimitive()) {
            String str = element.getAsString();
            try {
                float val = Float.parseFloat(str);
                float finalVal = transformConst(type, 0, val);
                return new ParseResult(new float[]{finalVal, finalVal, finalVal}, null);
            } catch (NumberFormatException ignored) {}

            if (!compileExpressions)
                return new ParseResult(new float[]{0f, 0f, 0f}, null);
            Expression expr = compileExpression(transformExpr(type, 0, str));
            return new ParseResult(null, new Expression[]{expr, expr, expr});
        }

        if (element.isJsonArray()) {
            JsonArray array = element.getAsJsonArray();
            float[] value = new float[3];
            Expression[] expressions = new Expression[3];
            boolean hasExpr = false;

            // Default scale is 1, pos and rot is 0
            if ("scale".equals(type)) {
                value[0] = value[1] = value[2] = 1f;
            }

            for (int i = 0; i < Math.min(3, array.size()); i++) {
                JsonElement item = array.get(i);
                if (item.isJsonPrimitive()) {
                    String str = item.getAsString();
                    try {
                        float val = Float.parseFloat(str);
                        value[i] = transformConst(type, i, val);
                    } catch (NumberFormatException ignored) {
                        if (compileExpressions) {
                            expressions[i] = compileExpression(transformExpr(type, i, str));
                            hasExpr = true;
                        }
                    }
                }
            }
            return new ParseResult(value, hasExpr ? expressions : null);
        }

        return new ParseResult(null, null);
    }

    private static float transformConst(String type, int axis, float val) {
        return val;
    }

    private static String transformExpr(String type, int axis, String expr) {
        return expr;
    }

    private static Expression compileExpression(String str) {
        if (str == null || str.isBlank()) return null;
        try {
            List<Expression> parsed = Avatar.getMolangEngine().parse(str);
            return parsed.isEmpty() ? null : parsed.get(0);
        } catch (Exception e) {
            return null;
        }
    }
}
