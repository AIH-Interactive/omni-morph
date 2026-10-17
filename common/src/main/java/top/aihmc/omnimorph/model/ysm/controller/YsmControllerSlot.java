package top.aihmc.omnimorph.model.ysm.controller;

import java.util.Locale;

public enum YsmControllerSlot {
    MAIN,
    PARALLEL,
    POST_MAIN,
    POST_SWING,
    FIRST_PERSON_ARM,
    VEHICLE,
    PROJECTILE,
    UNKNOWN;

    public static YsmControllerSlot fromName(String name) {
        String normalized = name == null ? "" : name.toLowerCase(Locale.US).replace('-', '_');
        if (normalized.contains("fp.arm.") || normalized.contains("fp_arm") || normalized.contains("first_person"))
            return FIRST_PERSON_ARM;
        if (normalized.contains("pre_parallel") || normalized.contains("parallel"))
            return PARALLEL;
        if (normalized.contains("vehicle"))
            return VEHICLE;
        if (normalized.contains("projectile"))
            return PROJECTILE;
        if (normalized.contains("post_main"))
            return POST_MAIN;
        if (normalized.contains("post_swing") || normalized.contains(".swing"))
            return POST_SWING;
        if (normalized.contains(".main") || normalized.endsWith(".pre_main") || normalized.endsWith(".main"))
            return MAIN;
        return UNKNOWN;
    }

    /** Body stages follow the reference's registration order; arm stages use their own later phase. */
    public static int executionOrder(String name) {
        String normalized = name == null ? "" : name.toLowerCase(Locale.US).replace('-', '_');
        YsmControllerSlot slot = fromName(name);
        if (slot == FIRST_PERSON_ARM) {
            return normalized.contains("parallel") ? 61 : 60;
        }
        if (normalized.contains("pre_parallel")) {
            return 0;
        }
        if (normalized.contains("pre_main")) {
            return 10;
        }
        return switch (slot) {
            case MAIN -> 20;
            case POST_MAIN -> 30;
            case POST_SWING -> 40;
            case PARALLEL -> 50;
            case VEHICLE -> 5;
            case PROJECTILE -> 70;
            default -> 80;
        };
    }
}
