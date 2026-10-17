package top.aihmc.omnimorph.avatar.ysm;

import java.util.List;

public record YsmManifest(
        YsmAvatarKind kind,
        String name,
        String description,
        String[] authors,
        String mainModelPath,
        String armModelPath,
        List<String> animationPaths,
        List<String> animationControllerPaths,
        List<YsmTextureOption> textures,
        String defaultTexture,
        YsmResourceIndex resourceIndex,
        boolean free
) {
    public YsmManifest {
        authors = authors == null ? new String[0] : authors.clone();
        animationPaths = List.copyOf(animationPaths);
        animationControllerPaths = List.copyOf(animationControllerPaths);
        textures = List.copyOf(textures);
    }

    @Override
    public String[] authors() {
        return authors.clone();
    }

    public YsmManifest(YsmAvatarKind kind, String name, String description, String[] authors, String mainModelPath,
                       String armModelPath, List<String> animationPaths, List<String> animationControllerPaths,
                       List<YsmTextureOption> textures, String defaultTexture, YsmResourceIndex resourceIndex) {
        this(kind, name, description, authors, mainModelPath, armModelPath, animationPaths,
                animationControllerPaths, textures, defaultTexture, resourceIndex, false);
    }

    public YsmManifest(YsmAvatarKind kind, String name, String description, String[] authors, String mainModelPath,
                       String armModelPath, List<String> animationPaths, List<YsmTextureOption> textures,
                       String defaultTexture) {
        this(kind, name, description, authors, mainModelPath, armModelPath, animationPaths, List.of(), textures, defaultTexture,
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

    public boolean hasAnimationControllers() {
        return animationControllerPaths != null && !animationControllerPaths.isEmpty();
    }
}
