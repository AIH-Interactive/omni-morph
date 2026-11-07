package top.aihmc.omm.preview;

import com.google.gson.JsonObject;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Map;

/** Original Figura particle methods must mutate and spawn independent, advancing real particle state. */
public final class PreviewParticleTest {
    private static PreviewLua host(String source, JsonObject states) throws Exception {
        var lua = new PreviewLua(new PreviewLua.Program(Map.of("script", source), List.of("script")), states, 42,
            new PreviewMolang(states, 42));
        lua.initialize();
        return lua;
    }

    public static void main(String[] args) throws Exception {
        var state = new JsonObject();
        var lua = host("""
            assert(particles:isPresent('minecraft:firework'))
            assert(not particles:isPresent('unknown'))
            assert(not particles:isPresent(nil))
            local ok = pcall(function() return particles['unknown'] end)
            assert(not ok)
            p = particles['firework']:pos(vec(0,1,0)):velocity(1,0.5,-0.25)
                :color(vec(0.2,0.4,0.6,0.8)):scale(2):lifetime(100):gravity(0):physics(false)
            assert(p:getScale() == 2 and p:getSize() == 2 and p:getLifetime() == 100)
            assert(not p:hasPhysics() and p:getGravity() == 0 and p:isAlive())
            assert((p:getColor()-vec(0.2,0.4,0.6,0.8)):length() < 0.0001)
            local copy = p:getPos()
            copy.x = 99
            assert(p:getPos().x == 0)
            p:spawn():spawn()
            """, state);
        if (lua.particles.active().size() != 1 || lua.particles.spawned() != 1) {
            throw new AssertionError("Repeated spawn duplicated a single mutable particle");
        }
        lua.advanceTicks(0.05f);
        lua.evaluate("""
            assert((p:getPos()-vec(1,1.5,-0.25)):length() < 0.0001)
            assert((p:getVelocity()-vec(0.91,0.455,-0.2275)):length() < 0.0001)
            p:remove()
            assert(not p:isAlive())
            """);
        lua.advanceTicks(0.1f);
        if (!lua.particles.active().isEmpty())
            throw new AssertionError("Removed particle remained active");
        lua.evaluate("""
            local p = particles:newParticle('firework',vec(1,2,3),vec(4,5,6))
            assert((p:getPos()-vec(1,2,3)):length() < 0.0001)
            local q = particles:newParticle('firework',1,2,3,vec(4,5,6))
            local r = particles:newParticle('firework',vec(1,2,3),4,5,6)
            local s = particles:newParticle('firework',1,2,3,4,5,6)
            for _, v in ipairs({p,q,r,s}) do
              assert((v:getVelocity()-vec(4,5,6)):length() < 0.0001)
              v:power(2)
              assert(v:getPower() == 2 and math.abs(v:getVelocity().y-9.9) < 0.0001)
            end
            particles:removeParticles()
            """);
        var empty = host("", state);
        if (empty.particles.spawned() != 0)
            throw new AssertionError("Instance particle state leaked");
        var floor = host("p = particles['firework']:pos(0,0.1,0):velocity(0,-1,0):gravity(0):spawn()", state);
        floor.particles.ground(0);
        floor.advanceTicks(0.05f);
        floor.evaluate("assert(p:getPos().y == 0 and p:getVelocity().y == 0)");
        var air = host("p = particles['bubble_column_up']:spawn()", state);
        air.advanceTicks(0.05f);
        air.evaluate("assert(not p:isAlive())");
        var wet = new JsonObject();
        var environment = new JsonObject();
        environment.addProperty("water", true);
        wet.add("particles", environment);
        var water = host("p = particles['bubble_column_up']:velocity(0,0,0):spawn()", wet);
        water.advanceTicks(0.05f);
        water.evaluate("assert(p:isAlive() and p:getPos().y > 0)");
        var expiry = host("p = particles['firework']:lifetime(0):spawn()", state);
        expiry.advanceTicks(0.05f);
        expiry.evaluate("assert(not p:isAlive())");
        String all = """
            for _,id in ipairs({'cherry_leaves','firework','campfire_signal_smoke',
                                'bubble_column_up','dragon_breath','squid_ink'}) do
              particles[id]:pos(0,1,0):lifetime(100):spawn()
            end
            """;
        var a = host(all, wet);
        var b = host(all, wet);
        a.advanceTicks(0.5f);
        b.advanceTicks(0.5f);
        for (int i = 0; i < 6; i++) {
            var pa = a.particles.active().get(i);
            var pb = b.particles.active().get(i);
            if (!pa.position.equals(pb.position) || !pa.velocity.equals(pb.velocity)) {
                throw new AssertionError("Fixed-seed particle simulation differs: " + pa.type);
            }
        }
        a.evaluate("particles:removeParticles()");
        if (!a.particles.active().isEmpty() || b.particles.active().size() != 6) {
            throw new AssertionError("Owner clear crossed instance boundary");
        }

        Path root = Path.of(args[0]).toAbsolutePath();
        Path folder = root.resolve("figura-particles");
        Files.createDirectories(folder);
        for (String name : List.of("blue.png", "orange.png", "main.bbmodel")) {
            Files.copy(
                root.resolve("figura-v4").resolve(name), folder.resolve(name), StandardCopyOption.REPLACE_EXISTING);
        }
        Files.writeString(folder.resolve("avatar.json"), "{\"autoScripts\":[\"script\"]}");
        Files.writeString(folder.resolve("script.lua"), """
            events.entity_init = function()
              for i,id in ipairs({'cherry_leaves','firework','campfire_signal_smoke',
                                 'bubble_column_up','dragon_breath','squid_ink'}) do
                particles[id]:pos((i-3)*0.3,0.8,-0.2):velocity(0,0,0):physics(false)
                    :gravity(0):color(1,0.4,0.7):scale(0.5):lifetime(100):spawn()
              end
            end
            """);
        var config = new PreviewConfig();
        config.settings.enabled = false;
        config.configFile = root.resolve("figura-particles.json");
        config.window.title = "omm-core six Figura particle sprites";
        config.scene.models = List.of(folder.toString());
        config.animation.enabled = true;
        config.animation.mode = "figura";
        config.animation.fixedTimeSeconds = 0.25f;
        config.scripts.enabled = true;
        config.vanilla.enabled = false;
        config.states = wet;
        config.recording.outputDirectory = "runs";
        Files.writeString(config.configFile, PreviewConfig.JSON.toJson(config));
        var mesh = PreviewMesh.load(config);
        var animated = new PreviewAnimation(mesh, config).newState();
        animated.sample(1, 1);
        animated.sample(2, 0.25f);
        if (animated.lua().particles.spawned() != 6 || animated.lua().particles.active().size() != 6) {
            throw new AssertionError("Backward seek did not rebuild isolated particle state");
        }
        animated.lua().evaluate("floorProbe = particles['firework']:pos(0," + (mesh.minimumY + 0.01f)
            + ",0):velocity(0,-1,0):gravity(0):spawn()");
        animated.sample(3, 0.3f);
        animated.lua().evaluate("assert(math.abs(floorProbe:getPos().y-" + mesh.minimumY + ") < 0.0001)");
        System.out.println(
            "Particles PASS: original Lua API, actual motion/expiry/ground/water, seed, seek and owner isolation");
    }
}
