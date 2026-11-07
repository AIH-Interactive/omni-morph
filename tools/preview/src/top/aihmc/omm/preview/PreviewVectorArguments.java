package top.aihmc.omm.preview;
import org.figuramc.figura.math.vector.*;
import org.luaj.vm2.LuaError;
/** Original scalar/vector argument conversion, without live game utilities. */
public final class PreviewVectorArguments {
    private PreviewVectorArguments() {}
    public record Pair<A, B>(A first, B second) {
        public static <A, B> Pair<A, B> of(A first, B second) {
            return new Pair<>(first, second);
        }
        public A getFirst() {
            return first;
        }
        public B getSecond() {
            return second;
        }
    }
    public static FiguraVec2 parseVec2(String methodName, Object x, Number y) {
        return parseVec2(methodName, x, y, 0, 0);
    }

    public static FiguraVec2 parseVec2(String methodName, Object x, Number y, double defaultX, double defaultY) {
        if (x instanceof FiguraVec2 vec)
            return vec.copy();
        if (x == null || x instanceof Number) {
            if (x == null)
                x = defaultX;
            if (y == null)
                y = defaultY;
            return FiguraVec2.of(((Number) x).doubleValue(), y.doubleValue());
        }
        throw new LuaError("Illegal argument to " + methodName + "(): " + x.getClass().getSimpleName());
    }

    /**
     * This code gets repeated SO MUCH that I decided to put it in the utils class.
     * @param x Either the x coordinate of a vector, or a vector itself.
     * @param y The y coordinate of a vector, used if the first parameter was a number.
     * @param z The z coordinate of a vector, used if the first parameter was a number.
     * @return A FiguraVec3 representing the data passed in.
     */
    public static FiguraVec3 parseVec3(String methodName, Object x, Number y, Number z) {
        return parseVec3(methodName, x, y, z, 0, 0, 0);
    }

    public static FiguraVec3 parseVec3(
        String methodName, Object x, Number y, Number z, double defaultX, double defaultY, double defaultZ) {
        if (x instanceof FiguraVec3 vec)
            return vec.copy();
        if (x == null || x instanceof Number) {
            if (x == null)
                x = defaultX;
            if (y == null)
                y = defaultY;
            if (z == null)
                z = defaultZ;
            return FiguraVec3.of(((Number) x).doubleValue(), y.doubleValue(), z.doubleValue());
        }
        throw new LuaError("Illegal argument to " + methodName + "(): " + x.getClass().getSimpleName());
    }

    public static FiguraVec3 parseOneArgVec(String methodName, Object x, Number y, Number z, double defaultArg) {
        double d = x instanceof Number n ? n.doubleValue() : defaultArg;
        return parseVec3(methodName, x, y, z, d, d, d);
    }

    public static FiguraVec3 nullableVec3(String methodName, Object x, Number y, Number z) {
        return x == null ? null : parseVec3(methodName, x, y, z);
    }

    public static Pair<FiguraVec3, FiguraVec3> parse2Vec3(
        String methodName, Object x, Object y, Number z, Object w, Number t, Number h, int xIndex) {
        FiguraVec3 a, b;

        if (x instanceof FiguraVec3 vec1) {
            a = vec1.copy();
            if (y instanceof FiguraVec3 vec2) {
                b = vec2.copy();
            } else if (y == null || y instanceof Number) {
                if (w == null || w instanceof Number) {
                    b = parseVec3(methodName, y, z, (Number) w);
                } else {
                    throw new LuaError("Illegal argument at position" + xIndex + 3 + "to " + methodName + "(): " + w);
                }
            } else {
                throw new LuaError("Illegal argument at position " + xIndex + 1 + " to " + methodName + "(): " + y);
            }
        } else if (x instanceof Number && y == null || y instanceof Number) {
            a = parseVec3(methodName, x, (Number) y, z);
            if (w instanceof FiguraVec3 vec1) {
                b = vec1.copy();
            } else if (w == null || w instanceof Number) {
                b = parseVec3(methodName, w, t, h);
            } else {
                throw new LuaError("Illegal argument at position " + xIndex + 3 + " to " + methodName + "(): " + w);
            }
        } else {
            throw new LuaError("Illegal argument at position " + xIndex + " to " + methodName + "(): " + x);
        }

        return Pair.of(a, b);
    }

    // These functions allow having vector parsing at the beggining of the function, taking into account other
    // arguments.
    public static FiguraVec4 parseVec4(String methodName, Object x, Number y, Number z, Number w, double defaultX,
        double defaultY, double defaultZ, double defaultW) {
        if (x instanceof FiguraVec3 vec)
            return FiguraVec4.of(vec.x, vec.y, vec.z, defaultW);
        if (x instanceof FiguraVec4 vec)
            return vec.copy();
        if (x == null || x instanceof Number) {
            if (x == null)
                x = defaultX;
            if (y == null)
                y = defaultY;
            if (z == null)
                z = defaultZ;
            if (w == null)
                w = defaultW;
            return FiguraVec4.of(((Number) x).doubleValue(), y.doubleValue(), z.doubleValue(), w.doubleValue());
        }
        throw new LuaError("Illegal argument to " + methodName + "(): " + x.getClass().getSimpleName());
    }
}
