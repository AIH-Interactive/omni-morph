package org.figuramc.figura.avatar.ysm;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import org.figuramc.figura.FiguraMod;
import org.figuramc.figura.avatar.UserData;
import org.figuramc.figura.gui.FiguraToast;
import org.figuramc.figura.parsers.AvatarMetadataParser;
import org.figuramc.figura.utils.FiguraText;
import org.figuramc.figura.utils.IOUtils;

import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public final class YsmAvatarLoader {
    private YsmAvatarLoader() {
    }

    public static void loadAvatar(Path path, UserData target) {
        CompletableFuture.runAsync(() -> {
            try {
                YsmManifest manifest = YsmManifestReader.read(path);
                YsmResourceIndex index = manifest.resourceIndex();
                YsmTextureOption texture = selectTexture(path, manifest.textures(), manifest.defaultTexture());
                CompoundTag nbt = new CompoundTag();

                String avatarJson = YsmAvatarMetadataParser.toFiguraAvatarJson(new YsmAvatarMetadata(manifest.name(), manifest.description(), manifest.authors(), manifest.textures()));
                AvatarMetadataParser.Metadata figuraMetadata = AvatarMetadataParser.read(avatarJson);
                CompoundTag metaNBT = AvatarMetadataParser.parse(figuraMetadata, avatarJson, IOUtils.getFileNameOrEmpty(path));
                metaNBT.putString("uuid", target.id.toString());
                metaNBT.putString("format", "ysm-native");
                metaNBT.putString("ysm_kind", manifest.kind().name().toLowerCase(java.util.Locale.US));
                if (texture != null) {
                    metaNBT.putString("ysm_texture", texture.id());
                    metaNBT.putString("ysm_texture_path", texture.path());
                }
                nbt.put("metadata", metaNBT);

                CompoundTag ysm = new CompoundTag();
                ysm.putString("kind", manifest.kind().name());
                ysm.putString("source_path", path.toAbsolutePath().normalize().toString());
                try (YsmPackage ysmPackage = YsmPackage.open(path)) {
                    addYsmProperties(ysm, ysmPackage);

                    String mainModelPath = index.hasMainModel() ? index.mainModelPath() : manifest.mainModelPath();
                    ysm.putString("main_model_path", mainModelPath);
                    ysm.putByteArray("main_model", ysmPackage.readString(mainModelPath).getBytes(java.nio.charset.StandardCharsets.UTF_8));

                    if (index.hasArmModel()) {
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
                        byte[] data = ysmPackage.readString(functionPath).getBytes(java.nio.charset.StandardCharsets.UTF_8);
                        CompoundTag function = new CompoundTag();
                        function.putString("path", functionPath);
                        function.putString("name", fileName(functionPath));
                        function.putString("hash", sha1(data));
                        function.putByteArray("data", data);
                        functions.add(function);
                    }
                    ysm.put("functions", functions);

                    if (texture != null) {
                        ysm.putString("texture_id", texture.id());
                        if (ysmPackage.exists(texture.path()))
                            ysm.putByteArray("texture", ysmPackage.readBytes(texture.path()));
                    }
                    ysm.put("texture_entries", textureEntries(ysmPackage, manifest.textures()));
                    ysm.put("action_schemas", actionSchemas(ysmPackage, index));
                    ysm.put("sub_entities", subEntities(ysmPackage));
                }
                ysm.put("resource_index", toNbt(index));
                nbt.put("ysm", ysm);

                target.loadAvatar(nbt);
            } catch (Throwable e) {
                FiguraMod.LOGGER.error("Failed to load YSM avatar from " + path, e);
                FiguraToast.sendToast(FiguraText.of("toast.load_error"), FiguraText.of("gui.load_error.models"), FiguraToast.ToastType.ERROR);
            }
        });
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

    private static void addYsmProperties(CompoundTag ysm, YsmPackage ysmPackage) throws java.io.IOException {
        if (!ysmPackage.exists("ysm.json"))
            return;
        JsonObject root = YsmJson.parseObject(ysmPackage.readString("ysm.json"));
        JsonObject properties = YsmJson.obj(root, "properties");
        putPositiveFloat(ysm, "width_scale", YsmJson.number(properties, "width_scale", 1f));
        putPositiveFloat(ysm, "height_scale", YsmJson.number(properties, "height_scale", 1f));
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

    private static ListTag textureEntries(YsmPackage ysmPackage, List<YsmTextureOption> textures) throws java.io.IOException {
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
            tag.add(entry);
        }
        return tag;
    }

    private static ListTag actionSchemas(YsmPackage ysmPackage, YsmResourceIndex index) throws java.io.IOException {
        ListTag tag = new ListTag();
        java.util.LinkedHashSet<String> paths = new java.util.LinkedHashSet<>();
        paths.addAll(List.of("ysm.json", "ysm.actions.json", "action_wheel.json", "ysm.controls.json", "ysm_controls.json", "controls.json"));
        if (index != null)
            paths.addAll(index.metadataPaths());
        for (String path : paths)
            addActionSchema(tag, ysmPackage, path);
        return tag;
    }

    private static void addActionSchema(ListTag tag, YsmPackage ysmPackage, String path) throws java.io.IOException {
        if (path == null || path.isBlank() || !ysmPackage.exists(path))
            return;
        CompoundTag entry = new CompoundTag();
        entry.putString("path", path);
        entry.putByteArray("data", ysmPackage.readString(path).getBytes(java.nio.charset.StandardCharsets.UTF_8));
        tag.add(entry);
    }

    private static ListTag subEntities(YsmPackage ysmPackage) throws java.io.IOException {
        ListTag tag = new ListTag();
        if (ysmPackage.exists("ysm.json")) {
            JsonObject root = YsmJson.parseObject(ysmPackage.readString("ysm.json"));
            JsonObject files = YsmJson.obj(root, "files");
            addSubEntitySection(tag, ysmPackage, "projectile", files.get("projectiles"));
            addSubEntitySection(tag, ysmPackage, "vehicle", files.get("vehicles"));
        }
        addLegacySubEntity(tag, ysmPackage, "projectile", "arrow", "arrow.json", "arrow.png", "arrow.animation.json");
        return tag;
    }

    private static void addSubEntitySection(ListTag tag, YsmPackage ysmPackage, String kind, JsonElement section) throws java.io.IOException {
        if (section == null || section.isJsonNull())
            return;
        if (section.isJsonObject()) {
            for (var entry : section.getAsJsonObject().entrySet())
                addSubEntity(tag, ysmPackage, kind, entry.getKey(), entry.getValue());
        } else if (section.isJsonArray()) {
            int index = 0;
            for (JsonElement element : section.getAsJsonArray())
                addSubEntity(tag, ysmPackage, kind, kind + index++, element);
        }
    }

    private static void addSubEntity(ListTag tag, YsmPackage ysmPackage, String kind, String fallbackId, JsonElement element) throws java.io.IOException {
        if (element == null || element.isJsonNull())
            return;
        JsonObject object = element.isJsonObject() ? element.getAsJsonObject() : new JsonObject();
        String id = firstString(object, fallbackId, "identifier", "id", "name");
        String model = element.isJsonPrimitive() ? element.getAsString() : firstString(object, "", "model", "model_path");
        if (model.isBlank() || !ysmPackage.exists(model))
            return;
        String texture = texturePath(object);
        String animation = firstString(object, "", "animation", "animations", "animation_path");
        String controller = firstString(object, "", "controller", "animation_controller", "controller_path");

        CompoundTag entry = new CompoundTag();
        entry.putString("kind", kind);
        entry.putString("id", id == null || id.isBlank() ? fallbackId : id);
        entry.put("match_ids", strings(matchIds(object)));
        entry.putString("model_path", model);
        entry.putByteArray("model", ysmPackage.readString(model).getBytes(java.nio.charset.StandardCharsets.UTF_8));
        if (!texture.isBlank() && ysmPackage.exists(texture)) {
            entry.putString("texture_path", texture);
            entry.putByteArray("texture", ysmPackage.readBytes(texture));
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

    private static void addLegacySubEntity(ListTag tag, YsmPackage ysmPackage, String kind, String id, String model, String texture, String animation) throws java.io.IOException {
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
        JsonElement texture = object.get("texture");
        if (texture == null)
            texture = object.get("textures");
        if (texture == null)
            return "";
        if (texture.isJsonPrimitive())
            return texture.getAsString();
        if (texture.isJsonObject())
            return firstString(texture.getAsJsonObject(), "", "uv", "path", "default", "normal");
        return "";
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
