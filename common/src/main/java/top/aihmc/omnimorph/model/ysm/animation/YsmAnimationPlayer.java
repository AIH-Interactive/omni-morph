package top.aihmc.omnimorph.model.ysm.animation;

import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.Minecraft;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUseAnimation;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.KineticWeapon;
import net.minecraft.util.Mth;
import org.figuramc.figura.avatar.Avatar;
import top.aihmc.omnimorph.mixin.AbstractArrowAccessor;
import top.aihmc.omnimorph.molang.parser.ast.Expression;
import top.aihmc.omnimorph.molang.runtime.ExpressionEvaluator;
import top.aihmc.omnimorph.molang.runtime.MolangExecutionBudget;
import org.figuramc.figura.FiguraMod;
import top.aihmc.omnimorph.molang.storage.StringPool;
import org.figuramc.figura.lua.api.particle.ParticleAPI;
import org.figuramc.figura.lua.api.sound.LuaSound;
import org.figuramc.figura.math.vector.FiguraVec3;
import top.aihmc.omnimorph.model.ysm.YsmBoneRole;
import top.aihmc.omnimorph.model.ysm.YsmHandActionState;
import top.aihmc.omnimorph.model.ysm.YsmHandQuery;
import top.aihmc.omnimorph.model.ysm.YsmInputState;
import top.aihmc.omnimorph.model.ysm.YsmModelPart;
import top.aihmc.omnimorph.model.ysm.YsmModelRuntime;
import top.aihmc.omnimorph.model.ysm.YsmParticleRuntime;
import top.aihmc.omnimorph.model.ysm.YsmMolangFunctionRuntime;
import top.aihmc.omnimorph.model.ysm.YsmFilterRuntime;
import top.aihmc.omnimorph.model.ysm.controller.YsmAnimationController;
import top.aihmc.omnimorph.model.ysm.controller.YsmControllerAnimationRef;
import top.aihmc.omnimorph.model.ysm.controller.YsmControllerRuntime;
import top.aihmc.omnimorph.model.ysm.controller.YsmControllerState;
import top.aihmc.omnimorph.model.ysm.controller.YsmControllerSlot;
import top.aihmc.omnimorph.model.ysm.controller.YsmControllerSlotBinder;
import top.aihmc.omnimorph.model.ysm.controller.YsmControllerPoseLayer;
import top.aihmc.omnimorph.model.ysm.controller.YsmBlendTransition;
import top.aihmc.omnimorph.model.ysm.sync.YsmSyncMessage;

import java.lang.reflect.Field;
import java.util.*;

public class YsmAnimationPlayer {
    private static final ClassValue<Map<String, Optional<Field>>> STATE_FIELDS =
            new ClassValue<>() {
                @Override
                protected Map<String, Optional<Field>> computeValue(Class<?> type) {
                    return new java.util.concurrent.ConcurrentHashMap<>();
                }
            };
    private final YsmModelRuntime runtime;
    private final Map<String, YsmAnimationClip> clips = new HashMap<>();
    private final Map<String, YsmAnimationClip> defaultClips = new HashMap<>();
    private final Map<String, YsmAnimationClip> defaultArmClips = new HashMap<>();
    private final Map<String, YsmAnimationController> controllers = new LinkedHashMap<>();
    private final YsmControllerRuntime controllerRuntime;
    private final Map<String, PlayingAnimation> activeAnimations = new LinkedHashMap<>();
    private final Map<String, String> controllerFunctionAnimations = new HashMap<>();
    private final Map<String, Float> controllerFunctionTransitionLengths = new HashMap<>();
    private final Set<String> controllerFunctionReloads = new HashSet<>();
    private final Map<String, YsmControllerPoseLayer> controllerPoseLayers = new LinkedHashMap<>();
    private final Map<String, YsmControllerPoseLayer> armControllerPoseLayers = new LinkedHashMap<>();
    public static final int PLAINTEXT_FORMAT_VERSION = 65535;
    /** Reference AnimationFormatValidator: from this binary format on, authored hold/use loops are kept. */
    private static final int AUTHORED_LOOP_FORMAT_VERSION = 19;
    private int modelFormatVersion = PLAINTEXT_FORMAT_VERSION;
    /** Per-arm use/swing action ("left"/"right"). */
    private final Map<String, String> nativeHandAnimations = new HashMap<>();
    /** Per-arm hold, kept separately so an action pauses rather than replaces it. */
    private final Map<String, String> nativeHoldAnimations = new HashMap<>();
    private final Set<String> disabledNativeAnimations = new HashSet<>();
    private final Set<String> baseHiddenBones = new HashSet<>();
    private final YsmAnimationFrameClock frameClock = new YsmAnimationFrameClock();
    private final YsmControllerSoundBank controllerSounds = new YsmControllerSoundBank();
    private boolean controllerInitialHiddenResolved;
    private Entity currentEntity;
    private final Map<String, String> subEntityAnimations = new LinkedHashMap<>();
    private final Set<String> functionEventControllers = new HashSet<>();
    private boolean wasSwinging;
    /** The local swing pulse already turned into a native swing action. */
    private int lastSwingPulseSequence;
    private long lastBudgetFailureFrame = Long.MIN_VALUE;
    private long notificationSequence;

    public static class PlayingAnimation {
        public final YsmAnimationClip clip;
        public long playbackGeneration;
        private long notificationGeneration;
        private boolean endNotificationDispatched;
        private boolean notificationsActive = true;
        public float time;
        public float previousTime;
        private double eventWindowEnd;
        private boolean completedCycle;
        public float speed = 1f;
        public float weight = 1f;
        public boolean loop;
        public YsmAnimationClip.LoopMode loopMode;
        public final boolean isNative;
        public boolean initialEventsDispatched;
        private final java.util.List<YsmAnimationEvent> pendingInitialSounds = new java.util.ArrayList<>();
        private final List<LuaSound> sounds = new java.util.ArrayList<>();

        // For smooth transitions in native state machine
        public float targetWeight = 0f;
        public float fadeSpeed = 0f;
        public float sampledTime = Float.NaN;
        /** Reference PlayState.PAUSE: the clock keeps running but no pose is submitted. */
        public boolean paused;
        private boolean retired;
        private String controllerName;
        private YsmSyncMessage.Playback remoteState;

        public PlayingAnimation(YsmAnimationClip clip, boolean isNative) {
            this.clip = clip;
            this.isNative = isNative;
            this.loop = clip.loop;
            this.loopMode = clip.loopMode;
            this.targetWeight = isNative ? 0f : 1f;
            this.weight = isNative ? 0f : 1f;
        }

        public boolean hasCompletedCycle() {
            return completedCycle || time > Math.max(0f, clip.length);
        }
    }

    private boolean nativeStateMachineEnabled = true;
    private boolean observerControlled;
    private long retiredControllerSequence;

    public YsmAnimationPlayer(YsmModelRuntime runtime) {
        this.runtime = runtime;
        this.controllerRuntime = new YsmControllerRuntime(this);
    }

    public boolean isNativeStateMachineEnabled() {
        return nativeStateMachineEnabled;
    }

    public void setNativeStateMachineEnabled(boolean enabled) {
        this.nativeStateMachineEnabled = enabled;
    }

    public boolean isNativeAnimationEnabled(String name) {
        return name != null && !disabledNativeAnimations.contains(normalizeName(name));
    }

    public void setNativeAnimationEnabled(String name, boolean enabled) {
        if (name == null || name.isBlank())
            return;
        String normalized = normalizeName(name);
        if (enabled) {
            disabledNativeAnimations.remove(normalized);
        } else {
            disabledNativeAnimations.add(normalized);
            PlayingAnimation animation = activeAnimations.get(normalized);
            if (animation != null && animation.isNative)
                animation.targetWeight = 0f;
        }
    }

    public void registerAnimations(Map<String, YsmAnimationClip> newClips) {
        if (newClips != null) {
            for (Map.Entry<String, YsmAnimationClip> entry : newClips.entrySet()) {
                this.clips.put(entry.getKey(), entry.getValue());
                this.clips.putIfAbsent(literalAnimationName(entry.getKey()), entry.getValue());
                String normalized = normalizeName(entry.getKey());
                this.clips.putIfAbsent(normalized, entry.getValue());
            }
            for (Map.Entry<String, YsmAnimationClip> entry : newClips.entrySet()) {
                String normalized = normalizeName(entry.getKey());
                this.clips.putIfAbsent(simpleName(normalized), entry.getValue());
            }
        }
    }

    public Map<String, YsmAnimationClip> getClips() {
        return clips;
    }

    /** Defaults stay outside the frozen author's inventory and its v1 clip indexes. */
    public void registerDefaultAnimations(Map<String, YsmAnimationClip> defaults,
                                           Map<String, YsmAnimationClip> firstPerson) {
        defaultClips.clear();
        defaultClips.putAll(defaults);
        defaultArmClips.clear();
        firstPerson.values().forEach(clip -> defaultArmClips.put(clip.name, clip));
    }

    public Set<String> defaultExcludedNames(Collection<String> declared) {
        Set<String> result = new HashSet<>(clips.keySet());
        for (String name : declared) {
            result.add(literalAnimationName(name));
            String normalized = normalizeName(name);
            result.add(normalized);
            result.add(simpleName(normalized));
        }
        return Set.copyOf(result);
    }

    /**
     * YSM binary format version of the loaded model. Plaintext folders have no binary
     * version; the reference reports them as 65535, so they keep authored loops.
     */
    public void setModelFormatVersion(int version) {
        modelFormatVersion = version;
    }

    public int modelFormatVersion() {
        return modelFormatVersion;
    }

    private YsmAnimationClip nativeClip(String name) {
        YsmAnimationClip authored = clips.get(name);
        return authored == null ? defaultClips.get(name) : authored;
    }

    public void registerControllers(Map<String, YsmAnimationController> newControllers) {
        if (newControllers != null) {
            controllers.putAll(newControllers);
            controllerRuntime.register(newControllers);
        }
    }

    public Map<String, YsmAnimationController> getControllers() {
        return controllers;
    }

    public void registerFunctionEventControllers(Collection<String> events) {
        Map<String, YsmAnimationController> missing = YsmControllerSlotBinder.missingEventControllers(events, controllers.values());
        functionEventControllers.addAll(missing.keySet());
        registerControllers(missing);
    }

    public YsmControllerRuntime controllerRuntime() {
        return controllerRuntime;
    }

    public void runFunctionEventsForController(YsmAnimationController controller, ExpressionEvaluator<?> evaluator) {
        if (controller != null) {
            try (var effectScope = beginControllerEffects()) {
                runtime.functions().runControllerSlotEvents(controller.name(), controller.slot(), evaluator);
            }
        }
    }

    public YsmMolangFunctionRuntime.EffectScope beginControllerEffects() {
        return runtime == null ? null : runtime.functions().beginClientEffects(null);
    }

    public void playControllerSounds(String controllerName, YsmControllerState state) {
        for (String sound : state.soundEffects()) {
            playOwnedSound(controllerName, new YsmAnimationEvent(0f, "sound", sound));
        }
    }

    public void stopControllerSounds(String controllerName) {
        controllerSounds.stop(normalizeControllerName(controllerName));
        if (runtime != null) {
            runtime.sounds().stopController(normalizeControllerName(controllerName));
        }
    }

    public void closeSounds() {
        activeAnimations.values().forEach(this::stopPlaybackSounds);
        controllerSounds.close();
        currentEntity = null;
    }

    public void resetControllerAnimations(String controllerName) {
        String controller = normalizeControllerName(controllerName);
        activeAnimations.values().removeIf(animation -> {
            if (!controller.equals(animation.controllerName)) {
                return false;
            }
            invalidateNotifications(animation);
            stopPlaybackSounds(animation);
            return true;
        });
        controllerFunctionAnimations.remove(controller);
        controllerFunctionReloads.remove(controller);
        controllerPoseLayers.remove(controller);
        armControllerPoseLayers.remove(controller);
    }

