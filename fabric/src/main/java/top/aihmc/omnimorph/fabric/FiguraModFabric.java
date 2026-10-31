package top.aihmc.omnimorph.fabric;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.minecraft.server.packs.PackType;
import org.figuramc.figura.FiguraMod;
import org.figuramc.figura.config.ConfigManager;
import top.aihmc.omnimorph.commands.fabric.FiguraCommandsFabric;
import top.aihmc.omnimorph.utils.fabric.FiguraResourceListenerImpl;

public class FiguraModFabric extends FiguraMod implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        ConfigManager.init();
        onClientInit();
        FiguraCommandsFabric.init();
        // register reload listener
        ResourceManagerHelper resourceManagerHelper = ResourceManagerHelper.get(PackType.CLIENT_RESOURCES);
        getResourceListeners().forEach(figuraResourceListener -> {
            FiguraResourceListenerImpl impl = (FiguraResourceListenerImpl) figuraResourceListener;
            resourceManagerHelper.registerReloadListener(impl);
        });
    }
}
