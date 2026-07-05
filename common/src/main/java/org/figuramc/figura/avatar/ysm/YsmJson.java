package org.figuramc.figura.avatar.ysm;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.stream.JsonReader;
import org.figuramc.figura.utils.IOUtils;

import java.io.StringReader;
import java.nio.file.Path;

final class YsmJson {
    private YsmJson() {
    }

    static JsonObject readObject(Path path) throws java.io.IOException {
        return parseObject(IOUtils.readFile(path));
    }

    static JsonObject parseObject(String json) {
        JsonReader reader = new JsonReader(new StringReader(json));
        reader.setLenient(true);
        JsonElement element = JsonParser.parseReader(reader);
        return element != null && element.isJsonObject() ? element.getAsJsonObject() : new JsonObject();
    }

    static JsonObject obj(JsonObject parent, String key) {
        if (parent == null)
            return new JsonObject();
        JsonElement element = parent.get(key);
        return element != null && element.isJsonObject() ? element.getAsJsonObject() : new JsonObject();
    }

    static String string(JsonObject parent, String key, String fallback) {
        if (parent == null)
            return fallback;
        JsonElement element = parent.get(key);
        return element != null && element.isJsonPrimitive() ? element.getAsString() : fallback;
    }
}
