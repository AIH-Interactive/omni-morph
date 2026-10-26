package top.aihmc.omnimorph.model.ysm;

import top.aihmc.omnimorph.model.ysm.animation.YsmAnimationClip;
import top.aihmc.omnimorph.model.ysm.animation.YsmAnimationEvent;
import top.aihmc.omnimorph.model.ysm.animation.YsmAnimationEventWindow;

import java.util.List;

/**
 * D011: expectations follow Sparkle's single-cursor executors. Events at or before the running time fire
 * once; a loop wraps only when the time is strictly past the end, then instructions after the cursor run
 * (executeRemaining), sounds after the cursor are dropped (SoundKeyFrameExecutor.reset), and the new cycle
 * runs up to the wrapped time once, however many cycles the step covered. Reverse playback fires nothing.
 */
public final class YsmAnimationEventWindowSmokeTest {
    private YsmAnimationEventWindowSmokeTest() {
    }

    public static void main(String[] args) {
        var clip = new YsmAnimationClip("fixture", 1f, true);
        clip.events.add(new YsmAnimationEvent(0f, "timeline", "start"));
        clip.events.add(new YsmAnimationEvent(0.5f, "timeline", "middle"));
        clip.events.add(new YsmAnimationEvent(0.75f, "sound", "bell"));
        clip.events.add(new YsmAnimationEvent(1f, "timeline", "end"));
        clip.events.add(new YsmAnimationEvent(1.5f, "timeline", "beyond"));

        expect(clip, 0d, 0.5d, false, List.of("start", "middle"), false, List.of(), "initial event and midpoint");
        expect(clip, 0d, 0.5d, true, List.of("middle"), false, List.of(), "explicit initial event is not duplicated");
        expect(clip, 0.6d, 1d, true, List.of("bell", "end"), false, List.of(), "landing exactly on the end does not wrap");
        expect(clip, 1d, 1.25d, true, List.of("beyond"), true, List.of("start"),
                "past the end: remaining instructions, then the new cycle");
        expect(clip, 0.6d, 1.25d, true, List.of("end", "beyond"), true, List.of("start"),
                "tail sounds are dropped on wrap while tail instructions still run");
        expect(clip, 0.6d, 2.8d, true, List.of("end", "beyond"), true, List.of("start", "middle", "bell"),
                "a step over several cycles wraps once");
        expect(clip, 0.5d, 0.5d, true, List.of(), false, List.of(), "zero delta has no event");
        expect(clip, 0.75d, -0.25d, true, List.of(), false, List.of(), "reverse playback fires nothing");

        var once = new YsmAnimationClip("once", 1f, false);
        once.events.add(new YsmAnimationEvent(1f, "timeline", "end"));
        var noLoop = YsmAnimationEventWindow.window(once, 0.75d, 2d, false, true);
        equal(List.of("end"), values(noLoop.events()), "once playback has one end event");
        require(!noLoop.wrapped(), "once playback never wraps");
        equal(List.of(), values(YsmAnimationEventWindow.window(once, 1d, 2d, false, true).events()),
                "finished once has no repeat event");
        reject(() -> YsmAnimationEventWindow.window(clip, Double.NaN, 1d, true, true));
        System.out.println("PASS reference event cursor: inclusive end, strict wrap, tail sounds dropped, one wrap per step.");
    }

    private static void expect(YsmAnimationClip clip, double from, double to, boolean initialDispatched,
                               List<String> before, boolean wrapped, List<String> after, String message) {
        var window = YsmAnimationEventWindow.window(clip, from, to, true, initialDispatched);
        equal(before, values(window.beforeWrap()), message + " (before wrap)");
        require(window.wrapped() == wrapped, message + " (wrapped)");
        equal(after, values(window.afterWrap()), message + " (after wrap)");
    }

    private static List<String> values(List<YsmAnimationEvent> events) {
        return events.stream().map(YsmAnimationEvent::value).toList();
    }

    private static void equal(Object expected, Object actual, String message) {
        if (!expected.equals(actual)) {
            throw new AssertionError(message + ": " + actual);
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    private static void reject(Runnable action) {
        try {
            action.run();
            throw new AssertionError("Invalid event window accepted");
        } catch (IllegalArgumentException expected) {
            // Event windows reject nonfinite time.
        }
    }
}
