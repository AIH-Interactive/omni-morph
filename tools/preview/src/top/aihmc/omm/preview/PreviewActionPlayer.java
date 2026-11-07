package top.aihmc.omm.preview;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import top.aihmc.omnimorph.model.ysm.animation.*;

/** Direct YSM action slots; real parsing is deferred until play, never replaced by metadata samples. */
public final class PreviewActionPlayer {
    static final class Definition {
        final Map<String, YsmAnimationClip> metadata = new LinkedHashMap<>();
        private final Map<String, Map.Entry<String, JsonElement>> sources = new LinkedHashMap<>();
        private final Map<String, YsmAnimationClip> compiled = new HashMap<>();
        private final PreviewMolang compiler;

        Definition(PreviewMesh mesh, PreviewConfig config) {
            compiler = new PreviewMolang(config.states, config.scene.seed);
            for (String source : mesh.animationSources) {
                JsonObject root = JsonParser.parseString(source).getAsJsonObject();
                if (!root.has("animations")) {
                    continue;
                }
                // This original non-compiling parser pass supplies lengths/loop modes for schemas only.
                YsmAnimationParser.parse(source, false, compiler.engine()).forEach((name, header) -> {
                    header.boneAnimations.clear();
                    metadata.put(normalize(name), header);
                });
                root.getAsJsonObject("animations")
                    .entrySet()
                    .forEach(entry -> sources.put(normalize(entry.getKey()), entry));
            }
        }

        YsmAnimationClip clip(String name) {
            String key = normalize(name);
            var source = sources.get(key);
            if (source == null) {
                return null;
            }
            return compiled.computeIfAbsent(key, ignored -> {
                JsonObject root = new JsonObject(), animations = new JsonObject();
                animations.add(source.getKey(), source.getValue());
                root.add("animations", animations);
                YsmAnimationClip clip =
                    YsmAnimationParser.parse(root.toString(), compiler.engine()).get(source.getKey());
                for (var event : clip.events) {
                    if (!event.type().equals("timeline") && !event.type().equals("sound")) {
                        throw new UnsupportedOperationException(
                            "Action clip requests unconnected " + event.type() + " host");
                    }
                }
                return clip;
            });
        }
    }

    public static final class PlayingAnimation {
        public final YsmAnimationClip clip;
        public float time;
        public long playbackGeneration;
        private boolean loop;
        private float speed;
        private float weight = 1;
        private float targetWeight = 1;
        private float fadeSpeed;
        private double elapsed;
        private final Map<String, YsmBonePose> startPose;
        private final Map<String, YsmBonePose> finite = new HashMap<>();

        private PlayingAnimation(YsmAnimationClip clip, Map<String, YsmBonePose> startPose) {
            this.clip = clip;
            this.startPose = Map.copyOf(startPose);
        }
    }

    private final Definition definition;
    private final PreviewMolang host;
    private final PreviewSkeleton skeleton;
    private final Map<String, PlayingAnimation> active = new LinkedHashMap<>();
    private Map<String, YsmBonePose> lastOutput = Map.of();

    PreviewActionPlayer(Definition definition, PreviewMolang host, PreviewSkeleton skeleton) {
        this.definition = definition;
        this.host = host;
        this.skeleton = skeleton;
    }

    public Map<String, YsmAnimationClip> getClips() {
        return java.util.Collections.unmodifiableMap(definition.metadata);
    }

    public YsmAnimationClip.LoopMode loopModeFor(String name, YsmAnimationClip.LoopMode fallback) {
        var clip = definition.metadata.get(normalize(name));
        return clip == null ? fallback : clip.loopMode;
    }

    public boolean observerControlled() {
        return false;
    }

    public PlayingAnimation play(String name, boolean loop, float speed) {
        YsmAnimationClip clip = definition.clip(name);
        if (clip == null) {
            return null;
        }
        String key = normalize(name);
        boolean created = !active.containsKey(key);
        PlayingAnimation playing = active.computeIfAbsent(key, ignored -> new PlayingAnimation(clip, lastOutput));
        playing.loop = loop;
        playing.speed = speed;
        playing.playbackGeneration++;
        playing.weight = 1;
        playing.targetWeight = 1;
        playing.fadeSpeed = 0;
        if (created) {
            events(playing, -Float.MIN_VALUE, 0);
        }
        return playing;
    }

    public void stop(String name) {
        host.effects.stopPlayback(active.remove(normalize(name)));
    }

