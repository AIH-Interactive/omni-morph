package org.figuramc.figura.avatar.ysm;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

/** Checks decoded content after compression, rather than grepping compressed bytes. */
public final class YsmPayloadSmokeTest {
    private static final String MARKER = "PRIVATE_DECRYPTED_MODEL_TEXTURE_FUNCTION_MARKER";

    private YsmPayloadSmokeTest() {
    }

    public static void main(String[] args) throws Exception {
        byte[] ciphertext;
        try (var stream = YsmPayloadSmokeTest.class.getResourceAsStream("/ysm/crypto-vectors/crypto1.ysm")) {
            ciphertext = stream.readAllBytes();
        }
        CompoundTag descriptor;
        try (var source = YsmEncryptedSource.read(ciphertext, "fixture.ysm", () -> false)) {
            descriptor = YsmSourcePayload.fromEncrypted(source, YsmAvatarKind.OLD, "body");
        }
        CompoundTag avatar = new CompoundTag();
        CompoundTag metadata = new CompoundTag();
        metadata.putString("format", "ysm-native");
        metadata.putString("name", "fixture");
        metadata.putString("source_path", MARKER);
        avatar.put("metadata", metadata);
        avatar.put("ysm", descriptor);
        for (String field : List.of("main_model", "arm_model", "texture", "functions", "animations", "sub_entities")) {
            descriptor.putByteArray(field, MARKER.getBytes(StandardCharsets.UTF_8));
        }
        descriptor.putString("source_path", "D:/private/" + MARKER);
        CompoundTag nested = new CompoundTag();
        nested.putString("decrypted", MARKER);
        descriptor.put("resource_index", nested);
        avatar.put("resources", nested.copy());
        avatar.put("sounds", nested.copy());
        avatar.put("scripts", nested.copy());
        CompoundTag portable = YsmSourcePayload.forOutput(avatar);
        require(portable.keySet().equals(Set.of("metadata", "ysm")), "encrypted root whitelist");
        require(Arrays.equals(ciphertext, portable.getCompoundOrEmpty("ysm").getByteArray("source_ciphertext").orElseThrow()),
                "original ciphertext byte equivalence");
        require(descriptor.contains("main_model") && metadata.contains("source_path"), "output filter preserves caller-owned input");
        require(!portable.toString().contains(MARKER), "portable NBT has no decoded markers");
        Path directory = Files.createTempDirectory("ysm-payload-");
        try {
            Path file = directory.resolve("avatar.nbt");
            NbtIo.writeCompressed(portable, file);
            try (var input = Files.newInputStream(file)) {
                CompoundTag reread = NbtIo.readCompressed(input, NbtAccounter.create(128L * 1024 * 1024));
                require(!reread.toString().contains(MARKER), "disk contents checked after decompression");
                require(reread.equals(portable), "portable disk round trip");
            }
        } finally {
            Files.deleteIfExists(directory.resolve("avatar.nbt"));
            Files.delete(directory);
        }
        descriptor.remove("source_ciphertext");
        expectFailure(avatar);
        descriptor.putByteArray("source_ciphertext", ciphertext);
        descriptor.putString("content_sha256", "bad");
        expectFailure(avatar);
        descriptor.putString("content_sha256", portable.getCompoundOrEmpty("ysm").getStringOr("content_sha256", ""));
        descriptor.putInt("crypto_version", 99);
        expectFailure(avatar);
        CompoundTag ordinary = new CompoundTag();
        ordinary.put("models", nested.copy());
        require(YsmSourcePayload.forOutput(ordinary).equals(ordinary), "ordinary Figura output preserved");
        CompoundTag plain = new CompoundTag();
        plain.put("metadata", metadata.copy());
        CompoundTag plainYsm = new CompoundTag();
        plainYsm.putString("source_path", "D:/private/model");
        plainYsm.putString("main_model", "ordinary unencrypted JSON");
        plain.put("ysm", plainYsm);
        CompoundTag plainOutput = YsmSourcePayload.forOutput(plain);
        require(!plainOutput.getCompoundOrEmpty("ysm").contains("source_path"), "plain YSM local path filtered");
        require(plainOutput.getCompoundOrEmpty("ysm").contains("main_model"), "plain YSM model content preserved");
        System.out.println("YSM ciphertext payload whitelist and compressed disk leak checks passed.");
    }

    private static void expectFailure(CompoundTag avatar) throws Exception {
        try {
            YsmSourcePayload.forOutput(avatar);
        } catch (java.io.IOException expected) {
            require(expected.getMessage().startsWith("INVALID_DATA:"), "fail-closed payload category");
            return;
        }
        throw new AssertionError("Invalid encrypted payload accepted");
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
