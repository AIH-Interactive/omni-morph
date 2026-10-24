package top.aihmc.omnimorph.utils.fabric;

import net.fabricmc.loader.api.FabricLoader;
import org.figuramc.figura.FiguraMod;
import org.figuramc.figura.utils.PlatformUtils;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.nio.file.Path;

public class PlatformUtilsImpl {
    public static Path getGameDir() {
        return FabricLoader.getInstance().getGameDir();
    }

    public static String getFiguraModVersionString() {
        return FabricLoader.getInstance().getModContainer(FiguraMod.MOD_ID).get().getMetadata().getVersion().getFriendlyString();
    }

    public static Path getConfigDir() {
        return FabricLoader.getInstance().getConfigDir();
    }

    public static boolean isModLoaded(String modId) {
        return FabricLoader.getInstance().isModLoaded(modId);
    }

    public static String getModVersion(String modId) {
        return FabricLoader.getInstance().getModContainer(modId).get().getMetadata().getVersion().getFriendlyString();
    }

    public static java.util.Map<String, String> getLoadedMods() {
        return FabricLoader.getInstance().getAllMods().stream()
                .map(container -> container.getMetadata())
                .collect(java.util.stream.Collectors.toUnmodifiableMap(metadata -> metadata.getId(), metadata -> metadata.getName()));
    }

    public static PlatformUtils.ModLoader getModLoader() {
        return PlatformUtils.ModLoader.FABRIC;
    }

    public static InputStream loadFileFromRoot(String path) throws FileNotFoundException {
        File file = FabricLoader.getInstance().getModContainer(FiguraMod.MOD_ID).get().findPath(path).get().toFile();
        return new FileInputStream(file);
    }
}
