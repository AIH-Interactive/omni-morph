package top.aihmc.omnimorph.lua.api.particle;

import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaValue;
import top.aihmc.omnimorph.model.outfit.OutfitLimits;

/** Dynamic illumination is independent of the particle material's emissive/full-bright setting. */
record ParticleLightSettings(boolean enabled, int level, double radius, int interval) {
    ParticleLightSettings {
        if (level < 0 || level > 15 || !Double.isFinite(radius) || radius <= 0 || radius > 32
                || interval < 1 || interval > 64) {
            throw new LuaError("Invalid custom particle light level, radius or interval");
        }
    }

    static ParticleLightSettings defaults() {
        return new ParticleLightSettings(false, 15, 7.5, 1);
    }

    void validate(OutfitLimits limits) {
        if (!enabled) {
            return;
        }
        if (!limits.dynamicLightingEnabled() || limits.maxDynamicLights() == 0) {
            throw new LuaError("PARTICLE_DYNAMIC_LIGHT_DISABLED");
        }
        if (level > limits.maxDynamicLightLevel() || radius > limits.maxDynamicLightRadius()) {
            throw new LuaError("PARTICLE_DYNAMIC_LIGHT_SETTING_LIMIT");
        }
    }

    ParticleLightSettings alias(LuaValue value) {
        if (value.isnil()) {
            return this;
        }
        if (value.isboolean()) {
            return new ParticleLightSettings(value.toboolean(), level, radius, interval);
        }
        if (!value.istable()) {
            throw new LuaError("Ribbon light must be a boolean or a settings table");
        }
        var table = value.checktable();
        var suppliedEnabled = table.rawget("enabled");
        if (!suppliedEnabled.isnil() && !suppliedEnabled.isboolean()) {
            throw new LuaError("Ribbon light enabled must be a boolean");
        }
        var suppliedLevel = table.rawget("level");
        double newLevel = suppliedLevel.isnil() ? level : number(suppliedLevel);
        if (newLevel != Math.rint(newLevel) || newLevel < 0 || newLevel > 15) {
            throw new LuaError("Custom particle light level must be an integer from 0 to 15");
        }
        var suppliedRadius = table.rawget("radius");
        var suppliedInterval = table.rawget("interval");
        double newInterval = suppliedInterval.isnil() ? interval : number(suppliedInterval);
        if (newInterval != Math.rint(newInterval) || newInterval < 1 || newInterval > 64) {
            throw new LuaError("Custom particle light interval must be an integer from 1 to 64");
        }
        return new ParticleLightSettings(suppliedEnabled.isnil() || suppliedEnabled.toboolean(), (int) newLevel,
                suppliedRadius.isnil() ? radius : number(suppliedRadius), (int) newInterval);
    }

    private static double number(LuaValue value) {
        if (value.type() != LuaValue.TNUMBER || !Double.isFinite(value.todouble())) {
            throw new LuaError("Custom particle light settings must be finite numbers");
        }
        return value.todouble();
    }
}
