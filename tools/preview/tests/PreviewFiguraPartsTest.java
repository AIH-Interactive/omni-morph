package top.aihmc.omm.preview;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import org.joml.Vector3f;

/** Model scripts and wheel callbacks must transform real per-vertex owner palettes. */
public final class PreviewFiguraPartsTest {
    public static void main(String[] args) throws Exception {
        Path root = Path.of(args[0]).toAbsolutePath();
        Path folder = root.resolve("figura-scripted-parts");
        Files.createDirectories(folder);
        for (String name : List.of("main.bbmodel", "blue.png", "orange.png")) {
            Files.copy(
                root.resolve("figura-v4").resolve(name), folder.resolve(name), StandardCopyOption.REPLACE_EXISTING);
        }
        Files.writeString(folder.resolve("avatar.json"), "{\"autoScripts\":[\"script\"]}");
        Files.writeString(folder.resolve("script.lua"), """
            local root = models.main.root
            avatar_controls:newNumber('offset'):range(0,64,1):setDefault(16)
            local page = action_wheel:newPage('Figura 动作')
            action_wheel:setPage(page)
            page:newAction(1):title('移动模型'):onLeftClick(function() avatar_controls:set('offset',32) end)
            page:newAction(2):title('模型设置'):controlsPage('root')
            events.render = function(delta)
              root:setPos(vec(avatar_controls:get('offset'),0,0))
            end
            """);
        var config = new PreviewConfig();
        config.settings.enabled = false;
        config.vanilla.enabled = false;
        config.configFile = root.resolve("figura-scripted-parts.json");
        config.window.title = "omm-core Figura scripted parts";
        config.scene.models = List.of(folder.toString());
        config.animation.enabled = true;
        config.animation.mode = "figura";
        config.scripts.enabled = true;
        config.interaction.enabled = true;
        config.recording.outputDirectory = "runs";
        Files.writeString(config.configFile, PreviewConfig.JSON.toJson(config));
        var mesh = PreviewMesh.load(PreviewConfig.load(new String[] {"--config", config.configFile.toString()}));
        var shared = new PreviewAnimation(mesh, config);
        var state = shared.newState();
        state.sample(0, 0);
        state.updateBones();
        transformed(mesh, state, 1, 0, 0, "Initial setting must translate the cube in actual owner palette");
        state.lua().wheelState(true, 0);
        state.lua().executeWheel(0, true);
        state.sample(1, 0.1f);
        state.updateBones();
        transformed(mesh, state, 2, 0, 0, "Wheel callback must change model pose");
        var isolated = shared.newState();
        isolated.sample(0, 0);
        isolated.updateBones();
        transformed(mesh, isolated, 1, 0, 0, "Instances must not share model-part or setting state");
        state.lua().evaluate("models.main.root:setVisible(false)");
        state.updateBones();
        for (int owner : mesh.owners) {
            if (state.palette[owner].m00() != 0) {
                throw new AssertionError("Hidden parent did not hide descendant geometry");
            }
        }
        state.sample(0, 0);
        state.updateBones();
        transformed(mesh, state, 1, 0, 0, "Rewind must rebuild Lua and part state");
        state.lua().evaluate("models.main.root:setRot(0,0,0):setScale(2)");
        state.updateBones();
        Vector3f corner = state.palette[mesh.owners[0]].transformPosition(
            new Vector3f(mesh.vertices[0], mesh.vertices[1], mesh.vertices[2]));
        if (corner.distance(new Vector3f(3, 0, 0)) > 0.0002f) {
            throw new AssertionError("setRot must replace bind rotation; uniform scale must pivot locally: " + corner);
        }
        System.out.println(
            "Figura parts PASS: original hierarchy, script/settings/wheel -> actual palette, visibility and isolation");
    }

    private static void transformed(
        PreviewMesh mesh, PreviewAnimation.State state, float x, float y, float z, String label) {
        Vector3f bind = new Vector3f(mesh.vertices[0], mesh.vertices[1], mesh.vertices[2]);
        Vector3f actual = state.palette[mesh.owners[0]].transformPosition(new Vector3f(bind));
        if (actual.distance(bind.add(x, y, z)) > 0.0002f) {
            throw new AssertionError(label + ": " + actual);
        }
    }
}
