package top.aihmc.omm.preview;

import com.google.gson.JsonParser;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Map;
import org.joml.Vector3f;

/** Tick and render clocks must preserve original Figura delta semantics and actual GPU poses. */
public final class PreviewWorldClockTest {
    public static void main(String[] args) throws Exception {
        Path root = Path.of(args[0]).toAbsolutePath();
        Path folder = root.resolve("figura-world-clock");
        Files.createDirectories(folder);
        for (String name : List.of("main.bbmodel", "blue.png", "orange.png")) {
            Files.copy(
                root.resolve("figura-v4").resolve(name), folder.resolve(name), StandardCopyOption.REPLACE_EXISTING);
        }
        Files.writeString(folder.resolve("avatar.json"), "{\"autoScripts\":[\"script\"]}");
        Files.writeString(folder.resolve("script.lua"), """
            ticks, sum = 0, 0
            assert(world.getTime() == 100 and world:getTimeOfDay() == 23999)
            events.tick = function()
              ticks = ticks + 1
              sum = sum + world.getTime()
            end
            events.render = function(delta)
              models.main.root:setPos(vec(world.getTime(delta)-100,0,0))
              day = world.getTimeOfDay(delta)
            end
            """);
        var config = new PreviewConfig();
        config.settings.enabled = false;
        config.configFile = root.resolve("figura-world-clock.json");
        config.scene.models = List.of(folder.toString());
        config.window.title = "omm-core Figura world clock";
        config.animation.enabled = true;
        config.animation.mode = "figura";
        config.animation.fixedTimeSeconds = 0.125f;
        config.scripts.enabled = true;
        config.states = JsonParser.parseString("{\"world\":{\"time\":100,\"dayTime\":23999}}").getAsJsonObject();
        config.recording.outputDirectory = "runs";
        Files.writeString(config.configFile, PreviewConfig.JSON.toJson(config));
        var mesh = PreviewMesh.load(config);
        var shared = new PreviewAnimation(mesh, config);
        var state = shared.newState();
        state.sample(0, 0.125f);
        state.updateBones();
        point(mesh, state, 2.5f / 16);
        state.lua().evaluate("""
            assert(ticks == 2 and sum == 203 and day == 24001.5)
            assert(world.getTime() == 102 and world:getTime(0.5) == 102.5)
            assert(math.lerp(2,6,0.25) == 3 and math.clamp(5,0,3) == 3)
            assert(math.map(5,0,10,0,100) == 50 and math.round(1.5) == 2)
            assert(math.sign(-3) == -1 and math.lerpAngle(350,10,0.5) == 0)
            assert(math.lerp(vec(0,0,0),vec(4,8,12),0.5) == vec(2,4,6))
            """);
        state.sample(1, 0.25f);
        state.updateBones();
        point(mesh, state, 5f / 16);
        state.sample(0, 0.125f);
        state.updateBones();
        point(mesh, state, 2.5f / 16);
        state.lua().evaluate("assert(ticks == 2 and sum == 203)");
        var isolated = shared.newState();
        isolated.sample(0, 0);
        isolated.updateBones();
        point(mesh, isolated, 0);
        var defaults = JsonParser.parseString("{}").getAsJsonObject();
        var lua =
            new PreviewLua(new PreviewLua.Program(Map.of(), List.of()), defaults, 0, new PreviewMolang(defaults, 0));
        lua.initialize();
        lua.frame(0.125f);
        lua.evaluate("assert(world.getTime() == 2 and world.getTimeOfDay(0.5) == 2.5)");
        try {
            var bad = JsonParser.parseString("{\"world\":{\"time\":1.5}}").getAsJsonObject();
            new PreviewLua(new PreviewLua.Program(Map.of(), List.of()), bad, 0, new PreviewMolang(bad, 0));
            throw new AssertionError("Fractional base tick count was accepted");
        } catch (org.luaj.vm2.LuaError expected) {
            if (!expected.getMessage().contains("states.world.time"))
                throw expected;
        }
        System.out.println("World clock PASS: original tick+delta/day semantics, math.lua, actual palette, seek and "
            + "instance isolation");
    }

    private static void point(PreviewMesh mesh, PreviewAnimation.State state, float x) {
        Vector3f bind = new Vector3f(mesh.vertices[0], mesh.vertices[1], mesh.vertices[2]);
        Vector3f actual = state.palette[mesh.owners[0]].transformPosition(new Vector3f(bind));
        if (actual.distance(bind.add(x, 0, 0)) > 0.0003f)
            throw new AssertionError("World clock did not drive actual pose: " + actual);
    }
}
