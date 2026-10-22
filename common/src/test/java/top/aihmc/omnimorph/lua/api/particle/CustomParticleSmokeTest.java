package top.aihmc.omnimorph.lua.api.particle;

import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;

/** CPU checks for the production sprite timing/UV path; no GPU or GUI assertions. */
public final class CustomParticleSmokeTest {
    public static void main(String[] args) {
        staticTexture();
        playbackModes();
        frameRateAndControls();
        gridAndBounds();
        independentSeeds();
        RibbonMeshSmokeTest.run();
        RibbonOptionsSmokeTest.run();
        ParticleMotionSmokeTest.run();
        ParticleLightFieldSmokeTest.run();
        System.out.println("PASS custom particle sprite foundation: static/grid UV, tick playback, pause/manual, "
                + "bounded parsing and instance seed isolation; rendering MANUAL/PENDING");
    }

    private static void staticTexture() {
        var sheet = ParticleSpriteSheet.read(13, 7, options(), 1, 0);
        require(sheet.frameAt(0) == 0 && sheet.frameAt(1000.25) == 0, "static texture remains one frame");
        require(sheet.uvAt(100).equals(new ParticleSpriteSheet.Uv(0, 1, 0, 1)),
                "static texture covers the full image");
    }

    private static void playbackModes() {
        var loop = sheet("loop");
        var once = sheet("once");
        var ping = sheet("pingPong");
        int[] expectedLoop = {1, 2, 3, 1, 2, 3, 1};
        int[] expectedOnce = {1, 2, 3, 3, 3, 3, 3};
        int[] expectedPing = {1, 2, 3, 2, 1, 2, 3};
        for (int index = 0; index < expectedLoop.length; index++) {
            double time = index * 2;
            require(loop.frameAt(time) == expectedLoop[index], "loop playback range and wrap");
            require(once.frameAt(time) == expectedOnce[index], "once holds last frame");
            require(ping.frameAt(time) == expectedPing[index], "ping-pong has no repeated endpoint");
        }
        require(loop.frameAt(1.999) == 1 && loop.frameAt(2) == 2, "tick boundary is exact");
        require(ping.frameAt(2_000_000_000) >= 1 && ping.frameAt(2_000_000_000) <= 3,
                "long finite time remains in range without allocating a timeline");
    }

    private static void frameRateAndControls() {
        var lowFps = sheet("loop");
        var highFps = sheet("loop");
        for (int tick = 0; tick < 50; tick++) {
            for (int subframe = 0; subframe < 12; subframe++) {
                highFps.uvAt(tick + subframe / 12.0);
            }
            require(lowFps.frameAt(tick + .75) == highFps.frameAt(tick + .75),
                    "render frequency does not advance sprite animation");
        }
        var sheet = sheet("pingPong");
        sheet.setPaused(true, 3);
        require(sheet.paused() && sheet.frameAt(200) == 2, "pause freezes fractional phase");
        sheet.setPaused(false, 200);
        require(sheet.frameAt(200.5) == 2 && sheet.frameAt(201) == 3,
                "resume preserves partial frame without a time jump");
        sheet.setFrame(1, 202);
        require(sheet.frameAt(202) == 1 && sheet.frameAt(204) == 2, "manual frame resets playback origin");
        sheet.setPaused(true, 205);
        sheet.setFrame(3, 206);
        require(sheet.frameAt(300) == 3, "manual frame remains paused");
        reject(() -> sheet.setFrame(0, 207));
        reject(() -> sheet.setFrame(4, 207));
        reject(() -> sheet.setPaused(false, 100));
        reject(() -> sheet.frameAt(Double.NaN));
    }

