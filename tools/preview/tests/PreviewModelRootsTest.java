package top.aihmc.omm.preview;

import com.google.gson.JsonObject;
import java.util.List;
import java.util.Map;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/** Exercises actual hierarchy objects entirely in memory. */
public final class PreviewModelRootsTest {
    private static PreviewLua runtime() throws Exception {
        var states = new JsonObject();
        var lua =
            new PreviewLua(new PreviewLua.Program(Map.of(), List.of()), states, 42, new PreviewMolang(states, 42));
        var parts = new PreviewFiguraParts();
        parts.nodes.add(node("one", -1));
        parts.nodes.add(node("child", 0));
        parts.nodes.add(node("two", -1));
        lua.attachFigura(parts);
        lua.initialize();
        return lua;
    }

    private static PreviewFiguraParts.Node node(String name, int parent) {
        return new PreviewFiguraParts.Node(
            name, name, parent, new Vector3f(), new Vector3f(), false, true, new Matrix4f(), new Matrix4f());
    }

    public static void main(String[] args) throws Exception {
        try (var first = runtime(); var second = runtime()) {
            first.evaluate("""
                local roots = models:getChildren()
                assert(#roots == 2 and roots[1] == models.one and roots[2] == models.two)
                assert(roots[1]:getChildren()[1]:getName() == 'child')
                roots[1]:setVisible(false)
                assert(models.one:getVisible() == false)
                roots[1] = nil
                assert(#models:getChildren() == 2)
                local count = 0
                for _, part in pairs(models) do count = count + 1 end
                assert(count == 2)
                """);
            second.evaluate("assert(models.one:getVisible())");
        }
        System.out.println(
            "Model roots PASS: actual child identity, hierarchy, fresh list and instance isolation; no model loads");
    }
}
