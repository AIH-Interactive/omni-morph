package org.figuramc.figura.model.ysm.sync;

import java.util.ArrayDeque;

/** Conservative rolling budget including ordinary Figura Pings on the same connection. */
public final class YsmSyncPingBudget {
    private static final int WINDOW_TICKS = 20;
    private static final int MAX_BYTES = 1024;
    private static final int MAX_MESSAGES = 32;
    private static final int C2S_HEADER_BYTES = 6;
    private final ArrayDeque<Sent> sent = new ArrayDeque<>();
    private int bytes;
    private long lastTick = -1;

    private record Sent(long tick, int bytes) {
    }

    /** Records existing Ping traffic without changing whether that traffic is allowed. */
    public synchronized void recordSent(long tick, int payloadBytes) {
        expire(tick);
        int size = wireBytes(payloadBytes);
        if (sent.size() >= MAX_MESSAGES || size > MAX_BYTES - bytes) {
            // Collapse excess ordinary traffic into one saturated entry. It blocks YSM for a full window.
            sent.clear();
            bytes = MAX_BYTES + 1;
            sent.addLast(new Sent(tick, bytes));
        } else {
            bytes += size;
            sent.addLast(new Sent(tick, size));
        }
    }

    public synchronized boolean canSend(long tick, int payloadBytes) {
        expire(tick);
        return sent.size() < MAX_MESSAGES && wireBytes(payloadBytes) <= MAX_BYTES - bytes;
    }

    public synchronized void clear() {
        sent.clear();
        bytes = 0;
        lastTick = -1;
    }

    private void expire(long tick) {
        if (tick < 0) {
            throw new IllegalArgumentException("Invalid Ping clock");
        }
        if (tick < lastTick) {
            clear();
        }
        lastTick = tick;
        while (!sent.isEmpty() && tick - sent.getFirst().tick() >= WINDOW_TICKS) {
            bytes -= sent.removeFirst().bytes();
        }
        bytes = Math.max(0, bytes);
    }

    private static int wireBytes(int payloadBytes) {
        if (payloadBytes < 0 || payloadBytes > Integer.MAX_VALUE - C2S_HEADER_BYTES) {
            throw new IllegalArgumentException("Invalid Ping size");
        }
        return payloadBytes + C2S_HEADER_BYTES;
    }
}
