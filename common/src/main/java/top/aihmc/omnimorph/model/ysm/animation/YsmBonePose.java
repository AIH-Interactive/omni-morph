package top.aihmc.omnimorph.model.ysm.animation;

import org.joml.Quaternionf;

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

    /**
     * Transition blend matching Sparkle TransitionVector3f: position and scale are linear; rotation is
     * the nlerp of the rendered ZYX quaternions including the bone's rest rotation (degrees, may be null).
     */
    public static YsmBonePose blend(YsmBonePose outgoing, YsmBonePose incoming,
                                    float progress, float[] restRotation) {
        if (!Float.isFinite(progress)) {
            throw new IllegalArgumentException("Invalid pose transition progress");
        }
        return new YsmBonePose(
                blendVector(outgoing.position, incoming.position, progress, 0f),
                blendRotation(outgoing.rotation, incoming.rotation, progress, restRotation),
                blendVector(outgoing.scale, incoming.scale, progress, 1f));
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

    private static float[] blendVector(float[] outgoing, float[] incoming, float progress, float defaultValue) {
        if (outgoing == null && incoming == null) {
            return null;
        }
        float[] result = new float[3];
        for (int axis = 0; axis < 3; axis++) {
            double from = outgoing == null ? defaultValue : outgoing[axis];
            double to = incoming == null ? defaultValue : incoming[axis];
            result[axis] = (float) (from + (to - from) * progress);
        }
        return result;
    }

    /** Sparkle MathUtil.nlerpEulerAngles, expressed in OmniMorph's rendered rotation convention. */
    private static float[] blendRotation(float[] outgoing, float[] incoming, float progress, float[] restRotation) {
        if (outgoing == null && incoming == null) {
            return null;
        }
        float[] from = outgoing == null ? new float[3] : outgoing;
        float[] to = incoming == null ? new float[3] : incoming;
        if (progress == 0f) {
            return from.clone();
        }
        float[] rest = restRotation == null ? new float[3] : restRotation;
        Quaternionf start = renderedRotation(from, rest);
        Quaternionf end = renderedRotation(to, rest);
        start.nlerp(end, progress, end);
        float[] rendered = eulerZYXDegrees(end);
        return new float[]{rest[0] - rendered[0], rest[1] - rendered[1], rendered[2] - rest[2]};
    }

    /** The renderer applies Z, then Y, then X of (rest.x - anim.x, rest.y - anim.y, rest.z + anim.z). */
    private static Quaternionf renderedRotation(float[] animation, float[] rest) {
        return new Quaternionf().rotateZYX(
                (float) Math.toRadians(rest[2] + animation[2]),
                (float) Math.toRadians(rest[1] - animation[1]),
                (float) Math.toRadians(rest[0] - animation[0]));
    }

    /** Same extraction as Sparkle MathUtil.getEulerAnglesZYX, returned in degrees. */
    private static float[] eulerZYXDegrees(Quaternionf q) {
        double x = Math.atan2(q.y * q.z + q.w * q.x, 0.5d - q.x * q.x - q.y * q.y);
        double sinY = Math.max(-1d, Math.min(1d, -2d * (q.x * q.z - q.w * q.y)));
        double y = Math.asin(sinY);
        double z = Math.atan2(q.x * q.y + q.w * q.z, 0.5d - q.y * q.y - q.z * q.z);
        return new float[]{(float) Math.toDegrees(x), (float) Math.toDegrees(y), (float) Math.toDegrees(z)};
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
