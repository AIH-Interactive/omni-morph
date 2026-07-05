package org.figuramc.figura.avatar.ysm;

import java.util.List;

public record YsmManifest(
        YsmAvatarKind kind,
        String name,
        String description,
        String[] authors,
        String mainModelPath,
        String armModelPath,
        List<YsmTextureOption> textures,
        String defaultTexture
) {
    public boolean hasTextures() {
        return textures != null && !textures.isEmpty();
    }
}
