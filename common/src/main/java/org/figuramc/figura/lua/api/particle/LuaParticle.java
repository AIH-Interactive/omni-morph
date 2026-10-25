package org.figuramc.figura.lua.api.particle;

import net.minecraft.client.Minecraft;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.SingleQuadParticle;
import net.minecraft.client.particle.WakeParticle;
import org.figuramc.figura.avatar.Avatar;
import org.figuramc.figura.ducks.SingleQuadParticleAccessor;
import org.figuramc.figura.lua.LuaWhitelist;
import org.figuramc.figura.lua.docs.LuaMethodDoc;
import org.figuramc.figura.lua.docs.LuaMethodOverload;
import org.figuramc.figura.lua.docs.LuaTypeDoc;
import org.figuramc.figura.math.vector.FiguraVec3;
import org.figuramc.figura.math.vector.FiguraVec4;
import org.figuramc.figura.mixin.particle.ParticleAccessor;
import org.figuramc.figura.permissions.Permissions;
import org.figuramc.figura.utils.LuaUtils;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaFunction;
import org.luaj.vm2.LuaTable;

import top.aihmc.omnimorph.lua.api.particle.CustomParticle;
@LuaWhitelist
@LuaTypeDoc(
        name = "Particle",
        value = "particle"
)
public class LuaParticle {

    private final String name;
    private final Avatar owner;
    private final Particle particle;

    private float power;

    public LuaParticle(String name, Particle particle, Avatar owner) {
        this.name = name;
        this.particle = particle;
        this.owner = owner;
    }

    @LuaWhitelist
    @LuaMethodDoc("particle.spawn")
    public LuaParticle spawn() {
        if (particle instanceof ParticleAPI.SuppressedPreviewParticle) {
            return this;
        }
        if (owner.previewParticles != null
                && !(particle instanceof CustomParticle custom && custom.preview() == owner.previewParticles)) {
            throw new LuaError("Only this preview's custom particles can be spawned in its isolated scope");
        }
        if (particle instanceof CustomParticle custom && (custom.active() || !custom.isAlive())) {
            return this;
        }
        if (!Minecraft.getInstance().isPaused()) {
            if (owner.useParticle()) {
                if (particle instanceof CustomParticle custom && !custom.activate()) {
                    owner.noPermissions.add(Permissions.PARTICLES);
                    return this;
                }
                try {
                    if (!(particle instanceof CustomParticle custom && custom.preview() != null)) {
                        ParticleAPI.getParticleEngine().figura$spawnParticle(particle, owner.owner);
                    }
                } catch (RuntimeException | Error failure) {
                    if (particle instanceof CustomParticle) {
                        particle.remove();
                    }
                    throw failure;
                }
                owner.noPermissions.remove(Permissions.PARTICLES);
            } else {
                owner.noPermissions.add(Permissions.PARTICLES);
            }
        }
        return this;
    }

    private CustomParticle custom() {
        if (particle instanceof CustomParticle custom) {
            return custom;
        }
        throw new LuaError("This setting requires a custom particle");
    }

    @LuaWhitelist
    public LuaParticle setSpriteSheet(LuaTable options) {
        custom().setSpriteSheet(options);
        return this;
    }

    @LuaWhitelist
    public LuaParticle setLayers(LuaTable layers) {
        custom().setLayers(layers);
        return this;
    }

    @LuaWhitelist
    public int getLayerCount() {
        return custom().layerCount();
    }

    @LuaWhitelist
    public LuaParticle setRenderMode(@org.figuramc.figura.lua.LuaNotNil String mode) {
        custom().setRenderMode(mode);
        return this;
    }

    @LuaWhitelist
    public String getRenderMode() {
        return custom().renderMode();
    }

    @LuaWhitelist
    public LuaParticle setAnchorTrail(boolean enabled) {
        custom().setAnchorTrail(enabled);
        return this;
    }

    @LuaWhitelist
    public boolean isAnchorTrail() {
        return custom().anchorTrail();
    }

    @LuaWhitelist
    public LuaParticle setRibbon(LuaTable options) {
        custom().setRibbon(options);
        return this;
    }

    @LuaWhitelist
    public LuaTable getRibbon() {
        return custom().getRibbon();
    }

    @LuaWhitelist
    public String getRenderError() {
        return custom().getRenderError();
    }

