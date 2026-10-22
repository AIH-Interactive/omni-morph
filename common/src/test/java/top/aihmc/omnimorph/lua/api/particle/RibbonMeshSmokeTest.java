package top.aihmc.omnimorph.lua.api.particle;

import com.google.gson.Gson;
import net.minecraft.world.phys.Vec3;
import top.aihmc.omnimorph.model.outfit.OutfitLimits;
import top.aihmc.omnimorph.model.outfit.TrailBuffer;

import java.util.ArrayList;
import java.util.List;

/** World coordinates, clipping and atlas-safe UVs only; native ribbon rendering is checked separately. */
public final class RibbonMeshSmokeTest {
    public static void run() {
        stablePathDistance();
        repeatAndDensity();
        clippingAndFade();
        stretchAndDegenerateDirections();
        rotation();
        budgetAndInvalidInput();
        System.out.println("PASS ribbon geometry: stable arc phase, atlas seam splitting, density, length/time clipping, "
                + "taper/fade, sharp/parallel directions and layered vertex preflight; native drawing pending");
    }

    private static void stablePathDistance() {
        var json = new Gson().toJsonTree(OutfitLimits.defaults()).getAsJsonObject();
        json.addProperty("maxTrailSamples", 3);
        var buffer = new TrailBuffer(new Gson().fromJson(json, OutfitLimits.class));
        var policy = new TrailBuffer.Policy(0, 1, 100, 8);
        Object world = new Object();
        for (int index = 0; index <= 2; index++) {
            buffer.sample(index, world, "tip", index, new Vec3(index, 0, 0), policy);
        }
        var retained = buffer.snapshotPath();
        buffer.sample(3, world, "tip", 3, new Vec3(3, 0, 0), policy);
        near(buffer.snapshotPath().getFirst().distance(), 1);
        near(buffer.snapshotPath().getLast().distance(), 3);
        near(retained.getFirst().distance(), 0);
        buffer.expire(102);
        near(buffer.snapshotPath().getFirst().distance(), 3);
        buffer.sample(4, world, "tip", 103, new Vec3(4, 0, 0), policy);
        near(buffer.snapshotPath().getLast().distance(), 4);
        buffer.sample(5, world, "hip", 104, new Vec3(4.5, 0, 0), policy);
        near(buffer.snapshotPath().getFirst().distance(), 0);
        require(retained.size() == 3, "distance snapshots survive expiry and target reset");
    }

    private static void repeatAndDensity() {
        var settings = settings(100, 100, 1, 0, false, false, RibbonMesh.UvMode.REPEAT);
        var coarse = build(straight(0, 1, 3), 3, settings, 1, 4096);
        require(coarse.quads().size() == 3 && coarse.submittedVertices() == 12,
                "three repeats split into three bounded atlas-safe quads");
        for (int index = 0; index < coarse.quads().size(); index++) {
            var quad = coarse.quads().get(index);
            near(quad.tailLeft().u(), 0);
            near(quad.headLeft().u(), 1);
            near(quad.tailLeft().position().distanceTo(quad.tailRight().position()), 2);
            if (index > 0) {
                require(coarse.quads().get(index - 1).headLeft().position().equals(quad.tailLeft().position())
                                && coarse.quads().get(index - 1).headRight().position().equals(quad.tailRight().position()),
                        "segment and UV seam joins reuse exactly the same world edges");
            }
        }
        var dense = build(straight(0, .25, 1.5, 3), 3, settings, 1, 4096);
        near(phaseAt(coarse, 2.25), .25);
        near(phaseAt(dense, 2.25), .25);
        var offset = build(straight(0, 1, 3), 3,
                settings(100, 100, .5, .25, false, false, RibbonMesh.UvMode.REPEAT), 1, 4096);
        near(phaseAt(offset, 2.25), .375);
    }

