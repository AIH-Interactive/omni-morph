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
        List<Crossing> crossings = new ArrayList<>();
        boolean looping = loop && Float.isFinite(clip.length) && clip.length > 0f;
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

    private static void add(List<Crossing> crossings, Crossing crossing) {
        if (crossings.size() >= MAX_CROSSINGS) {
            throw new IllegalArgumentException("Animation event crossing budget exceeded");
        }
        crossings.add(crossing);
    }
}
