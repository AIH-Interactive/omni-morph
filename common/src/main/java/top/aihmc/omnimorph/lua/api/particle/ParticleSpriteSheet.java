package top.aihmc.omnimorph.lua.api.particle;

import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;

import java.util.Set;
import java.util.SplittableRandom;

/** Validated texture cells and tick-based playback, shared by all custom particle render modes. */
public final class ParticleSpriteSheet {
    private static final Set<String> OPTIONS = Set.of("frameWidth", "frameHeight", "columns", "rows",
            "marginX", "marginY", "spacingX", "spacingY", "frameCount", "startFrame", "endFrame",
            "frameTimeTicks", "playback", "initialFrame", "randomStart", "paused");

    public enum Playback {
        ONCE, LOOP, PING_PONG
    }

    public record Uv(float u0, float u1, float v0, float v1) {
    }

    private record Axis(int cell, int count, int margin, int spacing) {
    }

    private final int textureWidth;
    private final int textureHeight;
    private final Axis horizontal;
    private final Axis vertical;
    private final int frameCount;
    private final int startFrame;
    private final int endFrame;
    private final double frameTimeTicks;
    private final Playback playback;
    private double phase;
    private double changedAt;
    private boolean paused;

    private ParticleSpriteSheet(int width, int height, LuaTable options, long seed, double time) {
        finiteTime(time);
        validateKeys(options);
        textureWidth = width;
        textureHeight = height;
        horizontal = axis(width, options, "frameWidth", "columns", "marginX", "spacingX");
        vertical = axis(height, options, "frameHeight", "rows", "marginY", "spacingY");
        long available = (long) horizontal.count * vertical.count;
        require(available <= Integer.MAX_VALUE, "sprite grid is too large");
        frameCount = integer(options, "frameCount", (int) available, 1);
        require(frameCount <= available, "frameCount exceeds sprite grid");
        startFrame = integer(options, "startFrame", 0, 0);
        endFrame = integer(options, "endFrame", frameCount - 1, 0);
        require(startFrame <= endFrame && endFrame < frameCount, "invalid sprite playback range");
        frameTimeTicks = number(options, "frameTimeTicks", 1);
        require(frameTimeTicks > 0, "frameTimeTicks must be positive");
        LuaValue mode = options.rawget("playback");
        require(mode.isnil() || mode.type() == LuaValue.TSTRING, "playback must be a string");
        playback = switch (mode.optjstring("loop")) {
            case "once" -> Playback.ONCE;
            case "loop" -> Playback.LOOP;
            case "pingPong" -> Playback.PING_PONG;
            default -> throw new LuaError("Unknown sprite playback mode");
        };
        int initial = integer(options, "initialFrame", startFrame, 0);
        require(initial >= startFrame && initial <= endFrame, "initialFrame is outside playback range");
        if (bool(options, "randomStart", false)) {
            initial = startFrame + new SplittableRandom(seed).nextInt(endFrame - startFrame + 1);
        }
        phase = initial - startFrame;
        changedAt = time;
        paused = bool(options, "paused", false);
    }

    public static ParticleSpriteSheet read(int width, int height, LuaTable options, long seed, double time) {
        require(options != null, "sprite options must be a table");
        return new ParticleSpriteSheet(width, height, options, seed, time);
    }

    public int frameAt(double time) {
        double current = Math.floor(phaseAt(time));
        int count = endFrame - startFrame + 1;
        int offset;
        if (playback == Playback.ONCE) {
            offset = (int) Math.min(count - 1, current);
        } else if (playback == Playback.LOOP || count == 1) {
            offset = (int) (current % count);
        } else {
            long period = 2L * (count - 1);
            long step = (long) (current % period);
            offset = (int) Math.min(step, period - step);
        }
        return startFrame + offset;
    }