    public Map<String, PlayingAnimation> getActiveAnimations() {
        return activeAnimations;
    }

    public boolean observerControlled() {
        return observerControlled;
    }

    public List<YsmSyncMessage.Playback> captureRemotePlaybacks(List<YsmAnimationClip> clipIndex, List<String> controllerIndex) {
        List<YsmSyncMessage.Playback> result = new ArrayList<>();
        for (var entry : activeAnimations.entrySet().stream().sorted(Map.Entry.comparingByKey()).toList()) {
            PlayingAnimation playing = entry.getValue();
            if (defaultClips.containsValue(playing.clip)) {
                // Legacy receivers retain the authored projection. A later outfit capability carries template identity.
                continue;
            }
            int flags = (playing.isNative ? 1 : 0) | (entry.getKey().startsWith("controller-function/") ? 2 : 0)
                    | (playing.retired ? 4 : 0);
            int controller = -1;
            if (playing.controllerName != null) {
                for (int index = 0; index < controllerIndex.size(); index++) {
                    if (controllerIndex.get(index).equalsIgnoreCase(playing.controllerName)) {
                        controller = index;
                        break;
                    }
                }
            }
            if (playing.controllerName != null && controller < 0) {
                throw new IllegalArgumentException("Unindexed YSM controller");
            }
            result.add(new YsmSyncMessage.Playback(result.size(), clipIndex.indexOf(playing.clip), controller,
                    playing.time, playing.speed, playing.weight, playing.targetWeight, playing.loopMode.ordinal(),
                    playing.fadeSpeed, flags));
        }
        return List.copyOf(result);
    }

    public void validateRemotePlaybacks(List<YsmSyncMessage.Playback> values, List<YsmAnimationClip> clips, List<String> controllers) {
        buildRemotePlaybacks(values, clips, controllers);
    }

    public void applyRemotePlaybacks(List<YsmSyncMessage.Playback> values, List<YsmAnimationClip> clips, List<String> controllers) {
        Map<String, PlayingAnimation> restored = buildRemotePlaybacks(values, clips, controllers);
        if (!observerControlled) {
            controllerSounds.close();
            runtime.sounds().stopAll();
        }
        activeAnimations.forEach((slot, previous) -> {
            PlayingAnimation next = restored.get(slot);
            if (observerControlled && next != null && previous.clip == next.clip) {
                if (previous.playbackGeneration == 0 && Objects.equals(previous.remoteState, next.remoteState)) {
                    // A repeated owner sample must not undo the observer's frame-by-frame advancement.
                    restored.put(slot, previous);
                } else {
                    next.sounds.addAll(previous.sounds);
                    previous.sounds.clear();
                    runtime.sounds().transferPlayback(previous, next);
                }
            } else {
                stopPlaybackSounds(previous);
            }
        });
        activeAnimations.clear();
        activeAnimations.putAll(restored);
        controllerFunctionAnimations.clear();
        controllerPoseLayers.clear();
        armControllerPoseLayers.clear();
        for (var entry : restored.entrySet()) {
            PlayingAnimation playing = entry.getValue();
            if (playing.controllerName != null) {
                controllerPoseLayers.computeIfAbsent(playing.controllerName, ignored -> new YsmControllerPoseLayer());
                armControllerPoseLayers.computeIfAbsent(playing.controllerName, ignored -> new YsmControllerPoseLayer());
                if (entry.getKey().startsWith("controller-function/")) {
                    controllerFunctionAnimations.put(playing.controllerName, entry.getKey());
                } else if (!playing.retired) {
                    controllerRuntime.addRemotePlayback(playing.controllerName, playing.clip.name);
                }
            }
        }
        observerControlled = true;
    }

    private Map<String, PlayingAnimation> buildRemotePlaybacks(List<YsmSyncMessage.Playback> values,
                                                               List<YsmAnimationClip> clips, List<String> controllers) {
        Map<String, PlayingAnimation> result = new LinkedHashMap<>();
        for (var value : values) {
            if (value.clip() >= clips.size() || value.controller() >= controllers.size()
                    || (value.flags() & 2) != 0 && value.controller() < 0) {
                throw new IllegalArgumentException("Invalid remote playback index");
            }
            YsmAnimationClip clip = clips.get(value.clip());
            PlayingAnimation playing = new PlayingAnimation(clip, (value.flags() & 1) != 0);
            playing.remoteState = value;
            playing.controllerName = value.controller() < 0 ? null : normalizeControllerName(controllers.get(value.controller()));
            playing.time = value.time();
            playing.previousTime = value.time();
            playing.eventWindowEnd = value.time();
            playing.initialEventsDispatched = true;
            playing.speed = value.speed();
            playing.weight = value.weight();
            playing.targetWeight = value.targetWeight();
            playing.fadeSpeed = value.fadeSpeed();
            playing.retired = (value.flags() & 4) != 0;
            playing.loopMode = YsmAnimationClip.LoopMode.values()[value.loopMode()];
            playing.loop = playing.loopMode == YsmAnimationClip.LoopMode.LOOP;
            String key;
            if (playing.retired) {
                key = "controller-retired/remote/" + value.slot();
            } else if ((value.flags() & 2) != 0) {
                key = "controller-function/" + playing.controllerName.length() + "/" + playing.controllerName
                        + "/" + resolveAnimationName(clip.name);
            } else if (playing.controllerName != null) {
                key = controllerSlotKey(playing.controllerName, clip.name);
            } else {
                key = resolveAnimationName(clip.name);
            }
            if (result.putIfAbsent(key, playing) != null) {
                throw new IllegalArgumentException("Duplicate remote playback role");
            }
        }
        return result;
    }

    public boolean isActive(String name) {
        return activeAnimations.containsKey(resolveAnimationName(name));
    }

    public PlayingAnimation getActiveAnimation(String name) {
        return activeAnimations.get(resolveAnimationName(name));
    }

    public void startBaseAnimations() {
        baseHiddenBones.clear();
        ExpressionEvaluator<?> evaluator = evaluatorForEvents();
        Set<YsmAnimationClip> controlledClips = new HashSet<>();
        for (YsmAnimationController controller : controllers.values()) {
            for (YsmControllerState state : controller.states().values()) {
                for (var reference : state.animations()) {
                    YsmAnimationClip controlled = clips.get(resolveAnimationName(reference.animation()));
                    if (controlled != null) {
                        controlledClips.add(controlled);
                    }
                }
            }
        }
        for (Map.Entry<String, YsmAnimationClip> entry : clips.entrySet()) {
            String normalized = normalizeName(entry.getKey());
            if (!isBaseAnimation(normalized, entry.getValue()) || controlledClips.contains(entry.getValue()))
                continue;
            // Explicit controller references own playback and gating; legacy autoplay is only a fallback.
            String activeKey = baseSlotKey(normalized);
            collectBaseHiddenBones(entry.getValue(), null);
            if (activeAnimations.containsKey(activeKey))
                continue;
            PlayingAnimation playing = new PlayingAnimation(entry.getValue(), false);
            playing.loop = true;
            playing.speed = 1f;
            playing.targetWeight = 1f;
            playing.weight = 1f;
            activeAnimations.put(activeKey, playing);
            dispatchInitialEvents(playing, evaluator);
        }
        collectControllerInitialHiddenBones(null);
        applyBaseHiddenDefaults();
    }

    public PlayingAnimation play(String name, boolean loop, float speed) {
        return play(name, loop, speed, 0f);
    }

    public PlayingAnimation play(String name, boolean loop, float speed, float fadeSeconds) {
        return play(name, loop ? YsmAnimationClip.LoopMode.LOOP : YsmAnimationClip.LoopMode.ONCE, speed, fadeSeconds);
    }

    public PlayingAnimation play(String name, YsmAnimationClip.LoopMode loopMode, float speed, float fadeSeconds) {
        YsmAnimationClip clip = clips.get(resolveAnimationName(name));
        if (clip != null && runtime != null && runtime.owner() != null && runtime.owner().isHost
                && !runtime.isPreviewRuntime() && !runtime.isSubEntityRuntime()) {
            YsmAnimationClip.LoopMode selectedLoop = loopMode == null ? clip.loopMode : loopMode;
            runtime.functions().recordOwnerEvent(YsmSyncMessage.EventKind.PLAY,
                    List.of((float) syncClipIndex(clip), (float) selectedLoop.ordinal(), speed,
                            Math.max(0, fadeSeconds)));
        }
        return playInSlot(resolveAnimationName(name), name, loopMode, speed, fadeSeconds, null);
    }

    private int syncClipIndex(YsmAnimationClip clip) {
        return clips.values().stream().distinct().sorted(Comparator.comparing(value -> value.name)).toList().indexOf(clip);
    }

    private PlayingAnimation playInSlot(String slot, String name, YsmAnimationClip.LoopMode loopMode,
                                         float speed, float fadeSeconds, String controllerName) {
        return playInSlot(slot, name, loopMode, speed, fadeSeconds, controllerName, null);
    }

    private PlayingAnimation playInSlot(String slot, String name, YsmAnimationClip.LoopMode loopMode,
                                         float speed, float fadeSeconds, String controllerName,
                                         ExpressionEvaluator<?> initialEvaluator) {
        String normalized = resolveAnimationName(name);
        YsmAnimationClip clip = nativeClip(normalized);
        if (clip == null) return null;

        boolean created = !activeAnimations.containsKey(slot);
        PlayingAnimation playing = activeAnimations.computeIfAbsent(slot, k -> new PlayingAnimation(clip, false));
        stopPlaybackSounds(playing);
        playing.playbackGeneration++;
        // Controllers refresh weights on an existing clock every frame. Only a new slot, an explicit
        // direct play, or reactivation after cancellation starts another notification generation.
        if (created || controllerName == null || !playing.notificationsActive) {
            playing.notificationGeneration = ++notificationSequence;
            playing.endNotificationDispatched = false;
            playing.notificationsActive = true;
        }
        playing.controllerName = controllerName;
        playing.loopMode = loopMode == null ? clip.loopMode : loopMode;
        playing.loop = playing.loopMode == YsmAnimationClip.LoopMode.LOOP;
        playing.speed = speed;
        playing.targetWeight = 1f;
        if (fadeSeconds > 0f) {
            if (created)
                playing.weight = 0f;
            playing.fadeSpeed = 1f / fadeSeconds;
        } else {
            playing.weight = 1f;
            playing.fadeSpeed = 0f;
        }
        if (created && !clip.events.isEmpty())
            dispatchInitialEvents(playing, initialEvaluator == null ? evaluatorForEvents() : initialEvaluator);
        return playing;
    }

    public PlayingAnimation playControllerAnimation(String controllerName, String animationName,
                                                     YsmAnimationClip.LoopMode loopMode, float weight,
                                                     float fadeSeconds) {
        return playControllerAnimation(controllerName, animationName, loopMode, weight, fadeSeconds, null);
    }

    public PlayingAnimation playControllerAnimation(String controllerName, String animationName,
                                                     YsmAnimationClip.LoopMode loopMode, float weight,
                                                     float fadeSeconds, ExpressionEvaluator<?> evaluator) {
        PlayingAnimation playing = playInSlot(controllerSlotKey(controllerName, animationName),
                animationName, loopMode, 1f, 0f, normalizeControllerName(controllerName), evaluator);
        if (playing != null) {
            playing.controllerName = normalizeControllerName(controllerName);
            controllerPoseLayers.computeIfAbsent(playing.controllerName, ignored -> new YsmControllerPoseLayer());
            armControllerPoseLayers.computeIfAbsent(playing.controllerName, ignored -> new YsmControllerPoseLayer());
            playing.targetWeight = Float.isFinite(weight) ? Math.max(0f, weight) : 0f;
            playing.weight = playing.targetWeight;
        }
        return playing;
    }

