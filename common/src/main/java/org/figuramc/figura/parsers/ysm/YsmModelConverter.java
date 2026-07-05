package org.figuramc.figura.parsers.ysm;

import com.google.gson.*;
import org.figuramc.figura.avatar.ysm.YsmAvatarMetadataParser;
import org.figuramc.figura.avatar.ysm.YsmTextureOption;
import org.figuramc.figura.parsers.BlockbenchCommonTypes;
import org.figuramc.figura.utils.IOUtils;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

public final class YsmModelConverter {
    private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().create();
    private static final Base64.Encoder BASE64 = Base64.getEncoder();

    private YsmModelConverter() {
    }

    public static ConvertedModel convert(Path avatarPath, JsonObject ysmRoot, String modelName, YsmTextureOption texture) throws java.io.IOException {
        JsonObject player = YsmAvatarMetadataParser.obj(YsmAvatarMetadataParser.obj(ysmRoot, "files"), "player");
        JsonObject model = YsmAvatarMetadataParser.obj(player, "model");
        String modelPath = YsmAvatarMetadataParser.string(model, "main", "");
        if (modelPath.isBlank())
            throw new java.io.IOException("YSM player main model is missing");

        Path geometryPath = avatarPath.resolve(modelPath);
        JsonObject geometryRoot = JsonParser.parseString(IOUtils.readFile(geometryPath)).getAsJsonObject();
        JsonObject geometry = firstGeometry(geometryRoot);
        JsonObject description = YsmAvatarMetadataParser.obj(geometry, "description");
        int textureWidth = intValue(description, "texture_width", 64);
        int textureHeight = intValue(description, "texture_height", 64);

        JsonArray elements = new JsonArray();
        Map<String, JsonObject> bones = new LinkedHashMap<>();
        Map<String, List<String>> boneChildren = new HashMap<>();
        Map<String, List<String>> boneElements = new HashMap<>();
        Map<String, String> boneUuids = new LinkedHashMap<>();
        Set<String> hiddenBones = collectBaseHiddenBones(avatarPath, player);

        JsonArray boneArray = array(geometry, "bones");
        for (JsonElement element : boneArray) {
            if (!element.isJsonObject())
                continue;
            JsonObject bone = element.getAsJsonObject();
            String name = string(bone, "name", "unknown");
            bones.put(name, bone);
            boneUuids.put(name, uuid());
            String parent = string(bone, "parent", "");
            if (!parent.isBlank())
                boneChildren.computeIfAbsent(parent, __ -> new ArrayList<>()).add(name);
        }

        for (JsonObject bone : bones.values()) {
            String boneName = string(bone, "name", "unknown");
            boolean boneMirror = bool(bone, "mirror", false);
            float boneInflate = floatValue(bone, "inflate", 0f);
            JsonArray cubes = array(bone, "cubes");
            List<String> elementIds = new ArrayList<>();
            for (int i = 0; i < cubes.size(); i++) {
                JsonElement cubeElement = cubes.get(i);
                if (!cubeElement.isJsonObject())
                    continue;
                JsonObject cube = cubeElement.getAsJsonObject();
                float[] origin = vec3(cube, "origin", 0f, 0f, 0f);
                float[] size = vec3(cube, "size", 1f, 1f, 1f);
                String cubeUuid = uuid();
                JsonObject out = new JsonObject();
                out.addProperty("name", boneName + "_cube_" + i);
                out.addProperty("type", "cube");
                out.addProperty("uuid", cubeUuid);
                out.add("from", vec(-(origin[0] + size[0]), origin[1], origin[2]));
                out.add("to", vec(-origin[0], origin[1] + size[1], origin[2] + size[2]));
                out.addProperty("inflate", floatValue(cube, "inflate", boneInflate));
                out.addProperty("visibility", true);
                out.addProperty("export", true);

                if (cube.has("pivot")) {
                    float[] pivot = vec3(cube, "pivot", 0f, 0f, 0f);
                    if (nonZero(pivot))
                        out.add("origin", vec(-pivot[0], pivot[1], pivot[2]));
                }
                if (cube.has("rotation")) {
                    float[] rotation = vec3(cube, "rotation", 0f, 0f, 0f);
                    if (nonZero(rotation))
                        out.add("rotation", vec(-rotation[0], -rotation[1], rotation[2]));
                }

                out.add("faces", cubeFaces(cube, size, bool(cube, "mirror", boneMirror)));
                elements.add(out);
                elementIds.add(cubeUuid);
            }
            boneElements.put(boneName, elementIds);
        }

        JsonArray outliner = new JsonArray();
        for (JsonObject bone : bones.values()) {
            String name = string(bone, "name", "unknown");
            if (string(bone, "parent", "").isBlank())
                outliner.add(buildGroup(name, bones, boneChildren, boneElements, boneUuids, hiddenBones, false));
        }
        addItemPivots(outliner);

        JsonObject bbmodel = new JsonObject();
        JsonObject meta = new JsonObject();
        meta.addProperty("format_version", "4.5");
        meta.addProperty("model_format", "free");
        meta.addProperty("box_uv", false);
        meta.addProperty("predicate_name", modelName);
        bbmodel.add("meta", meta);
        bbmodel.addProperty("name", modelName);
        JsonObject resolution = new JsonObject();
        resolution.addProperty("width", textureWidth);
        resolution.addProperty("height", textureHeight);
        bbmodel.add("resolution", resolution);
        bbmodel.add("elements", elements);
        bbmodel.add("outliner", outliner);
        bbmodel.add("textures", textures(avatarPath, modelName, texture, textureWidth, textureHeight));
        YsmAnimationConverter.Result animations = YsmAnimationConverter.convert(avatarPath, player, boneUuids);
        if (!animations.animations().isEmpty())
            bbmodel.add("animations", animations.animations());
        return new ConvertedModel(modelName, GSON.toJson(bbmodel), animations.names());
    }

