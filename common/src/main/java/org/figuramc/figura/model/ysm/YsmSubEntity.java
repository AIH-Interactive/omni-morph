package org.figuramc.figura.model.ysm;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
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
    private final String animationPath;
    private final String animationJson;
    private final String controllerPath;
    private final String controllerJson;

    private YsmSubEntity(String kind, String id, List<String> matchIds, String modelPath, String texturePath, YsmGeometry geometry, FiguraTexture texture, String animationPath, String animationJson, String controllerPath, String controllerJson) {
        this.kind = kind == null || kind.isBlank() ? "sub_entity" : kind;
        this.id = id == null || id.isBlank() ? this.kind : id;
        this.matchIds = List.copyOf(matchIds == null ? List.of() : matchIds);
        this.modelPath = modelPath == null ? "" : modelPath;
        this.texturePath = texturePath == null ? "" : texturePath;
        this.geometry = geometry;
        this.texture = texture;
        this.animationPath = animationPath == null ? "" : animationPath;
        this.animationJson = animationJson == null ? "" : animationJson;
        this.controllerPath = controllerPath == null ? "" : controllerPath;
        this.controllerJson = controllerJson == null ? "" : controllerJson;
    }

    public static YsmSubEntity fromNbt(Avatar owner, CompoundTag tag, byte[] fallbackTexture) throws java.io.IOException {
        String modelJson = new String(tag.getByteArray("model").orElse(new byte[0]), StandardCharsets.UTF_8);
        if (modelJson.isBlank())
            throw new java.io.IOException("YSM sub entity model is empty");
        YsmGeometry geometry = YsmGeometryParser.parse(modelJson);
        String texturePath = tag.getStringOr("texture_path", "");
        byte[] textureBytes = tag.getByteArray("texture").orElse(fallbackTexture == null ? new byte[0] : fallbackTexture);
        FiguraTexture texture = new FiguraTexture(owner, "ysm_sub_entity/" + tag.getStringOr("id", "sub_entity"), textureBytes.length == 0 ? onePixelPng() : textureBytes);
        return new YsmSubEntity(
                tag.getStringOr("kind", "sub_entity"),
                tag.getStringOr("id", "sub_entity"),
                readStrings(tag),
                tag.getStringOr("model_path", ""),
                texturePath,
                geometry,
                texture,
                tag.getStringOr("animation_path", ""),
                new String(tag.getByteArray("animation").orElse(new byte[0]), StandardCharsets.UTF_8),
                tag.getStringOr("controller_path", ""),
                new String(tag.getByteArray("controller").orElse(new byte[0]), StandardCharsets.UTF_8)
        );
    }

    private static List<String> readStrings(CompoundTag tag) {
        List<String> result = new ArrayList<>();
        for (Tag value : tag.getListOrEmpty("match_ids")) {
            String string = value.asString().orElse("");
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
