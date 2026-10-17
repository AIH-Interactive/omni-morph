package top.aihmc.omnimorph.avatar.ysm;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import top.aihmc.omnimorph.avatar.local.LocalAvatarFetcher;
import top.aihmc.omnimorph.avatar.local.LocalAvatarLoader;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Set;
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
                            verifyAttachedResources(data, nbt.getCompoundOrEmpty("ysm"));
                            var transferred = transfer(nbt);
                            verifyAttachedResources(data, transferred.getCompoundOrEmpty("ysm"));
                            require(transferred.getCompoundOrEmpty("ysm").getStringOr("content_sha256", "")
                                    .equals(data.identity().contentSha256()), "plain model content identity retained through transfer");
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
                    compileEncrypted(path);
                    encrypted++;
                }
            }
            require(figura > 0 && ysm > 0 && encrypted > 0, "all user-requested corpus categories are present");
            System.out.printf("Local corpus: Figura compiled=%d YSM data assembled=%d encrypted assembled/rehydrated=%d%n",
                    figura, ysm, encrypted);
        } finally {
            try (var files = Files.walk(environment)) {
                for (Path path : files.sorted(Comparator.reverseOrder()).toList()) {
                    Files.delete(path);
                }
            }
        }
    }

    private static void verifyAttachedResources(YsmModelData data, net.minecraft.nbt.CompoundTag runtime)
            throws Exception {
        verifyAttachedResources(data, runtime, "sound_entries", data.manifest().resourceIndex().soundPaths());
        verifyAttachedResources(data, runtime, "language_entries", data.manifest().resourceIndex().languagePaths());
    }

    private static void verifyAttachedResources(YsmModelData data, net.minecraft.nbt.CompoundTag runtime,
                                                String key, java.util.List<String> paths) throws Exception {
        var entries = runtime.getListOrEmpty(key);
        require(entries.size() == paths.size(), "all indexed attached resources assembled: " + key);
        for (int index = 0; index < entries.size(); index++) {
            var entry = entries.getCompoundOrEmpty(index);
            String path = entry.getStringOr("path", "");
            require(path.equals(paths.get(index)), "attached resource path order retained: " + key);
            require(Arrays.equals(entry.getByteArray("data").orElseThrow(), data.resources().readBytes(path)),
                    "attached resource bytes retained: " + key);
        }
    }

    private static void compileEncrypted(Path path) throws Exception {
        require(YsmEncryptedSource.hasMagic(path), "encrypted corpus header");
        require(LocalAvatarFetcher.isLoadableAvatar(path), "encrypted wardrobe discovery");
        var manifest = YsmManifestReader.read(path);
        try (YsmModelData data = YsmModelData.read(path, () -> false)) {
            require(data.encrypted() && !data.mainGeometry().bones.isEmpty(), "encrypted formal geometry");
            require(data.manifest().name().equals(manifest.name()), "wardrobe metadata agrees with formal data");
            var texture = data.manifest().textures().getFirst();
            var nbt = YsmAvatarLoader.toAvatarNbt(data, texture, new UUID(0, 1), path);
            require(nbt.keySet().equals(Set.of("metadata", "ysm")), "encrypted avatar root whitelist");
            var payload = nbt.getCompoundOrEmpty("ysm");
            require(!payload.contains("main_model") && !payload.contains("animations")
                    && !payload.contains("texture") && !payload.contains("source_path"), "ciphertext storage boundary");
            require(Arrays.equals(Files.readAllBytes(path), payload.getByteArray("source_ciphertext").orElseThrow()),
                    "original ciphertext retained");
            var runtime = YsmAvatarLoader.toRuntimeTag(data, texture);
            verifyAttachedResources(data, runtime);
            require(!payload.contains("sound_entries") && !payload.contains("language_entries"),
                    "decrypted sound/language bytes remain outside the portable payload");
            require(!runtime.contains("source_ciphertext") && runtime.contains("texture"), "ephemeral runtime resources");
            require(runtime.getListOrEmpty("animations").size() == data.manifest().animationPaths().size(),
                    "all player animation resources retained");
            CompoundTag transferred = transfer(nbt).getCompoundOrEmpty("ysm");
            try (YsmModelData restored = YsmSourcePayload.openData(transferred, () -> false)) {
                require(restored.identity().equals(data.identity()), "portable source identity");
                require(restored.mainGeometry().bones.size() == data.mainGeometry().bones.size(), "rehydrated geometry");
                require(restored.resources().resourcePaths().equals(data.resources().resourcePaths()), "rehydrated inventory");
                for (String resource : data.resources().resourcePaths()) {
                    require(Arrays.equals(data.resources().readBytes(resource), restored.resources().readBytes(resource)),
                            "rehydrated resource equivalence: " + resource);
                }
            }
            try {
                YsmSourcePayload.openData(payload, () -> true).close();
                throw new AssertionError("Cancelled portable load accepted");
            } catch (java.io.InterruptedIOException expected) {
                // Cancellation must propagate instead of publishing a partial model.
            }
            System.out.println("ENCRYPTED_DATA_REHYDRATE_OK " + path.getFileName());
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
        require(transfer(nbt).equals(nbt), "ordinary Figura keeps the identical existing static payload");
        System.out.println("FIGURA_COMPILE_OK " + path.getFileName());
    }

    private static CompoundTag transfer(CompoundTag avatar) throws Exception {
        CompoundTag original = avatar.copy();
        CompoundTag portable = YsmSourcePayload.forOutput(avatar);
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        NbtIo.writeCompressed(portable, output);
        CompoundTag received;
        try (var input = new ByteArrayInputStream(output.toByteArray())) {
            received = NbtIo.readCompressed(input, NbtAccounter.create(256L * 1024 * 1024));
        }
        require(received.equals(portable), "every supported static resource survives the existing compressed NBT transport");
        require(avatar.equals(original), "portable transport does not mutate caller-owned model content");
        require(YsmSourcePayload.forOutput(received).equals(received), "cache filtering remains idempotent after transfer");
        return received;
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
