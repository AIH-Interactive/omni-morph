package top.aihmc.omnimorph.avatar.ysm;

import java.util.List;

public record YsmAvatarMetadata(
        String name,
        String description,
        String[] authors,
        List<YsmTextureOption> textures
) {
}
