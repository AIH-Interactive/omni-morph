package top.aihmc.omm;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.joml.Matrix4f;

/** Multiple original reference-MOD models feeding one visible window. */
public final class ReferenceListWorker implements CpuPreviewWindow.FrameSource, AutoCloseable {
    private final List<ReferenceCpuScene> scenes = new ArrayList<>();
    private final List<Object> nativeModels = new ArrayList<>();
    private final List<byte[]> textures = new ArrayList<>();
    private final List<ReferenceGpuWindow.ModelInstance> gpuInstances = new ArrayList<>();

    public static void main(String[] args) throws Exception {
        String backend = args[0];
        Path output = Path.of(System.getProperty("omm.preview.output"));
        JsonObject configuration = RuntimeConfiguration.json(output.resolve("effective-config.json"));
        boolean nativeSimd = System.getProperty("omm.preview.mode").equals("simd");
        boolean gpu = args[2].equals("gpu");
        try (ReferenceListWorker worker = new ReferenceListWorker()) {
            worker.load(backend, nativeSimd, gpu, configuration, SceneLayout.read(), output);
            if (gpu) {
                ReferenceGpuWindow.run(backend, worker.gpuInstances);
                return;
            }
            List<double[]> vertices = worker.sample(0);
            List<int[]> indices = vertices.stream()
                .map(batch -> ReferenceVertexOutput.quadIndices(batch.length / 9)).toList();
            CpuPreviewWindow.run(backend + " original model list " + (nativeSimd ? "SIMD" : "Java"),
                vertices, indices, worker.textures, worker, backend.equals("oysm"));
        }
    }

    private void load(String backend, boolean nativeSimd, boolean gpu, JsonObject configuration,
                      SceneLayout layout, Path output) throws Exception {
        JsonArray models = configuration.getAsJsonObject("scene").getAsJsonArray("models");
        JsonArray manifest = new JsonArray();
        boolean nativeLoaded = false;
        for (SceneLayout.Placement placement : layout.instances()) {
            JsonObject entry = models.get(placement.index() % models.size()).getAsJsonObject();
            Path path = Path.of(entry.get("path").getAsString());
            JsonObject inputs = configuration.deepCopy();
            if (entry.has("animation")) {
                if (entry.get("animation").isJsonNull()) {
                    inputs.remove("animation");
                } else {
                    inputs.add("animation", entry.get("animation").deepCopy());
                }
            }
            ReferencePreparedModel prepared = ReferencePreparedModel.load(backend, path, inputs,
                output.resolve(backend).resolve("model-texture-" + placement.index() + ".png"));
            Object model = prepared.model();
            boolean cpuFallback = gpu && backend.equals("spm") && prepared.translucent();
            if (nativeSimd || (gpu && !cpuFallback)) {
                if (!nativeLoaded) {
                    String jarPath = Path.of(model.getClass().getProtectionDomain().getCodeSource().getLocation().toURI()).toString();
                    JarNativeProbe.main(new String[]{backend, jarPath});
                    nativeLoaded = true;
                }
                if (nativeSimd) {
                    model.getClass().getMethod("buildNativeCache").invoke(model);
                    nativeModels.add(model);
                }
            }
            if (gpu) {
                gpuInstances.add(new ReferenceGpuWindow.ModelInstance(model, prepared.texture(), prepared.animation(), placement));
            } else {
                scenes.add(new ReferenceCpuScene(backend, model, nativeSimd, prepared.animation(),
                    new SceneLayout(layout.seed(), List.of(placement))));
            }
            textures.add(Files.readAllBytes(prepared.texture()));
            JsonObject record = new JsonObject();
            record.addProperty("index", placement.index());
            record.addProperty("path", path.toString());
            record.addProperty("format", "ysm");
            record.addProperty("backend", backend);
            record.addProperty("requestedMode", System.getProperty("omm.preview.mode"));
            record.addProperty("actualMode", cpuFallback ? "java" : System.getProperty("omm.preview.mode"));
            if (cpuFallback) {
                record.addProperty("fallbackReason", "original-spm-translucent-gpu-declined");
            }
            record.addProperty("translucent", prepared.translucent());
            record.add("placement", new com.google.gson.Gson().toJsonTree(placement));
            if (inputs.has("animation")) {
                record.add("animation", inputs.get("animation").deepCopy());
            }
            manifest.add(record);
            System.out.println("REFERENCE MODEL LOADED index=" + placement.index() + " backend=" + backend + " path=" + path);
        }
        Files.writeString(output.resolve("scene-models.json"), new GsonBuilder().setPrettyPrinting().create().toJson(manifest));
    }

    @Override
    public void camera(Matrix4f camera) {
        for (ReferenceCpuScene scene : scenes) {
            scene.camera(camera);
        }
    }

    @Override
    public List<float[]> colors() {
        List<float[]> result = new ArrayList<>();
        for (ReferenceCpuScene scene : scenes) {
            result.addAll(scene.colors());
        }
        return result;
    }

    @Override
    public List<int[]> lightingCoordinates() {
        List<int[]> result = new ArrayList<>();
        for (ReferenceCpuScene scene : scenes) {
            result.addAll(scene.lightingCoordinates());
        }
        return result;
    }

    @Override
    public List<double[]> sample(int frame) throws Exception {
        List<double[]> result = new ArrayList<>();
        for (ReferenceCpuScene scene : scenes) {
            result.addAll(scene.sample(frame));
        }
        return result;
    }

    @Override
    public void close() throws Exception {
        Exception firstFailure = null;
        for (Object model : nativeModels.reversed()) {
            try {
                model.getClass().getMethod("freeNativeCache").invoke(model);
            } catch (Exception failure) {
                if (firstFailure == null) {
                    firstFailure = failure;
                } else {
                    firstFailure.addSuppressed(failure);
                }
            }
        }
        nativeModels.clear();
        if (firstFailure != null) {
            throw firstFailure;
        }
    }
}
