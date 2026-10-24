package top.aihmc.omnimorph.lua.api.particle;

import net.minecraft.client.particle.SingleQuadParticle;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.figuramc.figura.avatar.Avatar;
import org.figuramc.figura.math.vector.FiguraVec3;
import org.figuramc.figura.math.vector.FiguraVec4;
import org.figuramc.figura.model.rendering.texture.FiguraTexture;

import java.util.Set;

import org.figuramc.figura.lua.api.particle.ParticleAPI;
/** One copied, bounded author layer declaration; a null sheet inherits particle-wide playback. */
record CustomParticleLayer(FiguraTexture texture, ParticleSpriteSheet sheet, ParticleSpriteSheet.Uv crop,
                           Tint tint, Boolean emissive, SingleQuadParticle.Layer renderLayer) {
    private static final Set<String> KEYS = Set.of("texture", "spriteSheet", "uv", "color",
            "alpha", "blend", "emissive");
    private static final ParticleSpriteSheet.Uv FULL_UV = new ParticleSpriteSheet.Uv(0, 1, 0, 1);

    record Tint(float red, float green, float blue, float alpha) {
    }

    static CustomParticleLayer base(FiguraTexture texture, CustomParticlePipelines.Blend blend) {
        return new CustomParticleLayer(texture, null, FULL_UV, new Tint(1, 1, 1, 1), null,
                new SingleQuadParticle.Layer(true, texture.getLocation(), CustomParticlePipelines.pipeline(0, blend)));
    }

    static CustomParticleLayer read(Avatar owner, FiguraTexture fallback, LuaTable value,
                                    int index, long seed, double age) {
        LuaValue key = LuaValue.NIL;
        int keys = 0;
        while (true) {
            key = value.next(key).arg1();
            if (key.isnil()) {
                break;
            }
            require(++keys <= KEYS.size() && key.type() == LuaValue.TSTRING && KEYS.contains(key.tojstring()),
                    "Unknown particle layer field");
        }
        LuaValue textureValue = value.rawget("texture");
        FiguraTexture texture = fallback;
        if (!textureValue.isnil()) {
            Object reference = textureValue.type() == LuaValue.TSTRING ? textureValue.tojstring()
                    : textureValue.isuserdata(FiguraTexture.class) ? textureValue.touserdata(FiguraTexture.class) : null;
            texture = ParticleAPI.resolveTexture(owner, reference);
        }
        LuaValue sprite = value.rawget("spriteSheet");
        require(sprite.isnil() || sprite.istable(), "spriteSheet must be a table");
        ParticleSpriteSheet sheet = sprite.isnil() ? null : ParticleSpriteSheet.read(texture.getWidth(),
                texture.getHeight(), sprite.checktable(), seed ^ Long.rotateLeft(index + 1L, 23), age);
        ParticleSpriteSheet.Uv crop = readUv(value.rawget("uv"));
        Tint tint = readTint(value.rawget("color"), value.rawget("alpha"));
        LuaValue light = value.rawget("emissive");
        require(light.isnil() || light.isboolean(), "emissive must be a boolean");
        Boolean emissive = light.isnil() ? null : light.toboolean();
        LuaValue blendValue = value.rawget("blend");
        require(blendValue.isnil() || blendValue.type() == LuaValue.TSTRING, "blend must be a string");
        CustomParticlePipelines.Blend blend = blend(blendValue.optjstring("alpha"));
        return new CustomParticleLayer(texture, sheet, crop, tint, emissive,
                new SingleQuadParticle.Layer(true, texture.getLocation(), CustomParticlePipelines.pipeline(index, blend)));
    }

    static CustomParticlePipelines.Blend blend(String value) {
        return switch (value) {
            case "alpha" -> CustomParticlePipelines.Blend.ALPHA;
            case "additive" -> CustomParticlePipelines.Blend.ADDITIVE;
            case "opaque" -> CustomParticlePipelines.Blend.OPAQUE;
            default -> throw new LuaError("Unknown custom particle blend mode");
        };
    }

    ParticleSpriteSheet.Uv uvAt(double time, ParticleSpriteSheet inherited) {
        ParticleSpriteSheet.Uv cell = (sheet == null ? inherited : sheet).uvAt(time);
        if (crop.equals(FULL_UV)) {
            return cell;
        }
        float width = cell.u1() - cell.u0();
        float height = cell.v1() - cell.v0();
        return new ParticleSpriteSheet.Uv(cell.u0() + width * crop.u0(), cell.u0() + width * crop.u1(),
                cell.v0() + height * crop.v0(), cell.v0() + height * crop.v1());
    }

    private static ParticleSpriteSheet.Uv readUv(LuaValue value) {
        if (value.isnil()) {
            return FULL_UV;
        }
        require(value.istable(), "uv must be a four-number array");
        LuaTable table = value.checktable();
        require(arrayLength(table, 4) == 4, "uv must contain exactly four numbers");
        float u0 = unit(table.rawget(1), 0);
        float u1 = unit(table.rawget(2), 1);
        float v0 = unit(table.rawget(3), 0);
        float v1 = unit(table.rawget(4), 1);
        require(u1 > u0 && v1 > v0, "uv must describe a positive rectangle");
        return new ParticleSpriteSheet.Uv(u0, u1, v0, v1);
    }

    private static Tint readTint(LuaValue color, LuaValue opacity) {
        double red = 1;
        double green = 1;
        double blue = 1;
        double alpha = 1;
        if (color.isuserdata(FiguraVec4.class)) {
            var tint = (FiguraVec4) color.touserdata(FiguraVec4.class);
            red = tint.x;
            green = tint.y;
            blue = tint.z;
            alpha = tint.w;
        } else if (color.isuserdata(FiguraVec3.class)) {
            var tint = (FiguraVec3) color.touserdata(FiguraVec3.class);
            red = tint.x;
            green = tint.y;
            blue = tint.z;
        } else {
            require(color.isnil(), "color must be a Figura Vec3 or Vec4");
        }
        return new Tint(unit(red), unit(green), unit(blue), unit(alpha) * unit(opacity, 1));
    }

    static int arrayLength(LuaTable table, int maximum) {
        LuaValue key = LuaValue.NIL;
        int count = 0;
        int last = 0;
        while (true) {
            key = table.next(key).arg1();
            if (key.isnil()) {
                break;
            }
            require(++count <= maximum && key.type() == LuaValue.TNUMBER && key.isint()
                    && key.toint() >= 1 && key.toint() <= maximum, "Particle array must be dense and bounded");
            last = Math.max(last, key.toint());
        }
        require(count == last, "Particle array has a missing element");
        return count;
    }

    private static float unit(LuaValue value, float fallback) {
        if (value.isnil()) {
            return fallback;
        }
        require(value.type() == LuaValue.TNUMBER, "Layer value must be a number");
        return unit(value.todouble());
    }

    private static float unit(double value) {
        require(Double.isFinite(value) && value >= 0 && value <= 1, "Layer value must be finite in [0,1]");
        return (float) value;
    }

    private static void require(boolean condition, String reason) {
        if (!condition) {
            throw new LuaError(reason);
        }
    }
}
