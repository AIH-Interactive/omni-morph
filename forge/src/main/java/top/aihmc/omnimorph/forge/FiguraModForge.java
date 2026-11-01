package top.aihmc.omnimorph.forge;

import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.Mod;

@Mod(FiguraModForge.MOD_ID)
public class FiguraModForge {
    public static final String MOD_ID = "omni_morph";
    // dummy empty mod class, we are client only
    public FiguraModForge() {
        MinecraftForge.EVENT_BUS.addListener(FiguraModClientForge::cancelVanillaOverlays);
    }
}
