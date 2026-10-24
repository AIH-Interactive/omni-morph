package top.aihmc.omnimorph.utils.fabric;

import org.figuramc.figura.utils.FiguraModMetadata;
import org.figuramc.figura.utils.Version;

/** Metadata for standalone parser tests; does not simulate a Minecraft client. */
public final class FiguraModMetadataImpl extends FiguraModMetadata {
    private FiguraModMetadataImpl(String modId) {
        super(modId);
    }

    public static FiguraModMetadata getMetadataForMod(String modId) {
        return new FiguraModMetadataImpl(modId);
    }

    @Override
    public String getCustomValueAsString(String key) {
        return "";
    }

    @Override
    public Number getCustomValueAsNumber(String key) {
        return 0;
    }

    @Override
    public Boolean getCustomValueAsBoolean(String key) {
        return false;
    }

    @Override
    public Object getCustomValueAsObject(String key) {
        return null;
    }

    @Override
    public Version getModVersion() {
        return new Version("0.1.0");
    }
}
