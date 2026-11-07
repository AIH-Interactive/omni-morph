package top.aihmc.omm.preview;

import com.google.gson.JsonParser;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import org.joml.Vector3f;

/** Configured Minecraft player queries must control real Figura model geometry without a game process. */
public final class PreviewPlayerAnimationTest {
    public static void main(String[] args) throws Exception {
        Path root = Path.of(args[0]).toAbsolutePath();
        Path folder = root.resolve("figura-player-simulation");
        Files.createDirectories(folder);
        for (String name : List.of("main.bbmodel", "blue.png", "orange.png")) {
            Files.copy(
                root.resolve("figura-v4").resolve(name), folder.resolve(name), StandardCopyOption.REPLACE_EXISTING);
        }
        Files.writeString(folder.resolve("avatar.json"), "{\"autoScripts\":[\"script\"]}");
        Files.writeString(folder.resolve("script.lua"), """
            avatar_controls:newToggle('running'):title('模拟奔跑'):setDefault(player:isSprinting())
            local page = action_wheel:newPage('玩家状态测试')
            action_wheel:setPage(page)
            function pings.stop_moving() avatar_controls:set('running',false) end
            page:newAction(1):title('停止移动'):onLeftClick(pings.stop_moving)
            events.render = function()
              local movement = avatar_controls:get('running') and player:getVelocity() * 16 or vec(0,0,0)
              models.main.root:setPos(movement)
            end
            """);
        var config = new PreviewConfig();
        config.settings.enabled = false;
        config.configFile = root.resolve("figura-player-simulation.json");
        config.scene.models = List.of(folder.toString());
        config.window.title = "omm-core configured Figura player state";
        config.animation.enabled = true;
        config.animation.mode = "figura";
        config.scripts.enabled = true;
        config.interaction.enabled = true;
        config.states =
            JsonParser.parseString("{\"player\":{\"sprinting\":true,\"velocity\":[0.25,0,0]}}").getAsJsonObject();
        config.recording.outputDirectory = "runs";
        Files.writeString(config.configFile, PreviewConfig.JSON.toJson(config));
        var mesh = PreviewMesh.load(config);
        var animation = new PreviewAnimation(mesh, config);
        var state = animation.newState();
        state.sample(0, 0);
        state.updateBones();
        displacement(mesh, state, 0.25f);
        state.lua().wheelState(true, 0);
        state.lua().executeWheel(0, true);
        state.sample(1, 0.1f);
        state.updateBones();
        displacement(mesh, state, 0);
        var other = animation.newState();
        other.sample(0, 0);
        other.updateBones();
        displacement(mesh, other, 0.25f);
        config.states.getAsJsonObject("player").addProperty("sprinting", false);
        var stopped = new PreviewAnimation(mesh, config).newState();
        stopped.sample(0, 0);
        stopped.updateBones();
        displacement(mesh, stopped, 0);
        System.out.println("Player simulation PASS: configured queries -> original vectors -> actual palette, local "
            + "ping/wheel and instance isolation");
    }

    private static void displacement(PreviewMesh mesh, PreviewAnimation.State state, float x) {
        Vector3f original = new Vector3f(mesh.vertices[0], mesh.vertices[1], mesh.vertices[2]);
        Vector3f actual = state.palette[mesh.owners[0]].transformPosition(new Vector3f(original));
        if (actual.distance(original.add(x, 0, 0)) > 0.0003f) {
            throw new AssertionError("Configured state did not change actual model geometry: " + actual);
        }
    }
}
