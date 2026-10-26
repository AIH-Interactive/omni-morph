package top.aihmc.omnimorph.model.ysm;

import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import top.aihmc.omnimorph.lua.api.ysm_model.YsmDisplayAPI;
import top.aihmc.omnimorph.model.ysm.sync.YsmDisplayState;

import java.util.Map;
import java.util.UUID;

/** D025/S1: display state ranges, the remote store and the Lua API's unknown (nil) semantics. */
public final class YsmDisplayStateSmokeTest {
    private YsmDisplayStateSmokeTest() {
    }

    public static void main(String[] args) {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        testRanges();
        testRemoteStore();
        testLuaUnknown();
        System.out.println("PASS YSM display state: ranges, remote store, local/remote/unknown read path");
    }

    private static void testRanges() {
        var valid = new YsmDisplayState(20, 30, true, -1f, 0f, 1f, Map.of("minecraft:speed", 2));
        require(valid.effectLevels().get("minecraft:speed") == 2, "valid state keeps effect levels");
        require(YsmDisplayState.UNKNOWN.foodLevel() == null, "unknown fields are null");
        reject(() -> new YsmDisplayState(21, null, null, null, null, null, null), "food above 20");
        reject(() -> new YsmDisplayState(null, -1, null, null, null, null, null), "negative experience");
        reject(() -> new YsmDisplayState(null, null, null, 1.5f, null, null, null), "input above 1");
        reject(() -> new YsmDisplayState(null, null, null, Float.NaN, null, null, null), "non-finite input");
        reject(() -> new YsmDisplayState(null, null, null, null, null, null, Map.of("Bad Id", 1)), "effect id format");
        reject(() -> new YsmDisplayState(null, null, null, null, null, null, Map.of("minecraft:speed", 0)), "effect level 0");
    }

    private static void testRemoteStore() {
        UUID owner = UUID.randomUUID();
        YsmDisplayQuery.acceptRemote(owner, new YsmDisplayState(5, null, null, null, null, null, null));
        YsmDisplayQuery.forget(owner);
        YsmDisplayQuery.acceptRemote(null, YsmDisplayState.UNKNOWN);
        require(YsmDisplayQuery.of(null) == null, "a missing entity is unknown");
        YsmDisplayQuery.clear();
    }

    private static void testLuaUnknown() {
        var api = new YsmDisplayAPI();
        require(api.get(null) == null && !api.isSynced(null) && api.getFood(null) == null
                        && api.getMovementInput(null) == null && api.getEffectLevel(null, "minecraft:speed") == null,
                "the Lua API reports unknown values as nil");
    }

    private static void reject(Runnable action, String message) {
        try {
            action.run();
        } catch (IllegalArgumentException expected) {
            return;
        }
        throw new AssertionError("must reject " + message);
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
