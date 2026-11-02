package top.aihmc.omnimorph.neoforge;

import net.neoforged.fml.common.Mod;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.neoforge.common.NeoForge;

@Mod(value = FiguraModForge.MOD_ID, dist = Dist.CLIENT)
public class FiguraModForge {
    // NeoForge disallows hyphens in mod IDs; resource namespaces stay "omni-morph".
    public static final String MOD_ID = "omni_morph";

    // dummy empty mod class, we are client only
    public FiguraModForge() {
        NeoForge.EVENT_BUS.addListener(FiguraModClientNeoForge::cancelVanillaOverlays);
    }
}
