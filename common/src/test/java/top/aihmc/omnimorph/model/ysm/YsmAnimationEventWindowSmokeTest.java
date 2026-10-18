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
        verifyIndexedWindows();
        System.out.println("M3 chronological event windows, loop endpoints, reverse order and bounded crossings passed.");
    }

    private static void verifyIndexedWindows() {
        var random = new java.util.Random(341);
        for (int trial = 0; trial < 2000; trial++) {
            var clip = new YsmAnimationClip("indexed", 1f, true);
            for (int index = 0; index < 24; index++) {
                clip.events.add(new YsmAnimationEvent(random.nextInt(21) / 16f, "timeline", "event" + index));
            }
            // Exercise cache invalidation, stable ties, unsorted input and loop boundaries.
            YsmAnimationEventWindow.crossed(clip, 0d, .25d, false, false);
            clip.events.set(0, new YsmAnimationEvent(.25f, "timeline", "changed"));
            clip.events.add(new YsmAnimationEvent(0f, "timeline", "added"));
            clip.events.subList(1, 3).clear();
            double from = random.nextInt(97) / 16d - 3d;
            double to = random.nextInt(97) / 16d - 3d;
            boolean loop = random.nextBoolean();
            boolean initial = random.nextBoolean();
            var expected = reference(clip, from, to, loop, initial);
            equal(expected, YsmAnimationEventWindow.crossed(clip, from, to, loop, initial),
                    "indexed event selection preserves original crossings trial=" + trial);
        }
        System.out.println("PASS indexed event windows: 2000 differential cases, mutable lists and stable event order");
    }

    private record ExpectedCrossing(double time, int cycle, int index, YsmAnimationEvent event) {}

    private static List<YsmAnimationEvent> reference(YsmAnimationClip clip, double from, double to,
                                                    boolean loop, boolean initial) {
        if (from == to) {
            return List.of();
        }
        boolean forward = to > from;
        var crossings = new java.util.ArrayList<ExpectedCrossing>();
        for (int index = 0; index < clip.events.size(); index++) {
            var event = clip.events.get(index);
            if (loop && event.time() > clip.length) {
                continue;
            }
            for (int cycle = loop ? -10 : 0; cycle <= (loop ? 10 : 0); cycle++) {
                double time = cycle * clip.length + event.time();
                boolean crossed = forward ? time > from && time <= to : time < from && time >= to;
                boolean first = forward && !initial && from == 0d && event.time() == 0f && cycle == 0;
                if (crossed || first) {
                    crossings.add(new ExpectedCrossing(time, cycle, index, event));
                }
            }
        }
        var order = java.util.Comparator.comparingDouble(ExpectedCrossing::time)
                .thenComparingInt(ExpectedCrossing::cycle).thenComparingInt(ExpectedCrossing::index);
        crossings.sort(forward ? order : order.reversed());
        return crossings.stream().map(ExpectedCrossing::event).toList();
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