    public void beginControllerPoseTransition(String controllerName, YsmBlendTransition curve, boolean shortestRotation) {
        controllerPoseLayers.computeIfAbsent(normalizeControllerName(controllerName), ignored -> new YsmControllerPoseLayer())
                .beginTransition(curve, shortestRotation);
        armControllerPoseLayers.computeIfAbsent(normalizeControllerName(controllerName), ignored -> new YsmControllerPoseLayer())
                .beginTransition(curve, shortestRotation);
    }

    public void inheritControllerPose(String parentName, String childName) {
        String parent = normalizeControllerName(parentName);
        String child = normalizeControllerName(childName);
        inheritControllerPose(controllerPoseLayers, parent, child);
        inheritControllerPose(armControllerPoseLayers, parent, child);
    }

    private static void inheritControllerPose(Map<String, YsmControllerPoseLayer> layers, String parent, String child) {
        YsmControllerPoseLayer source = layers.get(child);
        if (source != null) {
            layers.computeIfAbsent(parent, ignored -> new YsmControllerPoseLayer()).inheritOutput(source);
        }
    }

    public PlayingAnimation getControllerAnimation(String controllerName, String animationName) {
        return activeAnimations.get(controllerSlotKey(controllerName, animationName));
    }

    public boolean isControllerAnimationFinished(String controllerName, String animationName) {
        PlayingAnimation playing = getControllerAnimation(controllerName, animationName);
        return playing != null && playing.loopMode == YsmAnimationClip.LoopMode.ONCE
                && playing.hasCompletedCycle();
    }

    public void fadeOutControllerAnimation(String controllerName, String animationName, float fadeSeconds) {
        fadeOutSlot(controllerSlotKey(controllerName, animationName), fadeSeconds);
    }

    public void retireControllerAnimation(String controllerName, String animationName, float fadeSeconds) {
        retireSlot(controllerSlotKey(controllerName, animationName), fadeSeconds);
    }

    private void retireSlot(String slot, float fadeSeconds) {
        PlayingAnimation playing = activeAnimations.remove(slot);
        if (playing != null) {
            invalidateNotifications(playing);
            stopPlaybackSounds(playing);
        }
        if (playing == null || fadeSeconds <= 0f) {
            return;
        }
        playing.targetWeight = 0f;
        playing.fadeSpeed = 1f / fadeSeconds;
        playing.retired = true;
        activeAnimations.put("controller-retired/" + ++retiredControllerSequence, playing);
    }

    private String controllerSlotKey(String controllerName, String animationName) {
        String controller = normalizeControllerName(controllerName);
        return "controller/" + controller.length() + "/" + controller + "/" + resolveAnimationName(animationName);
    }

    public PlayingAnimation playControllerFunctionAnimation(String controllerName, String animationName, YsmAnimationClip.LoopMode loopMode, float speed, float fadeSeconds) {
        String controller = normalizeControllerName(controllerName);
        String slot = "controller-function/" + controller.length() + "/" + controller + "/" + resolveAnimationName(animationName);
        String previous = controllerFunctionAnimations.get(controller);
        if (controllerFunctionReloads.remove(controller)) {
            if (previous != null) {
                retireSlot(previous, fadeSeconds);
            }
            previous = null;
        }
        if (previous != null && !previous.equals(slot))
            fadeOutSlot(previous, fadeSeconds);
        PlayingAnimation playing = playInSlot(slot, animationName, loopMode, speed, fadeSeconds, controller);
        if (playing != null)
            controllerFunctionAnimations.put(controller, slot);
        if (playing != null) {
            playing.controllerName = controller;
            controllerPoseLayers.computeIfAbsent(controller, ignored -> new YsmControllerPoseLayer());
            armControllerPoseLayers.computeIfAbsent(controller, ignored -> new YsmControllerPoseLayer());
        }
        return playing;
    }

    public void clearControllerFunctionAnimation(String controllerName, float fadeSeconds) {
        String controller = normalizeControllerName(controllerName);
        controllerFunctionReloads.remove(controller);
        String previous = controllerFunctionAnimations.remove(controller);
        if (previous != null)
            retireSlot(previous, fadeSeconds);
    }

    public PlayingAnimation getControllerFunctionAnimation(String controllerName) {
        String slot = controllerFunctionAnimations.get(normalizeControllerName(controllerName));
        return slot == null ? null : activeAnimations.get(slot);
    }

    public void setControllerFunctionTransitionLength(String controllerName, float seconds) {
        if (Float.isFinite(seconds) && seconds >= 0f) {
            controllerFunctionTransitionLengths.put(normalizeControllerName(controllerName), seconds);
        }
    }

    public float controllerFunctionTransitionLength(String controllerName) {
        return controllerFunctionTransitionLengths.getOrDefault(normalizeControllerName(controllerName), 0f);
    }

    public void clearControllerFunctionTransitions() {
        controllerFunctionTransitionLengths.clear();
        controllerFunctionReloads.clear();
    }

    public void requestControllerFunctionReload(String controllerName) {
        controllerFunctionReloads.add(normalizeControllerName(controllerName));
    }

    public YsmAnimationClip.LoopMode loopModeFor(String animationName, YsmAnimationClip.LoopMode fallback) {
        YsmAnimationClip clip = clips.get(resolveAnimationName(animationName));
        if (clip == null || clip.loopMode == null)
            return fallback;
        return clip.loopMode;
    }

    public void stop(String name) {
        String normalized = resolveAnimationName(name);
        PlayingAnimation removed = activeAnimations.remove(normalized);
        invalidateNotifications(removed);
        stopPlaybackSounds(removed);
        if (removed != null && runtime != null && runtime.owner() != null && runtime.owner().isHost
                && !runtime.isPreviewRuntime() && !runtime.isSubEntityRuntime() && !defaultClips.containsValue(removed.clip)) {
            runtime.functions().recordOwnerEvent(YsmSyncMessage.EventKind.STOP, List.of((float) syncClipIndex(removed.clip)));
        }
        controllerFunctionAnimations.values().removeIf(normalized::equals);
    }

    public void fadeOut(String name, float fadeSeconds) {
        fadeOutSlot(resolveAnimationName(name), fadeSeconds);
    }

    private void fadeOutSlot(String slot, float fadeSeconds) {
        PlayingAnimation animation = activeAnimations.get(slot);
        if (animation == null)
            return;
        invalidateNotifications(animation);
        if (fadeSeconds <= 0f) {
            activeAnimations.remove(slot);
            stopPlaybackSounds(animation);
            controllerFunctionAnimations.values().removeIf(slot::equals);
            return;
        }
        animation.targetWeight = 0f;
        animation.fadeSpeed = 1f / fadeSeconds;
    }

    public boolean isFinishedOnce(String name) {
        PlayingAnimation animation = activeAnimations.get(resolveAnimationName(name));
        return animation != null
                && animation.loopMode == YsmAnimationClip.LoopMode.ONCE
                && animation.clip.length > 0f
                && animation.time >= animation.clip.length;
    }

    public void update(LivingEntityRenderState state, LivingEntity entity) {
        update(state, entity, false);
    }

    public void update(LivingEntityRenderState state, LivingEntity entity, boolean firstPerson) {
        Avatar.MolangContext context = runtime.owner().getMolangContext();
        int previousCameraType = context == null ? 2 : context.ysm_camera_type;
        runtime.boneQueries().beginFrame(YsmRenderFrameClock.currentFrame());
        try (var frameBudget = context == null ? null
                : context.executionBudget().beginFrame(YsmRenderFrameClock.currentFrame());
             var boneQueries = runtime.boneQueries().beginEvaluation()) {
            updateFrame(state, entity, firstPerson);
            runtime.boneQueries().captureOutput(firstPerson);
            if (!firstPerson && context != null && !runtime.isSubEntityRuntime()
                    && runtime.owner().cosmeticContext == null) {
                var head = runtime.getPart("Head");
                if (head != null) {
                    // Track after authored poses, without feeding this offset back into bone queries.
                    // Rendered X/Y axes invert the model-degree animation channels.
                    head.addAnimRot(-context.ysm_head_pitch, -context.ysm_head_yaw, 0);
                }
            }
        } catch (MolangExecutionBudget.Exceeded exhausted) {
            runtime.particles().discardPending();
            if (runtime.owner().cosmeticAnimations != null) {
                runtime.owner().cosmeticAnimations.discardPending();
            }
            runtime.functions().discardDeferredCalls();
            long frame = YsmRenderFrameClock.currentFrame();
            if (lastBudgetFailureFrame != frame) {
                lastBudgetFailureFrame = frame;
                if (runtime.encryptedSource()) {
                    FiguraMod.LOGGER.warn("Encrypted YSM operation failed: runtime/molang-budget");
                } else {
                    FiguraMod.LOGGER.warn("YSM execution budget exceeded in {}", runtime.getModelKey());
                }
            }
        } finally {
            if (context != null) {
                context.ysm_camera_type = previousCameraType;
            }
        }
    }

