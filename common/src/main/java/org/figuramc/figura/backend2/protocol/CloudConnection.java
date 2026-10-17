package org.figuramc.figura.backend2.protocol;

import java.util.ArrayDeque;

/** Identity and bounded game-thread mailbox for one protocol/endpoint/session epoch. */
public final class CloudConnection implements AutoCloseable {
    private record Callback(int bytes, Runnable action) {}

    private final ArrayDeque<Callback> callbacks = new ArrayDeque<>();
    public final long epoch;
    public final CloudProtocol protocol;
    private volatile boolean active = true;
    private volatile String failure;
    private int queuedBytes;

    public CloudConnection(long epoch, CloudProtocol protocol) {
        this.epoch = epoch;
        this.protocol = protocol;
    }

    public synchronized boolean post(int bytes, Runnable callback) {
        if (!active) {
            return false;
        }
        if (bytes < 0 || callbacks.size() >= 128 || queuedBytes + bytes > 262144) {
            fail("Client receive queue requires recovery");
            return false;
        }
        callbacks.addLast(new Callback(bytes, callback));
        queuedBytes += bytes;
        return true;
    }

    /** Called only by the game tick. Retirement and execution cannot race. */
    public synchronized void drain() {
        while (active && !callbacks.isEmpty()) {
            Callback callback = callbacks.removeFirst();
            queuedBytes -= callback.bytes();
            callback.action().run();
        }
    }

    /** Keep decoded model transfers bounded until the game thread publishes or retires them. */
    public void publishModel(Runnable action) throws Exception {
        var applied = new java.util.concurrent.CompletableFuture<Void>();
        if (!post(128, () -> {
            try {
                action.run();
                applied.complete(null);
            } catch (Exception exception) {
                applied.completeExceptionally(exception);
            }
        })) {
            throw new java.io.IOException("Model connection retired");
        }
        long deadline = System.nanoTime() + java.time.Duration.ofSeconds(60).toNanos();
        while (active && System.nanoTime() < deadline) {
            try {
                applied.get(100, java.util.concurrent.TimeUnit.MILLISECONDS);
                return;
            } catch (java.util.concurrent.TimeoutException pending) {
                // Retirement is checked independently of a game tick draining the mailbox.
            }
        }
        throw new java.io.IOException("Model publication cancelled or timed out");
    }

    public boolean active() {
        return active;
    }

    public String failure() {
        return failure;
    }

    public void fail(String reason) {
        failure = reason;
        close();
    }

    @Override
    public void close() {
        synchronized (this) {
            active = false;
            callbacks.clear();
            queuedBytes = 0;
        }
        protocol.close();
    }
}
