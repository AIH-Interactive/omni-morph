package top.aihmc.omnimorph.gui;

import com.mojang.blaze3d.ProjectionType;
import com.mojang.renderpearl.api.buffers.GpuBufferSlice;
import com.mojang.blaze3d.systems.RenderSystem;
import org.joml.Matrix4f;

/** Tests real RenderSystem state restoration without a device, window or pixel-rendering claim. */
public final class PipRenderStateScopeSmokeTest {
    public static void main(String[] args) {
        RenderSystem.initRenderThread();
        var outerProjection = new GpuBufferSlice(null, 16, 64);
        var outerLights = new GpuBufferSlice(null, 80, 32);
        RenderSystem.setProjectionMatrix(outerProjection, ProjectionType.PERSPECTIVE);
        RenderSystem.setShaderLights(outerLights);
        RenderSystem.getModelViewStack().translation(2, 3, 4);
        var outerMatrix = RenderSystem.getModelViewMatrixCopy();

        // An existing native projection backup must survive nested custom PIP work.
        RenderSystem.backupProjectionMatrix();
        try (var outer = new PipRenderStateScope()) {
            var pipProjection = new GpuBufferSlice(null, 112, 64);
            var pipLights = new GpuBufferSlice(null, 176, 32);
            RenderSystem.setProjectionMatrix(pipProjection, ProjectionType.ORTHOGRAPHIC);
            RenderSystem.setShaderLights(pipLights);
            RenderSystem.getModelViewStack().translate(5, 6, 7);
            var pipMatrix = RenderSystem.getModelViewMatrixCopy();
            try {
                try (var nested = new PipRenderStateScope()) {
                    RenderSystem.setProjectionMatrix(null, ProjectionType.PERSPECTIVE);
                    RenderSystem.setShaderLights(null);
                    RenderSystem.getModelViewStack().identity();
                    throw new ExpectedFailure();
                }
            } catch (ExpectedFailure expected) {
                assertState(pipProjection, ProjectionType.ORTHOGRAPHIC, pipLights, pipMatrix);
            }
        }
        assertState(outerProjection, ProjectionType.PERSPECTIVE, outerLights, outerMatrix);
        RenderSystem.setProjectionMatrix(null, ProjectionType.ORTHOGRAPHIC);
        RenderSystem.restoreProjectionMatrix();
        check(RenderSystem.getProjectionMatrixBuffer() == outerProjection, "native projection backup overwritten");
        check(RenderSystem.getProjectionType() == ProjectionType.PERSPECTIVE, "native projection type backup overwritten");
        // Both scope pushes must have been popped, including after the nested failure.
        try {
            RenderSystem.getModelViewStack().popMatrix();
            throw new AssertionError("PIP leaked a model-view stack entry");
        } catch (IllegalStateException expected) {
            // The real JOML stack is back at its initial depth.
        }
        System.out.println("PIP state restoration PASS: nested failure, lights, projection, matrix and stack depth");
    }

    private static void assertState(GpuBufferSlice projection,
                                    ProjectionType type, GpuBufferSlice lights, Matrix4f matrix) {
        check(RenderSystem.getProjectionMatrixBuffer() == projection, "PIP projection leaked");
        check(RenderSystem.getProjectionType() == type, "PIP projection type leaked");
        check(RenderSystem.getShaderLights() == lights, "PIP lighting leaked");
        check(RenderSystem.getModelViewMatrixCopy().equals(matrix), "PIP model-view matrix leaked");
    }

    private static void check(boolean valid, String message) {
        if (!valid) {
            throw new AssertionError(message);
        }
    }

    private static final class ExpectedFailure extends RuntimeException {
    }

}
