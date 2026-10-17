package top.aihmc.omnimorph.config.neoforge;

import top.aihmc.omnimorph.config.ConfigKeyBind;
import top.aihmc.omnimorph.neoforge.FiguraModClientNeoForge;

public class ConfigKeyBindImpl {
    public static void addKeyBind(ConfigKeyBind keyBind) {
        FiguraModClientNeoForge.KEYBINDS.add(keyBind);
    }
}
