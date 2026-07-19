package org.figuramc.figura.model.ysm.action;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import org.figuramc.figura.utils.IOUtils;

import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

public final class YsmActionWheelLayoutStore {
    public static final int MIN_SLOT = 1;
    public static final int MAX_SLOT = 8;
    private static final Map<String, Map<String, Entry>> LAYOUTS = new HashMap<>();
    private static final Map<String, Set<String>> PAGES = new HashMap<>();
    private static boolean loaded;

    private YsmActionWheelLayoutStore() {
    }

    public static Entry get(String modelKey, String actionId) {
        if (modelKey == null || modelKey.isBlank() || actionId == null || actionId.isBlank())
            return null;
        load();
        return LAYOUTS.getOrDefault(modelKey, Map.of()).get(normalize(actionId));
    }

    public static Set<String> pages(String modelKey) {
        if (modelKey == null || modelKey.isBlank())
            return Set.of();
        load();
        return new LinkedHashSet<>(PAGES.getOrDefault(modelKey, Set.of()));
    }

    public static void addPage(String modelKey, String page) {
        if (modelKey == null || modelKey.isBlank() || page == null || page.isBlank())
            return;
        load();
        PAGES.computeIfAbsent(modelKey, ignored -> new LinkedHashSet<>()).add(page);
        save();
    }

    public static void removePage(String modelKey, String page) {
        if (modelKey == null || modelKey.isBlank() || page == null || page.isBlank())
            return;
        load();
        Set<String> pages = PAGES.get(modelKey);
        if (pages != null)
            pages.remove(page);
        Map<String, Entry> entries = LAYOUTS.get(modelKey);
        if (entries != null)
            entries.entrySet().removeIf(entry -> page.equals(entry.getValue().page()));
        save();
    }

    public static void renamePage(String modelKey, String oldPage, String newPage) {
        if (modelKey == null || modelKey.isBlank() || oldPage == null || oldPage.isBlank() || newPage == null || newPage.isBlank() || oldPage.equals(newPage))
            return;
        load();
        Set<String> pages = PAGES.computeIfAbsent(modelKey, ignored -> new LinkedHashSet<>());
        if (pages.remove(oldPage))
            pages.add(newPage);
        Map<String, Entry> entries = LAYOUTS.get(modelKey);
        if (entries != null) {
            for (Map.Entry<String, Entry> entry : entries.entrySet()) {
                Entry value = entry.getValue();
                if (oldPage.equals(value.page()))
                    entry.setValue(new Entry(newPage, value.slot()));
            }
        }
        save();
    }

    public static void set(String modelKey, String actionId, String page, int slot) {
        if (modelKey == null || modelKey.isBlank() || actionId == null || actionId.isBlank())
            return;
        load();
        if (slot < MIN_SLOT) {
            Map<String, Entry> entries = LAYOUTS.get(modelKey);
            if (entries != null)
                entries.remove(normalize(actionId));
        } else if (!isValidSlot(slot)) {
            return;
        } else {
            String action = normalize(actionId);
            String targetPage = page == null || page.isBlank() ? "extra_animation" : page;
            Map<String, Entry> entries = LAYOUTS.computeIfAbsent(modelKey, ignored -> new HashMap<>());
            entries.entrySet().removeIf(entry -> !entry.getKey().equals(action)
                    && entry.getValue().slot() == slot
                    && targetPage.equals(entry.getValue().page()));
            entries.put(action, new Entry(targetPage, slot));
        }
        save();
    }

    public static void clearPage(String modelKey, String page) {
        if (modelKey == null || modelKey.isBlank() || page == null || page.isBlank())
            return;
        load();
        Map<String, Entry> entries = LAYOUTS.get(modelKey);
        if (entries == null)
            return;
        entries.entrySet().removeIf(entry -> page.equals(entry.getValue().page()));
        save();
    }

    public static void clearSlot(String modelKey, String page, int slot) {
        if (modelKey == null || modelKey.isBlank() || page == null || page.isBlank() || !isValidSlot(slot))
            return;
        load();
        Map<String, Entry> entries = LAYOUTS.get(modelKey);
        if (entries == null)
            return;
        entries.entrySet().removeIf(entry -> slot == entry.getValue().slot() && page.equals(entry.getValue().page()));
        save();
    }

