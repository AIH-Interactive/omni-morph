package top.aihmc.omnimorph.backend2.protocol;

/** Tick-driven retries; only an explicit connection request enables recovery. */
public final class CloudReconnect {
    private static final int INITIAL_DELAY = 100;
    private static final int MAXIMUM_DELAY = 1200;
    private boolean enabled;
    private int nextDelay = INITIAL_DELAY;
    private int remaining = -1;

    public synchronized void start() {
        enabled = true;
        connected();
    }

    public synchronized void stop() {
        enabled = false;
        remaining = -1;
    }

    public synchronized void connected() {
        remaining = -1;
        nextDelay = INITIAL_DELAY;
    }

    public synchronized void failed() {
        if (enabled) {
            remaining = nextDelay;
            nextDelay = Math.min(MAXIMUM_DELAY, nextDelay * 2);
        }
    }

    public synchronized boolean tick(boolean connecting) {
        if (!enabled || connecting || remaining < 0) {
            return false;
        }
        if (--remaining <= 0) {
            remaining = -1;
            return true;
        }
        return false;
    }
}
