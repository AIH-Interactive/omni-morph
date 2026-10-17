package top.aihmc.omnimorph.render.acceleration;

import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.resources.Identifier;

import java.util.Objects;

/**
 * Immutable per-channel values captured after engine visibility and budget rules.
 * Complexity is descriptive and already charged by the engine; layers may share it.
 */
public record DrawSemantics(RenderType material, Identifier texture, Layer layer, String channel,
                            int submitOrder, int complexityCost, int light, int overlay,
                            float red, float green, float blue, float alpha,
                            double vertexOffset, boolean visible) {
    public enum Layer {
        PRIMARY, SECONDARY
    }

    public DrawSemantics {
        Objects.requireNonNull(layer);
        Objects.requireNonNull(channel);
        if (complexityCost < 0) {
            throw new IllegalArgumentException("Negative complexity cost");
        }
    }

    public boolean isOutline() {
        return material != null && material.isOutline();
    }

    public boolean cull() {
        return material != null && material.pipeline().isCull();
    }

    public boolean sortOnUpload() {
        return material != null && material.sortOnUpload();
    }

    /** Numeric-only callers explicitly lack a draw/material capability. */
    public static DrawSemantics transformOnly() {
        return new DrawSemantics(null, null, Layer.PRIMARY, "transform", 0, 0, 0, 0,
                1, 1, 1, 1, 0, true);
    }
}
