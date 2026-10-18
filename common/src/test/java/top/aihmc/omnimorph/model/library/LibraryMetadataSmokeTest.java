package top.aihmc.omnimorph.model.library;

import top.aihmc.omnimorph.avatar.ysm.YsmManifestReader;
import top.aihmc.omnimorph.avatar.ysm.YsmMemoryResourceSource;
import top.aihmc.omnimorph.avatar.ysm.binary.YsmBinaryModel;
import top.aihmc.omnimorph.avatar.ysm.binary.YsmBinaryResourceMapper;
import top.aihmc.omnimorph.avatar.ysm.crypto.YsmDecodeBudget;
import top.aihmc.omnimorph.parsers.AvatarMetadataParser;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public final class LibraryMetadataSmokeTest {
    public static void main(String[] args) throws Exception {
        for (String value : List.of("true", "false", "null")) {
            String field = value.equals("null") ? "" : "\"free\":" + value;
            boolean authorization = !value.equals("true");
            var figura = LibraryMetadataReader.fromFigura(AvatarMetadataParser.read("{" + field + "}"), "fallback");
            require(figura.requiresAuthorizationDefault() == authorization, "Figura free default");
            require(figura.internalName().equals("fallback") && figura.authors().isEmpty(),
                    "absent authors stay empty without the wardrobe '?' display sentinel");
            for (boolean old : new boolean[]{false, true}) {
                String formatted = "\"name\":\"§b测试模型\",\"tips\":\"§l模型说明§r\",\"authors\":[\"§e作者\"]";
                String json = old
                        ? "{\"minecraft:geometry\":[{\"description\":{\"ysm_extra_info\":{" + formatted
                            + (field.isEmpty() ? "" : "," + field) + "}}}]}"
                        : "{\"metadata\":{" + formatted + "},\"properties\":{" + field + "}}";
                try (var source = new YsmMemoryResourceSource("synthetic", Map.of(
                        old ? "main.json" : "ysm.json", json.getBytes(StandardCharsets.UTF_8)))) {
                    var manifest = YsmManifestReader.read(source);
                    require(manifest.free() == !authorization, "actual new/old free field survives parsing");
                    require(LibraryMetadataReader.fromYsm(manifest, false).requiresAuthorizationDefault()
                            == authorization, "YSM upload authorization default");
                    var catalog = LibraryMetadataReader.fromYsm(manifest, false);
                    require(catalog.internalName().equals("测试模型") && catalog.authors().equals(List.of("作者"))
                                    && catalog.description().equals("模型说明") && manifest.name().contains("§"),
                            "new/old YSM catalog text is plain without changing the source manifest");
                }
            }
        }
        for (boolean free : new boolean[]{false, true}) {
            try (var binary = new YsmBinaryModel()) {
                binary.mainEntity.mainModel = new YsmBinaryModel.RawGeometry();
                binary.mainEntity.mainModel.textureWidth = 16;
                binary.mainEntity.mainModel.textureHeight = 16;
                binary.properties.isFree = free;
                try (var mapped = YsmBinaryResourceMapper.map(binary, "synthetic", new YsmDecodeBudget())) {
                    require(mapped.manifest().free() == free, "binary properties.isFree survives direct mapping");
                    require(YsmManifestReader.read(mapped.resources()).free() == free,
                            "binary generated ysm.json properties.free agrees with its manifest");
                    require(LibraryMetadataReader.fromYsm(mapped.manifest(), true).requiresAuthorizationDefault()
                            == !free, "encrypted model upload default");
                }
            }
        }
        var named = LibraryMetadataReader.fromFigura(AvatarMetadataParser.read(
                "{\"name\":\"内部名称\",\"authors\":[\"作者一\",\"\",\" 作者二 \",\"作者一\"],\"free\":true}"), "fallback");
        require(named.authors().equals(List.of("作者一", "作者二")) && named.internalName().equals("内部名称"),
                "internal name and ordered authors are derived from the model");
        testZipContentAndCancellation();
        System.out.println("PASS library metadata: new/old/binary free true/false/missing, Figura defaults, authors, ZIP content and cancellation");
    }

    private static void testZipContentAndCancellation() throws Exception {
        Path directory = Files.createTempDirectory("library-metadata-");
        try {
            Path figura = directory.resolve("figura.zip");
            writeZip(figura, Map.of("nested/avatar.json", "{\"free\":true,\"author\":\"作者\"}",
                    "nested/main.json", "{}"));
            var metadata = LibraryMetadataReader.read(figura, () -> false);
            require(metadata.modelType().equals("FIGURA") && !metadata.requiresAuthorizationDefault()
                            && metadata.authors().equals(List.of("作者")),
                    "ZIP content chooses Figura even with an unrelated main.json");
            Path disguised = directory.resolve("unknown.ysm");
            writeZip(disguised, Map.of("anything.json", "{}"));
            expectFailure(() -> LibraryMetadataReader.read(disguised, () -> false), "UNRECOGNIZED_SOURCE");
            expectFailure(() -> LibraryMetadataReader.read(figura, () -> true), "CANCELLED");
        } finally {
            try (var files = Files.walk(directory)) {
                for (Path file : files.sorted(Comparator.reverseOrder()).toList()) {
                    Files.delete(file);
                }
            }
        }
    }

    private static void writeZip(Path target, Map<String, String> files) throws IOException {
        try (var zip = new ZipOutputStream(Files.newOutputStream(target))) {
            for (var file : files.entrySet()) {
                zip.putNextEntry(new ZipEntry(file.getKey()));
                zip.write(file.getValue().getBytes(StandardCharsets.UTF_8));
                zip.closeEntry();
            }
        }
    }

    private interface Action {
        void run() throws Exception;
    }

    private static void expectFailure(Action action, String code) throws Exception {
        try {
            action.run();
            throw new AssertionError("Expected " + code);
        } catch (IOException failure) {
            require(failure.getMessage().startsWith(code), "stable metadata failure category");
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
