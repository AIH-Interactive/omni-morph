package top.aihmc.omnimorph.avatar.ysm;

import org.figuramc.figura.avatar.local.LocalAvatarFetcher;
import org.figuramc.figura.avatar.local.LocalAvatarLoader;
import top.aihmc.omnimorph.model.ysm.YsmGeometry;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public final class YsmDataBoundarySmokeTest {
    private YsmDataBoundarySmokeTest() {
    }

    public static void main(String[] args) throws Exception {
        Path root = Files.createTempDirectory("ysm-data-boundary-");
        System.setProperty("figura.test.directory", root.toString());
        try {
            Map<String, byte[]> files = fixture();
            Path folder = root.resolve("fixture");
            for (var entry : files.entrySet()) {
                Path file = folder.resolve(entry.getKey());
                Files.createDirectories(file.getParent());
                Files.write(file, entry.getValue());
            }
            Path zip = root.resolve("fixture.ysm");
            writeZip(zip, files, "wrapper/");
            try (YsmPackage directorySource = YsmPackage.open(folder);
                 YsmPackage zipSource = YsmPackage.open(zip);
                 YsmMemoryResourceSource memorySource = new YsmMemoryResourceSource("fixture", files);
                 YsmModelData directory = YsmModelData.read(directorySource,
                         YsmSourceIdentity.ContainerKind.DIRECTORY, () -> false);
                 YsmModelData archive = YsmModelData.read(zipSource,
                         YsmSourceIdentity.ContainerKind.ZIP, () -> false);
                 YsmModelData memory = YsmModelData.read(memorySource,
                         YsmSourceIdentity.ContainerKind.MEMORY, () -> false)) {
                equivalent(directory, archive);
                equivalent(directory, memory);
                require(directory.identity().containerKind() != archive.identity().containerKind(),
                        "container kind is independent from content identity");
                require(directory.resources().exists("textures/BODY.PNG"), "case insensitive source read");
                byte[] returned = directory.resources().readBytes("textures/body.png");
                returned[0] = 0;
                require(directory.resources().readBytes("textures/body.png")[0] != 0,
                        "resource buffers are not shared with callers");
                var avatar = YsmAvatarLoader.toAvatarNbt(directory, directory.manifest().textures().getFirst(),
                        new UUID(0, 1), folder);
                require(avatar.getCompoundOrEmpty("metadata").getStringOr("format", "").equals("ysm-native"),
                        "formal YSM metadata retained");
                require(avatar.getCompoundOrEmpty("ysm").getStringOr("source_path", "")
                        .equals(folder.toAbsolutePath().normalize().toString()), "local source path compatibility");
                require(avatar.getCompoundOrEmpty("ysm").getStringOr("content_sha256", "")
                        .equals(directory.identity().contentSha256()), "content identity reaches formal payload");
                require(directory.resources().readString("lang/zh_cn.json").contains("测试"),
                        "language and other raw resources retained");
                var runtimeTag = YsmAvatarLoader.toRuntimeTag(directory, null);
                var sound = runtimeTag.getListOrEmpty("sound_entries").getCompoundOrEmpty(0);
                require(sound.getStringOr("path", "").equals("sounds/example.ogg")
                                && Arrays.equals(sound.getByteArray("data").orElseThrow(), files.get("sounds/example.ogg")),
                        "runtime sound assembly preserves binary bytes and path");
                var language = runtimeTag.getListOrEmpty("language_entries").getCompoundOrEmpty(0);
                require(language.getStringOr("path", "").equals("lang/zh_cn.json")
                                && Arrays.equals(language.getByteArray("data").orElseThrow(), files.get("lang/zh_cn.json")),
                        "runtime language assembly preserves original UTF-8 bytes");
                testDirectFaces(directory);
            }
            Files.move(zip, root.resolve("closed.ysm"));
            YsmMemoryResourceSource closed = new YsmMemoryResourceSource("closed", files);
            closed.close();
            expectFailure(() -> closed.readBytes("ysm.json"), "closed memory source");
            try (YsmPackage source = YsmPackage.open(folder)) {
                expectFailure(() -> YsmModelData.read(source, YsmSourceIdentity.ContainerKind.DIRECTORY,
                        () -> true), "cancelled decoding");
                require(source.readBytes("ysm.json").length > 0, "cancellation leaves caller source usable");
            }
            try (YsmPackage source = YsmPackage.open(folder)) {
                var before = YsmSourceIdentity.ofResources(source, YsmSourceIdentity.ContainerKind.DIRECTORY);
                Files.writeString(folder.resolve("functions/run.molang"), "return 2;");
                var after = YsmSourceIdentity.ofResources(source, YsmSourceIdentity.ContainerKind.DIRECTORY);
                require(!before.contentSha256().equals(after.contentSha256()), "content changes invalidate identity");
            }
            testBudgetsAndPaths(root);
            testGeneration();
            testFigura(root);
        } finally {
            try (var paths = Files.walk(root)) {
                for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
                    Files.delete(path);
                }
            }
        }
    }

    private static Map<String, byte[]> fixture() {
        Map<String, byte[]> files = new LinkedHashMap<>();
        put(files, "ysm.json", """
                {"metadata":{"name":"fixture","authors":[{"name":"author"}]},
                 "properties":{"width_scale":0.7,"height_scale":0.9},
                 "files":{"player":{"model":{"main":"models/main.json"},"texture":["textures/body.png"]}}}
                """);
        put(files, "models/main.json", """
                {"minecraft:geometry":[{"description":{"texture_width":16,"texture_height":16},
                  "bones":[{"name":"root","pivot":[1,2,3],"rotation":[10,20,30],
                    "cubes":[{"origin":[0,0,0],"size":[2,3,4],"uv":[0,0]}]},
                    {"name":"child","parent":"root","pivot":[2,4,6]}]}]}
                """);
        put(files, "animations/main.animation.json", "{\"animations\":{\"idle\":{\"animation_length\":1}}}");
        put(files, "functions/run.molang", "return 1;");
        put(files, "lang/zh_cn.json", "{\"name\":\"测试\"}");
        files.put("sounds/example.ogg", new byte[]{'O', 'g', 'g', 'S'});
        files.put("textures/body.png", java.util.Base64.getDecoder().decode(
                "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAFgwJ/lR4sfwAAAABJRU5ErkJggg=="));
        return files;
    }

    private static void put(Map<String, byte[]> files, String path, String text) {
        files.put(path, text.getBytes(StandardCharsets.UTF_8));
    }

    private static void equivalent(YsmModelData first, YsmModelData second) throws Exception {
        require(first.identity().contentSha256().equals(second.identity().contentSha256()), "same content digest");
        require(first.manifest().resourceIndex().equals(second.manifest().resourceIndex()), "same resource inventory");
        require(first.manifest().name().equals(second.manifest().name()), "same metadata");
        require(first.resources().resourcePaths().equals(second.resources().resourcePaths()), "same full resource paths");
        for (String path : first.resources().resourcePaths()) {
            require(Arrays.equals(first.resources().readBytes(path), second.resources().readBytes(path)), path);
        }
        YsmGeometry firstGeometry = first.mainGeometry();
        YsmGeometry secondGeometry = second.mainGeometry();
        for (String name : firstGeometry.bones.keySet()) {
            YsmGeometry.Bone left = firstGeometry.bones.get(name);
            YsmGeometry.Bone right = secondGeometry.bones.get(name);
            require(Arrays.equals(left.pivot, right.pivot), "same pivot " + name);
            require(Arrays.equals(left.rotation, right.rotation), "same rotation " + name);
            require(left.parentName.equals(right.parentName), "same pose hierarchy " + name);
            for (int group = 0; group < left.cubes.size(); group++) {
                for (int face = 0; face < left.cubes.get(group).quads.size(); face++) {
                    var leftFace = left.cubes.get(group).quads.get(face);
                    var rightFace = right.cubes.get(group).quads.get(face);
                    require(Arrays.equals(leftFace.positions(), rightFace.positions()), "same direct vertices");
                    require(Arrays.equals(leftFace.uvs(), rightFace.uvs()), "same UVs");
                    require(Arrays.equals(leftFace.normal(), rightFace.normal()), "same normals");
                }
            }
        }
    }

    private static void testDirectFaces(YsmModelData original) throws Exception {
        YsmGeometry direct = new YsmGeometry();
        YsmGeometry.Bone bone = new YsmGeometry.Bone("mesh", "", new float[3], new float[3]);
        float[] vertices = {0, 0, 0, 0.2f, 0, 0.4f, 0.3f, 0.6f, 0.4f, 0, 0.5f, 0};
        bone.cubes.add(new YsmGeometry.Cube(List.of(new YsmGeometry.Quad(
                vertices, new float[]{0, 0, 1, 0, 1, 1, 0, 1}, new float[]{0, 0, 1}))));
        direct.bones.put(bone.name, bone);
        direct.roots.add(bone);
        try (YsmMemoryResourceSource source = new YsmMemoryResourceSource("binary-faces", Map.of());
             YsmModelData data = new YsmModelData(original.identity(), original.manifest(), source, direct, null)) {
            require(Arrays.equals(vertices, data.mainGeometry().bones.get("mesh").cubes.getFirst()
                    .quads.getFirst().positions()), "arbitrary faces preserved without cube reconstruction");
            direct.bones.get("mesh").pivot[0] = 9;
            require(data.mainGeometry().bones.get("mesh").pivot[0] == 0, "input geometry is isolated");
            YsmGeometry copy = data.mainGeometry();
            copy.bones.get("mesh").pivot[0] = 5;
            require(data.mainGeometry().bones.get("mesh").pivot[0] == 0, "runtime geometry instances are isolated");
            require(!YsmAvatarLoader.toRuntimeTag(data, null).contains("main_model"),
                    "direct geometry input needs no synthesized JSON");
        }
    }

    private static void testBudgetsAndPaths(Path root) throws Exception {
        YsmResourceLimits small = new YsmResourceLimits(1000, 3, 5, 2);
        expectFailure(() -> new YsmMemoryResourceSource("large", Map.of("x", new byte[4]), small), "single budget");
        expectFailure(() -> new YsmMemoryResourceSource("large", Map.of("x", new byte[3], "y", new byte[3]), small),
                "total budget");
        expectFailure(() -> new YsmMemoryResourceSource("many", Map.of("x", new byte[0], "y", new byte[0],
                "z", new byte[0]), small), "count budget");
        try (YsmMemoryResourceSource exact = new YsmMemoryResourceSource("exact", Map.of(
                "x", new byte[3], "y", new byte[2]), small)) {
            require(exact.resourcePaths().size() == 2, "exact budget accepted");
            expectFailure(() -> exact.readBytes("../x"), "memory traversal rejected");
            expectFailure(() -> exact.readBytes("C:x"), "drive relative path rejected");
        }
        expectFailure(() -> new YsmMemoryResourceSource("case", Map.of("X", new byte[0], "x", new byte[0])),
                "memory case conflict");
        Path large = root.resolve("large");
        Files.createDirectories(large);
        Files.write(large.resolve("x"), new byte[4]);
        try (YsmPackage source = YsmPackage.open(large, small)) {
            expectFailure(source::resourcePaths, "directory budget before read");
        }
        Path zip = root.resolve("case.zip");
        writeZip(zip, Map.of("X", new byte[0], "x", new byte[0]), "");
        try (YsmPackage source = YsmPackage.open(zip)) {
            expectFailure(source::resourcePaths, "archive case conflict");
        }
        Files.delete(zip);
        Path cancelledZip = root.resolve("cancelled.zip");
        writeZip(cancelledZip, fixture(), "");
        try (YsmPackage source = YsmPackage.open(cancelledZip)) {
            expectFailure(() -> YsmMemoryResourceSource.snapshot(source, YsmResourceLimits.DEFAULT, () -> true),
                    "cancelled archive snapshot");
        }
        Files.delete(cancelledZip);
        Path outside = root.resolve("outside.txt");
        Files.writeString(outside, "outside");
        Path linked = root.resolve("linked");
        Files.createDirectories(linked);
        try {
            Files.createSymbolicLink(linked.resolve("escape.txt"), outside);
            try (YsmPackage source = YsmPackage.open(linked)) {
                expectFailure(source::resourcePaths, "directory symbolic link escape rejected");
            }
        } catch (java.nio.file.FileSystemException | UnsupportedOperationException e) {
            System.out.println("SKIPPED/MANUAL_BY_USER: host cannot create a symbolic link for the escape fixture");
        }
    }

    private static void testGeneration() {
        YsmLoadGeneration requests = new YsmLoadGeneration();
        long a = requests.begin();
        long b = requests.begin();
        long nextA = requests.begin();
        AtomicBoolean published = new AtomicBoolean();
        require(!requests.publish(a, () -> published.set(true)), "old A cannot publish");
        require(!requests.publish(b, () -> published.set(true)), "old B cannot publish");
        require(!published.get(), "stale publication has no side effects");
        require(requests.publish(nextA, () -> published.set(true)) && published.get(), "latest A publishes");
    }

    private static void testFigura(Path root) throws Exception {
        Path figura = root.resolve("figura");
        Files.createDirectories(figura);
        Files.writeString(figura.resolve("avatar.json"), "{\"name\":\"Figura regression\"}");
        Files.writeString(figura.resolve("script.lua"), "function events.tick() end");
        Files.writeString(figura.resolve("model.bbmodel"), """
                {"meta":{"format_version":"4.10","model_format":"free"},"name":"model",
                 "resolution":{"width":16,"height":16},"elements":[],"outliner":[],"textures":[],"animations":[]}
                """);
        require(LocalAvatarFetcher.isAvatar(figura), "ordinary Figura is recognized");
        require(LocalAvatarFetcher.isLoadableAvatar(figura), "ordinary Figura is discoverable");
        require(!YsmAvatarDetector.isYsmAvatar(figura), "Figura is not routed to YSM");
        var nbt = LocalAvatarLoader.compileFiguraAvatar(figura, new UUID(0, 1));
        require(nbt.contains("models") && nbt.contains("scripts"), "real Figura compiler loads models and scripts");
        require(!nbt.contains("ysm"), "ordinary payload has no YSM runtime");
    }

    private static void writeZip(Path zip, Map<String, byte[]> files, String prefix) throws IOException {
        try (ZipOutputStream output = new ZipOutputStream(Files.newOutputStream(zip))) {
            for (var entry : files.entrySet()) {
                output.putNextEntry(new ZipEntry(prefix + entry.getKey()));
                output.write(entry.getValue());
                output.closeEntry();
            }
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    private static void expectFailure(CheckedAction action, String message) throws Exception {
        try {
            action.run();
        } catch (IOException expected) {
            return;
        }
        throw new AssertionError(message);
    }

    @FunctionalInterface
    private interface CheckedAction {
        void run() throws Exception;
    }
}
