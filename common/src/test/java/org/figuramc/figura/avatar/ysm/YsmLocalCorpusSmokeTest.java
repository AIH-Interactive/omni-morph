package org.figuramc.figura.avatar.ysm;

import org.figuramc.figura.avatar.local.LocalAvatarFetcher;
import org.figuramc.figura.avatar.local.LocalAvatarLoader;

import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.UUID;

/** Optional local corpus; user model files are read only and never committed. */
public final class YsmLocalCorpusSmokeTest {
    private YsmLocalCorpusSmokeTest() {
    }

    public static void main(String[] args) throws Exception {
        Path root = Path.of(args[0]);
        Path environment = Files.createTempDirectory("figura-corpus-environment-");
        System.setProperty("figura.test.directory", environment.toString());
        int figura = 0;
        int ysm = 0;
        int encrypted = 0;
        try (var entries = Files.list(root)) {
            for (Path path : entries.sorted().toList()) {
                if (Files.isDirectory(path)) {
                    if (LocalAvatarFetcher.isAvatar(path)) {
                        compileFigura(path);
                        figura++;
                    } else if (YsmAvatarDetector.isYsmAvatar(path)) {
                        try (YsmPackage source = YsmPackage.open(path);
                             YsmModelData data = YsmModelData.read(source,
                                     YsmSourceIdentity.ContainerKind.DIRECTORY, () -> false)) {
                            var nbt = YsmAvatarLoader.toAvatarNbt(data, null, new UUID(0, 1), path);
                            require(nbt.contains("ysm") && !data.mainGeometry().bones.isEmpty(), "YSM formal data assembly");
                        }
                        ysm++;
                        System.out.println("YSM_DATA_OK " + path.getFileName());
                    }
                } else if (path.getFileName().toString().toLowerCase(java.util.Locale.ROOT).endsWith(".zip")) {
                    try (var archive = FileSystems.newFileSystem(path, (ClassLoader) null);
                         var paths = Files.walk(archive.getPath("/"))) {
                        for (Path metadata : paths.filter(file -> file.getFileName() != null
                                && file.getFileName().toString().equals("avatar.json")).toList()) {
                            compileFigura(metadata.getParent());
                            figura++;
                        }
                    }
                } else if (path.getFileName().toString().toLowerCase(java.util.Locale.ROOT).endsWith(".ysm")) {
                    var report = org.figuramc.figura.model.ysm.YsmModelScanner.scanOne(path);
                    require(report.errors().stream().anyMatch(error -> error.startsWith("UNSUPPORTED_VERSION:")),
                            "M1 encrypted input must be explicit unsupported rather than silently loaded");
                    encrypted++;
                }
            }
            require(figura > 0 && ysm > 0 && encrypted > 0, "all user-requested corpus categories are present");
            System.out.printf("Local corpus: Figura compiled=%d YSM data assembled=%d encrypted pending M2=%d%n",
                    figura, ysm, encrypted);
        } finally {
            try (var files = Files.walk(environment)) {
                for (Path path : files.sorted(Comparator.reverseOrder()).toList()) {
                    Files.delete(path);
                }
            }
        }
    }

    private static void compileFigura(Path path) throws Exception {
        require(LocalAvatarFetcher.isLoadableAvatar(path), "Figura wardrobe discovery");
        require(!YsmAvatarDetector.isYsmAvatar(path), "Figura remains in its original route");
        var nbt = LocalAvatarLoader.compileFiguraAvatar(path, new UUID(0, 1));
        require(nbt.contains("models") && !nbt.contains("ysm"), "Figura formal compilation");
        require(nbt.contains("textures") || nbt.contains("scripts"), "Figura assets retained");
        try (var files = Files.walk(path)) {
            for (Path model : files.filter(file -> file.getFileName().toString().endsWith(".bbmodel")).toList()) {
                String name = model.getFileName().toString();
                name = name.substring(0, name.length() - ".bbmodel".length());
                require(hasModel(nbt.getCompoundOrEmpty("models"), name), "every Figura model retained: " + name);
            }
        }
        var textureSources = nbt.getCompoundOrEmpty("textures").getCompoundOrEmpty("src");
        for (String texture : textureSources.keySet()) {
            require(textureSources.getByteArray(texture).orElse(new byte[0]).length > 0,
                    "Figura compiled texture bytes retained, including embedded fallback");
        }
        System.out.println("FIGURA_COMPILE_OK " + path.getFileName());
    }

    private static boolean hasModel(net.minecraft.nbt.CompoundTag model, String name) {
        if (model.getStringOr("name", "").equals(name)) {
            return true;
        }
        for (var child : model.getListOrEmpty("chld")) {
            if (child instanceof net.minecraft.nbt.CompoundTag part && hasModel(part, name)) {
                return true;
            }
        }
        return false;
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
