package top.aihmc.omnimorph.mixin;

import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import top.aihmc.omnimorph.avatar.Avatar;
import top.aihmc.omnimorph.avatar.AvatarManager;
import top.aihmc.omnimorph.lua.api.entity.EntityAPI;
import top.aihmc.omnimorph.lua.api.world.ItemStackAPI;
import top.aihmc.omnimorph.math.vector.FiguraVec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LivingEntity.class)
public abstract class LivingEntityMixin extends Entity {

    public LivingEntityMixin(EntityType<?> type, Level world) {
        super(type, world);
    }

    @Inject(at = @At("TAIL"), method = "handleDamageEvent")
    private void handleDamageEvent(DamageSource source, CallbackInfo ci) {
        Avatar avatar = AvatarManager.getAvatar(this);
        if (avatar == null) return;
        avatar.damageEvent(
                source.typeHolder().unwrapKey().get().identifier().toString(),
                EntityAPI.wrap(source.getEntity()),
                EntityAPI.wrap(source.getDirectEntity()),
                source.getSourcePosition() != null ? FiguraVec3.fromVec3(source.getSourcePosition()) : null
        );
    }
}
