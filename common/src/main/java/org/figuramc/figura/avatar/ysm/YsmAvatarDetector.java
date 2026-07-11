package org.figuramc.figura.avatar.ysm;

import java.nio.file.Files;
import java.nio.file.Path;

public final class YsmAvatarDetector {
    private YsmAvatarDetector() {
    }

    public static boolean isYsmAvatar(Path path) {
        return kind(path) != YsmAvatarKind.NONE;
    }

    public static YsmAvatarKind kind(Path path) {
        if (path == null || !Files.exists(path))
            return YsmAvatarKind.NONE;
        try (YsmPackage ysmPackage = YsmPackage.open(path)) {
            Path manifest = ysmPackage.resolve("ysm.json");
            if (Files.exists(manifest) && !Files.isDirectory(manifest))
                return YsmAvatarKind.NEW;
            Path main = ysmPackage.resolve("main.json");
            if (Files.exists(main) && !Files.isDirectory(main)) {
                try {
                    if (org.figuramc.figura.utils.IOUtils.readFile(main).contains("minecraft:geometry"))
                        return YsmAvatarKind.OLD;
                } catch (Exception ignored) {
                }
            }
        } catch (Exception ignored) {
        }
        return YsmAvatarKind.NONE;
    }
}
