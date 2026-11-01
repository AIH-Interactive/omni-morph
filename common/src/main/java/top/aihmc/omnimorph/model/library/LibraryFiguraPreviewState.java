package top.aihmc.omnimorph.model.library;

import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.model.AnimationUtils;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.PlayerModelPart;
import org.figuramc.figura.avatar.Avatar;
import top.aihmc.omnimorph.render.EntityFrame;

/** Frozen skin and standing pose inputs; drawing performs no entity lookup or dispatch. */
public record LibraryFiguraPreviewState(Avatar avatar, PlayerPose playerPose, float delta,
                                       float pitch, float yaw, float offsetX, float offsetY,
                                       int x0, int y0, int x1, int y1, float scale,
                                       ScreenRectangle scissorArea, ScreenRectangle bounds) {
    public record PlayerPose(EntityFrame frame, ResourceLocation skin, boolean slim, float ageInTicks,
                             boolean showHat, boolean showJacket, boolean showLeftSleeve,
                             boolean showRightSleeve, boolean showLeftPants, boolean showRightPants) {
        static PlayerPose capture(AbstractClientPlayer player, float delta) {
            return new PlayerPose(new EntityFrame(player, delta), player.getSkinTextureLocation(),
                    "slim".equals(player.getModelName()), player.tickCount + delta,
                    player.isModelPartShown(PlayerModelPart.HAT), player.isModelPartShown(PlayerModelPart.JACKET),
                    player.isModelPartShown(PlayerModelPart.LEFT_SLEEVE), player.isModelPartShown(PlayerModelPart.RIGHT_SLEEVE),
                    player.isModelPartShown(PlayerModelPart.LEFT_PANTS_LEG), player.isModelPartShown(PlayerModelPart.RIGHT_PANTS_LEG));
        }

        /** The native standing pose, with no live swimming, item-use, riding or attack state. */
        void apply(PlayerModel<?> model) {
            for (ModelPart part : new ModelPart[]{model.head, model.hat, model.body, model.leftArm, model.rightArm,
                    model.leftLeg, model.rightLeg, model.jacket, model.leftSleeve, model.rightSleeve,
                    model.leftPants, model.rightPants}) {
                part.resetPose();
                part.visible = true;
            }
            model.young = false;
            model.riding = false;
            model.crouching = false;
            model.attackTime = 0;
            model.swimAmount = 0;
            model.leftArmPose = HumanoidModel.ArmPose.EMPTY;
            model.rightArmPose = HumanoidModel.ArmPose.EMPTY;
            model.leftArm.y = 2;
            model.rightArm.y = 2;
            model.rightLeg.yRot = 0.005f;
            model.rightLeg.zRot = 0.005f;
            model.leftLeg.yRot = -0.005f;
            model.leftLeg.zRot = -0.005f;
            AnimationUtils.bobModelPart(model.rightArm, ageInTicks, 1);
            AnimationUtils.bobModelPart(model.leftArm, ageInTicks, -1);
            model.hat.copyFrom(model.head);
            model.jacket.copyFrom(model.body);
            model.leftSleeve.copyFrom(model.leftArm);
            model.rightSleeve.copyFrom(model.rightArm);
            model.leftPants.copyFrom(model.leftLeg);
            model.rightPants.copyFrom(model.rightLeg);
            model.hat.visible = showHat;
            model.jacket.visible = showJacket;
            model.leftSleeve.visible = showLeftSleeve;
            model.rightSleeve.visible = showRightSleeve;
            model.leftPants.visible = showLeftPants;
            model.rightPants.visible = showRightPants;
        }
    }
}
