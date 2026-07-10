package org.figuramc.figura.model.ysm;

public record YsmLocator(
        String name,
        String boneName,
        YsmBoneRole role,
        boolean leftSide,
        String defaultItemTransform
) {
}
