package org.figuramc.figura.gui;

import com.mojang.blaze3d.ProjectionType;
import com.mojang.blaze3d.systems.GpuDevice;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.*;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.gui.render.TextureSetup;
import net.minecraft.client.gui.render.pip.PictureInPictureRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.Projection;
import net.minecraft.client.renderer.ProjectionMatrixBuffer;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.state.gui.BlitRenderState;
import net.minecraft.client.renderer.state.gui.GuiRenderState;

import java.util.OptionalDouble;

public class FiguraGuiRenderer extends PictureInPictureRenderer<FiguraGuiRenderState> {

    private GpuTexture texture;
    private GpuTextureView textureView;
    private GpuTexture depthTexture;
    private GpuTextureView depthTextureView;
    private GpuSampler sampler;

    private final ProjectionMatrixBuffer projectionMatrixBuffer = new ProjectionMatrixBuffer(
            "GUI-PIP - " + this.getClass().getSimpleName()
    );
    private final Projection projection = new Projection();

    public FiguraGuiRenderer(MultiBufferSource.BufferSource bufferSource) {
        super(bufferSource);
    }

    @Override
    public Class<FiguraGuiRenderState> getRenderStateClass() {
        return FiguraGuiRenderState.class;
    }

    @Override
    protected String getTextureLabel() {
        return "Figura GUI";
    }

    @Override
    protected void renderToTexture(FiguraGuiRenderState state, PoseStack poseStack) {
        state.avatar().hudRender(poseStack, this.bufferSource, state.entity(), state.tickDelta());
    }

    @Override
    public void prepare(FiguraGuiRenderState state, GuiRenderState guiRenderState, int guiScale) {
        int pixelW = (state.x1() - state.x0()) * guiScale;
        int pixelH = (state.y1() - state.y0()) * guiScale;
        float guiWidth = (float) (state.x1() - state.x0());
        float guiHeight = (float) (state.y1() - state.y0());

        GpuDevice gpuDevice = RenderSystem.getDevice();
        boolean needsResize = texture == null
                || texture.getWidth(0) != pixelW
                || texture.getHeight(0) != pixelH;

        if (needsResize && texture != null) {
            texture.close();
            texture = null;
            textureView.close();
            textureView = null;
            depthTexture.close();
            depthTexture = null;
            depthTextureView.close();
            depthTextureView = null;
            sampler.close();
            sampler = null;
        }

        if (texture == null) {
            texture = gpuDevice.createTexture(
                    () -> "UI Figura GUI texture", 13, TextureFormat.RGBA8, pixelW, pixelH, 1, 1
            );
            textureView = gpuDevice.createTextureView(texture);
            depthTexture = gpuDevice.createTexture(
                    () -> "UI Figura GUI depth texture", 9, TextureFormat.DEPTH32, pixelW, pixelH, 1, 1
            );
            depthTextureView = gpuDevice.createTextureView(depthTexture);
            sampler = gpuDevice.createSampler(
                    AddressMode.CLAMP_TO_EDGE, AddressMode.CLAMP_TO_EDGE,
                    FilterMode.NEAREST, FilterMode.NEAREST,
                    1, OptionalDouble.empty()
            );
        }

        gpuDevice.createCommandEncoder().clearColorAndDepthTextures(texture, 0, depthTexture, 1.0);

        projection.setupOrtho(-1000.0F, 1000.0F, guiWidth, guiHeight, true);
        RenderSystem.setProjectionMatrix(
                projectionMatrixBuffer.getBuffer(projection), ProjectionType.ORTHOGRAPHIC
        );

        RenderSystem.outputColorTextureOverride = textureView;
        RenderSystem.outputDepthTextureOverride = depthTextureView;

        renderToTexture(state, new PoseStack());
        this.bufferSource.endBatch();

        RenderSystem.outputColorTextureOverride = null;
        RenderSystem.outputDepthTextureOverride = null;

        blitTexture(state, guiRenderState);
    }

    @Override
    protected void blitTexture(FiguraGuiRenderState state, GuiRenderState guiRenderState) {
        if (textureView == null) return;
        guiRenderState.addBlitToCurrentLayer(
                new BlitRenderState(
                        RenderPipelines.GUI_TEXTURED_PREMULTIPLIED_ALPHA,
                        TextureSetup.singleTexture(textureView, sampler),
                        state.pose(),
                        state.x0(), state.y0(), state.x1(), state.y1(),
                        0.0F, 1.0F, 1.0F, 0.0F,
                        -1,
                        state.scissorArea(),
                        null
                )
        );
    }

    @Override
    public void close() {
        if (texture != null) {
            texture.close();
            textureView.close();
            depthTexture.close();
            depthTextureView.close();
            sampler.close();
        }
        super.close();
    }
}
