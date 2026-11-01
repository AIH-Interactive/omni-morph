package top.aihmc.omnimorph.model.library;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.gui.GuiGraphics;
import com.mojang.blaze3d.platform.Lighting;
import net.minecraft.client.player.AbstractClientPlayer;
import top.aihmc.omnimorph.model.ysm.animation.YsmEntityFrame;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.texture.OverlayTexture;
import org.figuramc.figura.avatar.Avatar;
import org.figuramc.figura.math.matrix.FiguraMat4;
import org.figuramc.figura.model.rendering.EntityRenderMode;
import org.figuramc.figura.model.rendering.PartFilterScheme;
import top.aihmc.omnimorph.model.outfit.OutfitNativeRenderer;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.figuramc.figura.permissions.Permissions;
import org.figuramc.figura.utils.ui.UIHelper;

/** Direct Figura geometry plus a native skinned body, without EntityRenderDispatcher or render layers. */
public final class LibraryFiguraPreviewRenderer {
    private static final LibraryFiguraPreviewRenderer INSTANCE = new LibraryFiguraPreviewRenderer();
    private final PlayerModel<AbstractClientPlayer> wide = createModel(false);
    private final PlayerModel<AbstractClientPlayer> slim = createModel(true);

    private LibraryFiguraPreviewRenderer() {}

    static PlayerModel<AbstractClientPlayer> createModel(boolean slim) {
        return new PlayerModel<>(LayerDefinition.create(PlayerModel.createMesh(CubeDeformation.NONE, slim),
                64, 64).bakeRoot(), slim);
    }

    static void render(GuiGraphics gui, LibraryFiguraPreviewState state) {
        var outfit = LibraryPreviewBinding.composition(state.playerPose().frame());
        if (outfit != null) {
            // Flushing prior GUI work may run callbacks that edit the live base model.
            outfit.prepareFiguraBase(state.avatar());
        }
        gui.flush();
        gui.enableScissor(state.x0(), state.y0(), state.x1(), state.y1());
        PoseStack stack = gui.pose();
        stack.pushPose();
        try {
            stack.translate((state.x0() + state.x1()) / 2f, (state.y0() + state.y1()) / 2f, 250);
            stack.scale(state.scale(), state.scale(), -state.scale());
            UIHelper.useFiguraLighting();
            var model = state.playerPose().slim() ? INSTANCE.slim : INSTANCE.wide;
            draw(state, stack, gui.bufferSource(), model);
            gui.flush();
        } finally {
            stack.popPose();
            gui.disableScissor();
            Lighting.setupFor3DItems();
        }
    }

    static void draw(LibraryFiguraPreviewState state, PoseStack stack, MultiBufferSource source, PlayerModel<?> model) {
        Avatar avatar = state.avatar();
        if (!avatar.loaded || !avatar.accelerationOwner().isCurrent()) {
            return;
        }
        EntityRenderMode previousMode = avatar.renderMode;
        boolean previousPaperdoll = UIHelper.paperdoll;
        avatar.renderMode = EntityRenderMode.FIGURA_GUI;
        UIHelper.paperdoll = true;
        stack.pushPose();
        try {
            stack.translate(state.offsetX(), state.offsetY(), 0);
            stack.mulPose(Axis.ZP.rotationDegrees(180));
            stack.mulPose(Axis.XP.rotationDegrees(state.pitch()));
            stack.mulPose(Axis.YP.rotationDegrees(state.yaw() + 180));
            Matrix4f entityToDraw = new Matrix4f(stack.last().pose());
            stack.translate(0, -1, 0);
            stack.mulPose(Axis.YP.rotationDegrees(180));
            stack.scale(-1, -1, 1);
            stack.translate(0, -1.501f, 0);

            var outfit = LibraryPreviewBinding.composition(state.playerPose().frame());
            if (outfit != null) {
                outfit.prepareFiguraBase(avatar);
            }
            Runnable drawSubmittedBase = () -> {
                state.playerPose().apply(model);
                avatar.updateMatrices(model, stack);
                avatar.renderEvent(state.delta(), FiguraMat4.of());
                var vanilla = avatar.luaRuntime == null ? null : avatar.luaRuntime.vanilla_model.PLAYER;
                if (vanilla != null) {
                    vanilla.save(model);
                }
                try {
                    if (vanilla != null && avatar.permissions.get(Permissions.VANILLA_MODEL_EDIT) == 1) {
                        vanilla.preTransform(model);
                    }
                    // Copy the unhidden native pose first. Hiding the vanilla body must not hide Figura's own geometry.
                    Runnable drawBase = () -> avatar.render(null, 0, state.delta(), 1, stack, source, 0x00f000f0,
                            OverlayTexture.NO_OVERLAY, model, PartFilterScheme.MODEL, false, false);
                    if (outfit == null) {
                        drawBase.run();
                    } else {
                        var basePose = outfit.captureFiguraBase(avatar, new Matrix4f(entityToDraw).invert(), drawBase);
                        OutfitNativeRenderer.draw(outfit, basePose, new YsmEntityFrame(state.playerPose().ageInTicks(), 0, 0, 0, 0),
                                0x00f000f0, state.delta(), entityToDraw, source, EntityRenderMode.FIGURA_GUI);
                    }
                    if (vanilla != null && avatar.permissions.get(Permissions.VANILLA_MODEL_EDIT) == 1) {
                        vanilla.posTransform(model);
                    }
                    model.renderToBuffer(stack, source.getBuffer(model.renderType(state.playerPose().skin())),
                            0x00f000f0, OverlayTexture.NO_OVERLAY, 1, 1, 1, 1);
                    avatar.postRenderEvent(state.delta(), FiguraMat4.of());
                } finally {
                    if (vanilla != null) {
                        vanilla.restore(model);
                    }
                }
            };
            if (outfit != null) {
                outfit.drawFiguraBase(avatar, drawSubmittedBase);
            } else {
                drawSubmittedBase.run();
            }
            // The outer GUI matrix reflects Z. Billboard orientation uses only the authored view rotation.
            var viewRotation = new Quaternionf().rotationZ((float) Math.PI)
                    .rotateX((float) Math.toRadians(state.pitch()))
                    .rotateY((float) Math.toRadians(state.yaw() + 180));
            LibraryPreviewBinding.particles(state.playerPose().frame(), viewRotation, state.delta()).draw(entityToDraw, source);
        } finally {
            stack.popPose();
            avatar.renderMode = previousMode;
            UIHelper.paperdoll = previousPaperdoll;
        }
    }
}
