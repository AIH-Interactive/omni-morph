package top.aihmc.omnimorph.mixin;

import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import top.aihmc.omnimorph.ducks.PlayerModelCapeAccessor;

import java.util.List;
import java.util.Map;

@Mixin(PlayerModel.class)
public abstract class PlayerModelMixin<T extends LivingEntity> extends HumanoidModel<T> implements PlayerModelCapeAccessor {
    // Fake cape ModelPart which we set rotations of: the vanilla cape renderer
    // positions the cloak through the matrix stack instead of part rotations.
    @Unique
    private final ModelPart figura$fakeCloak = new ModelPart(List.of(), Map.of());

    @Final
    @Shadow
    private ModelPart cloak;

    public PlayerModelMixin(ModelPart modelPart) {
        super(modelPart);
    }

    @Override
    public ModelPart figura$getCloak() {
        return cloak;
    }

    @Override
    public ModelPart figura$getFakeCloak() {
        return figura$fakeCloak;
    }

    @Inject(at = @At("HEAD"), method = "setupAnim(Lnet/minecraft/world/entity/LivingEntity;FFFFF)V")
    private void showHeadOnAnim(CallbackInfo ci) {
        this.head.visible = true;
    }
}
