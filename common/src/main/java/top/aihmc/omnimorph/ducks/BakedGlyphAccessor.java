package top.aihmc.omnimorph.ducks;

import top.aihmc.omnimorph.font.EmojiContainer;

public interface BakedGlyphAccessor {
    void figura$setupEmoji(EmojiContainer container, int codepoint, int glyphPixelWidth);
}
