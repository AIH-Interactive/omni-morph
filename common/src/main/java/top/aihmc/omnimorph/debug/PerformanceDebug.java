package top.aihmc.omnimorph.debug;

import net.minecraft.client.Minecraft;
import org.figuramc.figura.FiguraMod;
import org.figuramc.figura.config.Configs;

/** Tick only reconciles the listener lifecycle; snapshots are collected exclusively on requests. */
public final class PerformanceDebug {
    private static final PerformanceHttpServer SERVER = new PerformanceHttpServer(
            task -> Minecraft.getInstance().execute(task), PerformanceSnapshot::capture);
    private static String previousError;

    private PerformanceDebug() {
    }

    public static void reconcile() {
        SERVER.configure(FiguraMod.debugModeEnabled(), Configs.PERFORMANCE_HTTP_PORT.value);
        String error = SERVER.error();
        if (error != null && !error.equals(previousError)) {
            FiguraMod.LOGGER.warn("{}: performance HTTP port {} is unavailable", error,
                    Configs.PERFORMANCE_HTTP_PORT.value);
        }
        previousError = error;
    }

    public static void close() {
        SERVER.close();
    }
}
