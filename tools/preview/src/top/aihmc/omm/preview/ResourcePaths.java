package top.aihmc.omm.preview;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/** Filesystem operations used by the reused OMP resource container, without a Minecraft dependency. */
public final class ResourcePaths {
    private ResourcePaths() {}

    public static String getFileNameOrEmpty(Path path) {
        return path == null || path.getFileName() == null ? "" : path.getFileName().toString();
    }

    public static List<Path> listPaths(Path path) {
        try (var children = Files.list(path)) {
            return children.sorted().toList();
        } catch (IOException exception) {
            throw new UncheckedIOException("Cannot list resources: " + path, exception);
        }
    }

    public static String readFile(Path path) throws IOException {
        return Files.readString(path);
    }

    public static byte[] readFileBytes(Path path) throws IOException {
        return Files.readAllBytes(path);
    }

    public static int[] uuidToIntArray(java.util.UUID uuid) {
        long most = uuid.getMostSignificantBits(), least = uuid.getLeastSignificantBits();
        return new int[] {(int) (most >>> 32), (int) most, (int) (least >>> 32), (int) least};
    }
}
