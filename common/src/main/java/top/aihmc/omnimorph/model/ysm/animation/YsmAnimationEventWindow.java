package top.aihmc.omnimorph.model.ysm.animation;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Selects chronological crossings before loop wrapping discards elapsed cycles. */
public final class YsmAnimationEventWindow {
    private static final int MAX_CROSSINGS = 1048576;

    private YsmAnimationEventWindow() {
    }

    private record Crossing(double time, double cycle, int index, YsmAnimationEvent event) {
    }

    public static List<YsmAnimationEvent> crossed(YsmAnimationClip clip, double from, double to,
                                                 boolean loop, boolean initialDispatched) {
        if (!Double.isFinite(from) || !Double.isFinite(to)) {
            throw new IllegalArgumentException("Invalid animation event window");
        }
        if (from == to || clip.events.isEmpty()) {
            return List.of();
        }
        boolean forward = to > from;
        boolean looping = loop && Float.isFinite(clip.length) && clip.length > 0f;
        List<YsmAnimationEvent> events = clip.orderedEvents();
        // Most render frames move forward without crossing a loop boundary. The
        // index excludes unrelated timeline events and avoids sorting crossings.
        if (forward && (!looping || Math.floor(from / clip.length) == Math.floor(to / clip.length))) {
            double cycleStart = looping ? Math.floor(from / clip.length) * clip.length : 0d;
            int start = bound(events, from - cycleStart, !initialDispatched && from == 0d);
            int end = bound(events, looping ? Math.min(clip.length, to - cycleStart) : to, false);
            if (end - start > MAX_CROSSINGS) {
                throw new IllegalArgumentException("Animation event crossing budget exceeded");
            }
            return events.subList(start, end);
        }
        List<Crossing> crossings = new ArrayList<>();
        for (int index = 0; index < clip.events.size(); index++) {
            YsmAnimationEvent event = clip.events.get(index);
            if (!Float.isFinite(event.time()) || event.time() < 0f) {
                throw new IllegalArgumentException("Invalid animation event time");
            }
            if (!looping) {
                boolean crossed = forward ? event.time() > from && event.time() <= to
                        : event.time() < from && event.time() >= to;
                if (crossed || forward && !initialDispatched && from == 0d && event.time() == 0f) {
                    add(crossings, new Crossing(event.time(), 0, index, event));
                }
                continue;
            }
            if (event.time() > clip.length) {
                continue;
            }
            double first = forward ? Math.floor((from - event.time()) / clip.length) + 1d
                    : Math.ceil((to - event.time()) / clip.length);
            double last = forward ? Math.floor((to - event.time()) / clip.length)
                    : Math.ceil((from - event.time()) / clip.length) - 1d;
            if (last - first + 1d > MAX_CROSSINGS - crossings.size()) {
                throw new IllegalArgumentException("Animation event crossing budget exceeded");
            }
            int count = (int) Math.max(0d, last - first + 1d);
            for (int occurrence = 0; occurrence < count; occurrence++) {
                double cycle = first + occurrence;
                add(crossings, new Crossing(cycle * clip.length + event.time(), cycle, index, event));
            }
            if (forward && !initialDispatched && from == 0d && event.time() == 0f) {
                add(crossings, new Crossing(0, 0, index, event));
            }
        }
        Comparator<Crossing> order = Comparator.comparingDouble(Crossing::time)
                .thenComparingDouble(Crossing::cycle).thenComparingInt(Crossing::index);
        crossings.sort(forward ? order : order.reversed());
        return crossings.stream().map(Crossing::event).toList();
    }

    private static int bound(List<YsmAnimationEvent> events, double time, boolean inclusive) {
        int low = 0;
        int high = events.size();
        while (low < high) {
            int middle = (low + high) >>> 1;
            double eventTime = events.get(middle).time();
            if (eventTime < time || !inclusive && eventTime == time) {
                low = middle + 1;
            } else {
                high = middle;
            }
        }
        return low;
    }

    private static void add(List<Crossing> crossings, Crossing crossing) {
        if (crossings.size() >= MAX_CROSSINGS) {
            throw new IllegalArgumentException("Animation event crossing budget exceeded");
        }
        crossings.add(crossing);
    }
}
