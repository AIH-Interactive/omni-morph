package org.figuramc.figura.mixin.font;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.font.GlyphInfo;
import com.mojang.blaze3d.font.GlyphProvider;
import com.mojang.blaze3d.font.SheetGlyphInfo;
import net.minecraft.client.gui.font.FontSet;
import net.minecraft.client.gui.font.FontTexture;
import net.minecraft.client.gui.font.glyphs.BakedGlyph;
import net.minecraft.client.gui.font.providers.BitmapProvider;
import net.minecraft.resources.ResourceLocation;
import org.figuramc.figura.FiguraMod;
import org.figuramc.figura.ducks.BakedGlyphAccessor;
import org.figuramc.figura.ducks.BitmapProviderGlyphAccessor;
import org.figuramc.figura.font.Emojis;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(FontSet.class)
public abstract class FontSetMixin {
    @Shadow @Final private ResourceLocation name;
    @Unique private int figura$codePoint = -1;

    @WrapOperation(method = {"computeGlyphInfo", "computeBakedGlyph"},
            at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/font/GlyphProvider;getGlyph(I)Lcom/mojang/blaze3d/font/GlyphInfo;", remap = true))
    private GlyphInfo figura$emojiAdvance(GlyphProvider provider, int codePoint, Operation<GlyphInfo> original) {
        GlyphInfo glyph = original.call(provider, codePoint);
        if (figura$isEmojiFont() && glyph instanceof BitmapProvider.Glyph) {
            var bitmap = (BitmapProviderGlyphAccessor) glyph;
            bitmap.figura$setAdvance(8);
            bitmap.figura$setCodePoint(codePoint);
        }
        return glyph;
    }

    @WrapMethod(method = "computeBakedGlyph")
    private BakedGlyph figura$captureCodePoint(int codePoint, Operation<BakedGlyph> original) {
        int previous = figura$codePoint;
        figura$codePoint = codePoint;
        try {
            return original.call(codePoint);
        } finally {
            figura$codePoint = previous;
        }
    }

    @WrapOperation(method = "stitch", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/font/FontTexture;add(Lcom/mojang/blaze3d/font/SheetGlyphInfo;)Lnet/minecraft/client/gui/font/glyphs/BakedGlyph;"))
    private BakedGlyph figura$attachEmoji(FontTexture texture, SheetGlyphInfo info, Operation<BakedGlyph> original) {
        BakedGlyph glyph = original.call(texture, info);
        if (figura$isEmojiFont() && glyph != null && figura$codePoint >= 0) {
            ((BakedGlyphAccessor) glyph).figura$setupEmoji(Emojis.getCategoryByFont(name), figura$codePoint, info.getPixelWidth());
        }
        return glyph;
    }

    @Unique
    private boolean figura$isEmojiFont() {
        return name.getNamespace().equals(FiguraMod.MOD_ID);
    }
}
