package top.aihmc.omnimorph.avatar.ysm.binary;

import top.aihmc.omnimorph.avatar.ysm.crypto.YsmDecodeBudget;
import top.aihmc.omnimorph.avatar.ysm.crypto.YsmDecodeException;
import top.aihmc.omnimorph.avatar.ysm.YsmEncryptedSource;
import top.aihmc.omnimorph.model.ysm.animation.YsmAnimationParser;
import top.aihmc.omnimorph.model.ysm.YsmGeometryParser;

import javax.imageio.ImageIO;
import java.io.ByteArrayInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Map;

public final class YsmBinaryResourcesSmokeTest {
    private YsmBinaryResourcesSmokeTest() {
    }

    public static void main(String[] args) throws Exception {
        testGeometry();
        testImages();
        testAnimationMapping();
        testLegacySourceOwnership();
        testMetadata();
        testJsonFailureClassification();
        if (args.length != 0) {
            testCorpus(Path.of(args[0]));
            testMappedCorpus(Path.of(args[0]));
        }
        System.out.println("YSM binary geometry and image resource smoke passed.");
    }

    private static void testGeometry() throws Exception {
        String json = """
                {"minecraft:geometry":[{"description":{"texture_width":64,"texture_height":64},"bones":[
                  {"name":"root","pivot":[16,16,16],"rotation":[90,-45,-30],"cubes":[
                    {"origin":[0,0,0],"size":[16,16,16],"uv":{"east":{"uv":[0,0],"uv_size":[16,16]}}}
                  ]}, {"name":"child","parent":"root","pivot":[0,16,0]}]}]}
                """;
        var expected = YsmGeometryParser.parse(json);
        var raw = new YsmBinaryModel.RawGeometry();
        var root = new YsmBinaryModel.RawBone();
        root.name = "root";
        root.parentName = "";
        root.pivot = new float[]{-16, 16, 16};
        root.rotation = new float[]{(float) -Math.PI / 2, (float) Math.PI / 4, (float) -Math.PI / 6};
        var cube = new YsmBinaryModel.RawCube();
        var face = new YsmBinaryModel.RawFace();
        face.positions = new float[][]{{0, 1, 0}, {0, 1, 1}, {0, 0, 1}, {0, 0, 0}};
        face.normal = new float[]{1, 0, 0};
        face.u = new float[]{0.25f, 0, 0, 0.25f};
        face.v = new float[]{0, 0, 0.25f, 0.25f};
        cube.faces.add(face);
        root.cubes.add(cube);
        raw.bones.add(root);
        var child = new YsmBinaryModel.RawBone();
        child.name = "child";
        child.parentName = "root";
        child.pivot = new float[]{0, 16, 0};
        raw.bones.add(child);
        var actual = YsmBinaryGeometryMapper.map(raw, new YsmDecodeBudget());
        require(actual.roots.size() == 1 && actual.roots.getFirst().children.size() == 1, "hierarchy retained");
        var expectedBone = expected.bones.get("root");
        var actualBone = actual.bones.get("root");
        equal(expectedBone.pivot, actualBone.pivot);
        equal(expectedBone.rotation, actualBone.rotation);
        var expectedFace = expectedBone.cubes.getFirst().quads.getFirst();
        var actualFace = actualBone.cubes.getFirst().quads.getFirst();
        equal(expectedFace.positions(), actualFace.positions());
        equal(expectedFace.uvs(), actualFace.uvs());
        equal(expectedFace.normal(), actualFace.normal());
        root.pivot[0] = 100;
        face.positions[0][0] = 100;
        require(actualBone.pivot[0] == -16 && actualFace.positions()[0] == 0, "raw buffers isolated");
        child.parentName = "child";
        expectFailure(() -> YsmBinaryGeometryMapper.map(raw, new YsmDecodeBudget()), "INVALID_DATA:");
        child.parentName = "absent";
        expectFailure(() -> YsmBinaryGeometryMapper.map(raw, new YsmDecodeBudget()), "INVALID_DATA:");
        child.parentName = "root";
        child.name = "root";
        expectFailure(() -> YsmBinaryGeometryMapper.map(raw, new YsmDecodeBudget()), "INVALID_DATA:");
    }

