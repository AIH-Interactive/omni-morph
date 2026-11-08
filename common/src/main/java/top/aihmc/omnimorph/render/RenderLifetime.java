package top.aihmc.omnimorph.render;

import java.util.Objects;

/** Validity of captured render work across model, resource and frame changes. */
public final class RenderLifetime {
    public static final RenderLifetime INSTANCE = new RenderLifetime();

    public record Lease(RenderOwner owner, long ownerGeneration, long resources, long frame, String view) {
        public Lease {
            Objects.requireNonNull(owner);
            Objects.requireNonNull(view);
        }
    }

    private volatile long resources;
    private volatile long frame;

    public synchronized void beginFrame() {
        frame++;
    }

    public synchronized void invalidateResources() {
        resources++;
    }

    public Lease capture(RenderOwner owner, String view) {
        return new Lease(owner, owner.generation(), resources, frame, view);
    }

    public boolean isCurrent(Lease lease) {
        return lease.owner().isCurrent() && lease.ownerGeneration() == lease.owner().generation()
                && lease.resources() == resources && lease.frame() == frame;
    }
}