    private void updateFrame(LivingEntityRenderState state, LivingEntity entity, boolean firstPerson) {
        Avatar.MolangContext renderContext = runtime.owner().getMolangContext();
        currentEntity = runtime.isSubEntityRuntime() && renderContext != null ? renderContext.entity : entity;
        if (renderContext != null) {
            renderContext.setYsmRenderTime(state == null ? Float.NaN : state.ageInTicks,
                    currentEntity == null ? 0 : currentEntity.tickCount);
            renderContext.updateYsmPositionContext(currentEntity);
            renderContext.updateYsmMovementContext(state == null ? Float.NaN : state.walkAnimationSpeed);
            top.aihmc.omnimorph.model.ysm.YsmHandQuery.capture(renderContext);
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (renderContext != null) {
            renderContext.setYsmCameraContext(currentEntity, minecraft.player, minecraft.getCameraEntity(),
                    minecraft.options.getCameraType(), !YsmParticleRuntime.isPreview(runtime.owner().renderMode));
        }
        boolean firstPersonActive = !runtime.isPreviewRuntime() && !runtime.isSubEntityRuntime() && (firstPerson || (entity != null && entity == minecraft.player
                && minecraft.options.getCameraType().isFirstPerson()));
        YsmAnimationFrameClock.Step step = frameClock.step(YsmRenderFrameClock.currentFrame(),
                state == null ? Float.NaN : state.ageInTicks);
        float deltaTime = step.deltaSeconds();
        updateYsmHeadRotation(state, entity);
        if (observerControlled) {
            ExpressionEvaluator<?> observerEvaluator = evaluatorForEvents();
            if (step.advance()) {
                advanceAnimationTimeline(deltaTime, observerEvaluator);
                advanceControllerPoseLayers(deltaTime);
                controllerRuntime.advanceRemoteTime(deltaTime);
            }
            sampleAnimations(observerEvaluator, firstPerson);
            return;
        }
        if (renderContext != null && state != null) {
            long renderFrame = YsmRenderFrameClock.currentFrame();
            if (YsmParticleRuntime.isPreview(runtime.owner().renderMode)) {
                renderContext.filters.update(YsmFilterRuntime.Domain.PREVIEW, state.ageInTicks,
                        renderFrame, renderContext.executionBudget());
            } else {
                renderContext.filters.update(YsmFilterRuntime.Domain.BODY, state.ageInTicks,
                        renderFrame, renderContext.executionBudget());
                if (firstPersonActive) {
                    renderContext.filters.update(YsmFilterRuntime.Domain.FIRST_PERSON, state.ageInTicks,
                            renderFrame, renderContext.executionBudget());
                }
            }
        }

        if (step.advance()) {
            if (!firstPersonActive) {
                controllerRuntime.deactivateFirstPersonControllers(evaluatorForEvents());
            }
            controllerSounds.prune();
            // 1. Run Native State Machine if enabled
            if (runtime.isSubEntityRuntime()) {
                updateSubEntityAnimations(currentEntity);
            } else if (nativeStateMachineEnabled) {
                updateNativeStateMachine(state, entity);
            } else {
                // Smoothly fade out any running native animations
                for (PlayingAnimation anim : activeAnimations.values()) {
                    if (anim.isNative) {
                        anim.targetWeight = 0f;
                    }
                }
            }

            if (!runtime.isPreviewRuntime() && !runtime.isSubEntityRuntime()) {
                updateNativeHandAnimations(entity);
            }

            advanceAnimationTimeline(deltaTime, evaluatorForEvents());
        }

        Avatar.MolangContext molangContext = runtime.owner().getMolangContext();
        ExpressionEvaluator<?> evaluator = molangContext != null ? ExpressionEvaluator.evaluator(molangContext) : ExpressionEvaluator.evaluator(runtime.owner());
        if (!controllerInitialHiddenResolved) {
            collectControllerBaseHiddenBones(evaluator);
            collectControllerInitialHiddenBones(evaluator);
            controllerInitialHiddenResolved = true;
        }
        if (step.advance()) {
            runtime.functions().beginControllerFrame();
            advanceControllerPoseLayers(deltaTime);
            controllerRuntime.update(evaluator, deltaTime, firstPersonActive);
            runtime.functions().runUnboundControllerEvents(evaluator, controllers.values());

        }

        sampleAnimations(evaluator, firstPerson);

        if (!runtime.isPreviewRuntime() && !runtime.isSubEntityRuntime()) {
            applyNativeHandPose(state, entity, firstPerson);
        }

    }

    private void advanceControllerPoseLayers(float deltaTime) {
        for (YsmControllerPoseLayer layer : controllerPoseLayers.values()) {
            layer.advance(Math.max(0f, deltaTime));
        }
        for (YsmControllerPoseLayer layer : armControllerPoseLayers.values()) {
            layer.advance(Math.max(0f, deltaTime));
        }
    }

    private void advanceAnimationTimeline(float deltaTime, ExpressionEvaluator<?> evaluator) {
        Iterator<Map.Entry<String, PlayingAnimation>> it = activeAnimations.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<String, PlayingAnimation> entry = it.next();
            PlayingAnimation anim = entry.getValue();

            // Handle transition weight
            if (anim.isNative || anim.fadeSpeed > 0f) {
                float fadeSpeed = anim.fadeSpeed > 0f ? anim.fadeSpeed : 5f;
                if (anim.weight < anim.targetWeight) {
                    anim.weight = Math.min(anim.targetWeight, anim.weight + deltaTime * fadeSpeed);
                } else if (anim.weight > anim.targetWeight) {
                    anim.weight = Math.max(anim.targetWeight, anim.weight - deltaTime * fadeSpeed);
                }

                // If animation faded out, remove it to save CPU
                if (anim.weight <= 0f && anim.targetWeight <= 0f) {
                    stopPlaybackSounds(anim);
                    it.remove();
                    continue;
                }
            }

            // Retired states keep their last sampled pose and never advance or emit events.
            if (anim.retired) {
                continue;
            }

            // Advance time
            anim.previousTime = anim.time;
            if (Float.isFinite(anim.sampledTime)) {
                anim.time = anim.sampledTime;
                anim.sampledTime = Float.NaN;
            } else {
                anim.time += deltaTime * anim.speed;
            }
            anim.eventWindowEnd = anim.time;
            // The reference keeps this flag after loop wrapping or hold-time clamping, until playback resets.
            anim.completedCycle |= anim.time > Math.max(0f, anim.clip.length);
            if (anim.clip.length > 0)
                advanceAnimationTime(anim);
            if (anim.loopMode == YsmAnimationClip.LoopMode.ONCE && anim.clip.length > 0f
                    && (anim.controllerName != null ? anim.hasCompletedCycle() : anim.time >= anim.clip.length)) {
                anim.targetWeight = 0f;
                if (anim.fadeSpeed <= 0f)
                    anim.fadeSpeed = 5f;
            }
            if (!observerControlled) {
                dispatchEvents(anim, evaluator);
                if (!anim.endNotificationDispatched && !anim.loop
                        && anim.loopMode != YsmAnimationClip.LoopMode.LOOP && anim.clip.length > 0f
                        && (anim.speed >= 0 ? anim.eventWindowEnd >= anim.clip.length : anim.eventWindowEnd <= 0f)) {
                    anim.endNotificationDispatched = true;
                    notifyAnimationEvent(anim, "end", "", anim.speed >= 0 ? anim.clip.length : 0f);
                }
            }
        }
    }

    private void sampleAnimations(ExpressionEvaluator<?> evaluator, boolean firstPerson) {
        try (var queries = runtime.boneQueries().beginEvaluation()) {
            sampleAnimationPoses(evaluator, firstPerson);
            runtime.boneQueries().captureOutput(firstPerson);
        }
    }

    private void sampleAnimationPoses(ExpressionEvaluator<?> evaluator, boolean firstPerson) {
        Avatar.MolangContext molangContext = evaluator != null && evaluator.entity() instanceof Avatar.MolangContext context
                ? context : null;
        // 3. Reset and apply animations to parts
        for (YsmModelPart part : runtime.uniqueParts(firstPerson)) {
            part.resetAnimPose();
            part.setVisible(part.defaultVisibleRaw());
        }
        applyBaseHiddenDefaults();

        Map<String, Map<String, YsmBonePose>> controllerPoses = new LinkedHashMap<>();
        Map<String, YsmBonePose> nativePoses = new LinkedHashMap<>();
        List<DirectPose> directPoses = new ArrayList<>();
        for (PlayingAnimation anim : activeAnimations.values()) {
            if (anim.retired) {
                continue;
            }
            if (!firstPerson && isFirstPersonController(anim.controllerName)) {
                continue;
            }
            if (anim.weight <= 0f || anim.paused) continue;

            float t = anim.time;
            boolean baseAnimation = isBaseAnimation(anim.clip.name);
            Object previousAnimCtrl = null;
            boolean touchedAnimCtrl = false;
            if (baseAnimation && molangContext != null) {
                int animCtrl = StringPool.computeIfAbsent("anim_ctrl");
                previousAnimCtrl = molangContext.variables.getScoped(animCtrl);
                molangContext.variables.setScoped(animCtrl, 1f);
                touchedAnimCtrl = true;
            }
            float previousAnimTime = molangContext == null ? 0f : molangContext.anim_time;
            if (molangContext != null) {
                molangContext.anim_time = t;
            }
            try (YsmControllerRuntime.ContextScope contextScope = controllerRuntime.openAnimationContext(
                    anim.controllerName, evaluator, t, anim.clip.length, firstPerson)) {
                float clipWeight = anim.clip.evaluateBlendWeight(evaluator);
                float effectiveWeight = anim.weight * clipWeight;
                if (!Float.isFinite(effectiveWeight) || effectiveWeight <= 0f) {
                    continue;
                }
                YsmAnimationClip sampledClip = anim.clip;
                if (firstPerson && defaultClips.containsValue(sampledClip)) {
                    sampledClip = defaultArmClips.get(sampledClip.name);
                    if (sampledClip == null) {
                        continue;
                    }
                }
                for (YsmBoneAnimation boneAnim : sampledClip.boneAnimations.values()) {
                    List<YsmModelPart> targetParts = runtime.getAnimationParts(boneAnim.boneName, firstPerson);
                    if (targetParts.isEmpty()) continue;

                    YsmBonePose pose = YsmAnimationSampler.sampleBone(boneAnim, t, evaluator);
                    float[] posVal = pose.position();
                    float[] rotVal = pose.rotation();
                    float[] scaleVal = pose.scale();

                    if (anim.controllerName != null) {
                        if (baseAnimation && isBaseHiddenBone(boneAnim.boneName)
                                && scaleVal != null && scaleMagnitude(scaleVal) < 0.0001f) {
                            pose = new YsmBonePose(posVal, rotVal, null);
                        }
                        Map<String, YsmBonePose> layer = controllerPoses.computeIfAbsent(anim.controllerName,
                                ignored -> new LinkedHashMap<>());
                        layer.put(boneAnim.boneName, YsmBonePose.accumulate(layer.get(boneAnim.boneName), pose, effectiveWeight));
                        continue;
                    }

                    if (anim.isNative) {
                        nativePoses.put(boneAnim.boneName,
                                YsmBonePose.accumulate(nativePoses.get(boneAnim.boneName), pose, effectiveWeight));
                    } else {
                        directPoses.add(new DirectPose(targetParts, pose, effectiveWeight, baseAnimation,
                                baseAnimation && normalizeName(anim.clip.name).contains("pre_parallel")));
                    }
                }
            } finally {
                if (molangContext != null) {
                    molangContext.anim_time = previousAnimTime;
                }
                if (touchedAnimCtrl) {
                    int animCtrl = StringPool.computeIfAbsent("anim_ctrl");
                    if (previousAnimCtrl == null) {
                        molangContext.variables.setScoped(animCtrl, 0f);
                    } else {
                        molangContext.variables.setScoped(animCtrl, previousAnimCtrl);
                    }
                }
            }
        }

        // Match the reference: setup controllers precede the native movement slot.
        for (DirectPose pose : directPoses) {
            if (pose.beforeMain()) {
                applyDirectPose(pose);
            }
        }
        applyControllerLayers(controllerPoses, firstPerson, true);
        for (var bone : nativePoses.entrySet()) {
            for (YsmModelPart part : runtime.getAnimationParts(bone.getKey(), firstPerson)) {
                applyControllerPose(part, bone.getValue());
            }
        }
        for (DirectPose pose : directPoses) {
            if (!pose.beforeMain()) {
                applyDirectPose(pose);
            }
        }
        applyControllerLayers(controllerPoses, firstPerson, false);
    }

    private record DirectPose(List<YsmModelPart> parts, YsmBonePose pose, float weight,
                              boolean baseAnimation, boolean beforeMain) {
    }

    private void applyDirectPose(DirectPose contribution) {
        float[] posVal = contribution.pose().position();
        float[] rotVal = contribution.pose().rotation();
        float[] scaleVal = contribution.pose().scale();
        float effectiveWeight = contribution.weight();
        boolean baseAnimation = contribution.baseAnimation();
        for (YsmModelPart part : contribution.parts()) {
            if (posVal != null)
                part.addAnimPos(posVal[0] * effectiveWeight, posVal[1] * effectiveWeight, posVal[2] * effectiveWeight);
            if (rotVal != null)
                part.addAnimRot(rotVal[0] * effectiveWeight, rotVal[1] * effectiveWeight, rotVal[2] * effectiveWeight);
            if (scaleVal != null) {
                boolean baseHidden = isBaseHiddenBone(part.getName());
                float scaleMagnitude = scaleMagnitude(scaleVal);
                if (baseHidden && baseAnimation && scaleMagnitude < 0.0001f)
                    continue;
                if (scaleMagnitude < 0.0001f) {
                    part.setVisible(false);
                    part.mulAnimScale(
                            1.0 + (scaleVal[0] - 1.0) * effectiveWeight,
                            1.0 + (scaleVal[1] - 1.0) * effectiveWeight,
                            1.0 + (scaleVal[2] - 1.0) * effectiveWeight
                    );
                    continue;
                }
                if (baseAnimation && part.defaultVisibleRaw()) {
                    part.setVisible(true);
                }
                if (!baseAnimation && scaleMagnitude > 0.0001f)
                    part.setVisible(true);
                // Scale lerps with weight: final_scale = 1 + (scale_val - 1) * weight
                double sx = 1.0 + (scaleVal[0] - 1.0) * effectiveWeight;
                double sy = 1.0 + (scaleVal[1] - 1.0) * effectiveWeight;
                double sz = 1.0 + (scaleVal[2] - 1.0) * effectiveWeight;
                part.mulAnimScale(sx, sy, sz);
            }
        }
    }