    private static void testJsonFailureClassification() throws Exception {
        var constructor = YsmBinaryResourceMapper.class.getDeclaredConstructor(YsmDecodeBudget.class);
        constructor.setAccessible(true);
        var json = YsmBinaryResourceMapper.class.getDeclaredMethod("json", String.class,
                com.google.gson.JsonObject.class);
        json.setAccessible(true);
        var value = new com.google.gson.JsonObject();
        value.addProperty("synthetic", "x".repeat(20000));
        var limits = new top.aihmc.omnimorph.avatar.ysm.YsmResourceLimits(1024, 1, 1, 4096);
        var mapper = constructor.newInstance(new YsmDecodeBudget(limits, () -> false, 10000));
        try {
            json.invoke(mapper, "fixture.json", value);
            throw new AssertionError("JSON resource limit was ignored");
        } catch (java.lang.reflect.InvocationTargetException failure) {
            require(failure.getCause() instanceof YsmDecodeException decoded
                            && decoded.code() == YsmDecodeException.Code.RESOURCE_LIMIT,
                    "JSON resource failures retain their category through Gson");
        }
    }

    private static void testImages() throws Exception {
        byte[] rgba = {(byte) 255, 0, 0, (byte) 255, 0, (byte) 255, 0, 64};
        byte[] before = rgba.clone();
        byte[] png = YsmTextureDecoder.toPng(rgba, -1, 2, 1, new YsmDecodeBudget());
        var image = ImageIO.read(new ByteArrayInputStream(png));
        require(image.getRGB(0, 0) == 0xffff0000 && image.getRGB(1, 0) == 0x4000ff00, "RGBA ordering and alpha");
        require(Arrays.equals(before, rgba), "decoder does not wipe caller-owned input");
        byte[] roundTrip = YsmTextureDecoder.toPng(png, 2, 2, 1, new YsmDecodeBudget());
        require(ImageIO.read(new ByteArrayInputStream(roundTrip)).getRGB(1, 0) == 0x4000ff00, "PNG pixel equivalence");
        for (var fixture : Map.of("small_1x1.webp", 1, "small_1x13.webp", 13).entrySet()) {
            byte[] webp;
            try (var input = YsmBinaryResourcesSmokeTest.class.getResourceAsStream("/ysm/image-vectors/" + fixture.getKey())) {
                webp = input.readAllBytes();
            }
            byte[] result = YsmTextureDecoder.toPng(webp, 4, 1, fixture.getValue(), new YsmDecodeBudget());
            var decoded = ImageIO.read(new ByteArrayInputStream(result));
            require(decoded.getWidth() == 1 && decoded.getHeight() == fixture.getValue(), "fixed WebP dimensions");
        }
        expectFailure(() -> YsmTextureDecoder.toPng(rgba, -1, 1, 1, new YsmDecodeBudget()), "INVALID_DATA:");
        expectFailure(() -> YsmTextureDecoder.toPng(png, 2, 3, 1, new YsmDecodeBudget()), "INVALID_DATA:");
        expectFailure(() -> YsmTextureDecoder.toPng(rgba, -1, 16777217, 1, new YsmDecodeBudget()), "RESOURCE_LIMIT:");
        expectFailure(() -> YsmTextureDecoder.toPng(new byte[3], 2, 1, 1, new YsmDecodeBudget()), "UNSUPPORTED_VERSION:");
        var budget = new YsmDecodeBudget();
        for (int index = 0; index < 4; index++) {
            budget.pixels(4096, 4096, "fixture", 0);
        }
        expectFailure(() -> YsmTextureDecoder.toPng(rgba, -1, 2, 1, budget), "RESOURCE_LIMIT:");
    }

