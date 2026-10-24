package org.figuramc.figura.resources;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.network.chat.Component;
import net.minecraft.server.packs.PackLocationInfo;
import net.minecraft.server.packs.PathPackResources;
import net.minecraft.server.packs.repository.PackSource;
import org.figuramc.figura.FiguraMod;
import org.figuramc.figura.backend2.NetworkStuff;
import org.figuramc.figura.config.Configs;
import org.figuramc.figura.utils.IOUtils;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

public class FiguraRuntimeResources {

    private static String ASSETS_VERSION;
    private static final Set<String> BUNDLED_BRAND_RESOURCES = Set.of(
            "icon.png", "textures/gui/icon.png", "textures/gui/help_icons.png",
            "textures/gui/show_figura.png", "textures/gui/unknown_icon.png", "textures/avatar_wizard/icon.png",
            "textures/gui/toast/default.png", "textures/gui/toast/warning.png", "textures/gui/toast/error.png",
            "font/badges.json", "textures/font/omnimorph.png");
    public static final PathPackResources PACK = new PathPackResources(new PackLocationInfo(FiguraMod.MOD_NAME + " runtime resource pack", Component.literal("OmniMorph Runtime Resources"), PackSource.BUILT_IN, Optional.empty()), getRootDirectory());

    public static Path getRootDirectory() {
        return IOUtils.getOrCreateDir(FiguraMod.getCacheDirectory(), "resources");
    }

    public static Path getAssetsDirectory() {
        return IOUtils.getOrCreateDir(getRootDirectory(), "assets/" + FiguraMod.MOD_ID);
    }

    public static String getAssetsVersion() {
        if (ASSETS_VERSION == null)
             ASSETS_VERSION = FiguraMod.METADATA.getCustomValueAsString("assets_version");
        return ASSETS_VERSION;
    }

    private static CompletableFuture<Void> future;

    public static void clearCache() {
        IOUtils.deleteFile(getRootDirectory());
    }

    public static CompletableFuture<Void> init() {
        return future = CompletableFuture.runAsync(() -> {
            FiguraMod.LOGGER.info("Fetching backend resources...");
            // The runtime pack takes precedence over bundled assets, including offline caches.
            clearCachedBrandResources();

            JsonObject hashes, oldHashes;

            // get old hashes
            Path hashesPath = getRootDirectory().resolve("hashes.json");
            try (BufferedReader reader = Files.newBufferedReader(hashesPath)) {
                oldHashes = JsonParser.parseReader(reader).getAsJsonObject();
            } catch (Exception ignored) {
                oldHashes = new JsonObject();
            }

            // get new hashes
            try (InputStream stream = NetworkStuff.getResourcesHashes(getAssetsVersion())) {
                byte[] bytes = stream.readAllBytes();
                String s = new String(bytes);
                hashes = JsonParser.parseString(s).getAsJsonObject();

                // save new hashes
                try (OutputStream fs = Files.newOutputStream(hashesPath)) {
                    fs.write(bytes);
                } catch (Exception e) {
                    FiguraMod.LOGGER.error("Failed to save resource hashes", e);
                }
            } catch (Exception ignored) {
                FiguraMod.LOGGER.warn("Failed to fetch backend resources");
                return;
            }

            // compare hashes
            for (Map.Entry<String, JsonElement> entry : hashes.entrySet()) {
                String key = entry.getKey();
                JsonElement oldHash = oldHashes.get(key);
                Path target = getAssetsDirectory().resolve(key);
                try {
                    if (oldHash == null || !oldHash.getAsString().equals(entry.getValue().getAsString()) || !Files.exists(target)) {
                        getAndSaveResource(key);
                    }
                } catch (Exception e) {
                    FiguraMod.debug("Failed to download resource \"" + key + "\"", e);
                }
            }
            File assetsDirectoryFile = getAssetsDirectory().toFile();
            JsonObject directoryJsonObject = constructAssetsDirectoryJsonObject(assetsDirectoryFile.listFiles());
            for (Map.Entry<String, JsonElement> entry : directoryJsonObject.entrySet()) {
                if (!hashes.has(entry.getKey()) && !Configs.LOCAL_ASSETS.value) {
                    IOUtils.deleteFile(getAssetsDirectory().resolve(entry.getKey()));
                }
            }
        });
    }

