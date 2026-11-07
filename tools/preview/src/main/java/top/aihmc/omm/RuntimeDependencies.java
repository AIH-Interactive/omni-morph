package top.aihmc.omm;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipInputStream;

/** Resolves the installed version manifest and original MOD nested dependencies. */
final class RuntimeDependencies {
    private static final Map<String, String> MODS = Map.of(
        "omp", "omni-morph-1.0.0-alpha.7+26.1.2-fabric-mc.jar",
        "spm", "sparkle-morpher-2.3.2-fa26.1.x.jar",
        "oysm", "openysm-forge-2.6.6.6.jar"
    );

    private RuntimeDependencies() {
    }

    static String prepare(String backend, JsonObject settings, Path output) throws Exception {
        Path version = Path.of(RuntimeConfiguration.text(settings,
            backend.equals("oysm") ? "forgeVersionDirectory" : "fabricVersionDirectory"));
        JsonObject info = RuntimeConfiguration.json(version.resolve(version.getFileName() + ".json"));
        Path libraries = Path.of(RuntimeConfiguration.text(settings, "librariesDirectory"));
        Path mod = Path.of(RuntimeConfiguration.text(settings, "modDirectory")).resolve(MODS.get(backend));
        List<Path> paths = new ArrayList<>();
        paths.add(mod);
        nested(Files.readAllBytes(mod), output.resolve("nested"), paths);
        if (backend.equals("oysm")) {
            JsonArray arguments = info.getAsJsonObject("arguments").getAsJsonArray("game");
            String forge = null;
            for (int index = 0; index + 1 < arguments.size(); index++) {
                if (arguments.get(index).isJsonPrimitive()
                    && arguments.get(index).getAsString().equals("--fml.forgeVersion")) {
                    forge = arguments.get(index + 1).getAsString();
                    break;
                }
            }
            if (forge == null) {
                throw new IllegalArgumentException("Forge version is missing from the installed manifest");
            }
            String artifact = "forge-1.20.1-" + forge;
            Path forgeDirectory = libraries.resolve("net/minecraftforge/forge/1.20.1-" + forge);
            paths.add(forgeDirectory.resolve(artifact + "-client.jar"));
            try (var files = Files.walk(libraries.resolve("net/minecraft/client"), 2)) {
                List<Path> srg = files.filter(path -> path.getFileName().toString().endsWith("-srg.jar"))
                    .filter(path -> path.getParent().getFileName().toString().startsWith("1.20.1-"))
                    .toList();
                if (srg.size() != 1) {
                    throw new IllegalArgumentException("Expected one installed Minecraft 1.20.1 SRG JAR: " + srg);
                }
                paths.addAll(srg);
            }
            paths.add(forgeDirectory.resolve(artifact + "-universal.jar"));
        } else {
            paths.add(version.resolve(version.getFileName() + ".jar"));
        }
        for (var element : info.getAsJsonArray("libraries")) {
            JsonObject library = element.getAsJsonObject();
            if (enabled(library)) {
                paths.add(libraries.resolve(artifactPath(library)));
            }
        }
        var unique = new LinkedHashSet<Path>();
        for (Path path : paths) {
            if (!Files.isRegularFile(path)) {
                throw new IllegalArgumentException("Missing installed dependency: " + path);
            }
            unique.add(path.toRealPath());
        }
        JsonArray records = new JsonArray();
        for (Path path : unique) {
            JsonObject record = new JsonObject();
            record.addProperty("path", path.toString());
            record.addProperty("sha256", hash(Files.readAllBytes(path)));
            records.add(record);
        }
        Files.createDirectories(output);
        Files.writeString(output.resolve("dependencies.json"), new GsonBuilder().setPrettyPrinting().create().toJson(records));
        String classpath = String.join(File.pathSeparator, unique.stream().map(Path::toString).toList());
        Files.writeString(output.resolve("classpath.txt"), classpath);
        System.out.println("Prepared " + backend + ": " + unique.size() + " original JAR dependencies");
        return classpath;
    }

    private static boolean enabled(JsonObject library) {
        if (!library.has("rules")) {
            return true;
        }
        boolean allowed = false;
        for (var element : library.getAsJsonArray("rules")) {
            JsonObject rule = element.getAsJsonObject();
            if (rule.has("features")) {
                continue;
            }
            if (rule.has("os")) {
                JsonObject os = rule.getAsJsonObject("os");
                if (os.has("name") && !os.get("name").getAsString().equals("windows")) {
                    continue;
                }
                if (os.has("arch") && !List.of("amd64", "x86_64").contains(os.get("arch").getAsString())) {
                    continue;
                }
                if (os.has("version") && !java.util.regex.Pattern.compile(os.get("version").getAsString())
                    .matcher(System.getProperty("os.version")).find()) {
                    continue;
                }
            }
            allowed = rule.get("action").getAsString().equals("allow");
        }
        return allowed;
    }

    private static String artifactPath(JsonObject library) {
        if (library.has("downloads")) {
            JsonObject downloads = library.getAsJsonObject("downloads");
            if (downloads.has("artifact")) {
                return downloads.getAsJsonObject("artifact").get("path").getAsString();
            }
        }
        String[] coordinate = library.get("name").getAsString().split(":");
        String suffix = coordinate.length > 3 ? "-" + coordinate[3] : "";
        return coordinate[0].replace('.', '/') + "/" + coordinate[1] + "/" + coordinate[2]
            + "/" + coordinate[1] + "-" + coordinate[2] + suffix + ".jar";
    }

    private static void nested(byte[] data, Path directory, List<Path> paths) throws Exception {
        try (var zip = new ZipInputStream(new ByteArrayInputStream(data))) {
            for (var entry = zip.getNextEntry(); entry != null; entry = zip.getNextEntry()) {
                if (!entry.getName().endsWith(".jar")) {
                    continue;
                }
                byte[] binary = zip.readAllBytes();
                Path target = directory.resolve(hash(binary)).resolve(Path.of(entry.getName()).getFileName());
                Files.createDirectories(target.getParent());
                Files.write(target, binary);
                paths.add(target);
                nested(binary, directory, paths);
            }
        }
    }

    private static String hash(byte[] bytes) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    }
}
