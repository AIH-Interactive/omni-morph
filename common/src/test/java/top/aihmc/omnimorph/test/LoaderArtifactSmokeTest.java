package top.aihmc.omnimorph.test;

import com.google.gson.JsonParser;

import java.io.ByteArrayInputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipFile;
import java.util.zip.ZipInputStream;

/** Executes the real selected FML discovery components without launching Minecraft or initializing mods. */
public final class LoaderArtifactSmokeTest {
    public static void main(String[] args) throws Exception {
        Path jar = Path.of(args[0]).toAbsolutePath().normalize();
        String coordinate = args[1];
        Class<?> secureJarType = Class.forName("cpw.mods.jarhandling.SecureJar");
        Class<?> fileType = Class.forName("net.minecraftforge.fml.loading.moddiscovery.ModFile");
        String implementation = fileType.getProtectionDomain().getCodeSource().getLocation().toString();
        String expectedLoader = coordinate.startsWith("net.neoforged:")
                ? "loader-47.2.2.jar" : "fmlloader-1.20.1-47.1.3.jar";
        require(implementation.endsWith(expectedLoader), "selected real FML implementation: " + implementation);

        Class<?> bootstrapType = Class.forName("net.minecraftforge.fml.loading.FMLLoader");
        var versionField = bootstrapType.getDeclaredField("versionInfo");
        versionField.setAccessible(true);
        Object previousVersion = versionField.get(null);
        String version = coordinate.substring(coordinate.lastIndexOf(':') + 1);
        int separator = version.indexOf('-');
        Class<?> versionType = Class.forName("net.minecraftforge.fml.loading.VersionInfo");
        Object context = versionType.getConstructor(String.class, String.class, String.class, String.class)
                .newInstance(version.substring(separator + 1), version.substring(0, separator),
                        "20230612.114412", coordinate.substring(0, coordinate.indexOf(':')));
        // The real parser requires this immutable bootstrap context; no launch service or game is started.
        versionField.set(null, context);

        var opened = new ArrayList<FileSystem>();
        try {
            Object archive = secureJarType.getMethod("from", Path[].class)
                    .invoke(null, (Object) new Path[]{jar});
            opened.add(fileSystem(secureJarType, archive));
            Class<?> providerType = Class.forName("net.minecraftforge.forgespi.locating.IModProvider");
            Class<?> locatorType = Class.forName("net.minecraftforge.fml.loading.moddiscovery.ModsFolderLocator");
            var constructor = locatorType.getDeclaredConstructor(Path.class);
            constructor.setAccessible(true);
            // Only scanFile is invoked by compileContent; scanCandidates never scans this directory.
            Object provider = constructor.newInstance(jar.getParent());
            Class<?> parserType = Class.forName("net.minecraftforge.forgespi.locating.ModFileFactory$ModFileInfoParser");
            Class<?> modFileInterface = Class.forName("net.minecraftforge.forgespi.locating.IModFile");
            var parse = Class.forName("net.minecraftforge.fml.loading.moddiscovery.ModFileParser")
                    .getMethod("modsTomlParser", modFileInterface);
            Object parser = Proxy.newProxyInstance(parserType.getClassLoader(), new Class<?>[]{parserType},
                    (proxy, method, values) -> {
                        if (!method.getName().equals("build")) {
                            throw new UnsupportedOperationException(method.getName());
                        }
                        return parse.invoke(null, values[0]);
                    });
            Object modFile = fileType.getConstructor(secureJarType, providerType, parserType, String.class)
                    .newInstance(archive, provider, parser, "MOD");
            List<?> mods = (List<?>) fileType.getMethod("getModInfos").invoke(modFile);
            require(mods.size() == 1, "one declared mod");
            Object mod = mods.get(0);
            require("omni_morph".equals(mod.getClass().getMethod("getModId").invoke(mod)), "native mod ID");
            require("omni-morph".equals(mod.getClass().getMethod("getNamespace").invoke(mod)),
                    "native resource namespace override");
            require(args[2].equals(mod.getClass().getMethod("getVersion").invoke(mod).toString()),
                    "native expanded version");

            Object scan = fileType.getMethod("compileContent").invoke(modFile);
            Set<?> annotations = (Set<?>) scan.getClass().getMethod("getAnnotations").invoke(scan);
            int entrypoints = 0;
            for (Object annotation : annotations) {
                String type = annotation.getClass().getMethod("annotationType").invoke(annotation).toString();
                if (type.equals("Lnet/minecraftforge/fml/common/Mod;")) {
                    Map<?, ?> values = (Map<?, ?>) annotation.getClass().getMethod("annotationData").invoke(annotation);
                    require("omni_morph".equals(values.get("value")), "native @Mod discovery agrees with TOML");
                    entrypoints++;
                }
            }
            require(entrypoints == 1, "one actual FML-discovered entrypoint");

            int expectedDependencies;
            try (var zip = new ZipFile(jar.toFile());
                 var reader = new InputStreamReader(zip.getInputStream(zip.getEntry("META-INF/jarjar/metadata.json")),
                         StandardCharsets.UTF_8)) {
                expectedDependencies = 0;
                for (var declaration : JsonParser.parseReader(reader).getAsJsonObject().getAsJsonArray("jars")) {
                    String path = declaration.getAsJsonObject().get("path").getAsString();
                    try (var stream = zip.getInputStream(zip.getEntry(path))) {
                        expectedDependencies += 1 + nestedDeclarations(stream.readAllBytes());
                    }
                }
            }
            require(expectedDependencies > 0, "declared runtime dependencies");
            Class<?> dependenciesType = Class.forName("net.minecraftforge.fml.loading.moddiscovery.JarInJarDependencyLocator");
            Object locator = dependenciesType.getConstructor().newInstance();
            List<?> dependencies = (List<?>) dependenciesType.getMethod("scanMods", Iterable.class)
                    .invoke(locator, List.of(modFile));
            for (Object dependency : dependencies) {
                Object nested = fileType.getMethod("getSecureJar").invoke(dependency);
                opened.add(fileSystem(secureJarType, nested));
            }
            require(dependencies.size() == expectedDependencies, "real Jar-in-Jar discovery consumes every declaration");
            System.out.println("PASS real selected FML ModFile/ModInfo, @Mod class scan and "
                    + dependencies.size() + " Jar-in-Jar dependencies: " + coordinate);
            System.out.println("Not covered: mod initialization, language loading, Mixin transformation or the game client");
        } finally {
            versionField.set(null, previousVersion);
            for (int index = opened.size() - 1; index >= 0; index--) {
                FileSystem fileSystem = opened.get(index);
                if (fileSystem != FileSystems.getDefault() && fileSystem.isOpen()) {
                    fileSystem.close();
                }
            }
        }
    }

