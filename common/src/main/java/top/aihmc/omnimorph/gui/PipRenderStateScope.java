package top.aihmc.omnimorph.gui;

import com.mojang.blaze3d.ProjectionType;
import com.mojang.renderpearl.api.buffers.GpuBufferSlice;
import com.mojang.blaze3d.systems.RenderSystem;

/** Keeps a custom PIP preparation and its deferred draws in the same render-state scope. */
public final class PipRenderStateScope implements AutoCloseable {
    private final GpuBufferSlice projection = RenderSystem.getProjectionMatrixBuffer();
    private final ProjectionType projectionType = RenderSystem.getProjectionType();
    private final GpuBufferSlice lights = RenderSystem.getShaderLights();

    public PipRenderStateScope() {
        RenderSystem.getModelViewStack().pushMatrix();
    }

    @Override
    public void close() {
        RenderSystem.setProjectionMatrix(projection, projectionType);
        RenderSystem.setShaderLights(lights);
        RenderSystem.getModelViewStack().popMatrix();
    }
}