    private void applyControllerLayers(Map<String, Map<String, YsmBonePose>> controllerPoses,
                                       boolean firstPerson, boolean beforeMain) {
        Map<String, YsmControllerPoseLayer> poseLayers = firstPerson ? armControllerPoseLayers : controllerPoseLayers;
        // Later stages replace the channels they write, so apply in stage order rather than first-play order.
        List<Map.Entry<String, YsmControllerPoseLayer>> orderedLayers = new ArrayList<>(poseLayers.entrySet());
        orderedLayers.sort(Comparator.comparingInt(entry -> YsmControllerSlot.executionOrder(entry.getKey())));
        for (var entry : orderedLayers) {
            if ((YsmControllerSlot.executionOrder(entry.getKey()) < 20) != beforeMain) {
                continue;
            }
            if (!firstPerson && isFirstPersonController(entry.getKey())) {
                continue;
            }
            Map<String, YsmBonePose> pose = entry.getValue().compose(controllerPoses.getOrDefault(entry.getKey(), Map.of()));
            boolean declaredController = controllers.keySet().stream()
                    .anyMatch(name -> normalizeControllerName(name).equals(entry.getKey()));
            for (var bone : pose.entrySet()) {
                for (YsmModelPart part : runtime.getAnimationParts(bone.getKey(), firstPerson)) {
                    applyControllerPose(part, bone.getValue(), declaredController);
                }
            }
        }
    }

    private boolean isFirstPersonController(String controllerName) {
        return controllerName != null && YsmControllerSlot.fromName(controllerName) == YsmControllerSlot.FIRST_PERSON_ARM;
    }

    private static void applyControllerPose(YsmModelPart part, YsmBonePose pose) {
        applyControllerPose(part, pose, true);
    }

    private static void applyControllerPose(YsmModelPart part, YsmBonePose pose, boolean replaceChannels) {
        // A controller replaces the channels it writes; only clips within that controller accumulate.
        // Synthesized native fallback slots retain their existing additive composition.
        float[] position = pose.position();
        float[] rotation = pose.rotation();
        float[] scale = pose.scale();
        if (position != null) {
            if (replaceChannels) {
                part.animPosRaw().set(position[0], position[1], position[2]);
            } else {
                part.addAnimPos(position[0], position[1], position[2]);
            }
        }
        if (rotation != null) {
            if (replaceChannels) {
                part.animRotRaw().set(rotation[0], rotation[1], rotation[2]);
            } else {
                part.addAnimRot(rotation[0], rotation[1], rotation[2]);
            }
        }
        if (scale != null) {
            float magnitude = Math.max(Math.abs(scale[0]), Math.max(Math.abs(scale[1]), Math.abs(scale[2])));
            part.setVisible(magnitude >= 0.0001f);
            if (replaceChannels) {
                part.animScaleRaw().set(scale[0], scale[1], scale[2]);
            } else {
                part.mulAnimScale(scale[0], scale[1], scale[2]);
            }
        }
    }

    private void updateYsmHeadRotation(LivingEntityRenderState state, LivingEntity entity) {
        Avatar.MolangContext context = runtime.owner().getMolangContext();
        if (context == null)
            return;

        float relativeYaw = entity == null
                ? 0f
                : Mth.wrapDegrees(entity.yHeadRot - entity.yBodyRot);
        float rawPitch = entity == null ? 0f : entity.getXRot();
        relativeYaw = readFloat(state, "yRot", relativeYaw);
        rawPitch = readFloat(state, "xRot", rawPitch);

        // YSM exposes the clamped relative head angles with GeckoLib's inverted axes.
        context.ysm_head_yaw = -Mth.clamp(Mth.wrapDegrees(relativeYaw), -85f, 85f);
        context.ysm_head_pitch = -rawPitch;
    }

    private void collectBaseHiddenBones(YsmAnimationClip clip, ExpressionEvaluator<?> evaluator) {
        if (clip == null)
            return;
        for (YsmBoneAnimation animation : clip.boneAnimations.values()) {
            if (animation.scale != null && isZeroScaleChannel(animation.scale, evaluator))
                baseHiddenBones.add(normalizeBone(animation.boneName));
        }
    }

    private ExpressionEvaluator<?> evaluatorForEvents() {
        Avatar.MolangContext molangContext = runtime.owner().getMolangContext();
        return molangContext != null ? ExpressionEvaluator.evaluator(molangContext) : ExpressionEvaluator.evaluator(runtime.owner());
    }

    private void dispatchEvents(PlayingAnimation animation, ExpressionEvaluator<?> evaluator) {
        if (animation.clip.events.isEmpty() || animation.weight <= 0f)
            return;
        try (YsmControllerRuntime.ContextScope contextScope = controllerRuntime.openAnimationContext(
                animation.controllerName, evaluator, animation.time, animation.clip.length);
             var effectScope = runtime == null ? null : runtime.functions().beginClientEffects(animation)) {
            if (currentEntity != null && !animation.pendingInitialSounds.isEmpty()) {
                for (YsmAnimationEvent event : animation.pendingInitialSounds) {
                    handleEvent(event, evaluator, animation.controllerName);
                }
                animation.pendingInitialSounds.clear();
            }
            for (YsmAnimationEvent event : YsmAnimationEventWindow.crossed(animation.clip,
                    animation.previousTime, animation.eventWindowEnd, animation.loop,
                    animation.initialEventsDispatched)) {
                notifyAnimationEvent(animation, event.type(), event.params().getOrDefault("marker", ""), event.time());
                handleEvent(event, evaluator, animation.controllerName);
            }
            if (runtime != null) {
                runtime.functions().flushDeferred(animation, evaluator);
            }
        }
        animation.initialEventsDispatched = true;
    }

    private void dispatchInitialEvents(PlayingAnimation animation, ExpressionEvaluator<?> evaluator) {
        if (animation == null || animation.initialEventsDispatched || animation.clip.events.isEmpty() || evaluator == null)
            return;
        try (YsmControllerRuntime.ContextScope contextScope = controllerRuntime.openAnimationContext(
                animation.controllerName, evaluator, animation.time, animation.clip.length);
             var effectScope = runtime == null ? null : runtime.functions().beginClientEffects(animation)) {
            for (YsmAnimationEvent event : animation.clip.events) {
                if (event.time() == 0f) {
                    notifyAnimationEvent(animation, event.type(), event.params().getOrDefault("marker", ""), event.time());
                    if (currentEntity == null && "sound".equals(event.type())) {
                        // Assembly precedes entity binding; retain only audio for the first live update.
                        animation.pendingInitialSounds.add(event);
                    } else {
                        handleEvent(event, evaluator, animation.controllerName);
                    }
                }
            }
            if (runtime != null) {
                runtime.functions().flushDeferred(animation, evaluator);
            }
        }
        animation.initialEventsDispatched = true;
    }

    private void invalidateNotifications(PlayingAnimation animation) {
        if (animation != null) {
            animation.notificationGeneration = ++notificationSequence;
            animation.endNotificationDispatched = true;
            animation.notificationsActive = false;
        }
    }

    private void notifyAnimationEvent(PlayingAnimation animation, String type, String marker, float time) {
        if (!animation.notificationsActive || runtime == null || runtime.isClosed()
                || runtime.isSubEntityRuntime() || observerControlled
                || runtime.owner().getYsmRuntime() != runtime || runtime.owner().cosmeticAnimations == null) {
            return;
        }
        if (animation.notificationGeneration == 0) {
            animation.notificationGeneration = ++notificationSequence;
        }
        long generation = animation.notificationGeneration;
        top.aihmc.omnimorph.lua.api.CosmeticAnimationsAPI.offer(runtime.owner(), animation.clip.name,
                type, marker, time, generation,
                () -> !runtime.isClosed() && !observerControlled && runtime.owner().getYsmRuntime() == runtime
                        && animation.notificationGeneration == generation);
    }

    private void advanceAnimationTime(PlayingAnimation animation) {
        if (animation.loopMode == YsmAnimationClip.LoopMode.LOOP || animation.loop) {
            animation.time %= animation.clip.length;
            if (animation.time < 0f) {
                animation.time += animation.clip.length;
            }
            return;
        }
        animation.time = Math.min(animation.time, animation.clip.length);
    }

    private void handleEvent(YsmAnimationEvent event, ExpressionEvaluator<?> evaluator, String controllerName) {
        if ("sound".equals(event.type())) {
            playOwnedSound(controllerName, event);
            return;
        }
        if ("particle".equals(event.type())) {
            spawnEventParticle(event);
            return;
        }
        if (!"timeline".equals(event.type()) || event.expressions().isEmpty() || evaluator == null)
            return;
        for (Expression expression : event.expressions()) {
            try {
                evaluator.eval(expression);
            } catch (MolangExecutionBudget.Exceeded exhausted) {
                throw exhausted;
            } catch (Exception ignored) {
            }
        }
    }

    private void stopPlaybackSounds(PlayingAnimation animation) {
        if (animation == null) {
            return;
        }
        animation.pendingInitialSounds.clear();
        animation.sounds.forEach(LuaSound::stop);
        animation.sounds.clear();
        if (runtime != null) {
            runtime.sounds().stopPlayback(animation);
        }
    }

    private void playOwnedSound(String controllerName, YsmAnimationEvent event) {
        if (runtime.isPreviewRuntime()) {
            return;
        }
        LuaSound sound = playEventSound(event);
        if (sound != null && sound.getHandle() != null
                && runtime.functions().clientEffectOwner() instanceof PlayingAnimation animation) {
            animation.sounds.removeIf(voice -> voice.getHandle() == null || voice.getHandle().isStopped());
            animation.sounds.add(sound);
        }
        if (controllerName != null && sound != null && sound.getHandle() != null) {
            // A channel may still be loading its buffer; isPlaying() is not a completion signal.
            controllerSounds.add(normalizeControllerName(controllerName),
                    () -> sound.getHandle() == null || sound.getHandle().isStopped(), sound::stop);
        }
    }

    private LuaSound playEventSound(YsmAnimationEvent event) {
        String id = event.value();
        if (id == null || id.isBlank() || currentEntity == null)
            return null;
        try {
            Map<String, String> params = event.params();
            double x = currentEntity.getX() + param(params, "offset_x", "offsetX", "x");
            double y = currentEntity.getY() + param(params, "offset_y", "offsetY", "y");
            double z = currentEntity.getZ() + param(params, "offset_z", "offsetZ", "z");
            double volume = param(params, 1d, "volume", "vol");
            double pitch = param(params, 1d, "pitch");
            boolean loop = boolParam(params, "loop", "looping");
            FiguraVec3 pos = FiguraVec3.of(x, y, z);
            LuaSound sound = runtime.sounds().createSound(id);
            if (sound == null) {
                return null;
            }
            sound.setPos(pos, null, null);
            sound.setEngineVolume((float) Math.max(0.001d, Math.min(1000d, volume)));
            sound.setPitch((float) Math.max(0.001d, Math.min(1000d, pitch)));
            sound.setLoop(loop);
            return sound.play();
        } catch (Exception ignored) {
            return null;
        }
    }

    private void spawnEventParticle(YsmAnimationEvent event) {
        String id = event.value();
        if (id == null || id.isBlank() || currentEntity == null)
            return;
        try {
            Map<String, String> params = event.params();
            double x = currentEntity.getX() + param(params, "offset_x", "offsetX", "x");
            double y = currentEntity.getY() + currentEntity.getBbHeight() * 0.5d + param(params, "offset_y", "offsetY", "y");
            double z = currentEntity.getZ() + param(params, "offset_z", "offsetZ", "z");
            double vx = param(params, "velocity_x", "velocityX", "speed_x", "speedX", "vx");
            double vy = param(params, "velocity_y", "velocityY", "speed_y", "speedY", "vy");
            double vz = param(params, "velocity_z", "velocityZ", "speed_z", "speedZ", "vz");
            new ParticleAPI(runtime.owner()).newParticle(
                    id,
                    x,
                    y,
                    z,
                    vx,
                    vy,
                    vz
            );
        } catch (Exception ignored) {
        }
    }

    private static double param(Map<String, String> params, String... keys) {
        return param(params, 0d, keys);
    }

    private static double param(Map<String, String> params, double fallback, String... keys) {
        if (params == null)
            return fallback;
        for (String key : keys) {
            String value = params.get(key);
            if (value == null || value.isBlank())
                continue;
            try {
                return Double.parseDouble(value);
            } catch (NumberFormatException ignored) {
            }
        }
        return fallback;
    }

