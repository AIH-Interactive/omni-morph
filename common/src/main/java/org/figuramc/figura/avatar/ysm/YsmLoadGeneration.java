package org.figuramc.figura.avatar.ysm;

/** Serializes request invalidation and publication without coupling decoding to Avatar. */
public final class YsmLoadGeneration {
    private long generation;

    public synchronized long begin() {
        return ++generation;
    }

    public synchronized boolean current(long expected) {
        return expected == generation;
    }

    public synchronized boolean publish(long expected, Runnable publication) {
        if (!current(expected)) {
            return false;
        }
        publication.run();
        return true;
    }
}
