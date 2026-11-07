package top.aihmc.omm.preview;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import top.aihmc.omnimorph.model.ysm.animation.YsmAnimationClip;
import top.aihmc.omnimorph.model.ysm.animation.YsmAnimationEvent;
import top.aihmc.omnimorph.model.ysm.animation.YsmAnimationParser;
import top.aihmc.omnimorph.model.ysm.animation.YsmAnimationSampler;
import top.aihmc.omnimorph.model.ysm.animation.YsmBonePose;
import top.aihmc.omnimorph.model.ysm.controller.YsmAnimationController;
import top.aihmc.omnimorph.model.ysm.controller.YsmAnimationControllerParser;
import top.aihmc.omnimorph.model.ysm.controller.YsmBlendTransition;
import top.aihmc.omnimorph.model.ysm.controller.YsmControllerPoseLayer;
import top.aihmc.omnimorph.model.ysm.controller.YsmControllerRuntime;
import top.aihmc.omnimorph.model.ysm.controller.YsmControllerSlot;
import top.aihmc.omnimorph.molang.runtime.ExpressionEvaluator;
import top.aihmc.omnimorph.molang.runtime.MolangExecutionBudget;

/** Standalone playback host for the original controller runtime, with owned clocks and transition snapshots. */
public final class PreviewControllerPlayer {
    public void runFunctionEventsForController(
        YsmAnimationController controller, top.aihmc.omnimorph.molang.runtime.ExpressionEvaluator<?> evaluator) {
        try (var scope = host.functions.beginClientEffects(null)) {
            host.functions.runControllerSlotEvents(controller.name(), controller.slot(), evaluator);
        }
    }
    static final class Definition {
        final Map<String, YsmAnimationClip> clips = new LinkedHashMap<>();
        final Map<String, YsmAnimationController> controllers = new LinkedHashMap<>();

        Definition(PreviewMesh mesh, PreviewConfig config) {
            var compiler = new PreviewMolang(config.states, config.scene.seed);
            for (String source : mesh.animationSources) {
                JsonObject root = JsonParser.parseString(source).getAsJsonObject();
                if (root.has("functions") || root.has("action_schemas")) {
                    throw new UnsupportedOperationException("Controller function/action host is not connected yet");
                }
                if (root.has("animations")) {
                    YsmAnimationParser.parse(source, compiler.engine())
                        .forEach((name, clip) -> clips.put(normalize(name), clip));
                }
                if (root.has("animation_controllers")) {
                    controllers.putAll(YsmAnimationControllerParser.parse(source, compiler.engine()));
                }
            }
            // Match original registration: explicit keys win before short animation aliases.
            for (var entry : new LinkedHashMap<>(clips).entrySet()) {
                int dot = entry.getKey().lastIndexOf('.');
                if (dot >= 0)
                    clips.putIfAbsent(entry.getKey().substring(dot + 1), entry.getValue());
            }
            var functions = new PreviewYsmFunctions(compiler, mesh.ysmResources.functions());
            var missing = top.aihmc.omnimorph.model.ysm.controller.YsmControllerSlotBinder.missingEventControllers(
                functions.controllerEventNames(), controllers.values());
            controllers.putAll(missing);
            if (controllers.isEmpty() && clips.isEmpty()) {
                throw new IllegalArgumentException(
                    "Controller playback requested but no controller or clip definitions were loaded");
            }
            for (var controller : controllers.values()) {
                for (var state : controller.states().values()) {
                    if (state.isBuiltin()) {
                        throw new UnsupportedOperationException("Controller " + controller.name()
                            + " requests an unconnected builtin stage in " + state.name());
                    }
                }
            }
            for (var clip : clips.values()) {
                for (var event : clip.events) {
                    if (!event.type().equals("timeline") && !event.type().equals("sound")) {
                        throw new UnsupportedOperationException(
                            "Controller clip " + clip.name + " requests an unconnected " + event.type() + " event");
                    }
                }
            }
        }
    }

    public static final class PlayingAnimation {
        public final YsmAnimationClip clip;
        public float time;
        public boolean controllerConditionActive;
        private final String controller;
        private YsmAnimationClip.LoopMode loopMode;
        private final Map<String, YsmBonePose> lastFinite = new HashMap<>();
        private final Map<String, YsmBonePose> startPose;
        private double elapsed;
        private float weight;
        private boolean functionSlot;
        private boolean retired;
        private boolean deferTimelineAdvance;
        private float beginningTransitionRemaining;
        private float targetWeight = 1;
        private float fadeSpeed;

