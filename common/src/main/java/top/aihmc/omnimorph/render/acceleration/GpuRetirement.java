package top.aihmc.omnimorph.render.acceleration;

import com.mojang.blaze3d.buffers.GpuFence;

import java.util.ArrayList;
import java.util.List;

/** Render-thread retirement outlives a disabled/replaced backend and never blocks the normal frame. */
final class GpuRetirement {
    static final GpuRetirement GLOBAL = new GpuRetirement();

    /** Takes back a resource whose fence completed; returning false lets retirement close it. */
    @FunctionalInterface
    interface Recycler {
        boolean recycle(GpuDeviceAdapter.Resource resource);
    }

    private static final class Pending {
        final GpuDeviceAdapter device;
        final List<GpuDeviceAdapter.Resource> resources;
        final long bytes;
        final Recycler recycler;
        GpuFence fence;

        Pending(GpuDeviceAdapter device, List<GpuDeviceAdapter.Resource> resources, long bytes, Recycler recycler) {
            this.device = device;
            this.resources = resources;
            this.bytes = bytes;
            this.recycler = recycler;
        }
    }

    private final List<Pending> pending = new ArrayList<>();
    private long bytes;

    void retire(GpuDeviceAdapter device, List<? extends GpuDeviceAdapter.Resource> resources) {
        retire(device, resources, null);
    }

    /** The recycler only receives resources after their fence completed, so the GPU no longer reads them. */
    void retire(GpuDeviceAdapter device, List<? extends GpuDeviceAdapter.Resource> resources, Recycler recycler) {
        if (resources.isEmpty()) {
            return;
        }
        device.assertThread();
        List<GpuDeviceAdapter.Resource> owned = List.copyOf(resources);
        long size = owned.stream().mapToLong(GpuDeviceAdapter.Resource::bytes).sum();
        // Enqueue ownership before attempting a fence; a driver failure must not lose the resources.
        pending.add(new Pending(device, owned, size, recycler));
        bytes += size;
    }

    void poll() {
        var iterator = pending.iterator();
        while (iterator.hasNext()) {
            Pending entry = iterator.next();
            entry.device.assertThread();
            if (entry.fence == null) {
                entry.fence = entry.device.fence();
            }
            if (entry.fence.awaitCompletion(0)) {
                iterator.remove();
                bytes -= entry.bytes;
                close(entry);
            }
        }
    }

    long bytes() {
        return bytes;
    }

    int batches() {
        return pending.size();
    }

    /** Only client shutdown may wait; normal frames exclusively use poll(). */
    boolean awaitRemaining(long budgetNanos) {
        long deadline = System.nanoTime() + budgetNanos;
        var iterator = pending.iterator();
        while (iterator.hasNext()) {
            Pending entry = iterator.next();
            entry.device.assertThread();
            if (entry.fence == null) {
                entry.fence = entry.device.fence();
            }
            long remaining = Math.max(0, deadline - System.nanoTime());
            if (!entry.fence.awaitCompletion(remaining)) {
                return false;
            }
            iterator.remove();
            bytes -= entry.bytes;
            close(entry);
        }
        return true;
    }

    private void close(Pending entry) {
        RuntimeException failure = null;
        try {
            for (GpuDeviceAdapter.Resource resource : entry.resources) {
                try {
                    if (entry.recycler != null && entry.recycler.recycle(resource)) {
                        continue;
                    }
                    resource.close();
                } catch (RuntimeException problem) {
                    if (failure == null) {
                        failure = problem;
                    } else {
                        failure.addSuppressed(problem);
                    }
                }
            }
        } finally {
            entry.fence.close();
        }
        if (failure != null) {
            throw failure;
        }
    }
}
