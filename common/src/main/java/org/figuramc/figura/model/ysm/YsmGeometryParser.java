package org.figuramc.figura.model.ysm;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.stream.JsonReader;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;

import java.io.StringReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class YsmGeometryParser {
    private YsmGeometryParser() {
    }

    public static YsmGeometry parse(String json) throws java.io.IOException {
        JsonReader reader = new JsonReader(new StringReader(json));
        reader.setLenient(true);
        JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
        JsonArray geometries = array(root, "minecraft:geometry");
        if (geometries.isEmpty() || !geometries.get(0).isJsonObject())
            throw new java.io.IOException("YSM geometry does not contain minecraft:geometry");

        JsonObject geometry = geometries.get(0).getAsJsonObject();
        JsonObject description = obj(geometry, "description");

        YsmGeometry result = new YsmGeometry();
        result.textureWidth = intValue(description, "texture_width", 64);
        result.textureHeight = intValue(description, "texture_height", 64);

        for (JsonElement element : array(geometry, "bones")) {
            if (!element.isJsonObject())
                continue;
            JsonObject boneJson = element.getAsJsonObject();
            float[] pivot = vec3(boneJson, "pivot", 0f, 0f, 0f);
            float[] rotation = vec3(boneJson, "rotation", 0f, 0f, 0f);
            YsmGeometry.Bone bone = new YsmGeometry.Bone(
                    string(boneJson, "name", "unknown"),
                    string(boneJson, "parent", ""),
                    new float[]{-pivot[0], pivot[1], pivot[2]},
                    new float[]{-rotation[0], -rotation[1], rotation[2]}
            );
            bone.visible = bool(boneJson, "visible", bool(boneJson, "default_visible", !bool(boneJson, "hidden", bool(boneJson, "neverRender", false))));
            boolean boneMirror = bool(boneJson, "mirror", false);
            float boneInflate = floatValue(boneJson, "inflate", 0f);
            for (JsonElement cubeElement : array(boneJson, "cubes")) {
                if (!cubeElement.isJsonObject())
                    continue;
                JsonObject cubeJson = cubeElement.getAsJsonObject();
                bone.cubes.add(bakeCube(cubeJson, boneMirror, boneInflate, result.textureWidth, result.textureHeight));
            }
            result.bones.put(bone.name, bone);
        }

        for (YsmGeometry.Bone bone : result.bones.values()) {
            YsmGeometry.Bone parent = result.bones.get(bone.parentName);
            if (parent != null)
                parent.children.add(bone);
            else
                result.roots.add(bone);
        }
        return result;
    }

    private static YsmGeometry.Cube bakeCube(JsonObject cube, boolean boneMirror, float boneInflate, float textureWidth, float textureHeight) {
        float inflate = floatValue(cube, "inflate", boneInflate);
        boolean mirror = bool(cube, "mirror", boneMirror);
        float[] origin = vec3(cube, "origin", 0f, 0f, 0f);
        float[] size = vec3(cube, "size", 0f, 0f, 0f);

        float x = -origin[0] - size[0] - inflate;
        float y = origin[1] - inflate;
        float z = origin[2] - inflate;
        float w = size[0] + inflate * 2f;
        float h = size[1] + inflate * 2f;
        float d = size[2] + inflate * 2f;

        Matrix4f cubeBakeMat = new Matrix4f();
        if (cube.has("rotation") || cube.has("pivot")) {
            float[] pivot = vec3(cube, "pivot", 0f, 0f, 0f);
            float[] rotation = vec3(cube, "rotation", 0f, 0f, 0f);
            cubeBakeMat.translate(-pivot[0] / 16f, pivot[1] / 16f, pivot[2] / 16f);
            cubeBakeMat.rotateZ((float) Math.toRadians(rotation[2]));
            cubeBakeMat.rotateY((float) -Math.toRadians(rotation[1]));
            cubeBakeMat.rotateX((float) -Math.toRadians(rotation[0]));
            cubeBakeMat.translate(pivot[0] / 16f, -pivot[1] / 16f, -pivot[2] / 16f);
        }
        Matrix3f cubeNormalMat = new Matrix3f();
        cubeBakeMat.normal(cubeNormalMat);

        List<YsmGeometry.Quad> quads = new ArrayList<>();
        JsonElement uv = cube.get("uv");
        if (uv == null)
            return new YsmGeometry.Cube(quads);

        if (uv.isJsonObject()) {
            JsonObject object = uv.getAsJsonObject();
            bakeFace(quads, object, "north", "north", mirror, x, y, z, w, h, d, textureWidth, textureHeight, new Vector3f(0, 0, -1), cubeBakeMat, cubeNormalMat);
            bakeFace(quads, object, "south", "south", mirror, x, y, z, w, h, d, textureWidth, textureHeight, new Vector3f(0, 0, 1), cubeBakeMat, cubeNormalMat);
            bakeFace(quads, object, "east", mirror ? "west" : "east", mirror, x, y, z, w, h, d, textureWidth, textureHeight, new Vector3f(1, 0, 0), cubeBakeMat, cubeNormalMat);
            bakeFace(quads, object, "west", mirror ? "east" : "west", mirror, x, y, z, w, h, d, textureWidth, textureHeight, new Vector3f(-1, 0, 0), cubeBakeMat, cubeNormalMat);
            bakeFace(quads, object, "up", "up", mirror, x, y, z, w, h, d, textureWidth, textureHeight, new Vector3f(0, 1, 0), cubeBakeMat, cubeNormalMat);
            bakeFace(quads, object, "down", "down", mirror, x, y, z, w, h, d, textureWidth, textureHeight, new Vector3f(0, -1, 0), cubeBakeMat, cubeNormalMat);
        } else if (uv.isJsonArray() && uv.getAsJsonArray().size() >= 2) {
            float u = uv.getAsJsonArray().get(0).getAsFloat();
            float v = uv.getAsJsonArray().get(1).getAsFloat();
            float dx = (float) Math.floor(size[0]), dy = (float) Math.floor(size[1]), dz = (float) Math.floor(size[2]);
            JsonObject object = new JsonObject();
            object.add("north", uvNode(u + dz, v + dz, dx, dy));
            object.add("south", uvNode(u + dz + dx + dz, v + dz, dx, dy));
            object.add("east", uvNode(u, v + dz, dz, dy));
            object.add("west", uvNode(u + dz + dx, v + dz, dz, dy));
            object.add("up", uvNode(u + dz, v, dx, dz));
            object.add("down", uvNode(u + dz + dx, v + dz, dx, -dz));
            bakeFace(quads, object, "north", "north", mirror, x, y, z, w, h, d, textureWidth, textureHeight, new Vector3f(0, 0, -1), cubeBakeMat, cubeNormalMat);
            bakeFace(quads, object, "south", "south", mirror, x, y, z, w, h, d, textureWidth, textureHeight, new Vector3f(0, 0, 1), cubeBakeMat, cubeNormalMat);
            bakeFace(quads, object, "east", mirror ? "west" : "east", mirror, x, y, z, w, h, d, textureWidth, textureHeight, new Vector3f(1, 0, 0), cubeBakeMat, cubeNormalMat);
            bakeFace(quads, object, "west", mirror ? "east" : "west", mirror, x, y, z, w, h, d, textureWidth, textureHeight, new Vector3f(-1, 0, 0), cubeBakeMat, cubeNormalMat);
            bakeFace(quads, object, "up", "up", mirror, x, y, z, w, h, d, textureWidth, textureHeight, new Vector3f(0, 1, 0), cubeBakeMat, cubeNormalMat);
            bakeFace(quads, object, "down", "down", mirror, x, y, z, w, h, d, textureWidth, textureHeight, new Vector3f(0, -1, 0), cubeBakeMat, cubeNormalMat);
        }
        return new YsmGeometry.Cube(quads);
    }

    private static void bakeFace(List<YsmGeometry.Quad> quads, JsonObject uvObject, String faceType, String uvFaceName, boolean mirror,
                                 float x, float y, float z, float w, float h, float d, float textureWidth, float textureHeight,
                                 Vector3f rawNormal, Matrix4f cubeBakeMat, Matrix3f cubeNormalMat) {
        JsonElement element = uvObject.get(uvFaceName);
        if (element == null || !element.isJsonObject())
            return;

        JsonObject faceObject = element.getAsJsonObject();
        float[] uv = vec2(faceObject, "uv", 0f, 0f);
        float[] uvSize = vec2(faceObject, "uv_size", 0f, 0f);
        float u0 = uv[0] / textureWidth;
        float v0 = uv[1] / textureHeight;
        float u1 = (uv[0] + uvSize[0]) / textureWidth;
        float v1 = (uv[1] + uvSize[1]) / textureHeight;
        if (!mirror) {
            float temp = u0;
            u0 = u1;
            u1 = temp;
        }

        float x1 = x / 16f, x2 = (x + w) / 16f;
        float y1 = y / 16f, y2 = (y + h) / 16f;
        float z1 = z / 16f, z2 = (z + d) / 16f;
        Vector3f p1 = new Vector3f(x1, y1, z1);
        Vector3f p2 = new Vector3f(x1, y1, z2);
        Vector3f p3 = new Vector3f(x1, y2, z1);
        Vector3f p4 = new Vector3f(x1, y2, z2);
        Vector3f p5 = new Vector3f(x2, y1, z1);
        Vector3f p6 = new Vector3f(x2, y1, z2);
        Vector3f p7 = new Vector3f(x2, y2, z1);
        Vector3f p8 = new Vector3f(x2, y2, z2);

        Vector3f[] positions = switch (faceType) {
            case "west" -> new Vector3f[]{p4, p3, p1, p2};
            case "east" -> new Vector3f[]{p7, p8, p6, p5};
            case "north" -> new Vector3f[]{p3, p7, p5, p1};
            case "south" -> new Vector3f[]{p8, p4, p2, p6};
            case "up" -> new Vector3f[]{p4, p8, p7, p3};
            case "down" -> new Vector3f[]{p1, p5, p6, p2};
            default -> null;
        };
        if (positions == null)
            return;

        Vector3f normal = new Vector3f(rawNormal).mul(cubeNormalMat).normalize();
        Vector4f temp = new Vector4f();
        float[] bakedPositions = new float[12];
        for (int i = 0; i < 4; i++) {
            temp.set(positions[i].x(), positions[i].y(), positions[i].z(), 1f).mul(cubeBakeMat);
            int offset = i * 3;
            bakedPositions[offset] = temp.x();
            bakedPositions[offset + 1] = temp.y();
            bakedPositions[offset + 2] = temp.z();
        }
        quads.add(new YsmGeometry.Quad(bakedPositions, new float[]{u0, v0, u1, v0, u1, v1, u0, v1}, new float[]{normal.x(), normal.y(), normal.z()}));
    }

    private static JsonObject uvNode(float u, float v, float w, float h) {
        JsonObject node = new JsonObject();
        JsonArray uv = new JsonArray();
        uv.add(u);
        uv.add(v);
        JsonArray size = new JsonArray();
        size.add(w);
        size.add(h);
        node.add("uv", uv);
        node.add("uv_size", size);
        return node;
    }

    private static JsonObject obj(JsonObject parent, String key) {
        JsonElement element = parent == null ? null : parent.get(key);
        return element != null && element.isJsonObject() ? element.getAsJsonObject() : new JsonObject();
    }

    private static JsonArray array(JsonObject parent, String key) {
        JsonElement element = parent == null ? null : parent.get(key);
        return element != null && element.isJsonArray() ? element.getAsJsonArray() : new JsonArray();
    }

    private static String string(JsonObject parent, String key, String fallback) {
        JsonElement element = parent == null ? null : parent.get(key);
        return element != null && element.isJsonPrimitive() ? element.getAsString() : fallback;
    }

    private static int intValue(JsonObject parent, String key, int fallback) {
        JsonElement element = parent == null ? null : parent.get(key);
        return element != null && element.isJsonPrimitive() ? element.getAsInt() : fallback;
    }

    private static float floatValue(JsonObject parent, String key, float fallback) {
        JsonElement element = parent == null ? null : parent.get(key);
        return element != null && element.isJsonPrimitive() ? element.getAsFloat() : fallback;
    }

    private static boolean bool(JsonObject parent, String key, boolean fallback) {
        JsonElement element = parent == null ? null : parent.get(key);
        return element != null && element.isJsonPrimitive() ? element.getAsBoolean() : fallback;
    }

    private static float[] vec3(JsonObject parent, String key, float x, float y, float z) {
        JsonElement element = parent == null ? null : parent.get(key);
        if (element != null && element.isJsonArray() && element.getAsJsonArray().size() >= 3) {
            JsonArray array = element.getAsJsonArray();
            return new float[]{array.get(0).getAsFloat(), array.get(1).getAsFloat(), array.get(2).getAsFloat()};
        }
        return new float[]{x, y, z};
    }

    private static float[] vec2(JsonObject parent, String key, float x, float y) {
        JsonElement element = parent == null ? null : parent.get(key);
        if (element != null && element.isJsonArray() && element.getAsJsonArray().size() >= 2) {
            JsonArray array = element.getAsJsonArray();
            return new float[]{array.get(0).getAsFloat(), array.get(1).getAsFloat()};
        }
        return new float[]{x, y};
    }
}
