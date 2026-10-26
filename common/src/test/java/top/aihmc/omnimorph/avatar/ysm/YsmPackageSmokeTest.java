package top.aihmc.omnimorph.avatar.ysm;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Comparator;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public final class YsmPackageSmokeTest {
    private YsmPackageSmokeTest() {
    }

    public static void main(String[] args) throws Exception {
        Path tempRoot = Files.createTempDirectory("ysm-package-smoke");
        try {
            testNewFolderAndZip(tempRoot);
            testOldFolderAndZip(tempRoot);
            testPathSafetyAndClose(tempRoot);
        } finally {
            deleteRecursively(tempRoot);
        }
    }

    private static void testNewFolderAndZip(Path tempRoot) throws Exception {
        Path folder = tempRoot.resolve("new-model");
        Files.createDirectories(folder.resolve("models"));
        Files.createDirectories(folder.resolve("animations"));
        Files.createDirectories(folder.resolve("lang"));
        Files.createDirectories(folder.resolve("Textures"));
        Files.writeString(folder.resolve("ysm.json"), """
                {
                  "metadata": {"name": "synthetic-new"},
                  "files": {
                    "player": {
                      "model": {"main": "models/main.json", "arm": "models/arm.json"},
                      "texture": ["textures/body.png", "textures/missing.png"],
                                            "animation": {"main": "animations/main.animation.json", "missing": "animations/missing.animation.json"}
                                        },
                    "language": {"en_us": "lang/en_us.json"},
                    "projectiles": [{"match": ["minecraft:arrow"], "model": "models/arrow.json", "texture": "textures/arrow.png"}],
                    "vehicles": [{"match": ["minecraft:boat"], "model": "models/boat.json"}]
                                    }
                }
                """, StandardCharsets.UTF_8);
        Files.writeString(folder.resolve("models/main.json"), "{}", StandardCharsets.UTF_8);
        Files.writeString(folder.resolve("models/arm.json"), "{}", StandardCharsets.UTF_8);
        Files.writeString(folder.resolve("models/arrow.json"), "{}", StandardCharsets.UTF_8);
        Files.writeString(folder.resolve("models/boat.json"), "{}", StandardCharsets.UTF_8);
        Files.writeString(folder.resolve("animations/main.animation.json"), "{}", StandardCharsets.UTF_8);
        Files.writeString(folder.resolve("animations/unused.animation.json"), "{}", StandardCharsets.UTF_8);
        Files.createDirectories(folder.resolve("controller"));
        Files.writeString(folder.resolve("controller/unused.animation_controllers.json"), "{}", StandardCharsets.UTF_8);
        Files.writeString(folder.resolve("lang/en_us.json"), "{}", StandardCharsets.UTF_8);
        Files.write(folder.resolve("Textures/body.png"), new byte[]{1, 2, 3});
        Files.write(folder.resolve("textures/arrow.png"), new byte[]{7, 8, 9});

        YsmManifest folderManifest = YsmManifestReader.read(folder);
        equals(YsmAvatarKind.NEW, folderManifest.kind(), "new folder kind");
        equals(List.of("animations/main.animation.json"), folderManifest.resourceIndex().animationPaths(),
                "new folder ignores undeclared animations");
        equals(List.of(), folderManifest.resourceIndex().animationControllerPaths(),
                "new folder ignores undeclared controllers");
        equals("models/main.json", folderManifest.resourceIndex().mainModelPath(), "new folder main model");
        equals("models/arm.json", folderManifest.resourceIndex().armModelPath(), "new folder arm model");
        contains(folderManifest.resourceIndex().texturePaths(), "textures/body.png", "new folder case-insensitive texture");
        contains(folderManifest.resourceIndex().languagePaths(), "lang/en_us.json", "new folder language");
        contains(folderManifest.resourceIndex().projectilePaths(), "models/arrow.json", "new folder projectile model");
        contains(folderManifest.resourceIndex().vehiclePaths(), "models/boat.json", "new folder vehicle model");
        containsWarning(folderManifest.resourceIndex().warnings(), "missing optional texture: textures/missing.png");
        containsWarning(folderManifest.resourceIndex().warnings(), "missing optional resource: animations/missing.animation.json");

        Path zip = tempRoot.resolve("new-model.zip");
        writeZip(folder, zip, "wrapper");
        YsmManifest zipManifest = YsmManifestReader.read(zip);
        equals(YsmAvatarKind.NEW, zipManifest.kind(), "new zip kind");
        equals(folderManifest.resourceIndex().animationPaths(), zipManifest.resourceIndex().animationPaths(),
                "new zip uses the same declared animation scope");
        equals(folderManifest.resourceIndex().animationControllerPaths(), zipManifest.resourceIndex().animationControllerPaths(),
                "new zip uses the same declared controller scope");
        equals(folderManifest.resourceIndex().mainModelPath(), zipManifest.resourceIndex().mainModelPath(), "new zip main model");
        equals(folderManifest.resourceIndex().armModelPath(), zipManifest.resourceIndex().armModelPath(), "new zip arm model");
        equals(folderManifest.resourceIndex().texturePaths(), zipManifest.resourceIndex().texturePaths(), "new zip textures");
        equals(folderManifest.resourceIndex().languagePaths(), zipManifest.resourceIndex().languagePaths(), "new zip languages");
        equals(folderManifest.resourceIndex().projectilePaths(), zipManifest.resourceIndex().projectilePaths(), "new zip projectiles");
        equals(folderManifest.resourceIndex().vehiclePaths(), zipManifest.resourceIndex().vehiclePaths(), "new zip vehicles");
        containsWarning(zipManifest.resourceIndex().warnings(), "missing optional texture: textures/missing.png");
    }

    private static void testOldFolderAndZip(Path tempRoot) throws Exception {
        Path folder = tempRoot.resolve("old-model");
        Files.createDirectories(folder);
        Files.writeString(folder.resolve("main.json"), "{\"minecraft:geometry\":[]}", StandardCharsets.UTF_8);
        Files.writeString(folder.resolve("main.animation.json"), "{}", StandardCharsets.UTF_8);
        Files.write(folder.resolve("body.png"), new byte[]{4, 5, 6});
        // D017: Sparkle YSMFolderDeserializer.isModelFolder needs main.json and arm.json for an old folder.
        equals(YsmAvatarKind.NONE, YsmAvatarDetector.kind(folder), "old folder without arm.json is not a model");
        Files.writeString(folder.resolve("arm.json"), "{\"minecraft:geometry\":[]}", StandardCharsets.UTF_8);
        equals(YsmAvatarKind.OLD, YsmAvatarDetector.kind(folder), "old folder with main.json and arm.json");

        YsmManifest folderManifest = YsmManifestReader.read(folder);
        equals(YsmAvatarKind.OLD, folderManifest.kind(), "old folder kind");
        equals("main.json", folderManifest.resourceIndex().mainModelPath(), "old folder main model");
        contains(folderManifest.resourceIndex().texturePaths(), "body.png", "old folder texture");

        Path zip = tempRoot.resolve("old-model.zip");
        writeZip(folder, zip, "legacy");
        YsmManifest zipManifest = YsmManifestReader.read(zip);
        equals(YsmAvatarKind.OLD, zipManifest.kind(), "old zip kind");
        equals(folderManifest.resourceIndex().mainModelPath(), zipManifest.resourceIndex().mainModelPath(), "old zip main model");
        equals(folderManifest.resourceIndex().texturePaths(), zipManifest.resourceIndex().texturePaths(), "old zip textures");
    }

    private static void testPathSafetyAndClose(Path tempRoot) throws Exception {
        Path folder = tempRoot.resolve("new-model");
        Path zip = tempRoot.resolve("safety.zip");
        writeZip(folder, zip, "wrapper");
        byte[] expected = Files.readAllBytes(folder.resolve("models/main.json"));

        try (YsmPackage ysmPackage = YsmPackage.open(zip)) {
            equals(new String(expected, StandardCharsets.UTF_8), ysmPackage.readString("MODELS/MAIN.JSON"), "zip case-insensitive read");
            equals(false, ysmPackage.exists("../outside.txt"), "zip traversal exists");
            expectIOException(() -> ysmPackage.readString("../outside.txt"), "zip traversal read");
        }

        Path moved = tempRoot.resolve("safety-moved.zip");
        Files.move(zip, moved, StandardCopyOption.REPLACE_EXISTING);
        Files.delete(moved);
    }

    private static void writeZip(Path folder, Path zip, String prefix) throws IOException {
        List<Path> files;
        try (var stream = Files.walk(folder)) {
            files = stream.filter(Files::isRegularFile).sorted(Comparator.comparing(Path::toString)).toList();
        }
        try (ZipOutputStream output = new ZipOutputStream(Files.newOutputStream(zip))) {
            for (Path file : files) {
                String relative = folder.relativize(file).toString().replace('/', '\\');
                String entryName = prefix + "\\" + relative;
                output.putNextEntry(new ZipEntry(entryName));
                output.write(Files.readAllBytes(file));
                output.closeEntry();
            }
        }
    }

    private static void deleteRecursively(Path root) throws IOException {
        if (root == null || !Files.exists(root))
            return;
        try (var stream = Files.walk(root)) {
            stream.sorted(Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            });
        } catch (RuntimeException e) {
            if (e.getCause() instanceof IOException ioException)
                throw ioException;
            throw e;
        }
    }

    private static void contains(List<String> values, String expected, String label) {
        if (!values.contains(expected))
            throw new AssertionError(label + ": expected " + values + " to contain " + expected);
    }

    private static void containsWarning(List<String> warnings, String expected) {
        if (!warnings.contains(expected))
            throw new AssertionError("Expected warnings " + warnings + " to contain " + expected);
    }

    private static void equals(Object expected, Object actual, String label) {
        if (!expected.equals(actual))
            throw new AssertionError(label + ": expected " + expected + " but got " + actual);
    }

    private static void expectIOException(IoRunnable runnable, String label) throws Exception {
        try {
            runnable.run();
        } catch (IOException expected) {
            return;
        }
        throw new AssertionError(label + ": expected IOException");
    }

    @FunctionalInterface
    private interface IoRunnable {
        void run() throws IOException;
    }
}
