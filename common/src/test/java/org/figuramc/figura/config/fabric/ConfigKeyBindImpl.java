package org.figuramc.figura.config.fabric;

import org.figuramc.figura.config.ConfigKeyBind;

/** Parser tests construct default config values but have no live input system. */
public final class ConfigKeyBindImpl {
    private ConfigKeyBindImpl() {
    }

    public static void addKeyBind(ConfigKeyBind key) {
        // The parser tests do not exercise keyboard input or game registration.
    }
}
