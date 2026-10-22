package top.aihmc.omnimorph.model.outfit;

/** Negotiated server budgets. Defaults are used for local drafts without outfit capabilities. */
public record OutfitLimits(int maxInstances, int maxVirtualAnchors, int maxDepth,
                           int maxResourceNodes, int maxTotalNodes, int maxBoneOverrides,
                           int maxJsonBytes, int maxTrails, int maxTrailSamples,
                           int maxControls, int maxLuaStateEntries, int maxStateStringBytes,
                           int maxPresets, int maxDisplayOrdinals, int maxExportedBones,
                           double positionLimit, double rotationLimit, double minScale, double maxScale,
                           boolean dynamicLightingEnabled, int maxDynamicLights, int maxDynamicLightLevel,
                           double maxDynamicLightRadius,
                           Integer maxCustomParticles, Integer maxParticleLayers, Integer maxRibbonVertices,
                           Integer maxParticleCallbacksPerTick, Integer maxParticleCallbacksPerFrame) {
    public OutfitLimits {
        // Boxed extension fields distinguish absent JSON keys from an explicit zero (disabled).
        maxCustomParticles = maxCustomParticles == null ? 512 : maxCustomParticles;
        maxParticleLayers = maxParticleLayers == null ? 4 : maxParticleLayers;
        maxRibbonVertices = maxRibbonVertices == null ? 4096 : maxRibbonVertices;
        maxParticleCallbacksPerTick = maxParticleCallbacksPerTick == null ? 64 : maxParticleCallbacksPerTick;
        maxParticleCallbacksPerFrame = maxParticleCallbacksPerFrame == null ? 32 : maxParticleCallbacksPerFrame;
        bounded(maxInstances, 0, 32);
        bounded(maxVirtualAnchors, 0, 128);
        bounded(maxDepth, 1, 64);
        bounded(maxResourceNodes, 0, 8192);
        bounded(maxTotalNodes, 0, 32768);
        bounded(maxBoneOverrides, 0, 1024);
        bounded(maxJsonBytes, 256, 65536);
        bounded(maxTrails, 0, 8);
        bounded(maxTrailSamples, 1, 256);
        bounded(maxControls, 0, 128);
        bounded(maxLuaStateEntries, 0, 128);
        bounded(maxStateStringBytes, 0, 512);
        bounded(maxPresets, 0, 64);
        bounded(maxDisplayOrdinals, 0, 256);
        bounded(maxExportedBones, 0, 128);
        OutfitConfig.require(Double.isFinite(positionLimit) && positionLimit >= 0 && positionLimit <= 4096);
        OutfitConfig.require(Double.isFinite(rotationLimit) && rotationLimit >= 0 && rotationLimit <= 36000);
        OutfitConfig.require(Double.isFinite(minScale) && Double.isFinite(maxScale)
                && minScale >= 0.001 && maxScale <= 64 && minScale <= maxScale);
        bounded(maxDynamicLights, 0, 32);
        bounded(maxDynamicLightLevel, 0, 15);
        OutfitConfig.require(Double.isFinite(maxDynamicLightRadius)
                && maxDynamicLightRadius >= 0 && maxDynamicLightRadius <= 32);
        bounded(maxCustomParticles, 0, 4096);
        bounded(maxParticleLayers, 0, 8);
        bounded(maxRibbonVertices, 0, 32768);
        bounded(maxParticleCallbacksPerTick, 0, 1024);
        bounded(maxParticleCallbacksPerFrame, 0, 512);
    }

    public OutfitLimits(int maxInstances, int maxVirtualAnchors, int maxDepth,
                        int maxResourceNodes, int maxTotalNodes, int maxBoneOverrides,
                        int maxJsonBytes, int maxTrails, int maxTrailSamples,
                        int maxControls, int maxLuaStateEntries, int maxStateStringBytes,
                        int maxPresets, int maxDisplayOrdinals, int maxExportedBones,
                        double positionLimit, double rotationLimit, double minScale, double maxScale,
                        boolean dynamicLightingEnabled, int maxDynamicLights, int maxDynamicLightLevel,
                        double maxDynamicLightRadius) {
        this(maxInstances, maxVirtualAnchors, maxDepth, maxResourceNodes, maxTotalNodes, maxBoneOverrides,
                maxJsonBytes, maxTrails, maxTrailSamples, maxControls, maxLuaStateEntries, maxStateStringBytes,
                maxPresets, maxDisplayOrdinals, maxExportedBones, positionLimit, rotationLimit, minScale, maxScale,
                dynamicLightingEnabled, maxDynamicLights, maxDynamicLightLevel, maxDynamicLightRadius,
                null, null, null, null, null);
    }

    public static OutfitLimits defaults() {
        return new OutfitLimits(32, 128, 64, 8192, 32768, 1024, 65536, 8, 256,
                128, 128, 512, 64, 256, 128, 4096, 36000, 0.001, 64, true, 8, 15, 16);
    }

    private static void bounded(int value, int minimum, int maximum) {
        OutfitConfig.require(value >= minimum && value <= maximum);
    }
}
