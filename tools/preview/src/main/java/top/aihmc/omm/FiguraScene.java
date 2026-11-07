package top.aihmc.omm;

import com.google.gson.JsonObject;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Independent original Figura avatars sharing only compiled input data. */
final class FiguraScene implements OriginalScene {
    private record Instance(Object runtime, FiguraBatches batches, FiguraAnimationInput animation, float start) {
    }

    private final List<Instance> instances = new ArrayList<>();
    private final float step;

    FiguraScene(Object data, Path model, SceneLayout layout, JsonObject configuration) throws Exception {
        JsonObject animationConfig = configuration.has("animation") ? configuration.getAsJsonObject("animation") : null;
        step = animationConfig != null && animationConfig.has("stepSeconds")
            ? animationConfig.get("stepSeconds").getAsFloat() : 0;
        if (!Float.isFinite(step) || step < 0) {
            throw new IllegalArgumentException("animation.stepSeconds must be finite and nonnegative");
        }
        try {
            for (SceneLayout.Placement placement : layout.instances()) {
                Object copy = data.getClass().getMethod("copy").invoke(data);
                Object runtime = FiguraWorker.createRuntime(copy, model);
                instances.add(new Instance(runtime, null, null, 0));
                FiguraBatches batches = new FiguraBatches(runtime.getClass().getField("renderer").get(runtime));
                instances.set(instances.size() - 1, new Instance(runtime, batches, null, 0));
                FiguraAnimationInput animation = animationConfig == null ? null : new FiguraAnimationInput(runtime, animationConfig);
                float start = animationConfig == null ? 0 : animationConfig.get("time").getAsFloat()
                    + placement.animationOffsetSeconds();
                batches.placement(placement);
                instances.set(instances.size() - 1, new Instance(runtime, batches, animation, start));
            }
            sample(0);
        } catch (Exception failure) {
            try {
                close();
            } catch (Exception cleanupFailure) {
                failure.addSuppressed(cleanupFailure);
            }
            throw failure;
        }
        System.out.println("FIGURA ORIGINAL SCENE runtimes=" + instances.size());
    }

    public List<Object> sample(int frame) throws Exception {
        var result = new ArrayList<Object>();
        for (Instance instance : instances) {
            if (instance.animation != null) {
                instance.animation.sample(instance.start + frame * step);
                instance.batches.refresh();
            }
            result.addAll(instance.batches.drawBatches());
        }
        return result;
    }

    public List<int[]> triangleIndices() throws Exception {
        var result = new ArrayList<int[]>();
        for (Instance instance : instances) {
            result.addAll(instance.batches.triangleIndices());
        }
        return result;
    }

    public List<byte[]> textureBytes() throws Exception {
        var result = new ArrayList<byte[]>();
        for (Instance instance : instances) {
            result.addAll(instance.batches.textureBytes());
        }
        return result;
    }

    @Override
    public void close() throws Exception {
        Exception firstFailure = null;
        for (Instance instance : instances.reversed()) {
            try {
                if (instance.batches != null) {
                    instance.batches.close();
                }
            } catch (Exception failure) {
                if (firstFailure == null) {
                    firstFailure = failure;
                } else {
                    firstFailure.addSuppressed(failure);
                }
            }
            try {
                new PreviewAvatarResources(instance.runtime).close();
            } catch (Exception failure) {
                if (firstFailure == null) {
                    firstFailure = failure;
                } else {
                    firstFailure.addSuppressed(failure);
                }
            }
        }
        instances.clear();
        if (firstFailure != null) {
            throw firstFailure;
        }
    }
}
