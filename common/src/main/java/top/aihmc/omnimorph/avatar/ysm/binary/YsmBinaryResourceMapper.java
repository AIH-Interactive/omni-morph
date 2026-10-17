package top.aihmc.omnimorph.avatar.ysm.binary;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.Gson;
import com.google.gson.stream.JsonWriter;
import top.aihmc.omnimorph.avatar.ysm.YsmAvatarKind;
import top.aihmc.omnimorph.avatar.ysm.YsmManifest;
import top.aihmc.omnimorph.avatar.ysm.YsmMemoryResourceSource;
import top.aihmc.omnimorph.avatar.ysm.YsmPackage;
import top.aihmc.omnimorph.avatar.ysm.YsmResourceIndex;
import top.aihmc.omnimorph.avatar.ysm.YsmTextureOption;
import top.aihmc.omnimorph.avatar.ysm.crypto.YsmDecodeBudget;
import top.aihmc.omnimorph.avatar.ysm.crypto.YsmDecodeException;
import top.aihmc.omnimorph.model.ysm.YsmGeometry;

import java.io.IOException;
import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Owned resource mapping. Geometries remain baked faces and never become generated model JSON. */
public final class YsmBinaryResourceMapper {
    private static final Gson JSON = new Gson();
    private final YsmDecodeBudget budget;
    private final Map<String, byte[]> files = new LinkedHashMap<>();
    private final Map<String, YsmGeometry> geometries = new LinkedHashMap<>();
    private final JsonObject hashes = new JsonObject();
    private final List<String> warnings = new ArrayList<>();
    private int imageIndex;

    private YsmBinaryResourceMapper(YsmDecodeBudget budget) {
        this.budget = budget;
    }

    public static Mapping map(YsmBinaryModel model, String displayName,
                                YsmDecodeBudget budget) throws IOException {
        YsmBinaryResourceMapper mapper = new YsmBinaryResourceMapper(budget);
        try {
            return mapper.map(model, displayName);
        } finally {
            mapper.files.values().forEach(bytes -> Arrays.fill(bytes, (byte) 0));
            mapper.files.clear();
        }
    }

    private Mapping map(YsmBinaryModel raw, String displayName) throws IOException {
        JsonObject manifestJson = YsmBinaryMetadataMapper.map(raw, this::metadataImage, budget);
        String main = geometry(raw.mainEntity.mainModel, "binary/main.geometry");
        String arm = raw.mainEntity.armModel == null ? "" : geometry(raw.mainEntity.armModel, "binary/arm.geometry");
        List<String> animations = animations(raw.mainEntity.animationFiles, "animations/player");
        List<String> controllers = controllers(raw.mainEntity.animationControllerFiles, "controllers/player");
        List<YsmTextureOption> textures = textures(raw.mainEntity.textures, "textures/player");
        JsonObject player = new JsonObject();
        JsonObject models = new JsonObject();
        models.addProperty("main", main);
        models.addProperty("arm", arm);
        player.add("model", models);
        player.add("animation", strings(animations));
        player.add("animation_controllers", strings(controllers));
        player.add("texture", textureDefinitions(textures));
        JsonObject definitions = new JsonObject();
        definitions.add("player", player);
        JsonArray projectiles = entities(raw.projectiles, "projectile");
        JsonArray vehicles = entities(raw.vehicles, "vehicle");
        definitions.add("projectiles", projectiles);
        definitions.add("vehicles", vehicles);
        manifestJson.add("files", definitions);
        List<String> functions = dataFiles(raw.functionFiles, "functions");
        List<String> sounds = dataFiles(raw.soundFiles, "sounds");
        List<String> languages = new ArrayList<>();
        for (var entry : raw.languageFiles.entrySet()) {
            String path = "lang/" + safeName(entry.getKey()) + ".json";
            JsonObject language = new JsonObject();
            entry.getValue().data.forEach(language::addProperty);
            json(path, language);
            hashes.addProperty(path, entry.getValue().hash);
            languages.add(path);
        }
        manifestJson.add("ysm_binary_resource_hashes", hashes);
        json("ysm.json", manifestJson);
        String defaultTexture = raw.properties.defaultTexture;
        if (!textures.isEmpty() && textures.stream().noneMatch(texture -> texture.id().equals(raw.properties.defaultTexture))) {
            defaultTexture = textures.getFirst().id();
        }
        YsmResourceIndex index = new YsmResourceIndex(main, arm, animations, controllers, functions,
                files.keySet().stream().filter(path -> path.startsWith("textures/")).toList(), sounds,
                "", raw.properties.guiBackground, List.of("ysm.json"), languages,
                entityPaths(projectiles), entityPaths(vehicles), warnings);
        YsmManifest manifest = new YsmManifest(YsmAvatarKind.NEW,
                raw.metadata.name.isBlank() ? displayName : raw.metadata.name, raw.metadata.tips,
                raw.metadata.authors.stream().map(author -> author.name).toArray(String[]::new), main, arm,
                animations, controllers, textures, defaultTexture, index);
        YsmMemoryResourceSource resources = new YsmMemoryResourceSource(displayName, files, budget.limits());
        return new Mapping(resources, manifest, Map.copyOf(geometries));
    }

