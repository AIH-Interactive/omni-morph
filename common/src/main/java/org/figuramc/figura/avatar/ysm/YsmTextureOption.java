package org.figuramc.figura.avatar.ysm;

public record YsmTextureOption(String id, String displayName, String path, String normalPath, String specularPath) {
    public YsmTextureOption(String id, String displayName, String path) {
        this(id, displayName, path, "", "");
    }

    public YsmTextureOption {
        normalPath = normalPath == null ? "" : normalPath;
        specularPath = specularPath == null ? "" : specularPath;
    }
}
