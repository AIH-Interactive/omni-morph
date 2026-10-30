package top.aihmc.omnimorph.render.acceleration;

/**
 * Experimental GPU acceleration layers, all off by default and set from the hardware acceleration settings.
 * Storage blocks and model draws are probed while the GPU activates; the other two are read per draw.
 */
public final class AccelerationOptions {
    private static volatile boolean storageBlocks;
    private static volatile boolean simdPack;
    private static volatile boolean mergePasses;
    private static volatile boolean modelMesh;

    private AccelerationOptions() {
    }

    /**
     * Applies all four options; returns true when a probed layer (storage blocks or model draws) changed, so an
     * active GPU backend must re-activate to probe it again.
     */
    public static boolean update(boolean storageBlocks, boolean simdPack, boolean mergePasses, boolean modelMesh) {
        boolean reprobe = AccelerationOptions.storageBlocks != storageBlocks || AccelerationOptions.modelMesh != modelMesh;
        AccelerationOptions.storageBlocks = storageBlocks;
        AccelerationOptions.simdPack = simdPack;
        AccelerationOptions.mergePasses = mergePasses;
        AccelerationOptions.modelMesh = modelMesh;
        return reprobe;
    }

    /** SSBO pose storage blocks (S1–S4, R4-1). */
    static boolean storageBlocks() {
        return storageBlocks;
    }

    /** Native SIMD pose packing for rigid groups (S5); needs automatic mode, where SIMD attaches to the GPU. */
    static boolean simdPack() {
        return simdPack;
    }

    /** One render pass per material boundary (R4-2). */
    static boolean mergePasses() {
        return mergePasses;
    }

    /** One draw per model material boundary (R4-3); requires storage blocks. */
    static boolean modelMesh() {
        return storageBlocks && modelMesh;
    }

    static void setStorageBlocks(boolean enabled) {
        storageBlocks = enabled;
    }

    static void setMergePasses(boolean enabled) {
        mergePasses = enabled;
    }

    static void setModelMesh(boolean enabled) {
        modelMesh = enabled;
    }
}
