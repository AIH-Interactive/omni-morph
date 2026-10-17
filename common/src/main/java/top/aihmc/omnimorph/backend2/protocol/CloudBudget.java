package top.aihmc.omnimorph.backend2.protocol;

import java.io.IOException;
import java.util.ArrayDeque;

/** Sliding control-plane budget shared by HTTP RPC and socket writes in one adapter. */
final class CloudBudget {
    private record Charge(long time, int bytes) {}
    private final ArrayDeque<Charge> charges = new ArrayDeque<>();
    private int bytes;
    private int maxMessages = 24;
    private int maxBytes = 262144;

    synchronized void configure(int messages, int bytes) throws IOException {
        if (messages <= 0 || bytes <= 0) {
            throw new IOException("Invalid negotiated control budget");
        }
        maxMessages = Math.min(24, messages);
        maxBytes = Math.min(262144, bytes);
    }

    synchronized boolean take(int size) {
        long now = System.nanoTime();
        while (!charges.isEmpty() && now - charges.getFirst().time() >= 1_000_000_000L) {
            bytes -= charges.removeFirst().bytes();
        }
        if (size < 0 || charges.size() >= maxMessages || size + bytes > maxBytes) {
            return false;
        }
        charges.addLast(new Charge(now, size));
        bytes += size;
        return true;
    }

    void await(int size, CloudHttp connection) throws Exception {
        if (size > maxBytes) {
            throw new IOException("Control message exceeds byte budget");
        }
        while (!take(size)) {
            connection.requireOpen();
            Thread.sleep(20);
        }
        connection.requireOpen();
    }
}
