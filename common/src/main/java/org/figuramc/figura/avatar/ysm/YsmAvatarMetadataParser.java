package org.figuramc.figura.avatar.ysm;

import com.google.gson.*;
import org.figuramc.figura.utils.IOUtils;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public final class YsmAvatarMetadataParser {
    private static final Gson GSON = new GsonBuilder().create();

    private YsmAvatarMetadataParser() {
    }

    public static JsonObject readRoot(Path avatarPath) throws java.io.IOException {
        JsonElement root = JsonParser.parseString(IOUtils.readFile(avatarPath.resolve("ysm.json")));
        return root != null && root.isJsonObject() ? root.getAsJsonObject() : new JsonObject();
    }

    public static YsmAvatarMetadata read(Path avatarPath) throws java.io.IOException {
        return read(readRoot(avatarPath), IOUtils.getFileNameOrEmpty(avatarPath));
    }

    public static YsmAvatarMetadata read(JsonObject root, String fallbackName) {
        JsonObject metadata = obj(root, "metadata");
        String name = string(metadata, "name", fallbackName);
        String description = string(metadata, "tips", string(metadata, "description", ""));
        List<String> authors = new ArrayList<>();
        JsonArray authorsJson = arr(metadata, "authors");
        if (authorsJson != null) {
            for (JsonElement element : authorsJson) {
                if (element.isJsonObject()) {
                    String author = string(element.getAsJsonObject(), "name", "");
                    if (!author.isBlank())
                        authors.add(author);
                } else if (element.isJsonPrimitive()) {
                    String author = element.getAsString();
                    if (!author.isBlank())
                        authors.add(author);
                }
            }
        }
        if (authors.isEmpty()) {
            String author = string(metadata, "author", "");
            if (!author.isBlank())
                authors.add(author);
        }
        return new YsmAvatarMetadata(name, description, authors.toArray(String[]::new), readTextures(root));
    }

    public static List<YsmTextureOption> readTextures(JsonObject root) {
        List<YsmTextureOption> result = new ArrayList<>();
        JsonArray textures = arr(obj(obj(root, "files"), "player"), "texture");
        if (textures == null)
            return result;

        for (JsonElement element : textures) {
            if (element.isJsonPrimitive()) {
                String path = element.getAsString();
                result.add(new YsmTextureOption(idFromPath(path), displayFromPath(path), path));
            } else if (element.isJsonObject()) {
                JsonObject object = element.getAsJsonObject();
                for (String key : object.keySet()) {
                    JsonElement value = object.get(key);
                    if (value != null && value.isJsonPrimitive()) {
                        String path = value.getAsString();
                        String id = "uv".equals(key) ? idFromPath(path) : key;
                        String display = "uv".equals(key) ? displayFromPath(path) : key;
                        result.add(new YsmTextureOption(id, display, path));
                    }
                }
            }
        }
        return result;
    }

    public static String toFiguraAvatarJson(YsmAvatarMetadata metadata) {
        JsonObject avatar = new JsonObject();
        avatar.addProperty("name", metadata.name());
        avatar.addProperty("description", metadata.description());
        avatar.addProperty("version", "1.0");
        JsonArray authors = new JsonArray();
        for (String author : metadata.authors())
            authors.add(author);
        avatar.add("authors", authors);
        return GSON.toJson(avatar);
    }

    public static JsonObject obj(JsonObject parent, String key) {
        if (parent == null)
            return new JsonObject();
        JsonElement element = parent.get(key);
        return element != null && element.isJsonObject() ? element.getAsJsonObject() : new JsonObject();
    }

    public static JsonArray arr(JsonObject parent, String key) {
        if (parent == null)
            return null;
        JsonElement element = parent.get(key);
        return element != null && element.isJsonArray() ? element.getAsJsonArray() : null;
    }

    public static String string(JsonObject parent, String key, String fallback) {
        if (parent == null)
            return fallback;
        JsonElement element = parent.get(key);
        return element != null && element.isJsonPrimitive() ? element.getAsString() : fallback;
    }

    private static String idFromPath(String path) {
        String name = displayFromPath(path);
        int dot = name.lastIndexOf('.');
        return dot > 0 ? name.substring(0, dot) : name;
    }

    private static String displayFromPath(String path) {
        if (path == null || path.isBlank())
            return "texture";
        return Path.of(path.replace('\\', '/')).getFileName().toString();
    }
}
