package top.aihmc.omm.preview;

import org.figuramc.figura.math.vector.FiguraVec3;
import org.luaj.vm2.LuaError;

/** Standalone wheel presentation values; no live Minecraft registry or texture objects are fabricated. */
public final class PreviewWheelValues {
    private PreviewWheelValues() {}

    public static FiguraVec3 color(String method, Object x, Number y, Number z) {
        if (x == null) {
            return null;
        }
        if (x instanceof FiguraVec3 color) {
            return color.copy();
        }
        if (x instanceof Number number) {
            return FiguraVec3.of(
                number.doubleValue(), y == null ? 0 : y.doubleValue(), z == null ? 0 : z.doubleValue());
        }
        throw new LuaError("Illegal argument to " + method + "(): " + x.getClass().getSimpleName());
    }

    public static String item(String method, Object item) {
        if (item == null || item instanceof String) {
            return (String) item;
        }
        throw new LuaError(method + " requires an item identifier string in the standalone preview");
    }
}
