package org.figuramc.figura.avatar.ysm;

import java.io.IOException;
import java.io.InterruptedIOException;
import java.util.function.BooleanSupplier;

/** Limits frozen in M0; callers may supply smaller limits for boundary tests. */
public record YsmResourceLimits(long fileBytes, long singleResourceBytes, long totalResourceBytes, int resourceCount) {
    public static final YsmResourceLimits DEFAULT = new YsmResourceLimits(
            64L * 1024 * 1024, 32L * 1024 * 1024, 256L * 1024 * 1024, 4096);

    public YsmResourceLimits {
        if (fileBytes < 0 || singleResourceBytes < 0 || singleResourceBytes > Integer.MAX_VALUE
                || totalResourceBytes < 0 || resourceCount < 0) {
            throw new IllegalArgumentException("Invalid YSM resource limits");
        }
    }

    public void checkResource(long bytes, long total, int count, String path) throws IOException {
        if (bytes < 0 || bytes > singleResourceBytes || total > totalResourceBytes || count > resourceCount) {
            throw new IOException("RESOURCE_LIMIT: resource budget exceeded at " + path);
        }
    }

    public static void checkCancelled(BooleanSupplier cancelled) throws InterruptedIOException {
        if (Thread.currentThread().isInterrupted() || cancelled.getAsBoolean()) {
            throw new InterruptedIOException("CANCELLED: YSM source read cancelled");
        }
    }
}
