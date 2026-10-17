package top.aihmc.omnimorph.config.fabric;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;
import top.aihmc.omnimorph.FiguraMod;
import top.aihmc.omnimorph.config.Configs;
import top.aihmc.omnimorph.gui.screens.ConfigScreen;

public class ModMenuConfig implements ModMenuApi {
    @Override
    public ConfigScreenFactory<?> getModConfigScreenFactory() {
        return parentScreen -> new ConfigScreen(parentScreen, FiguraMod.debugModeEnabled());
    }
}