    private String geometry(YsmBinaryModel.RawGeometry raw, String path) throws IOException {
        if (geometries.putIfAbsent(path, YsmBinaryGeometryMapper.map(raw, budget)) != null) {
            throw invalid("mapping/geometry-path");
        }
        hashes.addProperty(path, raw.sha256);
        JsonObject metadata = new JsonObject();
        metadata.addProperty("identifier", raw.identifier);
        metadata.addProperty("model_type", raw.modelType);
        metadata.addProperty("visible_bounds_width", raw.visibleBoundsWidth);
        metadata.addProperty("visible_bounds_height", raw.visibleBoundsHeight);
        metadata.add("visible_bounds_offset", JSON.toJsonTree(raw.visibleBoundsOffset));
        metadata.add("unknown_floats", JSON.toJsonTree(new float[]{raw.unkFloat1, raw.unkFloat2}));
        metadata.add("footer_padding", JSON.toJsonTree(new int[]{raw.footerPad1, raw.footerPad2, raw.footerPad3}));
        JsonArray bones = new JsonArray();
        for (var bone : raw.bones) {
            JsonObject descriptor = new JsonObject();
            descriptor.addProperty("name", bone.name);
            descriptor.add("unknown_padding", JSON.toJsonTree(new int[]{
                    bone.unkPad1, bone.unkPad2, bone.unkPad3, bone.unkPad4, bone.unkPad5}));
            JsonArray cubes = new JsonArray();
            for (var cube : bone.cubes) {
                cubes.add(JSON.toJsonTree(new int[]{cube.unkInt1, cube.unkInt2, cube.unkInt3}));
            }
            descriptor.add("cube_padding", cubes);
            bones.add(descriptor);
        }
        metadata.add("bones", bones);
        json(path + ".metadata.json", metadata);
        return path;
    }

    private List<YsmTextureOption> textures(Map<String, YsmBinaryModel.RawTexture> textures,
                                            String prefix) throws IOException {
        List<YsmTextureOption> options = new ArrayList<>();
        java.util.Set<String> ids = new java.util.HashSet<>();
        int index = 0;
        for (var texture : textures.values()) {
            if (texture.name == null || texture.name.isBlank() || !ids.add(texture.name.toLowerCase(Locale.ROOT))) {
                throw invalid("mapping/texture-id");
            }
            String stem = prefix + "/" + index++;
            add(stem + ".encoded", texture.data);
            hashes.addProperty(stem + ".encoded", texture.hash);
            String path = stem + ".png";
            addOwned(path, YsmTextureDecoder.toPng(texture.data, texture.imageFormat,
                    texture.width, texture.height, budget));
            JsonObject material = new JsonObject();
            material.addProperty("id", texture.name);
            material.addProperty("path", path);
            material.addProperty("encoded_path", stem + ".encoded");
            material.addProperty("encoding", texture.imageFormat);
            material.addProperty("width", texture.width);
            material.addProperty("height", texture.height);
            material.addProperty("unknown_flag", texture.unknownFlag);
            JsonArray subtextures = new JsonArray();
            String normalPath = "";
            String specularPath = "";
            int subIndex = 0;
            for (var sub : texture.subTextures) {
                String subStem = stem + "_sub_" + subIndex++;
                add(subStem + ".encoded", sub.data);
                hashes.addProperty(subStem + ".encoded", sub.hash);
                addOwned(subStem + ".png", YsmTextureDecoder.toPng(sub.data, sub.imageFormat, sub.width, sub.height, budget));
                if (sub.specularType == 1) {
                    normalPath = subStem + ".png";
                } else if (sub.specularType == 2) {
                    specularPath = subStem + ".png";
                }
                JsonObject descriptor = new JsonObject();
                descriptor.addProperty("type", sub.specularType);
                descriptor.addProperty("path", subStem + ".png");
                descriptor.addProperty("encoded_path", subStem + ".encoded");
                descriptor.addProperty("encoding", sub.imageFormat);
                descriptor.addProperty("width", sub.width);
                descriptor.addProperty("height", sub.height);
                descriptor.addProperty("unknown_flag", sub.unknownFlag);
                subtextures.add(descriptor);
            }
            material.add("sub_textures", subtextures);
            json(stem + ".material.json", material);
            options.add(new YsmTextureOption(texture.name, texture.name, path, normalPath, specularPath));
        }
        return options;
    }

