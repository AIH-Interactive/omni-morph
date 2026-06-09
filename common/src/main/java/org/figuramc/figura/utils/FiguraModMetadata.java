package org.figuramc.figura.utils;

import dev.architectury.injectables.annotations.ExpectPlatform;

public abstract class FiguraModMetadata {
    private final String modId;
    protected FiguraModMetadata(String modID) {
        this.modId = modID;
    }

    public abstract String getCustomValueAsString(String key);
    public abstract Number getCustomValueAsNumber(String key);
    public abstract Boolean getCustomValueAsBoolean(String key);
    public abstract Object getCustomValueAsObject(String key);

    public abstract Version getModVersion();

    public String getModId() {
        return this.modId;
    }
    @ExpectPlatform
    public static FiguraModMetadata getMetadataForMod(String modID) {
        try {
            return (FiguraModMetadata) Class.forName("org.figuramc.figura.utils.fabric.FiguraModMetadataImpl")
                .getMethod("getMetadataForMod", String.class).invoke(null, modID);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }
}
