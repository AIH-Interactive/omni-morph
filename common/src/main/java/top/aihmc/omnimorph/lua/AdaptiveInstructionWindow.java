package top.aihmc.omnimorph.lua;

/** Grows a VM hook threshold without restarting execution or resetting its counter. */
public final class AdaptiveInstructionWindow {
    public static final int INITIAL_LIMIT = 8192;
    private final int hardLimit;
    private int threshold;

    public AdaptiveInstructionWindow(int hardLimit) {
        this(INITIAL_LIMIT, hardLimit);
    }

    public AdaptiveInstructionWindow(int baseLimit, int hardLimit) {
        if (baseLimit < 1 || hardLimit < 1) {
            throw new IllegalArgumentException("Instruction limits must be positive");
        }
        this.hardLimit = hardLimit;
        threshold = Math.min(baseLimit, hardLimit);
    }

    public int threshold() {
        return threshold;
    }

    public boolean grow(int consumed) {
        if (consumed < threshold || consumed >= hardLimit) {
            return false;
        }
        threshold = (int) Math.min(hardLimit, Math.max((long) threshold * 2, (long) consumed + 1));
        return true;
    }

    /** Retains blocked and explicitly restricted trust levels. */
    public static int ceiling(int permission, boolean enabled, int configuredLimit) {
        if (!enabled || permission < INITIAL_LIMIT) {
            return permission;
        }
        return Math.max(1, configuredLimit);
    }
}
