package top.aihmc.omnimorph.debug;

import org.figuramc.figura.config.ConfigManager;
import org.figuramc.figura.config.ConfigType;

/** The normal integer input, restricted to TCP's usable port range. */
public final class PerformancePortConfig extends ConfigType.IntConfig {
    public PerformancePortConfig(String name, ConfigType.Category category, int defaultValue) {
        super(name, category, defaultValue);
    }

    @Override
    public Integer parseValue(String text) {
        int port = super.parseValue(text);
        if (port < 1 || port > 65535) {
            throw new IllegalArgumentException("Port must be between 1 and 65535");
        }
        return port;
    }

    @Override
    public void onChange() {
        if (!ConfigManager.isInitializing()) {
            PerformanceDebug.reconcile();
        }
    }
}
