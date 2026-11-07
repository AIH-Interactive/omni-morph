package top.aihmc.omm.preview;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;

/** Real Molang events must emit, tick and render particles even when no Lua VM is requested. */
public final class PreviewYsmParticleTest {
    private static void near(double actual, double expected) {
        if (Math.abs(actual - expected) > 0.00001)
            throw new AssertionError(actual + " != " + expected);
    }

    private static float eval(PreviewMolang host, String expression) throws Exception {
        return host.evaluate(host.compile(expression));
    }

    public static void main(String[] args) throws Exception {
        Path root = Path.of(args[0]).toAbsolutePath();
        Path folder = root.resolve("ysm-particles");
        Files.createDirectories(folder);
        for (String name : List.of("main.json", "texture.png", "ysm.json")) {
            Files.copy(
                root.resolve("ysm-functions").resolve(name), folder.resolve(name), StandardCopyOption.REPLACE_EXISTING);
        }
        Files.writeString(folder.resolve("main.animation.json"), """
            {"animations":{"still":{"loop":true,"animation_length":2,
              "timeline":{"0":"ysm.abs_particle('minecraft:firework',0,1,0,0,0,0,0,0,100);"},
              "bones":{"root":{"position":[0,0,0]}}
            }}}
            """);
        Files.writeString(folder.resolve("controller.animation.json"), """
            {"animation_controllers":{"controller.player.main":{"states":{
              "default":{"animations":["still"]}
            }}}}
            """);
        var config = new PreviewConfig();
        config.configFile = root.resolve("ysm-particles.json");
        config.scene.models = List.of(folder.toString());
        config.settings.enabled = false;
        config.animation.enabled = true;
        config.animation.mode = "controller";
        config.animation.fixedTimeSeconds = 0.1f;
        config.recording.outputDirectory = "runs";
        Files.writeString(config.configFile, PreviewConfig.JSON.toJson(config));
        try (var animation = new PreviewAnimation(PreviewMesh.load(config), config)) {
            var state = animation.newState();
            state.sample(0, 0);
            if (state.lua() != null || state.particles().active().size() != 1)
                throw new AssertionError("No-Lua controller event did not emit a real particle");
            state.sample(1, 0.1f);
            if (state.particles().active().getFirst().age != 2)
                throw new AssertionError("No-Lua controller particles did not tick at 20 Hz");
            var other = animation.newState();
            other.sample(0, 0);
            if (other.particles() == state.particles() || other.particles().active().getFirst().age != 0)
                throw new AssertionError("Particle state leaked between model instances");
            var previous = state.particles();
            state.sample(2, 0);
            if (!previous.active().isEmpty() || state.particles().active().size() != 1
                || state.particles().active().getFirst().age != 0)
                throw new AssertionError("Seek did not close and reconstruct the particle owner");
            try (var firstScope = state.molang().functions.beginClientEffects(null);
                var secondScope = other.molang().functions.beginClientEffects(null)) {
                String burst = "ysm.abs_particle('firework',0,1,0,0.5,0.5,0.5,0.2,3,10);";
                eval(state.molang(), burst);
                eval(other.molang(), burst);
                for (int index = 1; index <= 3; index++) {
                    var first = state.particles().active().get(index);
                    var second = other.particles().active().get(index);
                    near(first.position.distance(second.position), 0);
                    near(first.velocity.distance(second.velocity), 0);
                    if (first.velocity.lengthSquared() == 0)
                        throw new AssertionError("Gaussian batch did not apply its speed");
                }
            }
        }

        JsonObject inputs = JsonParser.parseString("""
            {"player":{"pos":[10,20,30],"yaw":180,"bodyYaw":90}}
            """).getAsJsonObject();
        var host = new PreviewMolang(inputs, 42);
        try (var audio = new PreviewAudio();
            var effects = new PreviewYsmEffects(audio, null, host, PreviewYsmResources.EMPTY, inputs, 42)) {
            host.effects = effects;
            eval(host, "ysm.particle('firework');");
            if (effects.particles.particles.spawned() != 0)
                throw new AssertionError("Particle query bypassed the client-effect scope");
            try (var scope = host.functions.beginClientEffects(null)) {
                near(eval(host, "ysm.particle('firework',1,2,3,2,0,0,0.5,0,5);"), 1);
                var single = effects.particles.particles.active().getFirst();
                near(single.position.x, 7);
                near(single.position.y, 22);
                near(single.position.z, 31);
                near(single.velocity.x, 1);
                near(single.velocity.z, 0);
                near(single.lifetime, 5);
                eval(host, "ysm.particle('firework',1,2,3,0,0,0,0,1,5);");
                var batch = effects.particles.particles.active().get(1);
                near(batch.position.x, 9);
                near(batch.position.y, 22);
                near(batch.position.z, 27);
                eval(host, "ysm.abs_particle('firework',1,2,3,0,0,0,0,0,5);");
                var absolute = effects.particles.particles.active().get(2);
                near(absolute.position.x, 11);
                near(absolute.position.z, 33);
                near(eval(host, "ysm.particle('');"), 0);
                try {
                    eval(host, "ysm.particle('firework',0,0,0,0,0,0,0,1025);");
                    throw new AssertionError("Excessive particle count was accepted");
                } catch (IllegalArgumentException expected) {
                    if (!expected.getMessage().contains("budget"))
                        throw expected;
                }
                try {
                    eval(host, "ysm.particle('unsupported:particle');");
                    throw new AssertionError("Unknown particle silently succeeded");
                } catch (UnsupportedOperationException expected) {
                    // The standalone registry must not silently replace unsupported effects.
                }
            }
            effects.particles.advance(0.3f);
            if (!effects.particles.particles.active().isEmpty())
                throw new AssertionError("Explicit YSM particle lifetime was not honored");
            try (var lua =
                     new PreviewLua(new PreviewLua.Program(java.util.Map.of(), List.of()), inputs, 42, host, audio)) {
                if (lua.particles != effects.particles.particles)
                    throw new AssertionError("Lua and YSM did not share their model's actual particle simulation");
            }
        }
        System.out.println("YSM particles PASS: real controller/no-Lua emission, single/batch yaw, absolute offsets, "
            + "velocity/lifetime, effect gate, budget, shared Lua state and owner/seek isolation");
    }
}