    @LuaWhitelist
    public LuaParticle setAcceleration(Object x, Double y, Double z) {
        var value = LuaUtils.parseVec3("setAcceleration", x, y, z);
        custom().setAcceleration(new net.minecraft.world.phys.Vec3(value.x, value.y, value.z));
        return this;
    }

    @LuaWhitelist
    public FiguraVec3 getAcceleration() {
        return FiguraVec3.fromVec3(custom().acceleration());
    }

    @LuaWhitelist
    public LuaParticle setMotion(LuaTable options) {
        custom().setMotion(options);
        return this;
    }

    @LuaWhitelist
    public String getMotionMode() {
        return custom().motionMode();
    }

    @LuaWhitelist
    public String getMotionError() {
        return custom().getMotionError();
    }

    @LuaWhitelist
    public LuaParticle setTickCallback(LuaFunction function) {
        custom().setTickCallback(function);
        return this;
    }

    @LuaWhitelist
    public LuaParticle setFrameCallback(LuaFunction function) {
        custom().setFrameCallback(function);
        return this;
    }

    @LuaWhitelist
    public String getCallbackError() {
        return custom().getCallbackError();
    }

    @LuaWhitelist
    public LuaParticle setRotation(float degrees) {
        custom().setRotation(degrees);
        return this;
    }

    @LuaWhitelist
    public double getRotation() {
        return custom().rotation();
    }

    @LuaWhitelist
    public LuaParticle setAngularVelocity(float degreesPerTick) {
        custom().setAngularVelocity(degreesPerTick);
        return this;
    }

    @LuaWhitelist
    public float getAngularVelocity() {
        return custom().angularVelocity();
    }

    @LuaWhitelist
    public LuaParticle setBlendMode(@org.figuramc.figura.lua.LuaNotNil String mode) {
        custom().setBlendMode(mode);
        return this;
    }

    @LuaWhitelist
    public String getBlendMode() {
        return custom().blendMode();
    }

    @LuaWhitelist
    public LuaParticle setFrame(int frame) {
        custom().setFrame(frame);
        return this;
    }

    @LuaWhitelist
    public int getFrame() {
        return custom().frame();
    }

    @LuaWhitelist
    public LuaParticle setPaused(boolean paused) {
        custom().setPaused(paused);
        return this;
    }

    @LuaWhitelist
    public boolean isPaused() {
        return custom().paused();
    }

    @LuaWhitelist
    public LuaParticle setEmissive(boolean emissive) {
        custom().setEmissive(emissive);
        return this;
    }

    @LuaWhitelist
    public boolean isEmissive() {
        return custom().emissive();
    }

    @LuaWhitelist
    public LuaParticle setDynamicLight(boolean enabled) {
        custom().setDynamicLight(enabled);
        return this;
    }

    @LuaWhitelist
    public boolean isDynamicLight() {
        return custom().dynamicLight();
    }

    @LuaWhitelist
    public LuaParticle setLightLevel(double level) {
        if (!Double.isFinite(level) || level != Math.rint(level) || level < 0 || level > 15) {
            throw new LuaError("Custom particle light level must be an integer from 0 to 15");
        }
        custom().setLightLevel((int) level);
        return this;
    }

    @LuaWhitelist
    public int getLightLevel() {
        return custom().lightLevel();
    }

    @LuaWhitelist
    public LuaParticle setLightRadius(double radius) {
        custom().setLightRadius(radius);
        return this;
    }

    @LuaWhitelist
    public double getLightRadius() {
        return custom().lightRadius();
    }

    @LuaWhitelist
    public LuaParticle setLightInterval(double interval) {
        if (!Double.isFinite(interval) || interval != Math.rint(interval) || interval < 1 || interval > 64) {
            throw new LuaError("Custom particle light interval must be an integer from 1 to 64");
        }
        custom().setLightInterval((int) interval);
        return this;
    }

    @LuaWhitelist
    public int getLightInterval() {
        return custom().lightInterval();
    }

    @LuaWhitelist
    @LuaMethodDoc("particle.remove")
    public LuaParticle remove() {
        particle.remove();
        return this;
    }

    @LuaWhitelist
    @LuaMethodDoc("particle.is_alive")
    public boolean isAlive() {
        return particle instanceof CustomParticle custom ? custom.active() : particle.isAlive();
    }

