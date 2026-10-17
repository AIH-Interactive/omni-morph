package top.aihmc.omnimorph.config.forge;

import top.aihmc.omnimorph.config.ConfigKeyBind;
import top.aihmc.omnimorph.forge.FiguraModClientForge;

public class ConfigKeyBindImpl {
    public static void addKeyBind(ConfigKeyBind keyBind) {
        FiguraModClientForge.KEYBINDS.add(keyBind);
    }
}
