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