    private static boolean boolParam(Map<String, String> params, String... keys) {
        if (params == null)
            return false;
        for (String key : keys) {
            String value = params.get(key);
            if (value != null && !value.isBlank())
                return Boolean.parseBoolean(value);
        }
        return false;
    }

    private void collectControllerInitialHiddenBones(ExpressionEvaluator<?> evaluator) {
        for (YsmAnimationController controller : controllers.values()) {
            YsmControllerState state = controller.initialStateDefinition();
            if (state == null)
                continue;
            for (YsmControllerAnimationRef ref : state.animations()) {
                if (ref.animation() == null || ref.animation().isBlank())
                    continue;
                if (ref.condition() != null) {
                    if (evaluator == null)
                        continue;
                    try {
                        if (!evaluator.evalAsBoolean(ref.condition()))
                            continue;
                    } catch (MolangExecutionBudget.Exceeded exhausted) {
                        throw exhausted;
                    } catch (Exception ignored) {
                        continue;
                    }
                }
                collectBaseHiddenBones(clips.get(normalizeName(ref.animation())), evaluator);
            }
        }
    }

    private void collectControllerBaseHiddenBones(ExpressionEvaluator<?> evaluator) {
        for (Map.Entry<String, YsmAnimationClip> entry : clips.entrySet()) {
            String normalized = normalizeName(entry.getKey());
            if (isBaseAnimation(normalized))
                collectBaseHiddenBones(entry.getValue(), evaluator);
        }
    }

    private void applyBaseHiddenDefaults() {
        for (String boneName : baseHiddenBones) {
            for (YsmModelPart part : runtime.getAnimationParts(boneName)) {
                part.setDefaultVisible(false);
                part.setVisible(false);
            }
        }
    }

    private boolean isBaseHiddenBone(String boneName) {
        return baseHiddenBones.contains(normalizeBone(boneName));
    }

    private boolean isZeroScaleChannel(YsmAnimationChannel channel, ExpressionEvaluator<?> evaluator) {
        if (channel == null)
            return false;
        if (hasDynamicScaleExpression(channel))
            return false;
        if (channel.keyframes.isEmpty())
            return scaleMagnitude(YsmAnimationSampler.sampleStatic(channel.staticValue, channel.staticExpressions, evaluator, new float[]{1f, 1f, 1f})) < 0.0001f;
        for (YsmKeyframe keyframe : channel.keyframes) {
            if (scaleMagnitude(YsmAnimationSampler.samplePost(keyframe, evaluator, new float[]{1f, 1f, 1f})) >= 0.0001f)
                return false;
            if (scaleMagnitude(YsmAnimationSampler.samplePre(keyframe, evaluator, new float[]{1f, 1f, 1f})) >= 0.0001f)
                return false;
        }
        return true;
    }

    private boolean hasDynamicScaleExpression(YsmAnimationChannel channel) {
        if (hasExpression(channel.staticExpressions))
            return true;
        for (YsmKeyframe keyframe : channel.keyframes) {
            if (hasExpression(keyframe.expressions))
                return true;
            if (hasExpression(keyframe.preExpressions))
                return true;
        }
        return false;
    }

    private static boolean hasExpression(Expression[] expressions) {
        if (expressions == null)
            return false;
        for (Expression expression : expressions) {
            if (expression != null)
                return true;
        }
        return false;
    }

    private static float scaleMagnitude(float[] value) {
        if (value == null)
            return 0f;
        float max = 0f;
        for (float v : value)
            max = Math.max(max, Math.abs(v));
        return max;
    }

    private boolean isBaseAnimation(String name) {
        return isBaseAnimation(name, null);
    }

    private boolean isBaseAnimation(String name, YsmAnimationClip clip) {
        String normalized = normalizeName(name);
        String simple = simpleName(normalized);
        boolean slot = isParallelSlot(normalized, "pre_parallel")
                || isParallelSlot(simple, "pre_parallel")
                || isParallelSlot(normalized, "parallel")
                || isParallelSlot(simple, "parallel");
        return slot && (clip == null || !isEmptyClip(clip));
    }

    private boolean isParallelSlot(String name, String prefix) {
        if (name == null || !name.startsWith(prefix))
            return false;
        int index = prefix.length();
        if (index < name.length() && name.charAt(index) == '_')
            index++;
        if (name.length() != index + 1)
            return false;
        char slot = name.charAt(index);
        return slot >= '0' && slot <= '7';
    }

    private boolean isEmptyClip(YsmAnimationClip clip) {
        return clip == null || clip.boneAnimations.isEmpty() && clip.events.isEmpty();
    }

    private String normalizeBone(String boneName) {
        return boneName == null ? "" : boneName.toLowerCase(Locale.US);
    }

    private void applyNativeHandPose(LivingEntityRenderState state, LivingEntity entity, boolean firstPerson) {
        if (entity == null || entity.getPose() == Pose.SLEEPING)
            return;

        HumanoidArm mainArm = entity.getMainArm();
        boolean mainLeft = mainArm == HumanoidArm.LEFT;
        boolean usingItem = entity.isUsingItem();
        InteractionHand usedHand = usingItem ? entity.getUsedItemHand() : null;
        InteractionHand swingingHand = entity.swinging ? entity.swingingArm : InteractionHand.MAIN_HAND;
        float attackProgress = Math.max(readFloat(state, "attackAnim", 0f), entity.getAttackAnim(0f));
        float swingAmount = attackProgress > 0f ? (float) Math.sin(Math.sqrt(attackProgress) * Math.PI) : 0f;

        // Native YSM locators already encode their own authored hand pose.
        // Only use the vanilla-like fallback when that side has no locator.
        if (!hasNativeHandAnimation("right") && !hasHandLocator(false)) {
            applyNativeArmPose(false, itemForArm(entity, false, mainLeft),
                    usingItem && isHandForArm(usedHand, false, mainLeft),
                    isHandForArm(swingingHand, false, mainLeft), swingAmount, firstPerson);
        }
        if (!hasNativeHandAnimation("left") && !hasHandLocator(true)) {
            applyNativeArmPose(true, itemForArm(entity, true, mainLeft),
                    usingItem && isHandForArm(usedHand, true, mainLeft),
                    isHandForArm(swingingHand, true, mainLeft), swingAmount, firstPerson);
        }
    }

    private boolean hasNativeHandAnimation(String slot) {
        return nativeHandAnimations.containsKey(slot) || nativeHoldAnimations.containsKey(slot);
    }

    private boolean hasHandLocator(boolean left) {
        // Fallback hand bones are registered as attachment points too. They
        // still require the vanilla-like pose when the model has no authored
        // locator; only an actual YSM locator suppresses that fallback.
        var point = runtime.getHandAttachmentPoint(left);
        return point != null && point.locator() != null;
    }

    /**
     * Device input belongs to the local player model only. Overridable so callers can supply the
     * ownership decision without a live client.
     */
    protected boolean ownsDeviceHandInput(LivingEntity entity) {
        return runtime != null && YsmHandQuery.ownsDeviceInput(runtime.owner(), entity);
    }

    /**
     * Resolves native hand actions from the same local input pulses that Molang queries read.
     * Sparkle's use and swing predicates accept a pending device pulse before vanilla mirrors it,
     * so selection must not read raw entity flags here.
     */
    private void updateNativeHandAnimations(LivingEntity entity) {
        if (entity == null || entity.getPose() == Pose.SLEEPING) {
            clearNativeHandAnimations();
            wasSwinging = false;
            lastSwingPulseSequence = 0;
            return;
        }

        YsmHandActionState hands = YsmInputState.device().hands();
        boolean localOwner = ownsDeviceHandInput(entity);
        HumanoidArm mainArm = entity.getMainArm();

        InteractionHand usedHand = hands.usedHand(entity, localOwner);
        boolean usingItem = hands.using(entity, usedHand, localOwner);
        String useSlot = null;
        boolean keepHoldWhileUsing = false;
        if (usingItem) {
            boolean left = handSlotIsLeft(usedHand, mainArm);
            useSlot = left ? "left" : "right";
            ItemStack item = entity.getItemInHand(usedHand);
            String[] names = useAnimationNames(entity, left, item);
            if (isKineticSpear(item) && firstHandAnimation(names) == null) {
                // Legacy :spear clips describe a trident throw. Keep the authored grip
                // when this model has no kinetic charge pose; the item handles its use orientation.
                keepHoldWhileUsing = true;
                syncNativeHandAnimation(useSlot, false);
            } else {
                syncNativeHandAnimation(useSlot, true, names);
                sampleKineticChargeAnimation(useSlot, entity, item);
            }
        }

        // The use slot follows the swing slot in YSM. A swing pulse must not replace
        // the drawing pose of the same hand while the item is still being used.
        InteractionHand swingingHand = hands.swingingHand(entity, localOwner);
        boolean anySwinging = hands.anySwinging(entity, localOwner);
        boolean swingUsesActiveHand = usingItem && swingingHand == usedHand;
        boolean newSwingPulse = localOwner && hands.swingPulseActive(entity, true)
                && hands.swingSequence() != lastSwingPulseSequence;
        lastSwingPulseSequence = hands.swingSequence();
        if ((anySwinging && !wasSwinging || newSwingPulse) && !swingUsesActiveHand) {
            boolean left = handSlotIsLeft(swingingHand, mainArm);
            ItemStack item = entity.getItemInHand(swingingHand);
            String[] names = swingAnimationNames(swingingHand, item);
            playNativeHandOnce(left ? "left" : "right", names);
            sampleKineticSwingAnimation(left ? "left" : "right", entity, item);
        }
        String swingSlot = anySwinging && !swingUsesActiveHand
                ? (handSlotIsLeft(swingingHand, mainArm) ? "left" : "right")
                : null;
        for (String slot : new String[]{"left", "right"}) {
            if (!slot.equals(useSlot) && !slot.equals(swingSlot)) {
                syncNativeHandAnimation(slot, false);
            }
        }
        wasSwinging = anySwinging;

        // Sparkle registers hold_offhand and hold_mainhand as separate controllers. Each one
        // pauses only while its own hand swings or uses an item; the other hand keeps holding.
        boolean pulseSwinging = localOwner && hands.swingPulseActive(entity, true);
        InteractionHand pulseHand = pulseSwinging ? hands.swingingHand(entity, true) : null;
        for (InteractionHand hand : InteractionHand.values()) {
            boolean swingOwnHand = (entity.swinging && entity.swingingArm == hand) || pulseHand == hand;
            boolean ownHandActs = swingOwnHand
                    || (usingItem && usedHand == hand && !keepHoldWhileUsing);
            syncHoldingAnimation(entity, hand, handSlotIsLeft(hand, mainArm), ownHandActs);
        }
    }

    private static boolean handSlotIsLeft(InteractionHand hand, HumanoidArm mainArm) {
        return hand == InteractionHand.MAIN_HAND
                ? mainArm == HumanoidArm.LEFT
                : mainArm != HumanoidArm.LEFT;
    }