    public static JsonObject constructAssetsDirectoryJsonObject(File[] files) {
        JsonObject object = new JsonObject();
        for (File file : files) {
            if (file.isDirectory()) {
                for (Map.Entry<String, JsonElement> entry : constructAssetsDirectoryJsonObject(file.listFiles()).entrySet()) {
                    object.addProperty(entry.getKey(), entry.getValue().getAsString());
                }
            } else if (!file.isHidden()){
                object.addProperty(getAssetsDirectory().toUri().relativize(file.toURI()).getPath(), "0");
            }
        }
        return object;
    }

    private static void clearCachedBrandResources() {
        if (Configs.LOCAL_ASSETS.value) {
            return;
        }
        for (String path : BUNDLED_BRAND_RESOURCES) {
            try {
                Files.deleteIfExists(getAssetsDirectory().resolve(path));
            } catch (IOException exception) {
                FiguraMod.LOGGER.warn("Failed to remove cached branding resource {}", path, exception);
            }
        }
    }

    private static void getAndSaveResource(String path) throws Exception {
        if (Configs.LOCAL_ASSETS.value || BUNDLED_BRAND_RESOURCES.contains(path)) {
            return;
        }
        Path target = getAssetsDirectory().resolve(path);
        IOUtils.createDirIfNeeded(target.getParent());
        try (InputStream resource = NetworkStuff.getResource(getAssetsVersion(), path); OutputStream fs = Files.newOutputStream(target)) {
            byte[] bytes = resource.readAllBytes();
            if (path.endsWith(".json")) {
                bytes = remapPublicResource(path, bytes);
            }
            fs.write(bytes);
            FiguraMod.debug("Downloaded resource \"" + path + "\"");
        }
    }

    private static byte[] remapPublicResource(String path, byte[] bytes) {
        String json = new String(bytes, java.nio.charset.StandardCharsets.UTF_8)
                .replace("\"figura:", "\"" + FiguraMod.MOD_ID + ":");
        if (!path.startsWith("lang/")) {
            return json.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        }
        JsonObject translations = new JsonObject();
        for (var entry : JsonParser.parseString(json).getAsJsonObject().entrySet()) {
            String key = entry.getKey();
            // Project identity and Help are supplied by our bundled dictionaries.
            if (key.equals("figura") || key.startsWith("figura.gui.help.")) {
                continue;
            }
            String mappedKey = key.startsWith("figura.") ? FiguraMod.MOD_ID + key.substring(6)
                    : key.replace("key.category.figura.", "key.category." + FiguraMod.MOD_ID + ".");
            String value = entry.getValue().getAsString();
            if (!key.startsWith("figura.docs.") && !key.contains("cloud_protocol")
                    && !key.contains(".filter.figura") && !key.endsWith("avatar_volume_gain.tooltip")) {
                value = value.replace("Figura Lua API", "__FIGURA_LUA_API__")
                        .replace("Figura Cloud", "__FIGURA_CLOUD__")
                        .replace("Figura Avatar", "__FIGURA_AVATAR__")
                        .replace("Figura", FiguraMod.MOD_NAME)
                        .replace("__FIGURA_LUA_API__", "Figura Lua API")
                        .replace("__FIGURA_CLOUD__", "Figura Cloud")
                        .replace("__FIGURA_AVATAR__", "Figura Avatar");
            }
            translations.addProperty(mappedKey, value.replace("/figura ", "/" + FiguraMod.MOD_ID + " "));
        }
        return translations.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8);
    }

    public static void joinFuture() {
        if (future != null && !future.isDone())
            future.join();
    }
}
