package top.aihmc.omnimorph.model.outfit;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.model.PlayerModel;
import top.aihmc.omnimorph.model.ysm.animation.YsmEntityFrame;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.figuramc.figura.avatar.Avatar;
import org.figuramc.figura.math.matrix.FiguraMat4;
import top.aihmc.omnimorph.model.library.PreviewBoneHighlight;
import org.figuramc.figura.model.rendering.EntityRenderMode;
import org.figuramc.figura.model.rendering.PartFilterScheme;

/** Converts the controller's model-unit matrices into each existing native renderer's input space. */
public final class OutfitNativeRenderer {
    private OutfitNativeRenderer() {}

    /** Iris shadow rendering must not run outfit scripts: POST_RENDER emission would use the shadow view. */
    private static boolean shadowPass() {
        return org.figuramc.figura.utils.PlatformUtils.isModLoaded("iris")
                && top.aihmc.omnimorph.compat.IrisCompat.isShadowPass();
    }

    public static void drawVanilla(OutfitComposition.Frame frame, PlayerModel<?> model, YsmEntityFrame state,
                                   int light, float delta, Matrix4fc entityToDraw, Matrix4fc modelToDraw,
                                   MultiBufferSource buffers, EntityRenderMode mode) {
        var basePose = frame.captureVanillaBase(model, new Matrix4f(entityToDraw).invert().mul(modelToDraw));
        draw(frame, basePose, state, light, delta, entityToDraw, buffers, mode);
    }

    public static void drawVanillaArm(OutfitComposition.Frame frame, PlayerModel<?> model,
                                      YsmEntityFrame state, int light, float delta, Matrix4fc armToDraw,
                                      MultiBufferSource buffers, boolean left) {
        var basePose = frame.captureVanillaArm(model, new Matrix4f(), left);
        draw(frame, basePose, state, light, delta, armToDraw, buffers, EntityRenderMode.FIRST_PERSON);
    }

    public static void draw(OutfitComposition.Frame frame, SkeletonPose basePose,
                            YsmEntityFrame state, int light, float delta, Matrix4fc entityToDraw,
                            MultiBufferSource buffers, EntityRenderMode mode) {
        if (shadowPass()) {
            return;
        }
        frame.withPreviewSelection(() -> frame.draw(basePose, state, delta, mode,
                command -> frame.withPreviewSelection(() ->
                        drawInstance(frame, command, delta, entityToDraw, buffers, light, mode))));
        if (mode == EntityRenderMode.FIGURA_GUI) {
            PreviewBoneHighlight.drawSkeleton(frame.previewHelpers(basePose), entityToDraw, buffers);
        } else {
            frame.drawLocalParticles(entityToDraw, buffers, delta);
        }
    }

    public static void drawInstance(OutfitComposition.Draw command, float delta, Matrix4fc entityToDraw,
                                    MultiBufferSource buffers, int light, EntityRenderMode mode) {
        drawInstance(null, command, delta, entityToDraw, buffers, light, mode);
    }

    private static void drawInstance(OutfitComposition.Frame frame, OutfitComposition.Draw command, float delta,
                                     Matrix4fc entityToDraw, MultiBufferSource buffers, int light, EntityRenderMode mode) {
        if (shadowPass()) {
            return;
        }
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
                net.minecraft.world.entity.Entity wearer;
                if (frame != null) {
                    wearer = frame.renderWearer();
                } else {
                    wearer = avatar.luaRuntime == null ? null : avatar.luaRuntime.getUser();
                }
                command.figuraPose().draw(() -> {
                    Runnable nativeDrawing = () -> {
                        // World draws use the camera-relative stack, so part-to-world matrices are valid here and
                        // scripts sampling partToWorldMatrix (trails, particles) need them; GUI draws are not.
                        var renderer = avatar.renderer;
                        if (renderer == null) {
                            return;
                        }
                        boolean previousMatrixUpdate = renderer.allowMatrixUpdate;
                        // Camera-relative world draws (third person and first-person hands/scripts) need fresh
                        // part-to-world matrices for trails and particles; GUI draws are not camera-relative.
                        renderer.allowMatrixUpdate = mode == EntityRenderMode.RENDER
                                || mode == EntityRenderMode.FIRST_PERSON
                                || mode == EntityRenderMode.FIRST_PERSON_WORLD;
                        try {
                            avatar.render(wearer, 0, delta, 1, stack, buffers, light,
                                    OverlayTexture.NO_OVERLAY, null, PartFilterScheme.MODEL, false, false);
                        } finally {
                            renderer.allowMatrixUpdate = previousMatrixUpdate;
                        }
                    };
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
