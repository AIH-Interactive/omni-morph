package org.figuramc.figura.avatar.ysm;

import net.minecraft.nbt.CompoundTag;
import org.figuramc.figura.utils.IOUtils;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

public final class YsmTextureSelectionStore {
    private static final Map<String, String> SELECTIONS = new HashMap<>();
    private static boolean loaded;

    private YsmTextureSelectionStore() {
    }

    public static String get(Path path) {
        load();
        return SELECTIONS.get(key(path));
    }

    public static void set(Path path, String textureId) {
        load();
        String key = key(path);
        if (textureId == null || textureId.isBlank())
            SELECTIONS.remove(key);
        else
            SELECTIONS.put(key, textureId);
        save();
    }

    private static void load() {
        if (loaded)
            return;
        loaded = true;
        IOUtils.readCacheFile("ysm_textures", nbt -> {
            for (String key : nbt.keySet())
                SELECTIONS.put(key, nbt.getStringOr(key, ""));
        });
    }

    private static void save() {
        IOUtils.saveCacheFile("ysm_textures", nbt -> {
            for (Map.Entry<String, String> entry : SELECTIONS.entrySet())
                nbt.putString(entry.getKey(), entry.getValue());
        });
    }

    private static String key(Path path) {
        return path == null ? "" : path.toAbsolutePath().normalize().toString();
    }
}
