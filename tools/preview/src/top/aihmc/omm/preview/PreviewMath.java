package top.aihmc.omm.preview;

/** Minecraft-independent arithmetic helpers required by the reused Molang math functions. */
public final class PreviewMath {
    private PreviewMath() {}

    public static int floor(double value) {
        return (int) Math.floor(value);
    }

    public static int ceil(float value) {
        return (int) Math.ceil(value);
    }

    public static float wrapDegrees(float value) {
        float result = value % 360;
        if (result >= 180) {
            result -= 360;
        }
        if (result < -180) {
            result += 360;
        }
        return result;
    }
}
