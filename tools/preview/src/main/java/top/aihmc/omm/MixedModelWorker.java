package top.aihmc.omm;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Renders configured YSM and Figura instances in one original OmniMorph backend. */
public final class MixedModelWorker implements AutoCloseable {
    private final List<OriginalScene> scenes = new ArrayList<>();
    private final List<AutoCloseable> resources = new ArrayList<>();

    public static void main(String[] args) throws Exception {
        Path output = Path.of(System.getProperty("omm.preview.output"));
        JsonObject configuration = RuntimeConfiguration.json(output.resolve("effective-config.json"));
        FabricRuntimeInputs.configure(output.resolve("figura-config"), Path.of(System.getProperty("omm.preview.modJar")));
        Class.forName("net.minecraft.SharedConstants").getMethod("tryDetectVersion").invoke(null);
        Class.forName("net.minecraft.server.Bootstrap").getMethod("bootStrap").invoke(null);
        boolean gpu = args[2].equals("gpu");
        if (!gpu) {
            com.mojang.blaze3d.systems.RenderSystem.initRenderThread();
        }
        try (MixedModelWorker worker = new MixedModelWorker()) {
            worker.load(configuration, SceneLayout.read(), output);
            Object backend = OmniMorphBatches.backend(!gpu && System.getProperty("omm.preview.mode").equals("simd"));
            var initial = worker.sample(0);
            var vertices = OmniMorphBatches.transform(initial, backend);
            if (gpu) {
                OmniMorphGpuWindow.run(initial, vertices, worker.textureBytes(), worker::sample);
            } else {
                CpuPreviewWindow.run("OmniMorph mixed models " + System.getProperty("omm.preview.mode"), vertices,
                    worker.triangleIndices(), worker.textureBytes(),
                    new OmniMorphCpuFrames(worker::sample, backend));
            }
        }
    }

    private void load(JsonObject configuration, SceneLayout layout, Path output) throws Exception {
        JsonArray models = configuration.getAsJsonObject("scene").getAsJsonArray("models");
        JsonArray manifest = new JsonArray();
        for (SceneLayout.Placement placement : layout.instances()) {
            JsonObject entry = models.get(placement.index() % models.size()).getAsJsonObject();
            Path path = Path.of(entry.get("path").getAsString());
            String format = entry.get("format").getAsString();
            JsonObject inputs = configuration.deepCopy();
            if (entry.has("animation")) {
                if (entry.get("animation").isJsonNull()) {
                    inputs.remove("animation");
                } else {
                    inputs.add("animation", entry.get("animation").deepCopy());
                }
            }
            SceneLayout instanceLayout = new SceneLayout(layout.seed(), List.of(placement));
            OriginalScene scene;
            if (format.equals("figura")) {
                scene = new FiguraScene(FiguraWorker.compileModel(path), path, instanceLayout, inputs);
            } else {
                OmniMorphModel model = OmniMorphModel.load(path);
                resources.add(model);
                model.report();
                scene = new OmniMorphScene(model, instanceLayout, inputs);
            }
            resources.add(scene);
            scenes.add(scene);
            JsonObject record = new JsonObject();
            record.addProperty("index", placement.index());
            record.addProperty("path", path.toString());
            record.addProperty("format", format);
            record.addProperty("backend", "omp");
            record.add("placement", new com.google.gson.Gson().toJsonTree(placement));
            if (inputs.has("animation")) {
                record.add("animation", inputs.get("animation").deepCopy());
            }
            manifest.add(record);
            System.out.println("MIXED MODEL LOADED index=" + placement.index() + " format=" + format + " path=" + path);
        }
        Files.writeString(output.resolve("scene-models.json"), new GsonBuilder().setPrettyPrinting().create().toJson(manifest));
        System.out.println("MIXED OMP SCENE instances=" + scenes.size());
    }

    private List<Object> sample(int frame) throws Exception {
        List<Object> result = new ArrayList<>();
        for (OriginalScene scene : scenes) {
            result.addAll(scene.sample(frame));
        }
        return result;
    }

    private List<int[]> triangleIndices() throws Exception {
        List<int[]> result = new ArrayList<>();
        for (OriginalScene scene : scenes) {
            result.addAll(scene.triangleIndices());
        }
        return result;
    }

    private List<byte[]> textureBytes() throws Exception {
        List<byte[]> result = new ArrayList<>();
        for (OriginalScene scene : scenes) {
            result.addAll(scene.textureBytes());
        }
        return result;
    }

    @Override
    public void close() throws Exception {
        Exception firstFailure = null;
        for (AutoCloseable resource : resources.reversed()) {
            try {
                resource.close();
            } catch (Exception failure) {
                if (firstFailure == null) {
                    firstFailure = failure;
                } else {
                    firstFailure.addSuppressed(failure);
                }
            }
        }
        resources.clear();
        scenes.clear();
        if (firstFailure != null) {
            throw firstFailure;
        }
    }
}
