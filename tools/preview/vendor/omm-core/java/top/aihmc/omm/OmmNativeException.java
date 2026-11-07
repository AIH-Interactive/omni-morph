package top.aihmc.omm;

/** Failure reported by omm-core; {@link #code()} is the C {@code omm_status} value. */
public final class OmmNativeException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    public static final int ARGUMENT = 1;
    public static final int ABI = 2;
    public static final int HANDLE = 3;
    public static final int UNSUPPORTED = 4;
    public static final int FORMAT = 5;
    public static final int LIMIT = 6;
    public static final int STATE = 12;

    private final int code;
    private final int stage;

    public OmmNativeException(int code, int stage, String message) {
        super("omm status " + code + " (stage " + stage + "): " + message);
        this.code = code;
        this.stage = stage;
    }

    public int code() {
        return code;
    }

    public int stage() {
        return stage;
    }
}