    private String metadataImage(YsmBinaryModel.RawImage image) throws IOException {
        String stem = "metadata/images/" + imageIndex++;
        if (image.format == 5 && isAvif(image.data)) {
            // Preserve encoded author images. User scope retains Figura UI without additional image widgets.
            budget.pixels(image.width, image.height, "mapping/encoded-metadata-pixels", 0);
            String path = stem + ".avif";
            add(path, image.data);
            warnings.add("UI_SKIPPED_BY_USER: AVIF metadata author image " + path);
            return path;
        }
        add(stem + ".encoded", image.data);
        addOwned(stem + ".png", YsmTextureDecoder.toPng(image.data, image.format, image.width, image.height, budget));
        return stem + ".png";
    }

    private static boolean isAvif(byte[] bytes) {
        if (bytes == null || bytes.length < 16 || bytes[4] != 'f' || bytes[5] != 't'
                || bytes[6] != 'y' || bytes[7] != 'p') {
            return false;
        }
        for (int offset = 8; offset <= Math.min(bytes.length - 4, 60); offset += 4) {
            if (bytes[offset] == 'a' && bytes[offset + 1] == 'v' && bytes[offset + 2] == 'i'
                    && (bytes[offset + 3] == 'f' || bytes[offset + 3] == 's')) {
                return true;
            }
        }
        return false;
    }

    private List<String> animations(Map<String, YsmBinaryModel.RawAnimationFile> animations,
                                     String prefix) throws IOException {
        List<String> paths = new ArrayList<>();
        for (var entry : animations.entrySet()) {
            int index = 0;
            var names = new java.util.HashSet<String>();
            // Separate clips keep a large binary animation file within the per-resource byte budget.
            for (var clip : entry.getValue().animations.values()) {
                if (clip.name == null || clip.name.isBlank() || !names.add(clip.name)) {
                    throw invalid("mapping/duplicate-or-empty-name");
                }
                JsonObject resource = new JsonObject();
                JsonObject clips = new JsonObject();
                clips.add(clip.name, YsmBinaryAnimationMapper.animation(clip, budget));
                resource.add("animations", clips);
                resource.addProperty("ysm_animation_type", entry.getValue().animType);
                resource.addProperty("ysm_resource_hash", entry.getValue().fileHash);
                String path = prefix + "/" + safeName(entry.getKey()) + "/" + index++ + ".animation.json";
                json(path, resource);
                hashes.addProperty(path, entry.getValue().fileHash);
                paths.add(path);
            }
        }
        return paths;
    }

    private List<String> controllers(List<YsmBinaryModel.RawAnimationControllerFile> controllers,
                                      String prefix) throws IOException {
        List<String> paths = new ArrayList<>();
        for (int index = 0; index < controllers.size(); index++) {
            var file = controllers.get(index);
            String path = prefix + "/" + index + ".controllers.json";
            json(path, YsmBinaryAnimationMapper.controllers(file, budget));
            hashes.addProperty(path, file.hash);
            paths.add(path);
        }
        return paths;
    }

    private static JsonArray textureDefinitions(List<YsmTextureOption> textures) {
        JsonArray textureDefinitions = new JsonArray();
        for (YsmTextureOption texture : textures) {
            JsonObject definition = new JsonObject();
            definition.addProperty("id", texture.id());
            definition.addProperty("name", texture.displayName());
            definition.addProperty("uv", texture.path());
            if (!texture.normalPath().isBlank()) {
                definition.addProperty("normal", texture.normalPath());
            }
            if (!texture.specularPath().isBlank()) {
                definition.addProperty("specular", texture.specularPath());
            }
            textureDefinitions.add(definition);
        }
        return textureDefinitions;
    }

