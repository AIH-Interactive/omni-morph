package top.aihmc.omnimorph.lua.api;

import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import top.aihmc.omnimorph.lua.LuaWhitelist;
import top.aihmc.omnimorph.lua.docs.LuaTypeDoc;
import top.aihmc.omnimorph.math.vector.FiguraVec2;
import top.aihmc.omnimorph.math.vector.FiguraVec3;

/** Read-only wearer queries, with no route to another entity, Avatar or model. */
@LuaWhitelist
@LuaTypeDoc(name = "CosmeticPlayerAPI", value = "cosmetic_player")
public final class CosmeticPlayerAPI {
    private final Entity wearer;

    public CosmeticPlayerAPI(Entity wearer) {
        this.wearer = wearer;
    }

    @LuaWhitelist
    public String getUUID() {
        return wearer.getUUID().toString();
    }

    @LuaWhitelist
    public String getName() {
        return wearer.getName().getString();
    }

    @LuaWhitelist
    public FiguraVec3 getPos(Float delta) {
        float partial = delta == null ? 1f : delta;
        return FiguraVec3.of(Mth.lerp(partial, wearer.xOld, wearer.getX()),
                Mth.lerp(partial, wearer.yOld, wearer.getY()),
                Mth.lerp(partial, wearer.zOld, wearer.getZ()));
    }

    @LuaWhitelist
    public FiguraVec2 getRot(Float delta) {
        float partial = delta == null ? 1f : delta;
        return FiguraVec2.of(Mth.lerp(partial, wearer.xRotO, wearer.getXRot()),
                Mth.lerp(partial, wearer.yRotO, wearer.getYRot()));
    }

    @LuaWhitelist
    public FiguraVec3 getVelocity() {
        return FiguraVec3.fromVec3(wearer.getDeltaMovement());
    }

    @LuaWhitelist
    public boolean isLoaded() {
        return !wearer.isRemoved();
    }

    @LuaWhitelist
    public boolean isSneaking() {
        return wearer.isShiftKeyDown();
    }

    @LuaWhitelist
    public boolean isOnGround() {
        return wearer.onGround();
    }

    @LuaWhitelist
    public String getPose() {
        return wearer.getPose().name();
    }

    @LuaWhitelist
    public float getHealth() {
        return wearer instanceof LivingEntity living ? living.getHealth() : 0;
    }
}
