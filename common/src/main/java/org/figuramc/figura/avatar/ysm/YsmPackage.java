package org.figuramc.figura.avatar.ysm;

import org.figuramc.figura.utils.IOUtils;

import java.io.IOException;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

public class YsmPackage implements AutoCloseable {
    private final Path root;
    private FileSystem zipFileSystem;

    private YsmPackage(Path root) {
        this.root = root;
    }

    public static YsmPackage open(Path root) {
        YsmPackage pkg = new YsmPackage(root);
        pkg.tryOpenZip();
        return pkg;
    }

    private void tryOpenZip() {
        if (Files.isDirectory(root))
            return;
        String name = root.getFileName() != null ? root.getFileName().toString().toLowerCase(java.util.Locale.US) : "";
        if (!name.endsWith(".zip") && !name.endsWith(".ysm"))
            return;
        try {
            zipFileSystem = FileSystems.newFileSystem(root, (ClassLoader) null);
        } catch (IOException ignored) {
        }
    }

    public Path root() {
        return zipFileSystem != null ? zipFileSystem.getPath("/") : root;
    }

    public Path resolve(String relativePath) {
        if (relativePath == null || relativePath.isBlank())
            return root();
        return root().resolve(normalize(relativePath));
    }

    public boolean exists(String relativePath) {
        return Files.exists(resolve(relativePath));
    }

    public boolean isRegularFile(String relativePath) {
        Path path = resolve(relativePath);
        return Files.exists(path) && !Files.isDirectory(path);
    }

    public String readString(String relativePath) throws IOException {
        return IOUtils.readFile(resolve(relativePath));
    }

    public byte[] readBytes(String relativePath) throws IOException {
        return IOUtils.readFileBytes(resolve(relativePath));
    }

    public List<Path> listRootPaths() {
        return IOUtils.listPaths(root());
    }

    public List<Path> listPaths() {
        try (Stream<Path> stream = Files.walk(root())) {
            return stream.sorted().toList();
        } catch (IOException ignored) {
            return List.of();
        }
    }

    public String relativize(Path path) {
        return normalize(root().relativize(path).toString());
    }

    public static String normalize(String relativePath) {
        return relativePath == null ? "" : relativePath.replace('\\', '/');
    }

    @Override
    public void close() {
        if (zipFileSystem != null) {
            try {
                zipFileSystem.close();
            } catch (IOException ignored) {
            }
            zipFileSystem = null;
        }
    }
}
