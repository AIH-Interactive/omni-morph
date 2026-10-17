package top.aihmc.omnimorph.neoforge;

import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;

@Mod("omni-morph")
public class FiguraModForge {
    // dummy empty mod class, we are client only
    public FiguraModForge() {
        NeoForge.EVENT_BUS.addListener(FiguraModClientNeoForge::cancelVanillaOverlays);
    }
}
