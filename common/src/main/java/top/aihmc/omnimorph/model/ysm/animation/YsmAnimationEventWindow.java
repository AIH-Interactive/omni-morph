package top.aihmc.omnimorph.model.ysm.animation;

import java.util.ArrayList;
import java.util.List;

/**
 * Event selection for one playback step, matching Sparkle's single-cursor executors
 * (AnimationControllerInstance with InstructionKeyFrameExecutor and SoundKeyFrameExecutor):
 * <ul>
 *   <li>events at or before the running time fire once, in time order;</li>
 *   <li>a loop wraps only when the running time is strictly past the clip end. Instructions after the
 *       cursor then run ({@code executeRemaining}), but sounds after the cursor are dropped and owned
 *       sounds stop ({@code SoundKeyFrameExecutor.reset}); the new cycle runs up to the wrapped time once,
 *       even if the step covered several cycles;</li>
 *   <li>reverse playback fires nothing, as the reference cursor only moves forward.</li>
 * </ul>
 */
public final class YsmAnimationEventWindow {
    private YsmAnimationEventWindow() {
    }

    /**
     * @param beforeWrap events of the cycle being left (or the only cycle), in time order
     * @param wrapped    whether a loop wrapped during this step; owned sounds must stop before {@code afterWrap}
     * @param afterWrap  events of the new cycle up to the wrapped time, in time order
     */
    public record Window(List<YsmAnimationEvent> beforeWrap, boolean wrapped, List<YsmAnimationEvent> afterWrap) {
        private static final Window EMPTY = new Window(List.of(), false, List.of());

        public List<YsmAnimationEvent> events() {
            if (afterWrap.isEmpty()) {
                return beforeWrap;
            }
            List<YsmAnimationEvent> all = new ArrayList<>(beforeWrap);
            all.addAll(afterWrap);
            return all;
        }
    }

    /**
     * @param from              the running time after the previous step (already wrapped)
     * @param to                the running time after this step, before loop wrapping
     * @param initialDispatched whether the clip's time-zero events already fired
     */
    public static Window window(YsmAnimationClip clip, double from, double to,
                                boolean loop, boolean initialDispatched) {
        if (!Double.isFinite(from) || !Double.isFinite(to)) {
            throw new IllegalArgumentException("Invalid animation event window");
        }
        if (to <= from || clip.events.isEmpty()) {
            return Window.EMPTY;
        }
        List<YsmAnimationEvent> events = clip.orderedEvents();
        boolean includeFrom = !initialDispatched && from == 0d;
        boolean looping = loop && Float.isFinite(clip.length) && clip.length > 0f;
        if (!looping || to <= clip.length) {
            return new Window(between(events, from, includeFrom, to, true), false, List.of());
        }
        List<YsmAnimationEvent> remaining = new ArrayList<>();
        for (YsmAnimationEvent event : between(events, from, includeFrom, Double.POSITIVE_INFINITY, false)) {
            if (!"sound".equals(event.type())) {
                remaining.add(event);
            }
        }
        double wrappedTime = to - Math.floor(to / clip.length) * clip.length;
        return new Window(remaining, true, between(events, 0d, true, wrappedTime, true));
    }

    /** Events with {@code from < time <= to} (or {@code from <= time} when {@code includeFrom}). */
    private static List<YsmAnimationEvent> between(List<YsmAnimationEvent> events, double from, boolean includeFrom,
                                                   double to, boolean includeTo) {
        List<YsmAnimationEvent> result = new ArrayList<>();
        for (YsmAnimationEvent event : events) {
            double time = event.time();
            boolean afterFrom = includeFrom ? time >= from : time > from;
            boolean beforeTo = includeTo ? time <= to : time < to;
            if (afterFrom && beforeTo) {
                result.add(event);
            }
        }
        return result;
    }
}
