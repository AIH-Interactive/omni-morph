package org.figuramc.figura.utils.fabric;

import java.nio.file.Path;

/** Standalone JVM test environment only; never included in production artifacts. */
public final class PlatformUtilsImpl {
    private PlatformUtilsImpl() {
    }

    public static Path getConfigDir() {
        return Path.of(System.getProperty("figura.test.directory"));
    }

    public static Path getGameDir() {
        return getConfigDir();
    }

    public static String getFiguraModVersionString() {
        return "0.1.0";
    }

    public static boolean isModLoaded(String modId) {
        return false;
    }
}
