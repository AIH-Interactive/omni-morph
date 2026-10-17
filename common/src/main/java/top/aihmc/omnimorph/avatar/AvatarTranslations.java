package top.aihmc.omnimorph.avatar;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/** Model-local dictionaries; selected-locale missing keys retain their original text. */
public final class AvatarTranslations implements AutoCloseable {
    private volatile Map<String, Map<String, String>> dictionaries = Map.of();
    private int invalidResources;

    public void load(Iterable<Tag> entries) {
        Map<String, Map<String, String>> loaded = new LinkedHashMap<>();
        int invalid = 0;
        for (Tag value : entries) {
            if (value instanceof CompoundTag entry && !add(loaded, entry.getStringOr("path", ""),
                    entry.getByteArray("data").orElse(new byte[0]))) {
                invalid++;
            }
        }
        dictionaries = Map.copyOf(loaded);
        invalidResources = invalid;
    }

    /** Loads format-independent UTF-8 JSON dictionaries named by locale. */
    public void load(Map<String, byte[]> resources) {
        Map<String, Map<String, String>> loaded = new LinkedHashMap<>();
        int invalid = 0;
        for (var entry : resources.entrySet()) {
            if (!add(loaded, entry.getKey(), entry.getValue())) {
                invalid++;
            }
        }
        dictionaries = Map.copyOf(loaded);
        invalidResources = invalid;
    }

    private static boolean add(Map<String, Map<String, String>> loaded, String path, byte[] data) {
        String file = path.replace('\\', '/');
        file = file.substring(file.lastIndexOf('/') + 1);
        if (!file.toLowerCase(Locale.ROOT).endsWith(".json")) {
            return false;
        }
        String locale = normalize(file.substring(0, file.length() - 5));
        if (locale.isBlank()) {
            return false;
        }
        try {
            var root = JsonParser.parseString(new String(data, StandardCharsets.UTF_8));
            if (!root.isJsonObject()) {
                return false;
            }
            JsonObject object = root.getAsJsonObject();
            Map<String, String> words = new LinkedHashMap<>();
            for (var entry : object.entrySet()) {
                if (entry.getValue().isJsonPrimitive()) {
                    words.put(entry.getKey(), entry.getValue().getAsString());
                }
            }
            loaded.put(locale, Map.copyOf(words));
            return true;
        } catch (RuntimeException invalid) {
            // Optional malformed dictionaries cannot expose private JSON or hide valid locales.
            return false;
        }
    }

    public String get(String key, String fallback) {
        return get(currentLocale(), key, fallback);
    }

    public String get(String locale, String key, String fallback) {
        Map<String, Map<String, String>> snapshot = dictionaries;
        Map<String, String> words = snapshot.get(normalize(locale));
        if (words == null) {
            words = snapshot.get("en_us");
        }
        String original = fallback == null ? "" : fallback;
        return words == null ? original : words.getOrDefault(key, original);
    }

    public String authors(String original) {
        if (original == null || original.isBlank()) {
            return original;
        }
        String[] authors = original.split("\n", -1);
        for (int index = 0; index < authors.length; index++) {
            authors[index] = get("metadata.authors." + index + ".name", authors[index]);
        }
        return String.join("\n", authors);
    }

    public int invalidResourceCount() {
        return invalidResources;
    }

    public static String currentLocale() {
        Minecraft client = Minecraft.getInstance();
        if (client == null || client.getLanguageManager() == null) {
            return "en_us";
        }
        return normalize(client.getLanguageManager().getSelected());
    }

    private static String normalize(String locale) {
        return locale == null ? "" : locale.toLowerCase(Locale.ROOT).replace('-', '_');
    }

    @Override
    public void close() {
        dictionaries = Map.of();
        invalidResources = 0;
    }
}
