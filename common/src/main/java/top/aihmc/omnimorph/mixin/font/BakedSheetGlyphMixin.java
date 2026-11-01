package top.aihmc.omnimorph.mixin.font;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.gui.font.glyphs.BakedGlyph;
import org.figuramc.figura.ducks.BakedGlyphAccessor;
import org.figuramc.figura.font.EmojiContainer;
import org.figuramc.figura.font.EmojiMetadata;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(BakedGlyph.class)
public abstract class BakedSheetGlyphMixin implements BakedGlyphAccessor {
    @Shadow
    @Final
    private float up;
    @Shadow
    @Final
    private float down;
    @Shadow
    @Final
    private float u0;
    @Shadow
    @Final
    private float v0;
    @Shadow
    @Final
    private float v1;
    @Shadow @Final private float left;
    @Shadow @Final private float right;
    @Shadow @Final private float u1;
    @Unique
    EmojiMetadata figura$metadata;
    @Unique
    int figura$glyphPixelWidth;

    @Override
    public void figura$setupEmoji(@Nullable EmojiContainer container, int codepoint, int glyphPixelWidth) {
        if (container != null) {
            figura$metadata = container.getLookup().getMetadata(codepoint);
            figura$glyphPixelWidth = glyphPixelWidth;
        }
    }

    @Inject(method = "render(ZFFLorg/joml/Matrix4f;Lcom/mojang/blaze3d/vertex/VertexConsumer;FFFFI)V", at = @At("HEAD"), cancellable = true)
    private void render(boolean italic, float x, float y, Matrix4f matrix, VertexConsumer vertexConsumer,
                        float red, float green, float blue, float alpha, int light, CallbackInfo ci) {
        if (figura$metadata == null) return;

        float h = this.up;
        float j = this.down;
        float k = y + h;
        float l = y + j;
        float m = italic ? 1.0f - 0.25f * h : 0f;
        float n = italic ? 1.0f - 0.25f * j : 0f;
        float singleWidth = (u1 - u0) * 8f / figura$glyphPixelWidth;
        float shift = singleWidth * figura$metadata.getCurrentFrame();

        float u = u0 + shift;
        vertexConsumer.vertex(matrix, x + m, k, 0).color(red, green, blue, alpha).uv(u, this.v0).uv2(light).endVertex();
        vertexConsumer.vertex(matrix, x + n, l, 0).color(red, green, blue, alpha).uv(u, this.v1).uv2(light).endVertex();
        vertexConsumer.vertex(matrix, x + figura$metadata.width + n, l, 0).color(red, green, blue, alpha).uv(u + singleWidth, this.v1).uv2(light).endVertex();
        vertexConsumer.vertex(matrix, x + figura$metadata.width + m, k, 0).color(red, green, blue, alpha).uv(u + singleWidth, this.v0).uv2(light).endVertex();
        ci.cancel();
    }
}
