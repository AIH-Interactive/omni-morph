package top.aihmc.omnimorph.config.neoforge;

import org.figuramc.figura.config.ConfigKeyBind;
import top.aihmc.omnimorph.neoforge.FiguraModClientNeoForge;

public class ConfigKeyBindImpl {
    public static void addKeyBind(ConfigKeyBind keyBind) {
        FiguraModClientNeoForge.KEYBINDS.add(keyBind);
    }
}
