package top.aihmc.omnimorph.model.library;

import org.figuramc.figura.FiguraMod;
import org.figuramc.figura.config.Configs;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Locale;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/** Exports one local avatar or cosmetic source as a portable archive. */
public final class LocalSourceExport {
    private static final int MAX_ENTRIES = 16384;
    private static final Set<String> INVALID_NAME = Set.of("<", ">", ":", "\"", "/", "\\", "|", "?", "*");

    private LocalSourceExport() {
    }

    /** The MISC config value is resolved relative to the OmniMorph data directory. */
    public static Path directory() {
        String configured = Configs.EXPORT_DIRECTORY.value;
        Path path = Path.of(configured == null || configured.isBlank() ? "export" : configured);
        if (!path.isAbsolute()) {
            path = FiguraMod.getFiguraDirectory().resolve(path);
        }
        return path.toAbsolutePath().normalize();
    }

    public static Path export(Path source, String requestedName, String kind) throws IOException {
        return export(source, requestedName, kind, directory());
    }

    public static Path export(Path source, String requestedName, String kind, Path output) throws IOException {
        if (source == null || Files.isSymbolicLink(source) || !Files.exists(source)) {
            throw new IOException("LOCAL_SOURCE_MISSING");
        }
        Path root = source.toAbsolutePath().normalize();
        output = output.toAbsolutePath().normalize();
        Files.createDirectories(output);

        boolean directory = Files.isDirectory(root);
        boolean zipFile = !directory && root.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".zip");
        String requested = requestedName == null || requestedName.isBlank()
                ? root.getFileName().toString() : requestedName;
        String base = sanitize((kind == null || kind.isBlank() ? "" : kind + "_") + requested)
                .replaceAll("(?i)\\.(zip|ysm)$", "");
        String extension = directory || zipFile ? ".zip" : extensionOf(root);
        Path target = uniqueTarget(output, base, extension);
        Path temporary = Files.createTempFile("omnimorph-export-", ".part");
        try {
            if (directory) {
                zipDirectory(root, temporary);
            } else {
                Files.copy(root, temporary, StandardCopyOption.REPLACE_EXISTING);
            }
            try {
                Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException unsupported) {
                Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
            }
            return target;
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    private static void zipDirectory(Path root, Path temporary) throws IOException {
        try (var zip = new ZipOutputStream(Files.newOutputStream(temporary));
             var walk = Files.walk(root)) {
            int entries = 0;
            var iterator = walk.iterator();
            while (iterator.hasNext()) {
                Path path = iterator.next();
                if (path.equals(root) || Files.isDirectory(path) || Files.isSymbolicLink(path)) {
                    continue;
                }
                if (!path.startsWith(root) || ++entries > MAX_ENTRIES) {
                    throw new IOException("LOCAL_SOURCE_EXPORT_LIMIT");
                }
                String name = root.relativize(path).toString().replace('\\', '/');
                if (name.isBlank() || name.startsWith("/") || name.contains("../")) {
                    throw new IOException("LOCAL_SOURCE_EXPORT_PATH");
                }
                zip.putNextEntry(new ZipEntry(name));
                Files.copy(path, zip);
                zip.closeEntry();
            }
        } catch (UncheckedIOException failure) {
            throw failure.getCause();
        }
    }

    private static Path uniqueTarget(Path output, String base, String extension) {
        Path candidate = output.resolve(base + extension);
        int suffix = 2;
        while (Files.exists(candidate)) {
            candidate = output.resolve(base + " (" + suffix++ + ")" + extension);
        }
        return candidate;
    }

    private static String sanitize(String value) {
        StringBuilder name = new StringBuilder();
        for (int index = 0; index < value.length() && name.length() < 80; index++) {
            String character = String.valueOf(value.charAt(index));
            if (Character.isISOControl(value.charAt(index)) || INVALID_NAME.contains(character)) {
                name.append('_');
            } else {
                name.append(value.charAt(index));
            }
        }
        String result = name.toString().strip();
        while (result.endsWith(".") || result.endsWith(" ")) {
            result = result.substring(0, result.length() - 1);
        }
        return result.isBlank() ? "local-source" : result;
    }

    private static String extensionOf(Path source) {
        String name = source.getFileName().toString();
        int dot = name.lastIndexOf('.');
        return dot <= 0 ? ".zip" : name.substring(dot);
    }
}
