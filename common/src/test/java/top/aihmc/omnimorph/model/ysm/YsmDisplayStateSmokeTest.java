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
        try {
            testRemoteMolangLuaAgree();
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError(exception);
        }
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

    /**
     * D025/S6 and AGENTS third-party rule 7: for a remote player with a published state, Molang bindings and
     * the Lua API read the same values; without a published state Lua reports unknown.
     */
    private static void testRemoteMolangLuaAgree() throws ReflectiveOperationException {
        var unsafeField = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
        unsafeField.setAccessible(true);
        var unsafe = (sun.misc.Unsafe) unsafeField.get(null);
        var player = (net.minecraft.client.player.RemotePlayer) unsafe.allocateInstance(net.minecraft.client.player.RemotePlayer.class);
        UUID owner = UUID.randomUUID();
        var uuid = net.minecraft.world.entity.Entity.class.getDeclaredField("uuid");
        uuid.setAccessible(true);
        uuid.set(player, owner);
        var api = new YsmDisplayAPI();
        var wrapped = org.figuramc.figura.lua.api.entity.EntityAPI.wrap(player);
        require(!api.isSynced(wrapped) && api.get(wrapped) == null, "no published state is unknown to Lua");

        var published = new YsmDisplayState(6, 42, true, -1f, 0f, 1f, Map.of("minecraft:speed", 3));
        YsmDisplayQuery.acceptRemote(owner, published);
        var engine = top.aihmc.omnimorph.molang.MolangEngine.fromCustomBinding(
                new top.aihmc.omnimorph.molang.runtime.binding.MolangBindings());
        var context = new org.figuramc.figura.avatar.Avatar.MolangContext(null);
        context.entity = player;
        var evaluator = top.aihmc.omnimorph.molang.runtime.ExpressionEvaluator.evaluator(context);
        java.util.function.Function<String, Float> molang = source -> {
            try {
                return ((Number) evaluator.eval(engine.parse(source).getFirst())).floatValue();
            } catch (Exception exception) {
                throw new AssertionError(source, exception);
            }
        };
        require(molang.apply("ysm.food_level;") == 6f && api.getFood(wrapped) == 6, "food agrees");
        require(molang.apply("ysm.xxa;") == -1f && molang.apply("ysm.zza;") == 1f
                        && api.getMovementInput(wrapped).x == -1d && api.getMovementInput(wrapped).z == 1d,
                "movement input agrees");
        require(molang.apply("ysm.effect_level('minecraft:speed');") == 3f
                        && api.getEffectLevel(wrapped, "minecraft:speed") == 3, "effect level agrees");
        require(YsmDisplayQuery.experienceLevel(player) == 42 && api.getExperienceLevel(wrapped) == 42,
                "experience agrees");
        require(YsmDisplayQuery.flying(player) && Boolean.TRUE.equals(api.isFlying(wrapped)), "flying agrees");
        YsmDisplayQuery.clear();
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