    private void syncHoldingAnimation(LivingEntity entity, InteractionHand hand, boolean left, boolean paused) {
        String slot = left ? "left" : "right";
        String current = nativeHoldAnimations.get(slot);
        PlayingAnimation currentHold = current == null ? null : activeAnimations.get(current);
        if (paused) {
            // PAUSE keeps the selected hold and its clock; the reference predicate does not
            // re-select a clip until its hand is free again.
            if (currentHold != null) {
                currentHold.paused = true;
            }
            return;
        }
        if (currentHold != null) {
            currentHold.paused = false;
        }
        ItemStack item = entity.getItemInHand(hand);
        String prefix = hand == InteractionHand.MAIN_HAND ? "hold_mainhand" : "hold_offhand";
        if (item.isEmpty()) {
            syncNativeHoldAnimation(slot, prefix + ":empty");
            return;
        }
        if (item.is(Items.CROSSBOW) && CrossbowItem.isCharged(item)) {
            syncNativeHoldAnimation(slot, prefix + ":charged_crossbow", prefix);
            return;
        }
        if (hand == InteractionHand.MAIN_HAND && item.is(Items.FISHING_ROD)
                && entity instanceof Player player && player.fishing != null && !player.fishing.isRemoved()) {
            syncNativeHoldAnimation(slot, prefix + ":fishing", prefix);
            return;
        }
        if (isKineticSpear(item)) {
            List<String> names = new ArrayList<>();
            if (entity.isPassenger())
                names.add("lance_riding_idle");
            names.add("lance_stand");
            names.add(prefix + ":lance");
            names.addAll(Arrays.asList(conditionalAnimationNames(prefix, item, false)));
            syncNativeHoldAnimation(slot, names.toArray(String[]::new));
            return;
        }
        syncNativeHoldAnimation(slot, conditionalAnimationNames(prefix, item, false));
    }

    private String[] useAnimationNames(LivingEntity entity, boolean left, ItemStack item) {
        String prefix = left ? "use_offhand" : "use_mainhand";
        List<String> names = new ArrayList<>();
        if (isKineticSpear(item)) {
            if (entity.isPassenger())
                names.add("lance_riding_charge");
            if (entity.isFallFlying())
                names.add("lance_fall_flying_charge");
            names.add("lance_charge");
        }
        names.addAll(Arrays.asList(conditionalAnimationNames(prefix, item, true)));
        // Kinetic weapons need a dedicated charge pose. Generic legacy actions
        // can point the authored grip in an unrelated direction.
        if (!isKineticSpear(item)) {
            names.add(prefix);
        }
        return names.toArray(String[]::new);
    }

    private void sampleKineticChargeAnimation(String slot, LivingEntity entity, ItemStack item) {
        if (!isKineticSpear(item))
            return;
        String name = nativeHandAnimations.get(slot);
        PlayingAnimation animation = name == null ? null : activeAnimations.get(name);
        KineticWeapon weapon = item.get(DataComponents.KINETIC_WEAPON);
        if (animation == null || weapon == null || animation.clip.length <= 0f)
            return;
        float duration = Math.max(1f, weapon.computeDamageUseDuration());
        animation.sampledTime = Math.clamp(entity.getTicksUsingItem() / duration, 0f, 1f) * animation.clip.length;
    }

    private void sampleKineticSwingAnimation(String slot, LivingEntity entity, ItemStack item) {
        if (!isKineticSpear(item))
            return;
        String name = nativeHandAnimations.get(slot);
        PlayingAnimation animation = name == null ? null : activeAnimations.get(name);
        if (animation != null && animation.clip.length > 0f)
            animation.sampledTime = Math.clamp(entity.getAttackAnim(0f), 0f, 1f) * animation.clip.length;
    }

    private static boolean isKineticSpear(ItemStack item) {
        return item != null && !item.isEmpty()
                && item.getUseAnimation() == ItemUseAnimation.SPEAR
                && item.get(DataComponents.KINETIC_WEAPON) != null;
    }

    private String[] swingAnimationNames(InteractionHand hand, ItemStack item) {
        String prefix = hand == InteractionHand.MAIN_HAND ? "swing" : "swing_offhand";
        List<String> names = new ArrayList<>();
        if (hand == InteractionHand.MAIN_HAND && isKineticSpear(item)) {
            if (currentEntity instanceof LivingEntity living && living.isFallFlying())
                names.add("lance_lunge");
            names.add("lance_jab");
            names.add("swing:lance");
        }
        names.addAll(Arrays.asList(conditionalAnimationNames(prefix, item, false)));
        if (hand == InteractionHand.MAIN_HAND && item.isEmpty())
            names.add("attack_empty");
        names.add(hand == InteractionHand.MAIN_HAND ? "swing_hand" : "swing_offhand");
        if (hand == InteractionHand.MAIN_HAND)
            names.add("attack_empty");
        return names.toArray(String[]::new);
    }

    /**
     * Resolves the same declarative item-condition names accepted by Sparkle's
     * hold, use and swing predicates. The model selects behavior by declaring
     * clips, never by Figura knowing a model or item mod in advance.
     */
    private String[] conditionalAnimationNames(String prefix, ItemStack item, boolean use) {
        List<String> names = new ArrayList<>();
        if (item == null || item.isEmpty()) {
            names.add(prefix + ":empty");
            names.add(prefix);
            return names.toArray(String[]::new);
        }

        String id = BuiltInRegistries.ITEM.getKey(item.getItem()).toString();
        List<String> itemTypes = itemTypes(item, use);
        if (!use && isWeaponItem(item))
            addTypeCandidates(names, prefix, itemTypes);
        names.add(prefix + "$" + id);
        addMatchingTagCandidates(names, prefix, item);
        addTypeCandidates(names, prefix, itemTypes);
        return names.toArray(String[]::new);
    }

    private void addMatchingTagCandidates(List<String> names, String prefix, ItemStack item) {
        String start = literalAnimationName(prefix + "#");
        for (String animation : clips.keySet()) {
            String normalized = literalAnimationName(animation);
            if (!normalized.startsWith(start))
                continue;
            Identifier tagId = identifier(normalized.substring(start.length()));
            if (tagId != null && item.is(TagKey.create(Registries.ITEM, tagId)))
                names.add(animation);
        }
    }

    private static Identifier identifier(String value) {
        try {
            return Identifier.parse(value);
        } catch (Exception ignored) {
            return null;
        }
    }

    private static void addTypeCandidates(List<String> names, String prefix, List<String> itemTypes) {
        for (String itemType : itemTypes)
            names.add(prefix + ":" + itemType);
    }

    private static boolean isWeaponItem(ItemStack item) {
        return item.is(Items.TRIDENT) || item.is(Items.MACE)
                || item.getUseAnimation() == ItemUseAnimation.SPEAR;
    }

    private static List<String> itemTypes(ItemStack item, boolean use) {
        List<String> result = new ArrayList<>();
        if (item.is(Items.TRIDENT)) {
            result.add(use ? "trident" : "spear");
            if (use)
                result.add("spear");
        } else if (item.getUseAnimation() == ItemUseAnimation.SPEAR) {
            result.add(use ? "lance" : "spear");
        } else if (item.is(Items.MACE)) {
            result.add("mace");
        } else {
            String kind = itemKind(item);
            if (!kind.equals("item") && !result.contains(kind))
                result.add(kind);
            if (kind.equals("shovel"))
                result.add("spade");
        }
        String useAnimation = item.getUseAnimation().name().toLowerCase(Locale.US);
        if (!result.contains(useAnimation) && !useAnimation.equals("none")
                && !(use && useAnimation.equals("spear"))) {
            result.add(useAnimation);
        }
        return result;
    }

    /** Use actions: the reference use predicate passes LOOP to its format validator. */
    private void syncNativeHandAnimation(String slot, boolean active, String... names) {
        String selected = active ? firstHandAnimation(names) : null;
        syncNativeSlotAnimation(nativeHandAnimations, slot, selected, YsmAnimationClip.LoopMode.LOOP);
    }

    /** Holds: LOOP, except a declared *:lance hold, which the reference holds on its last frame. */
    private void syncNativeHoldAnimation(String slot, String... names) {
        String selected = firstHandAnimation(names);
        YsmAnimationClip.LoopMode predicateLoop = selected != null && selected.endsWith(":lance")
                ? YsmAnimationClip.LoopMode.HOLD_ON_LAST_FRAME
                : YsmAnimationClip.LoopMode.LOOP;
        syncNativeSlotAnimation(nativeHoldAnimations, slot, selected, predicateLoop);
    }

    /**
     * Reference AnimationFormatValidator: a modern format or a clip from the built-in default
     * model keeps its authored loop; otherwise the predicate's loop replaces it.
     */
    private YsmAnimationClip.LoopMode nativeHandLoopMode(String selected, YsmAnimationClip clip,
                                                          YsmAnimationClip.LoopMode predicateLoop) {
        boolean defaultClip = clips.get(selected) == null;
        if (clip != null && (modelFormatVersion >= AUTHORED_LOOP_FORMAT_VERSION || defaultClip)) {
            return clip.loopMode;
        }
        return predicateLoop;
    }

    private void syncNativeSlotAnimation(Map<String, String> owners, String slot, String selected,
                                         YsmAnimationClip.LoopMode predicateLoop) {
        String previous = owners.get(slot);
        if (Objects.equals(previous, selected))
            return;
        if (previous != null)
            fadeOut(previous, 0.1f);
        if (selected == null) {
            owners.remove(slot);
        } else {
            YsmAnimationClip clip = nativeClip(selected);
            // This is a new hand action, even if the previous instance is still fading.
            // Reusing that instance would resume the previous draw's clock and events.
            fadeOut(selected, 0f);
            play(selected, nativeHandLoopMode(selected, clip, predicateLoop), 1f, 0.1f);
            owners.put(slot, selected);
        }
    }

    private void playNativeHandOnce(String slot, String... names) {
        String selected = firstHandAnimation(names);
        if (selected == null)
            return;
        String previous = nativeHandAnimations.put(slot, selected);
        if (previous != null && !previous.equals(selected))
            fadeOut(previous, 0.05f);
        play(selected, YsmAnimationClip.LoopMode.ONCE, 1f, 0f);
    }

    private String firstHandAnimation(String... names) {
        boolean authoredEmpty = false;
        for (String name : names) {
            String normalized = resolveAnimationName(name);
            YsmAnimationClip clip = clips.get(normalized);
            if (!isEmptyClip(clip)) {
                return normalized;
            }
            authoredEmpty |= clip != null;
        }
        if (!authoredEmpty && nativeStateMachineEnabled) {
            for (String name : names) {
                String normalized = resolveAnimationName(name);
                if (isNativeAnimationEnabled(normalized) && !isEmptyClip(defaultClips.get(normalized))) {
                    return normalized;
                }
            }
        }
        return null;
    }

    private void clearNativeHandAnimations() {
        for (String animation : nativeHandAnimations.values())
            fadeOut(animation, 0.1f);
        nativeHandAnimations.clear();
        for (String animation : nativeHoldAnimations.values())
            fadeOut(animation, 0.1f);
        nativeHoldAnimations.clear();
    }

    private static String itemKind(ItemStack item) {
        if (item == null || item.isEmpty())
            return "empty";
        if (item.is(Items.TRIDENT))
            return "trident";
        if (item.is(Items.MACE))
            return "mace";
        if (item.getUseAnimation() == ItemUseAnimation.SPEAR)
            return "spear";
        if (item.is(Items.SHIELD))
            return "shield";
        if (item.is(Items.CROSSBOW))
            return "crossbow";
        if (item.is(Items.BOW))
            return "bow";
        if (item.is(Items.FISHING_ROD))
            return "fishing_rod";
        if (item.is(Items.SPLASH_POTION) || item.is(Items.LINGERING_POTION))
            return "throwable_potion";
        if (item.is(ItemTags.SWORDS))
            return "sword";
        if (item.is(ItemTags.AXES))
            return "axe";
        if (item.is(ItemTags.PICKAXES))
            return "pickaxe";
        if (item.is(ItemTags.SHOVELS))
            return "shovel";
        if (item.is(ItemTags.HOES))
            return "hoe";
        return "item";
    }

