package top.aihmc.omnimorph.lua.api.particle;

import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import top.aihmc.omnimorph.model.outfit.TrailBuffer;

import java.util.Locale;

/** Immutable geometry and sampling settings; parsing never invokes author table metamethods. */
public record RibbonOptions(RibbonMesh.Settings geometry, double sampleDistance, double sampleInterval,
                            double teleportDistance) {
    public RibbonOptions {
        if (geometry == null || !Double.isFinite(sampleDistance) || sampleDistance < 0
                || !Double.isFinite(sampleInterval) || sampleInterval <= 0
                || !Double.isFinite(teleportDistance) || teleportDistance <= 0
                || !Double.isFinite(geometry.lifetime() + sampleInterval)) {
            throw new LuaError("Invalid Ribbon sampling settings");
        }
    }

    public static RibbonOptions defaults() {
        return new RibbonOptions(new RibbonMesh.Settings(.15, 4, 12, 1, 0,
                false, false, RibbonMesh.UvMode.REPEAT), .1, 1, 8);
    }

    public RibbonOptions update(LuaTable options) {
        if (options == null) {
            throw new LuaError("Ribbon settings require a table");
        }
        RibbonMesh.UvMode mode = geometry.uvMode();
        LuaValue suppliedMode = options.rawget("uvMode");
        if (!suppliedMode.isnil()) {
            if (suppliedMode.type() != LuaValue.TSTRING) {
                throw new LuaError("Ribbon uvMode must be repeat or stretch");
            }
            try {
                mode = RibbonMesh.UvMode.valueOf(suppliedMode.tojstring().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException invalid) {
                throw new LuaError("Ribbon uvMode must be repeat or stretch");
            }
        }
        try {
            var settings = new RibbonMesh.Settings(number(options, "width", geometry.width()),
                    number(options, "length", geometry.length()), number(options, "lifetime", geometry.lifetime()),
                    number(options, "uvRepeat", geometry.uvRepeat()), number(options, "uvOffset", geometry.uvOffset()),
                    flag(options, "taper", geometry.taper()), flag(options, "fade", geometry.fade()), mode);
            return new RibbonOptions(settings, number(options, "sampleDistance", sampleDistance),
                    number(options, "sampleInterval", sampleInterval),
                    number(options, "teleportDistance", teleportDistance));
        } catch (IllegalArgumentException invalid) {
            throw new LuaError("Invalid Ribbon geometry settings");
        }
    }

    public TrailBuffer.Policy samplingPolicy() {
        // Retain one sampling interval beyond the visible TTL so clipping can interpolate its boundary.
        return new TrailBuffer.Policy(sampleDistance, sampleInterval, geometry.lifetime() + sampleInterval,
                teleportDistance);
    }

    public LuaTable toLua() {
        LuaTable table = new LuaTable();
        table.set("width", LuaValue.valueOf(geometry.width()));
        table.set("length", LuaValue.valueOf(geometry.length()));
        table.set("lifetime", LuaValue.valueOf(geometry.lifetime()));
        table.set("uvRepeat", LuaValue.valueOf(geometry.uvRepeat()));
        table.set("uvOffset", LuaValue.valueOf(geometry.uvOffset()));
        table.set("taper", LuaValue.valueOf(geometry.taper()));
        table.set("fade", LuaValue.valueOf(geometry.fade()));
        table.set("uvMode", LuaValue.valueOf(geometry.uvMode().name().toLowerCase(Locale.ROOT)));
        table.set("sampleDistance", LuaValue.valueOf(sampleDistance));
        table.set("sampleInterval", LuaValue.valueOf(sampleInterval));
        table.set("teleportDistance", LuaValue.valueOf(teleportDistance));
        return table;
    }

    private static double number(LuaTable table, String key, double fallback) {
        LuaValue value = table.rawget(key);
        if (value.isnil()) {
            return fallback;
        }
        if (value.type() != LuaValue.TNUMBER || !Double.isFinite(value.todouble())) {
            throw new LuaError("Ribbon " + key + " must be a finite number");
        }
        return value.todouble();
    }

    private static boolean flag(LuaTable table, String key, boolean fallback) {
        LuaValue value = table.rawget(key);
        if (value.isnil()) {
            return fallback;
        }
        if (!value.isboolean()) {
            throw new LuaError("Ribbon " + key + " must be a boolean");
        }
        return value.toboolean();
    }
}
