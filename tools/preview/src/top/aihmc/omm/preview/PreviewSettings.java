package top.aihmc.omm.preview;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.LinkedHashMap;
import java.util.Map;
import top.aihmc.omnimorph.avatar.control.AvatarControlDefinition;
import top.aihmc.omnimorph.avatar.control.AvatarControlType;

/** Model-local JSON adapter for OMP's persistent control selection and restore/save order. */
public final class PreviewSettings {
    private final Path file;
    private final String model;
    private final String mode;
    private final JsonObject controls;
    private final boolean ysm;
    private int saves;

    private PreviewSettings(Path file, String model, String mode, JsonObject controls, boolean ysm) {
        this.file = file;
        this.model = model;
        this.mode = mode;
        this.controls = controls;
        this.ysm = ysm;
    }

    static PreviewSettings open(PreviewConfig config, boolean ysm) throws IOException {
        if (!config.settings.enabled || config.benchmark.enabled) {
            return new PreviewSettings(
                null, "", config.benchmark.enabled ? "benchmark-disabled" : "disabled", new JsonObject(), ysm);
        }
        Path directory;
        if (config.settings.directory.isBlank()) {
            try {
                Path location =
                    Path.of(PreviewSettings.class.getProtectionDomain().getCodeSource().getLocation().toURI());
                directory = location.getParent().resolve("model-settings");
            } catch (java.net.URISyntaxException failure) {
                throw new IOException("Unable to locate preview settings directory", failure);
            }
        } else {
            directory = config.configFile.getParent().resolve(config.settings.directory).normalize();
        }
        String identity = config.settings.modelKey.isBlank()
            ? "source:" + config.configFile.getParent().resolve(config.scene.models.get(0)).toRealPath()
            : "key:" + config.settings.modelKey;
        String model = hash((ysm ? "ysm:" : "figura:") + identity);
        Path file = directory.resolve(model + ".json");
        JsonObject values = new JsonObject();
        if (Files.exists(file)) {
            try {
                var stored = JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8)).getAsJsonObject();
                if (stored.get("schemaVersion").getAsInt() != 1 || !model.equals(stored.get("model").getAsString())) {
                    throw new IOException("Settings version/model mismatch: " + file);
                }
                values = stored.getAsJsonObject("controls");
                if (values == null)
                    throw new IOException("Missing settings controls: " + file);
            } catch (RuntimeException invalid) {
                throw new IOException("Invalid model settings file: " + file, invalid);
            }
        }
        return new PreviewSettings(file, model, "enabled", values, ysm);
    }

    private static String hash(String value) {
        return PreviewTextures.sha256(value.getBytes(StandardCharsets.UTF_8));
    }

    private static boolean persistent(AvatarControlDefinition control) {
        return control.persistent() && control.type() != AvatarControlType.LABEL
            && control.type() != AvatarControlType.SEPARATOR && control.type() != AvatarControlType.BUTTON;
    }

    public boolean applySavedValue(AvatarControlDefinition control) {
        if (file == null || !persistent(control))
            return false;
        var element = controls.get(hash(control.id()));
        if (element == null)
            return false;
        var stored = element.getAsJsonObject();
        if (!control.type().name().equals(stored.get("type").getAsString()))
            return false;
        var value = stored.getAsJsonPrimitive("value");
        control.restoreValue(value.isBoolean() ? value.getAsBoolean()
                : value.isNumber()             ? value.getAsDouble()
                                               : value.getAsString());
        return true;
    }

    public void save(AvatarControlDefinition control) {
        if (file == null || !persistent(control))
            return;
        Object value = control.getValue();
        if (!(value instanceof Boolean) && !(value instanceof Number)) {
            value = value == null ? "" : value.toString();
        }
        if (ysm && control.type() == AvatarControlType.ENUM) {
            int index = control.options().indexOf(String.valueOf(value));
            if (index < 0) {
                try {
                    index = Math.round(Float.parseFloat(String.valueOf(value)));
                } catch (NumberFormatException notAnOption) {
                    return;
                }
            }
            if (index < 0 || index >= control.options().size())
                return;
            value = index;
        }
        var entry = new JsonObject();
        entry.addProperty("type", control.type().name());
        entry.add("value", PreviewConfig.JSON.toJsonTree(value));
        if (entry.equals(controls.get(hash(control.id()))))
            return;
        controls.add(hash(control.id()), entry);
        var document = new JsonObject();
        document.addProperty("schemaVersion", 1);
        document.addProperty("model", model);
        document.add("controls", controls);
        try {
            Files.createDirectories(file.getParent());
            Path temporary = Files.createTempFile(file.getParent(), ".controls-", ".tmp");
            try {
                Files.writeString(temporary, PreviewConfig.JSON.toJson(document), StandardCharsets.UTF_8);
                Files.move(temporary, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
                saves++;
            } finally {
                Files.deleteIfExists(temporary);
            }
        } catch (IOException failure) {
            throw new UncheckedIOException("Unable to save model settings: " + file, failure);
        }
    }

    Map<String, Object> snapshot() {
        var result = new LinkedHashMap<String, Object>();
        result.put("mode", mode);
        result.put("file", file == null ? "" : file.toString());
        result.put("model", model);
        result.put("storedControls", controls.size());
        result.put("writes", saves);
        return result;
    }
}
