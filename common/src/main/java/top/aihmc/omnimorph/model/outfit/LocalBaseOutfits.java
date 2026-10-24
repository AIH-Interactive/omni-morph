package top.aihmc.omnimorph.model.outfit;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import top.aihmc.omnimorph.FiguraMod;
import top.aihmc.omnimorph.avatar.AvatarLoadExecutor;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Device-local accessory sets remembered per base avatar. Anchors and bone references belong to one base's
 * skeleton, so each base keeps its own instances; nothing here is published or shared with the server.
 */
public final class LocalBaseOutfits {
    private static final Map<Path, LocalBaseOutfits> STORES = new HashMap<>();
    private static final int MAX_BASES = 64;
    private static final int MAX_FILE_BYTES = MAX_BASES * (OutfitConfig.MAX_JSON_BYTES + 1024);

    /** Insertion order is refreshed on every save so the least recently edited base is evicted first. */
    private final LinkedHashMap<String, OutfitConfig> outfits = new LinkedHashMap<>();
    private final Path file;
    private volatile long generation;

    public static synchronized LocalBaseOutfits forDirectory(Path root) {
        return STORES.computeIfAbsent(root.toAbsolutePath().normalize(), LocalBaseOutfits::new);
    }

    private LocalBaseOutfits(Path root) {
        file = root.resolve("wardrobe-base-outfits.json");
        try {
            if (Files.isRegularFile(file) && Files.size(file) <= MAX_FILE_BYTES) {
                JsonObject document = JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8))
                        .getAsJsonObject();
                if (document.get("schema_version").getAsInt() == 1) {
                    for (var entry : document.getAsJsonObject("bases").entrySet()) {
                        readEntry(entry.getKey(), entry.getValue().toString());
                    }
                }
            }
        } catch (IOException | RuntimeException invalid) {
            FiguraMod.LOGGER.warn("Could not read local per-avatar outfits", invalid);
        }
    }

    private void readEntry(String identity, String json) {
        try {
            OutfitConfig configuration = OutfitCodec.decode(json, OutfitLimits.defaults());
            if (configuration.base() != null && configuration.base().identity().equals(identity)) {
                outfits.put(identity, configuration);
            }
        } catch (RuntimeException invalid) {
            FiguraMod.LOGGER.warn("Ignoring invalid local outfit for base {}", identity, invalid);
        }
    }

    /** The accessory set last edited on this base, or null when the base has never been dressed here. */
    public synchronized OutfitConfig get(OutfitConfig.ResourceRef base) {
        return base == null ? null : outfits.get(base.identity());
    }

    /** Remember the configuration under its own base. Vanilla (no base) is not remembered. */
    public synchronized void remember(OutfitConfig configuration) {
        if (configuration == null || configuration.base() == null) {
            return;
        }
        String identity = configuration.base().identity();
        if (configuration.equals(outfits.get(identity))) {
            return;
        }
        outfits.remove(identity);
        outfits.put(identity, configuration);
        while (outfits.size() > MAX_BASES) {
            outfits.remove(outfits.keySet().iterator().next());
        }
        save();
    }

    private void save() {
        var bases = new JsonObject();
        for (var entry : outfits.entrySet()) {
            try {
                bases.add(entry.getKey(), JsonParser.parseString(OutfitCodec.encode(entry.getValue(), OutfitLimits.defaults())));
            } catch (RuntimeException invalid) {
                FiguraMod.LOGGER.warn("Not saving local outfit for base {}", entry.getKey(), invalid);
            }
        }
        var document = new JsonObject();
        document.addProperty("schema_version", 1);
        document.add("bases", bases);
        String json = document.toString();
        long expected = ++generation;
        AvatarLoadExecutor.submit(() -> {
            synchronized (file) {
                if (expected != generation) {
                    return true;
                }
                Path partial = null;
                try {
                    Files.createDirectories(file.getParent());
                    partial = Files.createTempFile(file.getParent(), "base-outfits-", ".part");
                    Files.writeString(partial, json, StandardCharsets.UTF_8);
                    Files.move(partial, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
                    return true;
                } catch (IOException failure) {
                    FiguraMod.LOGGER.warn("Could not save local per-avatar outfits", failure);
                    return false;
                } finally {
                    if (partial != null) {
                        try {
                            Files.deleteIfExists(partial);
                        } catch (IOException failure) {
                            FiguraMod.debug("Could not remove per-avatar outfit temporary file");
                        }
                    }
                }
            }
        });
    }
}