        private PlayingAnimation(String controller, YsmAnimationClip clip, YsmAnimationClip.LoopMode loopMode,
            Map<String, YsmBonePose> startPose) {
            this.controller = controller;
            this.clip = clip;
            this.loopMode = loopMode == null ? clip.loopMode : loopMode;
            this.startPose = Map.copyOf(startPose);
        }

        public boolean hasCompletedCycle() {
            return elapsed > Math.max(0, clip.length);
        }
    }

    private final Definition definition;
    private final PreviewMolang host;
    private final PreviewSkeleton skeleton;
    private final boolean[] defaults;
    private final Map<String, Integer> boneIndices = new HashMap<>();
    private final Map<String, PlayingAnimation> active = new LinkedHashMap<>();
    private final Map<String, YsmControllerPoseLayer> layers = new LinkedHashMap<>();
    private final Map<String, String> controllerFunctionAnimations = new HashMap<>();
    private final Map<String, Float> controllerFunctionTransitionLengths = new HashMap<>();
    private final java.util.Set<String> controllerFunctionReloads = new java.util.HashSet<>();
    private boolean updatingControllerFrame;
    private long retiredSequence;
    private Map<String, YsmBonePose> output = Map.of();
    final YsmControllerRuntime runtime;

    PreviewControllerPlayer(Definition definition, PreviewMolang host, PreviewSkeleton skeleton, boolean[] defaults) {
        this.definition = definition;
        this.host = host;
        this.skeleton = skeleton;
        this.defaults = defaults;
        for (int index = 0; index < skeleton.names.length; index++) {
            boneIndices.put(skeleton.names[index], index);
        }
        runtime = new YsmControllerRuntime(this);
        runtime.register(definition.controllers);
        host.controllers = this;
    }

    public PlayingAnimation getControllerFunctionAnimation(String controller) {
        String key = controllerFunctionAnimations.get(normalizeControllerName(controller));
        return key == null ? null : active.get(key);
    }

    void setControllerFunctionTransitionLength(String controller, float seconds) {
        if (Float.isFinite(seconds) && seconds >= 0) {
            controllerFunctionTransitionLengths.put(normalizeControllerName(controller), seconds);
        }
    }

    float controllerFunctionTransitionLength(String controller) {
        return controllerFunctionTransitionLengths.getOrDefault(normalizeControllerName(controller), 0f);
    }

    void requestControllerFunctionReload(String controller) {
        controllerFunctionReloads.add(normalizeControllerName(controller));
    }

    YsmAnimationClip.LoopMode loopModeFor(String animation, YsmAnimationClip.LoopMode fallback) {
        var clip = findClip(animation);
        return clip == null || clip.loopMode == null ? fallback : clip.loopMode;
    }

    void clearControllerFunctionAnimation(String controllerName, float fadeSeconds) {
        String controller = normalizeControllerName(controllerName);
        controllerFunctionReloads.remove(controller);
        String previous = controllerFunctionAnimations.remove(controller);
        if (previous != null)
            retireFunctionSlot(previous, fadeSeconds);
    }

    PlayingAnimation playControllerFunctionAnimation(
        String controllerName, String animation, YsmAnimationClip.LoopMode loopMode, float fadeSeconds) {
        String controller = normalizeControllerName(controllerName);
        var clip = findClip(animation);
        String resolved = clip == null ? normalize(animation) : normalize(clip.name);
        String key = "controller-function/" + controller.length() + "/" + controller + "/" + resolved;
        String previous = controllerFunctionAnimations.get(controller);
        if (controllerFunctionReloads.remove(controller)) {
            if (previous != null)
                retireFunctionSlot(previous, fadeSeconds);
            previous = null;
        }
        boolean created = !active.containsKey(key);
        if (previous != null && !previous.equals(key))
            retireFunctionSlot(previous, fadeSeconds);
        if (created)
            beginControllerPoseTransition(controller, YsmBlendTransition.linear(fadeSeconds));
        PlayingAnimation playing = active.get(key);
        if (playing == null) {
            if (clip == null)
                return null;
            playing = new PlayingAnimation(controller, clip, loopMode, output);
            playing.functionSlot = true;
            playing.controllerConditionActive = true;
            playing.weight = 1;
            playing.beginningTransitionRemaining = Math.max(0, fadeSeconds);
            playing.deferTimelineAdvance = updatingControllerFrame;
            active.put(key, playing);
            if (playing.beginningTransitionRemaining == 0)
                events(playing, -Float.MIN_VALUE, 0);
        }
        playing.loopMode = loopMode == null ? playing.clip.loopMode : loopMode;
        playing.targetWeight = 1;
        playing.weight = 1;
        playing.fadeSpeed = 0;
        controllerFunctionAnimations.put(controller, key);
        layers.computeIfAbsent(controller, ignored -> new YsmControllerPoseLayer());
        return playing;
    }

