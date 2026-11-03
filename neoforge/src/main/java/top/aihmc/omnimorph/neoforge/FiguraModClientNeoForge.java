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
import net.neoforged.neoforge.client.event.RegisterPictureInPictureRenderersEvent;
import net.neoforged.neoforge.client.event.RenderGuiLayerEvent;
import org.figuramc.figura.FiguraMod;
import org.figuramc.figura.avatar.Avatar;
import org.figuramc.figura.avatar.AvatarManager;
import org.figuramc.figura.config.ConfigManager;
import top.aihmc.omnimorph.config.neoforge.ModConfig;
import top.aihmc.omnimorph.gui.neoforge.GuiOverlay;
import top.aihmc.omnimorph.gui.neoforge.GuiUnderlay;
import top.aihmc.omnimorph.gui.FiguraGuiRenderState;
import top.aihmc.omnimorph.gui.FiguraGuiRenderer;
import top.aihmc.omnimorph.gui.FiguraPortraitRenderState;
import top.aihmc.omnimorph.gui.FiguraPortraitRenderer;
import top.aihmc.omnimorph.model.library.LibraryFiguraPreviewRenderer;
import top.aihmc.omnimorph.model.library.LibraryFiguraPreviewState;
import top.aihmc.omnimorph.utils.neoforge.FiguraResourceListenerImpl;

import java.util.ArrayList;
import java.util.List;

@EventBusSubscriber(modid = FiguraModForge.MOD_ID, value = Dist.CLIENT)
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
    public static void registerPictureInPictureRenderers(RegisterPictureInPictureRenderersEvent event) {
        event.register(FiguraPortraitRenderState.class, FiguraPortraitRenderer::new);
        event.register(FiguraGuiRenderState.class, FiguraGuiRenderer::new);
        event.register(LibraryFiguraPreviewState.class, LibraryFiguraPreviewRenderer::new);
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
