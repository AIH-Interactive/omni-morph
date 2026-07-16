package org.figuramc.figura.avatar.ysm;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.figuramc.figura.utils.IOUtils;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class YsmManifestReader {
    private YsmManifestReader() {
    }

    public static YsmManifest read(Path path) throws java.io.IOException {
        try (YsmPackage ysmPackage = YsmPackage.open(path)) {
            if (YsmAvatarDetector.kind(path) == YsmAvatarKind.NEW)
                return readNew(ysmPackage);
            if (YsmAvatarDetector.kind(path) == YsmAvatarKind.OLD)
                return readOld(ysmPackage);
            throw new java.io.IOException("Not a YSM avatar: " + path);
        }
    }

    public static YsmManifest readNew(Path path) throws java.io.IOException {
        try (YsmPackage ysmPackage = YsmPackage.open(path)) {
            return readNew(ysmPackage);
        }
    }

    private static YsmManifest readNew(YsmPackage ysmPackage) throws java.io.IOException {
        JsonObject root = YsmJson.parseObject(ysmPackage.readString("ysm.json"));
        JsonObject metadata = YsmJson.obj(root, "metadata");
        JsonObject properties = YsmJson.obj(root, "properties");
        JsonObject player = YsmJson.obj(YsmJson.obj(root, "files"), "player");
        JsonObject model = YsmJson.obj(player, "model");

        String name = YsmJson.string(metadata, "name", IOUtils.getFileNameOrEmpty(ysmPackage.root()));
        String description = YsmJson.string(metadata, "tips", YsmJson.string(metadata, "description", ""));
        String mainModel = YsmPackage.normalize(YsmJson.string(model, "main", "models/main.json"));
        String armModel = existingOrBlank(ysmPackage, YsmJson.string(model, "arm", ""));
        List<YsmTextureOption> textures = readNewTextures(player);
        String defaultTexture = YsmJson.string(properties, "default_texture", "");

        List<String> animations = readNewAnimations(ysmPackage, player);
        List<String> controllers = readNewAnimationControllers(ysmPackage, player);
        YsmResourceIndex index = buildIndex(ysmPackage, YsmAvatarKind.NEW, mainModel, armModel, animations, controllers, textures);
        return new YsmManifest(YsmAvatarKind.NEW, name, description, readAuthors(metadata), mainModel, armModel, animations, controllers, textures, defaultTexture, index);
    }

    public static YsmManifest readOld(Path path) throws java.io.IOException {
        try (YsmPackage ysmPackage = YsmPackage.open(path)) {
            return readOld(ysmPackage);
        }
    }

    private static YsmManifest readOld(YsmPackage ysmPackage) throws java.io.IOException {
        JsonObject root = YsmJson.parseObject(ysmPackage.readString("main.json"));
        JsonObject description = new JsonObject();
        JsonArray geometries = root.has("minecraft:geometry") && root.get("minecraft:geometry").isJsonArray() ? root.getAsJsonArray("minecraft:geometry") : null;
        if (geometries != null && !geometries.isEmpty() && geometries.get(0).isJsonObject())
            description = YsmJson.obj(geometries.get(0).getAsJsonObject(), "description");
        JsonObject extra = YsmJson.obj(description, "ysm_extra_info");

        String name = YsmJson.string(extra, "name", IOUtils.getFileNameOrEmpty(ysmPackage.root()));
        String tips = YsmJson.string(extra, "tips", "");
        String armModel = ysmPackage.exists("arm.json") ? "arm.json" : "";
        List<YsmTextureOption> textures = readOldTextures(ysmPackage);
        List<String> animations = readOldAnimations(ysmPackage);
        List<String> controllers = readOldAnimationControllers(ysmPackage);

        YsmResourceIndex index = buildIndex(ysmPackage, YsmAvatarKind.OLD, "main.json", armModel, animations, controllers, textures);
        return new YsmManifest(YsmAvatarKind.OLD, name, tips, readAuthors(extra), "main.json", armModel, animations, controllers, textures, "", index);
    }

    private static List<String> readNewAnimations(YsmPackage ysmPackage, JsonObject player) {
        List<String> result = new ArrayList<>();
        readPathField(result, ysmPackage, player.get("animation"));
        readPathField(result, ysmPackage, player.get("extra_animation"));
        readPathField(result, ysmPackage, player.get("animation_extra"));
        addExisting(result, ysmPackage, "animations/main.animation.json");
        addExisting(result, ysmPackage, "animations/extra.animation.json");
        return result;
    }

    private static List<String> readOldAnimations(YsmPackage ysmPackage) {
        List<String> result = new ArrayList<>();
        addExisting(result, ysmPackage, "main.animation.json");
        addExisting(result, ysmPackage, "extra.animation.json");
        return result;
    }

    private static List<String> readNewAnimationControllers(YsmPackage ysmPackage, JsonObject player) {
        List<String> result = new ArrayList<>();
        readPathField(result, ysmPackage, player.get("animation_controllers"));
        readPathField(result, ysmPackage, player.get("animation_controller"));
        collectDirectoryJson(result, ysmPackage, "controller/");
        collectDirectoryJson(result, ysmPackage, "controllers/");
        return result;
    }

    private static List<String> readOldAnimationControllers(YsmPackage ysmPackage) {
        List<String> result = new ArrayList<>();
        addExisting(result, ysmPackage, "controller.animation.json");
        addExisting(result, ysmPackage, "animation_controllers.json");
        collectDirectoryJson(result, ysmPackage, "controller/");
        collectDirectoryJson(result, ysmPackage, "controllers/");
        return result;
    }

    private static void readPathField(List<String> result, YsmPackage ysmPackage, JsonElement element) {
        if (element == null)
            return;
        if (element.isJsonPrimitive()) {
            addExisting(result, ysmPackage, element.getAsString());
        } else if (element.isJsonArray()) {
            for (JsonElement child : element.getAsJsonArray())
                readPathField(result, ysmPackage, child);
        } else if (element.isJsonObject()) {
            JsonObject object = element.getAsJsonObject();
            String path = YsmJson.string(object, "path", YsmJson.string(object, "file", ""));
            if (!path.isBlank()) {
                addExisting(result, ysmPackage, path);
                return;
            }
            for (String key : object.keySet()) {
                JsonElement value = object.get(key);
                if (value != null && value.isJsonPrimitive())
                    addExisting(result, ysmPackage, value.getAsString());
            }
        }
    }

    private static void addExisting(List<String> result, YsmPackage ysmPackage, String animationPath) {
        if (animationPath == null || animationPath.isBlank())
            return;
        String normalized = YsmPackage.normalize(animationPath);
        if (!result.contains(normalized) && ysmPackage.exists(normalized))
            result.add(normalized);
    }

    private static void collectDirectoryJson(List<String> result, YsmPackage ysmPackage, String directory) {
        String prefix = YsmPackage.normalize(directory);
        for (Path path : ysmPackage.listPaths()) {
            if (Files.isDirectory(path))
                continue;
            String relative = ysmPackage.relativize(path);
            String normalized = YsmPackage.normalize(relative);
            if (normalized.startsWith(prefix) && normalized.toLowerCase(Locale.US).endsWith(".json") && !result.contains(normalized))
                result.add(normalized);
        }
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

    private static List<YsmTextureOption> readOldTextures(YsmPackage ysmPackage) {
        List<YsmTextureOption> result = new ArrayList<>();
        List<Path> files = ysmPackage.listRootPaths();
        if (files == null)
            return result;
        for (Path file : files) {
            String name = IOUtils.getFileNameOrEmpty(file);
            String lower = name.toLowerCase(Locale.US);
            if (!Files.isDirectory(file) && lower.endsWith(".png") && !lower.contains("foreground") && !lower.contains("background"))
                result.add(new YsmTextureOption(idFromPath(name), displayFromPath(name), name));
        }
        return result;
    }

    private static YsmResourceIndex buildIndex(YsmPackage ysmPackage, YsmAvatarKind kind, String mainModel, String armModel,
                                               List<String> animations, List<String> controllers, List<YsmTextureOption> textures) {
        return new YsmResourceIndex(
                ysmPackage.exists(mainModel) ? mainModel : "",
                existingOrBlank(ysmPackage, armModel),
                animations,
                controllers,
                textures == null ? List.of() : textures.stream()
                        .map(YsmTextureOption::path)
                        .map(YsmPackage::normalize)
                        .filter(ysmPackage::exists)
                        .toList(),
                collectByExtension(ysmPackage, ".ogg"),
                firstExisting(ysmPackage, "avatar.png", "icon.png", "textures/avatar.png", "textures/icon.png"),
                firstExisting(ysmPackage, "background.png", "textures/background.png", "gui/background.png"),
                kind == YsmAvatarKind.NEW ? List.of("ysm.json") : List.of("main.json")
        );
    }

    private static List<String> collectByExtension(YsmPackage ysmPackage, String extension) {
        return ysmPackage.listPaths().stream()
                .filter(path -> !Files.isDirectory(path))
                .map(ysmPackage::relativize)
                .filter(path -> path.toLowerCase(Locale.US).endsWith(extension))
                .toList();
    }

    private static String firstExisting(YsmPackage ysmPackage, String... paths) {
        for (String path : paths) {
            if (ysmPackage.exists(path))
                return YsmPackage.normalize(path);
        }
        return "";
    }

    private static String existingOrBlank(YsmPackage ysmPackage, String path) {
        if (path == null || path.isBlank())
            return "";
        String normalized = YsmPackage.normalize(path);
        return ysmPackage.exists(normalized) ? normalized : "";
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
