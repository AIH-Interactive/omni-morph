package org.figuramc.figura.avatar.ysm;

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
                try (YsmPackage ysmPackage = YsmPackage.open(path)) {
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

                    if (texture != null) {
                        ysm.putString("texture_id", texture.id());
                        if (ysmPackage.exists(texture.path()))
                            ysm.putByteArray("texture", ysmPackage.readBytes(texture.path()));
                    }
                    ysm.put("texture_entries", textureEntries(ysmPackage, manifest.textures()));
                    ysm.put("action_schemas", actionSchemas(ysmPackage));
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

    private static CompoundTag toNbt(YsmResourceIndex index) {
        CompoundTag tag = new CompoundTag();
        tag.putString("main_model", index.mainModelPath());
        tag.putString("arm_model", index.armModelPath());
        tag.put("animations", strings(index.animationPaths()));
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

    private static ListTag actionSchemas(YsmPackage ysmPackage) throws java.io.IOException {
        ListTag tag = new ListTag();
        for (String path : List.of("ysm.json", "ysm.actions.json", "action_wheel.json", "ysm.controls.json", "ysm_controls.json", "controls.json")) {
            if (!ysmPackage.exists(path))
                continue;
            CompoundTag entry = new CompoundTag();
            entry.putString("path", path);
            entry.putByteArray("data", ysmPackage.readString(path).getBytes(java.nio.charset.StandardCharsets.UTF_8));
            tag.add(entry);
        }
        return tag;
    }

}
