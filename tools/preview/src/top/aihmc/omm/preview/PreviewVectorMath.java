package top.aihmc.omm.preview;
import java.awt.Color;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;
import org.figuramc.figura.math.vector.*;
import org.joml.Quaternionf;
/** Original pure vector/color helpers extracted from Figura's game-dependent utilities. */
public final class PreviewVectorMath {
    private static final java.util.Map<String, Integer> NAMED_COLORS =
        java.util.Map.ofEntries(java.util.Map.entry("black", 0x000000), java.util.Map.entry("dark_blue", 0x0000aa),
            java.util.Map.entry("dark_green", 0x00aa00), java.util.Map.entry("dark_aqua", 0x00aaaa),
            java.util.Map.entry("dark_red", 0xaa0000), java.util.Map.entry("dark_purple", 0xaa00aa),
            java.util.Map.entry("gold", 0xffaa00), java.util.Map.entry("gray", 0xaaaaaa),
            java.util.Map.entry("dark_gray", 0x555555), java.util.Map.entry("blue", 0x5555ff),
            java.util.Map.entry("green", 0x55ff55), java.util.Map.entry("aqua", 0x55ffff),
            java.util.Map.entry("red", 0xff5555), java.util.Map.entry("light_purple", 0xff55ff),
            java.util.Map.entry("yellow", 0xffff55), java.util.Map.entry("white", 0xffffff),
            java.util.Map.entry("awesome_blue", 0x5ea5ff), java.util.Map.entry("purple", 0xa672ef),
            java.util.Map.entry("figura_blue", 0x00f0ff), java.util.Map.entry("soft_blue", 0x99bbee),
            java.util.Map.entry("figura_red", 0xff2400), java.util.Map.entry("orange", 0xffc400),
            java.util.Map.entry("cheese", 0xf8c53a), java.util.Map.entry("lua_log", 0x5555ff),
            java.util.Map.entry("lua_error", 0xff5555), java.util.Map.entry("lua_ping", 0xa155da),
            java.util.Map.entry("default", 0x5aaaff), java.util.Map.entry("discord", 0x5865f2),
            java.util.Map.entry("kofi", 0x27aae0), java.util.Map.entry("github", 0xffffff),
            java.util.Map.entry("modrinth", 0x1bd96a), java.util.Map.entry("curseforge", 0xf16436));

    public static FiguraVec3 userInputHex(String name, FiguraVec3 fallback) {
        Integer color = name == null ? null : NAMED_COLORS.get(name.toLowerCase(Locale.ROOT));
        return color == null ? hexStringToRGB(name, fallback) : intToRGB(color);
    }
    private PreviewVectorMath() {}
    public static final double DEG_TO_RAD = Math.PI / 180;
    public static final double RAD_TO_DEG = 180 / Math.PI;
    public static final ThreadLocal<DecimalFormat> FORMAT =
        ThreadLocal.withInitial(() -> new DecimalFormat("0.###", DecimalFormatSymbols.getInstance(Locale.ROOT)));
    public static FiguraVector<?, ?> sizedVector(double... vals) {
        return switch (vals.length) {
            case 2 -> FiguraVec2.of(vals[0], vals[1]);
            case 3 -> FiguraVec3.of(vals[0], vals[1], vals[2]);
            case 4 -> FiguraVec4.of(vals[0], vals[1], vals[2], vals[3]);
            default -> throw new IllegalStateException("Cannot create vector of size: " + vals.length);
        };
    }
    public static FiguraVec3 rotateAroundAxis(FiguraVec3 vec, FiguraVec3 axis, double degrees) {
        FiguraVec3 normalizedAxis = axis.normalized();
        Quaternionf vectorQuat = new Quaternionf((float) vec.x, (float) vec.y, (float) vec.z, 0);
        Quaternionf rotatorQuat = new Quaternionf().fromAxisAngleDeg(
            (float) normalizedAxis.x, (float) normalizedAxis.y, (float) normalizedAxis.z, (float) degrees);
        Quaternionf rotatorQuatConj = new Quaternionf(rotatorQuat);
        rotatorQuatConj.conjugate();

        rotatorQuat.mul(vectorQuat);
        rotatorQuat.mul(rotatorQuatConj);

        return FiguraVec3.of(rotatorQuat.x(), rotatorQuat.y(), rotatorQuat.z());
    }
    public static int[] split(int color, int len) {
        int[] array = new int[len];
        for (int i = 0; i < len; i++) {
            int shift = (len * 8) - ((i + 1) * 8);
            array[i] = color >> shift & 0xFF;
        }

        return array;
    }
    public static int rgbToInt(FiguraVec3 rgb) {
        int hex = (int) (rgb.x * 0xFF);
        hex = (hex << 8) + (int) (rgb.y * 0xFF);
        hex = (hex << 8) + (int) (rgb.z * 0xFF);
        return hex;
    }
    public static FiguraVec3 intToRGB(int color) {
        int[] rgb = PreviewVectorMath.split(color, 3);
        return FiguraVec3.of(rgb[0] / 255f, rgb[1] / 255f, rgb[2] / 255f);
    }
    public static FiguraVec3 hexStringToRGB(String string, FiguraVec3 fallback) {
        if (string == null || string.isBlank())
            return fallback;

        // parse hex color
        StringBuilder hex = new StringBuilder(string);

        if (hex.toString().startsWith("#"))
            hex = new StringBuilder(hex.substring(1));

        // short hex
        if (hex.length() == 3) {
            char[] bgChar = hex.toString().toCharArray();
            hex = new StringBuilder("" + bgChar[0] + bgChar[0] + bgChar[1] + bgChar[1] + bgChar[2] + bgChar[2]);
        } else {
            hex.append("0".repeat(Math.max(6 - hex.length(), 0)));
        }

        // return
        try {
            return intToRGB(Integer.parseInt(hex.substring(0, 6), 16));
        } catch (Exception ignored) {
            return fallback;
        }
    }
    public static FiguraVec3 hsvToRGB(FiguraVec3 hsv) {
        int hex = Color.HSBtoRGB((float) hsv.x, (float) hsv.y, (float) hsv.z);
        return intToRGB(hex);
    }
    public static FiguraVec3 rgbToHSV(FiguraVec3 rgb) {
        float[] hsv = Color.RGBtoHSB((int) (rgb.x * 255f), (int) (rgb.y * 255f), (int) (rgb.z * 255f), null);
        return FiguraVec3.of(hsv[0], hsv[1], hsv[2]);
    }
    public static String rgbToHex(FiguraVec3 rgb) {
        String color = Integer.toHexString(PreviewVectorMath.rgbToInt(rgb));
        return "0".repeat(Math.max(6 - color.length(), 0)) + color;
    }
}