    private static FileSystem fileSystem(Class<?> secureJarType, Object archive) throws Exception {
        return ((Path) secureJarType.getMethod("getRootPath").invoke(archive)).getFileSystem();
    }

    private static int nestedDeclarations(byte[] archive) throws Exception {
        Map<String, byte[]> entries = new HashMap<>();
        try (var zip = new ZipInputStream(new ByteArrayInputStream(archive))) {
            for (var entry = zip.getNextEntry(); entry != null; entry = zip.getNextEntry()) {
                if (entry.getName().equals("META-INF/jarjar/metadata.json") || entry.getName().endsWith(".jar")) {
                    entries.put(entry.getName(), zip.readAllBytes());
                }
            }
        }
        byte[] metadata = entries.get("META-INF/jarjar/metadata.json");
        if (metadata == null) {
            return 0;
        }
        int count = 0;
        var declarations = JsonParser.parseString(new String(metadata, StandardCharsets.UTF_8))
                .getAsJsonObject().getAsJsonArray("jars");
        for (var declaration : declarations) {
            String path = declaration.getAsJsonObject().get("path").getAsString();
            byte[] nested = entries.get(path);
            require(nested != null, "declared transitive Jar-in-Jar exists: " + path);
            count += 1 + nestedDeclarations(nested);
        }
        return count;
    }

    private static void require(boolean valid, String message) {
        if (!valid) {
            throw new AssertionError(message);
        }
    }
}