    private static void gridAndBounds() {
        var cell = ParticleSpriteSheet.read(22, 13, options("frameWidth", 6, "frameHeight", 4,
                "marginX", 1, "marginY", 2, "spacingX", 1, "spacingY", 1,
                "frameCount", 5, "initialFrame", 4, "paused", true), 1, 0);
        var grid = ParticleSpriteSheet.read(22, 13, options("columns", 3, "rows", 2,
                "marginX", 1, "marginY", 2, "spacingX", 1, "spacingY", 1,
                "frameCount", 5, "initialFrame", 4, "paused", true), 1, 0);
        var uv = cell.uvAt(0);
        require(uv.equals(grid.uvAt(0)), "cell and grid definitions select the same row-major frame");
        near(uv.u0(), 8.0 / 22);
        near(uv.u1(), 14.0 / 22);
        near(uv.v0(), 7.0 / 13);
        near(uv.v1(), 11.0 / 13);
        reject(() -> ParticleSpriteSheet.read(23, 13, options("columns", 3), 1, 0));
        reject(() -> ParticleSpriteSheet.read(8, 8, options("columns", 2, "frameWidth", 3), 1, 0));
        reject(() -> ParticleSpriteSheet.read(8, 8, options("frameCount", 2), 1, 0));
        reject(() -> ParticleSpriteSheet.read(8, 8, options("marginX", Integer.MAX_VALUE), 1, 0));
        reject(() -> ParticleSpriteSheet.read(8, 8, options("columns", Integer.MAX_VALUE,
                "spacingX", Integer.MAX_VALUE), 1, 0));
        reject(() -> ParticleSpriteSheet.read(0, 8, options(), 1, 0));
        for (Object invalid : new Object[]{-1, 0, 1.5, Double.NaN, Double.POSITIVE_INFINITY, "2"}) {
            reject(() -> ParticleSpriteSheet.read(8, 8, options("columns", invalid), 1, 0));
        }
        reject(() -> ParticleSpriteSheet.read(8, 8, options("frameTimeTicks", 0), 1, 0));
        reject(() -> ParticleSpriteSheet.read(8, 8, options("playback", "unknown"), 1, 0));
        reject(() -> ParticleSpriteSheet.read(8, 8, options("paused", 1), 1, 0));
        reject(() -> ParticleSpriteSheet.read(8, 8, options("endFrame", 1), 1, 0));
        reject(() -> ParticleSpriteSheet.read(8, 8, options("frameWidth", 2, "startFrame", 3,
                "endFrame", 1), 1, 0));
        reject(() -> ParticleSpriteSheet.read(8, 8, options("unexpected", true), 1, 0));
        var one = ParticleSpriteSheet.read(8, 8, options("playback", "pingPong"), 1, 0);
        require(one.frameAt(100) == 0, "single-frame ping-pong avoids zero period");
    }

    private static void independentSeeds() {
        var input = options("columns", 8, "randomStart", true);
        var first = ParticleSpriteSheet.read(64, 8, input, 123, 0);
        int initial = first.frameAt(0);
        for (int seed = 0; seed < 100; seed++) {
            ParticleSpriteSheet.read(64, 8, input, seed, 0).frameAt(50);
        }
        var repeated = ParticleSpriteSheet.read(64, 8, input, 123, 0);
        require(repeated.frameAt(0) == initial, "random start uses only this particle seed");
        first.setPaused(true, 0);
        first.setFrame((initial + 1) % 8, 0);
        require(repeated.frameAt(0) == initial, "one particle's manual controls do not alter its duplicate");
        input.set("columns", 1);
        require(repeated.frameAt(20) == (initial + 20) % 8,
                "author mutating the options table cannot rewrite an existing particle");
    }

    private static ParticleSpriteSheet sheet(String mode) {
        return ParticleSpriteSheet.read(32, 8, options("columns", 4, "startFrame", 1,
                "endFrame", 3, "frameTimeTicks", 2, "playback", mode), 1, 0);
    }

    private static LuaTable options(Object... pairs) {
        var table = new LuaTable();
        for (int index = 0; index < pairs.length; index += 2) {
            Object value = pairs[index + 1];
            LuaValue lua = switch (value) {
                case Boolean flag -> LuaValue.valueOf(flag);
                case Number number -> LuaValue.valueOf(number.doubleValue());
                case String text -> LuaValue.valueOf(text);
                default -> throw new AssertionError("Unsupported fixture value");
            };
            table.set((String) pairs[index], lua);
        }
        return table;
    }

    private static void reject(Runnable action) {
        try {
            action.run();
        } catch (LuaError expected) {
            return;
        }
        throw new AssertionError("Invalid sprite configuration was accepted");
    }

    private static void near(double actual, double expected) {
        require(Math.abs(actual - expected) < .000001, "UV coordinate " + actual + " != " + expected);
    }

    private static void require(boolean condition, String reason) {
        if (!condition) {
            throw new AssertionError(reason);
        }
    }
}
