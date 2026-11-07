package top.aihmc.omm;

import com.mojang.blaze3d.opengl.GlConst;
import net.minecraft.client.renderer.rendertype.RenderType;

import static org.lwjgl.opengl.GL33C.*;

/** Applies the selected MOD's original pipeline state to the host draw call. */
final class OmniMorphCpuMaterial {
    private OmniMorphCpuMaterial() {
    }

    static void apply(Object originalMaterial, int program) {
        if (originalMaterial == null) {
            glDisable(GL_CULL_FACE);
            glEnable(GL_BLEND);
            glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
            glEnable(GL_DEPTH_TEST);
            glDepthFunc(GL_LESS);
            glDepthMask(true);
            glColorMask(true, true, true, true);
            glDisable(GL_POLYGON_OFFSET_FILL);
            glPolygonMode(GL_FRONT_AND_BACK, GL_FILL);
            glUniform1f(glGetUniformLocation(program, "alphaCutout"), 0.01f);
            return;
        }
        var pipeline = ((RenderType) originalMaterial).pipeline();
        if (pipeline.isCull()) {
            glEnable(GL_CULL_FACE);
        } else {
            glDisable(GL_CULL_FACE);
        }
        var color = pipeline.getColorTargetState();
        if (color.blendFunction().isPresent()) {
            var blend = color.blendFunction().orElseThrow();
            glEnable(GL_BLEND);
            glBlendFuncSeparate(GlConst.toGl(blend.sourceColor()), GlConst.toGl(blend.destColor()),
                GlConst.toGl(blend.sourceAlpha()), GlConst.toGl(blend.destAlpha()));
        } else {
            glDisable(GL_BLEND);
        }
        glColorMask(color.writeRed(), color.writeGreen(), color.writeBlue(), color.writeAlpha());
        var depth = pipeline.getDepthStencilState();
        if (depth == null) {
            glDisable(GL_DEPTH_TEST);
            glDepthMask(false);
            glDisable(GL_POLYGON_OFFSET_FILL);
        } else {
            glEnable(GL_DEPTH_TEST);
            glDepthFunc(GlConst.toGl(depth.depthTest()));
            glDepthMask(depth.writeDepth());
            if (depth.depthBiasScaleFactor() != 0 || depth.depthBiasConstant() != 0) {
                glEnable(GL_POLYGON_OFFSET_FILL);
                glPolygonOffset(depth.depthBiasScaleFactor(), depth.depthBiasConstant());
            } else {
                glDisable(GL_POLYGON_OFFSET_FILL);
            }
        }
        glPolygonMode(GL_FRONT_AND_BACK, GlConst.toGl(pipeline.getPolygonMode()));
        String cutoff = pipeline.getShaderDefines().values().get("ALPHA_CUTOUT");
        glUniform1f(glGetUniformLocation(program, "alphaCutout"), cutoff == null ? 0 : Float.parseFloat(cutoff));
    }
}
