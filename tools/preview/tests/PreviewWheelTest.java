package top.aihmc.omm.preview;

import com.google.gson.JsonObject;
import java.util.List;
import java.util.Map;
import org.luaj.vm2.LuaError;

public final class PreviewWheelTest {
    static final String SCRIPT = """
        avatar_controls:newToggle('hat'):binding('v.hat')
        main = action_wheel:newPage('Main')
        other = action_wheel:newPage('Other')
        action_wheel:setPage(main)
        clicks, right_clicks, scrolls = 0, 0, 0
        first = main:newAction(1):title('Show hat'):toggleTitle('Hide hat'):color(0.2,0.4,0.6)
        first:onLeftClick(function(action) clicks = clicks + 1 end)
        first:onRightClick(function(action) right_clicks = right_clicks + 1 end)
        first:onToggle(function(enabled, action) avatar_controls:set('hat',enabled) end)
        first:onUntoggle(function(enabled, action) avatar_controls:set('hat',enabled) end)
        first:onScroll(function(delta, action) scrolls = scrolls + delta end)
        main:newAction(2):title('Settings'):controlsPage('appearance')
        main:newAction(9):title('Second group')
        action_wheel.leftClick = function() intercepted = (intercepted or 0) + 1; return false end
        """;

    public static void main(String[] args) throws Exception {
        JsonObject states = new JsonObject();
        var program = new PreviewLua.Program(Map.of("main", SCRIPT), List.of("main"));
        var host = new PreviewMolang(states, 42);
        var lua = new PreviewLua(program, states, 42, host);
        lua.initialize();
        lua.wheelState(true, 0);
        lua.executeWheel(0, true);
        check(lua, "return clicks == 1 and intercepted == 1 and first:isToggled() and first:getTitle() == 'Hide hat'",
            "Original left/global/toggle callbacks and title");
        number(host, 1, "Wheel toggle changes actual Molang control binding");
        lua.wheelState(true, 0);
        lua.executeWheel(0, false);
        check(lua, "return right_clicks == 1 and first:isToggled()", "Right click does not toggle");
        lua.wheelState(true, 0);
        lua.scrollWheel(2);
        check(lua, "return scrolls == 2 and main:getSlotsShift() == 1", "Selected action consumes wheel scroll");
        lua.wheelState(true, -1);
        lua.scrollWheel(-1);
        check(lua, "return main:getSlotsShift() == 2", "Empty selection scrolls to second page group");
        lua.evaluate("main:setKeepSlots(true); action_wheel:setPage(other); action_wheel:setPage(main)");
        check(lua, "return main:getSlotsShift() == 2", "Page keepSlots is preserved");
        lua.evaluate("main:setKeepSlots(false); action_wheel:setPage(main)");
        lua.wheelState(true, 0);
        lua.executeWheel(0, true);
        number(host, 0, "Untoggle restores original model state");
        lua.wheelState(true, 0);
        lua.evaluate("action_wheel.leftClick = function() return true end");
        lua.executeWheel(0, true);
        number(host, 0, "Global interceptor consumes action before toggle");
        lua.evaluate("action_wheel.leftClick = nil");
        lua.wheelState(true, 1);
        lua.executeWheel(1, true);
        if (!lua.controlsPage().equals("appearance")) {
            throw new AssertionError("Wheel action did not request its actual settings page");
        }
        var color = lua.actionWheel.currentPage.slots()[0].getColor(false);
        if (Math.abs(color.x - 0.2) > 0.00001 || Math.abs(color.z - 0.6) > 0.00001) {
            throw new AssertionError("Wheel color metadata was not retained");
        }
        var second = new PreviewLua(program, states, 42, new PreviewMolang(states, 42));
        second.initialize();
        check(second, "return clicks == 0 and not first:isToggled()", "Wheel pages/actions are per instance");
        lua.wheelState(false, 0);
        lua.executeWheel(0, true);
        number(host, 0, "Closed wheel does not execute selected action");
        try {
            lua.evaluate("action_wheel:execute(9)");
            throw new AssertionError("Invalid visible slot index accepted");
        } catch (LuaError expected) {
            if (!expected.getMessage().contains("between 1 and 8")) {
                throw expected;
            }
        }
        System.out.println(
            "Wheel PASS: original pages/actions, callbacks, toggle/scroll/interception, Molang and isolation");
    }

    private static void check(PreviewLua lua, String expression, String label) {
        if (!lua.evaluate(expression).arg1().checkboolean()) {
            throw new AssertionError(label);
        }
    }

    private static void number(PreviewMolang host, float expected, String label) throws Exception {
        float actual = host.evaluate(host.compile("return v.hat;"));
        if (Math.abs(actual - expected) > 0.00001) {
            throw new AssertionError(label + ": " + actual);
        }
    }
}
