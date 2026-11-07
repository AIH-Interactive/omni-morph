package org.figuramc.figura.lua.api.particle;

import org.figuramc.figura.lua.LuaWhitelist;
import org.figuramc.figura.lua.docs.LuaMethodDoc;
import org.figuramc.figura.lua.docs.LuaTypeDoc;
import org.figuramc.figura.math.vector.FiguraVec3;
import org.figuramc.figura.math.vector.FiguraVec4;
import top.aihmc.omm.preview.PreviewParticles;
import top.aihmc.omm.preview.PreviewVectorArguments;

@LuaWhitelist
@LuaTypeDoc(name = "Particle", value = "particle")
public class LuaParticle {
    private final String name;
    private final PreviewParticles owner;
    private final PreviewParticles.Particle particle;

    private float power;

    public LuaParticle(String name, PreviewParticles.Particle particle, PreviewParticles owner) {
        this.name = name;
        this.particle = particle;
        this.owner = owner;
    }

    @LuaWhitelist
    @LuaMethodDoc("particle.spawn")
    public LuaParticle spawn() {
        owner.spawn(particle);
        return this;
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
        return particle.isAlive();
    }

    @LuaWhitelist
    @LuaMethodDoc("particle.get_pos")
    public FiguraVec3 getPos() {
        PreviewParticles.Particle p = particle;
        return FiguraVec3.of(p.getX(), p.getY(), p.getZ());
    }

    @LuaWhitelist
    @LuaMethodDoc("particle.set_pos")
    public LuaParticle setPos(Object x, Double y, Double z) {
        FiguraVec3 vec = PreviewVectorArguments.parseVec3("setPos", x, y, z);
        particle.setPos(vec.x, vec.y, vec.z);

        PreviewParticles.Particle p = particle;
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
        PreviewParticles.Particle p = particle;
        return FiguraVec3.of(p.getXd(), p.getYd(), p.getZd());
    }

    @LuaWhitelist
    @LuaMethodDoc("particle.set_velocity")
    public LuaParticle setVelocity(Object x, Double y, Double z) {
        FiguraVec3 vec = PreviewVectorArguments.parseVec3("setVelocity", x, y, z);
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
        PreviewParticles.Particle p = particle;
        return FiguraVec4.of(p.getRCol(), p.getGCol(), p.getBCol(), p.getAlpha());
    }

    @LuaWhitelist
    @LuaMethodDoc("particle.set_color")
    public LuaParticle setColor(Object r, Double g, Double b, Double a) {
        FiguraVec4 vec = PreviewVectorArguments.parseVec4("setColor", r, g, b, a, 1, 1, 1, 1);
        particle.setColor((float) vec.x, (float) vec.y, (float) vec.z);
        particle.setParticleAlpha((float) vec.w);
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
    @LuaMethodDoc("particle.set_lifetime")
    public LuaParticle setLifetime(int age) {
        particle.setLifetime(Math.max(age, 0));
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
    @LuaMethodDoc("particle.set_power")
    public LuaParticle setPower(float power) {
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
        return particle.getBbWidth() / 0.2f;
    }

    @LuaWhitelist
    public float getSize() {
        return getScale();
    }

    @LuaWhitelist
    @LuaMethodDoc("particle.set_scale")
    public LuaParticle setScale(float scale) {
        particle.fixQuadSize();
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
        return particle.getGravity();
    }

    @LuaWhitelist
    @LuaMethodDoc("particle.set_gravity")
    public LuaParticle setGravity(float gravity) {
        particle.setGravity(gravity);
        return this;
    }

    @LuaWhitelist
    public LuaParticle gravity(float gravity) {
        return setGravity(gravity);
    }

    @LuaWhitelist
    @LuaMethodDoc("particle.has_physics")
    public boolean hasPhysics() {
        return particle.getHasPhysics();
    }

    @LuaWhitelist
    @LuaMethodDoc("particle.set_physics")
    public LuaParticle setPhysics(boolean physics) {
        particle.setHasPhysics(physics);
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
