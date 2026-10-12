package org.figuramc.figura.avatar.ysm;

import org.figuramc.figura.utils.IOUtils;

import java.io.IOException;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Stream;

public class YsmPackage implements AutoCloseable {
    private final Path root;
    private FileSystem zipFileSystem;
    private final Map<String, Path> archiveFiles = new LinkedHashMap<>();
    private final Map<String, String> archiveLowerIndex = new LinkedHashMap<>();
    private final List<String> warnings = new ArrayList<>();

    private YsmPackage(Path root) {
        this.root = root;
    }

    public static YsmPackage open(Path root) {
        YsmPackage pkg = new YsmPackage(root);
        pkg.tryOpenZip();
        return pkg;
    }

    private void tryOpenZip() {
        if (root == null || Files.isDirectory(root))
            return;
        String name = root.getFileName() != null ? root.getFileName().toString().toLowerCase(Locale.US) : "";
        if (!name.endsWith(".zip") && !name.endsWith(".ysm"))
            return;
        try {
            zipFileSystem = FileSystems.newFileSystem(root, (ClassLoader) null);
            indexArchive(zipFileSystem.getPath("/"));
        } catch (IOException | RuntimeException e) {
            warnings.add("failed to open archive: " + message(e));
            if (zipFileSystem != null) {
                try {
                    zipFileSystem.close();
                } catch (IOException ignored) {
                }
                zipFileSystem = null;
            }
        }
    }

    public Path root() {
        return root;
    }

    public Path resolve(String relativePath) {
        if (relativePath == null || relativePath.isBlank())
            return root();
        String normalized = normalizeRelative(relativePath);
        if (normalized == null || normalized.isBlank())
            return root().resolve("__invalid_ysm_resource_path__");

        if (!archiveFiles.isEmpty()) {
            Path archivePath = archiveFiles.get(normalized);
            if (archivePath == null) {
                String matched = archiveLowerIndex.get(normalized.toLowerCase(Locale.ROOT));
                archivePath = matched == null ? null : archiveFiles.get(matched);
            }
            return archivePath == null
                    ? zipFileSystem.getPath("/__missing_ysm_resource__").resolve(normalized)
                    : archivePath;
        }

        Path direct = root().resolve(normalized).normalize();
        if (Files.exists(direct))
            return direct;
        Path current = root();
        for (String segment : normalized.split("/")) {
            if (segment.isBlank() || !Files.isDirectory(current))
                return direct;
            try (Stream<Path> children = Files.list(current)) {
                Path matched = children
                        .filter(child -> child.getFileName().toString().equalsIgnoreCase(segment))
                        .findFirst()
                        .orElse(null);
                if (matched == null)
                    return direct;
                current = matched;
            } catch (IOException ignored) {
                return direct;
            }
        }
        return current;
    }

    public boolean exists(String relativePath) {
        String normalized = normalizeRelative(relativePath);
        if (normalized == null || normalized.isBlank())
            return false;
        return Files.exists(resolve(relativePath));
    }

    public boolean isRegularFile(String relativePath) {
        Path path = resolve(relativePath);
        return Files.exists(path) && !Files.isDirectory(path);
    }

    public String readString(String relativePath) throws IOException {
        requireResourcePath(relativePath);
        return IOUtils.readFile(resolve(relativePath));
    }

    public byte[] readBytes(String relativePath) throws IOException {
        requireResourcePath(relativePath);
        return IOUtils.readFileBytes(resolve(relativePath));
    }

    public List<Path> listRootPaths() {
        if (!archiveFiles.isEmpty()) {
            return archiveFiles.entrySet().stream()
                    .filter(entry -> !entry.getKey().contains("/"))
                    .sorted(Map.Entry.comparingByKey())
                    .map(Map.Entry::getValue)
                    .toList();
        }
        return IOUtils.listPaths(root());
    }

    public List<Path> listPaths() {
        if (!archiveFiles.isEmpty())
            return archiveFiles.entrySet().stream().sorted(Map.Entry.comparingByKey()).map(Map.Entry::getValue).toList();
        try (Stream<Path> stream = Files.walk(root())) {
            return stream.sorted().toList();
        } catch (IOException ignored) {
            return List.of();
        }
    }