    private static void testCorpus(Path root) throws Exception {
        int files = 0;
        try (var paths = Files.list(root)) {
            for (Path path : paths.filter(file -> file.getFileName().toString().endsWith(".ysm")).sorted().toList()) {
                if (path.getFileName().toString().equals("old.ysm")) {
                    continue; // Crypto 2 has a resource collection, not a binary payload.
                }
                require(YsmEncryptedSource.hasMagic(path), "bounded encrypted header probe");
                try (var source = YsmEncryptedSource.read(path, () -> false)) {
                    var model = source.binary();
                    var budget = source.budget();
                    require(source.identity().cryptoVersion() == 3
                            && source.identity().binaryFormatVersion() == model.formatVersion, "separate crypto/format identity");
                    require(Arrays.equals(source.ciphertextCopy(), Files.readAllBytes(path)), "original ciphertext preserved");
                    var geometry = YsmBinaryGeometryMapper.map(model.mainEntity.mainModel, budget);
                    require(!geometry.bones.isEmpty(), "corpus main geometry");
                    if (model.mainEntity.armModel != null) {
                        YsmBinaryGeometryMapper.map(model.mainEntity.armModel, budget);
                    }
                    int keyframes = mapAnimations(model.mainEntity.animationFiles, budget);
                    int textures = decodeTextures(model.mainEntity.textures, budget);
                    for (var entity : model.vehicles.values()) {
                        YsmBinaryGeometryMapper.map(entity.model, budget);
                        textures += decodeTextures(entity.textures, budget);
                        keyframes += mapAnimations(entity.animationFiles, budget);
                    }
                    for (var entity : model.projectiles.values()) {
                        YsmBinaryGeometryMapper.map(entity.model, budget);
                        textures += decodeTextures(entity.textures, budget);
                        keyframes += mapAnimations(entity.animationFiles, budget);
                    }
                    System.out.println("BINARY_RESOURCE_OK " + path.getFileName() + " format=" + model.formatVersion
                            + " bones=" + geometry.bones.size() + " textures=" + textures + " keyframes=" + keyframes);
                    files++;
                }
            }
        }
        require(files == 4, "four local crypto 3 samples checked");
    }

    private static void testAnimationMapping() throws Exception {
        var file = new YsmBinaryModel.RawAnimationFile();
        var clip = new YsmBinaryModel.RawAnimation();
        clip.name = "animation.main.fixture";
        clip.length = 2;
        clip.loopMode = 3;
        clip.blendWeight = "v.weight";
        var bone = new YsmBinaryModel.RawBoneAnimation();
        bone.boneName = "root";
        var frame = new YsmBinaryModel.RawKeyframe();
        frame.timestamp = 0.5f;
        frame.interpolationMode = 2;
        frame.hasPreData = true;
        frame.preData = new Object[]{1f, 2f, 3f};
        frame.postData = new Object[]{4f, 5f, 6f};
        bone.rotation.add(frame);
        clip.boneAnimations.add(bone);
        var timeline = new YsmBinaryModel.RawTimelineEvent();
        timeline.timestamp = 0.25f;
        timeline.events.add("v.marker = 1;");
        clip.timelineEvents.add(timeline);
        file.animations.put(clip.name, clip);
        var mapped = YsmBinaryAnimationMapper.animations(file, new YsmDecodeBudget());
        var mappedClip = mapped.getAsJsonObject("animations").getAsJsonObject(clip.name);
        require(mappedClip.get("blend_weight").getAsString().equals("v.weight"), "expression weight retained");
        var actual = YsmAnimationParser.parse(mapped.toString(), false, null).get(clip.name);
        var expected = YsmAnimationParser.parse("""
                {"animations":{"animation.main.fixture":{"animation_length":2,"loop":"hold_on_last_frame",
                "bones":{"root":{"rotation":{"0.5":{"lerp_mode":"catmullrom","pre":[1,2,3],"post":[4,5,6]}}}},
                "timeline":{"0.25":["v.marker = 1;"]}}}}
                """, false, null).get(clip.name);
        require(actual != null && actual.length == expected.length && actual.loopMode == expected.loopMode,
                "JSON/binary duration and loop equivalence");
        var actualFrame = actual.boneAnimations.get("root").rotation.keyframes.getFirst();
        var expectedFrame = expected.boneAnimations.get("root").rotation.keyframes.getFirst();
        equal(expectedFrame.value, actualFrame.value);
        equal(expectedFrame.preValue, actualFrame.preValue);
        require(actualFrame.time == expectedFrame.time && actualFrame.interpolation.equals(expectedFrame.interpolation),
                "JSON/binary keyframe equivalence");
        require(actual.events.equals(expected.events), "JSON/binary timeline equivalence");
        clip.loopMode = 2;
        clip.length = Float.POSITIVE_INFINITY;
        mappedClip = YsmBinaryAnimationMapper.animations(file, new YsmDecodeBudget())
                .getAsJsonObject("animations").getAsJsonObject(clip.name);
        require(!mappedClip.has("loop") && !mappedClip.has("animation_length")
                && mappedClip.get("ysm_duration_unbounded").getAsBoolean(), "omitted binary defaults retained");
    }

