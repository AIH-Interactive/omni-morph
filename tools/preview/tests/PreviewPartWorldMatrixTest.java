package top.aihmc.omm.preview;

import com.google.gson.JsonArray;
import com.google.gson.JsonParser;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import org.joml.Vector3f;

/** Figura world queries must report actual last-submitted hierarchical poses in block units. */
public final class PreviewPartWorldMatrixTest {
    public static void main(String[] args) throws Exception {
        Path root = Path.of(args[0]).toAbsolutePath();
        Path folder = root.resolve("figura-part-world");
        Files.createDirectories(folder);
        for (String name : List.of("blue.png", "orange.png")) {
            Files.copy(
                root.resolve("figura-v4").resolve(name), folder.resolve(name), StandardCopyOption.REPLACE_EXISTING);
        }
        var model = JsonParser.parseString(Files.readString(root.resolve("figura-v4/main.bbmodel"))).getAsJsonObject();
        JsonArray pivot = new JsonArray();
        pivot.add(8);
        pivot.add(8);
        pivot.add(8);
        model.getAsJsonArray("elements").get(0).getAsJsonObject().add("origin", pivot);
        Files.writeString(folder.resolve("main.bbmodel"), model.toString());
        Files.writeString(folder.resolve("avatar.json"), "{\"autoScripts\":[\"script\"]}");
        Files.writeString(folder.resolve("script.lua"), """
            models.main.root:setPos(vec(16,0,0))
            models.main.root.cube:setScale(2)
            events.post_render = function()
              local origin = models.main.root.cube:partToWorldMatrix():apply()
              assert(math.abs(origin.x - (models.main.root:getPos().x/16-0.5)) < 0.0001)
            end
            """);
        var config = new PreviewConfig();
        config.settings.enabled = false;
        config.configFile = root.resolve("figura-part-world.json");
        config.window.title = "omm-core Figura part world matrix";
        config.scene.models = List.of(folder.toString());
        config.animation.enabled = true;
        config.animation.mode = "figura";
        config.scripts.enabled = true;
        config.vanilla.enabled = false;
        config.recording.outputDirectory = "runs";
        Files.writeString(config.configFile, PreviewConfig.JSON.toJson(config));
        var mesh = PreviewMesh.load(config);
        var shared = new PreviewAnimation(mesh, config);
        var state = shared.newState();
        state.sample(0, 0);
        state.updateBones();
        state.postRender(0);
        state.lua().evaluate("""
            local matrix = models.main.root.cube:partToWorldMatrix()
            local origin = matrix:apply()
            assert((origin-vec(0.5,0.5,0.5)):length() < 0.0001)
            local point = matrix:apply(vec(8,-8,-8))
            assert((point-vec(1.5,1.5,-0.5)):length() < 0.0001)
            matrix:translate(100,100,100)
            assert((models.main.root.cube:partToWorldMatrix():apply()-origin):length() < 0.0001)
            """);
        Vector3f rendered = state.palette[mesh.owners[0]].transformPosition(
            new Vector3f(mesh.vertices[0], mesh.vertices[1], mesh.vertices[2]));
        if (rendered.distance(new Vector3f(1.5f, 1.5f, -0.5f)) > 0.0001f) {
            throw new AssertionError("World query disagrees with actual submitted vertex: " + rendered);
        }
        state.lua().evaluate("models.main.root:setPos(32,0,0)");
        // Original API returns the last submitted matrix, not a live evaluation of unrendered changes.
        state.lua().evaluate("assert(math.abs(models.main.root.cube:partToWorldMatrix():apply().x-0.5) < 0.0001)");
        state.updateBones();
        state.lua().evaluate("assert(math.abs(models.main.root.cube:partToWorldMatrix():apply().x-1.5) < 0.0001)");
        var isolated = shared.newState();
        isolated.sample(0, 0);
        isolated.updateBones();
        isolated.lua().evaluate("assert(math.abs(models.main.root.cube:partToWorldMatrix():apply().x-0.5) < 0.0001)");
        System.out.println("Part world matrix PASS: pivot/parent/scale, pixel-to-block conversion, submitted vertex "
            + "parity and copy/instance isolation");
    }
}
