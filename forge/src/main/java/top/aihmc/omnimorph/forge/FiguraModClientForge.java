package top.aihmc.omnimorph.forge;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ShaderInstance;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterClientReloadListenersEvent;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.client.event.RegisterShadersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import org.figuramc.figura.FiguraMod;
import org.figuramc.figura.config.ConfigManager;
import org.figuramc.figura.avatar.AvatarManager;
import org.figuramc.figura.gui.FiguraGui;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import top.aihmc.omnimorph.model.ysm.YsmPreviewRenderTypes;
import top.aihmc.omnimorph.config.forge.ModConfig;
import top.aihmc.omnimorph.utils.forge.FiguraResourceListenerImpl;

import java.util.ArrayList;
import java.util.List;

@Mod.EventBusSubscriber(modid = top.aihmc.omnimorph.forge.FiguraModForge.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public class FiguraModClientForge extends FiguraMod {
    // keybinds stored here
    public static List<KeyMapping> KEYBINDS = new ArrayList<>();

    @SubscribeEvent
    public static void onInitializeClient(FMLClientSetupEvent event) {
        onClientInit();
        ModConfig.registerConfigScreen();
    }

    @SubscribeEvent
    public static void registerResourceListener(RegisterClientReloadListenersEvent event) {
        getResourceListeners().forEach(figuraResourceListener -> event.registerReloadListener((FiguraResourceListenerImpl)figuraResourceListener));
    }

    @SubscribeEvent
    public static void registerShaders(RegisterShadersEvent event) throws java.io.IOException {
        event.registerShader(new ShaderInstance(event.getResourceProvider(), YsmPreviewRenderTypes.SHADER_ID,
                DefaultVertexFormat.NEW_ENTITY), YsmPreviewRenderTypes::setShader);
    }

    @SubscribeEvent
    public static void registerOverlays(RegisterGuiOverlaysEvent event) {
        event.registerBelowAll("figura_underlay", (forgeGui, graphics, partialTick, width, height) ->
                FiguraGui.onRender(graphics, partialTick, new CallbackInfo("figura_hud", true)));
        event.registerAboveAll("figura_overlay", (forgeGui, graphics, partialTick, width, height) -> {
            if (!AvatarManager.panic && !hideVanillaHud()) {
                FiguraGui.renderOverlays(graphics);
            }
        });
    }

    private static boolean hideVanillaHud() {
        var entity = Minecraft.getInstance().getCameraEntity();
        var avatar = entity == null ? null : AvatarManager.getAvatar(entity);
        return !AvatarManager.panic && avatar != null && avatar.luaRuntime != null
                && !avatar.luaRuntime.renderer.renderHUD;
    }

    public static void cancelVanillaOverlays(RenderGuiOverlayEvent.Pre event) {
        if (event.getOverlay().id().getNamespace().equals("minecraft") && hideVanillaHud()) {
            event.setCanceled(true);
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
