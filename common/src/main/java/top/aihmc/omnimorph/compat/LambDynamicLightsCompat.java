package top.aihmc.omnimorph.compat;

import dev.lambdaurora.lambdynlights.api.DynamicLightsContext;
import dev.lambdaurora.lambdynlights.api.DynamicLightsInitializer;
import dev.lambdaurora.lambdynlights.api.behavior.DynamicLightBehaviorManager;
import dev.lambdaurora.lambdynlights.api.item.ItemLightSourceManager;
import top.aihmc.omnimorph.lua.api.particle.ParticleDynamicLights;

/**
 * LambDynamicLights entrypoint (declared in fabric.mod.json). When the mod is installed it takes over particle
 * illumination through its public dynamic light behavior API, and the built-in light field stops injecting light
 * so the two providers cannot double up. Without LambDynamicLights this class is never loaded.
 */
public final class LambDynamicLightsCompat implements DynamicLightsInitializer {
    private static DynamicLightBehaviorManager manager;

    @Override
    public void onInitializeDynamicLights(DynamicLightsContext context) {
        manager = context.dynamicLightBehaviorManager();
        register();
        ParticleDynamicLights.useExternalProvider(true);
    }

    /** Legacy LambDynamicLights 4.8 item-light entrypoint; particle lights only use the context overload. */
    @Override
    public void onInitializeDynamicLights(ItemLightSourceManager itemLightSourceManager) {
    }

    /** LambDynamicLights clears behavior sources on world change, so register again after joining a world. */
    public static void onWorldJoin() {
        if (manager != null) {
            register();
        }
    }

    private static void register() {
        manager.add(new ParticleLightBehavior());
    }
}
