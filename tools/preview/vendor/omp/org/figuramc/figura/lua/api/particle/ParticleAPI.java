package org.figuramc.figura.lua.api.particle;

import org.figuramc.figura.lua.LuaNotNil;
import org.figuramc.figura.lua.LuaWhitelist;
import org.figuramc.figura.lua.docs.LuaMethodDoc;
import org.figuramc.figura.lua.docs.LuaTypeDoc;
import org.figuramc.figura.math.vector.FiguraVec3;
import top.aihmc.omm.preview.PreviewParticles;
import top.aihmc.omm.preview.PreviewVectorArguments;

@LuaWhitelist
@LuaTypeDoc(name = "ParticleAPI", value = "particles")
public class ParticleAPI {
    private final PreviewParticles owner;

    public ParticleAPI(PreviewParticles owner) {
        this.owner = owner;
    }

    private LuaParticle generate(String id, double x, double y, double z, double w, double t, double h) {
        return new LuaParticle(id, owner.create(id, x, y, z, w, t, h), owner);
    }

    @LuaWhitelist
    @LuaMethodDoc("particles.new_particle")
    public LuaParticle newParticle(@LuaNotNil String id, Object x, Object y, Double z, Object w, Double t, Double h) {
        FiguraVec3 pos, vel;

        // Parse pos and vel
        PreviewVectorArguments.Pair<FiguraVec3, FiguraVec3> pair =
            PreviewVectorArguments.parse2Vec3("newParticle", x, y, z, w, t, h, 2);
        pos = pair.getFirst();
        vel = pair.getSecond();

        LuaParticle particle = generate(id, pos.x, pos.y, pos.z, vel.x, vel.y, vel.z);
        particle.spawn();
        return particle;
    }

    @LuaWhitelist
    @LuaMethodDoc("particles.remove_particles")
    public ParticleAPI removeParticles() {
        owner.clear();
        return this;
    }

    @LuaWhitelist
    @LuaMethodDoc("particles.is_present")
    public boolean isPresent(String id) {
        return owner.isPresent(id);
    }

    @LuaWhitelist
    public LuaParticle __index(String id) {
        return generate(id, 0, 0, 0, 0, 0, 0);
    }

    @Override
    public String toString() {
        return "ParticleAPI";
    }
}
