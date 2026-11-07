package top.aihmc.omm.preview;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import top.aihmc.omnimorph.avatar.ysm.YsmManifest;
import top.aihmc.omnimorph.avatar.ysm.YsmResourceSource;
import top.aihmc.omnimorph.avatar.ysm.binary.YsmBinaryModel;

/** Model-local operation schemas/languages, retained before package/decrypted resources close. */
record PreviewYsmResources(
    List<Schema> schemas, Map<String, String> languages, Map<String, byte[]> audio, Map<String, String> functions) {
    record Schema(String path, String json) {}
    static final PreviewYsmResources EMPTY = new PreviewYsmResources(List.of(), Map.of(), Map.of());

    PreviewYsmResources(List<Schema> schemas, Map<String, String> languages, Map<String, byte[]> audio) {
        this(schemas, languages, audio, Map.of());
    }

    PreviewYsmResources {
        schemas = List.copyOf(schemas);
        languages = Map.copyOf(languages);
        audio = Map.copyOf(audio);
        functions = java.util.Collections.unmodifiableMap(new LinkedHashMap<>(functions));
    }

    static PreviewYsmResources read(YsmResourceSource resources, YsmManifest manifest) throws IOException {
        var paths = new LinkedHashSet<String>(List.of("ysm.json", "ysm.actions.json", "action_wheel.json",
            "ysm.controls.json", "ysm_controls.json", "controls.json"));
        paths.addAll(manifest.resourceIndex().metadataPaths());
        var schemas = new ArrayList<Schema>();
        for (String path : paths) {
            if (resources.exists(path)) {
                schemas.add(new Schema(path, resources.readString(path)));
            }
        }
        var languages = new LinkedHashMap<String, String>();
        for (String path : manifest.resourceIndex().languagePaths()) {
            languages.put(path, resources.readString(path));
        }
        var audio = new LinkedHashMap<String, byte[]>();
        for (String path : manifest.resourceIndex().soundPaths()) {
            audio.put(soundName(path), resources.readBytes(path));
        }
        var functions = new LinkedHashMap<String, String>();
        for (String path : manifest.resourceIndex().functionPaths()) {
            functions.put(path, resources.readString(path));
        }
        return new PreviewYsmResources(schemas, languages, audio, functions);
    }

    static PreviewYsmResources binary(YsmBinaryModel raw) {
        var root = new JsonObject();
        root.add("properties", properties(raw.properties));
        var languages = new LinkedHashMap<String, String>();
        raw.languageFiles.forEach(
            (name,
                file) -> languages.put(name.endsWith(".json") ? name : name + ".json", strings(file.data).toString()));
        var audio = new LinkedHashMap<String, byte[]>();
        raw.soundFiles.forEach((name, file) -> audio.put(soundName(name), file.data.clone()));
        var functions = new LinkedHashMap<String, String>();
        raw.functionFiles.forEach(
            (name, file) -> functions.put(name, new String(file.data, java.nio.charset.StandardCharsets.UTF_8)));
        return new PreviewYsmResources(
            List.of(new Schema("binary-properties", root.toString())), languages, audio, functions);
    }

    // Original YsmAudioResources uses the file stem, independently of Figura's dotted directory IDs.
    static String soundName(String path) {
        String normalized = path.replace('\\', '/');
        String name = normalized.substring(normalized.lastIndexOf('/') + 1);
        int extension = name.lastIndexOf('.');
        return extension >= 0 ? name.substring(0, extension) : name;
    }

    // Extracted pure property mapping from OMP YsmBinaryMetadataMapper; no metadata image sink is needed.
    private static JsonObject properties(YsmBinaryModel.RawProperties raw) {
        JsonObject properties = new JsonObject();
        properties.addProperty("width_scale", raw.widthScale);
        properties.addProperty("height_scale", raw.heightScale);
        properties.addProperty("default_texture", raw.defaultTexture);
        properties.addProperty("preview_animation", raw.previewAnimation);
        properties.addProperty("free", raw.isFree);
        properties.addProperty("render_layers_first", raw.renderLayersFirst);
        properties.addProperty("all_cutout", raw.allCutout);
        properties.addProperty("disable_preview_rotation", raw.disablePreviewRotation);
        properties.addProperty("gui_no_lighting", raw.guiNoLighting);
        properties.addProperty("merge_multiline_expr", raw.mergeMultilineExpr);
        properties.addProperty("gui_foreground", raw.guiForeground);
        properties.addProperty("gui_background", raw.guiBackground);
        properties.add("extra_animation", strings(raw.extraAnimations));
        JsonArray classifications = new JsonArray();
        for (var classification : raw.extraAnimationClassifies) {
            JsonObject object = new JsonObject();
            object.addProperty("id", classification.id);
            object.add("extra_animation", strings(classification.extras));
            classifications.add(object);
        }
        properties.add("extra_animation_classify", classifications);
        JsonArray buttons = new JsonArray();
        for (var button : raw.extraAnimationButtons) {
            JsonObject object = new JsonObject();
            object.addProperty("id", button.id);
            object.addProperty("name", button.name);
            object.addProperty("description", button.description);
            JsonArray forms = new JsonArray();
            for (var form : button.forms) {
                JsonObject config = new JsonObject();
                config.addProperty("type", form.type);
                config.addProperty("title", form.title);
                config.addProperty("description", form.description);
                // The reference field's name is misleading: this is the Molang value expression.
                config.addProperty("value", form.defaultValue);
                config.addProperty("step", form.step);
                config.addProperty("min", form.min);
                config.addProperty("max", form.max);
                config.add("labels", strings(form.labels));
                forms.add(config);
            }
            object.add("config_forms", forms);
            buttons.add(object);
        }
        properties.add("extra_animation_buttons", buttons);
        return properties;
    }

    private static JsonObject strings(Map<String, String> values) {
        JsonObject object = new JsonObject();
        values.forEach(object::addProperty);
        return object;
    }
}