    private static JsonObject firstGeometry(JsonObject root) throws java.io.IOException {
        JsonArray geometries = array(root, "minecraft:geometry");
        if (geometries.isEmpty() || !geometries.get(0).isJsonObject())
            throw new java.io.IOException("YSM geometry file does not contain minecraft:geometry");
        return geometries.get(0).getAsJsonObject();
    }

    private static JsonObject buildGroup(String name, Map<String, JsonObject> bones, Map<String, List<String>> boneChildren, Map<String, List<String>> boneElements, Map<String, String> boneUuids, Set<String> hiddenBones, boolean inheritedHidden) {
        JsonObject bone = bones.get(name);
        JsonObject group = new JsonObject();
        boolean hidden = inheritedHidden || hiddenBones.contains(name);
        group.addProperty("name", name);
        group.addProperty("uuid", boneUuids.get(name));
        float[] pivot = vec3(bone, "pivot", 0f, 0f, 0f);
        float[] rotation = vec3(bone, "rotation", 0f, 0f, 0f);
        group.add("origin", vec(-pivot[0], pivot[1], pivot[2]));
        group.add("rotation", vec(-rotation[0], -rotation[1], rotation[2]));
        group.addProperty("visibility", !hidden);
        group.addProperty("export", true);
        String parentType = parentType(name);
        group.addProperty("pt", parentType.isBlank() ? "None" : parentType);

        JsonArray children = new JsonArray();
        for (String elementId : boneElements.getOrDefault(name, List.of()))
            children.add(elementId);
        for (String child : boneChildren.getOrDefault(name, List.of()))
            children.add(buildGroup(child, bones, boneChildren, boneElements, boneUuids, hiddenBones, hidden));
        if (!children.isEmpty())
            group.add("children", children);
        return group;
    }

