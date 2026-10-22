package top.aihmc.omnimorph.lua.api.particle;

import net.minecraft.world.phys.Vec3;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.lib.TwoArgFunction;
import top.aihmc.omnimorph.model.outfit.OutfitLimits;
import top.aihmc.omnimorph.model.outfit.TrailBuffer;

/** Exercises real Lua table parsing and the production sampling/geometry boundary. */
final class RibbonOptionsSmokeTest {
    static void run() {
        immutableUpdates();
        invalidValues();
        rawBoundedReads();
        lifetimeBoundary();
        System.out.println("PASS ribbon options: typed Lua values, immutable partial updates, raw bounded reads "
                + "and interpolated lifetime boundary; renderer integration pending");
    }

    private static void immutableUpdates() {
        RibbonOptions defaults = RibbonOptions.defaults();
        LuaTable supplied = defaults.toLua();
        supplied.set("width", LuaValue.valueOf(.5));
        supplied.set("lifetime", LuaValue.valueOf(4.5));
        supplied.set("uvOffset", LuaValue.valueOf(-2.25));
        supplied.set("uvMode", "STRETCH");
        supplied.set("taper", LuaValue.TRUE);
        supplied.set("fade", LuaValue.TRUE);
        var updated = defaults.update(supplied);
        supplied.set("width", LuaValue.valueOf(999));
        require(updated.geometry().width() == .5 && defaults.geometry().width() == .15,
                "caller tables and prior configuration are independent");
        require(updated.geometry().lifetime() == 4.5 && updated.geometry().uvOffset() == -2.25
                        && updated.geometry().uvMode() == RibbonMesh.UvMode.STRETCH
                        && updated.geometry().taper() && updated.geometry().fade(),
                "fractional ticks, signed offset, stretch and optional effects are retained");
        LuaTable partial = new LuaTable();
        partial.set("sampleDistance", LuaValue.ZERO);
        var next = updated.update(partial);
        require(next.geometry().equals(updated.geometry()) && next.sampleDistance() == 0,
                "partial sampling changes preserve geometry and allow zero minimum distance");
        require(next.equals(defaults.update(next.toLua())), "configuration snapshot round trip");
        LuaTable returned = next.toLua();
        returned.set("length", LuaValue.valueOf(999));
        require(next.geometry().length() == 4, "returned settings are a copy");
    }

    private static void invalidValues() {
        var defaults = RibbonOptions.defaults();
        for (String name : new String[]{"width", "length", "lifetime", "uvRepeat", "sampleInterval",
                "teleportDistance"}) {
            for (double invalid : new double[]{0, -1, Double.NaN, Double.POSITIVE_INFINITY}) {
                reject(() -> defaults.update(option(name, LuaValue.valueOf(invalid))));
            }
        }
        for (String name : new String[]{"width", "uvOffset", "sampleDistance"}) {
            reject(() -> defaults.update(option(name, LuaValue.valueOf("1"))));
            reject(() -> defaults.update(option(name, LuaValue.TRUE)));
        }
        reject(() -> defaults.update(option("sampleDistance", LuaValue.valueOf(-1))));
        reject(() -> defaults.update(option("uvOffset", LuaValue.valueOf(Double.NEGATIVE_INFINITY))));
        reject(() -> defaults.update(option("taper", LuaValue.ONE)));
        reject(() -> defaults.update(option("fade", LuaValue.valueOf("false"))));
        reject(() -> defaults.update(option("uvMode", LuaValue.ONE)));
        reject(() -> defaults.update(option("uvMode", LuaValue.valueOf("unknown"))));
        LuaTable overflow = option("lifetime", LuaValue.valueOf(Double.MAX_VALUE));
        overflow.set("sampleInterval", LuaValue.valueOf(Double.MAX_VALUE));
        reject(() -> defaults.update(overflow));
        reject(() -> defaults.update(null));
    }

    private static void rawBoundedReads() {
        LuaTable table = new LuaTable();
        LuaTable metatable = new LuaTable();
        metatable.set("__index", new TwoArgFunction() {
            @Override
            public LuaValue call(LuaValue receiver, LuaValue key) {
                throw new AssertionError("Ribbon parsing must not run author metamethods");
            }
        });
        table.setmetatable(metatable);
        // Sparse/extraneous entries do not turn fixed option reads into an unbounded table walk.
        table.rawset(Integer.MAX_VALUE, LuaValue.ONE);
        require(RibbonOptions.defaults().equals(RibbonOptions.defaults().update(table)),
                "only the fixed geometry and sampling option keys are read");
    }

    private static void lifetimeBoundary() {
        var options = RibbonOptions.defaults().update(option("lifetime", LuaValue.valueOf(1.5)));
        var buffer = new TrailBuffer(OutfitLimits.defaults());
        Object world = new Object();
        for (int index = 0; index <= 2; index++) {
            buffer.sample(index, world, "tip", index, new Vec3(index, 0, 0), options.samplingPolicy());
        }
        require(buffer.snapshot().getFirst().timeTicks() == 0, "retain the predecessor of the visible TTL boundary");
        var mesh = RibbonMesh.build(buffer.snapshotPath(), 2, new Vec3(0, 0, 10), new Vec3(1, 0, 0),
                options.geometry(), OutfitLimits.defaults(), 1, 4096, 100);
        require(mesh.quads().getFirst().tailLeft().position().x == .5,
                "visible geometry is interpolated at the exact configured lifetime boundary");
        buffer.advance(world, 4.5);
        require(buffer.snapshot().isEmpty(), "extra interpolation retention still expires bounded history");
    }

    private static LuaTable option(String name, LuaValue value) {
        LuaTable table = new LuaTable();
        table.set(name, value);
        return table;
    }

    private static void reject(Runnable action) {
        try {
            action.run();
        } catch (LuaError expected) {
            return;
        }
        throw new AssertionError("Invalid Ribbon settings were accepted");
    }

    private static void require(boolean value, String message) {
        if (!value) {
            throw new AssertionError(message);
        }
    }
}
