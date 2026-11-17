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
        if (baseLimit < 1 || hardLimit == 0 || hardLimit < -1) {
            throw new IllegalArgumentException("Base must be positive; ceiling must be positive or -1");
        }
        this.hardLimit = hardLimit;
        threshold = hardLimit == -1 ? baseLimit : Math.min(baseLimit, hardLimit);
    }

    public int threshold() {
        return threshold;
    }

    public boolean grow(int consumed) {
        if (consumed < threshold || hardLimit != -1 && consumed >= hardLimit) {
            return false;
        }
        int maximum = hardLimit == -1 ? Integer.MAX_VALUE : hardLimit;
        threshold = (int) Math.min(maximum, Math.max((long) threshold * 2, (long) consumed + 1));
        return true;
    }

    public boolean unlimited() {
        return hardLimit == -1;
    }

    /** Retains blocked and explicitly restricted trust levels. */
    public static int ceiling(int permission, boolean enabled, int configuredLimit) {
        if (!enabled || permission < INITIAL_LIMIT) {
            return permission;
        }
        return configuredLimit == -1 ? -1 : Math.max(1, configuredLimit);
    }
}
