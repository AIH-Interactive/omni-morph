package org.figuramc.figura.molang.runtime.binding;

import org.figuramc.figura.molang.parser.ast.StringExpression;
import org.figuramc.figura.molang.storage.StringPool;

/**
 * Utility class for type conversions between Molang value types.
 * Molang uses float as its primary numeric type, with NaN→0 semantics.
 *
 * Reference: YSM ValueConversions
 */
public final class ValueConversions {

    private ValueConversions() {
    }

    public static boolean asBoolean(Object obj) {
        if (obj == null) {
            return false;
        }
        if (obj instanceof Boolean) {
            return (Boolean) obj;
        }
        if (obj instanceof Number) {
            float f = ((Number) obj).floatValue();
            return !Float.isNaN(f) && f != 0.0f;
        }
        return true;
    }

    public static float asFloat(Object obj) {
        if (obj == null) {
            return 0;
        }
        if (obj instanceof Number) {
            float f = ((Number) obj).floatValue();
            if (!Float.isNaN(f)) {
                return f;
            }
            return 0.0f;
        }
        if (obj instanceof Boolean) {
            return (Boolean) obj ? 1 : 0;
        }
        return 1;
    }

    public static int asInt(Object obj) {
        if (obj == null) {
            return 0;
        }
        if (obj instanceof Number) {
            return ((Number) obj).intValue();
        }
        if (obj instanceof Boolean) {
            return (Boolean) obj ? 1 : 0;
        }
        return 1;
    }

    public static double asDouble(Object obj) {
        if (obj == null) {
            return 0;
        }
        if (obj instanceof Number) {
            double d = ((Number) obj).doubleValue();
            if (!Double.isNaN(d)) {
                return d;
            }
            return 0.0;
        }
        if (obj instanceof Boolean) {
            return (Boolean) obj ? 1 : 0;
        }
        return 1;
    }

    public static String asString(Object obj) {
        if (obj instanceof StringExpression) {
            return ((StringExpression) obj).getName();
        }
        if (obj instanceof String) {
            return (String) obj;
        }
        return null;
    }

    public static int asStringId(Object obj) {
        if (obj instanceof StringExpression) {
            return ((StringExpression) obj).getPath();
        }
        if (obj instanceof String) {
            return StringPool.computeIfAbsent((String) obj);
        }
        return StringPool.EMPTY_ID;
    }
}
