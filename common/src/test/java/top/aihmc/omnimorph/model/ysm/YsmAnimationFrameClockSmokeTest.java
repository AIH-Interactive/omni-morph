package top.aihmc.omnimorph.model.ysm;

import top.aihmc.omnimorph.model.ysm.animation.YsmAnimationClip;
import top.aihmc.omnimorph.model.ysm.animation.YsmAnimationEvent;
import top.aihmc.omnimorph.model.ysm.animation.YsmAnimationEventWindow;
import top.aihmc.omnimorph.model.ysm.animation.YsmAnimationFrameClock;
import top.aihmc.omnimorph.model.ysm.animation.YsmRenderFrameClock;

import java.util.ArrayList;
import java.util.List;

public final class YsmAnimationFrameClockSmokeTest {
    private YsmAnimationFrameClockSmokeTest() {
    }

    public static void main(String[] args) {
        var clock = new YsmAnimationFrameClock();
        var independent = new YsmAnimationFrameClock();
        var clip = new YsmAnimationClip("fixture", 0.1f, true);
        clip.events.add(new YsmAnimationEvent(0f, "timeline", "start"));
        clip.events.add(new YsmAnimationEvent(0.05f, "timeline", "middle"));
        clip.events.add(new YsmAnimationEvent(0.1f, "timeline", "end"));
        List<String> trace = new ArrayList<>();
        double cursor = 0d;
        int advances = 0;
        int samples = 0;
        for (int frame = 0; frame < 3; frame++) {
            YsmRenderFrameClock.beginFrame();
            long identity = YsmRenderFrameClock.currentFrame();
            float age = frame == 0 ? 0f : 1f;
            for (int pass = 0; pass < 3; pass++) {
                var step = clock.step(identity, pass == 0 ? age : 999f);
                samples++;
                if (step.advance()) {
                    advances++;
                    double next = cursor + step.deltaSeconds();
                    trace.addAll(YsmAnimationEventWindow.window(clip, cursor, next, true, true).events()
                            .stream().map(YsmAnimationEvent::value).toList());
                    cursor = next;
                } else {
                    equal(0f, step.deltaSeconds(), "duplicate pass delta");
                }
            }
            require(independent.step(identity, age).advance(), "players do not share advancement state");
        }
        require(advances == 3 && samples == 9, "three frames advance once and all nine passes can sample");
        // D011: reaching the end exactly does not wrap, so the next cycle's start has not fired yet.
        require(trace.equals(List.of("middle", "end")), "loop events remain single-dispatch across repeated passes");
        equal(0.1f, (float) cursor, "equal age in a new frame does not invent another tick");
        var fallback = new YsmAnimationFrameClock();
        equal(0.05f, fallback.step(1, Float.NaN).deltaSeconds(), "missing age initial fallback");
        require(!fallback.step(1, 100f).advance(), "missing age still obeys frame identity");
        equal(0.05f, fallback.step(2, 100f).deltaSeconds(), "missing prior age fallback");
        equal(0.05f, fallback.step(3, 99f).deltaSeconds(), "backward age fallback");
        equal(0.05f, fallback.step(4, 200f).deltaSeconds(), "large gap bounded fallback");
        equal(0.025f, fallback.step(5, 200.5f).deltaSeconds(), "fractional tick delta");
        System.out.println("M3 real-frame identity gate, repeated pass cadence and loop event trace checks passed.");
    }

    private static void equal(float expected, float actual, String message) {
        require(Float.isFinite(actual) && Math.abs(expected - actual) <= 1e-5f, message);
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
