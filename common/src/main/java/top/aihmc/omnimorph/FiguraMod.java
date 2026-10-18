package top.aihmc.omnimorph;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import top.aihmc.omnimorph.avatar.Avatar;
import top.aihmc.omnimorph.avatar.AvatarManager;
import top.aihmc.omnimorph.avatar.local.CacheAvatarLoader;
import top.aihmc.omnimorph.avatar.local.LocalAvatarFetcher;
import top.aihmc.omnimorph.avatar.local.LocalAvatarLoader;
import top.aihmc.omnimorph.backend2.NetworkStuff;
import top.aihmc.omnimorph.compat.GeckoLibCompat;
import top.aihmc.omnimorph.compat.IrisCompat;
import top.aihmc.omnimorph.compat.SimpleVCCompat;
import top.aihmc.omnimorph.config.Configs;
import top.aihmc.omnimorph.entries.EntryPointManager;
import top.aihmc.omnimorph.font.Emojis;
import top.aihmc.omnimorph.lua.FiguraLuaPrinter;
import top.aihmc.omnimorph.lua.docs.FiguraDocsManager;
import top.aihmc.omnimorph.mixin.gui.DebugScreenEntriesAccessor;
import top.aihmc.omnimorph.mixin.MinecraftAccesor;
import top.aihmc.omnimorph.gui.widgets.DebugEntryFigura;
import top.aihmc.omnimorph.permissions.PermissionManager;
import top.aihmc.omnimorph.resources.FiguraRuntimeResources;
import top.aihmc.omnimorph.render.acceleration.HardwareAcceleration;
import top.aihmc.omnimorph.model.ysm.YsmInputState;
import top.aihmc.omnimorph.utils.*;
import top.aihmc.omnimorph.wizards.AvatarWizard;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.*;

public class FiguraMod {

