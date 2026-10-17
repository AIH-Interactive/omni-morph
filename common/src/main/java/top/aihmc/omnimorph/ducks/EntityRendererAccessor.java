package top.aihmc.omnimorph.ducks;

public interface EntityRendererAccessor {
    default boolean figura$isRenderingName() {
        return false;
    }

    default boolean figura$hasScore() {
        return false;
    }
}
