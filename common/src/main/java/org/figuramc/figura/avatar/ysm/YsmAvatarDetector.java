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
        if (YsmEncryptedSource.hasMagic(path)) {
            return YsmAvatarKind.NEW;
        }
        // Directory discovery must not recursively index every model below a grouping folder.
        if (Files.isDirectory(path)) {
            if (Files.isRegularFile(path.resolve("ysm.json"))) {
                return YsmAvatarKind.NEW;
            }
            Path main = path.resolve("main.json");
            if (Files.isRegularFile(main)) {
                try (var reader = Files.newBufferedReader(main)) {
                    char[] header = new char[16384];
                    String suffix = "";
                    int read;
                    while ((read = reader.read(header)) != -1) {
                        String text = suffix + new String(header, 0, read);
                        if (text.contains("minecraft:geometry")) {
                            return YsmAvatarKind.OLD;
                        }
                        suffix = text.substring(Math.max(0, text.length() - 18));
                    }
                } catch (java.io.IOException ignored) {
                }
            }
            return YsmAvatarKind.NONE;
        }
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