    public static final String MOD_ID = "omni-morph";
    public static final String MOD_NAME = "OmniMorph";
    public static final String DISPLAY_VERSION = "1.0 Alpha 2";
    public static final FiguraModMetadata METADATA = FiguraModMetadata.getMetadataForMod(MOD_ID);
    public static final Version VERSION = new Version(PlatformUtils.getFiguraModVersionString());
    public static final Calendar CALENDAR = Calendar.getInstance();
    public static final Path GAME_DIR = PlatformUtils.getGameDir().normalize();
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_NAME);
    public static final float VERTEX_OFFSET = -0.0005f;

    public static int ticks;
    public static Entity extendedPickEntity;
    public static Component splashText;
    public static boolean parseMessages = true;
    public static boolean processingKeybind;
    public static KeyMapping.Category FIGURA_KEY_CATEGORY = new KeyMapping.Category(new FiguraIdentifier("keys"));
    public static Identifier FIGURA_DEBUG_KEY = new Identifier(MOD_ID, "figura_debug_info");

    /* For some reason, the mod menu entrypoint (or something) is able to call this before the Config
    class can initialize, meaning Configs.DEBUG_MODE can be null when this is called.... Weird */
    @SuppressWarnings("all")
    public static boolean debugModeEnabled() {
        return Configs.DEBUG_MODE != null && Configs.DEBUG_MODE.value;
    }

    public static void onClientInit() {
        // init managers
        EntryPointManager.init();
        PermissionManager.init();
        LocalAvatarFetcher.init();
        CacheAvatarLoader.init();
        FiguraDocsManager.init();
        FiguraRuntimeResources.init();
        DebugScreenEntriesAccessor.figura$invokeRegister(FIGURA_DEBUG_KEY, new DebugEntryFigura());

        GeckoLibCompat.init();
        SimpleVCCompat.init();
        if (PlatformUtils.isModLoaded("iris")) {
            IrisCompat.assignPipelinesToIrisPrograms();
        }
    }

    public static List<FiguraResourceListener> getResourceListeners() {
        List<FiguraResourceListener> listeners = new ArrayList<>();
        listeners.add(LocalAvatarLoader.AVATAR_LISTENER);
        listeners.add(Emojis.RESOURCE_LISTENER);
        listeners.add(AvatarWizard.RESOURCE_LISTENER);
        listeners.add(AvatarManager.RESOURCE_RELOAD_EVENT);
        return listeners;
    }


    public static void tick() {
        HardwareAcceleration.flushChat();
        YsmInputState.device().tick(Minecraft.getInstance());
        pushProfiler("network");
        NetworkStuff.tick();
        popPushProfiler("files");
        LocalAvatarLoader.tick();
        LocalAvatarFetcher.tick();
        if (Minecraft.getInstance().player != null) {
            popPushProfiler("avatars");
            AvatarManager.tickLoadedAvatars();
        }
        popPushProfiler("chatPrint");
        FiguraLuaPrinter.printChatFromQueue();
        popPushProfiler("emojiAnim");
        Emojis.tickAnimations();
        popProfiler();
        ticks++;
    }

    // -- Helper Functions -- //

    // debug print
    public static void debug(String str, Object... args) {
        if (FiguraMod.debugModeEnabled()) LOGGER.info("[DEBUG] " + str, args);
        else LOGGER.debug(str, args);
    }

    // mod root directory
    public static synchronized Path getFiguraDirectory() {
        String config = Configs.MAIN_DIR.value;
        if (!config.isBlank()) {
            return IOUtils.createDirIfNeeded(Path.of(config));
        }

        Path gameDirectory = GAME_DIR.toAbsolutePath().normalize();
        Path directory = gameDirectory.resolve("omnimorph");
        if (!Files.exists(directory, LinkOption.NOFOLLOW_LINKS)) {
            for (String oldName : new String[] {"omni-morph", "figura"}) {
                Path legacy = gameDirectory.resolve(oldName).normalize();
                // Restrict directory renames to direct children of the game directory.
                if (!gameDirectory.equals(legacy.getParent()) || !gameDirectory.equals(directory.getParent())) {
                    throw new IllegalStateException("Mod directory migration escaped the game directory");
                }
                if (!Files.isDirectory(legacy)) {
                    continue;
                }
                try {
                    Files.move(legacy, directory);
                    LOGGER.info("Migrated mod directory from {} to {}", legacy, directory);
                } catch (IOException exception) {
                    LOGGER.error("Failed to migrate mod directory; continuing to use {}", legacy, exception);
                    return legacy;
                }
                break;
            }
        }
        return IOUtils.createDirIfNeeded(directory);
    }

    // mod cache directory
    public static Path getCacheDirectory() {
        return IOUtils.getOrCreateDir(getFiguraDirectory(), Configs.LOCAL_ASSETS.value ? "local_cache" : "cache");
    }

    // get local player uuid
    public static UUID getLocalPlayerUUID() {
        Entity player = Minecraft.getInstance().player;
        return player != null ? player.getUUID() : Minecraft.getInstance().getUser().getProfileId();
    }

    public static boolean isLocal(UUID other) {
        return getLocalPlayerUUID().equals(other);
    }

    /**
     * Sends a chat message right away. Use when you know your message is safe.
     * If your message is unsafe, (generated by a user), use luaSendChatMessage instead.
     *
     * @param message - text to send
     */
    public static void sendChatMessage(Component message) {
        if (Minecraft.getInstance().gui != null) {
            parseMessages = false;
            Minecraft.getInstance().gui.getChat().addClientSystemMessage(TextUtils.replaceTabs(message));
            parseMessages = true;
        } else {
            LOGGER.info(message.getString());
        }
    }

    public static Style getAccentColor() {
        Avatar avatar = AvatarManager.getAvatarForPlayer(getLocalPlayerUUID());
        int color = avatar != null ? ColorUtils.rgbToInt(ColorUtils.userInputHex(avatar.color, ColorUtils.Colors.AWESOME_BLUE.vec)) : ColorUtils.Colors.AWESOME_BLUE.hex;
        return Style.EMPTY.withColor(color);
    }

    // -- profiler -- //

    public static void pushProfiler(String name) {
        ((MinecraftAccesor)Minecraft.getInstance()).figura$invokeGetMetricsRecorder().getProfiler().push(name);
    }

    public static void pushProfiler(Avatar avatar) {
        ((MinecraftAccesor)Minecraft.getInstance()).figura$invokeGetMetricsRecorder().getProfiler().push(avatar.entityName.isBlank() ? avatar.owner.toString() : avatar.entityName);
    }

    public static void popPushProfiler(String name) {
        ((MinecraftAccesor)Minecraft.getInstance()).figura$invokeGetMetricsRecorder().getProfiler().popPush(name);
    }

    public static void popProfiler() {
        ((MinecraftAccesor)Minecraft.getInstance()).figura$invokeGetMetricsRecorder().getProfiler().pop();
    }

    public static <T> T popReturnProfiler(T var) {
        ((MinecraftAccesor)Minecraft.getInstance()).figura$invokeGetMetricsRecorder().getProfiler().pop();
        return var;
    }

    public static void popProfiler(int times) {
        var profiler = ((MinecraftAccesor)Minecraft.getInstance()).figura$invokeGetMetricsRecorder().getProfiler();
        for (int i = 0; i < times; i++)
            profiler.pop();
    }

    public enum Links {
        Docs("https://github.com/AIH-Interactive/omni-morph/blob/main/README.md", ColorUtils.Colors.AWESOME_BLUE.style),
        MolangGuide("https://github.com/AIH-Interactive/omni-morph/blob/main/README.md#lua-与-molang", ColorUtils.Colors.AWESOME_BLUE.style),
        Github("https://github.com/AIH-Interactive", ColorUtils.Colors.GITHUB.style),
        Credits("https://github.com/AIH-Interactive/omni-morph/blob/main/CREDITS", ColorUtils.Colors.GITHUB.style),
        Upstream("https://github.com/FiguraMC/Figura", ColorUtils.Colors.GITHUB.style),
        Releases("https://github.com/AIH-Interactive/omni-morph/releases", ColorUtils.Colors.GITHUB.style),
        LuaManual("https://www.lua.org/manual/5.2/manual.html", ColorUtils.Colors.LUA_LOG.style);

        public final String url;
        public final Style style;

        Links(String url, Style style) {
            this.url = url;
            this.style = style;
        }
    }
}