    @LuaWhitelist
    @LuaMethodDoc("particle.get_pos")
    public FiguraVec3 getPos() {
        ParticleAccessor p = (ParticleAccessor) particle;
        return FiguraVec3.of(p.getX(), p.getY(), p.getZ());
    }

    @LuaWhitelist
    @LuaMethodDoc(
            overloads = {
                    @LuaMethodOverload(
                            argumentTypes = FiguraVec3.class,
                            argumentNames = "pos"
                    ),
                    @LuaMethodOverload(
                            argumentTypes = {Double.class, Double.class, Double.class},
                            argumentNames = {"x", "y", "z"}
                    )
            },
            aliases = "pos",
            value = "particle.set_pos")
    public LuaParticle setPos(Object x, Double y, Double z) {
        FiguraVec3 vec = LuaUtils.parseVec3("setPos", x, y, z);
        particle.setPos(vec.x, vec.y, vec.z);

        ParticleAccessor p = (ParticleAccessor) particle;
        p.setXo(vec.x);
        p.setYo(vec.y);
        p.setZo(vec.z);

        return this;
    }

    @LuaWhitelist
    public LuaParticle pos(Object x, Double y, Double z) {
        return setPos(x, y, z);
    }

    @LuaWhitelist
    @LuaMethodDoc("particle.get_velocity")
    public FiguraVec3 getVelocity() {
        ParticleAccessor p = (ParticleAccessor) particle;
        return FiguraVec3.of(p.getXd(), p.getYd(), p.getZd());
    }

    @LuaWhitelist
    @LuaMethodDoc(
            overloads = {
                    @LuaMethodOverload(
                            argumentTypes = FiguraVec3.class,
                            argumentNames = "velocity"
                    ),
                    @LuaMethodOverload(
                            argumentTypes = {Double.class, Double.class, Double.class},
                            argumentNames = {"x", "y", "z"}
                    )
            },
            aliases = "velocity",
            value = "particle.set_velocity")
    public LuaParticle setVelocity(Object x, Double y, Double z) {
        FiguraVec3 vec = LuaUtils.parseVec3("setVelocity", x, y, z);
        particle.setParticleSpeed(vec.x, vec.y, vec.z);
        return this;
    }

    @LuaWhitelist
    public LuaParticle velocity(Object x, Double y, Double z) {
        return setVelocity(x, y, z);
    }

    @LuaWhitelist
    @LuaMethodDoc("particle.get_color")
    public FiguraVec4 getColor() {
        if (!(particle instanceof SingleQuadParticle)) {
            return FiguraVec4.of(1, 1, 1, 1);
        }
        SingleQuadParticleAccessor p = (SingleQuadParticleAccessor) particle;
        return FiguraVec4.of(p.figura$getRCol(), p.figura$getGCol(), p.figura$getBCol(), p.figura$getAlpha());
    }

    @LuaWhitelist
    @LuaMethodDoc(
            overloads = {
                    @LuaMethodOverload(
                            argumentTypes = FiguraVec3.class,
                            argumentNames = "rgb"
                    ),
                    @LuaMethodOverload(
                            argumentTypes = FiguraVec4.class,
                            argumentNames = "rgba"
                    ),
                    @LuaMethodOverload(
                            argumentTypes = {Double.class, Double.class, Double.class, Double.class},
                            argumentNames = {"r", "g", "b", "a"}
                    )
            },
            aliases = "color",
            value = "particle.set_color")
    public LuaParticle setColor(Object r, Double g, Double b, Double a) {
        FiguraVec4 vec = LuaUtils.parseVec4("setColor", r, g, b, a, 1, 1, 1, 1);
        if (particle instanceof CustomParticle) {
            requireFinite((float) vec.x, (float) vec.y, (float) vec.z, (float) vec.w);
        }
        if (particle instanceof SingleQuadParticle quadParticle) {
            quadParticle.setColor((float) vec.x, (float) vec.y, (float) vec.z);
            ((SingleQuadParticleAccessor) particle).figura$setParticleAlpha((float) vec.w);
        }
        return this;
    }

    @LuaWhitelist
    public LuaParticle color(Object r, Double g, Double b, Double a) {
        return setColor(r, g, b, a);
    }

