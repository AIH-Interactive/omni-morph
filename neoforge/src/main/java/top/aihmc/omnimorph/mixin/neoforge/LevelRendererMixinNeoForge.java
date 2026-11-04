package top.aihmc.omnimorph.mixin.neoforge;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.state.level.LevelRenderState;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.figuramc.figura.avatar.Avatar;
import org.figuramc.figura.avatar.AvatarManager;
import org.figuramc.figura.config.Configs;
import org.figuramc.figura.math.matrix.FiguraMat3;
import org.figuramc.figura.mixin.render.PoseStackAccessor;
import org.figuramc.figura.utils.RenderUtils;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import top.aihmc.omnimorph.ducks.NodeCollectorExtension;

@Mixin(LevelRenderer.class)
public class LevelRendererMixinNeoForge {
    @Shadow @Final private EntityRenderDispatcher entityRenderDispatcher;

    @Inject(method = "submitFeatures", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/LevelRenderer;checkPoseStack(Lcom/mojang/blaze3d/vertex/PoseStack;)V"))
    private void renderLevelFirstPerson(LevelRenderState levelRenderState, SubmitNodeCollector collector,
                                        boolean renderOutline, CallbackInfo ci) {
        Minecraft minecraft = Minecraft.getInstance();
        Camera camera = minecraft.gameRenderer.mainCamera();
        if (camera.isDetached()) {
            return;
        }

        Entity entity = camera.entity();
        Avatar avatar = AvatarManager.getAvatar(entity);
        if (avatar == null || !(entity instanceof LivingEntity livingEntity) || avatar.isYsmNative()) {
            return;
        }

        DeltaTracker deltaTracker = minecraft.getDeltaTracker();
        float tickDelta = deltaTracker.getGameTimeDeltaPartialTick(false);
        EntityRenderer<LivingEntity, LivingEntityRenderState> renderer =
                (EntityRenderer<LivingEntity, LivingEntityRenderState>) entityRenderDispatcher.getRenderer(livingEntity);
        LivingEntityRenderState state = renderer.createRenderState(livingEntity,
                deltaTracker.getGameTimeDeltaPartialTick(minecraft.level.tickRateManager().isEntityFrozen(entity)));
        boolean updateMatrices = Configs.FIRST_PERSON_MATRICES.value;

        // Emit into the world's native preparation, before its shared upload, never during execution.
        ((NodeCollectorExtension) collector).submitFiguraModel(avatar, state, (submittedAvatar, submittedState, vertices) -> {
            PoseStack stack = new PoseStack();
            submittedAvatar.firstPersonWorldRender(entity, vertices, stack, camera, tickDelta);
            if (updateMatrices) {
                boolean previousFirstPerson = Avatar.firstPerson;
                int lastIndex = ((PoseStackAccessor) stack).getLastIndex();
                stack.pushPose();
                try {
                    Avatar.firstPerson = true;
                    Vec3 offset = renderer.getRenderOffset(submittedState);
                    Vec3 cam = camera.position();
                    stack.translate(
                            Mth.lerp(tickDelta, livingEntity.xOld, livingEntity.getX()) - cam.x() + offset.x(),
                            Mth.lerp(tickDelta, livingEntity.yOld, livingEntity.getY()) - cam.y() + offset.y(),
                            Mth.lerp(tickDelta, livingEntity.zOld, livingEntity.getZ()) - cam.z() + offset.z());
                    renderer.submit(submittedState, stack, collector, levelRenderState.cameraRenderState);
                } finally {
                    while (((PoseStackAccessor) stack).getLastIndex() > lastIndex) {
                        stack.popPose();
                    }
                    Avatar.firstPerson = previousFirstPerson;
                }
            }
            return null;
        });
    }

    @Inject(method = "submitEntities", at = @At("HEAD"))
    private void applyFiguraNormals(PoseStack stack, LevelRenderState levelRenderState,
                                    SubmitNodeCollector collector, CallbackInfo ci) {
        Minecraft minecraft = Minecraft.getInstance();
        Avatar avatar = AvatarManager.getAvatar(minecraft.getCameraEntity() == null
                ? minecraft.player : minecraft.getCameraEntity());
        if (!RenderUtils.vanillaModelAndScript(avatar)) {
            return;
        }

        FiguraMat3 normal = avatar.luaRuntime.renderer.cameraNormal;
        if (normal != null) {
            stack.last().normal().set(normal.toMatrix3f());
        }
    }
}
