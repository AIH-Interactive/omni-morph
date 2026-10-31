package top.aihmc.omnimorph.avatar.ysm;

import org.figuramc.figura.utils.NbtDefaults;

import net.minecraft.nbt.CompoundTag;
import top.aihmc.omnimorph.avatar.ysm.crypto.YsmDecodeException;

import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.function.BooleanSupplier;

/** Portable encrypted sources retain ciphertext, bounded identity fields and explicitly authored accessory wrappers. */
public final class YsmSourcePayload {
    private static final List<String> DISPLAY_FIELDS = List.of(
            "name", "authors", "ver", "uuid", "format", "ysm_kind", "ysm_texture", "color");

    private YsmSourcePayload() {
    }

    public static CompoundTag fromEncrypted(YsmEncryptedSource source, YsmAvatarKind kind, String textureId) {
        return descriptor(source.identity(), kind, source.displayName(), source.ciphertextCopy(), textureId);
    }

    public static CompoundTag fromData(YsmModelData data, String textureId) {
        return descriptor(data.identity(), data.manifest().kind(), data.resources().displayName(), data.ciphertextCopy(), textureId);
    }

    private static CompoundTag descriptor(YsmSourceIdentity identity, YsmAvatarKind kind,
                                           String displayName, byte[] ciphertext, String textureId) {
        CompoundTag tag = new CompoundTag();
        tag.putString("kind", kind.name());
        tag.putString("container_kind", identity.containerKind().name());
        tag.putString("content_sha256", identity.contentSha256());
        tag.putInt("engine_schema_version", identity.engineSchemaVersion());
        tag.putInt("crypto_version", identity.cryptoVersion());
        tag.putInt("binary_format_version", identity.binaryFormatVersion());
        tag.putString("display_name", displayName);
        tag.putByteArray("source_ciphertext", ciphertext);
        if (textureId != null && !textureId.isBlank()) {
            tag.putString("texture_id", textureId);
        }
        return tag;
    }

    public static boolean isEncrypted(CompoundTag tag) {
        return tag.contains("source_ciphertext") || tag.getInt("crypto_version") > 0
                || tag.getString("container_kind").equals(YsmSourceIdentity.ContainerKind.YSGP.name());
    }

    public static YsmModelData openData(CompoundTag tag, BooleanSupplier cancelled) throws IOException {
        CompoundTag avatar = new CompoundTag();
        CompoundTag metadata = new CompoundTag();
        metadata.putString("format", "ysm-native");
        avatar.put("metadata", metadata);
        avatar.put("ysm", tag);
        CompoundTag portable = forOutput(avatar).getCompound("ysm");
        YsmModelData data = YsmModelData.readEncrypted(NbtDefaults.findByteArray(portable, "source_ciphertext").orElseThrow(),
                NbtDefaults.getStringOr(portable, "display_name", "ysm"), cancelled);
        if (data.identity().cryptoVersion() != portable.getInt("crypto_version")
                || data.identity().binaryFormatVersion() != portable.getInt("binary_format_version")) {
            data.close();
            throw invalid("payload/source-version-mismatch");
        }
        return data;
    }

    public static CompoundTag forOutput(CompoundTag avatar) throws IOException {
        if (!avatar.getCompound("metadata").getString("format").equals("ysm-native")) {
            return avatar;
        }
        CompoundTag source = avatar.getCompound("ysm");
        if (!isEncrypted(source)) {
            CompoundTag output = avatar.copy();
            output.getCompound("ysm").remove("source_path");
            output.getCompound("metadata").remove("source_path");
            return output;
        }
        byte[] ciphertext = NbtDefaults.findByteArray(source, "source_ciphertext").orElseThrow(() -> invalid("payload/missing-ciphertext"));
        if (ciphertext.length == 0 || ciphertext.length > YsmResourceLimits.DEFAULT.fileBytes()) {
            throw invalid("payload/ciphertext-size");
        }
        if (!YsmEncryptedSource.hasMagic(ciphertext)) {
            throw invalid("payload/ciphertext-header");
        }
        ciphertext = ciphertext.clone();
        String digest = sha256(ciphertext);
        if (!digest.equals(source.getString("content_sha256"))) {
            throw invalid("payload/content-hash");
        }
        int crypto = source.getInt("crypto_version");
        int format = source.getInt("binary_format_version");
        if (crypto < 1 || crypto > 3 || crypto == 3 && (format < 1 || format > 32)
                || crypto < 3 && format != 0 || source.getInt("engine_schema_version") != YsmSourceIdentity.ENGINE_SCHEMA_VERSION) {
            throw invalid("payload/version");
        }
        CompoundTag portable = new CompoundTag();
        portable.putByteArray("source_ciphertext", ciphertext);
        portable.putString("content_sha256", digest);
        portable.putString("container_kind", YsmSourceIdentity.ContainerKind.YSGP.name());
        portable.putInt("engine_schema_version", YsmSourceIdentity.ENGINE_SCHEMA_VERSION);
        portable.putInt("crypto_version", crypto);
        portable.putInt("binary_format_version", format);
        portable.putString("kind", bounded(source.getString("kind")));
        portable.putString("texture_id", bounded(source.getString("texture_id")));
        String name = bounded(NbtDefaults.getStringOr(source, "display_name", "ysm"));
        if (name.contains("/") || name.contains("\\") || name.contains(":")) {
            throw invalid("payload/display-name");
        }
        portable.putString("display_name", name);
        CompoundTag metadata = new CompoundTag();
        CompoundTag original = avatar.getCompound("metadata");
        for (String key : DISPLAY_FIELDS) {
            if (original.contains(key)) {
                metadata.putString(key, bounded(original.getString(key)));
            }
        }
        CompoundTag output = new CompoundTag();
        output.put("metadata", metadata);
        output.put("ysm", portable);
        if (original.getString("resource_kind").equals("ACCESSORY")) {
            String cosmeticJson = original.getString("cosmetic_json");
            try {
                top.aihmc.omnimorph.model.outfit.CosmeticMetadata.read(cosmeticJson);
            } catch (RuntimeException malformed) {
                throw invalid("payload/cosmetic-metadata");
            }
            metadata.putString("resource_kind", "ACCESSORY");
            metadata.putString("cosmetic_json", cosmeticJson);
            // These are authored wrapper files supplied by LocalCosmeticSource, not decoded YSM geometry or textures.
            for (String key : List.of("scripts", "sounds", "resources")) {
                if (avatar.contains(key)) {
                    output.put(key, avatar.get(key).copy());
                }
            }
        }
        return output;
    }

    private static String bounded(String value) throws IOException {
        if (value.length() > 1024) {
            throw invalid("payload/display-length");
        }
        return value;
    }

    private static String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is required by the JVM", e);
        }
    }

    private static YsmDecodeException invalid(String stage) {
        return new YsmDecodeException(YsmDecodeException.Code.INVALID_DATA, stage, 0);
    }
}
