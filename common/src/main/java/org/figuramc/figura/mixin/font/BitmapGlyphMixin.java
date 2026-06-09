package org.figuramc.figura.mixin.font;

import com.mojang.blaze3d.font.UnbakedGlyph;
import net.minecraft.client.gui.font.glyphs.BakedGlyph;
import net.minecraft.client.gui.font.providers.BitmapProvider;
import org.figuramc.figura.ducks.BitmapProviderGlyphAccessor;
import org.figuramc.figura.font.EmojiCodePointHolder;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(BitmapProvider.Glyph.class)
public class BitmapGlyphMixin implements BitmapProviderGlyphAccessor {
    @Final
    @Mutable
    @Shadow
    private int advance;

    @Unique
    private int figura$codePoint = -1;

    @Override
    public void figura$setAdvance(int advance) {
        this.advance = advance;
    }

    @Override
    public void figura$setCodePoint(int codePoint) {
        this.figura$codePoint = codePoint;
    }

    @Override
    public int figura$getCodePoint() {
        return this.figura$codePoint;
    }

    @Inject(method = "bake", at = @At("HEAD"))
    private void onBake(UnbakedGlyph.Stitcher stitcher, CallbackInfoReturnable<BakedGlyph> cir) {
        EmojiCodePointHolder.pendingCodePoint = this.figura$codePoint;
    }
}
