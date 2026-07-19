package org.figuramc.figura.model.ysm.controller;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public final class YsmControllerSlotBinder {
    private YsmControllerSlotBinder() {
    }

    public static Set<String> eventNamesForController(String controllerName, YsmControllerSlot slot) {
        LinkedHashSet<String> result = new LinkedHashSet<>();
        String normalized = normalizeName(controllerName).replace('-', '_');
        String simple = normalized;
        int controllerPrefix = simple.indexOf("controller.");
        if (controllerPrefix >= 0)
            simple = simple.substring(controllerPrefix + "controller.".length());
        int animationPrefix = simple.indexOf("animation.");
        if (animationPrefix >= 0)
            simple = simple.substring(animationPrefix + "animation.".length());

        if (simple.contains("player.") || simple.startsWith("player_")) {
            if (simple.contains("pre_main"))
                result.add("player_ctrl_pre_main");
            if (simple.contains("pre_parallel")) {
                result.add("player_ctrl_pre_parallel" + numericSuffix(simple));
                result.add("player_ctrl_pre_parallel");
            } else if (simple.contains("parallel")) {
                result.add("player_ctrl_parallel" + numericSuffix(simple));
                result.add("player_ctrl_parallel");
            }
            if (simple.contains("post_main")) {
                result.add("player_ctrl_post_main" + numericSuffix(simple));
                result.add("player_ctrl_post_main");
            }
            if (simple.contains("post_swing") || simple.contains(".swing") || simple.contains("_swing"))
                result.add("player_ctrl_post_swing");
            if (simple.contains("fp_arm") || simple.contains("first_person"))
                result.add("player_ctrl_fp_arm");
            if (simple.endsWith(".main") || simple.endsWith("_main") || simple.contains("player.main"))
                result.add("player_ctrl_main");
        }

        switch (slot) {
            case MAIN -> {
                result.add("player_ctrl_pre_main");
                result.add("player_ctrl_main");
            }
            case PARALLEL -> {
                if (simple.contains("pre_parallel")) {
                    result.add("player_ctrl_pre_parallel" + numericSuffix(simple));
                    result.add("player_ctrl_pre_parallel");
                } else {
                    result.add("player_ctrl_parallel" + numericSuffix(simple));
                    result.add("player_ctrl_parallel");
                }
            }
            case POST_MAIN -> {
                result.add("player_ctrl_post_main" + numericSuffix(simple));
                result.add("player_ctrl_post_main");
            }
            case POST_SWING -> result.add("player_ctrl_post_swing");
            case FIRST_PERSON_ARM -> result.add("player_ctrl_fp_arm");
            case VEHICLE -> result.add("vehicle_ctrl_main");
            case PROJECTILE -> result.add("projectile_ctrl_main");
            default -> {
            }
        }
        return result;
    }

    public static List<String> unboundEvents(Collection<String> events, Set<String> executedEvents) {
        if (events == null || events.isEmpty())
            return List.of();
        return events.stream()
                .filter(YsmControllerSlotBinder::isControllerEvent)
                .filter(event -> executedEvents == null || !executedEvents.contains(event))
                .sorted(YsmControllerSlotBinder::compareEventOrder)
                .toList();
    }

    public static boolean isControllerEvent(String event) {
        return event != null && (event.startsWith("player_ctrl_") || event.startsWith("vehicle_ctrl_") || event.startsWith("projectile_ctrl_"));
    }

    public static int compareEventOrder(String left, String right) {
        int order = Integer.compare(eventOrder(left), eventOrder(right));
        return order != 0 ? order : left.compareTo(right);
    }

    private static int eventOrder(String event) {
        if ("player_ctrl_pre_main".equals(event))
            return 0;
        if ("player_ctrl_main".equals(event))
            return 10;
        if (event != null && event.startsWith("player_ctrl_pre_parallel"))
            return 15;
        if (event != null && event.startsWith("player_ctrl_parallel"))
            return 20;
        if (event != null && event.startsWith("player_ctrl_post_main"))
            return 25;
        if ("player_ctrl_post_swing".equals(event))
            return 30;
        if ("player_ctrl_fp_arm".equals(event))
            return 40;
        if (event != null && event.startsWith("vehicle_ctrl"))
            return 50;
        if (event != null && event.startsWith("projectile_ctrl"))
            return 60;
        return 100;
    }

    private static String numericSuffix(String value) {
        int end = value.length() - 1;
        while (end >= 0 && !Character.isDigit(value.charAt(end)))
            end--;
        if (end < 0)
            return "";
        int start = end;
        while (start >= 0 && Character.isDigit(value.charAt(start)))
            start--;
        return "_" + value.substring(start + 1, end + 1);
    }

    private static String normalizeName(String name) {
        return name == null ? "" : name.trim().toLowerCase(Locale.US);
    }
}