    private void retireFunctionSlot(String key, float seconds) {
        var playing = active.remove(key);
        if (playing == null)
            return;
        host.effects.stopPlayback(playing);
        if (seconds <= 0)
            return;
        playing.retired = true;
        playing.targetWeight = 0;
        playing.fadeSpeed = 1 / seconds;
        active.put("controller-retired/" + ++retiredSequence, playing);
    }

    private static String normalizeControllerName(String name) {
        return name == null || name.isBlank() ? "ctrl" : name.toLowerCase(Locale.US);
    }

    public Map<String, YsmAnimationClip> clipsForController(String controller) {
        return definition.clips;
    }

    public MolangExecutionBudget.Scope beginControllerEffects() {
        return host.executionBudget().begin();
    }

    public PreviewYsmFunctions.EffectScope beginFunctionEffects() {
        return host.functions.beginClientEffects(null);
    }

    public void playControllerSounds(
        String controller, top.aihmc.omnimorph.model.ysm.controller.YsmControllerState state) {
        host.effects.stateSounds(controller, state.soundEffects());
    }

    public void stopControllerSounds(String controller) {
        host.effects.stopController(controller);
    }

    public PlayingAnimation getControllerAnimation(String controller, String animation) {
        return active.get(slot(controller, animation));
    }

    public boolean isControllerAnimationFinished(String controller, String animation) {
        var playing = getControllerAnimation(controller, animation);
        return playing != null && playing.loopMode == YsmAnimationClip.LoopMode.ONCE && playing.hasCompletedCycle();
    }

    public void playControllerAnimation(String controller, String animation, YsmAnimationClip.LoopMode loopMode,
        float weight, float fadeSeconds, ExpressionEvaluator<?> evaluator) {
        String key = slot(controller, animation);
        PlayingAnimation playing = active.get(key);
        if (playing == null) {
            YsmAnimationClip clip = findClip(animation);
            if (clip == null) {
                return;
            }
            playing = new PlayingAnimation(controller, clip, loopMode, output);
            playing.beginningTransitionRemaining = Math.max(0, fadeSeconds);
            playing.deferTimelineAdvance = updatingControllerFrame;
            active.put(key, playing);
            playing.controllerConditionActive = weight > 0;
            if (playing.controllerConditionActive && playing.beginningTransitionRemaining == 0) {
                events(playing, -Float.MIN_VALUE, 0);
            }
        }
        layers.computeIfAbsent(controller, ignored -> new YsmControllerPoseLayer());
        playing.controllerConditionActive = weight > 0;
        playing.weight = playing.loopMode == YsmAnimationClip.LoopMode.ONCE && playing.hasCompletedCycle() ? 0 : weight;
    }

    public void beginControllerPoseTransition(String controller, YsmBlendTransition curve) {
        layers.computeIfAbsent(controller, ignored -> new YsmControllerPoseLayer()).beginTransition(curve);
    }

    public void inheritControllerPose(String parent, String child) {
        var source = layers.get(child);
        if (source != null) {
            layers.computeIfAbsent(parent, ignored -> new YsmControllerPoseLayer()).inheritOutput(source);
        }
    }

    public void fadeOutControllerAnimation(String controller, String animation, float fadeSeconds) {
        // Declared controller fades are represented by the pose layer's outgoing snapshot.
        host.effects.stopPlayback(active.remove(slot(controller, animation)));
    }

    public void retireControllerAnimation(String controller, String animation, float fadeSeconds) {
        host.effects.stopPlayback(active.remove(slot(controller, animation)));
    }

    public void resetControllerAnimations(String controller) {
        active.values().removeIf(playing -> {
            if (!java.util.Objects.equals(playing.controller, controller))
                return false;
            host.effects.stopPlayback(playing);
            return true;
        });
        layers.remove(controller);
        controllerFunctionAnimations.remove(normalizeControllerName(controller));
        controllerFunctionReloads.remove(normalizeControllerName(controller));
    }

