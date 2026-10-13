package org.figuramc.figura.model.ysm.controller;

import java.util.Map;
import java.util.TreeMap;

/** Immutable seconds-based transition; curve values represent the remaining outgoing pose. */
public final class YsmBlendTransition {
    private final float duration;
    private final float[] times;
    private final float[] incoming;

    private YsmBlendTransition(float duration, float[] times, float[] incoming) {
        this.duration = duration;
        this.times = times;
        this.incoming = incoming;
    }

    public static YsmBlendTransition linear(float duration) {
        if (!Float.isFinite(duration) || duration < 0f) {
            throw new IllegalArgumentException("Invalid transition duration");
        }
        return new YsmBlendTransition(duration, new float[0], new float[0]);
    }

    public static YsmBlendTransition curve(Map<Float, Float> outgoingValues) {
        if (outgoingValues == null || outgoingValues.isEmpty()) {
            return linear(0f);
        }
        var sorted = new TreeMap<>(outgoingValues);
        float[] times = new float[sorted.size()];
        float[] incoming = new float[sorted.size()];
        int index = 0;
        for (var entry : sorted.entrySet()) {
            float time = entry.getKey();
            float value = entry.getValue();
            if (!Float.isFinite(time) || time < 0f || !Float.isFinite(value)) {
                throw new IllegalArgumentException("Invalid transition point");
            }
            times[index] = time;
            incoming[index] = 1f - value;
            index++;
        }
        return new YsmBlendTransition(times[times.length - 1], times, incoming);
    }

    public float duration() {
        return duration;
    }

    public float progress(float elapsedSeconds) {
        if (!Float.isFinite(elapsedSeconds)) {
            throw new IllegalArgumentException("Invalid transition time");
        }
        if (times.length == 0) {
            return duration == 0f ? 1f : Math.clamp(elapsedSeconds / duration, 0f, 1f);
        }
        if (elapsedSeconds <= times[0]) {
            return incoming[0];
        }
        for (int index = 1; index < times.length; index++) {
            if (elapsedSeconds < times[index]) {
                float fraction = (elapsedSeconds - times[index - 1]) / (times[index] - times[index - 1]);
                return incoming[index - 1] + fraction * (incoming[index] - incoming[index - 1]);
            }
        }
        return incoming[incoming.length - 1];
    }
}
