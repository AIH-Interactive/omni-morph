package top.aihmc.omnimorph.render.acceleration;

import top.aihmc.omnimorph.render.RenderLifetime;

/** Engines supply numeric input after their own semantics and complexity accounting. */
@FunctionalInterface
public interface EngineAccelerationAdapter {
    DrawBatch capture(RenderLifetime.Lease lease);
}
