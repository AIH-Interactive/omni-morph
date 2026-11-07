package top.aihmc.omm.preview;

import com.google.gson.JsonObject;
import java.util.LinkedHashMap;
import java.util.List;

/** Real script discovery uses the loaded model inventory, including modules not executed yet. */
public final class PreviewLuaFilesTest {
    public static void main(String[] args) throws Exception {
        var sources = new LinkedHashMap<String, String>();
        sources.put("root/main", """
            local direct = listFiles('./', false)
            assert(#direct == 2 and direct[1] == 'root.main' and direct[2] == 'root.helper')
            local nested = listFiles('./nested', true)
            assert(#nested == 1 and nested[1] == 'root.nested.GSAnimBlend')
            local all = listFiles(nil, true)
            assert(#all == 4 and all[4] == 'other.module')
            assert(unloaded_module == nil)
            assert(not pcall(function() listFiles(nil, 'wrong') end))
            discovery_passed = true
            """);
        sources.put("root/helper", "unloaded_module = true");
        sources.put("root/nested/GSAnimBlend", "return {}");
        sources.put("other/module", "return {}");
        var states = new JsonObject();
        try (var lua = new PreviewLua(
                 new PreviewLua.Program(sources, List.of("root/main")), states, 42, new PreviewMolang(states, 42))) {
            lua.initialize();
            if (!lua.evaluate("return discovery_passed").arg1().checkboolean()) {
                throw new AssertionError("Real model script inventory was not discovered");
            }
        }
        System.out.println(
            "Lua listFiles PASS: root/relative/recursive paths, unloaded modules and original argument rules");
    }
}
