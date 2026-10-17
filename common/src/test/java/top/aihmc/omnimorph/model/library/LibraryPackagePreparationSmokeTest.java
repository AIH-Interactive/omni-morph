package top.aihmc.omnimorph.model.library;

import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import top.aihmc.omnimorph.avatar.AvatarManager;
import top.aihmc.omnimorph.avatar.local.LocalAvatarLoader;
import top.aihmc.omnimorph.avatar.ysm.YsmSourcePayload;
import top.aihmc.omnimorph.backend2.protocol.CloudHttp;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.Map;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public final class LibraryPackagePreparationSmokeTest {
    public static void run() throws Exception {
        Path directory = Files.createTempDirectory("library-preparation-test-");
        var state = LocalAvatarLoader.getLoadState();
        var usersField = AvatarManager.class.getDeclaredField("LOADED_USERS");
        usersField.setAccessible(true);
        Map<?, ?> users = (Map<?, ?>) usersField.get(null);
        var loaded = Map.copyOf(users);
        UUID owner = UUID.randomUUID();
        try {
            Path figura = zip(directory.resolve("图包.zip"), Map.of(
                    "avatar.json", "{\"free\":true,\"resources\":[\"extra.txt\"]}",
                    "extra.txt", "bounded packaged resource", "script.lua", "return 1"));
            var prepared = LibraryPackagePreparation.prepare(figura, owner, 1024 * 1024, () -> false);
            require(prepared.metadata().internalName().equals("图包") && prepared.metadata().authors().isEmpty()
                    && !prepared.metadata().requiresAuthorizationDefault(), "Figura source defaults");
            require(prepared.nbt().getCompoundOrEmpty("metadata").getStringOr("authors", "?").isEmpty()
                    && prepared.nbt().contains("scripts") && prepared.nbt().contains("resources"),
                    "compiled resources and no invented author");
            require(CloudHttp.sha256(prepared.compressed()).equals(prepared.sha256()), "distribution digest");
            require(NbtIo.readCompressed(new ByteArrayInputStream(prepared.compressed()),
                    NbtAccounter.create(256L * 1024 * 1024)).equals(prepared.nbt()), "wire NBT round trip");
            byte[] copy = prepared.compressed();
            copy[0] ^= 1;
            require(!CloudHttp.sha256(copy).equals(prepared.sha256())
                    && CloudHttp.sha256(prepared.compressed()).equals(prepared.sha256()), "owned bytes");

            Path plain = zip(directory.resolve("plain.ysm"), Map.of("ysm.json",
                    "{\"metadata\":{\"name\":\"Plain\"},\"properties\":{\"free\":false},"
                            + "\"files\":{\"player\":{\"model\":{\"main\":\"main.json\"}}}}",
                    "main.json", "{\"minecraft:geometry\":[{\"description\":{\"identifier\":\"geometry.test\"},"
                            + "\"bones\":[{\"name\":\"root\"}]}]}"));
            var ysm = LibraryPackagePreparation.prepare(plain, owner, 1024 * 1024, () -> false);
            require(ysm.metadata().modelType().equals("YSM_PLAIN") && ysm.metadata().requiresAuthorizationDefault()
                    && !ysm.nbt().getCompoundOrEmpty("ysm").contains("source_path"), "plain portable input");

            Path encrypted = Path.of("src/test/resources/ysm/crypto-vectors/crypto1.ysm");
            var cipher = LibraryPackagePreparation.prepare(encrypted, owner, 1024 * 1024, () -> false);
            require(cipher.metadata().modelType().equals("YSM_ENCRYPTED")
                    && cipher.nbt().equals(YsmSourcePayload.forOutput(cipher.nbt()))
                    && java.util.Arrays.equals(Files.readAllBytes(encrypted),
                    cipher.nbt().getCompoundOrEmpty("ysm").getByteArray("source_ciphertext").orElseThrow()),
                    "encrypted distribution retains original ciphertext only");
            reject(() -> LibraryPackagePreparation.prepare(figura, owner, 1, () -> false));
            reject(() -> LibraryPackagePreparation.prepare(figura, owner, 1024, () -> true));
            Path unsafe = zip(directory.resolve("unsafe.zip"), Map.of("avatar.json", "{}", "../escape", "x"));
            reject(() -> LibraryPackagePreparation.prepare(unsafe, owner, 1024, () -> false));
            require(LocalAvatarLoader.getLoadState().equals(state) && loaded.equals(users),
                    "preparation preserves wardrobe state and loaded users");
            System.out.println("PASS pure library preparation: Figura resources, plain/encrypted YSM, limits and cancellation");
        } finally {
            try (var paths = Files.walk(directory)) {
                for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
                    Files.deleteIfExists(path);
                }
            }
        }
    }

    private static Path zip(Path path, Map<String, String> files) throws IOException {
        try (var output = new ZipOutputStream(Files.newOutputStream(path))) {
            for (var entry : files.entrySet()) {
                output.putNextEntry(new ZipEntry(entry.getKey()));
                output.write(entry.getValue().getBytes(StandardCharsets.UTF_8));
                output.closeEntry();
            }
        }
        return path;
    }

    private static void reject(Action action) throws Exception {
        try {
            action.run();
            throw new AssertionError("invalid preparation accepted");
        } catch (IOException expected) {
            // Rejected before publication.
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    private interface Action {
        void run() throws Exception;
    }
}
