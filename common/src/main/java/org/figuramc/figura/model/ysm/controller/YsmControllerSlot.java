package org.figuramc.figura.model.ysm.controller;

import java.util.Locale;

public enum YsmControllerSlot {
    MAIN,
    PARALLEL,
    POST_SWING,
    FIRST_PERSON_ARM,
    VEHICLE,
    PROJECTILE,
    UNKNOWN;

    public static YsmControllerSlot fromName(String name) {
        String normalized = name == null ? "" : name.toLowerCase(Locale.US);
        if (normalized.contains(".parallel"))
            return PARALLEL;
        if (normalized.contains("post_swing") || normalized.contains(".swing"))
            return POST_SWING;
        if (normalized.contains("fp_arm") || normalized.contains("first_person"))
            return FIRST_PERSON_ARM;
        if (normalized.contains("vehicle"))
            return VEHICLE;
        if (normalized.contains("projectile"))
            return PROJECTILE;
        if (normalized.contains(".main") || normalized.endsWith(".pre_main") || normalized.endsWith(".main"))
            return MAIN;
        return UNKNOWN;
    }
}
