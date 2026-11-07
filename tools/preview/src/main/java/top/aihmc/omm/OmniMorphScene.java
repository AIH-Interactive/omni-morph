package top.aihmc.omm;

import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.List;

/** One original YSM runtime and mesh owner per placed instance. */
final class OmniMorphScene implements OriginalScene {
    private record Instance(Object runtime, OmniMorphBatches batches, YsmAnimationInput animation, float start) {
    }

    private final List<Instance> instances = new ArrayList<>();
    private final float step;
    private final OmniMorphModel model;
    private final JsonObject partVisibility;

    OmniMorphScene(OmniMorphModel model, SceneLayout layout, JsonObject configuration) throws Exception {
        this.model = model;
        partVisibility = configuration.has("partVisibility")
            ? configuration.getAsJsonObject("partVisibility").deepCopy() : new JsonObject();
        JsonObject animationConfig = configuration.has("animation") ? configuration.getAsJsonObject("animation") : null;
        step = animationConfig != null && animationConfig.has("stepSeconds")
            ? animationConfig.get("stepSeconds").getAsFloat() : 0;
        if (!Float.isFinite(step) || step < 0) {
            throw new IllegalArgumentException("animation.stepSeconds must be finite and nonnegative");
        }
        try {
            for (SceneLayout.Placement placement : layout.instances()) {
                Object runtime = model.createRuntime();
                instances.add(new Instance(runtime, null, null, 0));
                YsmAnimationInput animation = animationConfig == null ? null : new YsmAnimationInput(runtime, animationConfig);
                float start = animationConfig == null ? 0 : animationConfig.get("time").getAsFloat()
                    + placement.animationOffsetSeconds();
                if (animation != null) {
                    animation.sample(start);
                }
                applyVisibility(runtime);
                OmniMorphBatches batches = new OmniMorphBatches(model.geometry(), runtime);
                instances.set(instances.size() - 1, new Instance(runtime, batches, animation, start));
                batches.placement(placement);
            }
        } catch (Exception failure) {
            try {
                close();
            } catch (Exception cleanupFailure) {
                failure.addSuppressed(cleanupFailure);
            }
            throw failure;
        }
        System.out.println("OMP ORIGINAL SCENE runtimes=" + instances.size());
    }

    public List<Object> sample(int frame) throws Exception {
        var result = new ArrayList<Object>();
        for (Instance instance : instances) {
            if (instance.animation != null) {
                instance.animation.sample(instance.start + frame * step);
            }
            applyVisibility(instance.runtime);
            instance.batches.refresh();
            result.addAll(instance.batches.drawBatches());
        }
        return result;
    }

    private void applyVisibility(Object runtime) throws Exception {
        for (var entry : partVisibility.entrySet()) {
            Object part = runtime.getClass().getMethod("getPart", String.class).invoke(runtime, entry.getKey());
            if (part == null) {
                throw new IllegalArgumentException("Unknown YSM part: " + entry.getKey());
            }
            part.getClass().getMethod("setVisible", boolean.class).invoke(part, entry.getValue().getAsBoolean());
        }
    }

    public List<int[]> triangleIndices() throws Exception {
        var result = new ArrayList<int[]>();
        for (Instance instance : instances) {
            result.addAll(instance.batches.triangleIndices());
        }
        return result;
    }

    @Override
    public List<byte[]> textureBytes() throws Exception {
        int count = 0;
        for (Instance instance : instances) {
            count += instance.batches.drawBatches().size();
        }
        return java.util.Collections.nCopies(count, model.texture());
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
                PreviewAvatarResources.closeYsmRuntime(instance.runtime);
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
