package top.aihmc.omnimorph.avatar.ysm;

import top.aihmc.omnimorph.avatar.ysm.binary.YsmBinaryModel;
import top.aihmc.omnimorph.avatar.ysm.binary.YsmBinaryReader;
import top.aihmc.omnimorph.avatar.ysm.crypto.YsmDecodeBudget;
import top.aihmc.omnimorph.avatar.ysm.crypto.YsmDecodeException;
import top.aihmc.omnimorph.avatar.ysm.crypto.YsmLegacyDecoder;
import top.aihmc.omnimorph.avatar.ysm.crypto.YsmV3Decoder;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.function.BooleanSupplier;

/** Owns original ciphertext separately from decoded resources; never writes plaintext to disk. */
public final class YsmEncryptedSource implements AutoCloseable {
    private final byte[] ciphertext;
    private final String displayName;
    private final YsmSourceIdentity identity;
    private final YsmMemoryResourceSource legacyResources;
    private final YsmBinaryModel binary;
    private final YsmDecodeBudget budget;
    private boolean closed;

    private YsmEncryptedSource(byte[] ciphertext, String displayName, int cryptoVersion,
                                YsmMemoryResourceSource legacyResources, YsmBinaryModel binary,
                                YsmDecodeBudget budget) {
        this.ciphertext = ciphertext;
        this.displayName = displayName;
        this.legacyResources = legacyResources;
        this.binary = binary;
        this.budget = budget;
        identity = new YsmSourceIdentity(YsmSourceIdentity.ContainerKind.YSGP, cryptoVersion,
                binary == null ? 0 : binary.formatVersion, YsmSourceIdentity.ENGINE_SCHEMA_VERSION, sha256(ciphertext));
    }

    public static boolean hasMagic(Path path) {
        if (path == null || !Files.isRegularFile(path)) {
            return false;
        }
        try (var stream = Files.newInputStream(path)) {
            byte[] header = stream.readNBytes(7);
            int offset = magicOffset(header);
            return offset >= 0;
        } catch (IOException e) {
            return false;
        }
    }

    public static boolean hasMagic(byte[] bytes) {
        return magicOffset(bytes) >= 0;
    }

    public static YsmEncryptedSource read(Path path, BooleanSupplier cancelled) throws IOException {
        YsmDecodeBudget budget = new YsmDecodeBudget(YsmResourceLimits.DEFAULT, cancelled, 10000);
        budget.length(Files.size(path), budget.limits().fileBytes(), "source/file", 0);
        byte[] bytes;
        try (var stream = Files.newInputStream(path)) {
            bytes = stream.readNBytes((int) budget.limits().fileBytes() + 1);
        }
        try {
            return read(bytes, path.getFileName().toString(), budget);
        } finally {
            Arrays.fill(bytes, (byte) 0);
        }
    }

    public static YsmEncryptedSource read(byte[] bytes, String displayName,
                                          BooleanSupplier cancelled) throws IOException {
        YsmDecodeBudget budget = new YsmDecodeBudget(YsmResourceLimits.DEFAULT, cancelled, 10000);
        return read(bytes, displayName, budget);
    }

    private static YsmEncryptedSource read(byte[] bytes, String displayName,
                                           YsmDecodeBudget budget) throws IOException {
        budget.length(bytes.length, budget.limits().fileBytes(), "source/file", 0);
        byte[] ciphertext = bytes.clone();
        YsmMemoryResourceSource legacy = null;
        YsmBinaryModel binary = null;
        try {
            int version = cryptoVersion(ciphertext, budget);
            if (version == 1 || version == 2) {
                legacy = YsmLegacyDecoder.decode(ciphertext, displayName, budget);
            } else if (version == 3) {
                byte[] payload = YsmV3Decoder.decode(ciphertext, budget);
                try {
                    binary = YsmBinaryReader.readFilePayload(payload, budget);
                } finally {
                    Arrays.fill(payload, (byte) 0);
                }
            } else {
                throw new YsmDecodeException(YsmDecodeException.Code.UNSUPPORTED_VERSION, "source/crypto", 4);
            }
            budget.check("source/decoded", 0);
            return new YsmEncryptedSource(ciphertext, displayName, version, legacy, binary, budget);
        } catch (IOException | RuntimeException e) {
            Arrays.fill(ciphertext, (byte) 0);
            if (legacy != null) {
                legacy.close();
            }
            if (binary != null) {
                binary.close();
            }
            throw e;
        }
    }

    private static int cryptoVersion(byte[] source, YsmDecodeBudget budget) throws IOException {
        int magic = magicOffset(source);
        if (magic < 0) {
            throw new YsmDecodeException(YsmDecodeException.Code.BAD_HEADER, "source/magic", 0);
        }
        if (source.length - magic < 8) {
            throw new YsmDecodeException(YsmDecodeException.Code.TRUNCATED, "source/crypto", magic + 4);
        }
        int legacyVersion = ByteBuffer.wrap(source, magic + 4, 4).order(ByteOrder.BIG_ENDIAN).getInt();
        if (magic == 0 && (legacyVersion == 1 || legacyVersion == 2)) {
            return legacyVersion;
        }
        int end = magic + 4;
        while (end < source.length && source[end] != 0) {
            budget.length(end + 1, YsmDecodeBudget.MAX_HEADER_BYTES, "source/text-header", end);
            end++;
        }
        if (end >= source.length || source.length - end < 5) {
            throw new YsmDecodeException(YsmDecodeException.Code.TRUNCATED, "source/crypto", end);
        }
        return ByteBuffer.wrap(source, end + 1, 4).order(ByteOrder.LITTLE_ENDIAN).getInt();
    }

    private static int magicOffset(byte[] bytes) {
        int offset = bytes.length >= 3 && bytes[0] == (byte) 0xef
                && bytes[1] == (byte) 0xbb && bytes[2] == (byte) 0xbf ? 3 : 0;
        if (bytes.length - offset >= 4 && bytes[offset] == 'Y' && bytes[offset + 1] == 'S'
                && bytes[offset + 2] == 'G' && bytes[offset + 3] == 'P') {
            return offset;
        }
        return -1;
    }

    public YsmSourceIdentity identity() {
        return identity;
    }

    public String displayName() {
        return displayName;
    }

    public byte[] ciphertextCopy() {
        requireOpen();
        return ciphertext.clone();
    }

    public YsmMemoryResourceSource legacyResources() {
        requireOpen();
        return legacyResources;
    }

    /** Owned parser input for the mapper; must not be retained by a published runtime. */
    public YsmBinaryModel binary() {
        requireOpen();
        return binary;
    }

    public YsmDecodeBudget budget() {
        requireOpen();
        return budget;
    }

    private void requireOpen() {
        if (closed) {
            throw new IllegalStateException("CLOSED_SOURCE: YSM encrypted source is closed");
        }
    }

    @Override
    public void close() {
        if (closed) {
            return;
        }
        Arrays.fill(ciphertext, (byte) 0);
        if (legacyResources != null) {
            legacyResources.close();
        }
        if (binary != null) {
            binary.close();
        }
        closed = true;
    }

    private static String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is required by the JVM", e);
        }
    }
}
