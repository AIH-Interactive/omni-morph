package top.aihmc.omnimorph.model.library;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.gui.render.pip.PictureInPictureRenderer;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.entity.player.PlayerModelType;
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
public final class LibraryFiguraPreviewRenderer extends PictureInPictureRenderer<LibraryFiguraPreviewState> {
    private final PlayerModel wide = createModel(false);
    private final PlayerModel slim = createModel(true);

    public LibraryFiguraPreviewRenderer(MultiBufferSource.BufferSource source) {
        super(source);
    }

    static PlayerModel createModel(boolean slim) {
        return new PlayerModel(LayerDefinition.create(PlayerModel.createMesh(CubeDeformation.NONE, slim),
                64, 64).bakeRoot(), slim);
    }

    @Override
    public Class<LibraryFiguraPreviewState> getRenderStateClass() {
        return LibraryFiguraPreviewState.class;
    }

    @Override
    protected String getTextureLabel() {
        return "OmniMorph independent Figura preview";
    }

    @Override
    protected float getTranslateY(int height, int guiScale) {
        return height / 2f;
    }

    @Override
    protected void renderToTexture(LibraryFiguraPreviewState state, PoseStack stack) {
        UIHelper.useFiguraLighting();
        PlayerModel model = state.playerPose().skin.model() == PlayerModelType.SLIM ? slim : wide;
        draw(state, stack, bufferSource, model);
    }

    static void draw(LibraryFiguraPreviewState state, PoseStack stack, MultiBufferSource source, PlayerModel model) {
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

            var outfit = LibraryPreviewBinding.composition(state.playerPose());
            if (outfit != null) {
                outfit.prepareFiguraBase(avatar);
            }
            Runnable drawSubmittedBase = () -> {
                model.setupAnim(state.playerPose());
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
                        OutfitNativeRenderer.draw(outfit, basePose, state.playerPose(), state.delta(), entityToDraw,
                                source, EntityRenderMode.FIGURA_GUI);
                    }
                    if (vanilla != null && avatar.permissions.get(Permissions.VANILLA_MODEL_EDIT) == 1) {
                        vanilla.posTransform(model);
                    }
                    model.renderToBuffer(stack, source.getBuffer(model.renderType(state.playerPose().skin.body().texturePath())),
                            0x00f000f0, OverlayTexture.NO_OVERLAY);
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
            // PiP's outer matrix reflects Z. Billboard orientation uses only the authored view rotation.
            var viewRotation = new Quaternionf().rotationZ((float) Math.PI)
                    .rotateX((float) Math.toRadians(state.pitch()))
                    .rotateY((float) Math.toRadians(state.yaw() + 180));
            LibraryPreviewBinding.particles(state.playerPose(), viewRotation, state.delta()).draw(entityToDraw, source);
        } finally {
            stack.popPose();
            avatar.renderMode = previousMode;
            UIHelper.paperdoll = previousPaperdoll;
        }
    }
}
