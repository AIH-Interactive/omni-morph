package top.aihmc.omnimorph.utils;

import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import top.aihmc.omnimorph.FiguraMod;

import java.io.InputStream;
import java.util.Optional;

public class ResourceUtils {

    public static byte[] getResource(ResourceManager manager, Identifier path) {
        Optional<Resource> optional = manager.getResource(path);
        if (optional.isPresent()) {
            try (InputStream is = optional.get().open()) {
                return is.readAllBytes();
            } catch (Exception e) {
                FiguraMod.LOGGER.error("", e);
            }
        }
        return null;
    }
}