    private static void testLegacySourceOwnership() throws Exception {
        for (int version : new int[]{1, 2}) {
            byte[] bytes;
            try (var input = YsmBinaryResourcesSmokeTest.class.getResourceAsStream("/ysm/crypto-vectors/crypto" + version + ".ysm")) {
                bytes = input.readAllBytes();
            }
            byte[] before = bytes.clone();
            var source = YsmEncryptedSource.read(bytes, "fixture", () -> false);
            var legacy = source.legacyResources();
            require(source.identity().cryptoVersion() == version && source.identity().binaryFormatVersion() == 0,
                    "legacy identity has no binary version");
            byte[] persisted = source.ciphertextCopy();
            source.close();
            require(Arrays.equals(before, persisted) && Arrays.equals(before, bytes), "close clears only owned source buffers");
            try {
                legacy.resourcePaths();
                throw new AssertionError("Closed legacy source remained open");
            } catch (java.io.IOException expected) {
                require(expected.getMessage().startsWith("CLOSED_SOURCE:"), "legacy resources closed with owner");
            }
        }
    }

    private static void testMetadata() throws Exception {
        try (var model = new YsmBinaryModel()) {
            model.formatVersion = 32;
            model.metadata.name = "fixture name";
            model.metadata.tips = "fixture tips";
            model.metadata.licenseType = "fixture license";
            model.metadata.licenseDescription = "fixture license description";
            model.metadata.links.put("home", "https://example.invalid");
            var author = new YsmBinaryModel.RawMetadata.Author();
            author.name = "fixture author";
            author.role = "model";
            author.comment = "comment";
            author.contacts.put("contact", "fixture contact");
            author.avatarImage = new YsmBinaryModel.RawImage();
            author.avatarImage.name = "avatar";
            author.avatarImage.width = 1;
            author.avatarImage.height = 1;
            author.avatarImage.format = -1;
            author.avatarImage.unknownFlag = 7;
            author.avatarImage.data = new byte[]{1, 2, 3, 4};
            model.metadata.authors.add(author);
            model.properties.isFree = true;
            model.properties.mergeMultilineExpr = true;
            model.properties.widthScale = 0.5f;
            model.properties.heightScale = 0.75f;
            model.properties.defaultTexture = "fixture texture";
            model.properties.previewAnimation = "gui";
            model.properties.extraAnimations.put("extra0", "#settings");
            var classification = new YsmBinaryModel.ExtraAnimationClassify();
            classification.id = "movement";
            classification.extras.put("extra1", "walk");
            model.properties.extraAnimationClassifies.add(classification);
            var button = new YsmBinaryModel.ExtraAnimationButton();
            button.id = "settings";
            button.name = "Settings";
            button.description = "fixture settings";
            var form = new YsmBinaryModel.ConfigForm();
            form.type = "range";
            form.title = "Range";
            form.description = "fixture range";
            form.defaultValue = "v.fixture_value";
            form.min = -1;
            form.max = 2;
            form.step = 0.2f;
            form.labels.put("option", "v.fixture_value = 1;");
            button.forms.add(form);
            model.properties.extraAnimationButtons.add(button);
            model.footer.version = 32;
            model.footer.rand = "fixture random";
            model.footer.extra = "fixture footer";
            model.footer.time = 1234567890123L;
            int[] images = {0};
            var mapped = YsmBinaryMetadataMapper.map(model, image -> {
                require(image.data == author.avatarImage.data, "image handed to resource sink without changing bytes");
                images[0]++;
                return "metadata/images/avatar.png";
            }, new YsmDecodeBudget());
            var metadata = mapped.getAsJsonObject("metadata");
            require(metadata.get("name").getAsString().equals(model.metadata.name), "name retained");
            require(metadata.getAsJsonObject("license").get("description").getAsString()
                    .equals(model.metadata.licenseDescription), "license description retained");
            require(images[0] == 1 && metadata.getAsJsonArray("authors").get(0).getAsJsonObject()
                    .get("avatar").getAsString().equals("metadata/images/avatar.png"), "avatar resource path retained");
            var properties = mapped.getAsJsonObject("properties");
            require(properties.get("free").getAsBoolean() && properties.get("merge_multiline_expr").getAsBoolean(),
                    "reference property names retained");
            var mappedForm = properties.getAsJsonArray("extra_animation_buttons").get(0).getAsJsonObject()
                    .getAsJsonArray("config_forms").get(0).getAsJsonObject();
            require(mappedForm.get("value").getAsString().equals("v.fixture_value") && !mappedForm.has("default"),
                    "value expression is not converted into a literal default");
            require(mappedForm.get("min").getAsFloat() == -1 && mappedForm.get("max").getAsFloat() == 2,
                    "form range retained");
            require(mapped.getAsJsonObject("ysm_binary_footer").get("time").getAsLong() == model.footer.time,
                    "footer timestamp is not truncated");
            require(!metadata.toString().contains("\"data\""), "metadata references images without embedding bytes");
        }
    }

