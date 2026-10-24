package top.aihmc.omnimorph.lua.api.particle;

import net.minecraft.world.phys.Vec3;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import top.aihmc.omnimorph.model.outfit.OutfitLimits;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

final class ParticleLightFieldSmokeTest {
    static void run() {
        Object world = new Object();
        int sky = 8 << 20;
        var sources = new ArrayList<>(List.of(new ParticleLightField.Source(Vec3.ZERO, 15, 8)));
        var snapshot = new ParticleLightField.Snapshot(world, sources);
        sources.clear();
        require(snapshot.apply(world, 0, 0, 0, sky) == (sky | 240), "source center illuminates block channel and retains sky");
        require(snapshot.apply(world, 4, 0, 0, sky) == (sky | 128), "linear falloff is rounded to native block levels");
        require(snapshot.apply(world, 8, 0, 0, sky) == sky && snapshot.apply(world, 100, 0, 0, sky) == sky,
                "radius boundary and unrelated sections retain native light");
        require(snapshot.apply(new Object(), 0, 0, 0, sky) == sky, "worlds are compared by identity");
        require(snapshot.apply(world, 4, 0, 0, sky | 208) == (sky | 208), "dynamic light never darkens a brighter native block");
        require(snapshot.apply(world, Double.NaN, 0, 0, sky) == sky, "invalid probes never affect lighting");
        var negative = new ParticleLightField.Snapshot(world,
                List.of(new ParticleLightField.Source(new Vec3(-16.5, -.5, -.5), 10, 2)));
        require(negative.apply(world, -16.5, -.5, -.5, 0) == 160, "negative section coordinates preserve source lookup");
        var overlapping = new ParticleLightField.Snapshot(world, List.of(
                new ParticleLightField.Source(Vec3.ZERO, 4, 8), new ParticleLightField.Source(Vec3.ZERO, 15, 8)));
        require(overlapping.apply(world, 0, 0, 0, 0) == 240, "overlap uses maximum light rather than unbounded addition");
        require(snapshot.luminanceAt(0, 0, 0) == 15.0 && snapshot.luminanceAt(4, 0, 0) == 7.5,
                "continuous luminance matches the falloff shared with the LambDynamicLights bridge");
        require(snapshot.luminanceAt(8, 0, 0) == 0.0 && snapshot.luminanceAt(Double.NaN, 0, 0) == 0.0,
                "radius boundary and invalid probes emit no continuous light");
        require(overlapping.luminanceAt(0, 0, 0) == 15.0, "bridge overlap uses the maximum source");
        var sections = new HashSet<ParticleLightField.Section>();
        new ParticleLightField.Source(new Vec3(.5, .5, .5), 15, 32).sections(sections::add);
        require(sections.size() <= 216 && sections.contains(new ParticleLightField.Section(0, 0, 0)),
                "maximum-radius invalidation and spatial indexing remain bounded");
        sections.clear();
        new ParticleLightField.Source(Vec3.ZERO, 0, 32).sections(sections::add);
        require(sections.isEmpty(), "zero-level lights require no terrain rebuilds");
        reject(() -> new ParticleLightField.Source(Vec3.ZERO, 16, 1));
        reject(() -> new ParticleLightField.Source(Vec3.ZERO, 1, Double.POSITIVE_INFINITY));
        var defaults = ParticleLightSettings.defaults();
        require(!defaults.enabled() && defaults.level() == 15 && defaults.radius() == 7.5, "independent light defaults");
        var alias = new LuaTable();
        alias.set("level", 8);
        alias.set("radius", LuaValue.valueOf(4));
        var enabled = defaults.alias(alias);
        enabled.validate(OutfitLimits.defaults());
        alias.set("level", 1);
        require(enabled.enabled() && enabled.level() == 8 && enabled.radius() == 4, "alias table is copied and enables one light");
        alias.set("level", LuaValue.valueOf(2.5));
        reject(() -> defaults.alias(alias));
        alias.set("level", LuaValue.valueOf("8"));
        reject(() -> defaults.alias(alias));
        require(!enabled.alias(LuaValue.FALSE).enabled(), "boolean alias disables the same source");
        System.out.println("PASS particle light field: immutable section lookup, radius/falloff/max, native sky, "
                + "world isolation, bounded invalidation and strict independent settings; scene rendering separate");
    }

    private static void reject(Runnable action) {
        try {
            action.run();
        } catch (IllegalArgumentException | LuaError expected) {
            return;
        }
        throw new AssertionError("Expected invalid light settings to be rejected");
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