    private static Set<String> collectBaseHiddenBones(Path avatarPath, JsonObject player) {
        Set<String> hidden = new HashSet<>();
        JsonObject animations = YsmAvatarMetadataParser.obj(player, "animation");
        for (String key : animations.keySet()) {
            JsonElement pathElement = animations.get(key);
            if (pathElement == null || !pathElement.isJsonPrimitive())
                continue;
            try {
                JsonObject animationRoot = JsonParser.parseString(IOUtils.readFile(avatarPath.resolve(pathElement.getAsString()))).getAsJsonObject();
                JsonObject animationMap = YsmAvatarMetadataParser.obj(animationRoot, "animations");
                for (String animationName : animationMap.keySet()) {
                    if (!animationName.startsWith("pre_parallel"))
                        continue;
                    JsonObject bones = YsmAvatarMetadataParser.obj(animationMap.getAsJsonObject(animationName), "bones");
                    for (String boneName : bones.keySet()) {
                        JsonObject boneAnim = bones.getAsJsonObject(boneName);
                        JsonElement scale = boneAnim.get("scale");
                        if (isAlwaysZeroScale(scale))
                            hidden.add(boneName);
                    }
                }
            } catch (Exception ignored) {
            }
        }
        return hidden;
    }

    private static boolean isAlwaysZeroScale(JsonElement scale) {
        if (scale == null || scale.isJsonNull())
            return false;
        if (scale.isJsonPrimitive() && scale.getAsJsonPrimitive().isNumber())
            return Math.abs(scale.getAsFloat()) < 0.0001f;
        if (scale.isJsonArray())
            return isZeroVector(scale.getAsJsonArray());
        if (!scale.isJsonObject())
            return false;

        JsonObject object = scale.getAsJsonObject();
        if (object.isEmpty())
            return false;
        for (JsonElement value : object.asMap().values()) {
            JsonElement target = value;
            if (value.isJsonObject()) {
                JsonObject keyframe = value.getAsJsonObject();
                target = keyframe.has("post") ? keyframe.get("post") : keyframe.get("pre");
            }
            if (target == null || !isZeroScaleValue(target))
                return false;
        }
        return true;
    }

    private static boolean isZeroScaleValue(JsonElement value) {
        if (value == null || value.isJsonNull())
            return false;
        if (value.isJsonPrimitive()) {
            JsonPrimitive primitive = value.getAsJsonPrimitive();
            if (primitive.isNumber())
                return Math.abs(value.getAsFloat()) < 0.0001f;
            if (primitive.isString()) {
                Float evaluated = evaluateDefaultMolang(primitive.getAsString());
                return evaluated != null && Math.abs(evaluated) < 0.0001f;
            }
        }
        return value.isJsonArray() && isZeroVector(value.getAsJsonArray());
    }

    private static boolean isZeroVector(JsonArray array) {
        if (array.isEmpty())
            return false;
        for (JsonElement element : array) {
            if (!isZeroScaleValue(element))
                return false;
        }
        return true;
    }

    private static Float evaluateDefaultMolang(String expression) {
        if (expression == null)
            return null;
        return evaluateDefaultExpression(expression.replace(" ", "").replace(";", ""));
    }

    private static Float evaluateDefaultExpression(String expression) {
        if (expression == null || expression.isBlank())
            return null;
        expression = stripOuterParens(expression);

        int question = findTopLevel(expression, '?');
        if (question >= 0) {
            int colon = findMatchingColon(expression, question + 1);
            if (colon < 0)
                return null;
            Boolean condition = evaluateDefaultCondition(expression.substring(0, question));
            if (condition == null)
                return null;
            return evaluateDefaultExpression(condition ? expression.substring(question + 1, colon) : expression.substring(colon + 1));
        }

        for (String operator : List.of(">=", "<=", "==", "!=", ">", "<")) {
            int index = findTopLevel(expression, operator);
            if (index >= 0) {
                Boolean condition = evaluateDefaultCondition(expression);
                return condition == null ? null : condition ? 1f : 0f;
            }
        }

        for (char operator : new char[]{'+', '-'}) {
            int index = findTopLevelBinary(expression, operator);
            if (index >= 0) {
                Float left = evaluateDefaultExpression(expression.substring(0, index));
                Float right = evaluateDefaultExpression(expression.substring(index + 1));
                if (left == null || right == null)
                    return null;
                return operator == '+' ? left + right : left - right;
            }
        }

        for (char operator : new char[]{'*', '/'}) {
            int index = findTopLevelBinary(expression, operator);
            if (index >= 0) {
                Float left = evaluateDefaultExpression(expression.substring(0, index));
                Float right = evaluateDefaultExpression(expression.substring(index + 1));
                if (left == null || right == null || operator == '/' && Math.abs(right) < 0.0001f)
                    return null;
                return operator == '*' ? left * right : left / right;
            }
        }

        if (expression.startsWith("-")) {
            Float value = evaluateDefaultExpression(expression.substring(1));
            return value == null ? null : -value;
        }
        try {
            return Float.parseFloat(expression);
        } catch (NumberFormatException ignored) {
        }
        return isDefaultZeroVariable(expression) ? 0f : null;
    }

