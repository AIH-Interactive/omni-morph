package org.figuramc.figura.font;

/**
 * Simple holder for passing codepoint data from BitmapProvider.Glyph.bake()
 * to GlyphStitcher.stitch() during the synchronous bake→stitch call chain.
 * Both happen on the render thread, so no synchronization needed.
 */
public class EmojiCodePointHolder {
    public static int pendingCodePoint = -1;
}
