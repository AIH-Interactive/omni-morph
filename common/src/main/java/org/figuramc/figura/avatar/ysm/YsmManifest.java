package org.figuramc.figura.avatar.ysm;

import java.util.List;

public record YsmManifest(
        YsmAvatarKind kind,
        String name,
        String description,
        String[] authors,
        String mainModelPath,
        String armModelPath,
        List<String> animationPaths,
        List<YsmTextureOption> textures,
        String defaultTexture,
        YsmResourceIndex resourceIndex
) {
    public YsmManifest(YsmAvatarKind kind, String name, String description, String[] authors, String mainModelPath,
                       String armModelPath, List<String> animationPaths, List<YsmTextureOption> textures,
                       String defaultTexture) {
        this(kind, name, description, authors, mainModelPath, armModelPath, animationPaths, textures, defaultTexture,
                YsmResourceIndex.fromManifest(mainModelPath, armModelPath, animationPaths, textures, kind));
    }

    public boolean hasTextures() {
        return textures != null && !textures.isEmpty();
    }

    public boolean hasArmModel() {
        return armModelPath != null && !armModelPath.isBlank();
    }

    public boolean hasAnimations() {
        return animationPaths != null && !animationPaths.isEmpty();
    }
}