    public String relativize(Path path) {
        if (!archiveFiles.isEmpty()) {
            for (Map.Entry<String, Path> entry : archiveFiles.entrySet()) {
                if (entry.getValue().equals(path))
                    return entry.getKey();
            }
        }
        return normalize(root().relativize(path).toString());
    }

    public static String normalize(String relativePath) {
        String normalized = normalizeRelative(relativePath);
        return normalized == null ? "" : normalized;
    }

    public List<String> warnings() {
        return List.copyOf(warnings);
    }

    private void indexArchive(Path archiveRoot) throws IOException {
        Map<String, Path> rawFiles = new LinkedHashMap<>();
        try (Stream<Path> stream = Files.walk(archiveRoot)) {
            for (Path path : stream.filter(Files::isRegularFile).toList()) {
                String normalized = normalize(archiveRoot.relativize(path).toString());
                if (!normalized.isBlank())
                    rawFiles.putIfAbsent(normalized, path);
            }
        }

        String rootPrefix = detectArchiveModelRoot(rawFiles.keySet());
        String lowerPrefix = rootPrefix.toLowerCase(Locale.ROOT);
        rawFiles.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(entry -> {
            String normalized = entry.getKey();
            if (!lowerPrefix.isEmpty() && normalized.toLowerCase(Locale.ROOT).startsWith(lowerPrefix))
                normalized = normalized.substring(rootPrefix.length());
            normalized = normalize(normalized);
            if (normalized.isBlank())
                return;
            String lower = normalized.toLowerCase(Locale.ROOT);
            if (archiveFiles.containsKey(normalized) || archiveLowerIndex.containsKey(lower))
                return;
            archiveFiles.put(normalized, entry.getValue());
            archiveLowerIndex.put(lower, normalized);
        });
    }

    private static String detectArchiveModelRoot(Iterable<String> names) {
        Map<String, String> ysmManifests = new LinkedHashMap<>();
        Map<String, String> legacyManifests = new LinkedHashMap<>();
        for (String name : names) {
            String normalized = normalize(name);
            if (normalized.isBlank())
                continue;
            int slash = normalized.lastIndexOf('/');
            String fileName = normalized.substring(slash + 1).toLowerCase(Locale.ROOT);
            if (!fileName.equals("ysm.json") && !fileName.equals("main.json"))
                continue;
            String prefix = slash < 0 ? "" : normalized.substring(0, slash);
            Map<String, String> manifests = fileName.equals("ysm.json") ? ysmManifests : legacyManifests;
            manifests.putIfAbsent(prefix.toLowerCase(Locale.ROOT), prefix);
        }
        Map<String, String> manifests = ysmManifests.isEmpty() ? legacyManifests : ysmManifests;
        if (manifests.size() != 1)
            return "";
        String prefix = manifests.values().iterator().next();
        return prefix.isBlank() ? "" : prefix + "/";
    }

    private static void requireResourcePath(String relativePath) throws IOException {
        String normalized = normalizeRelative(relativePath);
        if (normalized == null || normalized.isBlank())
            throw new IOException("Invalid YSM resource path: " + relativePath);
    }

    private static String normalizeRelative(String relativePath) {
        if (relativePath == null)
            return null;
        String path = relativePath.replace('\\', '/');
        if (path.isBlank() || path.startsWith("/") || path.startsWith("//") || path.matches("^[A-Za-z]:/.*$"))
            return path.isBlank() ? "" : null;

        List<String> segments = new ArrayList<>();
        for (String segment : path.split("/+")) {
            if (segment.isBlank() || segment.equals("."))
                continue;
            if (segment.equals(".."))
                return null;
            segments.add(segment);
        }
        return String.join("/", segments);
    }

    private static String message(Exception exception) {
        return exception.getMessage() == null || exception.getMessage().isBlank()
                ? exception.getClass().getSimpleName()
                : exception.getMessage();
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
        archiveFiles.clear();
        archiveLowerIndex.clear();
    }
}
