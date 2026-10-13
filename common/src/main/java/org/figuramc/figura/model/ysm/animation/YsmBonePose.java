package org.figuramc.figura.model.ysm.animation;

/** Owned pose sample: callers cannot mutate a transition snapshot through its vectors. */
public record YsmBonePose(float[] position, float[] rotation, float[] scale) {
    public YsmBonePose {
        position = copy(position);
        rotation = copy(rotation);
        scale = copy(scale);
    }

    @Override
    public float[] position() {
        return copy(position);
    }

    @Override
    public float[] rotation() {
        return copy(rotation);
    }

    @Override
    public float[] scale() {
        return copy(scale);
    }

    public static YsmBonePose blend(YsmBonePose outgoing, YsmBonePose incoming,
                                    float progress, boolean shortestRotation) {
        if (!Float.isFinite(progress)) {
            throw new IllegalArgumentException("Invalid pose transition progress");
        }
        return new YsmBonePose(
                blendVector(outgoing.position, incoming.position, progress, 0f, false),
                blendVector(outgoing.rotation, incoming.rotation, progress, 0f, shortestRotation),
                blendVector(outgoing.scale, incoming.scale, progress, 1f, false));
    }

    public static YsmBonePose accumulate(YsmBonePose previous, YsmBonePose sample, float weight) {
        if (!Float.isFinite(weight) || weight < 0f) {
            throw new IllegalArgumentException("Invalid pose contribution weight");
        }
        YsmBonePose base = previous == null ? new YsmBonePose(null, null, null) : previous;
        return new YsmBonePose(
                accumulateVector(base.position, sample.position, weight, false),
                accumulateVector(base.rotation, sample.rotation, weight, false),
                accumulateVector(base.scale, sample.scale, weight, true));
    }

    private static float[] accumulateVector(float[] previous, float[] sample, float weight, boolean scale) {
        if (previous == null && sample == null) {
            return null;
        }
        float[] result = new float[3];
        for (int axis = 0; axis < 3; axis++) {
            float base = previous == null ? (scale ? 1f : 0f) : previous[axis];
            float value = sample == null ? (scale ? 1f : 0f) : sample[axis];
            result[axis] = scale ? base * (1f + (value - 1f) * weight) : base + value * weight;
        }
        return result;
    }

    private static float[] blendVector(float[] outgoing, float[] incoming, float progress,
                                       float defaultValue, boolean shortestRotation) {
        if (outgoing == null && incoming == null) {
            return null;
        }
        float[] result = new float[3];
        for (int axis = 0; axis < 3; axis++) {
            double from = outgoing == null ? defaultValue : outgoing[axis];
            double to = incoming == null ? defaultValue : incoming[axis];
            double delta = to - from;
            if (shortestRotation) {
                delta = ((delta % 360d) + 540d) % 360d - 180d;
            }
            result[axis] = (float) (from + delta * progress);
        }
        return result;
    }

    private static float[] copy(float[] vector) {
        if (vector == null) {
            return null;
        }
        if (vector.length != 3) {
            throw new IllegalArgumentException("Pose vector must have three components");
        }
        return vector.clone();
    }
}
