package top.aihmc.omnimorph.avatar.ysm;

import top.aihmc.omnimorph.avatar.ysm.binary.YsmBinaryModel;
import top.aihmc.omnimorph.avatar.ysm.binary.YsmBinaryResourceMapper;
import top.aihmc.omnimorph.avatar.ysm.crypto.YsmDecodeBudget;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Checks declared material channels through source/manifest/runtime input; GPU consumption is a separate gate. */
public final class YsmMaterialInputSmokeTest {
    public static void main(String[] args) throws Exception {
        Path directory = Files.createTempDirectory("figura-material-input-").toAbsolutePath().normalize();
        String previous = System.getProperty("figura.test.directory");
        System.setProperty("figura.test.directory", directory.toString());
        try {
            testFolderDeclarations();
            testBinaryChannels();
            if (args.length != 1) {
                throw new IllegalArgumentException("Provide the model corpus directory");
            }
            testCorpus(Path.of(args[0]));
            System.out.println("M5 material input: plain/binary normal-specular pairing, runtime bytes and private payload boundary PASS; GPU/shader pending.");
        } finally {
            if (previous == null) {
                System.clearProperty("figura.test.directory");
            } else {
                System.setProperty("figura.test.directory", previous);
            }
            try (var paths = Files.walk(directory)) {
                for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
                    if (!path.toAbsolutePath().normalize().startsWith(directory)) {
                        throw new IllegalStateException("Unexpected material fixture cleanup path");
                    }
                    Files.delete(path);
                }
            }
        }
    }

    private static void testFolderDeclarations() throws Exception {
        byte[] base = png(0xff112233);
        byte[] normal = png(0xff8080ff);
        byte[] specular = png(0xff7799aa);
        Map<String, byte[]> resources = new LinkedHashMap<>();
        resources.put("ysm.json", bytes("""
                {"metadata":{"name":"Material fixture"},"files":{"player":{"model":{"main":"main.json"},
                 "texture":[{"id":"paired","uv":"textures/base.png","normal":"textures/normal.png","specular":"textures/specular.png"},
                            {"id":"plain","uv":"textures/plain.png","normal":"missing.png"}]},
                 "vehicles":{"minecraft:minecart":{"model":"main.json","texture":[
                   {"uv":"textures/base.png","normal":"textures/normal.png","specular":"textures/specular.png"}]}}},
                 "properties":{"default_texture":"paired"}}
                """));
        resources.put("main.json", bytes("""
                {"minecraft:geometry":[{"description":{"identifier":"geometry.material","texture_width":1,"texture_height":1},
                 "bones":[{"name":"root"}]}]}
                """));
        resources.put("textures/base.png", base);
        resources.put("textures/normal.png", normal);
        resources.put("textures/specular.png", specular);
        resources.put("textures/plain.png", base);
        try (var source = new YsmMemoryResourceSource("fixture", resources);
             var data = YsmModelData.read(source, YsmSourceIdentity.ContainerKind.DIRECTORY, () -> false)) {
            YsmManifest manifest = data.manifest();
            require(manifest.textures().size() == 2, "PBR channels are not extra selectable skins");
            YsmTextureOption paired = manifest.textures().getFirst();
            require(paired.id().equals("paired") && paired.normalPath().equals("textures/normal.png")
                    && paired.specularPath().equals("textures/specular.png"), "declared channel paths retained per skin");
            require(manifest.resourceIndex().texturePaths().containsAll(List.of("textures/base.png", "textures/normal.png", "textures/specular.png")),
                    "all material channels are indexed as resources");
            require(manifest.textures().get(1).normalPath().isEmpty()
                    && manifest.resourceIndex().warnings().stream().anyMatch(warning -> warning.contains("normal")),
                    "missing optional normal keeps the base material with a diagnostic");
            var tag = YsmAvatarLoader.toRuntimeTag(data, paired);
            var first = tag.getListOrEmpty("texture_entries").getCompoundOrEmpty(0);
            require(Arrays.equals(base, first.getByteArray("data").orElseThrow()), "primary bytes assembled");
            require(Arrays.equals(normal, first.getByteArray("normal_data").orElseThrow()), "normal bytes assembled without conversion or loss");
            require(Arrays.equals(specular, first.getByteArray("specular_data").orElseThrow()), "specular bytes assembled without conversion or loss");
            require(!tag.getListOrEmpty("texture_entries").getCompoundOrEmpty(1).contains("normal_data"), "plain skin does not inherit a prior skin's channel");
            var child = tag.getListOrEmpty("sub_entities").getCompoundOrEmpty(0);
            require(Arrays.equals(base, child.getByteArray("texture").orElseThrow())
                            && Arrays.equals(normal, child.getByteArray("normal_texture").orElseThrow())
                            && Arrays.equals(specular, child.getByteArray("specular_texture").orElseThrow()),
                    "sub-entity material array preserves selected base/normal/specular bytes through the formal loader");
        }
    }

    private static void testBinaryChannels() throws Exception {
        try (YsmBinaryModel raw = new YsmBinaryModel()) {
            raw.metadata.name = "binary fixture";
            raw.mainEntity.mainModel = new YsmBinaryModel.RawGeometry();
            var root = new YsmBinaryModel.RawBone();
            root.name = "root";
            raw.mainEntity.mainModel.bones.add(root);
            var texture = new YsmBinaryModel.RawTexture();
            texture.name = "paired";
            texture.width = texture.height = 1;
            texture.imageFormat = -1;
            texture.data = new byte[]{10, 20, 30, (byte) 255};
            for (int type : new int[]{1, 2, 9}) {
                var channel = new YsmBinaryModel.RawTexture.SubTexture();
                channel.specularType = type;
                channel.width = channel.height = 1;
                channel.imageFormat = -1;
                channel.data = new byte[]{(byte) (20 * type), 40, 80, (byte) 255};
                texture.subTextures.add(channel);
            }
            raw.mainEntity.textures.put("paired", texture);
            var projectile = new YsmBinaryModel.RawSubEntity();
            projectile.identifier = "minecraft:arrow";
            projectile.model = raw.mainEntity.mainModel;
            projectile.textures.put("paired", texture);
            raw.projectiles.put(projectile.identifier, projectile);
            try (var mapping = YsmBinaryResourceMapper.map(raw, "binary fixture", new YsmDecodeBudget())) {
                YsmTextureOption paired = mapping.manifest().textures().getFirst();
                require(paired.normalPath().equals("textures/player/0_sub_0.png")
                        && paired.specularPath().equals("textures/player/0_sub_1.png"), "reference type1 normal/type2 specular routed independently");
                require(ImageIO.read(new java.io.ByteArrayInputStream(mapping.resources().readBytes(paired.normalPath()))).getRGB(0, 0) == 0xff142850,
                        "normal pixels come from type1");
                require(ImageIO.read(new java.io.ByteArrayInputStream(mapping.resources().readBytes(paired.specularPath()))).getRGB(0, 0) == 0xff282850,
                        "specular pixels come from type2");
                require(mapping.resources().exists("textures/player/0_sub_2.encoded")
                        && mapping.resources().exists("textures/player/0_sub_2.png"), "unknown subtype data remains preserved without pretending it is a supported channel");
                var reread = YsmManifestReader.read(mapping.resources()).textures().getFirst();
                require(reread.equals(paired), "mapped manifest preserves the same material identity and channel links");
                var manifest = com.google.gson.JsonParser.parseString(mapping.resources().readString("ysm.json")).getAsJsonObject();
                var childMaterial = manifest.getAsJsonObject("files").getAsJsonArray("projectiles")
                        .get(0).getAsJsonObject().getAsJsonArray("texture").get(0).getAsJsonObject();
                require(childMaterial.get("normal").getAsString().endsWith("0_sub_0.png")
                                && childMaterial.get("specular").getAsString().endsWith("0_sub_1.png"),
                        "binary sub-entity material mapping retains channel associations instead of flattening to base paths");
            }
        }
    }

    private static void testCorpus(Path root) throws Exception {
        int models = 0;
        int encrypted = 0;
        int materials = 0;
        int normals = 0;
        int speculars = 0;
        try (var paths = Files.list(root)) {
            for (Path path : paths.sorted().toList()) {
                if (!YsmAvatarDetector.isYsmAvatar(path)) {
                    continue;
                }
                try (YsmModelData data = YsmModelData.read(path, () -> false)) {
                    models++;
                    if (data.encrypted()) {
                        encrypted++;
                    }
                    var entries = YsmAvatarLoader.toRuntimeTag(data, data.manifest().textures().getFirst()).getListOrEmpty("texture_entries");
                    int entryIndex = 0;
                    for (YsmTextureOption texture : data.manifest().textures()) {
                        if (!data.resources().exists(texture.path())) {
                            continue;
                        }
                        var entry = entries.getCompoundOrEmpty(entryIndex++);
                        require(entry.getStringOr("id", "").equals(texture.id()), "runtime preserves skin ordering/identity");
                        require(Arrays.equals(entry.getByteArray("data").orElseThrow(), data.resources().readBytes(texture.path())), "corpus base bytes retained");
                        for (String channel : List.of("normal", "specular")) {
                            String resource = channel.equals("normal") ? texture.normalPath() : texture.specularPath();
                            if (!resource.isBlank()) {
                                require(Arrays.equals(entry.getByteArray(channel + "_data").orElseThrow(), data.resources().readBytes(resource)),
                                        "corpus channel stays paired with its base skin");
                                if (channel.equals("normal")) {
                                    normals++;
                                } else {
                                    speculars++;
                                }
                            }
                        }
                        materials++;
                    }
                    if (data.encrypted()) {
                        var portable = YsmAvatarLoader.toAvatarNbt(data, data.manifest().textures().getFirst(), new java.util.UUID(0, 1), path)
                                .getCompoundOrEmpty("ysm");
                        require(!portable.contains("texture_entries") && !portable.contains("normal_data") && !portable.contains("specular_data"),
                                "encrypted channel bytes do not enter portable Avatar NBT");
                    }
                }
            }
        }
        require(models == 40 && encrypted == 5, "all plain and encrypted YSM materials audited");
        System.out.println("M5 material corpus: models=" + models + " encrypted=" + encrypted
                + " materials=" + materials + " normals=" + normals + " speculars=" + speculars);
    }

    private static byte[] png(int color) throws Exception {
        BufferedImage image = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
        image.setRGB(0, 0, color);
        try (var output = new ByteArrayOutputStream()) {
            require(ImageIO.write(image, "PNG", output), "PNG writer available");
            return output.toByteArray();
        } finally {
            image.flush();
        }
    }

    private static byte[] bytes(String value) {
        return value.getBytes(StandardCharsets.UTF_8);
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
