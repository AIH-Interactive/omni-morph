package top.aihmc.omnimorph.render.acceleration;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayDeque;
import java.util.List;
import java.util.function.Consumer;

/**
 * Collects one frame of pose storage blocks in CPU staging and writes them into a single fixed-capacity arena
 * buffer. Material boundaries interleave preparation and drawing, so each flush writes only the bytes staged
 * since the previous flush; regions already bound by draws are never rewritten within the frame. Arena buffers
 * return for reuse only after their retirement fence completed. Render thread only.
 */
final class GpuPoseArena {
    record Slice(int offset, int bytes) {
    }

    static final int MIN_CAPACITY = 64 * 1024;
    private static final int MAX_FREE_SLOTS = 3;

    private final GpuDeviceAdapter device;
    private final GpuRetirement retirement;
    private final int alignment;
    private final long maxBytes;
    private final ArrayDeque<GpuDeviceAdapter.Storage> free = new ArrayDeque<>();
    private ByteBuffer staging = ByteBuffer.allocateDirect(MIN_CAPACITY).order(ByteOrder.nativeOrder());
    private GpuDeviceAdapter.Storage current;
    private int flushedEnd;
    private int previousFrameBytes;
    private boolean closed;
    private long residentBytes;
    private long uploads;
    private long uploadedBytes;

    GpuPoseArena(GpuDeviceAdapter device, GpuRetirement retirement, int alignment, long maxBytes) {
        if (alignment <= 0 || Integer.bitCount(alignment) != 1) {
            throw new IllegalArgumentException("Storage offset alignment must be a power of two");
        }
        this.device = device;
        this.retirement = retirement;
        this.alignment = alignment;
        this.maxBytes = maxBytes;
    }

    /**
     * Reserves an aligned block and lets {@code writer} fill exactly {@code bytes} at the staging position.
     * Returns null, without running the writer, when the block would exceed the arena limit or the capacity of
     * this frame's arena buffer; the caller then keeps using uniform pose blocks for this batch.
     */
    Slice append(int bytes, Consumer<ByteBuffer> writer) {
        if (closed || bytes <= 0) {
            return null;
        }
        int offset = align(staging.position());
        long end = (long) offset + bytes;
        long limit = current == null ? maxBytes : current.bytes();
        if (end > limit) {
            return null;
        }
        ensureStaging((int) end);
        staging.position(offset);
        writer.accept(staging);
        if (staging.position() != end) {
            staging.position(offset);
            throw new IllegalStateException("Storage writer wrote a different size than " + bytes + " bytes");
        }
        return new Slice(offset, bytes);
    }

    /**
     * Makes every staged slice readable by the GPU: creates this frame's arena on first use, sized for the
     * larger of this and the previous frame, then writes only the bytes staged since the last flush.
     */
    GpuDeviceAdapter.Storage flush() {
        if (closed) {
            throw new IllegalStateException("Pose arena closed");
        }
        int staged = staging.position();
        if (staged == 0) {
            return current;
        }
        if (current == null) {
            current = acquire(Math.max(staged, previousFrameBytes));
        }
        if (flushedEnd < staged) {
            ByteBuffer pending = staging.duplicate().position(flushedEnd).limit(staged);
            device.writeStorage(current, flushedEnd, pending);
            uploads++;
            uploadedBytes += staged - flushedEnd;
            AccelerationFrameDiagnostics.arenaUpload(staged - flushedEnd);
            flushedEnd = staged;
        }
        return current;
    }

    /** Ends the frame: the frame's arena is retired behind a fence and staging restarts empty. */
    void beginFrame() {
        previousFrameBytes = staging.position();
        if (current != null) {
            GpuDeviceAdapter.Storage finished = current;
            current = null;
            residentBytes -= finished.bytes();
            retirement.retire(device, List.of(finished), this::recycle);
        }
        flushedEnd = 0;
        staging.clear();
    }

    void close() {
        if (closed) {
            return;
        }
        beginFrame();
        closed = true;
        while (!free.isEmpty()) {
            retire(free.pollFirst());
        }
    }

    long residentBytes() {
        return residentBytes;
    }

    long uploads() {
        return uploads;
    }

    long uploadedBytes() {
        return uploadedBytes;
    }

    private GpuDeviceAdapter.Storage acquire(int needed) {
        while (!free.isEmpty()) {
            GpuDeviceAdapter.Storage candidate = free.pollFirst();
            if (candidate.bytes() >= needed) {
                return candidate;
            }
            // Too small for this frame; a larger buffer replaces it.
            retire(candidate);
        }
        long rounded = needed <= 1 ? 1 : Long.highestOneBit(needed - 1L) << 1;
        long capacity = Math.max(needed, Math.min(maxBytes, Math.max(MIN_CAPACITY, rounded)));
        GpuDeviceAdapter.Storage created = device.createStorage(capacity);
        residentBytes += created.bytes();
        return created;
    }

    /** Runs from retirement after the fence completed; declines once closed or when enough slots are idle. */
    private boolean recycle(GpuDeviceAdapter.Resource resource) {
        if (closed || free.size() >= MAX_FREE_SLOTS || !(resource instanceof GpuDeviceAdapter.Storage storage)) {
            return false;
        }
        free.addLast(storage);
        residentBytes += storage.bytes();
        return true;
    }

    private void retire(GpuDeviceAdapter.Storage storage) {
        residentBytes -= storage.bytes();
        retirement.retire(device, List.of(storage));
    }

    private int align(int position) {
        return (position + alignment - 1) & -alignment;
    }

    private void ensureStaging(int end) {
        if (end <= staging.capacity()) {
            return;
        }
        int capacity = staging.capacity();
        while (capacity < end) {
            capacity = Math.multiplyExact(capacity, 2);
        }
        ByteBuffer grown = ByteBuffer.allocateDirect(capacity).order(ByteOrder.nativeOrder());
        grown.put(staging.duplicate().flip());
        staging = grown;
    }
}
