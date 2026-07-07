package org.figuramc.figura.avatar.ysm;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
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
                ysm.putByteArray("main_model", IOUtils.readFile(path.resolve(manifest.mainModelPath())).getBytes(java.nio.charset.StandardCharsets.UTF_8));
                ListTag animations = new ListTag();
                for (String animationPath : manifest.animationPaths()) {
                    Path resolved = path.resolve(animationPath);
                    if (!java.nio.file.Files.exists(resolved))
                        continue;
                    CompoundTag animation = new CompoundTag();
                    animation.putString("path", animationPath);
                    animation.putByteArray("data", IOUtils.readFile(resolved).getBytes(java.nio.charset.StandardCharsets.UTF_8));
                    animations.add(animation);
                }
                ysm.put("animations", animations);
                if (texture != null) {
                    Path texturePath = path.resolve(texture.path());
                    ysm.putString("texture_id", texture.id());
                    if (java.nio.file.Files.exists(texturePath))
                        ysm.putByteArray("texture", IOUtils.readFileBytes(texturePath));
                }
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
}
