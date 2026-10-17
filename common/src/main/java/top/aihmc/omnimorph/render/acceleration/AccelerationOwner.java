package top.aihmc.omnimorph.render.acceleration;

import java.util.concurrent.atomic.AtomicLong;

/** One published model lifetime; retirement never resurrects an old model. */
public final class AccelerationOwner {
    private static final AtomicLong NEXT_GENERATION = new AtomicLong();
    private final long generation = NEXT_GENERATION.incrementAndGet();
    private volatile boolean retired;

    public long generation() {
        return generation;
    }

    public boolean isCurrent() {
        return !retired;
    }

    public void retire() {
        retired = true;
    }
}
