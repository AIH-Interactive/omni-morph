package org.figuramc.figura.avatar.ysm.crypto;

import org.figuramc.figura.avatar.ysm.YsmResourceLimits;

import java.io.IOException;
import java.util.function.BooleanSupplier;

/** Shared by all layers so resource totals and the deadline cannot reset per nested resource. */
public final class YsmDecodeBudget {
    public static final int MAX_HEADER_BYTES = 65536;
    public static final int MAX_STRING_BYTES = 1048576;
    public static final int MAX_TEXTURE_PIXELS = 16777216;
    public static final int MAX_TOTAL_TEXTURE_PIXELS = 67108864;

    private final YsmResourceLimits limits;
    private final BooleanSupplier cancelled;
    private final long started = System.nanoTime();
    private final long timeoutNanos;
    private long resourceBytes;
    private long texturePixels;
    private int resourceCount;

    public YsmDecodeBudget() {
        this(YsmResourceLimits.DEFAULT, () -> false, 10000);
    }

    public YsmDecodeBudget(YsmResourceLimits limits, BooleanSupplier cancelled, long timeoutMillis) {
        this.limits = limits;
        this.cancelled = cancelled;
        if (timeoutMillis <= 0 || timeoutMillis > 60000) {
            throw new IllegalArgumentException("Invalid YSM decode timeout");
        }
        timeoutNanos = timeoutMillis * 1000000L;
    }

    public void check(String stage, long offset) throws IOException {
        YsmResourceLimits.checkCancelled(cancelled);
        if (System.nanoTime() - started > timeoutNanos) {
            throw new YsmDecodeException(YsmDecodeException.Code.RESOURCE_LIMIT, stage + "/deadline", offset);
        }
    }

    public int length(long length, long max, String stage, long offset) throws IOException {
        check(stage, offset);
        if (length < 0 || length > max || length > Integer.MAX_VALUE) {
            throw new YsmDecodeException(YsmDecodeException.Code.RESOURCE_LIMIT, stage, offset);
        }
        return (int) length;
    }

    public void resource(long bytes, String stage, long offset) throws IOException {
        length(bytes, limits.singleResourceBytes(), stage, offset);
        if (resourceBytes > limits.totalResourceBytes() - bytes) {
            throw new YsmDecodeException(YsmDecodeException.Code.RESOURCE_LIMIT, stage, offset);
        }
        resourceBytes += bytes;
    }

    public void beginResource(String stage, long offset) throws IOException {
        check(stage, offset);
        if (++resourceCount > limits.resourceCount()) {
            throw new YsmDecodeException(YsmDecodeException.Code.RESOURCE_LIMIT, stage, offset);
        }
    }

    public long remainingResourceBytes() {
        return Math.min(limits.singleResourceBytes(), limits.totalResourceBytes() - resourceBytes);
    }

    public long pixels(int width, int height, String stage, long offset) throws IOException {
        long pixels = (long) width * height;
        if (width <= 0 || height <= 0 || pixels > MAX_TEXTURE_PIXELS
                || texturePixels > MAX_TOTAL_TEXTURE_PIXELS - pixels) {
            throw new YsmDecodeException(YsmDecodeException.Code.RESOURCE_LIMIT, stage, offset);
        }
        check(stage, offset);
        texturePixels += pixels;
        return pixels;
    }

    public YsmResourceLimits limits() {
        return limits;
    }
}