    private void applyNativeArmPose(boolean left, ItemStack item, boolean using, boolean swinging,
                                   float swingAmount, boolean firstPerson) {
        if ((item == null || item.isEmpty()) && !using && !(swinging && swingAmount > 0f))
            return;

        double side = left ? 1d : -1d;
        double x = item != null && !item.isEmpty() ? -12d : 0d;
        double y = 0d;
        double z = item != null && !item.isEmpty() ? side * 4d : 0d;

        if (using) {
            x -= 52d;
            y += side * 8d;
            z += side * 8d;
        }

        if (swinging && swingAmount > 0f) {
            x -= 65d * swingAmount;
            y += side * 12d * swingAmount;
            z += side * 18d * swingAmount;
        }

        YsmBoneRole role = left ? YsmBoneRole.LEFT_HAND : YsmBoneRole.RIGHT_HAND;
        for (YsmModelPart part : runtime.uniqueParts(firstPerson)) {
            YsmBoneRole partRole = firstPerson && runtime.hasArmModel()
                    ? runtime.armRoleOf(part.getName()) : runtime.roleOf(part.getName());
            if (partRole == role)
                part.addAnimRot(x, y, z);
        }
    }

    private static ItemStack itemForArm(LivingEntity entity, boolean left, boolean mainLeft) {
        boolean main = left == mainLeft;
        return main ? entity.getMainHandItem() : entity.getOffhandItem();
    }

    private static boolean isHandForArm(InteractionHand hand, boolean left, boolean mainLeft) {
        if (hand == null)
            return false;
        boolean main = left == mainLeft;
        return main ? hand == InteractionHand.MAIN_HAND : hand == InteractionHand.OFF_HAND;
    }

    private void updateSubEntityAnimations(Entity entity) {
        if (entity == null) {
            return;
        }
        if ("projectile".equals(runtime.getKind())) {
            syncSubEntityAnimation("main", projectileEnvironment(entity));
        } else if ("vehicle".equals(runtime.getKind())) {
            String environment = "fly";
            if (entity.isInWater()) {
                environment = "water";
            } else if (entity.onGround()) {
                environment = "ground";
            }
            syncSubEntityAnimation("main", environment);
            syncSubEntityAnimation("move", entity.getDeltaMovement().horizontalDistance() > 0.05d ? "forward" : "idle");
            syncSubEntityAnimation("ride", entity.getPassengers().isEmpty() ? "not_ride" : "has_ride");
        }
    }

    private static String projectileEnvironment(Entity entity) {
        if (entity.isInWater()) {
            return "water";
        }
        if (entity.isOnFire()) {
            return "fire";
        }
        if (entity instanceof AbstractArrowAccessor arrow && arrow.figura$isInGround()) {
            return "ground";
        }
        return "air";
    }

    private void syncSubEntityAnimation(String slot, String animationName) {
        String controller = runtime.getKind() + "." + slot;
        // An explicitly declared controller owns this slot; native defaults only fill missing slots.
        boolean explicit = controllers.keySet().stream()
                .filter(name -> !functionEventControllers.contains(name)).map(this::normalizeControllerName)
                .anyMatch(name -> name.equals(controller) || name.endsWith("." + controller));
        String selected = explicit ? null : firstHandAnimation(animationName);
        String previous = subEntityAnimations.get(controller);
        if (Objects.equals(previous, selected)) {
            return;
        }
        if (previous != null) {
            stopControllerSounds(controller);
            beginControllerPoseTransition(controller, YsmBlendTransition.linear(0.1f), false);
            retireControllerAnimation(controller, previous, 0f);
        }
        if (selected == null) {
            subEntityAnimations.remove(controller);
        } else {
            subEntityAnimations.put(controller, selected);
            playControllerAnimation(controller, selected, null, 1f, 0f, evaluatorForEvents());
        }
    }

    private void updateNativeStateMachine(LivingEntityRenderState state, LivingEntity entity) {
        if (state == null) return;

        boolean isPassenger = false;
        boolean isSleeping = false;
        boolean isFallFlying = false;
        boolean isSwimming = false;
        boolean isSneaking = false;
        boolean isSprinting = false;
        boolean isOnGround = true;
        boolean isInWater = false;
        float speed = 0f;

        try {
            speed = state.walkAnimationSpeed;
        } catch (Throwable ignored) {}

        if (entity != null) {
            isPassenger = entity.isPassenger();
            isSleeping = entity.getPose() == Pose.SLEEPING;
            isFallFlying = entity.isFallFlying();
            isSwimming = entity.isVisuallySwimming() || entity.getPose() == Pose.SWIMMING;
            isSneaking = entity.isCrouching() || entity.getPose() == Pose.CROUCHING;
            isSprinting = entity.isSprinting();
            isOnGround = entity.onGround();
            isInWater = entity.isInWater();
        } else {
            isPassenger = readBoolean(state, "isPassenger", false);
            isSleeping = readBoolean(state, "isSleeping", false);
            isFallFlying = readBoolean(state, "isFallFlying", false);
            isSprinting = readBoolean(state, "isSprinting", false);
            isSneaking = readBoolean(state, "isCrouching", readBoolean(state, "isSneaking", readBoolean(state, "crouching", false)));
            isSwimming = readFloat(state, "swimAmount", 0f) > 0.1f || readBoolean(state, "isSwimming", readBoolean(state, "swimming", false));
        }

        String idealState = selectState(isPassenger, isSleeping, isFallFlying, isSwimming, isSneaking, isSprinting, isOnGround, isInWater, speed);
        if (shouldInterruptActions(idealState))
            runtime.actions().stopAll();
        for (Map.Entry<String, PlayingAnimation> entry : activeAnimations.entrySet()) {
            PlayingAnimation anim = entry.getValue();
            if (anim.isNative && !entry.getKey().equals(idealState))
                anim.targetWeight = 0f;
        }
        if (!idealState.isBlank()) {
            PlayingAnimation anim = activeAnimations.get(idealState);
            if (anim == null) {
                anim = new PlayingAnimation(nativeClip(idealState), true);
                activeAnimations.put(idealState, anim);
            }
            anim.targetWeight = 1f;
        }
    }

    private String selectState(boolean isPassenger, boolean isSleeping, boolean isFallFlying, boolean isSwimming, boolean isSneaking,
                               boolean isSprinting, boolean isOnGround, boolean isInWater, float speed) {
        final float minSpeed = 0.05f;
        if (isPassenger)
            return firstEnabled("sit", "ride");
        if (isSleeping)
            return firstEnabled("sleep");
        if (isFallFlying)
            return firstEnabled("elytra_fly", "fly");
        if (isSwimming)
            return firstEnabled(speed > minSpeed ? "swim" : "swim_stand", "swim", "swim_stand");
        if (isInWater && !isOnGround)
            return firstEnabled("swim_stand", "swim");
        if (!isOnGround)
            return firstEnabled("fall", "jump", "fly");
        if (isSneaking)
            return firstEnabled(speed > minSpeed ? "sneak" : "sneaking", "sneaking", "sneak");
        if (isSprinting && speed > minSpeed)
            return firstEnabled("run", "walk");
        if (speed > minSpeed)
            return firstEnabled("walk", "run");
        return firstEnabled("idle");
    }

    private static boolean shouldInterruptActions(String state) {
        return state != null && !state.isBlank() && !"idle".equals(state);
    }

    private String firstEnabled(String... names) {
        for (String name : names) {
            String normalized = normalizeName(name);
            if (nativeClip(normalized) != null && !disabledNativeAnimations.contains(normalized))
                return normalized;
        }
        return "";
    }

    private boolean readBoolean(LivingEntityRenderState state, String name, boolean fallback) {
        try {
            Field field = stateField(state, name);
            return field == null ? fallback : field.getBoolean(state);
        } catch (Throwable ignored) {
            return fallback;
        }
    }

    private float readFloat(LivingEntityRenderState state, String name, float fallback) {
        try {
            Field field = stateField(state, name);
            return field == null ? fallback : field.getFloat(state);
        } catch (Throwable ignored) {
            return fallback;
        }
    }

    private static Field stateField(LivingEntityRenderState state, String name) {
        Class<?> type = state.getClass();
        // Render-state fields differ across entity types. Cache absent fields as well
        // as present ones instead of creating NoSuchFieldException every frame.
        return STATE_FIELDS.get(type).computeIfAbsent(name, key -> {
            try {
                return Optional.of(type.getField(key));
            } catch (NoSuchFieldException | SecurityException missing) {
                return Optional.empty();
            }
        }).orElse(null);
    }

    private String normalizeName(String name) {
        if (name == null)
            return "";
        String normalized = name;
        if (normalized.startsWith("animation."))
            normalized = normalized.substring("animation.".length());
        int colon = normalized.indexOf(':');
        if (colon >= 0 && colon + 1 < normalized.length()) {
            // A hand predicate's colon separates its item condition, not a namespace.
            // Stripping it aliases hold/use clips to the same item name (e.g. bow).
            if (!isConditionalHandName(normalized)) {
                normalized = normalized.substring(colon + 1);
            }
        }
        if (!isConditionalHandName(normalized)) {
            normalized = stripReferenceSuffix(normalized);
        }
        return normalized.toLowerCase(Locale.US);
    }

    private boolean isConditionalHandName(String name) {
        int condition = name.length();
        for (char separator : new char[]{':', '$', '#'}) {
            int index = name.indexOf(separator);
            if (index >= 0) {
                condition = Math.min(condition, index);
            }
        }
        if (condition == name.length()) {
            return false;
        }
        return switch (simpleName(name.substring(0, condition))) {
            case "hold_mainhand", "hold_offhand", "use_mainhand", "use_offhand", "swing", "swing_offhand" -> true;
            default -> false;
        };
    }

    private String literalAnimationName(String name) {
        if (name == null)
            return "";
        String normalized = name;
        if (normalized.startsWith("animation."))
            normalized = normalized.substring("animation.".length());
        return (isConditionalHandName(normalized) ? normalized : stripReferenceSuffix(normalized)).toLowerCase(Locale.US);
    }

    private String resolveAnimationName(String name) {
        String literal = literalAnimationName(name);
        if (clips.containsKey(literal))
            return literal;
        String normalized = normalizeName(name);
        if (clips.containsKey(normalized))
            return normalized;
        String simple = simpleName(normalized);
        String parallel = parallelSlotAnimationName(simple);
        if (clips.containsKey(parallel))
            return parallel;
        if (clips.containsKey(simple))
            return simple;
        parallel = parallelSlotAnimationName(normalized);
        if (clips.containsKey(parallel))
            return parallel;
        return normalized;
    }

    private String parallelSlotAnimationName(String name) {
        if (name == null || name.isBlank())
            return "";
        String value = name.toLowerCase(Locale.US);
        int pre = value.lastIndexOf("pre_parallel_");
        if (pre >= 0 && pre + "pre_parallel_".length() < value.length()) {
            char slot = value.charAt(pre + "pre_parallel_".length());
            if (slot >= '0' && slot <= '7')
                return "pre_parallel" + slot;
        }
        int parallel = value.lastIndexOf("parallel_");
        if (parallel >= 0 && parallel + "parallel_".length() < value.length()) {
            char slot = value.charAt(parallel + "parallel_".length());
            if (slot >= '0' && slot <= '7')
                return "parallel" + slot;
        }
        return value;
    }

    private String baseSlotKey(String name) {
        String normalized = normalizeName(name);
        String simple = simpleName(normalized);
        String slot = parallelSlotAnimationName(simple);
        if (!slot.equals(simple))
            return slot;
        slot = parallelSlotAnimationName(normalized);
        return slot.isBlank() ? normalized : slot;
    }

    private String normalizeControllerName(String name) {
        if (name == null || name.isBlank())
            return "ctrl";
        return name.toLowerCase(Locale.US);
    }

    private String simpleName(String name) {
        if (name == null)
            return "";
        String normalized = name.toLowerCase(Locale.US);
        int slash = Math.max(normalized.lastIndexOf('/'), normalized.lastIndexOf('\\'));
        if (slash >= 0 && slash + 1 < normalized.length())
            normalized = normalized.substring(slash + 1);
        int dot = normalized.lastIndexOf('.');
        if (dot >= 0 && dot + 1 < normalized.length())
            normalized = normalized.substring(dot + 1);
        return normalized;
    }

    private String stripReferenceSuffix(String name) {
        if (name == null || name.isBlank())
            return "";
        int suffix = name.indexOf('#');
        return suffix >= 0 ? name.substring(0, suffix) : name;
    }
}