    private static Boolean evaluateDefaultCondition(String expression) {
        expression = stripOuterParens(expression);
        for (String operator : List.of(">=", "<=", "==", "!=", ">", "<")) {
            int index = findTopLevel(expression, operator);
            if (index < 0)
                continue;
            Float left = evaluateDefaultExpression(expression.substring(0, index));
            Float right = evaluateDefaultExpression(expression.substring(index + operator.length()));
            if (left == null || right == null)
                return null;
            return switch (operator) {
                case ">=" -> left >= right;
                case "<=" -> left <= right;
                case "==" -> Math.abs(left - right) < 0.0001f;
                case "!=" -> Math.abs(left - right) >= 0.0001f;
                case ">" -> left > right;
                case "<" -> left < right;
                default -> null;
            };
        }
        Float value = evaluateDefaultExpression(expression);
        return value == null ? null : Math.abs(value) >= 0.0001f;
    }

    private static boolean isDefaultZeroVariable(String expression) {
        String lower = expression.toLowerCase(Locale.ROOT);
        return lower.matches("(v|variable|q|query|ysm)(\\.[a-z_][a-z0-9_]*)+");
    }

    private static String stripOuterParens(String expression) {
        while (expression.length() >= 2 && expression.charAt(0) == '(' && expression.charAt(expression.length() - 1) == ')' && enclosesWholeExpression(expression))
            expression = expression.substring(1, expression.length() - 1);
        return expression;
    }

    private static boolean enclosesWholeExpression(String expression) {
        int depth = 0;
        for (int i = 0; i < expression.length(); i++) {
            char c = expression.charAt(i);
            if (c == '(')
                depth++;
            else if (c == ')' && --depth == 0)
                return i == expression.length() - 1;
        }
        return false;
    }

    private static int findMatchingColon(String expression, int start) {
        int depth = 0;
        for (int i = start; i < expression.length(); i++) {
            char c = expression.charAt(i);
            if (c == '(')
                depth++;
            else if (c == ')')
                depth--;
            else if (c == ':' && depth == 0)
                return i;
        }
        return -1;
    }

    private static int findTopLevelBinary(String expression, char operator) {
        int depth = 0;
        for (int i = expression.length() - 1; i >= 0; i--) {
            char c = expression.charAt(i);
            if (c == ')')
                depth++;
            else if (c == '(')
                depth--;
            else if (c == operator && depth == 0 && i > 0)
                return i;
        }
        return -1;
    }

    private static int findTopLevel(String expression, char needle) {
        int depth = 0;
        for (int i = 0; i < expression.length(); i++) {
            char c = expression.charAt(i);
            if (c == '(')
                depth++;
            else if (c == ')')
                depth--;
            else if (c == needle && depth == 0)
                return i;
        }
        return -1;
    }

    private static int findTopLevel(String expression, String needle) {
        int depth = 0;
        for (int i = 0; i <= expression.length() - needle.length(); i++) {
            char c = expression.charAt(i);
            if (c == '(')
                depth++;
            else if (c == ')')
                depth--;
            if (depth == 0 && expression.startsWith(needle, i))
                return i;
        }
        return -1;
    }

