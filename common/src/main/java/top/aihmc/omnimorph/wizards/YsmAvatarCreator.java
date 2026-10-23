package top.aihmc.omnimorph.wizards;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import top.aihmc.omnimorph.avatar.ysm.YsmModelData;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;

/** Copies the bundled, frozen template on a model worker; never equips or publishes it. */
public final class YsmAvatarCreator {
    private static final String RESOURCES = "/assets/omni-morph/";

    @FunctionalInterface
    interface Resources {
        InputStream open(String path) throws IOException;
    }

    private YsmAvatarCreator() {
    }

    public static String directoryName(String name) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("AVATAR_NAME_REQUIRED");
        }
        String result = name.strip().replaceAll("[\\\\/:*?\"<>|\\p{Cntrl}]", "_")
                .replaceAll("[. ]+$", "");
        if (result.isEmpty() || result.equals(".")) {
            throw new IllegalArgumentException("AVATAR_NAME_INVALID");
        }
        String stem = result.split("\\.", 2)[0].toUpperCase(Locale.ROOT);
        if (stem.matches("CON|PRN|AUX|NUL|COM[1-9]|LPT[1-9]")) {
            result = "_" + result;
        }
        return result;
    }

    public static Path create(Path root, String name, String authors, String description) throws IOException {
        return create(root, name, authors, description, YsmAvatarCreator.class::getResourceAsStream);
    }

    static Path create(Path root, String name, String authors, String description, Resources resources)
            throws IOException {
        String filename = directoryName(name);
        Path directory = root.toAbsolutePath().normalize();
        Files.createDirectories(directory);
        // The avatar inventory recursively scans its root, including dot directories.
        // Stage beside that root so no watcher can expose an unvalidated package.
        Path folder = Files.createTempDirectory(directory.getParent(), ".omnimorph-ysm-");

        List<Path> created = new ArrayList<>();
        created.add(folder);
        try {
            JsonObject manifest = JsonParser.parseString(new String(read(resources,
                    RESOURCES + "wizard/ysm-template-manifest.json"), StandardCharsets.UTF_8)).getAsJsonObject();
            for (var value : manifest.getAsJsonArray("files")) {
                JsonObject entry = value.getAsJsonObject();
                String relative = entry.get("path").getAsString();
                Path target = folder.resolve(relative).normalize();
                if (!target.startsWith(folder) || target.equals(folder)) {
                    throw new IOException("YSM_TEMPLATE_PATH_INVALID");
                }
                byte[] bytes = read(resources, RESOURCES + "ysm_default/" + relative);
                if (bytes.length != entry.get("bytes").getAsInt()
                        || !sha256(bytes).equals(entry.get("sha256").getAsString())) {
                    throw new IOException("YSM_TEMPLATE_HASH_MISMATCH: " + relative);
                }
                createParents(folder, target.getParent(), created);
                Files.createFile(target);
                created.add(target);
                Files.write(target, bytes);
            }

            Path metadataFile = folder.resolve("ysm.json");
            JsonObject document = JsonParser.parseString(Files.readString(metadataFile)).getAsJsonObject();
            JsonObject metadata = document.getAsJsonObject("metadata");
            metadata.addProperty("name", name.strip());
            metadata.addProperty("tips", description == null ? "" : description);
            // Keep the original authors, roles, contact details and license as template provenance.
            if (authors != null && !authors.isBlank()) {
                var credits = metadata.getAsJsonArray("authors");
                for (String author : authors.split(",")) {
                    if (!author.isBlank()) {
                        JsonObject credit = new JsonObject();
                        credit.addProperty("name", author.strip());
                        credits.add(credit);
                    }
                }
            }
            Files.writeString(metadataFile, new GsonBuilder().disableHtmlEscaping().setPrettyPrinting()
                    .create().toJson(document));
            try (YsmModelData ignored = YsmModelData.read(folder, () -> false)) {
                // Parse the actual directory package before returning it to the inventory.
            }
            for (int suffix = 0; ; suffix++) {
                Path candidate = directory.resolve(filename + (suffix == 0 ? "" : "_" + suffix));
                try {
                    // Same filesystem, no REPLACE_EXISTING: never overwrite another creator/user.
                    return Files.move(folder, candidate);
                } catch (FileAlreadyExistsException conflict) {
                    // Retry only the final name; the already validated package stays private.
                }
            }
        } catch (IOException | RuntimeException failure) {
            // Delete only paths created by this attempt, never recurse into an existing avatar.
            for (int i = created.size() - 1; i >= 0; i--) {
                try {
                    Files.deleteIfExists(created.get(i));
                } catch (IOException cleanup) {
                    failure.addSuppressed(cleanup);
                }
            }
            throw failure;
        }
    }

    private static void createParents(Path root, Path parent, List<Path> created) throws IOException {
        if (parent.equals(root) || Files.isDirectory(parent)) {
            return;
        }
        createParents(root, parent.getParent(), created);
        Files.createDirectory(parent);
        created.add(parent);
    }

    private static byte[] read(Resources resources, String path) throws IOException {
        try (InputStream stream = resources.open(path)) {
            if (stream == null) {
                throw new IOException("YSM_TEMPLATE_RESOURCE_MISSING: " + path);
            }
            return stream.readAllBytes();
        }
    }

    private static String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException impossible) {
            throw new AssertionError(impossible);
        }
    }
}
