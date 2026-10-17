package top.aihmc.omnimorph.neoforge;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.AddClientReloadListenersEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.RenderGuiLayerEvent;
import top.aihmc.omnimorph.FiguraMod;
import top.aihmc.omnimorph.avatar.Avatar;
import top.aihmc.omnimorph.avatar.AvatarManager;
import top.aihmc.omnimorph.config.ConfigManager;
import top.aihmc.omnimorph.config.neoforge.ModConfig;
import top.aihmc.omnimorph.gui.neoforge.GuiOverlay;
import top.aihmc.omnimorph.gui.neoforge.GuiUnderlay;
import top.aihmc.omnimorph.utils.neoforge.FiguraResourceListenerImpl;

import java.util.ArrayList;
import java.util.List;

@EventBusSubscriber(modid = FiguraMod.MOD_ID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public class FiguraModClientNeoForge extends FiguraMod {
    // keybinds stored here
    public static List<KeyMapping> KEYBINDS = new ArrayList<>();

    @SubscribeEvent
    public static void onInitializeClient(FMLClientSetupEvent event) {
        onClientInit();
        ModConfig.registerConfigScreen();
    }

    @SubscribeEvent
    public static void registerResourceListener(AddClientReloadListenersEvent event) {
        getResourceListeners().forEach(figuraResourceListener -> event.addListener(new Identifier(FiguraMod.MOD_ID, figuraResourceListener.id()), (FiguraResourceListenerImpl)figuraResourceListener));
    }

    @SubscribeEvent
    public static void registerOverlays(RegisterGuiLayersEvent event) {
        event.registerAboveAll(new Identifier(FiguraMod.MOD_ID, "figura_overlay"), new GuiOverlay());
        event.registerBelowAll(new Identifier(FiguraMod.MOD_ID, "figura_underlay"), new GuiUnderlay());
    }

    private static final List<Identifier> vanillaOverlays = new ArrayList<>();

    public static void cancelVanillaOverlays(RenderGuiLayerEvent.Pre event) {
        if (event.getName().getNamespace().equals("minecraft")) {
            Entity entity = Minecraft.getInstance().getCameraEntity();
            Avatar avatar = entity == null ? null : AvatarManager.getAvatar(entity);
            if (avatar != null && avatar.luaRuntime != null && !avatar.luaRuntime.renderer.renderHUD) {
                event.setCanceled(true);
            }
        }
    }

    @SubscribeEvent
    public static void registerKeyBinding(RegisterKeyMappingsEvent event) {
        // Config has to be initialized here, so that the keybinds exist on time
        ConfigManager.init();
        for (KeyMapping value : KEYBINDS) {
            if(value != null)
                event.register(value);
        }
    }
}
