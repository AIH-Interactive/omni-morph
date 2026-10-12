package org.figuramc.figura.avatar.ysm;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.Locale;

public record YsmSourceIdentity(ContainerKind containerKind, int cryptoVersion, int binaryFormatVersion,
                                 int engineSchemaVersion, String contentSha256) {
    public static final int ENGINE_SCHEMA_VERSION = 1;

    public enum ContainerKind {
        DIRECTORY, ZIP, MEMORY, YSGP
    }

    public static YsmSourceIdentity ofResources(YsmResourceSource source, ContainerKind kind) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            for (String path : source.resourcePaths().stream()
                    .sorted(java.util.Comparator.comparing(value -> value.toLowerCase(Locale.ROOT))).toList()) {
                YsmResourceLimits.checkCancelled(() -> false);
                byte[] name = path.toLowerCase(Locale.ROOT).getBytes(StandardCharsets.UTF_8);
                byte[] bytes = source.readBytes(path);
                try {
                    digest.update(ByteBuffer.allocate(4).putInt(name.length).array());
                    digest.update(name);
                    digest.update(ByteBuffer.allocate(4).putInt(bytes.length).array());
                    digest.update(bytes);
                } finally {
                    Arrays.fill(bytes, (byte) 0);
                }
            }
            return new YsmSourceIdentity(kind, 0, 0, ENGINE_SCHEMA_VERSION,
                    HexFormat.of().formatHex(digest.digest()));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is required by the JVM", e);
        }
    }

    public String modelKey() {
        return "ysm:" + engineSchemaVersion + ":" + contentSha256;
    }
}
