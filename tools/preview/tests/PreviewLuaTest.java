package top.aihmc.omm.preview;

import com.google.gson.JsonObject;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import org.luaj.vm2.LuaError;

/** Real LuaJ, original event/type/control APIs and Molang values; no parser or callback stubs. */
public final class PreviewLuaTest {
    public static void main(String[] args) throws Exception {
        var states = new JsonObject();
        states.addProperty("running", true);
        var sources = new LinkedHashMap<String, String>();
        sources.put("scripts/main", """
            helper = require('./helper')
            assert(helper == require('./helper'))
            ticks = 0
            events.entity_init = function() initialized = preview.running end
            events.tick:register(function()
              ticks = ticks + 1
            end, 'counter')
            events.render = function(delta, context) last_delta = delta; last_context = context end
            trace = ''
            avatar_controls:setOnChange(function(id, value, control) trace = trace .. 'I'; return false end)
            avatar_controls:newToggle('hat'):title('Hat'):binding('v.roaming.hat'):onChange(function(value)
              trace = trace .. 'C'; changed = value
            end)
            avatar_controls:newSlider('size'):range(0, 10, 1):setDefault(2):binding('v.size')
            avatar_controls:newButton('reset'):onPress(function() pressed = (pressed or 0) + 1 end)
            avatar_controls:newEnum('mode'):options({'idle', 'run'}):optionCommand('run', 'v.command = 7;')
            """);
        sources.put("scripts/helper", "helper_loads = (helper_loads or 0) + 1; return { answer = 42 }");
        var program = new PreviewLua.Program(sources, List.of("scripts/main"));
        var molang = new PreviewMolang(states, 42);
        var first = new PreviewLua(program, states, 42, molang);
        first.initialize();
        if (!first.evaluate("return initialized and helper_loads == 1 and helper.answer == 42").arg1().checkboolean()) {
            throw new AssertionError("Actual initialization, relative require or module cache failed");
        }
        first.event("TICK");
        first.event("tick");
        first.event("render", 0.5, "RENDER");
        if (!first.evaluate("return ticks == 2 and last_delta == 0.5 and last_context == 'RENDER'")
                .arg1()
                .checkboolean()) {
            throw new AssertionError("Original event dispatch did not execute callbacks");
        }
        first.evaluate(
            "avatar_controls:set('hat', true); avatar_controls:set('size', 99); avatar_controls:press('reset')");
        close(molang.evaluate(molang.compile("return v.roaming.hat + v.hat + v.show_hat + v.size;")), 13,
            "Real nested Molang binding, roaming aliases and slider normalization");
        if (!first.evaluate("return trace == 'ICI' and changed == true and pressed == 1").arg1().checkboolean()) {
            throw new AssertionError("Control interceptor/change/press callbacks differ from OMP order");
        }
        first.evaluate("avatar_controls:set('mode', 'run')");
        close(molang.evaluate(molang.compile("return v.command;")), 7, "Option command executes real Molang");
        molang.variables.setPath("command", 0f);
        first.evaluate("avatar_controls:set('mode', 1)");
        close(molang.evaluate(molang.compile("return v.command;")), 7,
            "Numeric enum option resolves its original command");
        first.evaluate("avatar_controls:reset('size')");
        close(molang.evaluate(molang.compile("return v.size;")), 2, "Default reset synchronizes binding");
        first.evaluate("avatar_controls:setOnChange(function() return true end); avatar_controls:set('size', 5)");
        close(molang.evaluate(molang.compile("return v.size;")), 2, "Interceptor prevents binding synchronization");
        if (first.evaluate("return avatar_controls:get('size')").arg1().checkdouble() != 5) {
            throw new AssertionError("Interceptor reverted the original control's normalized stored value");
        }
        var second = new PreviewLua(program, states, 42, new PreviewMolang(states, 42));
        second.initialize();
        if (!second.evaluate("return ticks == 0 and helper_loads == 1 and avatar_controls:get('hat') == false")
                .arg1()
                .checkboolean()) {
            throw new AssertionError("Lua globals/module/control state leaked between model instances");
        }
        for (int index = 0; index < 10; index++) {
            close(first.evaluate("return math.random()").arg1().checkdouble(),
                second.evaluate("return math.random()").arg1().checkdouble(), "Seeded per-instance Lua RNG");
        }
        if (!first.evaluate("return debug == nil and io == nil and os == nil and luajava == nil and loadfile == nil")
                .arg1()
                .checkboolean()) {
            throw new AssertionError("Original restricted library/sandbox policy was not retained");
        }
        try {
            first.evaluate("preview.running = false");
            throw new AssertionError("Simulated states were writable from the model");
        } catch (LuaError expected) {
            if (!expected.getMessage().contains("read-only")) {
                throw expected;
            }
        }
        try {
            first.evaluate("while true do pcall(function() while true do end end) end");
            throw new AssertionError("Lua pcall bypassed the instruction budget");
        } catch (LuaError expected) {
            if (!expected.getMessage().contains("instruction budget")) {
                throw expected;
            }
        }
        try {
            first.evaluate("""
                avatar_controls:newButton('loop'):onPress(function() while true do end end)
                while true do pcall(function() avatar_controls:press('loop') end) end
                """);
            throw new AssertionError("A reflected nested model callback bypassed the instruction budget");
        } catch (LuaError expected) {
            if (!expected.getMessage().contains("instruction budget")) {
                throw expected;
            }
        }
        close(first.evaluate("return 2 + 2").arg1().checkdouble(), 4, "Bounded call cleanup restores later execution");
        Path folder = Path.of(args[0]).resolve("lua-program");
        Files.createDirectories(folder.resolve("scripts"));
        Files.writeString(folder.resolve("avatar.json"), "{\"autoScripts\":[\"scripts/main.lua\"]}");
        for (var entry : sources.entrySet()) {
            Files.writeString(folder.resolve(entry.getKey() + ".lua"), entry.getValue());
        }
        var loaded = PreviewLua.Program.load(folder);
        if (!loaded.sources().equals(program.sources()) || !loaded.entries().equals(program.entries())) {
            throw new AssertionError("Actual package scripts or avatar autoScripts semantics were lost");
        }
        Path archive = folder.resolveSibling("lua-program.zip");
        try (var zip = new java.util.zip.ZipOutputStream(Files.newOutputStream(archive))) {
            for (String file : List.of("avatar.json", "scripts/main.lua", "scripts/helper.lua")) {
                zip.putNextEntry(new java.util.zip.ZipEntry("nested/avatar/" + file));
                Files.copy(folder.resolve(file), zip);
                zip.closeEntry();
            }
        }
        var zipped = PreviewLua.Program.load(archive);
        if (!zipped.sources().equals(program.sources()) || !zipped.entries().equals(program.entries())) {
            throw new AssertionError("Nested Figura ZIP lost script root or autoScripts semantics");
        }
        System.out.println(
            "Lua PASS: Figura LuaJ 3.0.8, real events/require/controls/Molang, limits and instance isolation");
    }

    private static void close(double actual, double expected, String label) {
        if (Math.abs(actual - expected) > 0.00001) {
            throw new AssertionError(label + ": " + actual + " != " + expected);
        }
    }
}
