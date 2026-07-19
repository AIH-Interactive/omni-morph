package org.figuramc.figura.model.ysm.action;

import org.figuramc.figura.FiguraMod;
import org.figuramc.figura.molang.storage.StringPool;
import org.figuramc.figura.model.ysm.YsmModelRuntime;
import org.figuramc.figura.model.ysm.animation.YsmAnimationClip;

import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public class YsmActionRuntime {
    private static final Set<String> NATIVE_STATES = Set.of(
            "idle", "walk", "run", "sneak", "sneaking", "jump", "fall", "fly", "elytra_fly",
            "swim", "swim_stand", "sit", "ride", "sleep", "death", "die"
    );

    private final YsmModelRuntime runtime;
    private final LinkedHashMap<String, YsmActionDefinition> actions = new LinkedHashMap<>();
    private final Map<String, Integer> cooldownUntil = new HashMap<>();

    public YsmActionRuntime(YsmModelRuntime runtime) {
        this.runtime = runtime;
    }

    public void buildDefaultsFromAnimations() {
        LinkedHashSet<YsmAnimationClip> seen = new LinkedHashSet<>();
        for (Map.Entry<String, YsmAnimationClip> entry : runtime.animations().getClips().entrySet()) {
            String id = normalize(entry.getKey());
            if (id.isBlank() || NATIVE_STATES.contains(id) || isInternalAnimation(id) || !seen.add(entry.getValue()))
                continue;
            boolean legacyExtra = id.matches("extra\\d+");
            String actionId = legacyExtra ? "extra_animation." + id : id;
            actions.putIfAbsent(actionId, new YsmActionDefinition(actionId)
                    .setTitle(prettyTitle(id))
                    .setAnimation(id)
                    .setPage(legacyExtra ? "extra_animation" : "root")
                    .setLoop(entry.getValue().loop));
        }
    }

    private static boolean isInternalAnimation(String id) {
        if (id == null || id.isBlank())
            return true;
        String normalized = normalize(id);
        return normalized.equals("empty")
                || normalized.equals("attack_empty")
                || normalized.startsWith("pre_main")
                || normalized.startsWith("pre_parallel")
                || normalized.startsWith("player_ctrl")
                || normalized.contains("@player_ctrl")
                || normalized.contains("locator");
    }

    public Collection<YsmActionDefinition> all() {
        return actions.values();
    }

    public YsmActionDefinition register(YsmActionDefinition action) {
        if (action != null && action.getId() != null && !action.getId().isBlank())
            actions.put(normalize(action.getId()), action);
        return action;
    }

    public Map<String, YsmActionDefinition> asMap() {
        return new LinkedHashMap<>(actions);
    }

    public YsmActionDefinition get(String id) {
        return actions.get(normalize(id));
    }

    public boolean trigger(String id) {
        YsmActionDefinition action = get(id);
        if (action == null || action.getAnimation() == null)
            return false;
        String normalized = normalize(action.getId());
        int tick = FiguraMod.ticks;
        int availableAt = cooldownUntil.getOrDefault(normalized, 0);
        if (tick < availableAt)
            return false;
        String mode = action.getMode();
        if ("toggle".equals(mode) && isActive(id)) {
            stop(id);
            if (action.getCooldownTicks() > 0)
                cooldownUntil.put(normalized, tick + action.getCooldownTicks());
            return true;
        }
        boolean loop = action.isLoop() || "toggle".equals(mode) || "hold".equals(mode) || Boolean.TRUE.equals(runtime.owner().controls.getValue("ysm.action_loop"));
        Object speedValue = runtime.owner().controls.getValue("ysm.action_speed");
        float speed = action.getSpeed() > 0f ? action.getSpeed() : speedValue instanceof Number number ? number.floatValue() : 1f;
        stopOthers(normalized);
        boolean played = runtime.animations().play(action.getAnimation(), loop, speed) != null;
        if (played)
            pulseActionVariables(action);
        if (played && action.getCooldownTicks() > 0)
            cooldownUntil.put(normalized, tick + action.getCooldownTicks());
        return played;
    }

    public void stop(String id) {
        YsmActionDefinition action = get(id);
        if (action != null && action.getAnimation() != null)
            runtime.animations().stop(action.getAnimation());
    }

    public void stopAll() {
        for (YsmActionDefinition action : actions.values()) {
            if (action.getAnimation() != null)
                runtime.animations().stop(action.getAnimation());
        }
    }

    private void stopOthers(String currentId) {
        for (YsmActionDefinition action : actions.values()) {
            if (action.getAnimation() != null && !normalize(action.getId()).equals(currentId))
                runtime.animations().stop(action.getAnimation());
        }
    }

    public boolean isActive(String id) {
        YsmActionDefinition action = get(id);
        if (action == null || action.getAnimation() == null)
            return false;
        return runtime.animations().isActive(action.getAnimation());
    }

    public float time(String id) {
        YsmActionDefinition action = get(id);
        if (action == null || action.getAnimation() == null)
            return 0f;
        var playing = runtime.animations().getActiveAnimation(action.getAnimation());
        return playing == null ? 0f : playing.time;
    }

    private static String normalize(String id) {
        if (id == null)
            return "";
        String value = id;
        if (value.startsWith("animation."))
            value = value.substring("animation.".length());
        int colon = value.indexOf(':');
        if (colon >= 0 && colon + 1 < value.length())
            value = value.substring(colon + 1);
        return value.toLowerCase(Locale.US);
    }

    private void pulseActionVariables(YsmActionDefinition action) {
        if (runtime.owner().getMolangContext() == null)
            return;
        setVariable(action.getId(), 1f);
        setVariable(action.getAnimation(), 1f);
        String merged = (normalize(action.getId()) + "_" + normalize(action.getAnimation())).toLowerCase(Locale.US);
        if (merged.contains("attack") || merged.contains("swing") || merged.contains("sword"))
            setVariable("swing_sword", 1f);
    }

    private void setVariable(String name, Object value) {
        String normalized = normalize(name).replace('-', '_').replace(' ', '_');
        if (normalized.isBlank())
            return;
        runtime.owner().getMolangContext().variables.setScoped(StringPool.computeIfAbsent(normalized), value);
    }

    private static String prettyTitle(String id) {
        String[] parts = id.replace('.', '_').replace('-', '_').split("_");
        StringBuilder builder = new StringBuilder();
        for (String part : parts) {
            if (part.isBlank())
                continue;
            if (!builder.isEmpty())
                builder.append(' ');
            builder.append(Character.toUpperCase(part.charAt(0)));
            if (part.length() > 1)
                builder.append(part.substring(1));
        }
        return builder.isEmpty() ? id : builder.toString();
    }
}
