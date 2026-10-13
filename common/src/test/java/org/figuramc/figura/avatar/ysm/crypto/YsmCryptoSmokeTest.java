package org.figuramc.figura.avatar.ysm.crypto;

import org.figuramc.figura.avatar.ysm.YsmResourceLimits;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.Arrays;

public final class YsmCryptoSmokeTest {
    private YsmCryptoSmokeTest() {
    }

    public static void main(String[] args) throws Exception {
        byte[] expected = fixture("v3-expected.bin");
        String cityVectors = new String(fixture("cityhash64-vectors.txt"), StandardCharsets.UTF_8);
        for (String line : cityVectors.lines().toList()) {
            String[] fields = line.split("=");
            long actual = new YsmCityHash().hash64WithSeed(expected, 0, Integer.parseInt(fields[0]), 0x9E5599DB80C67C29L);
            require(Long.toUnsignedString(actual, 16).equals(fields[1]), "independent CityHash vector " + fields[0]);
        }
        try (YsmMt19937 random = new YsmMt19937(5489)) {
            require(random.next() == Long.parseUnsignedLong("14514284786278117030"), "independent MT19937-64 vector");
        }
        for (int version = 1; version <= 2; version++) {
            byte[] source = fixture("crypto" + version + ".ysm");
            try (var resources = YsmLegacyDecoder.decode(source, "fixture", new YsmDecodeBudget())) {
                require(resources.resourcePaths().size() == 3, "fixed legacy resource count");
                require(resources.readString("main.json").equals(
                        "{\"minecraft:geometry\":[{\"description\":{},\"bones\":[{\"name\":\"fixture\"}]}]}"),
                        "fixed legacy geometry bytes " + version);
                require(resources.readString("functions/event.molang").equals("return 0.2;"),
                        "fixed legacy function bytes " + version);
                require(Arrays.equals(resources.readBytes("body.png"), java.util.Base64.getDecoder().decode(
                        "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAFgwJ/lR4sfwAAAABJRU5ErkJggg==")),
                        "fixed legacy texture bytes " + version);
            }
        }
        for (String name : new String[]{"crypto3.ysm", "crypto3-compressed.ysm"}) {
            require(Arrays.equals(expected, YsmV3Decoder.decode(fixture(name), new YsmDecodeBudget())),
                    "independent V3 segmented fixed vector " + name);
        }
        for (String name : new String[]{"v3-raw-frame.bin", "v3-compressed-frame.bin"}) {
            byte[] frame = fixture(name);
            require(Arrays.equals(expected, YsmZstdDecoder.decompress(frame, 0, frame.length, new YsmDecodeBudget())),
                    "independent full Zstd vector " + name);
        }
        byte[] legacy = fixture("crypto1.ysm");
        byte[] corrupted = legacy.clone();
        corrupted[8] ^= 1;
        failure(() -> YsmLegacyDecoder.decode(corrupted, "fixture", new YsmDecodeBudget()),
                YsmDecodeException.Code.CHECKSUM_FAILED);
        byte[] unknown = legacy.clone();
        ByteBuffer.wrap(unknown).putInt(4, 19);
        failure(() -> YsmLegacyDecoder.decode(unknown, "fixture", new YsmDecodeBudget()),
                YsmDecodeException.Code.UNSUPPORTED_VERSION);
        byte[] shortRecord = Arrays.copyOf(legacy, legacy.length - 1);
        legacyChecksum(shortRecord);
        failure(() -> YsmLegacyDecoder.decode(shortRecord, "fixture", new YsmDecodeBudget()),
                YsmDecodeException.Code.TRUNCATED);
        byte[] badLength = legacy.clone();
        ByteBuffer.wrap(badLength).putInt(24, Integer.MAX_VALUE);
        legacyChecksum(badLength);
        failure(() -> YsmLegacyDecoder.decode(badLength, "fixture", new YsmDecodeBudget()),
                YsmDecodeException.Code.RESOURCE_LIMIT);
        byte[] badPath = legacy.clone();
        System.arraycopy("../xx.txt".getBytes(StandardCharsets.UTF_8), 0, badPath, 28, 9);
        legacyChecksum(badPath);
        failure(() -> YsmLegacyDecoder.decode(badPath, "fixture", new YsmDecodeBudget()),
                YsmDecodeException.Code.INVALID_PATH);
        byte[] badPadding = legacy.clone();
        badPadding[badPadding.length - 1] ^= 7;
        legacyChecksum(badPadding);
        failure(() -> YsmLegacyDecoder.decode(badPadding, "fixture", new YsmDecodeBudget()),
                YsmDecodeException.Code.DECRYPT_FAILED);
        byte[] v3 = fixture("crypto3.ysm");
        byte[] badHash = v3.clone();
        badHash[badHash.length - 1] ^= 1;
        failure(() -> YsmV3Decoder.decode(badHash, new YsmDecodeBudget()), YsmDecodeException.Code.CHECKSUM_FAILED);
        failure(() -> YsmV3Decoder.decode(fixture("crypto3-bad-padding.ysm"), new YsmDecodeBudget()),
                YsmDecodeException.Code.TRUNCATED);
        failure(() -> YsmV3Decoder.decode(fixture("crypto3-bad-zstd.ysm"), new YsmDecodeBudget()),
                YsmDecodeException.Code.BAD_HEADER);
        YsmResourceLimits tiny = new YsmResourceLimits(65536, 5, 5, 2);
        failure(() -> YsmLegacyDecoder.decode(legacy, "fixture", new YsmDecodeBudget(tiny, () -> false, 10000)),
                YsmDecodeException.Code.RESOURCE_LIMIT);
        failure(() -> YsmV3Decoder.decode(v3, new YsmDecodeBudget(tiny, () -> false, 10000)),
                YsmDecodeException.Code.RESOURCE_LIMIT);
        byte[] frame = fixture("v3-raw-frame.bin");
        byte[] reservedBlock = frame.clone();
        reservedBlock[9] = (byte) ((reservedBlock[9] & 0x9F) | 0x40);
        failure(() -> YsmZstdDecoder.decompress(reservedBlock, 0, reservedBlock.length, new YsmDecodeBudget()),
                YsmDecodeException.Code.INVALID_DATA);
        byte[] truncatedFrame = Arrays.copyOf(frame, 11);
        failure(() -> YsmZstdDecoder.decompress(truncatedFrame, 0, truncatedFrame.length, new YsmDecodeBudget()),
                YsmDecodeException.Code.TRUNCATED);
        failure(() -> new YsmDecodeBudget().pixels(Integer.MAX_VALUE, Integer.MAX_VALUE, "fixture/pixels", 0),
                YsmDecodeException.Code.RESOURCE_LIMIT);
        new YsmDecodeBudget().pixels(4096, 4096, "fixture/pixels", 0);
        if (args.length > 0) {
            testLocalFiles(Path.of(args[0]));
        }
        System.out.println("YSM crypto 1/2/3 fixed vectors and bounded negative cases passed");
    }

