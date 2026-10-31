package top.aihmc.omnimorph.model.outfit;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.figuramc.figura.avatar.Avatar;
import org.figuramc.figura.model.rendering.EntityRenderMode;
import org.figuramc.figura.model.rendering.PartFilterScheme;
import top.aihmc.omnimorph.model.ysm.YsmModelRuntime;
import top.aihmc.omnimorph.model.ysm.YsmRenderer;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;

/**
 * Runs the local outfit's accessory scripts once per first-person frame with a full-body pose.
 *
 * <p>Vanilla skips the local player's body render in first person, so the outfit composition is normally built
 * from the arm-only skeleton: body-mounted accessories never reach {@code prepare()}, their {@code RENDER} /
 * {@code POST_RENDER} scripts stop, and particle trails vanish. This pass replays the composition with the
 * camera-relative body pose and submits all geometry to a discard buffer, so scripts, trails and particles keep
 * running while the visible first-person arm is still drawn by the regular hand pass.
 */
public final class OutfitFirstPersonScripts {
    /** Every submission is discarded; scripts still compute part-to-world matrices and spawn particles. */
    private static final InvocationHandler DISCARD = (proxy, method, args) -> {
        if (method.isDefault()) {
            return InvocationHandler.invokeDefault(proxy, method, args);
        }
        return method.getReturnType() == void.class ? null : proxy;
    };

    private OutfitFirstPersonScripts() {
    }

    public static void render(Minecraft minecraft, LocalPlayer player, float delta) {
        if (minecraft == null || player == null || player.level() == null
                || !OutfitRenderBinding.hasComposition(player)) {
            return;
        }
        var dispatcher = minecraft.getEntityRenderDispatcher();
        var renderer = dispatcher.getRenderer(player);
        if (!(renderer instanceof LivingEntityRenderer<?, ?, ?>)) {
            return;
        }
        @SuppressWarnings({"rawtypes", "unchecked"})
        EntityRenderState created = ((LivingEntityRenderer) renderer).createRenderState(player, delta);
        if (!(created instanceof AvatarRenderState state)) {
            return;
        }
        state.lightCoords = dispatcher.getPackedLightCoords(player, delta);
        OutfitRenderBinding.capture(player, state, delta, true);
        var composition = OutfitRenderBinding.composition(state);
        Avatar base = OutfitRenderBinding.avatar(state);
        if (composition == null || base == null || base.renderer == null) {
            return;
        }
        Vec3 camera = minecraft.gameRenderer.getMainCamera().getPosition();
        Vec3 feet = player.getPosition(delta);
        Matrix4f entityToDraw = new Matrix4f().translate(
                (float) (feet.x - camera.x), (float) (feet.y - camera.y), (float) (feet.z - camera.z));
        float bodyYaw = Mth.rotLerp(delta, player.yBodyRotO, player.yBodyRot);
        MultiBufferSource discard = type -> (VertexConsumer) Proxy.newProxyInstance(
                OutfitFirstPersonScripts.class.getClassLoader(),
                new Class<?>[]{VertexConsumer.class}, DISCARD);
        EntityRenderMode previousMode = base.renderMode;
        boolean previousMatrixUpdate = base.renderer.allowMatrixUpdate;
        base.renderMode = EntityRenderMode.FIRST_PERSON_WORLD;
        // The base is not drawn in first person, so its part-to-world matrices would stay frozen at the last
        // third-person pose. Updating them here lets the base scripts emit particles and lights at the body.
        base.renderer.allowMatrixUpdate = true;
        try {
            if (base.isYsmNative()) {
                drawYsm(composition, base, state, player, delta, entityToDraw, bodyYaw, discard);
            } else {
                drawFigura(composition, base, state, player, delta, entityToDraw, bodyYaw, discard);
            }
        } finally {
            base.renderer.allowMatrixUpdate = previousMatrixUpdate;
            base.renderMode = previousMode;
        }
    }

    private static void drawYsm(OutfitComposition.Frame composition, Avatar base, AvatarRenderState state,
                                LocalPlayer player, float delta, Matrix4f entityToDraw, float bodyYaw,
                                MultiBufferSource discard) {
        YsmModelRuntime runtime = base.getYsmRuntime() == null ? null : base.getYsmRuntime().forRenderPass();
        if (runtime == null) {
            return;
        }
        runtime.updateAnimations(state, player);
        PoseStack stack = new PoseStack();
        stack.last().pose().set(entityToDraw);
        stack.mulPose(Axis.YP.rotationDegrees(180.0f - bodyYaw));
        YsmRenderer.applyEntityRotation(stack, state, bodyYaw);
        Matrix4f nativeToEntity = new Matrix4f(entityToDraw).invert().mul(stack.last().pose());
        var skeleton = composition.captureYsmBase(runtime, nativeToEntity, runtime.captureRenderPose(false));
        composition.draw(skeleton, state, delta, EntityRenderMode.FIRST_PERSON_WORLD,
                command -> OutfitNativeRenderer.drawInstance(command, delta, entityToDraw, discard,
                        state.lightCoords, EntityRenderMode.FIRST_PERSON_WORLD));
    }

    private static void drawFigura(OutfitComposition.Frame composition, Avatar base, AvatarRenderState state,
                                   LocalPlayer player, float delta, Matrix4f entityToDraw, float bodyYaw,
                                   MultiBufferSource discard) {
        PoseStack stack = new PoseStack();
        stack.last().pose().set(entityToDraw);
        stack.mulPose(Axis.YP.rotationDegrees(180.0f - bodyYaw));
        stack.scale(-1.0f, -1.0f, 1.0f);
        var pose = composition.captureFiguraBase(base, new Matrix4f(entityToDraw).invert(),
                () -> base.render(player, 0, delta, 1.0f, stack, discard, state.lightCoords,
                        OverlayTexture.NO_OVERLAY, null, PartFilterScheme.MODEL, false, false));
        composition.draw(pose, state, delta, EntityRenderMode.FIRST_PERSON_WORLD,
                command -> OutfitNativeRenderer.drawInstance(command, delta, entityToDraw, discard,
                        state.lightCoords, EntityRenderMode.FIRST_PERSON_WORLD));
    }
}