    public Uv uvAt(double time) {
        int frame = frameAt(time);
        int column = frame % horizontal.count;
        int row = frame / horizontal.count;
        long x = horizontal.margin + (long) column * (horizontal.cell + (long) horizontal.spacing);
        long y = vertical.margin + (long) row * (vertical.cell + (long) vertical.spacing);
        return new Uv((float) x / textureWidth, (float) (x + horizontal.cell) / textureWidth,
                (float) y / textureHeight, (float) (y + vertical.cell) / textureHeight);
    }

    public void setPaused(boolean value, double time) {
        finiteTime(time);
        require(time >= changedAt, "sprite control time moved backwards");
        phase = phaseAt(time);
        changedAt = time;
        paused = value;
    }

    public boolean paused() {
        return paused;
    }

    public void setFrame(int frame, double time) {
        finiteTime(time);
        require(time >= changedAt, "sprite control time moved backwards");
        require(frame >= startFrame && frame <= endFrame, "frame is outside playback range");
        phase = frame - startFrame;
        changedAt = time;
    }

    private double phaseAt(double time) {
        finiteTime(time);
        // Old extracted frames cannot rewind the live playback clock.
        double value = phase + (paused ? 0 : Math.max(0, time - changedAt) / frameTimeTicks);
        require(Double.isFinite(value), "sprite playback time overflow");
        return value;
    }

    private static Axis axis(int pixels, LuaTable options, String cellKey, String countKey,
                             String marginKey, String spacingKey) {
        require(pixels > 0, "texture dimensions must be positive");
        int margin = integer(options, marginKey, 0, 0);
        int spacing = integer(options, spacingKey, 0, 0);
        long usable = pixels - 2L * margin;
        require(usable > 0, "sprite margins exceed texture size");
        boolean hasCell = !options.rawget(cellKey).isnil();
        boolean hasCount = !options.rawget(countKey).isnil();
        int cell = integer(options, cellKey, (int) usable, 1);
        int count = integer(options, countKey, 1, 1);
        if (hasCell && !hasCount) {
            long stride = cell + (long) spacing;
            require((usable + spacing) % stride == 0, "sprite cell does not divide texture");
            long derived = (usable + spacing) / stride;
            require(derived >= 1 && derived <= Integer.MAX_VALUE, "invalid sprite cell count");
            count = (int) derived;
        } else if (hasCount && !hasCell) {
            long content = usable - (long) (count - 1) * spacing;
            require(content > 0 && content % count == 0, "sprite grid does not divide texture");
            cell = (int) (content / count);
        }
        require((long) cell * count + (long) spacing * (count - 1) == usable,
                "conflicting sprite dimensions");
        return new Axis(cell, count, margin, spacing);
    }

    private static void validateKeys(LuaTable options) {
        LuaValue key = LuaValue.NIL;
        int count = 0;
        while (true) {
            key = options.next(key).arg1();
            if (key.isnil()) {
                return;
            }
            require(++count <= OPTIONS.size() && key.type() == LuaValue.TSTRING
                    && OPTIONS.contains(key.tojstring()), "unknown sprite option");
        }
    }

    private static int integer(LuaTable options, String key, int fallback, int minimum) {
        double value = number(options, key, fallback);
        require(value >= minimum && value <= Integer.MAX_VALUE && value == Math.rint(value),
                key + " must be a bounded integer");
        return (int) value;
    }

    private static double number(LuaTable options, String key, double fallback) {
        LuaValue value = options.rawget(key);
        if (value.isnil()) {
            return fallback;
        }
        require(value.type() == LuaValue.TNUMBER && Double.isFinite(value.todouble()),
                key + " must be finite");
        return value.todouble();
    }

    private static boolean bool(LuaTable options, String key, boolean fallback) {
        LuaValue value = options.rawget(key);
        require(value.isnil() || value.isboolean(), key + " must be a boolean");
        return value.isnil() ? fallback : value.toboolean();
    }

    private static void finiteTime(double time) {
        require(Double.isFinite(time) && time >= 0, "particle time must be finite and nonnegative");
    }

    private static void require(boolean condition, String reason) {
        if (!condition) {
            throw new LuaError(reason);
        }
    }
}
