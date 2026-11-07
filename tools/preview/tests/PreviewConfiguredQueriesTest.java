package top.aihmc.omm.preview;

import com.google.gson.JsonParser;
import java.util.List;
import java.util.Map;

/** Configured query values only; no model parsing or simulated Minecraft state machine. */
public final class PreviewConfiguredQueriesTest {
    public static void main(String[] args) throws Exception {
        var states = JsonParser.parseString("""
            {"host":{"isHost":false},"player":{"getVehicle":null,
             "getHeldItem":{"id":"minecraft:air","getUseAction":"NONE","tag":{}}},
             "world":{"time":40,"getBlockState":{"id":"minecraft:air","isSolidBlock":false}}}
            """).getAsJsonObject();
        try (var lua = new PreviewLua(
                 new PreviewLua.Program(Map.of(), List.of()), states, 42, new PreviewMolang(states, 42))) {
            lua.initialize();
            lua.evaluate("""
                assert(host:isHost() == false)
                assert(player:getVehicle() == nil)
                local right = player:getHeldItem(false)
                local left = player:getHeldItem(true)
                assert(right.id == 'minecraft:air' and right:getUseAction() == 'NONE')
                assert(left ~= right and next(right.tag) == nil)
                local block = world.getBlockState(vec(0, -0.1, 0))
                assert(block.id == 'minecraft:air' and block:isSolidBlock() == false)
                assert(world.getTime() == 40)
                assert(player.getTargetedEntity == nil and host.isFlying == nil)
                """);
        }
        System.out.println("Configured queries PASS: explicit host/null vehicle/item/block, fresh results and original "
                           + "clock; no model loads");
    }
}