    private static void clippingAndFade() {
        var path = straight(0, 2, 4);
        var taper = build(path, 4, settings(1.5, 100, 1, 0, true, false, RibbonMesh.UvMode.REPEAT), 1, 4096);
        near(center(taper.quads().getFirst().tailLeft(), taper.quads().getFirst().tailRight()).x, 2.5);
        near(taper.quads().getFirst().tailLeft().position().distanceTo(taper.quads().getFirst().tailRight().position()), 0);
        near(taper.quads().getLast().headLeft().position().distanceTo(taper.quads().getLast().headRight().position()), 2);
        var fade = build(path, 4, settings(100, 1.5, 1, 0, false, true, RibbonMesh.UvMode.REPEAT), 1, 4096);
        near(center(fade.quads().getFirst().tailLeft(), fade.quads().getFirst().tailRight()).x, 2.5);
        near(fade.quads().getFirst().tailLeft().alpha(), 0);
        near(fade.quads().getLast().headLeft().alpha(), 1);
        var partialHead = build(path, 2.5, settings(100, 100, 1, 0, false, false, RibbonMesh.UvMode.REPEAT), 1, 4096);
        near(center(partialHead.quads().getLast().headLeft(), partialHead.quads().getLast().headRight()).x, 2.5);
        var expired = build(path, 20, settings(100, 1, 1, 0, false, true, RibbonMesh.UvMode.REPEAT), 1, 4096);
        require(expired.quads().isEmpty(), "expired trajectories emit no geometry");
    }

    private static void stretchAndDegenerateDirections() {
        var stretch = build(straight(0, 1, 3), 3,
                settings(100, 100, 99, 0, false, false, RibbonMesh.UvMode.STRETCH), 1, 4096);
        require(stretch.quads().size() == 2, "stretch ignores repeat density and spans one texture interval");
        near(stretch.quads().getFirst().tailLeft().u(), 0);
        near(stretch.quads().getLast().headLeft().u(), 1);
        var settings = settings(100, 100, 1, -.25, false, false, RibbonMesh.UvMode.REPEAT);
        for (List<TrailBuffer.PathSample> path : List.of(
                points(Vec3.ZERO, new Vec3(1, 0, 0), Vec3.ZERO),
                points(Vec3.ZERO, new Vec3(1, 0, 0), new Vec3(1, 1, 0), new Vec3(0, 1, 0)),
                points(Vec3.ZERO, Vec3.ZERO, new Vec3(1, 0, 0)))) {
            var mesh = RibbonMesh.build(path, 4, new Vec3(10, 0, 0), new Vec3(1, 0, 0), settings,
                    OutfitLimits.defaults(), 1, 4096);
            require(!mesh.quads().isEmpty(), "U-turn, acute/parallel view and duplicate points remain drawable");
            for (int index = 1; index < mesh.quads().size(); index++) {
                require(mesh.quads().get(index - 1).headLeft().position().equals(mesh.quads().get(index).tailLeft().position()),
                        "sharp turns and UV subdivisions do not create disconnected edges");
            }
        }
        require(build(points(Vec3.ZERO, Vec3.ZERO), 1, settings, 1, 4096).quads().isEmpty(),
                "fully degenerate history emits no NaN strip");
    }

    private static void budgetAndInvalidInput() {
        var settings = settings(100, 100, 1, 0, false, false, RibbonMesh.UvMode.REPEAT);
        var one = straight(0, 1);
        require(build(one, 1, settings, 2, 8).submittedVertices() == 8
                        && build(one, 1, settings, 2, 8).submittedFaces() == 2,
                "vertex and face cost includes every texture layer");
        reject(() -> build(one, 1, settings, 2, 7), "RIBBON_VERTEX_LIMIT");
        reject(() -> build(one, 1, settings, 1, 0), "RIBBON_VERTEX_LIMIT");
        require(RibbonMesh.build(one, 1, new Vec3(0, 0, 10), new Vec3(1, 0, 0), settings,
                OutfitLimits.defaults(), 2, 8, 2).submittedFaces() == 2,
                "complete layered geometry fits the exact remaining face allowance");
        reject(() -> RibbonMesh.build(one, 1, new Vec3(0, 0, 10), new Vec3(1, 0, 0), settings,
                OutfitLimits.defaults(), 2, 8, 1), "RIBBON_FACE_LIMIT");
        reject(() -> RibbonMesh.build(one, 1, new Vec3(0, 0, 10), new Vec3(1, 0, 0), settings,
                OutfitLimits.defaults(), 1, 4096, 0), "RIBBON_FACE_LIMIT");
        reject(() -> RibbonMesh.build(one, 1, new Vec3(0, 0, 10), new Vec3(1, 0, 0), settings,
                OutfitLimits.defaults(), 1, 4096, -1), "RIBBON_FACE_LIMIT");
        reject(() -> build(straight(0, 3), 3,
                settings(100, 100, 1e9, 0, false, false, RibbonMesh.UvMode.REPEAT), 1, 4096), "RIBBON_VERTEX_LIMIT");
        reject(() -> new RibbonMesh.Settings(0, 1, 1, 1, 0, false, false, RibbonMesh.UvMode.REPEAT),
                "INVALID_RIBBON_SETTINGS");
        var reversed = new ArrayList<>(one);
        java.util.Collections.reverse(reversed);
        reject(() -> build(reversed, 1, settings, 1, 4096), "INVALID_RIBBON_PATH");
        var invalid = List.of(new TrailBuffer.PathSample(new TrailBuffer.Sample(Double.NaN, 0, 0, 0), 0));
        reject(() -> build(invalid, 1, settings, 1, 4096), "INVALID_RIBBON_POSITION");
    }