    private static JsonObject cubeFaces(JsonObject cube, float[] size, boolean mirror) {
        JsonObject result = new JsonObject();
        JsonElement uv = cube.get("uv");
        if (uv == null)
            return result;

        if (uv.isJsonObject()) {
            JsonObject object = uv.getAsJsonObject();
            for (String face : List.of("north", "south", "east", "west", "up", "down")) {
                JsonElement faceElement = object.get(face);
                if (faceElement != null && faceElement.isJsonObject()) {
                    JsonObject faceObject = faceElement.getAsJsonObject();
                    float[] pos = vec2(faceObject, "uv", 0f, 0f);
                    float[] uvSize = vec2(faceObject, "uv_size", 0f, 0f);
                    emitFace(result, face, new float[]{pos[0], pos[1], pos[0] + uvSize[0], pos[1] + uvSize[1]}, mirror);
                }
            }
        } else if (uv.isJsonArray() && uv.getAsJsonArray().size() >= 2) {
            float u = uv.getAsJsonArray().get(0).getAsFloat();
            float v = uv.getAsJsonArray().get(1).getAsFloat();
            float dx = size[0], dy = size[1], dz = size[2];
            emitFace(result, "north", new float[]{u + dz, v + dz, u + dz + dx, v + dz + dy}, mirror);
            emitFace(result, "south", new float[]{u + dz + dx + dz, v + dz, u + dz + dx + dz + dx, v + dz + dy}, mirror);
            emitFace(result, "east", new float[]{u, v + dz, u + dz, v + dz + dy}, mirror);
            emitFace(result, "west", new float[]{u + dz + dx, v + dz, u + dz + dx + dz, v + dz + dy}, mirror);
            emitFace(result, "up", new float[]{u + dz, v, u + dz + dx, v + dz}, mirror);
            emitFace(result, "down", new float[]{u + dz + dx, v + dz, u + dz + dx + dx, v}, mirror);
        }
        return result;
    }

    private static void emitFace(JsonObject target, String face, float[] uv, boolean mirror) {
        float[] out = Arrays.copyOf(uv, uv.length);
        if (mirror)
            out = new float[]{out[2], out[1], out[0], out[3]};
        if (face.equals("up") || face.equals("down"))
            out = new float[]{out[2], out[3], out[0], out[1]};
        JsonObject faceObject = new JsonObject();
        faceObject.add("uv", vec(out[0], out[1], out[2], out[3]));
        faceObject.addProperty("texture", 0);
        target.add(face, faceObject);
    }

    private static JsonArray textures(Path avatarPath, String modelName, YsmTextureOption option, int textureWidth, int textureHeight) throws java.io.IOException {
        JsonArray textures = new JsonArray();
        if (option == null || option.path().isBlank())
            return textures;

        Path texturePath = avatarPath.resolve(option.path());
        byte[] bytes = Files.exists(texturePath) ? IOUtils.readFileBytes(texturePath) : new byte[0];
        int width = textureWidth;
        int height = textureHeight;
        if (bytes.length >= 24) {
            try {
                var size = BlockbenchCommonTypes.getPNGDimensions(bytes);
                width = size.x;
                height = size.y;
            } catch (Exception ignored) {
            }
        }

        JsonObject texture = new JsonObject();
        texture.addProperty("name", option.id());
        if (bytes.length > 0)
            texture.addProperty("source", "data:image/png;base64," + BASE64.encodeToString(bytes));
        else
            texture.addProperty("source", "");
        texture.addProperty("width", width);
        texture.addProperty("height", height);
        texture.addProperty("uv_width", textureWidth);
        texture.addProperty("uv_height", textureHeight);
        textures.add(texture);
        return textures;
    }

    private static void addItemPivots(JsonArray outliner) {
        addItemPivot(outliner, "RightHandLocator", "RightItemPivot");
        addItemPivot(outliner, "LeftHandLocator", "LeftItemPivot");
    }

