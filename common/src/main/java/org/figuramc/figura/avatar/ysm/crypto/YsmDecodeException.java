package org.figuramc.figura.avatar.ysm.crypto;

import java.io.IOException;

/** Diagnostics contain locations and codes, never decrypted text or key material. */
public final class YsmDecodeException extends IOException {
    public enum Code {
        BAD_HEADER, TRUNCATED, CHECKSUM_FAILED, UNSUPPORTED_VERSION,
        DECRYPT_FAILED, DECOMPRESS_FAILED, RESOURCE_LIMIT, INVALID_PATH, PATH_CONFLICT, INVALID_DATA
    }

    private final Code code;
    private final String stage;
    private final long offset;

    public YsmDecodeException(Code code, String stage, long offset) {
        super(code + ": stage=" + stage + " offset=" + offset);
        this.code = code;
        this.stage = stage;
        this.offset = offset;
    }

    public Code code() {
        return code;
    }

    public String stage() {
        return stage;
    }

    public long offset() {
        return offset;
    }
}
