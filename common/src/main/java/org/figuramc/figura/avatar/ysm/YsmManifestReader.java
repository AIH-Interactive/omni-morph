package org.figuramc.figura.avatar.ysm;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.figuramc.figura.utils.IOUtils;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public final class YsmManifestReader {
    private YsmManifestReader() {
    }

    public static YsmManifest read(Path path) throws java.io.IOException {
        if (YsmAvatarDetector.kind(path) == YsmAvatarKind.NEW)
            return readNew(path);
        if (YsmAvatarDetector.kind(path) == YsmAvatarKind.OLD)
            return readOld(path);
        throw new java.io.IOException("Not a YSM avatar: " + path);
    }

    public static YsmManifest readNew(Path path) throws java.io.IOException {
        JsonObject root = YsmJson.readObject(path.resolve("ysm.json"));
        JsonObject metadata = YsmJson.obj(root, "metadata");
        JsonObject properties = YsmJson.obj(root, "properties");
        JsonObject player = YsmJson.obj(YsmJson.obj(root, "files"), "player");
        JsonObject model = YsmJson.obj(player, "model");

        String name = YsmJson.string(metadata, "name", IOUtils.getFileNameOrEmpty(path));
        String description = YsmJson.string(metadata, "tips", YsmJson.string(metadata, "description", ""));
        String mainModel = YsmJson.string(model, "main", "models/main.json");
        String armModel = YsmJson.string(model, "arm", "");
        List<YsmTextureOption> textures = readNewTextures(player);
        String defaultTexture = YsmJson.string(properties, "default_texture", "");

        return new YsmManifest(YsmAvatarKind.NEW, name, description, readAuthors(metadata), mainModel, armModel, readNewAnimations(path, player), textures, defaultTexture);
    }

    public static YsmManifest readOld(Path path) throws java.io.IOException {
        JsonObject root = YsmJson.readObject(path.resolve("main.json"));
        JsonObject description = new JsonObject();
        JsonArray geometries = root.has("minecraft:geometry") && root.get("minecraft:geometry").isJsonArray() ? root.getAsJsonArray("minecraft:geometry") : null;
        if (geometries != null && !geometries.isEmpty() && geometries.get(0).isJsonObject())
            description = YsmJson.obj(geometries.get(0).getAsJsonObject(), "description");
        JsonObject extra = YsmJson.obj(description, "ysm_extra_info");

        String name = YsmJson.string(extra, "name", IOUtils.getFileNameOrEmpty(path));
        String tips = YsmJson.string(extra, "tips", "");
        String armModel = Files.exists(path.resolve("arm.json")) ? "arm.json" : "";
        List<YsmTextureOption> textures = readOldTextures(path);

        return new YsmManifest(YsmAvatarKind.OLD, name, tips, readAuthors(extra), "main.json", armModel, readOldAnimations(path), textures, "");
    }

    private static List<String> readNewAnimations(Path path, JsonObject player) {
        List<String> result = new ArrayList<>();
        JsonElement element = player.get("animation");
        if (element != null) {
            if (element.isJsonPrimitive()) {
                addExisting(result, path, element.getAsString());
            } else if (element.isJsonArray()) {
                for (JsonElement animationElement : element.getAsJsonArray()) {
                    if (animationElement.isJsonPrimitive()) {
                        addExisting(result, path, animationElement.getAsString());
                    } else if (animationElement.isJsonObject()) {
                        JsonObject object = animationElement.getAsJsonObject();
                        String animationPath = YsmJson.string(object, "path", YsmJson.string(object, "file", ""));
                        if (animationPath.isBlank()) {
                            for (String key : object.keySet()) {
                                JsonElement value = object.get(key);
                                if (value != null && value.isJsonPrimitive()) {
                                    animationPath = value.getAsString();
                                    break;
                                }
                            }
                        }
                        addExisting(result, path, animationPath);
                    }
                }
            } else if (element.isJsonObject()) {
                JsonObject object = element.getAsJsonObject();
                for (String key : object.keySet()) {
                    JsonElement value = object.get(key);
                    if (value != null && value.isJsonPrimitive())
                        addExisting(result, path, value.getAsString());
                }
            }
        }
        addExisting(result, path, "animations/main.animation.json");
        return result;
    }

    private static List<String> readOldAnimations(Path path) {
        List<String> result = new ArrayList<>();
        addExisting(result, path, "main.animation.json");
        return result;
    }

    private static void addExisting(List<String> result, Path root, String animationPath) {
        if (animationPath == null || animationPath.isBlank())
            return;
        String normalized = animationPath.replace('\\', '/');
        if (!result.contains(normalized) && Files.exists(root.resolve(normalized)))
            result.add(normalized);
    }

    private static List<YsmTextureOption> readNewTextures(JsonObject player) {
        List<YsmTextureOption> result = new ArrayList<>();
        JsonElement element = player.get("texture");
        if (element == null || !element.isJsonArray())
            return result;

        for (JsonElement textureElement : element.getAsJsonArray()) {
            if (textureElement.isJsonPrimitive()) {
                String texturePath = textureElement.getAsString();
                result.add(new YsmTextureOption(idFromPath(texturePath), displayFromPath(texturePath), texturePath));
            } else if (textureElement.isJsonObject()) {
                JsonObject object = textureElement.getAsJsonObject();
                String path = YsmJson.string(object, "path", YsmJson.string(object, "uv", ""));
                if (path.isBlank()) {
                    for (String key : object.keySet()) {
                        JsonElement value = object.get(key);
                        if (value != null && value.isJsonPrimitive()) {
                            path = value.getAsString();
                            break;
                        }
                    }
                }
                if (!path.isBlank()) {
                    String id = YsmJson.string(object, "id", idFromPath(path));
                    String name = YsmJson.string(object, "name", displayFromPath(path));
                    result.add(new YsmTextureOption(id, name, path));
                }
            }
        }
        return result;
    }

    private static List<YsmTextureOption> readOldTextures(Path path) {
        List<YsmTextureOption> result = new ArrayList<>();
        List<Path> files = IOUtils.listPaths(path);
        if (files == null)
            return result;
        for (Path file : files) {
            String name = IOUtils.getFileNameOrEmpty(file);
            String lower = name.toLowerCase(java.util.Locale.US);
            if (!Files.isDirectory(file) && lower.endsWith(".png") && !lower.contains("foreground") && !lower.contains("background"))
                result.add(new YsmTextureOption(idFromPath(name), displayFromPath(name), name));
        }
        return result;
    }

    private static String[] readAuthors(JsonObject object) {
        List<String> authors = new ArrayList<>();
        JsonElement element = object.get("authors");
        if (element != null && element.isJsonArray()) {
            for (JsonElement authorElement : element.getAsJsonArray()) {
                if (authorElement.isJsonObject()) {
                    String name = YsmJson.string(authorElement.getAsJsonObject(), "name", "");
                    if (!name.isBlank())
                        authors.add(name);
                } else if (authorElement.isJsonPrimitive()) {
                    String name = authorElement.getAsString();
                    if (!name.isBlank())
                        authors.add(name);
                }
            }
        }
        String author = YsmJson.string(object, "author", "");
        if (authors.isEmpty() && !author.isBlank())
            authors.add(author);
        return authors.toArray(String[]::new);
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
