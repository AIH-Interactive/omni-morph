package top.aihmc.omnimorph.model.library;

import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import org.figuramc.figura.avatar.AvatarManager;
import org.figuramc.figura.avatar.local.LocalAvatarLoader;
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
            String regressionPackage = System.getenv("OMNIMORPH_TEST_FIGURA_PACKAGE");
            if (regressionPackage != null && !regressionPackage.isBlank()) {
                var actual = LibraryPackagePreparation.prepare(Path.of(regressionPackage), owner,
                        64L * 1024 * 1024, () -> false);
                require(actual.metadata().modelType().equals("FIGURA") && actual.nbt().contains("models")
                                && actual.nbt().contains("scripts"),
                        "provided Figura archive prepares actual model geometry and scripts");
                var actualDraft = new LibraryUploadDraft(owner);
                long actualGeneration = actualDraft.beginSource(Path.of(regressionPackage));
                actualDraft.category("full_model");
                actualDraft.prepared(actualGeneration, actual);
                var actualLimits = new top.aihmc.omnimorph.backend2.library.LibraryModels.Capabilities(
                        true, 1, 64L * 1024 * 1024, 8, 1024, 256, 256,
                        java.util.List.of("zip", "ysm"), java.util.List.of("FIGURA"),
                        java.util.List.of("full_model"), false);
                var actualSnapshot = actualDraft.snapshot(actualLimits);
                require(actualSnapshot.metadata().get("authors").equals(actual.metadata().authors())
                                && actualSnapshot.metadata().get("tags").equals(java.util.List.of()),
                        "provided package retains all original authors and accepts empty optional tags at submit");
                String exportDirectory = System.getenv("OMNIMORPH_TEST_UPLOAD_DIRECTORY");
                if (exportDirectory != null && !exportDirectory.isBlank()) {
                    Path output = Files.createDirectories(Path.of(exportDirectory));
                    Files.write(output.resolve("package.nbt"), actual.compressed());
                    var input = new java.util.HashMap<String, Object>();
                    input.put("metadata", actualSnapshot.metadata());
                    input.put("raw_sha256", actual.sha256());
                    input.put("byte_length", actual.byteLength());
                    input.put("source_filename", Path.of(regressionPackage).getFileName().toString());
                    input.put("metadata_source", actual.metadataSource());
                    Files.writeString(output.resolve("upload.json"), new com.google.gson.Gson().toJson(input));
                }
                System.out.println("PASS provided Figura archive preparation");
            }
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

            testAccessoryPreparation(directory, owner);

            for (String prefix : java.util.List.of("aylDWT/", "release/anan-fullmodel/")) {
                Path nested = zip(directory.resolve("nested-" + prefix.length() + ".zip"), Map.of(
                        prefix + "avatar.json", "{\"name\":\"Nested\",\"author\":\"Original\",\"free\":true,\"resources\":[\"extra.txt\"]}",
                        prefix + "extra.txt", "selected model resource",
                        prefix + "script.lua", "return 1",
                        "skins/unrelated.lua", "error('sibling must not be compiled')",
                        "README.txt", "packaging documentation"));
                var nestedPrepared = LibraryPackagePreparation.prepare(nested, owner, 1024 * 1024, () -> false);
                require(nestedPrepared.metadata().internalName().equals("Nested")
                                && nestedPrepared.metadata().authors().equals(java.util.List.of("Original"))
                                && !nestedPrepared.metadata().requiresAuthorizationDefault(),
                        "nested Figura metadata resolves the model's own avatar.json");
                var scripts = nestedPrepared.nbt().getCompoundOrEmpty("scripts");
                require(scripts.contains("script") && scripts.size() == 1
                                && nestedPrepared.nbt().contains("resources"),
                        "nested compilation uses model-relative paths and excludes sibling skins/scripts");
            }

            Path unusualNames = zip(directory.resolve("unusual-names.zip"), Map.of(
                    "wrapped/avatar.json", "{\"resources\":[\"LICENSE/\\nAnAn-Figura-avatar/LICENSE\"]}",
                    "wrapped/script.lua", "return 1",
                    "wrapped/LICENSE/\nAnAn-Figura-avatar/LICENSE", "original attribution"));
            var unusual = LibraryPackagePreparation.prepare(unusualNames, owner, 1024 * 1024, () -> false);
            require(unusual.nbt().getCompoundOrEmpty("resources")
                            .contains("LICENSE/\nAnAn-Figura-avatar/LICENSE"),
                    "ZIP-only path names and explicitly declared attribution resources retain their exact names");

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
            testPrivatePreparation(directory, owner, figura, plain, encrypted);
            OutfitSourceCacheSmokeTest.run(directory, prepared);
            OutfitSourceCacheSmokeTest.run(directory, ysm);
            OutfitSourceCacheSmokeTest.run(directory, cipher);
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

    private static void testAccessoryPreparation(Path directory, UUID owner) throws Exception {
        String declaration = """
                {"name":"Ribbon","authors":["Original"],"category":"Trail",
                 "cosmetic_anchor":"hip","resources":["credit.txt"],"free":true}
                """;
        Path folder = Files.createDirectory(directory.resolve("装饰目录"));
        Files.writeString(folder.resolve("cosmetic.json"), declaration);
        Files.writeString(folder.resolve("script.lua"), "return 1");
        Files.writeString(folder.resolve("credit.txt"), "Original credit");
        for (Path input : java.util.List.of(folder, zip(directory.resolve("accessory.zip"), Map.of(
                "wrapper/cosmetic.json", declaration, "wrapper/script.lua", "return 1",
                "wrapper/credit.txt", "Original credit")))) {
            var phases = new java.util.ArrayList<LibraryPackagePreparation.Phase>();
            var prepared = LibraryPackagePreparation.prepareAccessory(input, owner, 1024 * 1024,
                    () -> false, phases::add);
            require(prepared.resourceKind().equals("ACCESSORY")
                            && prepared.metadataSource().equals("cosmetic.json")
                            && prepared.metadata().authors().equals(java.util.List.of("Original"))
                            && prepared.nbt().contains("scripts") && prepared.nbt().contains("resources"),
                    "folder and nested archive retain accessory purpose, authors and native resources");
            require(phases.equals(java.util.List.of(LibraryPackagePreparation.Phase.READING,
                            LibraryPackagePreparation.Phase.PREPARING, LibraryPackagePreparation.Phase.COMPRESSING)),
                    "accessory preparation reports the existing foreground progress phases");
            var edited = prepared.accessoryMetadata();
            edited.addProperty("cosmetic_anchor", "head");
            var changed = LibraryPackagePreparation.withAccessoryMetadata(prepared, edited, 1024 * 1024, () -> false);
            require(changed.accessoryMetadata().get("cosmetic_anchor").getAsString().equals("head")
                            && prepared.accessoryMetadata().get("cosmetic_anchor").getAsString().equals("hip")
                            && !changed.sha256().equals(prepared.sha256())
                            && NbtIo.readCompressed(new ByteArrayInputStream(changed.compressed()),
                            NbtAccounter.create(256L * 1024 * 1024)).equals(changed.nbt()),
                    "edited anchor is serialized into a new immutable distribution and digest");
            var changedAuthors = new com.google.gson.JsonArray();
            changedAuthors.add("Changed author");
            edited.add("authors", changedAuthors);
            reject(() -> LibraryPackagePreparation.withAccessoryMetadata(prepared, edited, 1024 * 1024, () -> false));
            reject(() -> LibraryPackagePreparation.withAccessoryMetadata(changed,
                    changed.accessoryMetadata(), 1024, () -> true));
        }
        require(Files.readString(folder.resolve("cosmetic.json")).equals(declaration),
                "upload edits never overwrite the author's source declaration");
        Path encryptedFolder = Files.createDirectory(directory.resolve("encrypted-accessory"));
        Files.writeString(encryptedFolder.resolve("cosmetic.json"), declaration);
        Files.writeString(encryptedFolder.resolve("credit.txt"), "Original credit");
        byte[] ciphertext = Files.readAllBytes(Path.of("src/test/resources/ysm/crypto-vectors/crypto1.ysm"));
        Files.write(encryptedFolder.resolve("payload.ysm"), ciphertext);
        var encrypted = LibraryPackagePreparation.prepareAccessory(encryptedFolder, owner, 1024 * 1024,
                () -> false, ignored -> {});
        OutfitSourceCacheSmokeTest.run(directory, encrypted);
        var encryptedDeclaration = encrypted.accessoryMetadata();
        encryptedDeclaration.addProperty("cosmetic_anchor", "head_anchor");
        var editedEncrypted = LibraryPackagePreparation.withAccessoryMetadata(encrypted, encryptedDeclaration,
                1024 * 1024, () -> false);
        require(editedEncrypted.metadata().modelType().equals("YSM_ENCRYPTED")
                        && editedEncrypted.resourceKind().equals("ACCESSORY")
                        && editedEncrypted.metadataSource().equals("encrypted_manifest")
                        && java.util.Arrays.equals(ciphertext, editedEncrypted.nbt().getCompoundOrEmpty("ysm")
                        .getByteArray("source_ciphertext").orElseThrow())
                        && editedEncrypted.accessoryMetadata().equals(encryptedDeclaration),
                "encrypted accessory upload retains original ciphertext, native format and edited wrapper declaration");
        Path ambiguous = zip(directory.resolve("ambiguous-accessory.zip"), Map.of(
                "one/cosmetic.json", declaration, "two/cosmetic.json", declaration));
        reject(() -> LibraryPackagePreparation.prepareAccessory(ambiguous, owner, 1024 * 1024,
                () -> false, ignored -> {}));
        reject(() -> LibraryPackagePreparation.prepareAccessory(folder, owner, 1, () -> false, ignored -> {}));
        reject(() -> LibraryPackagePreparation.prepareAccessory(folder, owner, 1024, () -> true, ignored -> {}));
    }

    private static void testPrivatePreparation(Path directory, UUID owner, Path figura, Path plain, Path encrypted)
            throws Exception {
        var limits = top.aihmc.omnimorph.model.outfit.OutfitLimits.defaults();
        var avatarKind = top.aihmc.omnimorph.model.outfit.OutfitConfig.ResourceKind.AVATAR;
        var accessoryKind = top.aihmc.omnimorph.model.outfit.OutfitConfig.ResourceKind.ACCESSORY;
        var index = new top.aihmc.omnimorph.avatar.local.LocalAvatarSource(directory);
        for (Path input : java.util.List.of(figura, plain, encrypted)) {
            var reference = index.describe(input, () -> false);
            UUID operation = UUID.randomUUID();
            var prepared = OutfitPrivatePreparation.prepare(operation, reference, avatarKind, owner,
                    directory, limits, 1024 * 1024, () -> false);
            var request = prepared.upload().request(limits);
            require(prepared.upload().operation().equals(operation)
                            && request.getAsJsonObject("reference").get("content_hash").getAsString()
                            .equals(reference.contentHash())
                            && request.get("raw_sha256").getAsString().equals(CloudHttp.sha256(prepared.upload().bytes()))
                            && request.get("accessory_metadata").isJsonNull(),
                    "fixed source provenance and native body hash stay distinct in private preparation");
            require(NbtIo.readCompressed(new ByteArrayInputStream(prepared.upload().bytes()),
                            NbtAccounter.create(256L * 1024 * 1024)).equals(prepared.nbt()),
                    "private preparation returns the exact frozen native distribution");
            var mutable = prepared.nbt();
            mutable.putString("injected", "not shared");
            require(!prepared.nbt().contains("injected"), "prepared consumers receive isolated NBT");
            if (reference.format() != top.aihmc.omnimorph.model.outfit.OutfitConfig.Format.FIGURA) {
                require(request.get("content_sha256").getAsString().matches("[0-9a-f]{64}")
                                && request.get("schema_sha256").getAsString().matches("[0-9a-f]{64}"),
                        "both YSM formats reuse the actual native content and schema inventory");
            }
            reject(() -> OutfitPrivatePreparation.prepare(operation, reference, avatarKind, owner,
                    directory, limits, 1, () -> false));
            reject(() -> OutfitPrivatePreparation.prepare(operation, reference, avatarKind, owner,
                    directory, limits, 1024, () -> true));
        }
        Path privateBase = Files.createDirectories(directory.resolve("private-base"));
        Files.writeString(privateBase.resolve("avatar.json"), "{\"name\":\"§bOriginal\",\"author\":\"§cAuthor\"}");
        var formattedReference = index.describe(privateBase, () -> false);
        var formatted = OutfitPrivatePreparation.prepare(UUID.randomUUID(), formattedReference, avatarKind, owner,
                directory, limits, 1024 * 1024, () -> false);
        require(formatted.nbt().getCompoundOrEmpty("metadata").getStringOr("name", "").equals("§bOriginal"),
                "private native packaging preserves existing runtime metadata instead of catalog plain text");
        Files.writeString(privateBase.resolve("avatar.json"), "{\"name\":\"Changed\"}");
        reject(() -> OutfitPrivatePreparation.prepare(UUID.randomUUID(), formattedReference, avatarKind, owner,
                directory, limits, 1024 * 1024, () -> false));

        Path accessory = Files.createDirectories(directory.resolve("cosmetics/private-accessory"));
        String authored = "{\"name\":\"Private\",\"category\":\"Hair\",\"cosmetic_anchor\":\"head\"}";
        Files.writeString(accessory.resolve("cosmetic.json"), authored);
        Files.writeString(accessory.resolve("script.lua"), "return 1");
        Files.copy(encrypted, accessory.resolve("payload.ysm"));
        top.aihmc.omnimorph.model.outfit.OutfitConfig.ResourceRef reference;
        try (var inventory = new top.aihmc.omnimorph.model.outfit.LocalCosmeticInventory(directory)) {
            reference = inventory.scan(() -> false).stream().filter(entry -> entry.path().equals(accessory))
                    .findFirst().orElseThrow().reference();
        }
        var prepared = OutfitPrivatePreparation.prepare(UUID.randomUUID(), reference, accessoryKind, owner,
                directory, limits, 1024 * 1024, () -> false);
        require(prepared.upload().request(limits).get("resource_kind").getAsString().equals("ACCESSORY")
                        && java.util.Arrays.equals(Files.readAllBytes(encrypted), prepared.nbt().getCompoundOrEmpty("ysm")
                        .getByteArray("source_ciphertext").orElseThrow())
                        && Files.readString(accessory.resolve("cosmetic.json")).equals(authored),
                "unapplied local accessory resolves from inventory and retains encrypted source and author declaration");
        Files.writeString(accessory.resolve("script.lua"), "return 2");
        reject(() -> OutfitPrivatePreparation.prepare(UUID.randomUUID(), reference, accessoryKind, owner,
                directory, limits, 1024 * 1024, () -> false));
        System.out.println("PASS private local sources: fixed Figura/plain/encrypted inputs, unapplied accessory, "
                + "native hashes, immutable data, source changes, byte budget and cancellation");
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
