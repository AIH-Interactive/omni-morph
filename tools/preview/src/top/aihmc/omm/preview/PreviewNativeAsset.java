package top.aihmc.omm.preview;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.imageio.ImageIO;
import org.lwjgl.system.MemoryUtil;
import top.aihmc.omm.NativeApi;
import top.aihmc.omnimorph.model.ysm.YsmGeometry;

/** Consumes native parsed geometry/motion/resource records; Java only binds data and compiles host expressions. */
final class PreviewNativeAsset {
    private PreviewNativeAsset() {}

    private static native byte[] geometry(long runtime, long asset, int geometry) throws IOException;

    private static JsonArray section(long asset, int id) {
        return JsonParser.parseString(new String(NativeApi.omm_asset_read_section(asset, id), StandardCharsets.UTF_8))
            .getAsJsonObject()
            .getAsJsonArray("records");
    }

    static PreviewMesh load(PreviewConfig config, Path source) throws IOException {
        NativeApi.load(config.configFile.getParent().resolve(config.execution.nativeLibrary).normalize());
        int[] version = NativeApi.omm_get_version();
        long runtime = NativeApi.omm_runtime_create(
            version[0], version[1], NativeApi.CPU_AUTO, config.execution.parserThreads, 0, 0, 0);
        long asset = 0;
        var inputs = new ArrayList<ByteBuffer>();
        try {
            int threads = NativeApi.omm_runtime_get_caps(runtime)[3];
            long started;
            if (Files.isDirectory(source)) {
                List<Path> files;
                try (var walk = Files.walk(source)) {
                    files = walk.filter(Files::isRegularFile).sorted().toList();
                }
                String[] paths = new String[files.size()];
                ByteBuffer[] buffers = new ByteBuffer[files.size()];
                for (int index = 0; index < files.size(); index++) {
                    paths[index] = source.relativize(files.get(index)).toString().replace('\\', '/');
                    byte[] data = Files.readAllBytes(files.get(index));
                    buffers[index] = MemoryUtil.memAlloc(data.length).put(data).flip();
                    inputs.add(buffers[index]);
                }
                started = System.nanoTime();
                asset = NativeApi.omm_ysm_parse_entries(runtime, paths, buffers, source.toString());
            } else {
                byte[] bytes = Files.readAllBytes(source);
                ByteBuffer buffer = MemoryUtil.memAlloc(bytes.length).put(bytes).flip();
                inputs.add(buffer);
                started = System.nanoTime();
                asset = NativeApi.omm_ysm_parse(runtime, buffer, 0, bytes.length, source.toString());
            }
            double parseMs = (System.nanoTime() - started) / 1e6;
            var metadata = section(asset, NativeApi.SECTION_METADATA).get(0).getAsJsonObject();
            var catalog = section(asset, NativeApi.SECTION_RESOURCES);
            var resources = new LinkedHashMap<Integer, JsonObject>();
            for (var record : catalog) {
                var resource = record.getAsJsonObject();
                resources.put(resource.get("id").getAsInt(), resource);
            }
            String mainPath = metadata.get("main_model").isJsonNull()
                ? ""
                : resources.get(metadata.get("main_model").getAsInt()).get("path").getAsString();
            if (mainPath.isEmpty()) {
                var manifest = metadata.getAsJsonObject("manifest");
                if (manifest != null && manifest.has("files")) {
                    var files = manifest.getAsJsonObject("files");
                    if (files.has("player") && files.getAsJsonObject("player").has("model")) {
                        var model = files.getAsJsonObject("player").getAsJsonObject("model");
                        if (model.has("main"))
                            mainPath = model.get("main").getAsString();
                    }
                }
            }
            int geometryId = -1;
            for (var value : metadata.getAsJsonArray("geometries")) {
                var record = value.getAsJsonObject();
                if (record.get("path").getAsString().equals(mainPath))
                    geometryId = record.get("id").getAsInt();
            }
            if (geometryId < 0)
                throw new IOException("Native asset has no parsed main geometry: " + mainPath);
            YsmGeometry parsed = bindGeometry(
                JsonParser.parseString(new String(geometry(runtime, asset, geometryId), StandardCharsets.UTF_8))
                    .getAsJsonObject());
            var animationFiles = section(asset, NativeApi.SECTION_ANIMATIONS);
            var controllerFiles = section(asset, NativeApi.SECTION_CONTROLLERS);
            var motion = new ArrayList<>(PreviewNativeBindings.animations(animationFiles));
            motion.addAll(PreviewNativeBindings.controllers(controllerFiles));
            BufferedImage texture = texture(asset, metadata, resources);
            var localResources = localResources(asset, metadata, resources);
            var mesh = PreviewMesh.bake(parsed, texture, source.toString(), motion, localResources);
            mesh.parserInfo = new PreviewMesh.ParserInfo("native", threads, parseMs, 0);
            config.execution.parserThreads = threads;
            return mesh;
        } finally {
            if (asset != 0)
                NativeApi.omm_asset_release(asset);
            NativeApi.omm_runtime_destroy(runtime);
            for (ByteBuffer buffer : inputs) MemoryUtil.memFree(buffer);
        }
    }

