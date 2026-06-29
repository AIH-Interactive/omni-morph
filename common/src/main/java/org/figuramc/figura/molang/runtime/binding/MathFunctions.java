package org.figuramc.figura.molang.runtime.binding;

import org.figuramc.figura.molang.runtime.Function;

import java.util.Random;

/**
 * Molang math.* function implementations.
 * All functions use float arithmetic as per Molang spec.
 *
 * Reference: YSM MathBinding
 */
public final class MathFunctions {

    private static final Random RANDOM = new Random();

    private MathFunctions() {
    }

    public static final Function SIN = (ctx, args) ->
            (float) Math.sin(Math.toRadians(args.getAsDouble(ctx, 0)));

    public static final Function COS = (ctx, args) ->
            (float) Math.cos(Math.toRadians(args.getAsDouble(ctx, 0)));

    public static final Function ASIN = (ctx, args) ->
            (float) Math.toDegrees(Math.asin(clamp(args.getAsDouble(ctx, 0), -1.0, 1.0)));

    public static final Function ACOS = (ctx, args) ->
            (float) Math.toDegrees(Math.acos(clamp(args.getAsDouble(ctx, 0), -1.0, 1.0)));

    public static final Function ATAN = (ctx, args) ->
            (float) Math.toDegrees(Math.atan(args.getAsDouble(ctx, 0)));

    public static final Function ATAN2 = (ctx, args) -> {
        double y = args.getAsDouble(ctx, 0);
        double x = args.getAsDouble(ctx, 1);
        return (float) Math.toDegrees(Math.atan2(y, x));
    };

    public static final Function ABS = (ctx, args) ->
            Math.abs(args.getAsFloat(ctx, 0));

    public static final Function CEIL = (ctx, args) ->
            (float) Math.ceil(args.getAsDouble(ctx, 0));

    public static final Function CLAMP = (ctx, args) -> {
        float value = args.getAsFloat(ctx, 0);
        float min = args.getAsFloat(ctx, 1);
        float max = args.getAsFloat(ctx, 2);
        return Math.max(min, Math.min(max, value));
    };

    public static final Function EXP = (ctx, args) ->
            (float) Math.exp(args.getAsDouble(ctx, 0));

    public static final Function FLOOR = (ctx, args) ->
            (float) Math.floor(args.getAsDouble(ctx, 0));

    public static final Function HERMIT_BLEND = (ctx, args) -> {
        float t = args.getAsFloat(ctx, 0);
        return t * t * (3 - 2 * t);
    };

    public static final Function LERP = (ctx, args) -> {
        float a = args.getAsFloat(ctx, 0);
        float b = args.getAsFloat(ctx, 1);
        float t = args.getAsFloat(ctx, 2);
        return a + t * (b - a);
    };

    public static final Function LERP_ROTATE = (ctx, args) -> {
        float a = args.getAsFloat(ctx, 0);
        float b = args.getAsFloat(ctx, 1);
        float t = args.getAsFloat(ctx, 2);
        float diff = ((b - a) % 360 + 540) % 360 - 180;
        return a + diff * t;
    };

    public static final Function LN = (ctx, args) ->
            (float) Math.log(args.getAsDouble(ctx, 0));

    public static final Function MAX = (ctx, args) -> {
        float max = Float.NEGATIVE_INFINITY;
        for (int i = 0; i < args.size(); i++) {
            max = Math.max(max, args.getAsFloat(ctx, i));
        }
        return max;
    };

    public static final Function MIN = (ctx, args) -> {
        float min = Float.POSITIVE_INFINITY;
        for (int i = 0; i < args.size(); i++) {
            min = Math.min(min, args.getAsFloat(ctx, i));
        }
        return min;
    };

    public static final Function MIN_ANGLE = (ctx, args) -> {
        float a = args.getAsFloat(ctx, 0);
        float b = args.getAsFloat(ctx, 1);
        float diff = ((a - b) % 360 + 540) % 360 - 180;
        return a - diff;
    };

    public static final Function MOD = (ctx, args) -> {
        float a = args.getAsFloat(ctx, 0);
        float b = args.getAsFloat(ctx, 1);
        return a % b;
    };

    public static final Function POW = (ctx, args) ->
            (float) Math.pow(args.getAsDouble(ctx, 0), args.getAsDouble(ctx, 1));

    public static final Function RANDOM_FUNC = (ctx, args) -> {
        if (args.size() >= 2) {
            float lo = args.getAsFloat(ctx, 0);
            float hi = args.getAsFloat(ctx, 1);
            return lo + RANDOM.nextFloat() * (hi - lo);
        }
        return RANDOM.nextFloat();
    };

    public static final Function RANDOM_INTEGER = (ctx, args) -> {
        int lo = args.getAsInt(ctx, 0);
        int hi = args.getAsInt(ctx, 1);
        return (float) (lo + RANDOM.nextInt(hi - lo + 1));
    };

    public static final Function ROUND = (ctx, args) ->
            (float) Math.round(args.getAsDouble(ctx, 0));

    public static final Function SQRT = (ctx, args) ->
            (float) Math.sqrt(args.getAsDouble(ctx, 0));

    public static final Function TRUNC = (ctx, args) ->
            (float) (long) args.getAsDouble(ctx, 0);

    public static final Function DIE_ROLL = (ctx, args) -> {
        int sides = args.getAsInt(ctx, 0);
        return (float) (RANDOM.nextInt(sides) + 1);
    };

    public static final Function DIE_ROLL_INTEGER = (ctx, args) -> {
        int sides = args.getAsInt(ctx, 0);
        return (float) (RANDOM.nextInt(sides) + 1);
    };

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }
}
