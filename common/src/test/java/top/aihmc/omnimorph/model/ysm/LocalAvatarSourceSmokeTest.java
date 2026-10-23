package top.aihmc.omnimorph.model.ysm;

import com.google.gson.Gson;
import top.aihmc.omnimorph.avatar.local.LocalAvatarSource;
import top.aihmc.omnimorph.model.outfit.OutfitConfig;

import java.io.IOException;
import java.io.InterruptedIOException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/** Source bytes and real ZIP filesystem ownership, independent of rendering and execution owner. */
final class LocalAvatarSourceSmokeTest {
    static void run(Path encrypted) throws Exception {
        Path root = Files.createTempDirectory("omnimorph-avatar-source-");
        try {
            Path data = Files.createDirectories(root.resolve("data"));
            Path folder = Files.createDirectories(root.resolve("作者 模型"));
            Files.writeString(folder.resolve("avatar.json"), "{\"name\":\"model\"}");
            Files.writeString(folder.resolve("script.lua"), "return 1");
            var index = new LocalAvatarSource(data);
            var first = index.describe(folder, () -> false);
            require(first.equals(new LocalAvatarSource(data).describe(folder, () -> false)),
                    "device ID and raw package hash survive index reload");
            String document = new Gson().toJson(first);
            require(!document.contains(folder.toString()) && !document.contains("file:") && !document.contains("jar:"),
                    "portable resource reference never exposes device source location");
            try (var source = new LocalAvatarSource(data).open(first, () -> false)) {
                require(source.path().equals(folder.toRealPath()) && source.ownedFileSystem() == null,
                        "folder reference resolves from the device index");
            }
            Path zip = root.resolve("model.zip");
            try (var output = new ZipOutputStream(Files.newOutputStream(zip))) {
                for (String name : new String[]{"script.lua", "avatar.json"}) {
                    output.putNextEntry(new ZipEntry("nested/" + name));
                    output.write(Files.readAllBytes(folder.resolve(name)));
                    output.closeEntry();
                }
            }
            OutfitConfig.ResourceRef zipped;
            try (var fs = FileSystems.newFileSystem(zip, Map.of())) {
                Path nested = fs.getPath("/nested");
                zipped = index.describe(nested, () -> false);
                require(!zipped.localId().equals(first.localId()) && zipped.contentHash().equals(first.contentHash()),
                        "sorted raw resources give archive subdirectories the same content hash, with independent source IDs");
                try (var borrowed = index.open(zipped, () -> false)) {
                    require(Files.readString(borrowed.path().resolve("script.lua")).equals("return 1")
                                    && (borrowed.ownedFileSystem() == null || borrowed.ownedFileSystem() != fs),
                            "Path-opened inventory ZIPs may need a separate URI-opened handle, never ownership of the inventory FS");
                }
                require(fs.isOpen(), "closing a source handle cannot close the wardrobe inventory's ZIP");
            }
            try (var fs = FileSystems.newFileSystem(java.net.URI.create("jar:" + zip.toUri()), Map.of());
                 var source = new LocalAvatarSource(data).open(zipped, () -> false)) {
                require(source.ownedFileSystem() == null && source.path().getFileSystem() == fs,
                        "a URI-registered inventory filesystem is borrowed without acquiring ownership");
                source.close();
                require(fs.isOpen(), "closing a borrowed URI filesystem handle preserves the inventory");
            }
            java.nio.file.FileSystem reopened;
            try (var source = new LocalAvatarSource(data).open(zipped, () -> false)) {
                reopened = source.ownedFileSystem();
                require(reopened != null && reopened.isOpen() && Files.exists(source.path().resolve("avatar.json")),
                        "archive subdirectory reference reopens after the original filesystem is gone");
            }
            require(!reopened.isOpen(), "a source handle closes only its own reopened filesystem");
            Files.writeString(folder.resolve("script.lua"), "return 2");
            var changed = index.describe(folder, () -> false);
            require(changed.localId().equals(first.localId()) && !changed.contentHash().equals(first.contentHash()),
                    "editing source content retains local identity and changes the exact version");
            try {
                index.open(first, () -> false).close();
                throw new AssertionError("changed source was accepted as the old version");
            } catch (IOException expected) {
                require(expected.getMessage().equals("OUTFIT_BASE_SOURCE_CHANGED"), "changed source is explicit");
            }
            try {
                index.describe(folder, () -> true);
                throw new AssertionError("source description ignored cancellation");
            } catch (InterruptedIOException expected) {
                require(expected.getMessage().contains("CANCELLED"), "hashing cooperates with load cancellation");
            }
            Files.writeString(folder.resolve("ysm.json"), "{}");
            require(index.describe(folder, () -> false).format() == OutfitConfig.Format.YSM_PLAIN,
                    "plain YSM package format follows the native detector");
            if (encrypted != null) {
                Path ciphertext = root.resolve("encrypted.ysm");
                Files.copy(encrypted, ciphertext);
                var reference = index.describe(ciphertext, () -> false);
                String rawHash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                        .digest(Files.readAllBytes(ciphertext)));
                require(reference.format() == OutfitConfig.Format.YSM_ENCRYPTED && reference.contentHash().equals(rawHash),
                        "encrypted provenance hashes original ciphertext without author metadata or execution UUID");
                try (var source = index.open(reference, () -> false)) {
                    require(source.path().equals(ciphertext.toRealPath()), "encrypted source resolves without extracting it");
                }
            }
            System.out.println("PASS avatar provenance: stable IDs, raw hashes, source mutation/cancellation, archive restart and borrowed filesystem ownership");
        } finally {
            try (var paths = Files.walk(root)) {
                for (Path path : paths.sorted(java.util.Comparator.reverseOrder()).toList()) {
                    Files.delete(path);
                }
            }
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
