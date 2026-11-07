package top.aihmc.omm.preview;

import com.google.gson.JsonObject;
import java.util.List;
import java.util.Map;

/** No file or avatar loading: validates the unequipped armor visibility adapter. */
public final class PreviewArmorVisibilityTest {
    private static PreviewLua runtime() throws Exception {
        var constructor = PreviewVanillaModel.class.getDeclaredConstructor(boolean.class);
        constructor.setAccessible(true);
        var states = new JsonObject();
        var lua =
            new PreviewLua(new PreviewLua.Program(Map.of(), List.of()), states, 42, new PreviewMolang(states, 42));
        lua.attachVanilla(constructor.newInstance(false));
        lua.initialize();
        return lua;
    }

    public static void main(String[] args) throws Exception {
        try (var first = runtime(); var second = runtime()) {
            first.evaluate("""
                assert(vanilla_model.ARMOR ~= nil)
                assert(vanilla_model.ARMOR:getVisible() == nil)
                assert(vanilla_model.ARMOR:setVisible(false) == vanilla_model.ARMOR)
                assert(vanilla_model.ARMOR:getVisible() == false)
                assert(vanilla_model.PLAYER:getVisible() == true)
                vanilla_model.PLAYER:setVisible(false)
                vanilla_model.ARMOR:setVisible(true)
                assert(vanilla_model.PLAYER:getVisible() == false)
                vanilla_model.ARMOR:setVisible(nil)
                assert(vanilla_model.ARMOR:getVisible() == nil)
                """);
            second.evaluate("assert(vanilla_model.ARMOR:getVisible() == nil)");
        }
        System.out.println(
            "Armor visibility PASS: nullable override, player independence and instance isolation; no model loads");
    }
}
