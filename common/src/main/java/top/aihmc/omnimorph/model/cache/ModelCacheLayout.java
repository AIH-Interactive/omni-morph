package top.aihmc.omnimorph.model.cache;

/** Cache path and suffix contract. Cosmetic storage is reserved for 2.0 and is not created yet. */
public final class ModelCacheLayout {
    public static final String AVATAR_DIRECTORY = "avatars";
    public static final String AVATAR_EXTENSION = ".oma";
    public static final String COLLECTION_EXTENSION = ".omc";
    public static final String COSMETIC_DIRECTORY = "cosmetic";
    public static final String COSMETIC_EXTENSION = ".oms";

    private ModelCacheLayout() {
    }
}