    @LuaWhitelist
    @LuaMethodDoc("particle.get_lifetime")
    public int getLifetime() {
        return particle.getLifetime();
    }

    @LuaWhitelist
    @LuaMethodDoc(
            overloads = @LuaMethodOverload(
                    argumentTypes = Integer.class,
                    argumentNames = "lifetime"
            ),
            aliases = "lifetime",
            value = "particle.set_lifetime")
    public LuaParticle setLifetime(int age) {
        particle.setLifetime(Math.max(particle instanceof WakeParticle ? Math.min(age, 60) : age, 0));
        return this;
    }

    @LuaWhitelist
    public LuaParticle lifetime(int age) {
        return setLifetime(age);
    }

    @LuaWhitelist
    @LuaMethodDoc("particle.get_power")
    public float getPower() {
        return power;
    }

    @LuaWhitelist
    @LuaMethodDoc(
            overloads = @LuaMethodOverload(
                    argumentTypes = Float.class,
                    argumentNames = "power"
            ),
            aliases = "power",
            value = "particle.set_power")
    public LuaParticle setPower(float power) {
        if (particle instanceof CustomParticle) {
            requireFinite(power);
        }
        particle.setPower(power);
        this.power = power;
        return this;
    }

    @LuaWhitelist
    public LuaParticle power(float power) {
        return setPower(power);
    }

    @LuaWhitelist
    @LuaMethodDoc(aliases = "getSize", value = "particle.get_scale")
    public float getScale() {
        return ((ParticleAccessor) particle).getBbWidth() / 0.2f;
    }

    @LuaWhitelist
    public float getSize() {
        return getScale();
    }

    @LuaWhitelist
    @LuaMethodDoc(
            overloads = @LuaMethodOverload(
                    argumentTypes = Float.class,
                    argumentNames = "scale"
            ),
            aliases = {"scale", "setSize", "size"},
            value = "particle.set_scale")
    public LuaParticle setScale(float scale) {
        if (particle instanceof CustomParticle) {
            requireFinite(scale);
            if (scale <= 0) {
                throw new LuaError("Custom particle scale must be positive");
            }
        }
        if (particle instanceof SingleQuadParticle quadParticle)
            ((SingleQuadParticleAccessor) quadParticle).figura$fixQuadSize();
        particle.scale(scale);
        return this;
    }

    @LuaWhitelist
    public LuaParticle scale(float scale) {
        return setScale(scale);
    }

    @LuaWhitelist
    public LuaParticle setSize(float scale) {
        return setScale(scale);
    }

    @LuaWhitelist
    public LuaParticle size(float scale) {
        return setScale(scale);
    }

    @LuaWhitelist
    @LuaMethodDoc("particle.get_gravity")
    public float getGravity() {
        return ((ParticleAccessor) particle).getGravity();
    }

    @LuaWhitelist
    @LuaMethodDoc(
            overloads = @LuaMethodOverload(
                    argumentTypes = Float.class,
                    argumentNames = "gravity"
            ),
            aliases = "gravity",
            value = "particle.set_gravity")
    public LuaParticle setGravity(float gravity) {
        if (particle instanceof CustomParticle) {
            requireFinite(gravity);
        }
        ((ParticleAccessor) particle).setGravity(gravity);
        return this;
    }

    private static void requireFinite(float... values) {
        for (float value : values) {
            if (!Float.isFinite(value)) {
                throw new LuaError("Custom particle settings must be finite");
            }
        }
    }

    @LuaWhitelist
    public LuaParticle gravity(float gravity) {
        return setGravity(gravity);
    }

    @LuaWhitelist
    @LuaMethodDoc("particle.has_physics")
    public boolean hasPhysics() {
        return ((ParticleAccessor) particle).getHasPhysics();
    }

    @LuaWhitelist
    @LuaMethodDoc(
            overloads = @LuaMethodOverload(
                    argumentTypes = Boolean.class,
                    argumentNames = "physics"
            ),
            aliases = "physics",
            value = "particle.set_physics")
    public LuaParticle setPhysics(boolean physics) {
        ((ParticleAccessor) particle).setHasPhysics(physics);
        return this;
    }

    @LuaWhitelist
    public LuaParticle physics(boolean physics) {
        return setPhysics(physics);
    }

    public String toString() {
        return name + " (Particle)";
    }
}
