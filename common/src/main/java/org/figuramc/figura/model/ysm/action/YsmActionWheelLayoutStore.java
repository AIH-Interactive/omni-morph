package org.figuramc.figura.model.ysm.action;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import org.figuramc.figura.utils.IOUtils;

import java.util.HashMap;
import java.util.Map;

public final class YsmActionWheelLayoutStore {
    private static final Map<String, Map<String, Entry>> LAYOUTS = new HashMap<>();
    private static boolean loaded;

    private YsmActionWheelLayoutStore() {
    }

    public static Entry get(String modelKey, String actionId) {
        if (modelKey == null || modelKey.isBlank() || actionId == null || actionId.isBlank())
            return null;
        load();
        return LAYOUTS.getOrDefault(modelKey, Map.of()).get(normalize(actionId));
    }

    public static void set(String modelKey, String actionId, String page, int slot) {
        if (modelKey == null || modelKey.isBlank() || actionId == null || actionId.isBlank())
            return;
        load();
        if (slot < 1) {
            Map<String, Entry> entries = LAYOUTS.get(modelKey);
            if (entries != null)
                entries.remove(normalize(actionId));
        } else {
            LAYOUTS.computeIfAbsent(modelKey, ignored -> new HashMap<>())
                    .put(normalize(actionId), new Entry(page == null || page.isBlank() ? "extra_animation" : page, slot));
        }
        save();
    }

    private static void load() {
        if (loaded)
            return;
        loaded = true;
        IOUtils.readCacheFile("ysm_action_wheel", nbt -> {
            for (Tag modelTag : nbt.getListOrEmpty("models")) {
                if (!(modelTag instanceof CompoundTag modelNbt))
                    continue;
                String model = modelNbt.getStringOr("model", "");
                if (model.isBlank())
                    continue;
                Map<String, Entry> entries = LAYOUTS.computeIfAbsent(model, ignored -> new HashMap<>());
                for (Tag entryTag : modelNbt.getListOrEmpty("entries")) {
                    if (!(entryTag instanceof CompoundTag entryNbt))
                        continue;
                    String action = normalize(entryNbt.getStringOr("action", ""));
                    if (!action.isBlank())
                        entries.put(action, new Entry(entryNbt.getStringOr("page", "extra_animation"), entryNbt.getIntOr("slot", 1)));
                }
            }
        });
    }

    private static void save() {
        IOUtils.saveCacheFile("ysm_action_wheel", nbt -> {
            ListTag models = new ListTag();
            for (Map.Entry<String, Map<String, Entry>> modelEntry : LAYOUTS.entrySet()) {
                CompoundTag modelNbt = new CompoundTag();
                modelNbt.putString("model", modelEntry.getKey());
                ListTag entries = new ListTag();
                for (Map.Entry<String, Entry> actionEntry : modelEntry.getValue().entrySet()) {
                    CompoundTag entryNbt = new CompoundTag();
                    Entry entry = actionEntry.getValue();
                    entryNbt.putString("action", actionEntry.getKey());
                    entryNbt.putString("page", entry.page());
                    entryNbt.putInt("slot", entry.slot());
                    entries.add(entryNbt);
                }
                modelNbt.put("entries", entries);
                models.add(modelNbt);
            }
            nbt.put("models", models);
        });
    }

    private static String normalize(String value) {
        return value == null ? "" : value.toLowerCase(java.util.Locale.US);
    }

    public record Entry(String page, int slot) {
    }
}
