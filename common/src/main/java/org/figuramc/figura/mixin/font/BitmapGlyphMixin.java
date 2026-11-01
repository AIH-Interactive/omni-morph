package org.figuramc.figura.mixin.font;

import net.minecraft.client.gui.font.providers.BitmapProvider;
import org.figuramc.figura.ducks.BitmapProviderGlyphAccessor;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;

@Mixin(BitmapProvider.Glyph.class)
public class BitmapGlyphMixin implements BitmapProviderGlyphAccessor {
    @Final
    @Mutable
    @Shadow
    private int advance;

    @Unique private int figura$codePoint = -1;

    @Override
    public void figura$setAdvance(int advance) {
        this.advance = advance;
    }

    @Override
    public void figura$setCodePoint(int codePoint) {
        figura$codePoint = codePoint;
    }

    @Override
    public int figura$getCodePoint() {
        return figura$codePoint;
    }
}
