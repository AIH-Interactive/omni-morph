package top.aihmc.omnimorph.molang.runtime.binding;

import top.aihmc.omnimorph.molang.runtime.Function;
import top.aihmc.omnimorph.molang.runtime.ExecutionContext;
import top.aihmc.omnimorph.molang.runtime.MolangExecutionBudget;
import org.figuramc.figura.avatar.Avatar;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;

/**
 * Molang math.* function implementations.
 * YSM sin/cos accept degrees; its inverse trigonometric functions return radians.
 *
 * Reference: YSM MathBinding
 */
public final class MathFunctions {

    private static final RandomSource FALLBACK_RANDOM = RandomSource.create();

    private MathFunctions() {
    }

    public static final Function SIN = (ctx, args) ->
            (float) Math.sin(Math.toRadians(args.getAsDouble(ctx, 0)));

    public static final Function COS = (ctx, args) ->
            (float) Math.cos(Math.toRadians(args.getAsDouble(ctx, 0)));

    public static final Function ASIN = rawFloatResult((ctx, args) ->
            (float) Math.asin(args.getAsDouble(ctx, 0)));

    public static final Function ACOS = rawFloatResult((ctx, args) ->
            (float) Math.acos(args.getAsDouble(ctx, 0)));

    public static final Function ATAN = (ctx, args) ->
            (float) Math.atan(args.getAsDouble(ctx, 0));

    public static final Function ATAN2 = (ctx, args) -> {
        double y = args.getAsDouble(ctx, 0);
        double x = args.getAsDouble(ctx, 1);
        return (float) Math.atan2(y, x);
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
        requireArguments(args, 1, 1);
        double t = Mth.ceil(args.getAsFloat(ctx, 0));
        return (float) Mth.floor(3d * Math.pow(t, 2d) - 2d * Math.pow(t, 3d));
    };

    public static final Function LERP = (ctx, args) -> {
        float a = args.getAsFloat(ctx, 0);
        float b = args.getAsFloat(ctx, 1);
        float t = args.getAsFloat(ctx, 2);
        return a + t * (b - a);
    };

    public static final Function LERP_ROTATE = (ctx, args) -> {
        float a = Mth.wrapDegrees(args.getAsFloat(ctx, 0));
        float b = Mth.wrapDegrees(args.getAsFloat(ctx, 1));
        float t = args.getAsFloat(ctx, 2);
        float difference = a - b;
        float target = difference > 180f || difference < -180f
                ? a + Math.copySign(360f - Math.abs(difference), difference) : b;
        return a + (target - a) * t;
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
        requireArguments(args, 1, 1);
        return Mth.wrapDegrees(args.getAsFloat(ctx, 0));
    };

    public static final Function MOD = (ctx, args) -> {
        float a = args.getAsFloat(ctx, 0);
        float b = args.getAsFloat(ctx, 1);
        return a % b;
    };

    public static final Function POW = (ctx, args) -> {
        double result = Math.pow(args.getAsDouble(ctx, 0), args.getAsDouble(ctx, 1));
        return Double.isFinite(result) ? (float) result : 0f;
    };

    public static final Function RANDOM_FUNC = (ctx, args) -> {
        requireArguments(args, 2, 3);
        float first = args.getAsFloat(ctx, 0);
        float second = args.getAsFloat(ctx, 1);
        return Math.min(first, second) + random(ctx).nextFloat() * Math.abs(second - first);
    };

    public static final Function RANDOM_INTEGER = (ctx, args) -> {
        requireArguments(args, 2, 2);
        int first = args.getAsInt(ctx, 0);
        int second = args.getAsInt(ctx, 1);
        return (float) (Math.min(first, second) + random(ctx).nextInt(Math.max(first, second) - Math.min(first, second)));
    };

    public static final Function ROUND = (ctx, args) ->
            (float) Math.round(args.getAsDouble(ctx, 0));

    public static final Function SQRT = (ctx, args) ->
            (float) Math.sqrt(args.getAsDouble(ctx, 0));

    public static final Function TRUNC = (ctx, args) ->
            (float) (long) args.getAsDouble(ctx, 0);

    public static final Function DIE_ROLL = (ctx, args) -> {
        requireArguments(args, 3, 3);
        int count = args.getAsInt(ctx, 0);
        float first = args.getAsFloat(ctx, 1);
        float second = args.getAsFloat(ctx, 2);
        float total = 0f;
        RandomSource random = random(ctx);
        for (int roll = 0; roll < count; roll++) {
            consumeRoll(ctx);
            total += Math.min(first, second) + random.nextFloat() * Math.abs(second - first);
        }
        return total;
    };

    public static final Function DIE_ROLL_INTEGER = (ctx, args) -> {
        requireArguments(args, 3, 3);
        int count = Math.round(args.getAsFloat(ctx, 0));
        int first = args.getAsInt(ctx, 1);
        int second = args.getAsInt(ctx, 2);
        int total = 0;
        RandomSource random = random(ctx);
        for (int roll = 0; roll < count; roll++) {
            consumeRoll(ctx);
            total += Math.min(first, second) + random.nextInt(Math.max(first, second) - Math.min(first, second));
        }
        return (float) total;
    };

    private static RandomSource random(ExecutionContext<?> execution) {
        return execution.entity() instanceof Avatar.MolangContext context ? context.random : FALLBACK_RANDOM;
    }

    private static Function rawFloatResult(Function implementation) {
        return new Function() {
            @Override
            public Object evaluate(ExecutionContext<?> context, ArgumentCollection arguments) {
                return implementation.evaluate(context, arguments);
            }

            @Override
            public float evaluateFloat(ExecutionContext<?> context, ArgumentCollection arguments) {
                return ((Number) implementation.evaluate(context, arguments)).floatValue();
            }
        };
    }

    private static void consumeRoll(ExecutionContext<?> execution) {
        if (execution.entity() instanceof MolangExecutionBudget.Owner owner) {
            owner.executionBudget().consume();
        }
    }

    private static void requireArguments(Function.ArgumentCollection arguments, int minimum, int maximum) {
        if (arguments.size() < minimum || arguments.size() > maximum) {
            throw new IllegalArgumentException("Invalid math function argument count");
        }
    }
}
