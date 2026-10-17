package top.aihmc.omnimorph.mixin;

import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.Consumable;
import top.aihmc.omnimorph.avatar.Avatar;
import top.aihmc.omnimorph.avatar.AvatarManager;
import top.aihmc.omnimorph.lua.api.world.ItemStackAPI;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Consumable.class)
public class ConsumableMixin {
    @Inject(method = "emitParticlesAndSounds", at = @At("HEAD"), cancellable = true)
    void triggerItemUseEvent(RandomSource random, LivingEntity entity, ItemStack stack, int i, CallbackInfo ci) {
        Avatar avatar = AvatarManager.getAvatar(entity);
        if (avatar != null && avatar.useItemEvent(ItemStackAPI.verify(stack), stack.getUseAnimation().name(), i))
            ci.cancel();
    }
}
