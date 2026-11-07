package top.aihmc.omm;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.Map;
import java.util.zip.ZipFile;

/** Reads configured local textures, preserving their resource paths and recording provenance. */
final class MinecraftTextureSources {
    private final Path source;
    private final Path root;
    private final JsonObject manifest = new JsonObject();
    private final JsonObject resources = new JsonObject();
    private final Map<String, byte[]> cache = new HashMap<>();

    MinecraftTextureSources() throws Exception {
        Path output = Path.of(System.getProperty("omm.preview.output"));
        var configuration = RuntimeConfiguration.json(output.resolve("effective-config.json"));
        source = Path.of(RuntimeConfiguration.text(configuration, "textureJar")).toRealPath();
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (var stream = new DigestInputStream(Files.newInputStream(source), digest)) {
            stream.transferTo(java.io.OutputStream.nullOutputStream());
        }
        String sourceHash = HexFormat.of().formatHex(digest.digest());
        root = output.resolve("resources").resolve(sourceHash).toAbsolutePath().normalize();
        Files.createDirectories(root);
        manifest.addProperty("sourceJar", source.toString());
        manifest.addProperty("sourceSha256", sourceHash);
        manifest.add("resources", resources);
    }

    byte[] read(String identifier) throws Exception {
        if (!identifier.startsWith("minecraft:textures/") || !identifier.endsWith(".png")) {
            throw new IllegalArgumentException("Expected a Minecraft texture identifier: " + identifier);
        }
        String entry = "assets/minecraft/" + identifier.substring("minecraft:".length());
        byte[] cached = cache.get(entry);
        if (cached != null) {
            return cached;
        }
        Path target = root.resolve(entry).normalize();
        if (!target.startsWith(root)) {
            throw new IllegalArgumentException("Texture path escapes resource directory: " + identifier);
        }
        byte[] bytes;
        try (var zip = new ZipFile(source.toFile())) {
            bytes = extract(zip, entry, true);
            extract(zip, entry + ".mcmeta", false);
        }
        Path manifestPath = root.resolve("texture-sources.json");
        if (Files.isRegularFile(manifestPath)) {
            var previous = RuntimeConfiguration.json(manifestPath).getAsJsonObject("resources");
            for (var resource : previous.entrySet()) {
                if (!resources.has(resource.getKey())) {
                    resources.add(resource.getKey(), resource.getValue());
                }
            }
        }
        Files.writeString(manifestPath,
            new GsonBuilder().setPrettyPrinting().create().toJson(manifest));
        cache.put(entry, bytes);
        System.out.println("MINECRAFT TEXTURE " + identifier + " source=" + source + " extracted=" + target);
        return bytes;
    }

    private byte[] extract(ZipFile zip, String name, boolean required) throws Exception {
        var entry = zip.getEntry(name);
        if (entry == null) {
            if (required) {
                throw new IllegalArgumentException("Missing texture in configured Minecraft JAR: " + name);
            }
            return null;
        }
        byte[] bytes;
        try (var stream = zip.getInputStream(entry)) {
            bytes = stream.readAllBytes();
        }
        Path target = root.resolve(name).normalize();
        Files.createDirectories(target.getParent());
        Files.write(target, bytes);
        resources.addProperty(name, HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)));
        return bytes;
    }
}
