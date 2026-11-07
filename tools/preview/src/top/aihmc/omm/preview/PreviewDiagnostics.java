package top.aihmc.omm.preview;

/** Routes reused parser diagnostics to the standalone application's log. */
public final class PreviewDiagnostics {
    private static final System.Logger LOG = System.getLogger("omm.preview.parser");

    private PreviewDiagnostics() {}

    public static void debug(String message, Object... arguments) {
        if (LOG.isLoggable(System.Logger.Level.DEBUG)) {
            LOG.log(System.Logger.Level.DEBUG, format(message, arguments));
        }
    }

    public static void warning(String message, Object... arguments) {
        LOG.log(System.Logger.Level.WARNING, format(message, arguments));
    }

    public static void error(Exception error) {
        LOG.log(System.Logger.Level.WARNING, "External texture unavailable; parser will try bundled data", error);
    }

    private static String format(String message, Object[] arguments) {
        StringBuilder result = new StringBuilder();
        int start = 0;
        for (Object argument : arguments) {
            int marker = message.indexOf("{}", start);
            if (marker < 0) {
                break;
            }
            result.append(message, start, marker).append(argument);
            start = marker + 2;
        }
        return result.append(message.substring(start)).toString();
    }
}
