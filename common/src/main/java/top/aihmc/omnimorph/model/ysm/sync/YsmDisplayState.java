package top.aihmc.omnimorph.model.ysm.sync;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.player.Player;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * D025: player values the vanilla client does not send to other players, published by their owner.
 * Every field is nullable ("unknown"). Ranges follow Sparkle's {@code PlayerDisplayState}; health and
 * shield blocking are left out because vanilla already synchronises them.
 */
public record YsmDisplayState(Integer foodLevel, Integer experienceLevel, Boolean flying,
                              Float strafeInput, Float verticalInput, Float forwardInput,
                              Map<String, Integer> effectLevels) {
    public static final int MAX_EFFECTS = 64;
    public static final int MAX_EFFECT_ID_LENGTH = 128;
    public static final YsmDisplayState UNKNOWN = new YsmDisplayState(null, null, null, null, null, null, null);
    private static final Pattern EFFECT_ID = Pattern.compile("[a-z0-9_.-]+:[a-z0-9_./-]+");

    public YsmDisplayState {
        if (foodLevel != null && (foodLevel < 0 || foodLevel > 20)) {
            throw new IllegalArgumentException("Invalid display food level");
        }
        if (experienceLevel != null && experienceLevel < 0) {
            throw new IllegalArgumentException("Invalid display experience level");
        }
        requireInput(strafeInput);
        requireInput(verticalInput);
        requireInput(forwardInput);
        if (effectLevels != null) {
            effectLevels = Map.copyOf(effectLevels);
            if (effectLevels.size() > MAX_EFFECTS) {
                throw new IllegalArgumentException("Too many display effects");
            }
            effectLevels.forEach((id, level) -> {
                if (id.length() > MAX_EFFECT_ID_LENGTH || !EFFECT_ID.matcher(id).matches()
                        || level == null || level < 1 || level > 256) {
                    throw new IllegalArgumentException("Invalid display effect");
                }
            });
        }
    }

    /** Captures the values a local player may publish; out-of-range values become unknown. */
    public static YsmDisplayState snapshot(Player player) {
        Map<String, Integer> effects = new LinkedHashMap<>();
        for (MobEffectInstance effect : player.getActiveEffects()) {
            int level = effect.getAmplifier() + 1;
            var key = BuiltInRegistries.MOB_EFFECT.getKey(effect.getEffect().value());
            if (key == null || level < 1 || level > 256 || effects.size() >= MAX_EFFECTS) {
                effects = null;
                break;
            }
            effects.put(key.toString(), level);
        }
        int food = player.getFoodData().getFoodLevel();
        return new YsmDisplayState(
                food >= 0 && food <= 20 ? food : null,
                Math.max(0, player.experienceLevel),
                player.getAbilities().flying,
                input(player.xxa),
                input(player.yya),
                input(player.zza),
                effects);
    }

    private static Float input(float value) {
        return Float.isFinite(value) ? Math.max(-1f, Math.min(1f, value)) : null;
    }

    private static void requireInput(Float value) {
        if (value != null && (!Float.isFinite(value) || value < -1f || value > 1f)) {
            throw new IllegalArgumentException("Invalid display input");
        }
    }
}
