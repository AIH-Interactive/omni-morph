package top.aihmc.omnimorph.fabric;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.resource.v1.ResourceLoader;
import net.minecraft.server.packs.PackType;
import top.aihmc.omnimorph.FiguraMod;
import top.aihmc.omnimorph.commands.fabric.FiguraCommandsFabric;
import top.aihmc.omnimorph.config.ConfigManager;
import top.aihmc.omnimorph.utils.FiguraIdentifier;
import top.aihmc.omnimorph.utils.fabric.FiguraResourceListenerImpl;

public class FiguraModFabric extends FiguraMod implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        ConfigManager.init();
        onClientInit();
        FiguraCommandsFabric.init();
        // register reload listener
        ResourceLoader resourceLoader = ResourceLoader.get(PackType.CLIENT_RESOURCES);
        getResourceListeners().forEach(figuraResourceListener -> {
            FiguraResourceListenerImpl impl = (FiguraResourceListenerImpl) figuraResourceListener;
            resourceLoader.registerReloadListener(impl.getFabricId(), impl);
        });
    }
}
