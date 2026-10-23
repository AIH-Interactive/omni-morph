package top.aihmc.omnimorph.model.outfit;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.model.player.PlayerModel;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import top.aihmc.omnimorph.avatar.Avatar;
import top.aihmc.omnimorph.math.matrix.FiguraMat4;
import top.aihmc.omnimorph.model.library.PreviewBoneHighlight;
import top.aihmc.omnimorph.model.rendering.EntityRenderMode;
import top.aihmc.omnimorph.model.rendering.PartFilterScheme;

/** Converts the controller's model-unit matrices into each existing native renderer's input space. */
public final class OutfitNativeRenderer {
    private OutfitNativeRenderer() {}

    public static void drawVanilla(OutfitComposition.Frame frame, PlayerModel model, LivingEntityRenderState state,
                                   float delta, Matrix4fc entityToDraw, Matrix4fc modelToDraw,
                                   MultiBufferSource buffers, EntityRenderMode mode) {
        var basePose = frame.captureVanillaBase(model, new Matrix4f(entityToDraw).invert().mul(modelToDraw));
        draw(frame, basePose, state, delta, entityToDraw, buffers, mode);
    }

    public static void drawVanillaArm(OutfitComposition.Frame frame, PlayerModel model,
                                      LivingEntityRenderState state, float delta, Matrix4fc armToDraw,
                                      MultiBufferSource buffers, boolean left) {
        var basePose = frame.captureVanillaArm(model, new Matrix4f(), left);
        draw(frame, basePose, state, delta, armToDraw, buffers, EntityRenderMode.FIRST_PERSON);
    }

    public static void draw(OutfitComposition.Frame frame, SkeletonPose basePose,
                            LivingEntityRenderState state, float delta, Matrix4fc entityToDraw,
                            MultiBufferSource buffers, EntityRenderMode mode) {
        frame.withPreviewSelection(() -> frame.draw(basePose, state, delta, mode,
                command -> frame.withPreviewSelection(() ->
                        drawInstance(frame, command, delta, entityToDraw, buffers, state.lightCoords, mode))));
        if (mode == EntityRenderMode.FIGURA_GUI) {
            PreviewBoneHighlight.drawSkeleton(frame.previewHelpers(basePose), entityToDraw, buffers);
        }
    }

    public static void drawInstance(OutfitComposition.Draw command, float delta, Matrix4fc entityToDraw,
                                    MultiBufferSource buffers, int light, EntityRenderMode mode) {
        drawInstance(null, command, delta, entityToDraw, buffers, light, mode);
    }

    private static void drawInstance(OutfitComposition.Frame frame, OutfitComposition.Draw command, float delta,
                                     Matrix4fc entityToDraw, MultiBufferSource buffers, int light, EntityRenderMode mode) {
        Avatar avatar = command.avatar();
        if (!avatar.loaded || avatar.scriptError || !avatar.accelerationOwner().isCurrent()) {
            return;
        }
        EntityRenderMode previous = avatar.renderMode;
        avatar.renderMode = mode;
        try {
            PoseStack stack = new PoseStack();
            Matrix4f mounted = new Matrix4f(entityToDraw).scale(1f / 16).mul(command.matrix());
            if (avatar.isYsmNative()) {
                if (frame != null) {
                    frame.captureYsmInstance(command);
                }
                // YSM's original renderer consumes block-space geometry and keeps its own model scale.
                stack.mulPose(mounted.scale(16));
                avatar.getYsmRuntime().renderer().renderWithAttachments(stack, buffers, light, command.ysmPose());
                avatar.postRenderEvent(delta, FiguraMat4.of());
            } else {
                // Figura normally prepends T(0,1.5,0) * Rz(180) * S(1/16) to authored geometry.
                // Cancel that basis at its input, leaving the mount matrix followed by authored transforms.
                mounted.scale(16).rotateZ((float) Math.PI).translate(0, -1.5f, 0);
                stack.mulPose(mounted);
                var wearer = avatar.luaRuntime == null ? null : avatar.luaRuntime.getUser();
                command.figuraPose().draw(() -> {
                    Runnable nativeDrawing = () -> avatar.render(wearer, 0, delta, 1, stack, buffers, light,
                            OverlayTexture.NO_OVERLAY, null, PartFilterScheme.MODEL, false, false);
                    if (frame == null) {
                        nativeDrawing.run();
                    } else {
                        frame.drawFiguraInstance(command, new Matrix4f(entityToDraw).invert(), nativeDrawing);
                    }
                    avatar.postRenderEvent(delta, FiguraMat4.of());
                });
            }
        } finally {
            avatar.renderMode = previous;
        }
    }
}
