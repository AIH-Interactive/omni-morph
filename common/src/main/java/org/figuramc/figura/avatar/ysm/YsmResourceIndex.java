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
        List<String> metadataPaths,
        List<String> languagePaths,
        List<String> projectilePaths,
        List<String> vehiclePaths,
        List<String> warnings
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
        languagePaths = copyNormalized(languagePaths);
        projectilePaths = copyNormalized(projectilePaths);
        vehiclePaths = copyNormalized(vehiclePaths);
        warnings = copyWarnings(warnings);
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

    public boolean hasLanguages() {
        return !languagePaths.isEmpty();
    }

    public boolean hasProjectiles() {
        return !projectilePaths.isEmpty();
    }

    public boolean hasVehicles() {
        return !vehiclePaths.isEmpty();
    }

    public boolean hasWarnings() {
        return !warnings.isEmpty();
    }

    public YsmResourceIndex(String mainModelPath, String armModelPath, List<String> animationPaths,
                            List<String> animationControllerPaths, List<String> functionPaths,
                            List<String> texturePaths, List<String> soundPaths, String iconPath,
                            String backgroundPath, List<String> metadataPaths) {
        this(mainModelPath, armModelPath, animationPaths, animationControllerPaths, functionPaths, texturePaths,
                        soundPaths, iconPath, backgroundPath, metadataPaths, List.of(), List.of(), List.of(), List.of());
    }

    public static YsmResourceIndex fromManifest(String mainModelPath, String armModelPath, List<String> animationPaths,
                                                List<YsmTextureOption> textures, YsmAvatarKind kind) {
        return new YsmResourceIndex(
                mainModelPath,
                armModelPath,
                animationPaths,
                List.of(),
                List.of(),
                textures == null ? List.of() : textures.stream()
                        .flatMap(texture -> java.util.stream.Stream.of(texture.path(), texture.normalPath(), texture.specularPath()))
                        .filter(path -> path != null && !path.isBlank())
                        .distinct().toList(),
                List.of(),
                "",
                "",
                kind == YsmAvatarKind.NEW ? List.of("ysm.json") : kind == YsmAvatarKind.OLD ? List.of("main.json") : List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of()
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

    private static List<String> copyWarnings(List<String> warnings) {
        if (warnings == null || warnings.isEmpty())
            return List.of();
        return warnings.stream()
                .filter(warning -> warning != null && !warning.isBlank())
                .distinct()
                .toList();
    }

    private static String normalizeNullable(String path) {
        if (path == null || path.isBlank())
            return "";
        return YsmPackage.normalize(path);
    }
}