    public static void swapSlots(String modelKey, String page, int firstSlot, int secondSlot) {
        if (modelKey == null || modelKey.isBlank() || page == null || page.isBlank() || !isValidSlot(firstSlot) || !isValidSlot(secondSlot) || firstSlot == secondSlot)
            return;
        load();
        Map<String, Entry> entries = LAYOUTS.get(modelKey);
        if (entries == null)
            return;
        String firstAction = null;
        String secondAction = null;
        for (Map.Entry<String, Entry> entry : entries.entrySet()) {
            Entry value = entry.getValue();
            if (!page.equals(value.page()))
                continue;
            if (value.slot() == firstSlot)
                firstAction = entry.getKey();
            else if (value.slot() == secondSlot)
                secondAction = entry.getKey();
        }
        if (firstAction != null)
            entries.put(firstAction, new Entry(page, secondSlot));
        if (secondAction != null)
            entries.put(secondAction, new Entry(page, firstSlot));
        save();
    }

    public static void compactPage(String modelKey, String page) {
        if (modelKey == null || modelKey.isBlank() || page == null || page.isBlank())
            return;
        load();
        Map<String, Entry> entries = LAYOUTS.get(modelKey);
        if (entries == null)
            return;
        int slot = 1;
        for (Map.Entry<String, Entry> entry : entries.entrySet().stream()
                .filter(entry -> page.equals(entry.getValue().page()))
                .sorted(Map.Entry.comparingByValue(java.util.Comparator.comparingInt(Entry::slot)))
                .toList()) {
            if (slot > MAX_SLOT)
                entry.setValue(new Entry(page, -1));
            else
                entry.setValue(new Entry(page, slot++));
        }
        entries.values().removeIf(entry -> !isValidSlot(entry.slot()));
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
                Set<String> pages = PAGES.computeIfAbsent(model, ignored -> new LinkedHashSet<>());
                for (Tag pageTag : modelNbt.getListOrEmpty("pages")) {
                    if (pageTag instanceof CompoundTag pageNbt) {
                        String page = pageNbt.getStringOr("page", "");
                        if (!page.isBlank())
                            pages.add(page);
                    }
                }
                Map<String, Entry> entries = LAYOUTS.computeIfAbsent(model, ignored -> new HashMap<>());
                for (Tag entryTag : modelNbt.getListOrEmpty("entries")) {
                    if (!(entryTag instanceof CompoundTag entryNbt))
                        continue;
                    String action = normalize(entryNbt.getStringOr("action", ""));
                    int slot = entryNbt.getIntOr("slot", MIN_SLOT);
                    if (!action.isBlank() && isValidSlot(slot))
                        entries.put(action, new Entry(entryNbt.getStringOr("page", "extra_animation"), slot));
                }
            }
        });
    }

    private static void save() {
        IOUtils.saveCacheFile("ysm_action_wheel", nbt -> {
            ListTag models = new ListTag();
            Set<String> modelKeys = new LinkedHashSet<>();
            modelKeys.addAll(LAYOUTS.keySet());
            modelKeys.addAll(PAGES.keySet());
            for (String modelKey : modelKeys) {
                CompoundTag modelNbt = new CompoundTag();
                modelNbt.putString("model", modelKey);
                ListTag pages = new ListTag();
                for (String page : PAGES.getOrDefault(modelKey, Set.of())) {
                    CompoundTag pageNbt = new CompoundTag();
                    pageNbt.putString("page", page);
                    pages.add(pageNbt);
                }
                modelNbt.put("pages", pages);
                ListTag entries = new ListTag();
                for (Map.Entry<String, Entry> actionEntry : LAYOUTS.getOrDefault(modelKey, Map.of()).entrySet()) {
                    CompoundTag entryNbt = new CompoundTag();
                    Entry entry = actionEntry.getValue();
                    if (!isValidSlot(entry.slot()))
                        continue;
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

    private static boolean isValidSlot(int slot) {
        return slot >= MIN_SLOT && slot <= MAX_SLOT;
    }

    public record Entry(String page, int slot) {
    }
}
