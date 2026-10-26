package top.aihmc.omnimorph.mixin;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.FishingHook;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Same fields as the reference FishingHookAccessor, for ysm.is_biting and ysm.hooked_in. */
@Mixin(FishingHook.class)
public interface FishingHookAccessor {
    @Accessor("biting")
    boolean figura$isBiting();

    @Accessor("hookedIn")
    Entity figura$getHookedIn();
}
