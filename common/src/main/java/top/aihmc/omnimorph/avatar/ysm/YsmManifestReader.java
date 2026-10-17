package top.aihmc.omnimorph.avatar.ysm;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import top.aihmc.omnimorph.utils.IOUtils;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class YsmManifestReader {
    private YsmManifestReader() {
    }

    /** List-only metadata: no decryption, model parsing, hashing or runtime validation. */
    public record ListMetadata(YsmAvatarMetadata metadata, java.util.Map<String, byte[]> languages) {
    }

    public static ListMetadata readListMetadata(Path path) throws IOException {
        String fallback = IOUtils.getFileNameOrEmpty(path);
        if (YsmEncryptedSource.hasMagic(path)) {
            return new ListMetadata(new YsmAvatarMetadata(fallback, "", new String[0], List.of()), java.util.Map.of());
        }
        try (YsmResourceSource source = YsmPackage.open(path)) {
            JsonObject metadata;
            JsonObject files = new JsonObject();
            List<YsmTextureOption> textures;
            if (source.exists("ysm.json")) {
                JsonObject root = YsmJson.parseObject(source.readString("ysm.json"));
                metadata = YsmJson.obj(root, "metadata");
                files = YsmJson.obj(root, "files");
                textures = readNewTextures(source, YsmJson.obj(YsmJson.obj(root, "files"), "player"), new ArrayList<>());
            } else {
                JsonObject root = YsmJson.parseObject(source.readString("main.json"));
                JsonArray geometries = root.getAsJsonArray("minecraft:geometry");
                JsonObject description = geometries != null && !geometries.isEmpty()
                        ? YsmJson.obj(geometries.get(0).getAsJsonObject(), "description") : new JsonObject();
                metadata = YsmJson.obj(description, "ysm_extra_info");
                textures = readOldTextures(source);
            }
            List<String> languagePaths = collectDirectoryFiles(source, "lang/", "language/");
            List<String> warnings = new ArrayList<>();
            addResourcePaths(languagePaths, source, files.get("language"), warnings, "language");
            addResourcePaths(languagePaths, source, files.get("lang"), warnings, "language");
            var languages = new java.util.LinkedHashMap<String, byte[]>();
            for (String resource : languagePaths) {
                languages.put(resource, source.readBytes(resource));
            }
            return new ListMetadata(new YsmAvatarMetadata(YsmJson.string(metadata, "name", fallback),
                    YsmJson.string(metadata, "tips", YsmJson.string(metadata, "description", "")),
                    readAuthors(metadata), textures), languages);
        }
    }

    public static YsmManifest read(Path path) throws java.io.IOException {
        if (YsmEncryptedSource.hasMagic(path)) {
            try (YsmModelData data = YsmModelData.read(path, () -> false)) {
                return data.manifest();
            }
        }
        try (YsmResourceSource ysmPackage = YsmPackage.open(path)) {
            return read(ysmPackage);
        }
    }

    public static YsmManifest read(YsmResourceSource source) throws IOException {
        source.resourcePaths();
        if (source.exists("ysm.json")) {
            return readNew(source);
        }
        if (source.exists("main.json") && source.readString("main.json").contains("minecraft:geometry")) {
            return readOld(source);
        }
        throw new IOException("UNRECOGNIZED_SOURCE: Not a YSM avatar: " + source.displayName());
    }

    public static YsmManifest readNew(Path path) throws java.io.IOException {
        try (YsmResourceSource ysmPackage = YsmPackage.open(path)) {
            return readNew(ysmPackage);
        }
    }

    private static YsmManifest readNew(YsmResourceSource ysmPackage) throws java.io.IOException {
        JsonObject root = YsmJson.parseObject(ysmPackage.readString("ysm.json"));
        JsonObject metadata = YsmJson.obj(root, "metadata");
        JsonObject properties = YsmJson.obj(root, "properties");
        JsonObject player = YsmJson.obj(YsmJson.obj(root, "files"), "player");
        JsonObject model = YsmJson.obj(player, "model");
        List<String> warnings = new ArrayList<>();

        String name = YsmJson.string(metadata, "name", ysmPackage.displayName());
        String description = YsmJson.string(metadata, "tips", YsmJson.string(metadata, "description", ""));
        String configuredMainModel = YsmJson.string(model, "main", "models/main.json");
        String mainModel = existingOrBlank(ysmPackage, configuredMainModel);
        if (mainModel.isBlank())
            warnings.add("missing required resource: " + displayPath(configuredMainModel));
        String configuredArmModel = YsmJson.string(model, "arm", "");
        String armModel = existingOrBlank(ysmPackage, configuredArmModel);
        if (!configuredArmModel.isBlank() && armModel.isBlank())
            warnings.add("missing optional resource: " + displayPath(configuredArmModel));
        List<YsmTextureOption> textures = readNewTextures(ysmPackage, player, warnings);
        String defaultTexture = YsmJson.string(properties, "default_texture", "");

        List<String> animations = readNewAnimations(ysmPackage, player, warnings);
        List<String> controllers = readNewAnimationControllers(ysmPackage, player, warnings);
        YsmResourceIndex index = buildIndex(ysmPackage, YsmAvatarKind.NEW, mainModel, armModel, animations, controllers, textures, root, warnings);
        return new YsmManifest(YsmAvatarKind.NEW, name, description, readAuthors(metadata), mainModel, armModel, animations, controllers, textures, defaultTexture, index);
    }

    public static YsmManifest readOld(Path path) throws java.io.IOException {
        try (YsmResourceSource ysmPackage = YsmPackage.open(path)) {
            return readOld(ysmPackage);
        }
    }

    private static YsmManifest readOld(YsmResourceSource ysmPackage) throws java.io.IOException {
        JsonObject root = YsmJson.parseObject(ysmPackage.readString("main.json"));
        JsonObject description = new JsonObject();
        JsonArray geometries = root.has("minecraft:geometry") && root.get("minecraft:geometry").isJsonArray() ? root.getAsJsonArray("minecraft:geometry") : null;
        if (geometries != null && !geometries.isEmpty() && geometries.get(0).isJsonObject())
            description = YsmJson.obj(geometries.get(0).getAsJsonObject(), "description");
        JsonObject extra = YsmJson.obj(description, "ysm_extra_info");
        List<String> warnings = new ArrayList<>();

        String name = YsmJson.string(extra, "name", ysmPackage.displayName());
        String tips = YsmJson.string(extra, "tips", "");
        String mainModel = existingOrBlank(ysmPackage, "main.json");
        if (mainModel.isBlank())
            warnings.add("missing required resource: main.json");
        String armModel = ysmPackage.exists("arm.json") ? "arm.json" : "";
        List<YsmTextureOption> textures = readOldTextures(ysmPackage);
        List<String> animations = readOldAnimations(ysmPackage);
        List<String> controllers = readOldAnimationControllers(ysmPackage);

        YsmResourceIndex index = buildIndex(ysmPackage, YsmAvatarKind.OLD, mainModel, armModel, animations, controllers, textures, null, warnings);
        return new YsmManifest(YsmAvatarKind.OLD, name, tips, readAuthors(extra), mainModel, armModel, animations, controllers, textures, "", index);
    }

    private static List<String> readNewAnimations(YsmResourceSource ysmPackage, JsonObject player, List<String> warnings) throws IOException {
        List<String> result = new ArrayList<>();
        readPathField(result, ysmPackage, player.get("animation"), warnings);
        readPathField(result, ysmPackage, player.get("extra_animation"), warnings);
        readPathField(result, ysmPackage, player.get("animation_extra"), warnings);
        addExisting(result, ysmPackage, "animations/main.animation.json");
        addExisting(result, ysmPackage, "animations/extra.animation.json");
        collectAnimationFiles(result, ysmPackage);
        return result;
    }

    private static List<String> readOldAnimations(YsmResourceSource ysmPackage) throws IOException {
        List<String> result = new ArrayList<>();
        addExisting(result, ysmPackage, "main.animation.json");
        addExisting(result, ysmPackage, "arm.animation.json");
        addExisting(result, ysmPackage, "extra.animation.json");
        addExisting(result, ysmPackage, "tac.animation.json");
        addExisting(result, ysmPackage, "carryon.animation.json");
        addExisting(result, ysmPackage, "slashblade.animation.json");
        addExisting(result, ysmPackage, "tlm.animation.json");
        collectAnimationFiles(result, ysmPackage);
        return result;
    }

    private static List<String> readNewAnimationControllers(YsmResourceSource ysmPackage, JsonObject player, List<String> warnings) throws IOException {
        List<String> result = new ArrayList<>();
        readPathField(result, ysmPackage, player.get("animation_controllers"), warnings);
        readPathField(result, ysmPackage, player.get("animation_controller"), warnings);
        collectDirectoryJson(result, ysmPackage, "controller/");
        collectDirectoryJson(result, ysmPackage, "controllers/");
        collectAnimationControllerFiles(result, ysmPackage);
        return result;
    }

    private static List<String> readOldAnimationControllers(YsmResourceSource ysmPackage) throws IOException {
        List<String> result = new ArrayList<>();
        addExisting(result, ysmPackage, "controller.animation.json");
        addExisting(result, ysmPackage, "animation_controllers.json");
        collectDirectoryJson(result, ysmPackage, "controller/");
        collectDirectoryJson(result, ysmPackage, "controllers/");
        collectAnimationControllerFiles(result, ysmPackage);
        return result;
    }

    private static void readPathField(List<String> result, YsmResourceSource ysmPackage, JsonElement element, List<String> warnings) throws IOException {
        if (element == null)
            return;
        if (element.isJsonPrimitive()) {
            addExisting(result, ysmPackage, element.getAsString(), warnings, "optional");
        } else if (element.isJsonArray()) {
            for (JsonElement child : element.getAsJsonArray())
                readPathField(result, ysmPackage, child, warnings);
        } else if (element.isJsonObject()) {
            JsonObject object = element.getAsJsonObject();
            String path = YsmJson.string(object, "path", YsmJson.string(object, "file", ""));
            if (!path.isBlank()) {
                addExisting(result, ysmPackage, path, warnings, "optional");
                return;
            }
            for (String key : object.keySet()) {
                JsonElement value = object.get(key);
                if (value != null && value.isJsonPrimitive())
                    addExisting(result, ysmPackage, value.getAsString(), warnings, "optional");
            }
        }
    }

    private static void addExisting(List<String> result, YsmResourceSource ysmPackage, String animationPath) throws IOException {
        addExisting(result, ysmPackage, animationPath, null, "optional");
    }

    private static void addExisting(List<String> result, YsmResourceSource ysmPackage, String animationPath,
                                    List<String> warnings, String resourceKind) throws IOException {
        if (animationPath == null || animationPath.isBlank())
            return;
        String normalized = YsmPackage.normalize(animationPath);
        if (normalized.isBlank()) {
            addWarning(warnings, "invalid " + resourceKind + " resource path: " + animationPath);
        } else if (ysmPackage.exists(normalized) && !result.contains(normalized)) {
            result.add(normalized);
        } else if (!ysmPackage.exists(normalized)) {
            addWarning(warnings, "missing " + resourceKind + " resource: " + normalized);
        }
    }

    private static void collectDirectoryJson(List<String> result, YsmResourceSource ysmPackage, String directory) throws IOException {
        String prefix = YsmPackage.normalize(directory);
        for (String relative : ysmPackage.resourcePaths()) {
            String normalized = YsmPackage.normalize(relative);
            if (normalized.startsWith(prefix) && normalized.toLowerCase(Locale.US).endsWith(".json") && !result.contains(normalized))
                result.add(normalized);
        }
    }

    private static void collectAnimationFiles(List<String> result, YsmResourceSource ysmPackage) throws IOException {
        for (String relative : ysmPackage.resourcePaths()) {
            String normalized = YsmPackage.normalize(relative);
            if (normalized.toLowerCase(Locale.US).endsWith(".animation.json") && !result.contains(normalized))
                result.add(normalized);
        }
    }

    private static void collectAnimationControllerFiles(List<String> result, YsmResourceSource ysmPackage) throws IOException {
        for (String relative : ysmPackage.resourcePaths()) {
            String normalized = YsmPackage.normalize(relative);
            String lower = normalized.toLowerCase(Locale.US);
            if (!lower.endsWith(".json") || result.contains(normalized))
                continue;
            if (lower.endsWith(".animation.json"))
                continue;
            if (lower.contains("animation_controller") || lower.contains("animation.controllers") || lower.endsWith("controller.animation.json"))
                result.add(normalized);
        }
    }

    private static List<YsmTextureOption> readNewTextures(YsmResourceSource ysmPackage, JsonObject player, List<String> warnings) throws IOException {
        List<YsmTextureOption> result = new ArrayList<>();
        JsonElement element = player.get("texture");
        if (element == null)
            return result;

        if (element.isJsonArray()) {
            for (JsonElement textureElement : element.getAsJsonArray())
                addNewTexture(result, textureElement, ysmPackage, warnings);
        } else {
            addNewTexture(result, element, ysmPackage, warnings);
        }
        return result;
    }

    private static void addNewTexture(List<YsmTextureOption> result, JsonElement textureElement,
                                      YsmResourceSource ysmPackage, List<String> warnings) throws IOException {
        if (textureElement == null || textureElement.isJsonNull())
            return;
        if (textureElement.isJsonPrimitive()) {
            String texturePath = textureElement.getAsString();
            String normalized = YsmPackage.normalize(texturePath);
            result.add(new YsmTextureOption(idFromPath(texturePath), displayFromPath(texturePath), normalized));
            if (normalized.isBlank())
                addWarning(warnings, "invalid optional texture: " + displayPath(texturePath));
            else if (!ysmPackage.exists(normalized))
                addWarning(warnings, "missing optional texture: " + normalized);
            return;
        }
        if (!textureElement.isJsonObject())
            return;
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
            String normalized = YsmPackage.normalize(path);
            result.add(new YsmTextureOption(id, name, normalized,
                    readTextureChannel(ysmPackage, object, "normal", warnings),
                    readTextureChannel(ysmPackage, object, "specular", warnings)));
            if (normalized.isBlank())
                addWarning(warnings, "invalid optional texture: " + displayPath(path));
            else if (!ysmPackage.exists(normalized))
                addWarning(warnings, "missing optional texture: " + normalized);
        }
    }

    private static String readTextureChannel(YsmResourceSource source, JsonObject texture, String key,
                                            List<String> warnings) {
        String configured = YsmJson.string(texture, key, "");
        if (configured.isBlank()) {
            return "";
        }
        String path = YsmPackage.normalize(configured);
        if (path.isBlank() || !source.exists(path)) {
            addWarning(warnings, "missing optional " + key + " texture: " + displayPath(configured));
            return "";
        }
        return path;
    }

    private static List<YsmTextureOption> readOldTextures(YsmResourceSource ysmPackage) throws IOException {
        List<YsmTextureOption> result = new ArrayList<>();
        List<String> files = ysmPackage.resourcePaths();
        if (files == null)
            return result;
        for (String relative : files) {
            if (relative.contains("/"))
                continue;
            String name = IOUtils.getFileNameOrEmpty(Path.of(relative));
            String lower = name.toLowerCase(Locale.US);
            if (lower.endsWith(".png") && !lower.contains("foreground") && !lower.contains("background"))
                result.add(new YsmTextureOption(idFromPath(name), displayFromPath(name), relative));
        }
        return result;
    }

    private static YsmResourceIndex buildIndex(YsmResourceSource ysmPackage, YsmAvatarKind kind, String mainModel, String armModel,
                                               List<String> animations, List<String> controllers, List<YsmTextureOption> textures,
                                               JsonObject manifestRoot, List<String> warnings) throws IOException {
        List<String> languagePaths = collectDirectoryFiles(ysmPackage, "lang/", "language/");
        List<String> projectilePaths = new ArrayList<>();
        List<String> vehiclePaths = new ArrayList<>();
        if (manifestRoot != null) {
            JsonObject files = YsmJson.obj(manifestRoot, "files");
            addResourcePaths(languagePaths, ysmPackage, files.get("language"), warnings, "language");
            addResourcePaths(languagePaths, ysmPackage, files.get("lang"), warnings, "language");
            collectSubEntityResourcePaths(projectilePaths, ysmPackage, files.get("projectiles"), warnings, "projectile");
            collectSubEntityResourcePaths(vehiclePaths, ysmPackage, files.get("vehicles"), warnings, "vehicle");
        }
        collectDirectoryFilesInto(projectilePaths, ysmPackage, "projectile/", "projectiles/");
        collectDirectoryFilesInto(vehiclePaths, ysmPackage, "vehicle/", "vehicles/");
        return new YsmResourceIndex(
                ysmPackage.exists(mainModel) ? mainModel : "",
                existingOrBlank(ysmPackage, armModel),
                animations,
                controllers,
                collectFunctions(ysmPackage),
                textures == null ? List.of() : textures.stream()
                        .flatMap(texture -> java.util.stream.Stream.of(texture.path(), texture.normalPath(), texture.specularPath()))
                        .filter(path -> path != null && !path.isBlank())
                        .map(YsmPackage::normalize)
                        .filter(ysmPackage::exists)
                        .distinct()
                        .toList(),
                collectByExtension(ysmPackage, ".ogg"),
                firstExisting(ysmPackage, "avatar.png", "icon.png", "textures/avatar.png", "textures/icon.png"),
                firstExisting(ysmPackage, "background.png", "textures/background.png", "gui/background.png"),
                kind == YsmAvatarKind.NEW ? List.of("ysm.json") : List.of("main.json"),
                languagePaths,
                projectilePaths,
                vehiclePaths,
                warnings
        );
    }

    private static List<String> collectByExtension(YsmResourceSource ysmPackage, String extension) throws IOException {
        return ysmPackage.resourcePaths().stream()
                .filter(path -> path.toLowerCase(Locale.US).endsWith(extension))
                .toList();
    }

    private static List<String> collectDirectoryFiles(YsmResourceSource ysmPackage, String... directories) throws IOException {
        List<String> result = new ArrayList<>();
        collectDirectoryFilesInto(result, ysmPackage, directories);
        return result;
    }

    private static void collectDirectoryFilesInto(List<String> result, YsmResourceSource ysmPackage, String... directories) throws IOException {
        for (String relative : ysmPackage.resourcePaths()) {
            String normalized = YsmPackage.normalize(relative);
            String lower = normalized.toLowerCase(Locale.US);
            for (String directory : directories) {
                String prefix = YsmPackage.normalize(directory).toLowerCase(Locale.US);
                if (lower.startsWith(prefix) && !result.contains(normalized)) {
                    result.add(normalized);
                    break;
                }
            }
        }
    }

    private static void addResourcePaths(List<String> result, YsmResourceSource ysmPackage, JsonElement element,
                                         List<String> warnings, String resourceKind) throws IOException {
        if (element == null || element.isJsonNull())
            return;
        if (element.isJsonPrimitive()) {
            addExisting(result, ysmPackage, element.getAsString(), warnings, resourceKind);
            return;
        }
        if (element.isJsonArray()) {
            for (JsonElement child : element.getAsJsonArray())
                addResourcePaths(result, ysmPackage, child, warnings, resourceKind);
            return;
        }
        if (!element.isJsonObject())
            return;
        JsonObject object = element.getAsJsonObject();
        String path = YsmJson.string(object, "path", YsmJson.string(object, "file", ""));
        if (!path.isBlank()) {
            addExisting(result, ysmPackage, path, warnings, resourceKind);
            return;
        }
        for (java.util.Map.Entry<String, JsonElement> entry : object.entrySet())
            addResourcePaths(result, ysmPackage, entry.getValue(), warnings, resourceKind);
    }

    private static void collectSubEntityResourcePaths(List<String> result, YsmResourceSource ysmPackage, JsonElement section,
                                                      List<String> warnings, String resourceKind) throws IOException {
        if (section == null || section.isJsonNull())
            return;
        if (section.isJsonArray()) {
            for (JsonElement element : section.getAsJsonArray())
                collectSubEntityResourcePaths(result, ysmPackage, element, warnings, resourceKind);
            return;
        }
        if (section.isJsonObject()) {
            JsonObject object = section.getAsJsonObject();
            String directModel = YsmJson.string(object, "model", YsmJson.string(object, "model_path", ""));
            if (!directModel.isBlank()) {
                addExisting(result, ysmPackage, directModel, warnings, resourceKind + " model");
                addResourcePaths(result, ysmPackage, object.get("texture"), warnings, resourceKind + " texture");
                addResourcePaths(result, ysmPackage, object.get("textures"), warnings, resourceKind + " texture");
                addResourcePaths(result, ysmPackage, object.get("animation"), warnings, resourceKind + " animation");
                addResourcePaths(result, ysmPackage, object.get("animations"), warnings, resourceKind + " animation");
                addResourcePaths(result, ysmPackage, object.get("controller"), warnings, resourceKind + " controller");
                addResourcePaths(result, ysmPackage, object.get("animation_controller"), warnings, resourceKind + " controller");
                addResourcePaths(result, ysmPackage, object.get("controller_path"), warnings, resourceKind + " controller");
                return;
            }
            for (java.util.Map.Entry<String, JsonElement> entry : object.entrySet())
                collectSubEntityResourcePaths(result, ysmPackage, entry.getValue(), warnings, resourceKind);
            return;
        }
        addResourcePaths(result, ysmPackage, section, warnings, resourceKind + " model");
    }

    private static List<String> collectFunctions(YsmResourceSource ysmPackage) throws IOException {
        return ysmPackage.resourcePaths().stream()
                .map(YsmPackage::normalize)
                .filter(path -> path.startsWith("functions/"))
                .filter(path -> path.toLowerCase(Locale.US).endsWith(".molang"))
                .toList();
    }

    private static String firstExisting(YsmResourceSource ysmPackage, String... paths) {
        for (String path : paths) {
            if (ysmPackage.exists(path))
                return YsmPackage.normalize(path);
        }
        return "";
    }

    private static String existingOrBlank(YsmResourceSource ysmPackage, String path) {
        if (path == null || path.isBlank())
            return "";
        String normalized = YsmPackage.normalize(path);
        return ysmPackage.exists(normalized) ? normalized : "";
    }

    private static void addWarning(List<String> warnings, String warning) {
        if (warnings != null && warning != null && !warning.isBlank() && !warnings.contains(warning))
            warnings.add(warning);
    }

    private static String displayPath(String path) {
        return path == null || path.isBlank() ? "<blank>" : path.replace('\\', '/');
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
