package top.aihmc.omnimorph.avatar.ysm.crypto;

import top.aihmc.omnimorph.avatar.ysm.YsmMemoryResourceSource;
import top.aihmc.omnimorph.avatar.ysm.YsmPackage;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteOrder;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.HashSet;
import java.util.zip.DataFormatException;
import java.util.zip.Inflater;

/** Client read-only crypto 1/2 resource decoder; contains no writer or export path. */
public final class YsmLegacyDecoder {
    private YsmLegacyDecoder() {
    }

    public static YsmMemoryResourceSource decode(byte[] source, String displayName, YsmDecodeBudget budget) throws IOException {
        budget.length(source.length, budget.limits().fileBytes(), "legacy/source", 0);
        YsmByteReader reader = new YsmByteReader(source, budget, "legacy");
        if (reader.int32(ByteOrder.BIG_ENDIAN) != 0x59534750) {
            throw new YsmDecodeException(YsmDecodeException.Code.BAD_HEADER, "legacy", 0);
        }
        int crypto = reader.int32(ByteOrder.BIG_ENDIAN);
        if (crypto != 1 && crypto != 2) {
            throw new YsmDecodeException(YsmDecodeException.Code.UNSUPPORTED_VERSION, "legacy/crypto", 4);
        }
        byte[] expected = reader.bytes(16);
        byte[] actual = digest(source, 24, source.length - 24);
        if (!MessageDigest.isEqual(expected, actual)) {
            throw new YsmDecodeException(YsmDecodeException.Code.CHECKSUM_FAILED, "legacy/container", 8);
        }
        Map<String, byte[]> files = new LinkedHashMap<>();
        var names = new HashSet<String>();
        try {
            while (reader.remaining() > 0) {
                budget.check("legacy/resource", reader.offset());
                budget.beginResource("legacy/resource-count", reader.offset());
                int nameLength = budget.length(reader.int32(ByteOrder.BIG_ENDIAN), YsmDecodeBudget.MAX_STRING_BYTES,
                        "legacy/name", reader.offset());
                byte[] nameBytes = reader.bytes(nameLength);
                if (crypto == 2) {
                    try {
                        nameBytes = Base64.getDecoder().decode(nameBytes);
                    } catch (IllegalArgumentException e) {
                        throw new YsmDecodeException(YsmDecodeException.Code.INVALID_DATA, "legacy/name", reader.offset());
                    }
                }
                String name = YsmPackage.normalize(reader.utf8(nameBytes));
                if (name.isBlank()) {
                    throw new YsmDecodeException(YsmDecodeException.Code.INVALID_PATH, "legacy/name", reader.offset());
                }
                if (!names.add(name.toLowerCase(Locale.ROOT))) {
                    throw new YsmDecodeException(YsmDecodeException.Code.PATH_CONFLICT, "legacy/name", reader.offset());
                }
                int size = budget.length(reader.int32(ByteOrder.BIG_ENDIAN), budget.limits().fileBytes(),
                        "legacy/ciphertext", reader.offset());
                byte[] key = null;
                byte[] wrapped = null;
                byte[] iv = null;
                byte[] ciphertext = null;
                byte[] compressed = null;
                try {
                    if (crypto == 1) {
                        key = reader.bytes(16);
                        iv = reader.bytes(16);
                        ciphertext = reader.bytes(size);
                    } else {
                        int wrappedSize = budget.length(reader.int32(ByteOrder.BIG_ENDIAN), 64,
                                "legacy/wrapped-key", reader.offset());
                        wrapped = reader.bytes(wrappedSize);
                        iv = reader.bytes(16);
                        ciphertext = reader.bytes(size);
                        byte[] hash = digest(ciphertext, 0, ciphertext.length);
                        long seed = 0;
                        for (byte value : hash) {
                            seed = (seed << 8) + (value & 255);
                        }
                        byte[] wrappingKey = new byte[16];
                        new Random(seed).nextBytes(wrappingKey);
                        try {
                            key = decrypt(wrapped, wrappingKey, iv, reader.offset());
                        } finally {
                            erase(hash, wrappingKey);
                        }
                    }
                    compressed = decrypt(ciphertext, key, iv, reader.offset());
                    byte[] resource = inflate(compressed, budget, reader.offset());
                    files.put(name, resource);
                    budget.resource(resource.length, "legacy/resource", reader.offset());
                } finally {
                    erase(key, wrapped, iv, ciphertext, compressed);
                }
            }
            if (files.isEmpty()) {
                throw new YsmDecodeException(YsmDecodeException.Code.INVALID_DATA, "legacy/empty", reader.offset());
            }
            return new YsmMemoryResourceSource(displayName, files, budget.limits());
        } finally {
            files.values().forEach(bytes -> Arrays.fill(bytes, (byte) 0));
        }
    }

    private static byte[] decrypt(byte[] ciphertext, byte[] key, byte[] iv, int offset) throws IOException {
        if (key == null || (key.length != 16 && key.length != 24 && key.length != 32)
                || ciphertext.length == 0 || ciphertext.length % 16 != 0) {
            throw new YsmDecodeException(YsmDecodeException.Code.DECRYPT_FAILED, "legacy/aes", offset);
        }
        try {
            Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
            cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(key, "AES"), new IvParameterSpec(iv));
            return cipher.doFinal(ciphertext);
        } catch (GeneralSecurityException e) {
            throw new YsmDecodeException(YsmDecodeException.Code.DECRYPT_FAILED, "legacy/aes", offset);
        }
    }

    private static byte[] inflate(byte[] compressed, YsmDecodeBudget budget, int offset) throws IOException {
        if (compressed.length == 0) {
            return new byte[0];
        }
        Inflater inflater = new Inflater();
        inflater.setInput(compressed);
        byte[] buffer = new byte[8192];
        try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            while (!inflater.finished()) {
                budget.check("legacy/deflate", offset);
                int count = inflater.inflate(buffer);
                budget.length((long) output.size() + count, budget.remainingResourceBytes(), "legacy/deflate", offset);
                output.write(buffer, 0, count);
                if (count == 0 && !inflater.finished()) {
                    throw new YsmDecodeException(YsmDecodeException.Code.DECOMPRESS_FAILED, "legacy/deflate", offset);
                }
            }
            if (inflater.getRemaining() != 0) {
                throw new YsmDecodeException(YsmDecodeException.Code.DECOMPRESS_FAILED, "legacy/deflate-trailing", offset);
            }
            return output.toByteArray();
        } catch (DataFormatException e) {
            throw new YsmDecodeException(YsmDecodeException.Code.DECOMPRESS_FAILED, "legacy/deflate", offset);
        } finally {
            inflater.end();
            Arrays.fill(buffer, (byte) 0);
        }
    }

    private static byte[] digest(byte[] data, int offset, int length) {
        try {
            MessageDigest digest = MessageDigest.getInstance("MD5");
            digest.update(data, offset, length);
            return digest.digest();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("MD5 is required for legacy YSM integrity", e);
        }
    }

    private static void erase(byte[]... buffers) {
        for (byte[] buffer : buffers) {
            if (buffer != null) {
                Arrays.fill(buffer, (byte) 0);
            }
        }
    }
}