    private static YsmGeometry bindGeometry(JsonObject data) {
        var geometry = new YsmGeometry();
        geometry.textureWidth = data.get("texture_width").getAsInt();
        geometry.textureHeight = data.get("texture_height").getAsInt();
        for (var value : data.getAsJsonArray("bones")) {
            var record = value.getAsJsonObject();
            var bone = new YsmGeometry.Bone(record.get("name").getAsString(), record.get("parent").getAsString(),
                floats(record.getAsJsonArray("pivot")), floats(record.getAsJsonArray("rotation")));
            for (var cubeValue : record.getAsJsonArray("cubes")) {
                var quads = new ArrayList<YsmGeometry.Quad>();
                for (var faceValue : cubeValue.getAsJsonArray()) {
                    var face = faceValue.getAsJsonObject();
                    quads.add(new YsmGeometry.Quad(floats(face.getAsJsonArray("positions")),
                        floats(face.getAsJsonArray("uvs")), floats(face.getAsJsonArray("normal"))));
                }
                bone.cubes.add(new YsmGeometry.Cube(quads));
            }
            geometry.bones.put(bone.name, bone);
        }
        for (var bone : geometry.bones.values()) {
            if (bone.parentName.isBlank())
                geometry.roots.add(bone);
            else
                geometry.bones.get(bone.parentName).children.add(bone);
        }
        return geometry;
    }

    private static float[] floats(JsonArray data) {
        float[] values = new float[data.size()];
        for (int index = 0; index < values.length; index++) values[index] = data.get(index).getAsFloat();
        return values;
    }

    private static byte[] resource(long asset, JsonObject record) throws IOException {
        long length = record.get("bytes").getAsLong();
        if (length > Integer.MAX_VALUE)
            throw new IOException("Resource exceeds Java array capacity: " + record.get("path"));
        byte[] output = new byte[(int) length];
        ByteBuffer buffer = MemoryUtil.memAlloc((int) Math.min(length, 1024 * 1024));
        try {
            int offset = 0;
            while (offset < output.length) {
                int written = NativeApi.omm_asset_read_resource(asset, record.get("id").getAsInt(), offset, buffer, 0,
                    Math.min(buffer.capacity(), output.length - offset));
                if (written <= 0)
                    throw new IOException("Truncated native resource: " + record.get("path"));
                buffer.position(0).limit(written);
                buffer.get(output, offset, written);
                offset += written;
                buffer.clear();
            }
            return output;
        } finally {
            MemoryUtil.memFree(buffer);
        }
    }

    private static BufferedImage texture(long asset, JsonObject metadata, Map<Integer, JsonObject> resources)
        throws IOException {
        JsonObject selected = null;
        for (var value : metadata.getAsJsonArray("textures")) {
            var texture = value.getAsJsonObject();
            if (selected == null
                || texture.get("id").getAsString().equals(metadata.get("default_texture").getAsString()))
                selected = texture;
        }
        if (selected == null)
            return null;
        if (selected.get("resource").isJsonNull())
            throw new IOException("Missing native texture: " + selected.get("path"));
        JsonObject info = resources.get(selected.get("resource").getAsInt());
        byte[] bytes = resource(asset, info);
        if (info.get("media_type").getAsString().equals("image/x-omm-rgba8")) {
            int width = info.get("width").getAsInt(), height = info.get("height").getAsInt();
            if ((long) width * height * 4 != bytes.length)
                throw new IOException("Native RGBA dimensions differ from resource bytes");
            var image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++) {
                    int offset = (y * width + x) * 4;
                    image.setRGB(x, y,
                        (bytes[offset + 3] & 255) << 24 | (bytes[offset] & 255) << 16 | (bytes[offset + 1] & 255) << 8
                            | (bytes[offset + 2] & 255));
                }
            }
            return image;
        }
        var image = ImageIO.read(new ByteArrayInputStream(bytes));
        if (image == null)
            throw new IOException("Unsupported native texture encoding: " + info.get("media_type"));
        return image;
    }

    private static PreviewYsmResources localResources(
        long asset, JsonObject metadata, Map<Integer, JsonObject> resources) throws IOException {
        var schemas = new ArrayList<PreviewYsmResources.Schema>();
        var root = metadata.get("manifest");
        if (!root.isJsonNull())
            schemas.add(new PreviewYsmResources.Schema(
                metadata.get("kind").getAsString().equals("legacy") ? "main.json" : "ysm.json", root.toString()));
        var languages = new LinkedHashMap<String, String>();
        var audio = new LinkedHashMap<String, byte[]>();
        var functions = new LinkedHashMap<String, String>();
        for (String group : List.of("languages", "sounds", "functions")) {
            for (var file : metadata.getAsJsonArray(group)) {
                var ref = file.getAsJsonObject();
                if (ref.get("resource").isJsonNull())
                    continue;
                String path = ref.get("path").getAsString();
                byte[] bytes = resource(asset, resources.get(ref.get("resource").getAsInt()));
                if (group.equals("sounds"))
                    audio.put(PreviewYsmResources.soundName(path), bytes);
                else if (group.equals("languages"))
                    languages.put(path, new String(bytes, StandardCharsets.UTF_8));
                else
                    functions.put(path, new String(bytes, StandardCharsets.UTF_8));
            }
        }
        for (var record : resources.values()) {
            String path = record.get("path").getAsString();
            if (List.of("ysm.actions.json", "action_wheel.json", "ysm.controls.json", "ysm_controls.json",
                        "controls.json")
                    .contains(path.toLowerCase(java.util.Locale.ROOT)))
                schemas.add(
                    new PreviewYsmResources.Schema(path, new String(resource(asset, record), StandardCharsets.UTF_8)));
        }
        return new PreviewYsmResources(schemas, languages, audio, functions);
    }
}
