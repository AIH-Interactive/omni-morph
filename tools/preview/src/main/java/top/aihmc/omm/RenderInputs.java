package top.aihmc.omm;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.nio.file.Files;
import java.nio.file.Path;
import org.joml.Vector3f;
import org.joml.Vector4f;

/** Values normally supplied by Minecraft, read once from the effective configuration. */
record RenderInputs(Vector4f tint, Vector3f light0, Vector3f light1,
                    Vector4f fogColor, float fogStart, float fogEnd,
                    float gameTime, int packedLight, int packedOverlay) {
    static RenderInputs read() throws Exception {
        Path path = Path.of(System.getProperty("omm.preview.output"), "effective-config.json");
        JsonObject root = JsonParser.parseString(Files.readString(path)).getAsJsonObject();
        JsonObject input = root.has("renderInputs") ? root.getAsJsonObject("renderInputs") : new JsonObject();
        float[] tint = vector(input, "tint", 1, 1, 1, 1);
        float[] fog = vector(input, "fogColor", 0, 0, 0, 1);
        for (float value : tint) {
            unit(value, "tint");
        }
        for (float value : fog) {
            unit(value, "fogColor");
        }
        float start = number(input, "fogStart", 10000);
        float end = number(input, "fogEnd", 20000);
        if (start < 0 || end <= start) {
            throw new IllegalArgumentException("renderInputs requires 0 <= fogStart < fogEnd");
        }
        return new RenderInputs(new Vector4f(tint), direction(input, "light0", 0.2f, 1, 0.7f),
            direction(input, "light1", -0.2f, 1, -0.7f), new Vector4f(fog), start, end,
            number(input, "gameTime", 0), integer(input, "packedLight", 0x00f000f0),
            integer(input, "packedOverlay", 0x000a0000));
    }

    private static Vector3f direction(JsonObject input, String name, float... defaults) {
        Vector3f direction = new Vector3f(vector(input, name, defaults));
        if (direction.lengthSquared() == 0) {
            throw new IllegalArgumentException("renderInputs." + name + " must be nonzero");
        }
        return direction.normalize();
    }

    private static float[] vector(JsonObject input, String name, float... defaults) {
        if (!input.has(name)) {
            return defaults;
        }
        var array = input.getAsJsonArray(name);
        if (array.size() != defaults.length) {
            throw new IllegalArgumentException("renderInputs." + name + " requires " + defaults.length + " values");
        }
        float[] result = new float[array.size()];
        for (int index = 0; index < result.length; index++) {
            result[index] = array.get(index).getAsFloat();
            finite(result[index], name);
        }
        return result;
    }

    private static float number(JsonObject input, String name, float defaultValue) {
        float value = input.has(name) ? input.get(name).getAsFloat() : defaultValue;
        finite(value, name);
        return value;
    }

    private static int integer(JsonObject input, String name, int defaultValue) {
        return input.has(name) ? input.get(name).getAsBigDecimal().intValueExact() : defaultValue;
    }

    private static void finite(float value, String name) {
        if (!Float.isFinite(value)) {
            throw new IllegalArgumentException("renderInputs." + name + " must be finite");
        }
    }

    private static void unit(float value, String name) {
        if (value < 0 || value > 1) {
            throw new IllegalArgumentException("renderInputs." + name + " must be in [0, 1]");
        }
    }
}
