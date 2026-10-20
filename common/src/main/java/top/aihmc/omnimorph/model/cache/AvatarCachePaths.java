package top.aihmc.omnimorph.model.cache;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/** Avatar models and thumbnails share a root; account metadata and future decorations remain separate. */
public final class AvatarCachePaths {
    private static final Set<Path> RENAMED_ROOTS = new HashSet<>();

    private AvatarCachePaths() {
    }

    @FunctionalInterface
    public interface MigratedName {
        String name(Path source) throws IOException;
    }

    /** Retains the separate directory for incomplete download staging. */
    public static synchronized Path prepare(Path cacheRoot, String relativeDirectory) throws IOException {
        Path root = cacheRoot.toAbsolutePath().normalize();
        Path target = root.resolve(ModelCacheLayout.AVATAR_DIRECTORY).resolve(relativeDirectory).normalize();
        validate(root, target);
        moveFiles(root, root.resolve(relativeDirectory).normalize(), target, file -> file.getFileName().toString());
        return target;
    }

    /** Migrates both historical layouts into the shared flat avatar directory. */
    public static synchronized Path prepareFlat(Path cacheRoot, String relativeDirectory,
                                                MigratedName names) throws IOException {
        Path root = cacheRoot.toAbsolutePath().normalize();
        Path target = root.resolve(ModelCacheLayout.AVATAR_DIRECTORY);
        migrateModelExtensions(root);
        validate(root, target);
        moveFiles(root, root.resolve(relativeDirectory).normalize(), target, names);
        moveFiles(root, target.resolve(relativeDirectory).normalize(), target, names);
        return target;
    }

    /** Rename only recognized model files; collection containers keep their .omc extension. */
    public static synchronized void migrateModelExtensions(Path cacheRoot) throws IOException {
        Path avatars = cacheRoot.toAbsolutePath().normalize().resolve(ModelCacheLayout.AVATAR_DIRECTORY);
        if (RENAMED_ROOTS.contains(avatars)) {
            return;
        }
        validate(cacheRoot.toAbsolutePath().normalize(), avatars);
        Files.createDirectories(avatars);
        try (var files = Files.list(avatars)) {
            for (Path file : files.toList()) {
                String name = file.getFileName().toString();
                if (!Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)
                        || !name.matches("([a-f0-9]{64}|(model|wardrobe)-[a-f0-9]{64}-[a-f0-9]{64}"
                        + "|resource-[a-f0-9]{64}(\\.[a-f0-9-]{36}\\.pending)?)\\.omc")) {
                    continue;
                }
                Path target = avatars.resolve(name.substring(0, name.length() - 4) + ModelCacheLayout.AVATAR_EXTENSION);
                if (!Files.exists(target, LinkOption.NOFOLLOW_LINKS)) {
                    Files.move(file, target);
                }
            }
        }
        RENAMED_ROOTS.add(avatars);
    }

    private static void moveFiles(Path root, Path source, Path target, MigratedName names) throws IOException {
        validate(root, source);
        checkParents(root, target);
        Files.createDirectories(target);
        if (!Files.isDirectory(source, LinkOption.NOFOLLOW_LINKS)) {
            return;
        }
        try (var files = Files.list(source)) {
            for (Path file : files.toList()) {
                if (!Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)) {
                    continue;
                }
                String name = names.name(file);
                if (name == null) {
                    continue;
                }
                Path destination = target.resolve(name).normalize();
                if (!destination.getParent().equals(target)) {
                    throw new IOException("INVALID_AVATAR_CACHE_NAME");
                }
                if (!Files.exists(destination, LinkOption.NOFOLLOW_LINKS)) {
                    Files.move(file, destination);
                }
            }
        }
        try (var remaining = Files.list(source)) {
            if (remaining.findAny().isEmpty()) {
                Files.delete(source);
            }
        }
    }

    /** Matches the formats accepted by the thumbnail decoder, without trusting a URL or MIME suffix. */
    public static String imageExtension(byte[] header) throws IOException {
        byte[] png = {(byte) 0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a};
        if (header.length >= png.length && Arrays.equals(Arrays.copyOf(header, png.length), png)) {
            return ".png";
        }
        if (header.length >= 3 && header[0] == (byte) 0xff && header[1] == (byte) 0xd8 && header[2] == (byte) 0xff) {
            return ".jpg";
        }
        throw new IOException("UNSUPPORTED_THUMBNAIL_FORMAT");
    }

    private static void validate(Path root, Path directory) throws IOException {
        if (!directory.startsWith(root) || directory.equals(root)) {
            throw new IOException("INVALID_AVATAR_CACHE_PATH");
        }
        checkParents(root, directory);
    }

    private static void checkParents(Path root, Path directory) throws IOException {
        for (Path current = directory; current != null && current.startsWith(root); current = current.getParent()) {
            if (Files.isSymbolicLink(current)) {
                throw new IOException("UNSAFE_AVATAR_CACHE");
            }
        }
    }
}
