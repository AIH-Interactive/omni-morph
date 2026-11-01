package top.aihmc.omnimorph.mixin.render.model;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import top.aihmc.omnimorph.ducks.PartPoseExtension;

@Mixin(ModelPart.class)
public class ModelPartMixin {
    @Shadow
    public boolean visible;

    @ModifyReturnValue(method = "storePose", at = @At("RETURN"))
    public PartPose figura$storeVisibility(PartPose original) {
        ((PartPoseExtension) original).figura$setVisible(visible);
        return original;
    }

    @Inject(method = "loadPose", at = @At("HEAD"))
    public void figura$loadVisibility(PartPose pose, CallbackInfo ci) {
        Boolean storedVisibility = ((PartPoseExtension) pose).figura$isVisible();
        if (storedVisibility != null) {
            visible = storedVisibility;
        }
    }
}
