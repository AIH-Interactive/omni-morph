package top.aihmc.omnimorph.neoforge;

import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;

@Mod(FiguraModForge.MOD_ID)
public class FiguraModForge {
    // NeoForge mod IDs cannot contain the resource namespace's hyphen.
    public static final String MOD_ID = "omni_morph";

    // dummy empty mod class, we are client only
    public FiguraModForge() {
        NeoForge.EVENT_BUS.addListener(FiguraModClientNeoForge::cancelVanillaOverlays);
    }
}