    Map<String, YsmBonePose> update(float delta) {
        advanceOnly(delta);
        return sampleCurrent();
    }

    void advanceOnly(float delta) {
        for (var layer : layers.values()) {
            layer.advance(delta);
        }
        host.functions.beginControllerFrame();
        updatingControllerFrame = true;
        try {
            runtime.update(host.evaluator(), delta, false);
            host.functions.runUnboundControllerEvents(host.evaluator(), definition.controllers.values());
        } finally {
            updatingControllerFrame = false;
        }
        for (var entry : List.copyOf(active.entrySet())) {
            var playing = entry.getValue();
            if (active.get(entry.getKey()) != playing)
                continue;
            float runningDelta = delta;
            boolean beginningEnded = false;
            if (playing.functionSlot) {
                if (playing.weight != playing.targetWeight && playing.fadeSpeed > 0) {
                    playing.weight = Math.max(playing.targetWeight, playing.weight - delta * playing.fadeSpeed);
                }
                if (playing.weight <= 0 && playing.targetWeight == 0) {
                    host.effects.stopPlayback(playing);
                    active.remove(entry.getKey(), playing);
                    continue;
                }
                if (playing.retired)
                    continue;
            }
            if (playing.deferTimelineAdvance) {
                playing.deferTimelineAdvance = false;
                continue;
            }
            if (playing.beginningTransitionRemaining > 0) {
                float remaining = playing.beginningTransitionRemaining;
                playing.beginningTransitionRemaining = Math.max(0, remaining - Math.max(0, delta));
                playing.time = 0;
                if (playing.beginningTransitionRemaining > 0)
                    continue;
                runningDelta = Math.max(0, delta - remaining);
                beginningEnded = true;
            }
            double previous = playing.elapsed;
            playing.elapsed += runningDelta;
            if (playing.clip.length > 0) {
                if (playing.loopMode == YsmAnimationClip.LoopMode.LOOP) {
                    double phase = playing.elapsed % playing.clip.length;
                    // Original function playback holds the end sample until it is strictly crossed.
                    if (playing.functionSlot && playing.elapsed > 0 && phase == 0)
                        phase = playing.clip.length;
                    playing.time = (float) phase;
                } else {
                    playing.time = (float) Math.min(playing.elapsed, playing.clip.length);
                }
            } else {
                playing.time = (float) playing.elapsed;
            }
            if (playing.controllerConditionActive) {
                if (beginningEnded)
                    events(playing, -Float.MIN_VALUE, 0);
                events(playing, previous, playing.elapsed);
            }
            if (playing.functionSlot && playing.hasCompletedCycle()
                && playing.loopMode == YsmAnimationClip.LoopMode.ONCE) {
                playing.targetWeight = 0;
                if (playing.fadeSpeed <= 0)
                    playing.fadeSpeed = 5;
            }
        }
    }

    Map<String, YsmBonePose> sampleCurrent() {
        return sampleCurrent(Map.of());
    }

