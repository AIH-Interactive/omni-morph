package top.aihmc.omnimorph.mixin.render.model;

import net.minecraft.client.model.geom.PartPose;
import top.aihmc.omnimorph.ducks.PartPoseExtension;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(PartPose.class)
public class PartPoseMixin implements PartPoseExtension {
    @Unique
    Boolean figura$isVisible = null;

    @Override
    public Boolean figura$isVisible() {
        return figura$isVisible;
    }

    @Override
    public void figura$setVisible(Boolean visible) {
        this.figura$isVisible = visible;
    }

}
