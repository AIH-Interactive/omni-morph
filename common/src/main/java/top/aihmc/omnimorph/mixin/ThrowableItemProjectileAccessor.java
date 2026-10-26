package top.aihmc.omnimorph.mixin;

import net.minecraft.world.entity.projectile.throwableitemprojectile.ThrowableItemProjectile;
import net.minecraft.world.item.Item;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** Same invoker as the reference ThrowableItemProjectileAccessor, for ysm.throwable_item. */
@Mixin(ThrowableItemProjectile.class)
public interface ThrowableItemProjectileAccessor {
    @Invoker("getDefaultItem")
    Item figura$getDefaultItem();
}
