package top.aihmc.omnimorph.model.cache;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;

/** Full-avatar caches only; thumbnails, account metadata and future decorations use other roots. */
public final class AvatarCachePaths {
    private AvatarCachePaths() {
    }

    /** Lazily moves a known flat cache directory without replacing newer cache entries. */
    public static synchronized Path prepare(Path cacheRoot, String relativeDirectory) throws IOException {
        Path root = cacheRoot.toAbsolutePath().normalize();
        Path avatars = root.resolve("avatars");
        Path legacy = root.resolve(relativeDirectory).normalize();
        Path target = avatars.resolve(relativeDirectory).normalize();
        if (!legacy.startsWith(root) || legacy.equals(root) || !target.startsWith(avatars) || target.equals(avatars)) {
            throw new IOException("INVALID_AVATAR_CACHE_PATH");
        }
        checkParents(root, legacy);
        checkParents(root, target);
        Files.createDirectories(target);
        if (Files.isDirectory(legacy, LinkOption.NOFOLLOW_LINKS)) {
            try (var files = Files.list(legacy)) {
                for (Path file : files.toList()) {
                    Path destination = target.resolve(file.getFileName());
                    if (Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)
                            && !Files.exists(destination, LinkOption.NOFOLLOW_LINKS)) {
                        Files.move(file, destination);
                    }
                }
            }
            try (var remaining = Files.list(legacy)) {
                if (remaining.findAny().isEmpty()) {
                    Files.delete(legacy);
                }
            }
        }
        return target;
    }

    private static void checkParents(Path root, Path directory) throws IOException {
        for (Path current = directory; current != null && current.startsWith(root); current = current.getParent()) {
            if (Files.isSymbolicLink(current)) {
                throw new IOException("UNSAFE_AVATAR_CACHE");
            }
        }
    }
}