    private static void testLocalFiles(Path root) throws Exception {
        try (var entries = Files.list(root)) {
            for (Path path : entries.filter(file -> file.getFileName().toString().endsWith(".ysm")).toList()) {
                byte[] source = Files.readAllBytes(path);
                boolean v3 = source.length > 3 && source[0] == (byte) 239;
                if (v3) {
                    byte[] binary = YsmV3Decoder.decode(source, new YsmDecodeBudget());
                    require(binary.length >= 4, "local binary format DWORD present");
                    System.out.println("LOCAL_V3_DECODE_OK " + path.getFileName() + " format="
                            + ByteBuffer.wrap(binary).order(ByteOrder.LITTLE_ENDIAN).getInt() + " bytes=" + binary.length);
                    Arrays.fill(binary, (byte) 0);
                } else {
                    try (var resources = YsmLegacyDecoder.decode(source, "local", new YsmDecodeBudget())) {
                        require(resources.resourcePaths().size() > 0, "local legacy resources present");
                        System.out.println("LOCAL_LEGACY_DECODE_OK " + path.getFileName()
                                + " resources=" + resources.resourcePaths().size());
                    }
                }
            }
        }
    }

    private static byte[] fixture(String name) throws IOException {
        try (var input = YsmCryptoSmokeTest.class.getResourceAsStream("/ysm/crypto-vectors/" + name)) {
            if (input == null) {
                throw new IOException("Missing crypto fixture " + name);
            }
            return input.readAllBytes();
        }
    }

    private static void legacyChecksum(byte[] source) throws Exception {
        var md5 = MessageDigest.getInstance("MD5");
        md5.update(source, 24, source.length - 24);
        System.arraycopy(md5.digest(), 0, source, 8, 16);
    }

    private static void failure(CheckedAction action, YsmDecodeException.Code expected) throws Exception {
        try {
            action.run();
        } catch (YsmDecodeException error) {
            require(error.code() == expected, "expected " + expected + " but got " + error.code());
            return;
        }
        throw new AssertionError("Expected " + expected);
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
