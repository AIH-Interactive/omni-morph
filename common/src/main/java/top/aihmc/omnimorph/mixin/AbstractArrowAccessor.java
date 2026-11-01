package top.aihmc.omnimorph.mixin;

import net.minecraft.world.entity.projectile.AbstractArrow;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;
import net.minecraft.world.item.ItemStack;

@Mixin(AbstractArrow.class)
public interface AbstractArrowAccessor {
    @Accessor("inGround")
    boolean figura$isInGround();

    @Accessor("inGroundTime")
    int figura$getInGroundTime();

    @Invoker("getPickupItem")
    ItemStack figura$getPickupItem();
}