    private static void rotation() {
        var settings = settings(100, 100, 1, 0, false, false, RibbonMesh.UvMode.REPEAT);
        var turned = RibbonMesh.build(straight(0, 1, 3), 3, new Vec3(0, 0, 10), new Vec3(1, 0, 0), settings,
                OutfitLimits.defaults(), 1, 4096, 100, Math.PI / 2);
        var quad = turned.quads().getFirst();
        near(quad.tailLeft().position().y, 0);
        near(quad.tailLeft().position().z, -1);
        near(quad.tailRight().position().z, 1);
        near(quad.tailLeft().position().distanceTo(quad.tailRight().position()), 2);
        for (int index = 1; index < turned.quads().size(); index++) {
            require(turned.quads().get(index - 1).headLeft().position()
                            .equals(turned.quads().get(index).tailLeft().position()),
                    "rotated sections retain exact seam joins");
        }
        reject(() -> RibbonMesh.build(straight(0, 1), 1, new Vec3(0, 0, 10), new Vec3(1, 0, 0), settings,
                OutfitLimits.defaults(), 1, 4096, 100, Double.NaN), "INVALID_RIBBON_ROTATION");
    }

    private static RibbonMesh.Settings settings(double length, double lifetime, double repeat, double offset,
                                                 boolean taper, boolean fade, RibbonMesh.UvMode mode) {
        return new RibbonMesh.Settings(2, length, lifetime, repeat, offset, taper, fade, mode);
    }

    private static RibbonMesh.Mesh build(List<TrailBuffer.PathSample> path, double now,
                                          RibbonMesh.Settings settings, int layers, int vertices) {
        return RibbonMesh.build(path, now, new Vec3(0, 0, 10), new Vec3(1, 0, 0), settings,
                OutfitLimits.defaults(), layers, vertices);
    }

    private static List<TrailBuffer.PathSample> straight(double... x) {
        var path = new ArrayList<TrailBuffer.PathSample>();
        for (double value : x) {
            path.add(new TrailBuffer.PathSample(new TrailBuffer.Sample(value, 0, 0, value), value));
        }
        return path;
    }

    private static List<TrailBuffer.PathSample> points(Vec3... positions) {
        var path = new ArrayList<TrailBuffer.PathSample>();
        double distance = 0;
        for (int index = 0; index < positions.length; index++) {
            if (index > 0) {
                distance += positions[index].distanceTo(positions[index - 1]);
            }
            Vec3 point = positions[index];
            path.add(new TrailBuffer.PathSample(new TrailBuffer.Sample(point.x, point.y, point.z, index), distance));
        }
        return path;
    }

    private static Vec3 center(RibbonMesh.Vertex left, RibbonMesh.Vertex right) {
        return left.position().add(right.position()).scale(.5);
    }

    private static double phaseAt(RibbonMesh.Mesh mesh, double x) {
        for (var quad : mesh.quads()) {
            double start = center(quad.tailLeft(), quad.tailRight()).x;
            double end = center(quad.headLeft(), quad.headRight()).x;
            if (x >= start && x <= end) {
                double u = quad.tailLeft().u() + (quad.headLeft().u() - quad.tailLeft().u()) * (x - start) / (end - start);
                return u - Math.floor(u);
            }
        }
        throw new AssertionError("No ribbon segment at " + x);
    }

    private static void reject(Runnable action, String code) {
        try {
            action.run();
        } catch (IllegalArgumentException expected) {
            require(expected.getMessage().equals(code), "Expected " + code + ", got " + expected.getMessage());
            return;
        }
        throw new AssertionError("Invalid ribbon request accepted");
    }

    private static void near(double actual, double expected) {
        require(Math.abs(actual - expected) < 1e-6, "Ribbon value " + actual + " != " + expected);
    }

    private static void require(boolean condition, String reason) {
        if (!condition) {
            throw new AssertionError(reason);
        }
    }
}
