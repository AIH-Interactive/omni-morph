package top.aihmc.omm;

import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.joml.Vector3f;

/** Configured single-clip sampling into the MOD's own animated bones and parameter storage. */
final class ReferenceModelAnimation {
    private record Bone(Object target, Vector3f initialRotation,
                        List<?> rotation, List<?> position, List<?> scale) {
    }

    private final Object animated;
    private final Class<?> boneType;
    private final ReferenceJarAnimation animation;
    private final List<Bone> bones = new ArrayList<>();
    private final float start;
    private final float step;
    private final String prefix;
    private final Object model;
    private final Map<?, ?> files;
    private final JsonObject configuration;

    ReferenceModelAnimation(String prefix, Object model, Map<?, ?> files, JsonObject configuration) throws Exception {
        this.prefix = prefix;
        this.model = model;
        this.files = files;
        this.configuration = configuration.deepCopy();
        String name = configuration.get("name").getAsString();
        Object clip = null;
        for (Object file : files.values()) {
            Map<?, ?> clips = (Map<?, ?>) file.getClass().getField("animations").get(file);
            if (clips.containsKey(name)) {
                if (clip != null) {
                    throw new IllegalArgumentException("Ambiguous reference animation: " + name);
                }
                clip = clips.get(name);
            }
        }
        if (clip == null) {
            throw new IllegalArgumentException("Original reference animation not found: " + name);
        }
        start = configuration.has("time") ? configuration.get("time").getAsFloat() : 0;
        step = configuration.has("stepSeconds") ? configuration.get("stepSeconds").getAsFloat() : 0;
        if (!Float.isFinite(start) || start < 0 || !Float.isFinite(step) || step < 0) {
            throw new IllegalArgumentException("Animation time and stepSeconds must be finite and nonnegative");
        }
        var queries = new HashMap<String, Double>();
        queries.put("anim_time", 0.0);
        if (configuration.has("queries")) {
            configuration.getAsJsonObject("queries").entrySet().forEach(entry ->
                queries.put(entry.getKey(), entry.getValue().getAsDouble()));
        }
        animation = new ReferenceJarAnimation(prefix, queries);
        animated = Class.forName(prefix + ".geckolib3.geo.animated.AnimatedGeoModel")
            .getConstructor(model.getClass()).newInstance(model);
        boneType = Class.forName(prefix + ".geckolib3.core.processor.IBone");
        Map<?, ?> originalBones = (Map<?, ?>) animated.getClass().getMethod("bones").invoke(animated);
        Map<String, Object> byName = new HashMap<>();
        for (Object bone : originalBones.values()) {
            byName.put((String) boneType.getMethod("getName").invoke(bone), bone);
        }
        for (Object raw : (List<?>) clip.getClass().getField("boneAnimations").get(clip)) {
            String boneName = (String) raw.getClass().getField("boneName").get(raw);
            Object target = byName.get(boneName);
            // Original clips may also target arm/extra geometries absent from this main mesh.
            if (target == null) {
                continue;
            }
            bones.add(new Bone(target, new Vector3f((Vector3f) boneType.getMethod("getInitialRotation").invoke(target)),
                channel(raw, "rotation", true), channel(raw, "position", false), channel(raw, "scale", false)));
        }
        if (bones.isEmpty()) {
            throw new IllegalArgumentException("Animation has no bones in the selected geometry: " + name);
        }
        System.out.println("ORIGINAL ANIMATION BIND name=" + name + " bones=" + bones.size()
            + " time=" + start + " stepSeconds=" + step);
    }

    float[] sample(int frame) throws Exception {
        return sampleTime(start + frame * step);
    }

    ReferenceModelAnimation independent(float timeOffset) throws Exception {
        JsonObject input = configuration.deepCopy();
        input.addProperty("time", start + timeOffset);
        return new ReferenceModelAnimation(prefix, model, files, input);
    }

    float[] sampleTime(float time) throws Exception {
        if (!Float.isFinite(time) || time < 0) {
            throw new IllegalArgumentException("Animation time must be finite and nonnegative");
        }
        for (Bone bone : bones) {
            if (!bone.rotation.isEmpty()) {
                Vector3f rotation = new Vector3f((Vector3f) animation.sample(bone.rotation, time));
                set(bone.target, "Rotation", rotation.add(bone.initialRotation));
            }
            if (!bone.position.isEmpty()) {
                set(bone.target, "Position", (Vector3f) animation.sample(bone.position, time));
            }
            if (!bone.scale.isEmpty()) {
                set(bone.target, "Scale", (Vector3f) animation.sample(bone.scale, time));
            }
        }
        return (float[]) animated.getClass().getMethod("getMatrixData").invoke(animated);
    }

    private List<?> channel(Object raw, String name, boolean rotation) throws Exception {
        List<?> frames = (List<?>) raw.getClass().getField(name).get(raw);
        return frames.isEmpty() ? List.of() : animation.channel(frames, rotation);
    }

    private void set(Object target, String channel, Vector3f value) throws Exception {
        if (!value.isFinite()) {
            throw new IllegalStateException("Non-finite original animation " + channel);
        }
        boneType.getMethod("set" + channel + "X", float.class).invoke(target, value.x);
        boneType.getMethod("set" + channel + "Y", float.class).invoke(target, value.y);
        boneType.getMethod("set" + channel + "Z", float.class).invoke(target, value.z);
    }
}
