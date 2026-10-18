package top.aihmc.omnimorph.utils;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** Display-only binary size conversion; upload limits remain exact byte counts. */
public final class FileSizeFormatter {
    private static final String[] UNITS = {"", "KB", "MB", "GB"};

    private FileSizeFormatter() {
    }

    public static String format(long bytes, String byteUnit) {
        if (bytes < 0) {
            throw new IllegalArgumentException("Negative file size");
        }
        long divisor = 1;
        int unit = 0;
        while (unit < UNITS.length - 1 && bytes / divisor >= 1024) {
            divisor *= 1024;
            unit++;
        }
        String amount = BigDecimal.valueOf(bytes).divide(BigDecimal.valueOf(divisor), 2, RoundingMode.HALF_UP)
                .stripTrailingZeros().toPlainString();
        return amount + " " + (unit == 0 ? byteUnit : UNITS[unit]);
    }
}
