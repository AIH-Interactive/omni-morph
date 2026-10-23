package top.aihmc.omnimorph.model.outfit;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/** Small device-local configuration documents. Models and textures remain in their existing source/cache stores. */
public final class LocalOutfitPresets {
    public record Preset(UUID id, long revision, String name, OutfitConfig configuration) {}
    public record Item(UUID id, long revision, String name, String error) {}

    private static final int MAX_FILE_BYTES = OutfitConfig.MAX_JSON_BYTES + 2048;
    private static final int MAX_FILES = 64;
    private final Path directory;

    public LocalOutfitPresets(Path dataRoot) {
        directory = dataRoot.toAbsolutePath().normalize().resolve("outfit-presets");
    }

    public synchronized List<Item> list(OutfitLimits limits) throws IOException {
        var items = new ArrayList<Item>();
        for (Path file : files()) {
            UUID id = id(file);
            try {
                Preset preset = read(id, limits);
                items.add(new Item(id, preset.revision(), preset.name(), ""));
            } catch (IOException invalid) {
                items.add(new Item(id, 0, id.toString(), invalid.getMessage()));
            }
        }
        items.sort(Comparator.comparing(Item::name, String.CASE_INSENSITIVE_ORDER).thenComparing(Item::id));
        return List.copyOf(items);
    }

    public synchronized Preset read(UUID id, OutfitLimits limits) throws IOException {
        checkDirectory();
        Path file = file(id);
        if (!Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("OUTFIT_PRESET_MISSING");
        }
        if (Files.size(file) > MAX_FILE_BYTES) {
            throw new IOException("OUTFIT_PRESET_FILE_LIMIT");
        }
        try {
            byte[] bytes;
            try (var input = Files.newInputStream(file)) {
                bytes = input.readNBytes(MAX_FILE_BYTES + 1);
            }
            if (bytes.length > MAX_FILE_BYTES) {
                throw new IOException("OUTFIT_PRESET_FILE_LIMIT");
            }
            JsonObject document = JsonParser.parseString(new String(bytes, StandardCharsets.UTF_8)).getAsJsonObject();
            if (document.get("schema_version").getAsInt() != 1
                    || !UUID.fromString(document.get("preset_id").getAsString()).equals(id)) {
                throw new IOException("INVALID_OUTFIT_PRESET");
            }
            long revision = document.get("revision").getAsLong();
            if (revision < 1) {
                throw new IOException("INVALID_OUTFIT_PRESET");
            }
            String name = name(document.get("name").getAsString());
            OutfitConfig configuration = OutfitCodec.decode(document.get("configuration").toString(), limits);
            return new Preset(id, revision, name, configuration);
        } catch (RuntimeException invalid) {
            throw new IOException("INVALID_OUTFIT_PRESET", invalid);
        }
    }

    /** expectedRevision=0 creates a new preset. A conflicting save never overwrites the existing document. */
    public synchronized Preset save(UUID id, long expectedRevision, String name,
                                     OutfitConfig configuration, OutfitLimits limits) throws IOException {
        checkDirectory();
        name = name(name);
        String configurationJson = OutfitCodec.encode(configuration, limits);
        if (expectedRevision < 0 || expectedRevision == Long.MAX_VALUE) {
            throw new IOException("OUTFIT_PRESET_REVISION_CONFLICT");
        }
        List<Path> existing = files();
        boolean present = Files.exists(file(id), LinkOption.NOFOLLOW_LINKS);
        long revision = present ? read(id, limits).revision() : 0;
        if (revision != expectedRevision) {
            throw new IOException("OUTFIT_PRESET_REVISION_CONFLICT");
        }
        if (limits.maxPresets() == 0 || existing.size() + (present ? 0 : 1) > limits.maxPresets()) {
            throw new IOException("OUTFIT_PRESET_COUNT_LIMIT");
        }
        var document = new JsonObject();
        document.addProperty("schema_version", 1);
        document.addProperty("preset_id", id.toString());
        document.addProperty("revision", revision + 1);
        document.addProperty("name", name);
        document.add("configuration", JsonParser.parseString(configurationJson));
        byte[] bytes = document.toString().getBytes(StandardCharsets.UTF_8);
        if (bytes.length > MAX_FILE_BYTES) {
            throw new IOException("OUTFIT_PRESET_FILE_LIMIT");
        }
        Files.createDirectories(directory);
        Path temporary = Files.createTempFile(directory, "preset-", ".part");
        try {
            Files.write(temporary, bytes);
            Files.move(temporary, file(id), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } finally {
            Files.deleteIfExists(temporary);
        }
        return new Preset(id, revision + 1, name, configuration);
    }

    private List<Path> files() throws IOException {
        checkDirectory();
        if (!Files.exists(directory)) {
            return List.of();
        }
        try (var entries = Files.list(directory)) {
            var files = new ArrayList<Path>();
            int count = 0;
            var iterator = entries.iterator();
            while (iterator.hasNext()) {
                Path entry = iterator.next();
                if (++count > 1024) {
                    throw new IOException("OUTFIT_PRESET_DIRECTORY_LIMIT");
                }
                if (id(entry) != null) {
                    files.add(entry);
                    if (files.size() > MAX_FILES) {
                        throw new IOException("OUTFIT_PRESET_COUNT_LIMIT");
                    }
                }
            }
            return List.copyOf(files);
        }
    }

    private void checkDirectory() throws IOException {
        if (Files.isSymbolicLink(directory) || Files.exists(directory) && !Files.isDirectory(directory)) {
            throw new IOException("OUTFIT_PRESET_DIRECTORY_UNAVAILABLE");
        }
    }

    private Path file(UUID id) {
        return directory.resolve(java.util.Objects.requireNonNull(id) + ".json");
    }

    private static UUID id(Path file) {
        String name = file.getFileName().toString();
        if (!name.endsWith(".json")) {
            return null;
        }
        try {
            UUID id = UUID.fromString(name.substring(0, name.length() - 5));
            return name.equals(id + ".json") ? id : null;
        } catch (IllegalArgumentException invalid) {
            return null;
        }
    }

    private static String name(String value) throws IOException {
        if (value == null || value.isBlank() || value.length() > 80 || value.indexOf('\0') >= 0) {
            throw new IOException("INVALID_OUTFIT_PRESET_NAME");
        }
        return value.strip();
    }
}
