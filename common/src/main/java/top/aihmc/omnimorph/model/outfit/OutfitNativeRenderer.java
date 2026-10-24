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
    private static final java.util.Set<String> DIAGNOSED = java.util.Collections.synchronizedSet(new java.util.HashSet<>());

    private OutfitNativeRenderer() {}

    /** Diagnostic only: each skipped instance/reason pair is logged once per client session. */
    private static void diagnoseSkipped(Avatar avatar, String reason) {
        if (DIAGNOSED.add(System.identityHashCode(avatar) + ":" + reason)) {
            top.aihmc.omnimorph.FiguraMod.LOGGER.warn("Outfit accessory not drawn: {}", reason);
        }
    }

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
        Avatar avatar = command.avatar();
        if (!avatar.loaded || avatar.scriptError || !avatar.accelerationOwner().isCurrent()) {
            diagnoseSkipped(avatar, !avatar.loaded ? "instance not loaded"
                    : avatar.scriptError ? "instance script error" : "instance acceleration owner retired");
            return;
        }
        // Diagnostic only: count this instance's submitted vertices once per instance and render mode.
        String drawKey = System.identityHashCode(avatar) + ":draw:" + mode;
        int[] drawnVertices = DIAGNOSED.contains(drawKey) ? null : new int[1];
        MultiBufferSource target = drawnVertices == null ? buffers : countingBuffers(buffers, drawnVertices);
        org.joml.Vector3f mountPosition = null;
        org.joml.Vector3f drawOrigin = null;
        EntityRenderMode previous = avatar.renderMode;
        avatar.renderMode = mode;
        try {
            PoseStack stack = new PoseStack();
            Matrix4f mounted = new Matrix4f(entityToDraw).scale(1f / 16).mul(command.matrix());
            if (drawnVertices != null) {
                mountPosition = command.matrix().getTranslation(new org.joml.Vector3f());
                drawOrigin = new Matrix4f(mounted).getTranslation(new org.joml.Vector3f());
            }
            if (avatar.isYsmNative()) {
                if (frame != null) {
                    frame.captureYsmInstance(command);
                }
                // YSM's original renderer consumes block-space geometry and keeps its own model scale.
                stack.mulPose(mounted.scale(16));
                avatar.getYsmRuntime().renderer().renderWithAttachments(stack, target, light, command.ysmPose());
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
                    Runnable nativeDrawing = () -> avatar.render(wearer, 0, delta, 1, stack, target, light,
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
            if (drawnVertices != null && DIAGNOSED.add(drawKey)) {
                top.aihmc.omnimorph.FiguraMod.LOGGER.warn(
                        "Outfit accessory draw: mode={}, vertices={}, mountModelUnits={}, drawOrigin={}",
                        mode, drawnVertices[0], mountPosition, drawOrigin);
                if (drawnVertices[0] == 0 && !avatar.isYsmNative()) {
                    reportEmptyFiguraDraw(avatar);
                }
            }
        }
    }

    /** Diagnostic only: separates hidden geometry from an exhausted face allowance for an empty accessory draw. */
    private static void reportEmptyFiguraDraw(Avatar avatar) {
        int hiddenParts = 0;
        int totalParts = 0;
        var pending = new java.util.ArrayDeque<top.aihmc.omnimorph.model.FiguraModelPart>();
        if (avatar.renderer != null && avatar.renderer.root != null) {
            pending.add(avatar.renderer.root);
        }
        while (!pending.isEmpty()) {
            var part = pending.pop();
            totalParts++;
            if (!part.customization.visible) {
                hiddenParts++;
            }
            pending.addAll(part.children);
        }
        top.aihmc.omnimorph.FiguraMod.LOGGER.warn(
                "Outfit accessory empty draw: complexityRemaining={}/{}, complexityPermission={}, hiddenParts={}/{}, rootVisible={}",
                avatar.complexity == null ? -1 : avatar.complexity.remaining,
                avatar.complexity == null ? -1 : avatar.complexity.max,
                avatar.permissions == null ? -1
                        : avatar.permissions.get(top.aihmc.omnimorph.permissions.Permissions.COMPLEXITY),
                hiddenParts, totalParts,
                avatar.renderer != null && avatar.renderer.root != null && avatar.renderer.root.customization.visible);
    }

    /** Diagnostic only: forwards every call and counts submitted vertices. */
    private static MultiBufferSource countingBuffers(MultiBufferSource buffers, int[] vertices) {
        return type -> {
            var consumer = buffers.getBuffer(type);
            return (com.mojang.blaze3d.vertex.VertexConsumer) java.lang.reflect.Proxy.newProxyInstance(
                    OutfitNativeRenderer.class.getClassLoader(),
                    new Class<?>[]{com.mojang.blaze3d.vertex.VertexConsumer.class}, (proxy, method, args) -> {
                        if (method.getName().equals("addVertex")) {
                            vertices[0]++;
                        }
                        Object result;
                        try {
                            result = method.invoke(consumer, args);
                        } catch (java.lang.reflect.InvocationTargetException failure) {
                            throw failure.getCause();
                        }
                        return result == consumer ? proxy : result;
                    });
        };
    }
}
