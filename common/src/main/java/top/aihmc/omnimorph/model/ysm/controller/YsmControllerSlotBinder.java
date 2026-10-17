package top.aihmc.omnimorph.model.ysm.controller;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.Map;

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

        if (slot == YsmControllerSlot.FIRST_PERSON_ARM) {
            result.add("player_ctrl_fp_arm");
            return result;
        }

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
            if (!simple.contains("pre_main") && !simple.contains("post_main")
                    && (simple.endsWith(".main") || simple.endsWith("_main") || simple.contains("player.main")))
                result.add("player_ctrl_main");
        }

        switch (slot) {
            case MAIN -> {
                result.add(simple.contains("pre_main") ? "player_ctrl_pre_main" : "player_ctrl_main");
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

    /** Standard function stages exist even when the model supplies no state-machine JSON for them. */
    public static Map<String, YsmAnimationController> missingEventControllers(Collection<String> events,
                                                                            Collection<YsmAnimationController> definitions) {
        Map<String, YsmAnimationController> result = new LinkedHashMap<>();
        Set<String> owned = new LinkedHashSet<>();
        for (YsmAnimationController definition : definitions) {
            owned.addAll(eventNamesForController(definition.name(), definition.slot()));
        }
        for (String event : events.stream().sorted(YsmControllerSlotBinder::compareEventOrder).toList()) {
            if (owned.contains(event)) {
                continue;
            }
            String name = controllerNameForEvent(event);
            if (name == null) {
                continue;
            }
            YsmControllerState state = new YsmControllerState("default", List.of(), List.of(),
                    List.of(), List.of(), 0f, false);
            LinkedHashMap<String, YsmControllerState> states = new LinkedHashMap<>();
            states.put(state.name(), state);
            YsmAnimationController controller = new YsmAnimationController(name, state.name(),
                    YsmControllerSlot.fromName(name), states);
            result.put(name, controller);
            owned.addAll(eventNamesForController(name, controller.slot()));
        }
        return result;
    }

    private static String controllerNameForEvent(String event) {
        if ("player_ctrl_fp_arm".equals(event)) {
            return "fp.arm.misc";
        }
        if ("vehicle_ctrl_main".equals(event)) {
            return "vehicle.main";
        }
        if ("projectile_ctrl_main".equals(event)) {
            return "projectile.main";
        }
        for (String stage : List.of("pre_parallel", "pre_main", "main", "post_main", "post_swing", "parallel")) {
            String prefix = "player_ctrl_" + stage;
            boolean indexed = stage.equals("pre_parallel") || stage.equals("parallel") || stage.equals("post_main");
            if (event.equals(prefix) || indexed && event.startsWith(prefix + "_")
                    && event.substring(prefix.length() + 1).matches("[0-9]+")) {
                return "player." + event.substring("player_ctrl_".length());
            }
        }
        return null;
    }

    public static boolean isControllerEvent(String event) {
        return event != null && (event.startsWith("player_ctrl_") || event.startsWith("vehicle_ctrl_") || event.startsWith("projectile_ctrl_"));
    }

    public static int compareEventOrder(String left, String right) {
        int order = Integer.compare(eventOrder(left), eventOrder(right));
        return order != 0 ? order : left.compareTo(right);
    }

    private static int eventOrder(String event) {
        if (event != null && event.startsWith("player_ctrl_pre_parallel"))
            return 0;
        if ("player_ctrl_pre_main".equals(event))
            return 10;
        if ("player_ctrl_main".equals(event))
            return 20;
        if (event != null && event.startsWith("player_ctrl_post_main"))
            return 30;
        if ("player_ctrl_post_swing".equals(event))
            return 40;
        if (event != null && event.startsWith("player_ctrl_parallel"))
            return 50;
        if ("player_ctrl_fp_arm".equals(event))
            return 60;
        if (event != null && event.startsWith("vehicle_ctrl"))
            return 5;
        if (event != null && event.startsWith("projectile_ctrl"))
            return 70;
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
