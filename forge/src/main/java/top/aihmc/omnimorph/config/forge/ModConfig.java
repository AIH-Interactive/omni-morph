package top.aihmc.omnimorph.config.forge;

import net.minecraftforge.client.ConfigScreenHandler;
import net.minecraftforge.fml.ModLoadingContext;
import top.aihmc.omnimorph.FiguraMod;
import top.aihmc.omnimorph.gui.screens.ConfigScreen;

public class ModConfig {
    public static void registerConfigScreen() {
        ModLoadingContext.get().registerExtensionPoint(ConfigScreenHandler.ConfigScreenFactory.class, () -> new ConfigScreenHandler.ConfigScreenFactory(
                (client, parent) -> new ConfigScreen(parent, FiguraMod.debugModeEnabled())));
    }
}
