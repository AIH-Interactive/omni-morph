package top.aihmc.omnimorph.model.ysm;

import org.figuramc.figura.utils.NbtDefaults;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.Entity;
import org.figuramc.figura.avatar.Avatar;
import org.figuramc.figura.model.rendering.texture.FiguraTexture;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public class YsmSubEntity implements AutoCloseable {
    private final String kind;
    private final String id;
    private final List<String> matchIds;
    private final String modelPath;
    private final String texturePath;
    private final YsmGeometry geometry;
    private final FiguraTexture texture;
    private final boolean textureTranslucent;
    private final String animationPath;
    private final String animationJson;
    private final String controllerPath;
    private final String controllerJson;
    private List<String> animationResources = List.of();
    private List<String> controllerResources = List.of();

    private YsmSubEntity(String kind, String id, List<String> matchIds, String modelPath, String texturePath, YsmGeometry geometry, FiguraTexture texture, boolean textureTranslucent, String animationPath, String animationJson, String controllerPath, String controllerJson) {
        this.kind = kind == null || kind.isBlank() ? "sub_entity" : kind;
        this.id = id == null || id.isBlank() ? this.kind : id;
        this.matchIds = List.copyOf(matchIds == null ? List.of() : matchIds);
        this.modelPath = modelPath == null ? "" : modelPath;
        this.texturePath = texturePath == null ? "" : texturePath;
        this.geometry = geometry;
        this.texture = texture;
        this.textureTranslucent = textureTranslucent;
        this.animationPath = animationPath == null ? "" : animationPath;
        this.animationJson = animationJson == null ? "" : animationJson;
        this.controllerPath = controllerPath == null ? "" : controllerPath;
        this.controllerJson = controllerJson == null ? "" : controllerJson;
    }

    public static YsmSubEntity fromNbt(Avatar owner, CompoundTag tag, byte[] fallbackTexture) throws java.io.IOException {
        String modelJson = new String(tag.getByteArray("model"), StandardCharsets.UTF_8);
        if (modelJson.isBlank())
            throw new java.io.IOException("YSM sub entity model is empty");
        YsmGeometry geometry = YsmGeometryParser.parse(modelJson);
        return fromGeometry(owner, tag, fallbackTexture, geometry);
    }

    public static YsmSubEntity fromGeometry(Avatar owner, CompoundTag tag, byte[] fallbackTexture,
                                           YsmGeometry geometry) throws java.io.IOException {
        String texturePath = tag.getString("texture_path");
        byte[] textureBytes = NbtDefaults.findByteArray(tag, "texture").orElse(fallbackTexture == null ? new byte[0] : fallbackTexture);
        boolean textureTranslucent = geometry.applyTextureAlpha(textureBytes);
        byte[] base = textureBytes.length == 0 ? onePixelPng() : textureBytes;
        byte[] normal = tag.getByteArray("normal_texture");
        byte[] specular = tag.getByteArray("specular_texture");
        String textureName = "ysm_sub_entity/" + NbtDefaults.getStringOr(tag, "id", "sub_entity");
        FiguraTexture texture = normal.length == 0 && specular.length == 0
                ? new FiguraTexture(owner, textureName, base)
                : new YsmMaterialTexture(owner, textureName, base, normal, specular);
        YsmSubEntity entity = new YsmSubEntity(
                NbtDefaults.getStringOr(tag, "kind", "sub_entity"),
                NbtDefaults.getStringOr(tag, "id", "sub_entity"),
                readStrings(tag),
                tag.getString("model_path"),
                texturePath,
                geometry,
                texture,
                textureTranslucent,
                tag.getString("animation_path"),
                new String(tag.getByteArray("animation"), StandardCharsets.UTF_8),
                tag.getString("controller_path"),
                new String(tag.getByteArray("controller"), StandardCharsets.UTF_8)
        );
        entity.animationResources = resourceStrings(tag, "animation_entries", entity.animationJson);
        entity.controllerResources = resourceStrings(tag, "controller_entries", entity.controllerJson);
        return entity;
    }

    private static List<String> resourceStrings(CompoundTag tag, String key, String fallback) {
        List<String> resources = new ArrayList<>();
        for (Tag entry : NbtDefaults.getListOrEmpty(tag, key)) {
            if (entry instanceof CompoundTag resource) {
                resources.add(new String(resource.getByteArray("data"), StandardCharsets.UTF_8));
            }
        }
        if (resources.isEmpty() && !fallback.isBlank()) {
            resources.add(fallback);
        }
        return List.copyOf(resources);
    }

    public List<String> animationResources() {
        return animationResources;
    }

    public List<String> controllerResources() {
        return controllerResources;
    }

    private static List<String> readStrings(CompoundTag tag) {
        List<String> result = new ArrayList<>();
        for (Tag value : NbtDefaults.getListOrEmpty(tag, "match_ids")) {
            String string = value.getAsString();
            if (string != null && !string.isBlank() && !result.contains(string))
                result.add(string);
        }
        return result;
    }

    public boolean matches(String requestedKind, String entityId) {
        if (requestedKind != null && !requestedKind.isBlank() && !kind.equalsIgnoreCase(requestedKind))
            return false;
        if (entityId == null || entityId.isBlank())
            return true;
        String normalized = entityId.toLowerCase(java.util.Locale.US);
        if (id.equalsIgnoreCase(entityId) || modelPath.equalsIgnoreCase(entityId) || texturePath.equalsIgnoreCase(entityId))
            return true;
        for (String matchId : matchIds) {
            if (matchId.equalsIgnoreCase(entityId) || matchId.toLowerCase(java.util.Locale.US).equals(normalized))
                return true;
        }
        return false;
    }

    public boolean matches(String requestedKind, Entity entity) {
        if (entity == null || !matches(requestedKind, (String) null)) {
            return false;
        }
        String typeId = BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).toString();
        if (matches(requestedKind, typeId) || matchesTag(id, entity)) {
            return true;
        }
        return matchIds.stream().anyMatch(match -> matchesTag(match, entity));
    }

    private static boolean matchesTag(String match, Entity entity) {
        if (!match.startsWith("#")) {
            return false;
        }
        ResourceLocation id = ResourceLocation.tryParse(match.substring(1));
        return id != null && entity.getType().builtInRegistryHolder().is(TagKey.create(Registries.ENTITY_TYPE, id));
    }

    public String kind() {
        return kind;
    }

    public String id() {
        return id;
    }

    public List<String> matchIds() {
        return matchIds;
    }

    public YsmGeometry geometry() {
        return geometry;
    }

    public FiguraTexture texture() {
        return texture;
    }

    public boolean isTextureTranslucent() {
        return textureTranslucent;
    }

    public String animationPath() {
        return animationPath;
    }

    public String animationJson() {
        return animationJson;
    }

    public String controllerPath() {
        return controllerPath;
    }

    public String controllerJson() {
        return controllerJson;
    }

    @Override
    public void close() {
        texture.close();
    }

    private static byte[] onePixelPng() {
        return java.util.Base64.getDecoder().decode("iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMB/atX7pQAAAAASUVORK5CYII=");
    }

}
