package top.aihmc.omnimorph.render.acceleration;

/** Engines supply numeric input after their own semantics and complexity accounting. */
@FunctionalInterface
public interface EngineAccelerationAdapter {
    DrawBatch capture(AccelerationService.Lease lease);
}
