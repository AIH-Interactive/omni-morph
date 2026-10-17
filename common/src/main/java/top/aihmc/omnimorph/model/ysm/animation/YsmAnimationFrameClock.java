package top.aihmc.omnimorph.model.ysm.animation;

/** Per-player advancement gate; sampling may still happen in every render pass. */
public final class YsmAnimationFrameClock {
    private boolean initialized;
    private long lastFrame;
    private float lastAge = Float.NaN;

    public record Step(boolean advance, float deltaSeconds) {
    }

    public Step step(long frame, float ageInTicks) {
        if (initialized && frame == lastFrame) {
            return new Step(false, 0f);
        }
        initialized = true;
        lastFrame = frame;
        float deltaTicks = Float.isFinite(lastAge) && Float.isFinite(ageInTicks) ? ageInTicks - lastAge : 1f;
        lastAge = ageInTicks;
        if (!Float.isFinite(deltaTicks) || deltaTicks < 0f || deltaTicks > 5f) {
            deltaTicks = 1f;
        }
        return new Step(true, deltaTicks / 20f);
    }
}