    private static boolean addItemPivot(JsonArray container, String locator, String pivotName) {
        for (JsonElement element : container) {
            if (!element.isJsonObject())
                continue;
            JsonObject group = element.getAsJsonObject();
            if (locator.equals(string(group, "name", ""))) {
                JsonArray children = group.has("children") && group.get("children").isJsonArray() ? group.getAsJsonArray("children") : new JsonArray();
                for (JsonElement child : children) {
                    if (child.isJsonObject() && pivotName.equals(string(child.getAsJsonObject(), "name", "")))
                        return true;
                }
                JsonObject pivot = new JsonObject();
                pivot.addProperty("name", pivotName);
                pivot.addProperty("uuid", uuid());
                pivot.add("origin", group.has("origin") ? group.get("origin").deepCopy() : vec(0f, 0f, 0f));
                pivot.add("rotation", vec(0f, 0f, 0f));
                pivot.addProperty("visibility", true);
                pivot.addProperty("export", true);
                pivot.addProperty("pt", pivotName);
                children.add(pivot);
                group.add("children", children);
                return true;
            }
            if (group.has("children") && group.get("children").isJsonArray() && addItemPivot(group.getAsJsonArray("children"), locator, pivotName))
                return true;
        }
        return false;
    }

    private static String parentType(String name) {
        String normalized = name.toLowerCase(Locale.US).replace(" ", "").replace("_", "");
        if (normalized.equals("allhead") || normalized.equals("mhead") || normalized.equals("head"))
            return "Head";
        if (normalized.contains("leftarm"))
            return "LeftArm";
        if (normalized.contains("rightarm"))
            return "RightArm";
        if (normalized.equals("body") || normalized.equals("upperbody") || normalized.equals("upbody") || normalized.equals("downbody") || normalized.equals("allbody") || normalized.equals("mallbody"))
            return "Body";
        if (normalized.contains("leftleg"))
            return "LeftLeg";
        if (normalized.contains("rightleg"))
            return "RightLeg";
        if (normalized.contains("elytra"))
            return "Elytra";
        if (normalized.contains("cape"))
            return "Cape";
        return "";
    }

    private static JsonArray array(JsonObject object, String key) {
        JsonElement element = object.get(key);
        return element != null && element.isJsonArray() ? element.getAsJsonArray() : new JsonArray();
    }

    private static String string(JsonObject object, String key, String fallback) {
        JsonElement element = object.get(key);
        return element != null && element.isJsonPrimitive() ? element.getAsString() : fallback;
    }

    private static boolean bool(JsonObject object, String key, boolean fallback) {
        JsonElement element = object.get(key);
        return element != null && element.isJsonPrimitive() ? element.getAsBoolean() : fallback;
    }

    private static int intValue(JsonObject object, String key, int fallback) {
        JsonElement element = object.get(key);
        return element != null && element.isJsonPrimitive() ? element.getAsInt() : fallback;
    }

    private static float floatValue(JsonObject object, String key, float fallback) {
        JsonElement element = object.get(key);
        return element != null && element.isJsonPrimitive() ? element.getAsFloat() : fallback;
    }

    private static float[] vec3(JsonObject object, String key, float x, float y, float z) {
        JsonElement element = object.get(key);
        if (element == null || !element.isJsonArray() || element.getAsJsonArray().size() < 3)
            return new float[]{x, y, z};
        JsonArray array = element.getAsJsonArray();
        return new float[]{array.get(0).getAsFloat(), array.get(1).getAsFloat(), array.get(2).getAsFloat()};
    }

    private static float[] vec2(JsonObject object, String key, float x, float y) {
        JsonElement element = object.get(key);
        if (element == null || !element.isJsonArray() || element.getAsJsonArray().size() < 2)
            return new float[]{x, y};
        JsonArray array = element.getAsJsonArray();
        return new float[]{array.get(0).getAsFloat(), array.get(1).getAsFloat()};
    }

    private static JsonArray vec(float... values) {
        JsonArray array = new JsonArray();
        for (float value : values)
            array.add(value);
        return array;
    }

    private static boolean nonZero(float[] values) {
        for (float value : values)
            if (Math.abs(value) > 0.0001f)
                return true;
        return false;
    }

    private static String uuid() {
        return UUID.randomUUID().toString();
    }

    public record ConvertedModel(String name, String bbmodelJson, Set<String> animationNames) {
    }
}
