package top.aihmc.omnimorph.model.ysm;

import top.aihmc.omnimorph.model.ysm.animation.YsmAnimationClip;
import top.aihmc.omnimorph.model.ysm.animation.YsmAnimationEvent;
import top.aihmc.omnimorph.model.ysm.animation.YsmAnimationEventWindow;

import java.util.List;

public final class YsmAnimationEventWindowSmokeTest {
    private YsmAnimationEventWindowSmokeTest() {
    }

    public static void main(String[] args) {
        var clip = new YsmAnimationClip("fixture", 1f, true);
        clip.events.add(new YsmAnimationEvent(0f, "timeline", "start"));
        clip.events.add(new YsmAnimationEvent(0.5f, "timeline", "middle"));
        clip.events.add(new YsmAnimationEvent(1f, "timeline", "end"));
        equal(List.of("start", "middle"), values(clip, 0d, 0.5d, false), "initial event and midpoint");
        equal(List.of("middle"), values(clip, 0d, 0.5d, true), "explicit initial event is not duplicated");
        equal(List.of("end", "start"), values(clip, 0.75d, 1d, true), "endpoint precedes next cycle zero event");
        equal(List.of("end", "start", "middle", "end", "start", "middle"),
                values(clip, 0.75d, 2.75d, true), "multiple elapsed cycles retain every crossing in order");
        equal(List.of(), values(clip, 0.5d, 0.5d, true), "zero delta has no event");
        equal(List.of("middle", "start", "end"), values(clip, 0.75d, -0.25d, true), "reverse crossings have reverse temporal order");
        equal(List.of("end"), YsmAnimationEventWindow.crossed(clip, 0.75d, 2d, false, true)
                .stream().map(YsmAnimationEvent::value).toList(), "once playback has one end event");
        equal(List.of(), YsmAnimationEventWindow.crossed(clip, 1d, 2d, false, true), "finished once has no repeat event");
        reject(() -> YsmAnimationEventWindow.crossed(clip, Double.NaN, 1d, true, true));
        reject(() -> YsmAnimationEventWindow.crossed(clip, 0d, 1048577d, true, true));
        System.out.println("M3 chronological event windows, loop endpoints, reverse order and bounded crossings passed.");
    }

    private static List<String> values(YsmAnimationClip clip, double from, double to, boolean initialDispatched) {
        return YsmAnimationEventWindow.crossed(clip, from, to, true, initialDispatched)
                .stream().map(YsmAnimationEvent::value).toList();
    }

    private static void equal(Object expected, Object actual, String message) {
        if (!expected.equals(actual)) {
            throw new AssertionError(message + ": " + actual);
        }
    }

    private static void reject(Runnable action) {
        try {
            action.run();
            throw new AssertionError("Invalid event window accepted");
        } catch (IllegalArgumentException expected) {
            // Event windows reject nonfinite time and unbounded dispatch work.
        }
    }
}
