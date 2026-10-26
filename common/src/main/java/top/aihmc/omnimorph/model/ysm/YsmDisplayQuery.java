package top.aihmc.omnimorph.model.ysm;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import top.aihmc.omnimorph.model.ysm.sync.YsmDisplayState;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * D025: the single read path for display values. The local player reads its own entity; another
 * player reads the state its owner published (stored by the sync receiver); anything else is unknown.
 * Molang bindings and Lua APIs both go through here so they cannot disagree.
 */
public final class YsmDisplayQuery {
    private static final Map<UUID, YsmDisplayState> REMOTE = new ConcurrentHashMap<>();

    private YsmDisplayQuery() {
    }

    public static YsmDisplayState of(Entity entity) {
        if (!(entity instanceof Player player)) {
            return null;
        }
        if (isLocal(player)) {
            return YsmDisplayState.snapshot(player);
        }
        return REMOTE.get(player.getUUID());
    }

    /*
     * Field reads used by Molang. A remote player's published value wins; otherwise the entity's own
     * value is used, which is exact for the local player and the vanilla default for other players.
     */

    public static boolean flying(Player player) {
        YsmDisplayState state = remoteState(player);
        return state != null && state.flying() != null ? state.flying() : player.getAbilities().flying;
    }

    public static int foodLevel(Player player) {
        YsmDisplayState state = remoteState(player);
        return state != null && state.foodLevel() != null ? state.foodLevel() : player.getFoodData().getFoodLevel();
    }

    public static int experienceLevel(Player player) {
        YsmDisplayState state = remoteState(player);
        return state != null && state.experienceLevel() != null ? state.experienceLevel() : player.experienceLevel;
    }

    /** Movement input axis: 0 = xxa (strafe), 1 = yya (vertical), 2 = zza (forward). */
    public static float input(net.minecraft.world.entity.LivingEntity entity, int axis) {
        YsmDisplayState state = entity instanceof Player player ? remoteState(player) : null;
        Float published = state == null ? null : switch (axis) {
            case 0 -> state.strafeInput();
            case 1 -> state.verticalInput();
            default -> state.forwardInput();
        };
        if (published != null) {
            return published;
        }
        return switch (axis) {
            case 0 -> entity.xxa;
            case 1 -> entity.yya;
            default -> entity.zza;
        };
    }

    /** Published effect level for a remote player, or null to read the entity's own effects. */
    public static Integer remoteEffectLevel(Entity entity, String effectId) {
        YsmDisplayState state = entity instanceof Player player ? remoteState(player) : null;
        if (state == null || state.effectLevels() == null || effectId == null) {
            return null;
        }
        return state.effectLevels().getOrDefault(effectId, 0);
    }

    private static YsmDisplayState remoteState(Player player) {
        return isLocal(player) ? null : REMOTE.get(player.getUUID());
    }

    /** True when the values come from the local entity or from a received owner publication. */
    public static boolean isKnown(Entity entity) {
        return of(entity) != null;
    }

    /** The state another player published, or null when none was received. */
    public static YsmDisplayState remote(UUID owner) {
        return owner == null ? null : REMOTE.get(owner);
    }

    public static void acceptRemote(UUID owner, YsmDisplayState state) {
        if (owner == null) {
            return;
        }
        if (state == null) {
            REMOTE.remove(owner);
        } else {
            REMOTE.put(owner, state);
        }
    }

    public static void forget(UUID owner) {
        if (owner != null) {
            REMOTE.remove(owner);
        }
    }

    public static void clear() {
        REMOTE.clear();
    }

    private static boolean isLocal(Player player) {
        Minecraft client = Minecraft.getInstance();
        return client != null && client.player != null && client.player == player;
    }
}
