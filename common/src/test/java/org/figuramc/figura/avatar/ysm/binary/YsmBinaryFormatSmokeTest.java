package org.figuramc.figura.avatar.ysm.binary;

import com.google.gson.JsonParser;
import org.figuramc.figura.avatar.ysm.YsmResourceLimits;
import org.figuramc.figura.avatar.ysm.crypto.YsmDecodeBudget;
import org.figuramc.figura.avatar.ysm.crypto.YsmDecodeException;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.HexFormat;

/** Fixed file vectors and explicit no-DWORD layouts; no runtime cache protocol is implied. */
public final class YsmBinaryFormatSmokeTest {
    private YsmBinaryFormatSmokeTest() {
    }

    public static void main(String[] args) throws Exception {
        var manifest = JsonParser.parseString(new String(resource("manifest.json"), java.nio.charset.StandardCharsets.UTF_8))
                .getAsJsonObject();
        for (var entry : manifest.entrySet()) {
            byte[] bytes = resource(entry.getKey());
            var expected = entry.getValue().getAsJsonObject();
            require(bytes.length == expected.get("bytes").getAsInt(), "fixed fixture length");
            require(HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes))
                    .equals(expected.get("sha256").getAsString()), "fixed fixture digest");
            int format = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).getInt();
            try (var file = YsmBinaryReader.readFilePayload(bytes, budget());
                 var explicit = YsmBinaryReader.readWithoutFormatDword(Arrays.copyOfRange(bytes, 4, bytes.length), format, budget())) {
                verify(file, format);
                verify(explicit, format);
                try (var mappedFile = YsmBinaryResourceMapper.map(file, "fixture", budget());
                     var mappedExplicit = YsmBinaryResourceMapper.map(explicit, "fixture", budget())) {
                    require(mappedFile.manifest().animationPaths().equals(mappedExplicit.manifest().animationPaths()),
                            "layout animation paths equivalent");
                    require(mappedFile.resources().resourcePaths().equals(mappedExplicit.resources().resourcePaths()),
                            "layout resource inventory equivalent");
                    for (String path : mappedFile.resources().resourcePaths()) {
                        require(Arrays.equals(mappedFile.resources().readBytes(path), mappedExplicit.resources().readBytes(path)),
                                "layout resource bytes equivalent: " + path);
                    }
                }
            }
            reject(Arrays.copyOf(bytes, 3));
            reject(Arrays.copyOf(bytes, bytes.length / 2));
            reject(Arrays.copyOf(bytes, bytes.length - 1));
            reject(Arrays.copyOf(bytes, bytes.length + 1));
            System.out.println("BINARY_FORMAT_FIXED_OK format=" + format);
        }
        reject(ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(0).array());
        reject(ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(33).array());
        System.out.println("Fixed binary format boundaries and explicit no-DWORD equivalence passed.");
    }

    private static void verify(YsmBinaryModel model, int format) {
        require(model.formatVersion == format, "format remains separate from crypto");
        var geometry = model.mainEntity.mainModel;
        require(geometry != null && geometry.bones.size() == 1, "nonempty main geometry");
        var bone = geometry.bones.getFirst();
        require(bone.name.equals("root") && Arrays.equals(bone.pivot, new float[]{1, 2, 3}), "bone name and pivot");
        require(bone.cubes.getFirst().faces.getFirst().positions[2][1] == 3, "direct quad vertex");
        require(geometry.footerPad3 == 6 && bone.unkPad5 == 5, "unknown geometry fields retained");
        var animation = model.mainEntity.animationFiles.get("main").animations.get("animation.fixture");
        require(animation.length == 2 && animation.loopMode == 1, "duration ticks and loop");
        var frame = animation.boneAnimations.getFirst().rotation.getFirst();
        require(frame.timestamp == 0.5f && frame.hasPreData && frame.preData[0].equals(1f)
                && frame.postData[2].equals(6f), "pre/post keyframe retained");
        require(Arrays.equals(model.mainEntity.textures.get("body").data, new byte[]{12, 34, 56, (byte) 255}),
                "synthetic RGBA texture");
        if (format > 9) {
            var controller = model.mainEntity.animationControllerFiles.getFirst().controllers.get("controller.fixture");
            require(controller.initialState.equals("default") && controller.states.size() == 1, "nonempty controller");
            require(controller.states.getFirst().soundEffects.size() == (format > 26 ? 1 : 0), "controller version boundary");
        }
        require(model.properties.mergeMultilineExpr == (format >= 32), "format 32 property boundary");
        if (format > 15) {
            require(model.vehicles.size() == 1 && model.projectiles.size() == (format >= 26 ? 1 : 0),
                    "format 26 subentity category boundary");
            var vehicle = model.vehicles.get("minecraft:boat");
            require(vehicle.animationFiles.size() == 2 && vehicle.model.bones.size() == 1, "all subentity animations and geometry");
            require(format <= 26 ? vehicle.matchIds == null : vehicle.matchIds.length == 2,
                    "format 27 match identifiers boundary");
            require(model.languageFiles.get("en_us").data.get("fixture.label").equals("Fixture label"), "language dictionary");
            require(Arrays.equals(model.functionFiles.get("fixture.txt").data,
                    "v.fixture = 1;".getBytes(java.nio.charset.StandardCharsets.UTF_8)), "function bytes");
            require(Arrays.equals(model.soundFiles.get("fixture.sound").data,
                    "synthetic-sound".getBytes(java.nio.charset.StandardCharsets.UTF_8)), "sound bytes");
        }
        if (format >= 9) {
            require(model.footer.time == 123456789 && model.footer.rand.equals("fixture-random"), "file footer layout");
            require(model.footer.unkInt2 == (format >= 24 ? 7 : 0), "format 24 footer boundary");
        }
    }

    private static byte[] resource(String name) throws Exception {
        try (var stream = YsmBinaryFormatSmokeTest.class.getResourceAsStream("/ysm/binary-vectors/" + name)) {
            if (stream == null) {
                throw new AssertionError("Missing fixed fixture " + name);
            }
            return stream.readAllBytes();
        }
    }

    private static YsmDecodeBudget budget() {
        return new YsmDecodeBudget(YsmResourceLimits.DEFAULT, () -> false, 10000);
    }

    private static void reject(byte[] bytes) throws Exception {
        try (var ignored = YsmBinaryReader.readFilePayload(bytes, budget())) {
            throw new AssertionError("Malformed binary accepted");
        } catch (YsmDecodeException expected) {
            require(expected.getCause() == null, "sanitized binary failure");
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