    Map<String, YsmBonePose> sampleCurrent(Map<String, YsmBonePose> direct) {
        Map<String, Map<String, YsmBonePose>> contributions = new LinkedHashMap<>();
        for (var playing : List.copyOf(active.values())) {
            if (playing.retired || playing.weight <= 0
                || !playing.functionSlot && playing.loopMode == YsmAnimationClip.LoopMode.ONCE
                    && playing.hasCompletedCycle()) {
                continue;
            }
            boolean base = YsmVisibilityDefaults.isBaseAnimation(playing.clip.name);
            int animCtrl = top.aihmc.omnimorph.molang.storage.StringPool.computeIfAbsent("anim_ctrl");
            Object previousAnimCtrl = base ? host.variables.getScoped(animCtrl) : null;
            if (base)
                host.variables.setScoped(animCtrl, 1f);
            try (var scope = runtime.openAnimationContext(
                     playing.controller, host.evaluator(), playing.time, playing.clip.length)) {
                float weight = playing.weight * playing.clip.evaluateBlendWeight(host.evaluator());
                if (!Float.isFinite(weight) || weight <= 0) {
                    continue;
                }
                var poses = contributions.computeIfAbsent(playing.controller, ignored -> new LinkedHashMap<>());
                for (var bone : playing.clip.boneAnimations.values()) {
                    YsmBonePose sample =
                        YsmAnimationSampler.holdFinite(YsmAnimationSampler.sampleBone(bone, playing.time,
                                                           host.evaluator(), playing.startPose.get(bone.boneName)),
                            playing.lastFinite.get(bone.boneName));
                    playing.lastFinite.put(bone.boneName, sample);
                    int index = indexOf(bone.boneName);
                    float[] scale = sample.scale();
                    if (YsmVisibilityDefaults.isBaseAnimation(playing.clip.name) && index >= 0 && !defaults[index]
                        && scale != null
                        && Math.max(Math.abs(scale[0]), Math.max(Math.abs(scale[1]), Math.abs(scale[2]))) < 0.0001f) {
                        // OMP keeps base-hidden visibility separate, allowing an action to restore scale later.
                        sample = new YsmBonePose(sample.position(), sample.rotation(), null);
                    }
                    poses.put(bone.boneName, YsmBonePose.accumulate(poses.get(bone.boneName), sample, weight));
                }
            } finally {
                if (base)
                    host.variables.setScoped(animCtrl, previousAnimCtrl == null ? 0f : previousAnimCtrl);
            }
        }
        Map<String, YsmBonePose> composed = new LinkedHashMap<>();
        var ordered = new ArrayList<>(layers.entrySet());
        ordered.sort(Comparator.comparingInt(entry -> YsmControllerSlot.executionOrder(entry.getKey())));
        boolean directApplied = false;
        for (var entry : ordered) {
            if (!directApplied && YsmControllerSlot.executionOrder(entry.getKey()) >= 20) {
                direct.forEach(
                    (bone, pose) -> composed.put(bone, PreviewActionPlayer.add(composed.get(bone), pose, 1)));
                directApplied = true;
            }
            var pose = entry.getValue().compose(contributions.getOrDefault(entry.getKey(), Map.of()),
                this::restRotation, bone -> currentPose(composed, bone));
            pose.forEach(
                (bone, value) -> composed.put(bone, YsmBonePose.fillMissingChannels(value, composed.get(bone))));
        }
        if (!directApplied) {
            direct.forEach((bone, pose) -> composed.put(bone, PreviewActionPlayer.add(composed.get(bone), pose, 1)));
        }
        output = Map.copyOf(composed);
        return output;
    }

    private YsmBonePose currentPose(Map<String, YsmBonePose> poses, String bone) {
        YsmBonePose current = poses.get(bone);
        int index = indexOf(bone);
        if (index < 0 || defaults[index]) {
            return current;
        }
        return YsmBonePose.fillMissingChannels(current == null ? new YsmBonePose(null, null, null) : current,
            new YsmBonePose(null, null, new float[] {0, 0, 0}));
    }

    private float[] restRotation(String bone) {
        int index = indexOf(bone);
        if (index < 0) {
            return null;
        }
        var rest = skeleton.rotations[index];
        return new float[] {rest.x, rest.y, rest.z};
    }

    private int indexOf(String bone) {
        return boneIndices.getOrDefault(bone, -1);
    }

    private void events(PlayingAnimation playing, double previous, double current) {
        try (var scope = runtime.openAnimationContext(
                 playing.controller, host.evaluator(), playing.time, playing.clip.length)) {
            host.effects.events(playing.clip, previous, current, playing.loopMode == YsmAnimationClip.LoopMode.LOOP,
                playing.controller, playing);
        }
    }

    private YsmAnimationClip findClip(String animation) {
        return lookupClip(definition.clips, animation);
    }

    private static YsmAnimationClip lookupClip(Map<String, YsmAnimationClip> clips, String animation) {
        String normalized = normalize(animation);
        YsmAnimationClip clip = clips.get(normalized);
        int dot = normalized.lastIndexOf('.');
        return clip == null && dot >= 0 ? clips.get(normalized.substring(dot + 1)) : clip;
    }

    private static String slot(String controller, String animation) {
        return controller.toLowerCase(Locale.ROOT) + '/' + normalize(animation);
    }

    private static String normalize(String name) {
        String normalized = name.startsWith("animation.") ? name.substring("animation.".length()) : name;
        int namespace = normalized.indexOf(':');
        if (namespace >= 0) {
            normalized = normalized.substring(namespace + 1);
        }
        int suffix = normalized.indexOf('#');
        return (suffix >= 0 ? normalized.substring(0, suffix) : normalized).toLowerCase(Locale.ROOT);
    }
}
