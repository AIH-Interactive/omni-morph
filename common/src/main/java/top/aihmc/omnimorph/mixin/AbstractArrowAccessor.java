package top.aihmc.omnimorph.mixin;

import net.minecraft.world.entity.projectile.AbstractArrow;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(AbstractArrow.class)
public interface AbstractArrowAccessor {
    @Invoker("isInGround")
    boolean figura$isInGround();

    @Accessor("inGroundTime")
    int figura$getInGroundTime();
}