    private JsonArray entities(Map<String, YsmBinaryModel.RawSubEntity> entities, String kind) throws IOException {
        JsonArray definitions = new JsonArray();
        int index = 0;
        for (var entity : entities.values()) {
            String stem = "sub_entities/" + kind + "/" + index++;
            JsonObject definition = new JsonObject();
            definition.addProperty("id", entity.identifier);
            definition.addProperty("model", geometry(entity.model, stem + "/model.geometry"));
            definition.add("match", strings(entity.matchIds == null ? List.of(entity.identifier) : Arrays.asList(entity.matchIds)));
            List<YsmTextureOption> textures = textures(entity.textures, "textures/" + stem);
            definition.add("texture", textureDefinitions(textures));
            definition.add("animation", strings(animations(entity.animationFiles, "animations/" + stem)));
            definition.add("animation_controllers", strings(controllers(entity.animationControllerFiles, "controllers/" + stem)));
            definitions.add(definition);
        }
        return definitions;
    }

    private List<String> dataFiles(Map<String, YsmBinaryModel.RawDataFile> input, String prefix) throws IOException {
        List<String> paths = new ArrayList<>();
        for (var entry : input.entrySet()) {
            String path = prefix + "/" + safeName(entry.getKey());
            add(path, entry.getValue().data);
            hashes.addProperty(path, entry.getValue().hash);
            paths.add(path);
        }
        return paths;
    }

    private void json(String path, JsonObject value) throws IOException {
        LimitedJsonOutput output = new LimitedJsonOutput(budget);
        try {
            try (JsonWriter writer = new JsonWriter(new OutputStreamWriter(output, StandardCharsets.UTF_8))) {
                JSON.toJson(value, writer);
            }
            addOwned(path, output.bytes.toByteArray());
        } catch (com.google.gson.JsonIOException failure) {
            // Gson wraps checked writer failures; keep our bounded diagnostics and cancellation.
            if (failure.getCause() instanceof IOException io) {
                throw io;
            }
            throw failure;
        } finally {
            output.bytes.clear();
        }
    }

    private void add(String path, byte[] bytes) throws IOException {
        if (bytes == null) {
            throw invalid("mapping/missing-resource");
        }
        budget.length(bytes.length, budget.limits().singleResourceBytes(), "mapping/resource", 0);
        addOwned(path, bytes.clone());
    }

    private void addOwned(String path, byte[] bytes) throws IOException {
        try {
            budget.beginResource("mapping/resource-count", 0);
            budget.resource(bytes.length, "mapping/resource-bytes", 0);
            if (files.putIfAbsent(path, bytes) != null) {
                throw invalid("mapping/duplicate-resource");
            }
        } catch (IOException | RuntimeException e) {
            Arrays.fill(bytes, (byte) 0);
            throw e;
        }
    }

    private static String safeName(String value) throws IOException {
        String normalized = YsmPackage.normalize(value);
        if (normalized.isBlank()) {
            throw invalid("mapping/resource-name");
        }
        return normalized;
    }

    private static JsonArray strings(List<String> values) {
        JsonArray array = new JsonArray();
        values.forEach(array::add);
        return array;
    }

    private static List<String> entityPaths(JsonArray definitions) {
        List<String> paths = new ArrayList<>();
        definitions.forEach(definition -> paths.add(definition.getAsJsonObject().get("model").getAsString()));
        return paths;
    }

    private static YsmDecodeException invalid(String stage) {
        return new YsmDecodeException(YsmDecodeException.Code.INVALID_DATA, stage, 0);
    }

    private static final class LimitedJsonOutput extends OutputStream {
        private final YsmDecodeBudget budget;
        private final ClearingBytes bytes = new ClearingBytes();

        private LimitedJsonOutput(YsmDecodeBudget budget) {
            this.budget = budget;
        }

        @Override
        public void write(int value) throws IOException {
            budget.length((long) bytes.size() + 1, budget.remainingResourceBytes(), "mapping/json-size", 0);
            bytes.write(value);
        }

        @Override
        public void write(byte[] values, int offset, int length) throws IOException {
            budget.length((long) bytes.size() + length, budget.remainingResourceBytes(), "mapping/json-size", 0);
            bytes.write(values, offset, length);
        }
    }

    private static final class ClearingBytes extends ByteArrayOutputStream {
        private void clear() {
            Arrays.fill(buf, (byte) 0);
            reset();
        }
    }

    public record Mapping(YsmMemoryResourceSource resources, YsmManifest manifest,
                           Map<String, YsmGeometry> geometries) implements AutoCloseable {
        @Override
        public void close() {
            resources.close();
        }
    }
}
