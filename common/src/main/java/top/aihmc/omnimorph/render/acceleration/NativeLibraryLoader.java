package top.aihmc.omnimorph.render.acceleration;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Locale;

/** Loads only a matching, hash-verified JAR resource. No library search path or model input is used. */
public final class NativeLibraryLoader {
    public record Loaded(String platform, String sha256, int capabilities) {
    }

    private static Loaded loaded;

    private NativeLibraryLoader() {
    }

    public static synchronized Loaded load() throws IOException {
        return load(NativeLibraryLoader.class.getClassLoader(),
                Path.of(System.getProperty("user.home"), ".omnimorph", "native-cache"));
    }

    static synchronized Loaded load(ClassLoader resources, Path cache) throws IOException {
        if (loaded != null) {
            return loaded;
        }
        String platform = currentPlatform();
        String libraryName = platform.startsWith("windows-") ? "omnimorph_accel.dll"
                : platform.startsWith("macos-") ? "libomnimorph_accel.dylib" : "libomnimorph_accel.so";
        String expectedPath = "natives/" + platform + "/" + libraryName;
        JsonObject manifest;
        try {
            manifest = JsonParser.parseString(new String(readResource(resources, "natives/manifest.json", 262_144),
                    StandardCharsets.UTF_8).replaceFirst("^\\uFEFF", "")).getAsJsonObject();
        } catch (RuntimeException failure) {
            throw new IOException("Invalid native manifest", failure);
        }
        JsonObject entry = validateManifest(manifest, platform, expectedPath);
        String hash = entry.get("sha256").getAsString();
        byte[] library = readResource(resources, expectedPath, 32 * 1024 * 1024);
        if (!sha256(library).equals(hash)) {
            throw new IOException("Native resource hash mismatch");
        }
        Path directory = cache.toAbsolutePath().resolve("1.0.0-alpha.1").resolve("abi-" + NativeBindings.ABI).resolve(hash);
        rejectSymbolicParents(directory);
        Files.createDirectories(directory);
        rejectSymbolicParents(directory);
        Path target = directory.resolve(libraryName);
        if (!Files.exists(target, LinkOption.NOFOLLOW_LINKS)) {
            Path temporary = Files.createTempFile(directory, "extract-", ".tmp");
            try {
                Files.write(temporary, library);
                try {
                    Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE);
                } catch (AtomicMoveNotSupportedException failure) {
                    throw new IOException("Native cache does not support atomic extraction", failure);
                }
            } finally {
                Files.deleteIfExists(temporary);
            }
        }
        if (Files.isSymbolicLink(target) || !Files.isRegularFile(target, LinkOption.NOFOLLOW_LINKS)
                || Files.size(target) != library.length || !sha256(Files.readAllBytes(target)).equals(hash)) {
            throw new IOException("Native cache hash/type mismatch");
        }
        System.load(target.toString());
        if (NativeBindings.handshake(NativeBindings.ABI) != NativeBindings.ABI) {
            throw new IOException("Native ABI handshake rejected");
        }
        loaded = new Loaded(platform, hash, NativeBindings.capabilities());
        return loaded;
    }

    static JsonObject validateManifest(JsonObject manifest, String platform, String expectedPath) throws IOException {
        try {
            if (manifest.get("abi").getAsInt() != NativeBindings.ABI) {
                throw new IOException("Native manifest ABI mismatch");
            }
            JsonObject found = null;
            for (var element : manifest.getAsJsonArray("libraries")) {
                JsonObject entry = element.getAsJsonObject();
                if (!platform.equals(entry.get("platform").getAsString())) {
                    continue;
                }
                if (found != null) {
                    throw new IOException("Duplicate native platform");
                }
                if (!expectedPath.equals(entry.get("path").getAsString())
                        || entry.get("abi").getAsInt() != NativeBindings.ABI
                        || !entry.get("sha256").getAsString().matches("[0-9a-f]{64}")
                        || !entry.get("sourceCommit").getAsString().matches("[0-9a-f]{40}")
                        || !entry.get("sourceTreeSha256").getAsString().matches("[0-9a-f]{64}")
                        || !platform.endsWith("-" + entry.get("architecture").getAsString())
                        || entry.get("toolchain").getAsString().isBlank()
                        || entry.get("minimumRuntime").getAsString().isBlank()
                        || entry.getAsJsonArray("dependencies") == null) {
                    throw new IOException("Invalid native platform metadata");
                }
                found = entry;
            }
            if (found == null) {
                throw new IOException("Native library missing for process platform: " + platform);
            }
            return found;
        } catch (RuntimeException failure) {
            throw new IOException("Malformed native manifest fields", failure);
        }
    }

    static String currentPlatform() throws IOException {
        String os = System.getProperty("os.name").toLowerCase(Locale.ROOT);
        String arch = System.getProperty("os.arch").toLowerCase(Locale.ROOT);
        String architecture = switch (arch) {
            case "amd64", "x86_64" -> "x86_64";
            case "aarch64", "arm64" -> "aarch64";
            default -> throw new IOException("Unsupported process architecture: " + arch);
        };
        String system = os.contains("win") ? "windows" : os.contains("mac") ? "macos"
                : os.contains("linux") ? "linux" : "unsupported";
        if (system.equals("unsupported") || (!system.equals("macos") && architecture.equals("aarch64"))) {
            throw new IOException("Unsupported native process platform: " + system + "-" + architecture);
        }
        return system + "-" + architecture;
    }

    private static byte[] readResource(ClassLoader resources, String path, int limit) throws IOException {
        try (InputStream stream = resources.getResourceAsStream(path)) {
            if (stream == null) {
                throw new IOException("Missing packaged resource: " + path);
            }
            byte[] bytes = stream.readNBytes(limit + 1);
            if (bytes.length > limit) {
                throw new IOException("Packaged native resource exceeds limit");
            }
            return bytes;
        }
    }

    private static String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException failure) {
            throw new IllegalStateException(failure);
        }
    }

    private static void rejectSymbolicParents(Path path) throws IOException {
        for (Path current = path; current != null; current = current.getParent()) {
            if (Files.isSymbolicLink(current)) {
                throw new IOException("Native cache must not contain symbolic links");
            }
        }
    }
}
