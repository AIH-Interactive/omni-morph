package org.figuramc.figura.avatar.ysm;

import java.util.List;

public record YsmResourceIndex(
        String mainModelPath,
        String armModelPath,
        List<String> animationPaths,
        List<String> animationControllerPaths,
        List<String> functionPaths,
        List<String> texturePaths,
        List<String> soundPaths,
        String iconPath,
        String backgroundPath,
        List<String> metadataPaths
) {
    public YsmResourceIndex {
        mainModelPath = normalizeNullable(mainModelPath);
        armModelPath = normalizeNullable(armModelPath);
        animationPaths = copyNormalized(animationPaths);
        animationControllerPaths = copyNormalized(animationControllerPaths);
        functionPaths = copyNormalized(functionPaths);
        texturePaths = copyNormalized(texturePaths);
        soundPaths = copyNormalized(soundPaths);
        iconPath = normalizeNullable(iconPath);
        backgroundPath = normalizeNullable(backgroundPath);
        metadataPaths = copyNormalized(metadataPaths);
    }

    public boolean hasMainModel() {
        return mainModelPath != null && !mainModelPath.isBlank();
    }

    public boolean hasArmModel() {
        return armModelPath != null && !armModelPath.isBlank();
    }

    public boolean hasAnimations() {
        return !animationPaths.isEmpty();
    }

    public boolean hasAnimationControllers() {
        return !animationControllerPaths.isEmpty();
    }

    public boolean hasTextures() {
        return !texturePaths.isEmpty();
    }

    public boolean hasSounds() {
        return !soundPaths.isEmpty();
    }

    public static YsmResourceIndex fromManifest(String mainModelPath, String armModelPath, List<String> animationPaths,
                                                List<YsmTextureOption> textures, YsmAvatarKind kind) {
        return new YsmResourceIndex(
                mainModelPath,
                armModelPath,
                animationPaths,
                List.of(),
                List.of(),
                textures == null ? List.of() : textures.stream().map(YsmTextureOption::path).toList(),
                List.of(),
                "",
                "",
                kind == YsmAvatarKind.NEW ? List.of("ysm.json") : kind == YsmAvatarKind.OLD ? List.of("main.json") : List.of()
        );
    }

    private static List<String> copyNormalized(List<String> paths) {
        if (paths == null || paths.isEmpty())
            return List.of();
        return paths.stream()
                .filter(path -> path != null && !path.isBlank())
                .map(YsmPackage::normalize)
                .distinct()
                .toList();
    }

    private static String normalizeNullable(String path) {
        if (path == null || path.isBlank())
            return "";
        return YsmPackage.normalize(path);
    }
}
