package top.aihmc.omnimorph.utils.fabric;

import java.nio.file.Path;

/** Standalone JVM test environment only; never included in production artifacts. */
public final class PlatformUtilsImpl {
    public static int loadedModReads;

    public static java.util.Map<String, String> getLoadedMods() {
        loadedModReads++;
        return java.util.Map.of("fixture-z", "Alpha Mod", "fixture-a", "Zulu Mod");
    }

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
        return "figura-test-version".equals(modId);
    }

    public static String getModVersion(String modId) {
        if (!isModLoaded(modId)) {
            throw new IllegalArgumentException("Unknown test mod");
        }
        return "1.2.3-test+cpu";
    }
}
