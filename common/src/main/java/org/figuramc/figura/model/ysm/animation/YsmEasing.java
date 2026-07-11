package org.figuramc.figura.model.ysm.animation;

/**
 * Bedrock animation easing utilities.
 * <p>
 * The Bedrock animation format supports per-keyframe easing types.
 * This class provides the easing implementations used by
 * {@link YsmAnimationPlayer#evaluateChannel}.
 * </p>
 */
public final class YsmEasing {

    private YsmEasing() {}

    /**
     * Apply an easing curve to a linear interpolation ratio [0, 1].
     *
     * @param easing the easing type string (e.g. "linear", "catmullrom")
     * @param t      the linear interpolation ratio, clamped to [0, 1]
     * @return the eased ratio in [0, 1]
     */
    public static float apply(String easing, float t) {
        if (easing == null || easing.isEmpty() || "linear".equalsIgnoreCase(easing))
            return t;

        // Clamp input
        if (t <= 0f) return 0f;
        if (t >= 1f) return 1f;

        switch (easing.toLowerCase(java.util.Locale.US)) {
            // --- Quadratic ---
            case "ease_in_quad":
            case "easeinquad":
                return t * t;
            case "ease_out_quad":
            case "easeoutquad":
                return t * (2f - t);
            case "ease_in_out_quad":
            case "easeinoutquad":
                return t < 0.5f ? 2f * t * t : -1f + (4f - 2f * t) * t;

            // --- Cubic ---
            case "ease_in_cubic":
            case "easeincubic":
                return t * t * t;
            case "ease_out_cubic":
            case "easeoutcubic":
                float t1 = t - 1f;
                return t1 * t1 * t1 + 1f;
            case "ease_in_out_cubic":
            case "easeinoutcubic":
                return t < 0.5f ? 4f * t * t * t : (t - 1f) * (2f * t - 2f) * (2f * t - 2f) + 1f;

            // --- Sine ---
            case "ease_in_sine":
            case "easeinsine":
                return 1f - (float) Math.cos(t * Math.PI / 2f);
            case "ease_out_sine":
            case "easeoutsine":
                return (float) Math.sin(t * Math.PI / 2f);
            case "ease_in_out_sine":
            case "easeinoutsine":
                return (float) (-0.5f * (Math.cos(Math.PI * t) - 1f));

            // --- Exponential ---
            case "ease_in_expo":
            case "easeinexpo":
                return t <= 0f ? 0f : (float) Math.pow(2f, 10f * (t - 1f));
            case "ease_out_expo":
            case "easeoutexpo":
                return t >= 1f ? 1f : (float) (1f - Math.pow(2f, -10f * t));
            case "ease_in_out_expo":
            case "easeinoutexpo":
                if (t <= 0f) return 0f;
                if (t >= 1f) return 1f;
                return t < 0.5f
                        ? (float) (0.5f * Math.pow(2f, 20f * t - 10f))
                        : (float) (0.5f * (2f - Math.pow(2f, -20f * t + 10f)));

            // --- Elastic ---
            case "ease_in_elastic":
            case "easeinelastic":
                return elasticIn(t);
            case "ease_out_elastic":
            case "easeoutelastic":
                return elasticOut(t);
            case "ease_in_out_elastic":
            case "easeinoutelastic":
                return t < 0.5f
                        ? 0.5f * elasticIn(2f * t)
                        : 0.5f * elasticOut(2f * t - 1f) + 0.5f;

            // --- Back ---
            case "ease_in_back":
            case "easeinback":
                return t * t * (2.70158f * t - 1.70158f);
            case "ease_out_back":
            case "easeoutback":
                float tb = t - 1f;
                return tb * tb * (2.70158f * tb + 1.70158f) + 1f;
            case "ease_in_out_back":
            case "easeinoutback":
                return t < 0.5f
                        ? 0.5f * (2f * t * 2f * t * (3.5949f * 2f * t - 2.5949f))
                        : 0.5f * ((2f * t - 2f) * (2f * t - 2f) * (3.5949f * (2f * t - 2f) + 2.5949f) + 2f);

            // --- Bounce ---
            case "ease_in_bounce":
            case "easeinbounce":
                return 1f - bounceOut(1f - t);
            case "ease_out_bounce":
            case "easeoutbounce":
                return bounceOut(t);
            case "ease_in_out_bounce":
            case "easeinoutbounce":
                return t < 0.5f
                        ? 0.5f * (1f - bounceOut(1f - 2f * t))
                        : 0.5f * bounceOut(2f * t - 1f) + 0.5f;

            // --- Spring (Minecraft-style) ---
            case "spring":
                return spring(t);

            default:
                return t;
        }
    }

    /**
     * Catmull-Rom spline interpolation between four values.
     * Interpolates between {@code p1} and {@code p2} using {@code p0} and {@code p3} as control points.
     */
    public static float catmullRom(float p0, float p1, float p2, float p3, float t) {
        float t2 = t * t;
        float t3 = t2 * t;
        return 0.5f * (
                (2f * p1)
                        + (-p0 + p2) * t
                        + (2f * p0 - 5f * p1 + 4f * p2 - p3) * t2
                        + (-p0 + 3f * p1 - 3f * p2 + p3) * t3
        );
    }

    /**
     * Catmull-Rom for float arrays (3-component vectors: position, rotation, scale).
     */
    public static float[] catmullRomVec(float[] p0, float[] p1, float[] p2, float[] p3, float t) {
        float[] result = new float[3];
        for (int i = 0; i < 3; i++) {
            result[i] = catmullRom(p0[i], p1[i], p2[i], p3[i], t);
        }
        return result;
    }

    // -- private helpers --

    private static float elasticIn(float t) {
        if (t <= 0f) return 0f;
        if (t >= 1f) return 1f;
        return (float) (-Math.pow(2f, 10f * t - 10f) * Math.sin((t * 10f - 10.75f) * (2f * Math.PI) / 3f));
    }

    private static float elasticOut(float t) {
        if (t <= 0f) return 0f;
        if (t >= 1f) return 1f;
        return (float) (Math.pow(2f, -10f * t) * Math.sin((t * 10f - 0.75f) * (2f * Math.PI) / 3f) + 1f);
    }

    private static float bounceOut(float t) {
        if (t < 1f / 2.75f) {
            return 7.5625f * t * t;
        } else if (t < 2f / 2.75f) {
            float t2 = t - 1.5f / 2.75f;
            return 7.5625f * t2 * t2 + 0.75f;
        } else if (t < 2.5f / 2.75f) {
            float t2 = t - 2.25f / 2.75f;
            return 7.5625f * t2 * t2 + 0.9375f;
        } else {
            float t2 = t - 2.625f / 2.75f;
            return 7.5625f * t2 * t2 + 0.984375f;
        }
    }

    private static float spring(float t) {
        // damped harmonic oscillator approximation
        float exponent = (float) Math.exp(-6f * t);
        return 1f - exponent * (float) Math.cos(12f * t * Math.PI);
    }
}
