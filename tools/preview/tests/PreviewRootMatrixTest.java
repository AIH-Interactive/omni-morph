package top.aihmc.omm.preview;

import com.google.gson.JsonObject;
import java.util.List;
import java.util.Map;

/** Root model-space units match the existing preview part-to-world convention. */
public final class PreviewRootMatrixTest {
    public static void main(String[] args) throws Exception {
        var states = new JsonObject();
        try (var lua = new PreviewLua(
                 new PreviewLua.Program(Map.of(), List.of()), states, 42, new PreviewMolang(states, 42))) {
            lua.attachFigura(new PreviewFiguraParts());
            lua.initialize();
            lua.evaluate("""
                local matrix = models:partToWorldMatrix()
                local direction = matrix:applyDir(0, 0, -1)
                assert(direction.x == 0 and direction.y == 0 and direction.z == -1/16)
                matrix:scale(2, 2, 2)
                local unchanged = models:partToWorldMatrix():applyDir(0, 0, -1)
                assert(unchanged.z == -1/16)
                local facing = unchanged:normalize()
                assert(facing.z == -1)
                """);
        }
        System.out.println(
            "Root matrix PASS: real matrix operations, preview units and copy isolation; no model loads");
    }
}
