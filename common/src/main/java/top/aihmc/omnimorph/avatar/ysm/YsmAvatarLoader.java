package top.aihmc.omnimorph.avatar.ysm;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import top.aihmc.omnimorph.FiguraMod;
import top.aihmc.omnimorph.avatar.UserData;
import top.aihmc.omnimorph.gui.FiguraToast;
import top.aihmc.omnimorph.parsers.AvatarMetadataParser;
import top.aihmc.omnimorph.utils.FiguraText;
import top.aihmc.omnimorph.utils.IOUtils;

import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public final class YsmAvatarLoader {
    private static final float DEFAULT_YSM_SCALE = 0.7f;

    private YsmAvatarLoader() {
    }

    public static CompletableFuture<YsmLoadResult> loadAvatar(Path path, UserData target) {
        long generation = target.ysmLoads.begin();
        var prepared = new top.aihmc.omnimorph.avatar.Avatar(target.id);
        return top.aihmc.omnimorph.avatar.AvatarLoadExecutor.submit(() -> {
            try (YsmModelData data = YsmModelData.read(path, () -> !target.ysmLoads.current(generation))) {
                YsmTextureOption texture = selectTexture(path, data.manifest().textures(), data.manifest().defaultTexture());
                CompoundTag nbt = toAvatarNbt(data, texture, target.id, path);
                var identity = data.identity();
                var diagnostics = data.diagnostics();
                return target.loadYsmAvatar(prepared, nbt, data, generation).thenApply(published -> {
                    if (published) {
                        top.aihmc.omnimorph.backend2.NetworkStuff.localAvatarReady(prepared);
                    }
                    return new YsmLoadResult(published ? YsmLoadResult.Status.PUBLISHED : YsmLoadResult.Status.CANCELLED,
                            identity, diagnostics);
                });
            } catch (Exception failure) {
                net.minecraft.client.Minecraft.getInstance().execute(prepared::clean);
                if (failure instanceof java.io.InterruptedIOException || !target.ysmLoads.current(generation)) {
                    return CompletableFuture.completedFuture(new YsmLoadResult(
                            YsmLoadResult.Status.CANCELLED, null, List.of("CANCELLED")));
                }
                FiguraMod.LOGGER.error("Failed to load YSM avatar from " + path, failure);
                net.minecraft.client.Minecraft.getInstance().execute(() -> {
                    if (target.ysmLoads.current(generation)) {
                        FiguraToast.sendToast(FiguraText.of("toast.load_error"),
                                FiguraText.of("gui.load_error.models"), FiguraToast.ToastType.ERROR);
                    }
                });
                return CompletableFuture.completedFuture(new YsmLoadResult(
                        YsmLoadResult.Status.FAILED, null, List.of("RUNTIME_ASSEMBLY_FAILED")));
            }
        }).thenCompose(result -> result).exceptionally(failure -> {
            net.minecraft.client.Minecraft.getInstance().execute(prepared::clean);
            FiguraMod.LOGGER.error("YSM preparation could not complete for {}", target.id, failure);
            return new YsmLoadResult(YsmLoadResult.Status.FAILED, null, List.of("RUNTIME_ASSEMBLY_FAILED"));
        });
    }

    public static CompoundTag toAvatarNbt(YsmModelData data, YsmTextureOption texture,
                                          java.util.UUID owner, Path localPath) throws java.io.IOException {
        YsmManifest manifest = data.manifest();
        String avatarJson = YsmAvatarMetadataParser.toFiguraAvatarJson(new YsmAvatarMetadata(
                manifest.name(), manifest.description(), manifest.authors(), manifest.textures()));
        AvatarMetadataParser.Metadata figuraMetadata = AvatarMetadataParser.read(avatarJson);
        CompoundTag metadata = AvatarMetadataParser.parse(figuraMetadata, avatarJson, data.resources().displayName());
        metadata.putString("uuid", owner.toString());
        metadata.putString("format", "ysm-native");
        metadata.putString("ysm_kind", manifest.kind().name().toLowerCase(java.util.Locale.US));
        if (texture != null) {
            metadata.putString("ysm_texture", texture.id());
            metadata.putString("ysm_texture_path", texture.path());
        }
        CompoundTag tag = new CompoundTag();
        tag.put("metadata", metadata);
        CompoundTag runtimeTag = data.encrypted() ? YsmSourcePayload.fromData(data, texture == null ? null : texture.id())
                : toRuntimeTag(data, texture);
        if (!data.encrypted() && localPath != null) {
            runtimeTag.putString("source_path", localPath.toAbsolutePath().normalize().toString());
        }
        tag.put("ysm", runtimeTag);
        return data.encrypted() ? YsmSourcePayload.forOutput(tag) : tag;
    }

    public static CompoundTag toRuntimeTag(YsmModelData data, YsmTextureOption texture) throws java.io.IOException {
        YsmManifest manifest = data.manifest();
        YsmResourceIndex index = manifest.resourceIndex();
        YsmResourceSource ysmPackage = data.resources();
        CompoundTag ysm = new CompoundTag();
        ysm.putString("kind", manifest.kind().name());
        ysm.putString("content_sha256", data.identity().contentSha256());
        ysm.putInt("engine_schema_version", data.identity().engineSchemaVersion());
        ysm.putString("container_kind", data.identity().containerKind().name());
        addYsmProperties(ysm, ysmPackage);

        String mainModelPath = index.hasMainModel() ? index.mainModelPath() : manifest.mainModelPath();
        ysm.putString("main_model_path", mainModelPath);
        if (ysmPackage.exists(mainModelPath)) {
            ysm.putByteArray("main_model", ysmPackage.readBytes(mainModelPath));
        }

        if (index.hasArmModel() && ysmPackage.exists(index.armModelPath())) {
            ysm.putString("arm_model_path", index.armModelPath());
            ysm.putByteArray("arm_model", ysmPackage.readString(index.armModelPath()).getBytes(java.nio.charset.StandardCharsets.UTF_8));
        }

        ListTag animations = new ListTag();
        for (String animationPath : index.animationPaths()) {
            if (!ysmPackage.exists(animationPath))
                continue;
            CompoundTag animation = new CompoundTag();
            animation.putString("path", animationPath);
            animation.putByteArray("data", ysmPackage.readString(animationPath).getBytes(java.nio.charset.StandardCharsets.UTF_8));
            animations.add(animation);
        }
        ysm.put("animations", animations);

        ListTag controllers = new ListTag();
        for (String controllerPath : index.animationControllerPaths()) {
            if (!ysmPackage.exists(controllerPath))
                continue;
            CompoundTag controller = new CompoundTag();
            controller.putString("path", controllerPath);
            controller.putByteArray("data", ysmPackage.readString(controllerPath).getBytes(java.nio.charset.StandardCharsets.UTF_8));
            controllers.add(controller);
        }
        ysm.put("animation_controllers", controllers);

        ListTag functions = new ListTag();
        for (String functionPath : index.functionPaths()) {
            if (!ysmPackage.exists(functionPath))
                continue;
            byte[] functionData = ysmPackage.readString(functionPath).getBytes(java.nio.charset.StandardCharsets.UTF_8);
            CompoundTag function = new CompoundTag();
            function.putString("path", functionPath);
            function.putString("name", fileName(functionPath));
            function.putString("hash", sha1(functionData));
            function.putByteArray("data", functionData);
            functions.add(function);
        }
        ysm.put("functions", functions);

        if (texture != null) {
            ysm.putString("texture_id", texture.id());
            if (ysmPackage.exists(texture.path()))
                ysm.putByteArray("texture", ysmPackage.readBytes(texture.path()));
        }
        ysm.put("texture_entries", textureEntries(ysmPackage, manifest.textures()));
        ysm.put("sound_entries", binaryResourceEntries(ysmPackage, index.soundPaths()));
        ysm.put("language_entries", binaryResourceEntries(ysmPackage, index.languagePaths()));
        addGuiResources(ysm, ysmPackage);
        ysm.put("action_schemas", actionSchemas(ysmPackage, index));
        ysm.put("sub_entities", subEntities(data));
        ysm.put("resource_index", toNbt(index));
        return ysm;
    }

    private static YsmTextureOption selectTexture(Path path, List<YsmTextureOption> textures, String defaultTexture) {
        if (textures == null || textures.isEmpty())
            return null;
        String selected = YsmTextureSelectionStore.get(path);
        if (selected != null) {
            for (YsmTextureOption texture : textures)
                if (selected.equals(texture.id()))
                    return texture;
        }
        if (defaultTexture != null && !defaultTexture.isBlank()) {
            for (YsmTextureOption texture : textures)
                if (defaultTexture.equals(texture.id()))
                    return texture;
        }
        return textures.get(0);
    }

    private static void addYsmProperties(CompoundTag ysm, YsmResourceSource ysmPackage) throws java.io.IOException {
        if (!ysmPackage.exists("ysm.json"))
            return;
        JsonObject root = YsmJson.parseObject(ysmPackage.readString("ysm.json"));
        JsonObject properties = YsmJson.obj(root, "properties");
        ysm.putString("preview_animation", YsmJson.string(properties, "preview_animation", ""));
        ysm.putBoolean("gui_no_lighting", Boolean.parseBoolean(
                YsmJson.string(properties, "gui_no_lighting", "false")));
        ysm.putBoolean("disable_preview_rotation", Boolean.parseBoolean(
                YsmJson.string(properties, "disable_preview_rotation", "false")));
        ysm.putBoolean("render_layers_first", Boolean.parseBoolean(
                YsmJson.string(properties, "render_layers_first", "false")));
        ysm.putBoolean("all_cutout", Boolean.parseBoolean(YsmJson.string(properties, "all_cutout", "false")));
        ysm.putBoolean("merge_multiline_expr", Boolean.parseBoolean(
                YsmJson.string(properties, "merge_multiline_expr", "false")));
        putPositiveFloat(ysm, "width_scale", YsmJson.number(properties, "width_scale", DEFAULT_YSM_SCALE));
        putPositiveFloat(ysm, "height_scale", YsmJson.number(properties, "height_scale", DEFAULT_YSM_SCALE));
    }

    private static void addGuiResources(CompoundTag tag, YsmResourceSource source) throws java.io.IOException {
        if (!source.exists("ysm.json")) {
            return;
        }
        JsonObject manifest = YsmJson.parseObject(source.readString("ysm.json"));
        JsonObject properties = YsmJson.obj(manifest, "properties");
        JsonObject images = YsmJson.obj(properties, "ysm_background_images");
        CompoundTag names = new CompoundTag();
        java.util.LinkedHashSet<String> paths = new java.util.LinkedHashSet<>();
        for (var entry : images.entrySet()) {
            if (entry.getValue().isJsonPrimitive()) {
                String path = YsmPackage.normalize(entry.getValue().getAsString());
                if (source.exists(path)) {
                    names.putString(entry.getKey(), path);
                    paths.add(path);
                }
            }
        }
        for (String key : List.of("gui_foreground", "gui_background")) {
            String value = YsmJson.string(properties, key, "");
            String path = names.getStringOr(value, YsmPackage.normalize(value));
            if (!path.isBlank() && source.exists(path)) {
                tag.putString(key, path);
                paths.add(path);
            }
        }
        tag.put("gui_image_names", names);
        tag.put("gui_entries", binaryResourceEntries(source, new ArrayList<>(paths)));
    }

    private static void putPositiveFloat(CompoundTag tag, String key, float value) {
        if (Float.isFinite(value) && value > 0f)
            tag.putFloat(key, value);
    }

    private static CompoundTag toNbt(YsmResourceIndex index) {
        CompoundTag tag = new CompoundTag();
        tag.putString("main_model", index.mainModelPath());
        tag.putString("arm_model", index.armModelPath());
        tag.put("animations", strings(index.animationPaths()));
        tag.put("animation_controllers", strings(index.animationControllerPaths()));
        tag.put("functions", strings(index.functionPaths()));
        tag.put("textures", strings(index.texturePaths()));
        tag.put("sounds", strings(index.soundPaths()));
        tag.putString("icon", index.iconPath());
        tag.putString("background", index.backgroundPath());
        tag.put("metadata", strings(index.metadataPaths()));
        tag.put("languages", strings(index.languagePaths()));
        tag.put("projectiles", strings(index.projectilePaths()));
        tag.put("vehicles", strings(index.vehiclePaths()));
        tag.put("warnings", strings(index.warnings()));
        return tag;
    }

    private static ListTag strings(List<String> values) {
        ListTag tag = new ListTag();
        for (String value : values)
            tag.add(StringTag.valueOf(value));
        return tag;
    }

    private static String fileName(String path) {
        if (path == null || path.isBlank())
            return "";
        String normalized = path.replace('\\', '/');
        int slash = normalized.lastIndexOf('/');
        return slash >= 0 ? normalized.substring(slash + 1) : normalized;
    }

    private static String sha1(byte[] data) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-1").digest(data == null ? new byte[0] : data);
            StringBuilder builder = new StringBuilder(digest.length * 2);
            for (byte value : digest)
                builder.append(String.format("%02x", value & 0xff));
            return builder.toString();
        } catch (NoSuchAlgorithmException e) {
            return "";
        }
    }

    private static ListTag textureEntries(YsmResourceSource ysmPackage, List<YsmTextureOption> textures) throws java.io.IOException {
        ListTag tag = new ListTag();
        if (textures == null)
            return tag;
        for (YsmTextureOption texture : textures) {
            if (texture == null || texture.path() == null || texture.path().isBlank() || !ysmPackage.exists(texture.path()))
                continue;
            CompoundTag entry = new CompoundTag();
            entry.putString("id", texture.id());
            entry.putString("path", texture.path());
            entry.putByteArray("data", ysmPackage.readBytes(texture.path()));
            if (!texture.normalPath().isBlank()) {
                entry.putString("normal_path", texture.normalPath());
                entry.putByteArray("normal_data", ysmPackage.readBytes(texture.normalPath()));
            }
            if (!texture.specularPath().isBlank()) {
                entry.putString("specular_path", texture.specularPath());
                entry.putByteArray("specular_data", ysmPackage.readBytes(texture.specularPath()));
            }
            tag.add(entry);
        }
        return tag;
    }

    private static ListTag binaryResourceEntries(YsmResourceSource source, List<String> paths)
            throws java.io.IOException {
        ListTag entries = new ListTag();
        for (String path : paths) {
            if (!source.exists(path)) {
                continue;
            }
            CompoundTag entry = new CompoundTag();
            entry.putString("path", path);
            entry.putByteArray("data", source.readBytes(path));
            entries.add(entry);
        }
        return entries;
    }

    private static ListTag actionSchemas(YsmResourceSource ysmPackage, YsmResourceIndex index) throws java.io.IOException {
        ListTag tag = new ListTag();
        java.util.LinkedHashSet<String> paths = new java.util.LinkedHashSet<>();
        paths.addAll(List.of("ysm.json", "ysm.actions.json", "action_wheel.json", "ysm.controls.json", "ysm_controls.json", "controls.json"));
        if (index != null)
            paths.addAll(index.metadataPaths());
        for (String path : paths)
            addActionSchema(tag, ysmPackage, path);
        return tag;
    }

    private static void addActionSchema(ListTag tag, YsmResourceSource ysmPackage, String path) throws java.io.IOException {
        if (path == null || path.isBlank() || !ysmPackage.exists(path))
            return;
        CompoundTag entry = new CompoundTag();
        entry.putString("path", path);
        entry.putByteArray("data", ysmPackage.readString(path).getBytes(java.nio.charset.StandardCharsets.UTF_8));
        tag.add(entry);
    }

    private static ListTag subEntities(YsmModelData data) throws java.io.IOException {
        YsmResourceSource ysmPackage = data.resources();
        ListTag tag = new ListTag();
        if (ysmPackage.exists("ysm.json")) {
            JsonObject root = YsmJson.parseObject(ysmPackage.readString("ysm.json"));
            JsonObject files = YsmJson.obj(root, "files");
            addSubEntitySection(tag, data, "projectile", files.get("projectiles"));
            addSubEntitySection(tag, data, "vehicle", files.get("vehicles"));
        }
        addLegacySubEntity(tag, ysmPackage, "projectile", "arrow", "arrow.json", "arrow.png", "arrow.animation.json");
        return tag;
    }

    private static void addSubEntitySection(ListTag tag, YsmModelData data, String kind, JsonElement section) throws java.io.IOException {
        if (section == null || section.isJsonNull())
            return;
        if (section.isJsonObject()) {
            for (var entry : section.getAsJsonObject().entrySet())
                addSubEntity(tag, data, kind, entry.getKey(), entry.getValue());
        } else if (section.isJsonArray()) {
            int index = 0;
            for (JsonElement element : section.getAsJsonArray())
                addSubEntity(tag, data, kind, kind + index++, element);
        }
    }

    private static void addSubEntity(ListTag tag, YsmModelData data, String kind, String fallbackId, JsonElement element) throws java.io.IOException {
        YsmResourceSource ysmPackage = data.resources();
        if (element == null || element.isJsonNull())
            return;
        JsonObject object = element.isJsonObject() ? element.getAsJsonObject() : new JsonObject();
        String id = firstString(object, fallbackId, "identifier", "id", "name");
        String model = element.isJsonPrimitive() ? element.getAsString() : firstString(object, "", "model", "model_path");
        if (model.isBlank() || !ysmPackage.exists(model) && !data.hasGeometry(model))
            return;
        String texture = texturePath(object);
        String animation = firstString(object, "", "animation", "animations", "animation_path");
        String controller = firstString(object, "", "controller", "animation_controller", "controller_path");

        CompoundTag entry = new CompoundTag();
        entry.putString("kind", kind);
        entry.putString("id", id == null || id.isBlank() ? fallbackId : id);
        entry.put("match_ids", strings(matchIds(object)));
        entry.putString("model_path", model);
        if (ysmPackage.exists(model)) {
            entry.putByteArray("model", ysmPackage.readBytes(model));
        }
        entry.put("animation_entries", resourceEntries(ysmPackage, object.get("animation")));
        entry.put("controller_entries", resourceEntries(ysmPackage, object.get("animation_controllers")));
        if (!texture.isBlank() && ysmPackage.exists(texture)) {
            entry.putString("texture_path", texture);
            entry.putByteArray("texture", ysmPackage.readBytes(texture));
        }
        JsonElement configuredTexture = object.has("texture") ? object.get("texture") : object.get("textures");
        JsonElement selectedTexture = firstTexture(configuredTexture);
        if (selectedTexture != null && selectedTexture.isJsonObject()) {
            JsonObject material = selectedTexture.getAsJsonObject();
            for (String channel : List.of("normal", "specular")) {
                String path = YsmPackage.normalize(YsmJson.string(material, channel, ""));
                if (!path.isBlank() && ysmPackage.exists(path)) {
                    entry.putString(channel + "_path", path);
                    entry.putByteArray(channel + "_texture", ysmPackage.readBytes(path));
                }
            }
        }
        if (!animation.isBlank() && ysmPackage.exists(animation)) {
            entry.putString("animation_path", animation);
            entry.putByteArray("animation", ysmPackage.readString(animation).getBytes(java.nio.charset.StandardCharsets.UTF_8));
        }
        if (!controller.isBlank() && ysmPackage.exists(controller)) {
            entry.putString("controller_path", controller);
            entry.putByteArray("controller", ysmPackage.readString(controller).getBytes(java.nio.charset.StandardCharsets.UTF_8));
        }
        tag.add(entry);
    }

    private static ListTag resourceEntries(YsmResourceSource source, JsonElement element) throws java.io.IOException {
        ListTag entries = new ListTag();
        if (element == null || element.isJsonNull()) {
            return entries;
        }
        if (element.isJsonArray()) {
            for (JsonElement child : element.getAsJsonArray()) {
                entries.addAll(resourceEntries(source, child));
            }
        } else if (element.isJsonPrimitive()) {
            String path = element.getAsString();
            if (source.exists(path)) {
                CompoundTag entry = new CompoundTag();
                entry.putString("path", path);
                entry.putByteArray("data", source.readBytes(path));
                entries.add(entry);
            }
        }
        return entries;
    }

    private static void addLegacySubEntity(ListTag tag, YsmResourceSource ysmPackage, String kind, String id, String model, String texture, String animation) throws java.io.IOException {
        if (!ysmPackage.exists(model))
            return;
        CompoundTag entry = new CompoundTag();
        entry.putString("kind", kind);
        entry.putString("id", id);
        entry.put("match_ids", strings(List.of(id, "minecraft:" + id)));
        entry.putString("model_path", model);
        entry.putByteArray("model", ysmPackage.readString(model).getBytes(java.nio.charset.StandardCharsets.UTF_8));
        if (ysmPackage.exists(texture)) {
            entry.putString("texture_path", texture);
            entry.putByteArray("texture", ysmPackage.readBytes(texture));
        }
        if (ysmPackage.exists(animation)) {
            entry.putString("animation_path", animation);
            entry.putByteArray("animation", ysmPackage.readString(animation).getBytes(java.nio.charset.StandardCharsets.UTF_8));
        }
        tag.add(entry);
    }

    private static String texturePath(JsonObject object) {
        if (object == null)
            return "";
        JsonElement configured = object.has("texture") ? object.get("texture") : object.get("textures");
        JsonElement texture = firstTexture(configured);
        if (texture == null) {
            return "";
        }
        if (texture.isJsonPrimitive()) {
            return texture.getAsString();
        }
        if (texture.isJsonObject()) {
            return firstString(texture.getAsJsonObject(), "", "uv", "path", "default");
        }
        return "";
    }

    private static JsonElement firstTexture(JsonElement configured) {
        if (configured == null || configured.isJsonNull()) {
            return null;
        }
        if (configured.isJsonArray()) {
            for (JsonElement candidate : configured.getAsJsonArray()) {
                JsonElement texture = firstTexture(candidate);
                if (texture != null) {
                    return texture;
                }
            }
            return null;
        }
        return configured;
    }

    private static String firstString(JsonObject object, String fallback, String... keys) {
        if (object == null)
            return fallback == null ? "" : fallback;
        for (String key : keys) {
            JsonElement element = object.get(key);
            String value = stringValue(element);
            if (!value.isBlank())
                return value;
        }
        return fallback == null ? "" : fallback;
    }

    private static String stringValue(JsonElement element) {
        if (element == null || element.isJsonNull())
            return "";
        if (element.isJsonPrimitive())
            return element.getAsString();
        if (element.isJsonArray()) {
            for (JsonElement item : element.getAsJsonArray()) {
                String value = stringValue(item);
                if (!value.isBlank())
                    return value;
            }
        }
        return "";
    }

    private static List<String> matchIds(JsonObject object) {
        List<String> result = new ArrayList<>();
        addMatches(result, object.get("match"));
        addMatches(result, object.get("matches"));
        addMatches(result, object.get("match_ids"));
        addMatches(result, object.get("entities"));
        addMatches(result, object.get("items"));
        return result;
    }

    private static void addMatches(List<String> result, JsonElement element) {
        if (element == null || element.isJsonNull())
            return;
        if (element.isJsonArray()) {
            JsonArray array = element.getAsJsonArray();
            for (JsonElement item : array)
                addMatches(result, item);
            return;
        }
        String value = stringValue(element);
        if (!value.isBlank() && !result.contains(value))
            result.add(value);
    }

}