    public boolean isActive(String name) {
        return active.containsKey(normalize(name));
    }

    public PlayingAnimation getActiveAnimation(String name) {
        return active.get(normalize(name));
    }

    void advance(float delta) {
        var iterator = active.values().iterator();
        while (iterator.hasNext()) {
            PlayingAnimation playing = iterator.next();
            if (playing.fadeSpeed > 0) {
                playing.weight = Math.max(playing.targetWeight, playing.weight - delta * playing.fadeSpeed);
                if (playing.weight <= 0 && playing.targetWeight <= 0) {
                    host.effects.stopPlayback(playing);
                    iterator.remove();
                    continue;
                }
            }
            double previous = playing.elapsed;
            playing.elapsed += delta * playing.speed;
            playing.time += delta * playing.speed;
            if (playing.clip.length > 0) {
                if (playing.loop && playing.time > playing.clip.length) {
                    playing.time %= playing.clip.length;
                } else if (!playing.loop) {
                    playing.time = Math.min(playing.time, playing.clip.length);
                    if (playing.time >= playing.clip.length) {
                        playing.targetWeight = 0;
                        playing.fadeSpeed = 5;
                    }
                }
            }
            events(playing, previous, playing.elapsed);
        }
    }

    Map<String, YsmBonePose> sample(boolean[] defaults) {
        Map<String, YsmBonePose> poses = new LinkedHashMap<>();
        float previousTime = host.anim_time, previousLife = host.life_time;
        try {
            for (PlayingAnimation playing : active.values()) {
                host.anim_time = playing.time;
                host.life_time = playing.clip.length;
                float weight = playing.weight * playing.clip.evaluateBlendWeight(host.evaluator());
                for (int index = 0; index < skeleton.names.length; index++) {
                    String name = skeleton.names[index];
                    var bone = playing.clip.boneAnimations.get(name);
                    if (bone == null || weight <= 0) {
                        continue;
                    }
                    YsmBonePose pose =
                        YsmAnimationSampler.holdFinite(YsmAnimationSampler.sampleBone(bone, playing.time,
                                                           host.evaluator(), playing.startPose.get(name)),
                            playing.finite.get(name));
                    playing.finite.put(name, pose);
                    float[] scale = pose.scale();
                    if (!defaults[index] && YsmVisibilityDefaults.isBaseAnimation(playing.clip.name) && scale != null
                        && Math.max(Math.abs(scale[0]), Math.max(Math.abs(scale[1]), Math.abs(scale[2]))) < 0.0001f) {
                        pose = new YsmBonePose(pose.position(), pose.rotation(), null);
                    }
                    poses.put(name, add(poses.get(name), pose, weight));
                }
            }
        } finally {
            host.anim_time = previousTime;
            host.life_time = previousLife;
        }
        return poses;
    }

    void capture(Map<String, YsmBonePose> poses) {
        lastOutput = Map.copyOf(poses);
    }

    private void events(PlayingAnimation playing, double previous, double current) {
        float oldTime = host.anim_time, oldLife = host.life_time;
        host.anim_time = playing.time;
        host.life_time = playing.clip.length;
        try {
            host.effects.events(playing.clip, previous, current, playing.loop, null, playing);
        } finally {
            host.anim_time = oldTime;
            host.life_time = oldLife;
        }
    }

    static YsmBonePose add(YsmBonePose previous, YsmBonePose sample, float weight) {
        // OMP applyDirectPose adds position/rotation and multiplies weighted scales.
        return new YsmBonePose(combine(previous == null ? null : previous.position(), sample.position(), weight, false),
            combine(previous == null ? null : previous.rotation(), sample.rotation(), weight, false),
            combine(previous == null ? null : previous.scale(), sample.scale(), weight, true));
    }

    private static float[] combine(float[] previous, float[] sample, float weight, boolean scale) {
        if (sample == null) {
            return previous;
        }
        float[] result = new float[3];
        for (int axis = 0; axis < 3; axis++) {
            float base = previous == null ? (scale ? 1 : 0) : previous[axis];
            result[axis] = scale ? base * (1 + (sample[axis] - 1) * weight) : base + sample[axis] * weight;
        }
        return result;
    }

    private static String normalize(String name) {
        if (name == null) {
            return "";
        }
        String normalized = name.startsWith("animation.") ? name.substring("animation.".length()) : name;
        int colon = normalized.indexOf(':');
        return (colon >= 0 ? normalized.substring(colon + 1) : normalized).toLowerCase(Locale.ROOT);
    }
}
