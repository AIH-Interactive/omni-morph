package top.aihmc.omnimorph.model.cache;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/** Model cache storage is selected by resource purpose, independently of the source format. */
public final class ModelCacheLayout {
    public static final String AVATAR_DIRECTORY = "avatars";
    public static final String AVATAR_EXTENSION = ".oma";
    public static final String COLLECTION_EXTENSION = ".omc";
    public static final String COSMETIC_DIRECTORY = "cosmetic";
    public static final String COSMETIC_EXTENSION = ".oms";

    private ModelCacheLayout() {
    }

    public static String extension(String resourceKind) {
        return "ACCESSORY".equals(resourceKind) ? COSMETIC_EXTENSION : AVATAR_EXTENSION;
    }

    /** Creates cosmetic storage only on demand; it never resolves the user's cosmetics directory. */
    public static Path cosmeticDirectory(Path cacheRoot) throws IOException {
        Path root = cacheRoot.toAbsolutePath().normalize();
        Path directory = root.resolve(COSMETIC_DIRECTORY);
        for (Path current = directory; current != null; current = current.getParent()) {
            if (Files.isSymbolicLink(current)) {
                throw new IOException("UNSAFE_COSMETIC_CACHE");
            }
        }
        Files.createDirectories(directory);
        return directory;
    }
}
