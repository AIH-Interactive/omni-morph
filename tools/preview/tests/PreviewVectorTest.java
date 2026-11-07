package top.aihmc.omm.preview;

import com.google.gson.JsonParser;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/** Real Figura algebra and explicit player state must execute through the original Lua binder. */
public final class PreviewVectorTest {
    public static void main(String[] args) throws Exception {
        var states = JsonParser.parseString("""
            {"player":{"velocity":[0.25,0,0],"pose":"STANDING","sprinting":true,"health":18,"loaded":true,"previewTestFlag":true,"isPreviewOtherFlag":false,"getPreviewGauge":2.5,"getPreviewDirection":[1,2,3]}}
            """).getAsJsonObject();
        var lua =
            new PreviewLua(new PreviewLua.Program(Map.of(), List.of()), states, 42, new PreviewMolang(states, 42));
        lua.initialize();
        lua.evaluate("""
            local a = vec(1,2,3)
            assert(a:lengthSquared() == 14)
            assert(a.yzx == vec(2,3,1))
            assert(a * 2 + vec(1,1,1) == vec(3,5,7))
            a.x = 4
            assert(a == vec(4,2,3))
            assert(vec(1,0,0):crossed(vec(0,1,0)) == vec(0,0,1))
            assert(vectors.vec2(3,4):length() == 5)
            assert(vectors.vec4(1,2,3,4).w == 4)
            assert(vectors.rgbToInt(vectors.hexToRGB('red')) == 0xff5555)
            assert(vectors.rgbToInt(vectors.hexToRGB('figura_blue')) == 0x00f0ff)
            local matrix = matrices.translate4(1,2,3)
            assert(matrix:apply(vec(3,4,5)) == vec(4,6,8))
            assert(matrix:inverted():apply(vec(4,6,8)) == vec(3,4,5))
            assert(player:isSprinting() and player:isLoaded())
            assert(player:isPreviewTestFlag() and not player:isPreviewOtherFlag())
            assert(player:getPreviewGauge() == 2.5)
            assert(player:getPreviewDirection().z == 3)
            assert(player:getPose() == 'STANDING' and player:getHealth() == 18)
            assert(player:getVelocity():length() == 0.25)
            local velocity = player:getVelocity()
            velocity.x = 99
            assert(player:getVelocity().x == 0.25)
            local good, error = pcall(function() return player:isGliding() end)
            assert(not good and string.find(error, 'states.player.gliding', 1, true))
            """);
        var badStates = JsonParser.parseString("{\"player\":{\"sprinting\":\"true\"}}").getAsJsonObject();
        var bad = new PreviewLua(
            new PreviewLua.Program(Map.of(), List.of()), badStates, 42, new PreviewMolang(badStates, 42));
        bad.initialize();
        if (bad.evaluate("return pcall(function() return player:isSprinting() end)").toboolean(1)) {
            throw new AssertionError("String simulated state was silently accepted as boolean");
        }
        System.out.println("Figura math/player PASS: original vectors/matrices, binder operators/swizzles, explicit "
            + "typed state and copy isolation");
    }
}