    private static int decodeTextures(Map<String, YsmBinaryModel.RawTexture> textures,
                                       YsmDecodeBudget budget) throws Exception {
        int count = 0;
        for (var texture : textures.values()) {
            byte[] png = YsmTextureDecoder.toPng(texture.data, texture.imageFormat, texture.width, texture.height, budget);
            Arrays.fill(png, (byte) 0);
            count++;
            for (var sub : texture.subTextures) {
                png = YsmTextureDecoder.toPng(sub.data, sub.imageFormat, sub.width, sub.height, budget);
                Arrays.fill(png, (byte) 0);
                count++;
            }
        }
        return count;
    }

    private static void testMappedCorpus(Path root) throws Exception {
        try (var paths = Files.list(root)) {
            for (Path path : paths.filter(file -> file.getFileName().toString().endsWith(".ysm")
                    && !file.getFileName().toString().equals("old.ysm")).sorted().toList()) {
                try (var source = YsmEncryptedSource.read(path, () -> false)) {
                    try (var mapped = YsmBinaryResourceMapper.map(source.binary(), source.displayName(), source.budget())) {
                        var resources = mapped.resources();
                        var manifest = mapped.manifest();
                        require(mapped.geometries().containsKey(manifest.mainModelPath()), "main remains direct geometry");
                        require(!resources.exists(manifest.mainModelPath()), "no generated cube/model JSON");
                        for (var texture : manifest.textures()) {
                            require(resources.readBytes(texture.path()).length > 0, "mapped PNG texture available");
                        }
                        for (var entry : source.binary().functionFiles.entrySet()) {
                            require(Arrays.equals(entry.getValue().data, resources.readBytes("functions/" + entry.getKey())),
                                    "function bytes unchanged");
                        }
                        for (var entry : source.binary().soundFiles.entrySet()) {
                            require(Arrays.equals(entry.getValue().data, resources.readBytes("sounds/" + entry.getKey())),
                                    "sound bytes unchanged");
                        }
                        for (var entry : source.binary().languageFiles.entrySet()) {
                            var language = com.google.gson.JsonParser.parseString(resources.readString("lang/" + entry.getKey() + ".json"))
                                    .getAsJsonObject();
                            for (var translation : entry.getValue().data.entrySet()) {
                                require(language.get(translation.getKey()).getAsString().equals(translation.getValue()), "translation unchanged");
                            }
                        }
                        int clips = 0;
                        for (String animationPath : manifest.animationPaths()) {
                            var animation = com.google.gson.JsonParser.parseString(resources.readString(animationPath)).getAsJsonObject();
                            clips += animation.getAsJsonObject("animations").size();
                        }
                        int expected = source.binary().mainEntity.animationFiles.values().stream().mapToInt(file -> file.animations.size()).sum();
                        require(clips == expected, "all player clips present after splitting");
                        System.out.println("BINARY_MAPPING_OK " + path.getFileName() + " resources=" + resources.resourcePaths().size()
                                + " geometries=" + mapped.geometries().size() + " playerClips=" + clips);
                    }
                }
            }
        }
    }

    private static int mapAnimations(Map<String, YsmBinaryModel.RawAnimationFile> files,
                                     YsmDecodeBudget budget) throws Exception {
        int count = 0;
        for (var file : files.values()) {
            var json = YsmBinaryAnimationMapper.animations(file, budget);
            require(json.getAsJsonObject("animations").size() == file.animations.size(), "all clips mapped");
            for (var clip : file.animations.values()) {
                for (var bone : clip.boneAnimations) {
                    count += bone.rotation.size() + bone.position.size() + bone.scale.size();
                }
            }
        }
        return count;
    }

    private static void equal(float[] expected, float[] actual) {
        require(expected.length == actual.length, "vector size");
        for (int index = 0; index < expected.length; index++) {
            require(Math.abs(expected[index] - actual[index]) <= 1e-5f, "geometry equality at " + index);
        }
    }

    private static void expectFailure(CheckedAction action, String prefix) throws Exception {
        try {
            action.run();
        } catch (YsmDecodeException expected) {
            require(expected.getMessage().startsWith(prefix), "stable failure category");
            return;
        }
        throw new AssertionError("Malformed input accepted");
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    @FunctionalInterface
    private interface CheckedAction {
        void run() throws Exception;
    }
}
