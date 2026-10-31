package top.aihmc.omnimorph.model.ysm.animation;

import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.Minecraft;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
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
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.item.Items;
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
import top.aihmc.omnimorph.model.ysm.YsmPlayerActionState;
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
    private final Map<String, YsmAnimationClip> authoredArmClips = new HashMap<>();
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
    private YsmPlayerActionState.State lastActionState = YsmPlayerActionState.State.NONE;
    private String lastNativeState = "";
    private final Map<String, String> handAnimationDiagnostics = new LinkedHashMap<>();
    /**
     * Reference stage orders for native clips. The main movement slot comes first; hold, swing and
     * use are separate stages the declared controller layers interleave with.
     */
    private static final int NATIVE_MAIN_STAGE = YsmControllerSlot.executionOrder("player.main");
    private static final int HOLD_OFFHAND_STAGE = YsmControllerSlot.executionOrder("player.hold_offhand");
    private static final int HOLD_MAINHAND_STAGE = YsmControllerSlot.executionOrder("player.hold_mainhand");
    private static final int SWING_STAGE = YsmControllerSlot.executionOrder("player.swing");
    private static final int USE_STAGE = YsmControllerSlot.executionOrder("player.use");
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
        /**
         * Reference stage order this clip is applied at. Native movement, native hand actions and
         * declared controller layers are merged by this order, so a later stage still wins.
         */
        public int poseOrder = NATIVE_MAIN_STAGE;
        public boolean initialEventsDispatched;
        private final java.util.List<YsmAnimationEvent> pendingInitialSounds = new java.util.ArrayList<>();
        private final List<LuaSound> sounds = new java.util.ArrayList<>();

        // For smooth transitions in native state machine
        public float targetWeight = 0f;
        public float fadeSpeed = 0f;
        public float sampledTime = Float.NaN;
        /** Reference PlayState.PAUSE: the clock keeps running but no pose is submitted. */
        public boolean paused;
        /** A declared controller slot retains its clock even while its condition is false. */
        public boolean controllerConditionActive = true;
        private boolean declaredControllerSlot;
        /**
         * Bone poses when this clip started (Sparkle BoneAnimationQueue.controllerSnapshot), read by the
         * Bedrock {@code this} keyword. Captured on the first sample, separately for body and arm parts.
         */
        private Map<String, YsmBonePose> startPose;
        private Map<String, YsmBonePose> armStartPose;
        /** Last finite pose per bone (SAMP-07, Sparkle PoseValueCache), separately for body and arm parts. */
        private final Map<String, YsmBonePose> lastValidPose = new HashMap<>();
        private final Map<String, YsmBonePose> armLastValidPose = new HashMap<>();
        private boolean retired;
        private String builtinSource;
        private boolean deferTimelineAdvance;
        private float beginningTransitionRemaining;
        private String controllerName;
        private YsmSyncMessage.Playback remoteState;
        private float remoteTimeCorrection;

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
    private boolean updatingControllerFrame;
    private final Map<String, Boolean> builtinSources = new HashMap<>();

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
        registerAnimations(clips, newClips);
    }

    public void registerArmAnimations(Map<String, YsmAnimationClip> newClips) {
        registerAnimations(authoredArmClips, newClips);
    }

    public Map<String, YsmAnimationClip> clipsForController(String controllerName) {
        return isFirstPersonController(controllerName) ? authoredArmClips : clips;
    }

    private void registerAnimations(Map<String, YsmAnimationClip> target, Map<String, YsmAnimationClip> newClips) {
        if (newClips != null) {
            for (Map.Entry<String, YsmAnimationClip> entry : newClips.entrySet()) {
                target.put(entry.getKey(), entry.getValue());
                target.putIfAbsent(literalAnimationName(entry.getKey()), entry.getValue());
                String normalized = normalizeName(entry.getKey());
                target.putIfAbsent(normalized, entry.getValue());
            }
            for (Map.Entry<String, YsmAnimationClip> entry : newClips.entrySet()) {
                String normalized = normalizeName(entry.getKey());
                target.putIfAbsent(simpleName(normalized), entry.getValue());
            }
        }
    }

    public Map<String, YsmAnimationClip> getClips() {
        return clips;
    }

    /** Keep the existing name-based wire inventory; controller identity selects the domain on restore. */
    public List<YsmAnimationClip> syncClips() {
        Map<String, YsmAnimationClip> inventory = new java.util.TreeMap<>();
        clips.values().forEach(clip -> inventory.put(clip.name, clip));
        authoredArmClips.values().forEach(clip -> inventory.putIfAbsent(clip.name, clip));
        return List.copyOf(inventory.values());
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
        return defaultExcludedNames(declared, false);
    }

    public Set<String> defaultExcludedNames(Collection<String> declared, boolean firstPerson) {
        Map<String, YsmAnimationClip> authored = firstPerson ? authoredArmClips : clips;
        Set<String> result = new HashSet<>();
        authored.forEach((name, clip) -> {
            if (!isEmptyClip(clip) || !isHandFallbackName(name)) {
                result.add(name);
            }
        });
        for (String name : declared) {
            YsmAnimationClip clip = authored.get(name);
            if (clip != null && isEmptyClip(clip) && isHandFallbackName(name)) {
                continue;
            }
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
        if (authored == null || isEmptyClip(authored) && isHandFallbackName(name)) {
            return defaultClips.getOrDefault(name, authored);
        }
        return authored;
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

    private static String canonicalController(String name) {
        return name.toLowerCase(Locale.ROOT).replaceFirst("^controller\\.animation\\.", "")
                .replaceFirst("_([0-9]+)$", "$1");
    }

    public boolean usesBuiltinController(String source) {
        String canonical = canonicalController(source);
        for (YsmAnimationController controller : controllers.values()) {
            if (!functionEventControllers.contains(controller.name())
                    && ownsBuiltinSource(controller.name(), canonical)) {
                YsmControllerState state = controller.states().get(controllerRuntime.currentState(controller.name()));
                if (state == null || !state.isBuiltin()) {
                    return false;
                }
            }
        }
        return true;
    }

    private static boolean ownsBuiltinSource(String controllerName, String source) {
        String candidate = canonicalController(controllerName);
        if (candidate.equals(source)) {
            return true;
        }
        // Numbered controllers share their core hand stage. Pre/post hooks are separate stages.
        return Set.of("player.use", "player.swing", "player.hold_mainhand", "player.hold_offhand").contains(source)
                && candidate.matches(java.util.regex.Pattern.quote(source) + "[0-9]+");
    }

    public List<String> handAnimationDiagnostics() {
        return List.copyOf(handAnimationDiagnostics.values());
    }

    private String selectHandAnimation(String action, ItemStack item, String... names) {
        String selected = firstHandAnimation(names);
        if (selected == null && !item.isEmpty() && !handAnimationDiagnostics.containsKey(action)) {
            String message = "NO_HAND_ANIMATION:" + action + ":" + BuiltInRegistries.ITEM.getKey(item.getItem())
                    + ":candidates=" + Arrays.toString(names);
            handAnimationDiagnostics.put(action, message);
            FiguraMod.LOGGER.warn("[YSM hand] model={} {}", runtime == null ? "detached" : runtime.contentHash(), message);
        }
        return selected;
    }

    private void reconcileBuiltinSources() {
        boolean restartBase = false;
        for (YsmAnimationController controller : controllers.values()) {
            String source = canonicalController(controller.name());
            boolean builtin = usesBuiltinController(source);
            Boolean previous = builtinSources.put(source, builtin);
            restartBase |= builtin && !Boolean.TRUE.equals(previous);
        }
        activeAnimations.values().removeIf(animation -> {
            if (animation.builtinSource == null || usesBuiltinController(animation.builtinSource)) {
                return false;
            }
            invalidateNotifications(animation);
            stopPlaybackSounds(animation);
            return true;
        });
        nativeHandAnimations.values().removeIf(name -> !activeAnimations.containsKey(name));
        nativeHoldAnimations.values().removeIf(name -> !activeAnimations.containsKey(name));
        if (restartBase) {
            startBaseAnimations();
        }
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
            result.add(new YsmSyncMessage.Playback(result.size(),
                    clipIndex.stream().map(clip -> clip.name).toList().indexOf(playing.clip.name), controller,
                    playing.time, playing.speed, playing.weight, playing.targetWeight,
                    (playing.loop ? YsmAnimationClip.LoopMode.LOOP : playing.loopMode).ordinal(),
                    playing.fadeSpeed, flags));
        }
        return List.copyOf(result);
    }

    public void validateRemotePlaybacks(List<YsmSyncMessage.Playback> values, List<YsmAnimationClip> clips, List<String> controllers) {
        buildRemotePlaybacks(values, clips, controllers);
    }

    public void applyRemotePlaybacks(List<YsmSyncMessage.Playback> values, List<YsmAnimationClip> clips, List<String> controllers) {
        applyRemotePlaybacks(values, clips, controllers, Set.of());
    }

    public void applyRemotePlaybacks(List<YsmSyncMessage.Playback> values, List<YsmAnimationClip> clips,
                                     List<String> controllers, Set<String> changedControllers) {
        Map<String, PlayingAnimation> restored = buildRemotePlaybacks(values, clips, controllers);
        if (!observerControlled) {
            controllerSounds.close();
            runtime.sounds().stopAll();
        }
        activeAnimations.forEach((slot, previous) -> {
            PlayingAnimation next = restored.get(slot);
            if (observerControlled && next != null && previous.clip == next.clip
                    && (previous.controllerName == null || !changedControllers.contains(previous.controllerName))) {
                if (previous.playbackGeneration == 0 && Objects.equals(previous.remoteState, next.remoteState)) {
                    // A repeated owner sample must not undo the observer's frame-by-frame advancement.
                    restored.put(slot, previous);
                } else if (continuesRemotePlayback(previous, next)) {
                    float correction = next.time - previous.time;
                    if (next.loop && next.clip.length > 0f) {
                        float length = next.clip.length;
                        correction -= Math.round(correction / length) * length;
                    }
                    previous.remoteTimeCorrection = correction;
                    previous.remoteState = next.remoteState;
                    previous.speed = next.speed;
                    previous.weight = next.weight;
                    previous.targetWeight = next.targetWeight;
                    previous.fadeSpeed = next.fadeSpeed;
                    previous.controllerConditionActive = next.controllerConditionActive;
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
        Set<String> restoredControllers = new HashSet<>();
        for (PlayingAnimation playing : restored.values()) {
            if (playing.controllerName != null) {
                restoredControllers.add(playing.controllerName);
            }
        }
        controllerPoseLayers.keySet().retainAll(restoredControllers);
        armControllerPoseLayers.keySet().retainAll(restoredControllers);
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

    private static boolean continuesRemotePlayback(PlayingAnimation previous, PlayingAnimation next) {
        if (previous.remoteState == null || previous.retired || next.retired
                || previous.playbackGeneration != 0 || previous.loopMode != next.loopMode
                || previous.remoteState.flags() != next.remoteState.flags()) {
            return false;
        }
        // A non-looping clip whose owner time moves backwards represents a new play request.
        return next.loop || next.time >= previous.remoteState.time();
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
            String controllerName = value.controller() < 0 ? null : controllers.get(value.controller());
            if (isFirstPersonController(controllerName)) {
                clip = authoredArmClips.get(resolveAnimationName(clip.name));
                if (clip == null) {
                    throw new IllegalArgumentException("Missing first-person playback resource");
                }
            }
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
            playing.declaredControllerSlot = controllerName != null && (value.flags() & 6) == 0;
            playing.controllerConditionActive = value.weight() > 0f;
            // Legacy publishers wrote the authored ONCE mode for their forced-loop setup layers.
            if (controllerName == null && !playing.isNative && isBaseAnimation(clip.name)) {
                playing.loop = true;
                playing.loopMode = YsmAnimationClip.LoopMode.LOOP;
            }
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
            String source = "player." + canonicalController(simpleName(normalized));
            boolean explicit = controllers.keySet().stream().anyMatch(name -> canonicalController(name).equals(source));
            if (!isBaseAnimation(normalized, entry.getValue()) || !usesBuiltinController(source)
                    || controlledClips.contains(entry.getValue()) && !explicit)
                continue;
            // Explicit controller references own playback and gating; legacy autoplay is only a fallback.
            String activeKey = baseSlotKey(normalized);
            collectBaseHiddenBones(entry.getValue(), null);
            if (activeAnimations.containsKey(activeKey))
                continue;
            PlayingAnimation playing = new PlayingAnimation(entry.getValue(), false);
            playing.builtinSource = source;
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
        return syncClips().stream().map(value -> value.name).toList().indexOf(clip.name);
    }

    private PlayingAnimation playInSlot(String slot, String name, YsmAnimationClip.LoopMode loopMode,
                                         float speed, float fadeSeconds, String controllerName) {
        return playInSlot(slot, name, loopMode, speed, fadeSeconds, controllerName, null, 1f, false);
    }

    private PlayingAnimation playInSlot(String slot, String name, YsmAnimationClip.LoopMode loopMode,
                                         float speed, float fadeSeconds, String controllerName,
                                         ExpressionEvaluator<?> initialEvaluator, float initialWeight,
                                         boolean declaredControllerSlot) {
        String normalized = resolveAnimationName(name);
        YsmAnimationClip clip = isFirstPersonController(controllerName)
                ? authoredArmClips.get(normalized) : nativeClip(normalized);
        if (clip == null) return null;

        boolean created = !activeAnimations.containsKey(slot);
        PlayingAnimation playing = activeAnimations.computeIfAbsent(slot, k -> new PlayingAnimation(clip, false));
        if (created) {
            playing.deferTimelineAdvance = updatingControllerFrame;
            playing.beginningTransitionRemaining = controllerName == null ? 0f : Math.max(0f, fadeSeconds);
        }
        // A controller refresh keeps both its clock and owned voices, including pending async playback.
        // New slots, direct play and reactivation invalidate the previous sound/notification generation.
        if (created || controllerName == null || !playing.notificationsActive) {
            stopPlaybackSounds(playing);
            playing.playbackGeneration++;
            playing.notificationGeneration = ++notificationSequence;
            playing.endNotificationDispatched = false;
            playing.notificationsActive = true;
        }
        playing.controllerName = controllerName;
        playing.loopMode = loopMode == null ? clip.loopMode : loopMode;
        playing.loop = playing.loopMode == YsmAnimationClip.LoopMode.LOOP;
        playing.speed = speed;
        playing.targetWeight = 1f;
        if (fadeSeconds > 0f && controllerName == null) {
            if (created)
                playing.weight = 0f;
            playing.fadeSpeed = 1f / fadeSeconds;
        } else {
            playing.weight = 1f;
            playing.fadeSpeed = 0f;
        }
        if (created && declaredControllerSlot) {
            playing.declaredControllerSlot = true;
            playing.controllerConditionActive = initialWeight > 0f;
            playing.weight = initialWeight;
            playing.targetWeight = initialWeight;
            if (!playing.controllerConditionActive) {
                playing.initialEventsDispatched = true;
            }
        }
        if (created && playing.beginningTransitionRemaining == 0f && !clip.events.isEmpty()
                && playing.controllerConditionActive)
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
        float contribution = Float.isFinite(weight) ? Math.max(0f, weight) : 0f;
        PlayingAnimation playing = playInSlot(controllerSlotKey(controllerName, animationName),
                animationName, loopMode, 1f, fadeSeconds, normalizeControllerName(controllerName), evaluator,
                contribution, true);
        if (playing != null) {
            playing.controllerName = normalizeControllerName(controllerName);
            controllerPoseLayers.computeIfAbsent(playing.controllerName, ignored -> new YsmControllerPoseLayer());
            armControllerPoseLayers.computeIfAbsent(playing.controllerName, ignored -> new YsmControllerPoseLayer());
            playing.controllerConditionActive = contribution > 0f;
            playing.targetWeight = playing.loopMode == YsmAnimationClip.LoopMode.ONCE && playing.hasCompletedCycle()
                    ? 0f : contribution;
            playing.weight = playing.targetWeight;
        }
        return playing;
    }

    public void beginControllerPoseTransition(String controllerName, YsmBlendTransition curve) {
        controllerPoseLayers.computeIfAbsent(normalizeControllerName(controllerName), ignored -> new YsmControllerPoseLayer())
                .beginTransition(curve);
        armControllerPoseLayers.computeIfAbsent(normalizeControllerName(controllerName), ignored -> new YsmControllerPoseLayer())
                .beginTransition(curve);
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
        playing.declaredControllerSlot = false;
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
        boolean created = !activeAnimations.containsKey(slot);
        if (previous != null && !previous.equals(slot)) {
            retireSlot(previous, fadeSeconds);
        }
        if (created) {
            beginControllerPoseTransition(controller, YsmBlendTransition.linear(fadeSeconds));
        }
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
        return loopModeFor(null, animationName, fallback);
    }

    public YsmAnimationClip.LoopMode loopModeFor(String controllerName, String animationName,
                                                 YsmAnimationClip.LoopMode fallback) {
        YsmAnimationClip clip = clipsForController(controllerName).get(resolveAnimationName(animationName));
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
        animation.declaredControllerSlot = false;
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

        Avatar.MolangContext molangContext = runtime.owner().getMolangContext();
        ExpressionEvaluator<?> evaluator = molangContext != null ? ExpressionEvaluator.evaluator(molangContext)
                : ExpressionEvaluator.evaluator(runtime.owner());
        if (!controllerInitialHiddenResolved) {
            collectControllerBaseHiddenBones(evaluator);
            collectControllerInitialHiddenBones(evaluator);
            controllerInitialHiddenResolved = true;
        }
        if (step.advance()) {
            runtime.functions().beginControllerFrame();
            if (!runtime.isSubEntityRuntime()) {
                advanceControllerPoseLayers(deltaTime);
                updatingControllerFrame = true;
                try {
                    controllerRuntime.update(evaluator, deltaTime, firstPersonActive);
                } finally {
                    updatingControllerFrame = false;
                }
                reconcileBuiltinSources();
            }
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

        if (step.advance()) {
            if (runtime.isSubEntityRuntime()) {
                advanceControllerPoseLayers(deltaTime);
                controllerRuntime.update(evaluator, deltaTime, firstPersonActive);
            }
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
            if (!anim.declaredControllerSlot && (anim.isNative || anim.fadeSpeed > 0f)) {
                float fadeSpeed = anim.fadeSpeed > 0f ? anim.fadeSpeed : 5f;
                if (anim.weight < anim.targetWeight) {
                    anim.weight = Math.min(anim.targetWeight, anim.weight + deltaTime * fadeSpeed);
                } else if (anim.weight > anim.targetWeight) {
                    anim.weight = Math.max(anim.targetWeight, anim.weight - deltaTime * fadeSpeed);
                }

                // If animation faded out, remove it to save CPU
                if (anim.weight <= 0f && anim.targetWeight <= 0f) {
                    stopPlaybackSounds(anim);
                    // A continuous use request remembers its completed ONCE clip, just as
                    // the reference controller retains lastRequestedAnimation. Release or
                    // selection change removes ownership and lets ordinary cleanup retire it.
                    if (anim.loopMode == YsmAnimationClip.LoopMode.ONCE && anim.time >= anim.clip.length
                            && anim.poseOrder == USE_STAGE && anim.controllerName == null
                            && nativeHandAnimations.containsValue(entry.getKey())) {
                        continue;
                    }
                    it.remove();
                    continue;
                }
            }

            // Retired states keep their last sampled pose and never advance or emit events.
            if (anim.retired) {
                continue;
            }

            if (anim.deferTimelineAdvance) {
                anim.deferTimelineAdvance = false;
                continue;
            }

            // Advance time
            float runningDelta = deltaTime;
            boolean beginningEnded = false;
            if (anim.beginningTransitionRemaining > 0f) {
                float remaining = anim.beginningTransitionRemaining;
                anim.beginningTransitionRemaining = Math.max(0f, remaining - Math.max(0f, deltaTime));
                anim.time = 0f;
                anim.previousTime = 0f;
                anim.eventWindowEnd = 0d;
                if (anim.beginningTransitionRemaining > 0f) {
                    continue;
                }
                runningDelta = Math.max(0f, deltaTime - remaining);
                beginningEnded = true;
            }
            anim.previousTime = anim.time;
            if (Float.isFinite(anim.sampledTime)) {
                anim.time = anim.sampledTime;
                anim.sampledTime = Float.NaN;
            } else {
                anim.time += runningDelta * anim.speed;
            }
            if (observerControlled && anim.remoteTimeCorrection != 0f) {
                // Bound correction to half the running speed: forward clips never snap or run backwards.
                float limit = Math.max(0f, runningDelta) * Math.abs(anim.speed) * 0.5f;
                float correction = Mth.clamp(anim.remoteTimeCorrection, -limit, limit);
                anim.time += correction;
                anim.remoteTimeCorrection -= correction;
            }
            anim.eventWindowEnd = anim.time;
            // The reference keeps this flag after loop wrapping or hold-time clamping, until playback resets.
            anim.completedCycle |= anim.time > Math.max(0f, anim.clip.length);
            if (anim.clip.length > 0)
                advanceAnimationTime(anim);
            // Native movement poses belong to the selected state, even after an ONCE clip ends.
            // Only the native state selector releases them; observers follow its published target weight.
            if (!anim.isNative && anim.loopMode == YsmAnimationClip.LoopMode.ONCE && anim.clip.length > 0f
                    && (anim.controllerName != null ? anim.hasCompletedCycle() : anim.time >= anim.clip.length)) {
                anim.targetWeight = 0f;
                if (!anim.declaredControllerSlot && anim.fadeSpeed <= 0f) {
                    anim.fadeSpeed = 5f;
                }
            }
            if (!observerControlled) {
                if (beginningEnded && anim.controllerConditionActive) {
                    dispatchInitialEvents(anim, evaluator);
                }
                dispatchEvents(anim, evaluator);
                if (!anim.endNotificationDispatched && !anim.loop
                        && anim.loopMode != YsmAnimationClip.LoopMode.LOOP && anim.clip.length > 0f
                        && (anim.speed >= 0 ? anim.eventWindowEnd >= anim.clip.length : anim.eventWindowEnd <= 0f)) {
                    anim.endNotificationDispatched = true;
                    if (anim.controllerConditionActive) {
                        notifyAnimationEvent(anim, "end", "", anim.speed >= 0 ? anim.clip.length : 0f);
                    }
                }
            }
            if (anim.declaredControllerSlot && anim.loopMode == YsmAnimationClip.LoopMode.ONCE
                    && anim.hasCompletedCycle()) {
                anim.weight = 0f;
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
        captureStartPoses(firstPerson);
        // 3. Reset and apply animations to parts
        for (YsmModelPart part : runtime.uniqueParts(firstPerson)) {
            part.resetAnimPose();
            part.setVisible(part.defaultVisibleRaw());
        }
        applyBaseHiddenDefaults();

        Map<String, Map<String, YsmBonePose>> controllerPoses = new LinkedHashMap<>();
        Map<Integer, Map<String, YsmBonePose>> nativePoses = new TreeMap<>();
        Map<Integer, List<WeightedPose>> handPoses = new TreeMap<>();
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
                YsmAnimationClip sampledClip = anim.clip;
                if (firstPerson && !isFirstPersonController(anim.controllerName)) {
                    sampledClip = authoredArmClips.get(resolveAnimationName(anim.clip.name));
                    if (sampledClip == null || isEmptyClip(sampledClip) && isHandFallbackName(anim.clip.name)) {
                        sampledClip = defaultArmClips.get(anim.clip.name);
                    }
                    if (sampledClip == null) {
                        continue;
                    }
                }
                float clipWeight = sampledClip.evaluateBlendWeight(evaluator);
                float effectiveWeight = anim.weight * clipWeight;
                if (!Float.isFinite(effectiveWeight) || effectiveWeight <= 0f) {
                    continue;
                }
                for (YsmBoneAnimation boneAnim : sampledClip.boneAnimations.values()) {
                    List<YsmModelPart> targetParts = runtime.getAnimationParts(boneAnim.boneName, firstPerson);
                    if (targetParts.isEmpty()) continue;

                    Map<String, YsmBonePose> startPoses = firstPerson ? anim.armStartPose : anim.startPose;
                    Map<String, YsmBonePose> lastValid = firstPerson ? anim.armLastValidPose : anim.lastValidPose;
                    YsmBonePose pose = YsmAnimationSampler.holdFinite(YsmAnimationSampler.sampleBone(boneAnim, t, evaluator,
                            startPoses == null ? null : startPoses.get(boneAnim.boneName)), lastValid.get(boneAnim.boneName));
                    lastValid.put(boneAnim.boneName, pose);
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
                        Map<String, YsmBonePose> stage = nativePoses.computeIfAbsent(anim.poseOrder,
                                ignored -> new LinkedHashMap<>());
                        stage.put(boneAnim.boneName,
                                YsmBonePose.accumulate(stage.get(boneAnim.boneName), pose, effectiveWeight));
                    } else if (anim.poseOrder > NATIVE_MAIN_STAGE) {
                        handPoses.computeIfAbsent(anim.poseOrder, ignored -> new ArrayList<>())
                                .add(new WeightedPose(targetParts, pose, effectiveWeight));
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
        applyControllerLayers(controllerPoses, nativePoses, handPoses, firstPerson, true);
        applyNativePoses(nativePoses.remove(NATIVE_MAIN_STAGE), firstPerson);
        for (DirectPose pose : directPoses) {
            if (!pose.beforeMain()) {
                applyDirectPose(pose);
            }
        }
        // Holds, swings and use are their own reference stages, so declared controllers such as
        // post_main or parallel interleave with them instead of always overriding them.
        applyControllerLayers(controllerPoses, nativePoses, handPoses, firstPerson, false);
    }

    /**
     * Records, once per playing clip, the bones' animated pose from the previous frame. The parts still hold
     * that pose because this runs before they are reset for the new frame.
     */
    private void captureStartPoses(boolean firstPerson) {
        for (PlayingAnimation anim : activeAnimations.values()) {
            if ((firstPerson ? anim.armStartPose : anim.startPose) != null) {
                continue;
            }
            Map<String, YsmBonePose> poses = new HashMap<>();
            for (String bone : anim.clip.boneAnimations.keySet()) {
                List<YsmModelPart> parts = runtime.getAnimationParts(bone, firstPerson);
                if (parts.isEmpty()) {
                    continue;
                }
                YsmModelPart part = parts.getFirst();
                poses.put(bone, new YsmBonePose(vector(part.animPosRaw()), vector(part.animRotRaw()),
                        vector(part.animScaleRaw())));
            }
            if (firstPerson) {
                anim.armStartPose = poses;
            } else {
                anim.startPose = poses;
            }
        }
    }

    private static float[] vector(FiguraVec3 value) {
        return new float[]{(float) value.x, (float) value.y, (float) value.z};
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

    private record WeightedPose(List<YsmModelPart> parts, YsmBonePose pose, float weight) {
    }

    private record PoseStage(int order, Runnable apply) {
    }

    /**
     * Applies native, native-hand and declared layers in reference stage order. Inside one stage
     * native clips come first and declared controllers last, so a later stage still wins.
     */
    private void applyControllerLayers(Map<String, Map<String, YsmBonePose>> controllerPoses,
                                       Map<Integer, Map<String, YsmBonePose>> nativePoses,
                                       Map<Integer, List<WeightedPose>> handPoses,
                                       boolean firstPerson, boolean beforeMain) {
        List<PoseStage> stages = new ArrayList<>();
        nativePoses.forEach((order, poses) -> {
            if ((order < NATIVE_MAIN_STAGE) == beforeMain) {
                stages.add(new PoseStage(order, () -> applyNativePoses(poses, firstPerson)));
            }
        });
        handPoses.forEach((order, poses) -> {
            if ((order < NATIVE_MAIN_STAGE) == beforeMain) {
                stages.add(new PoseStage(order, () -> {
                    for (WeightedPose pose : poses) {
                        applyWeightedPose(pose);
                    }
                }));
            }
        });
        Map<String, YsmControllerPoseLayer> poseLayers = firstPerson ? armControllerPoseLayers : controllerPoseLayers;
        for (var entry : poseLayers.entrySet()) {
            int order = YsmControllerSlot.executionOrder(entry.getKey());
            if ((order < NATIVE_MAIN_STAGE) != beforeMain) {
                continue;
            }
            if (!firstPerson && isFirstPersonController(entry.getKey())) {
                continue;
            }
            Map.Entry<String, YsmControllerPoseLayer> layer = entry;
            stages.add(new PoseStage(order, () -> applyControllerLayer(
                    layer.getKey(), layer.getValue(), controllerPoses, firstPerson)));
        }
        // Later stages replace the channels they write, so stable-sort by stage rather than by play order.
        stages.sort(Comparator.comparingInt(PoseStage::order));
        for (PoseStage stage : stages) {
            stage.apply().run();
        }
    }

    private void applyNativePoses(Map<String, YsmBonePose> poses, boolean firstPerson) {
        if (poses == null) {
            return;
        }
        for (var bone : poses.entrySet()) {
            for (YsmModelPart part : runtime.getAnimationParts(bone.getKey(), firstPerson)) {
                applyControllerPose(part, bone.getValue());
            }
        }
    }

    /**
     * Reference controller blend: a hand action replaces the channels it writes, scaled by its
     * weight so a fading action still interpolates from the pose below it.
     */
    private static void applyWeightedPose(WeightedPose contribution) {
        float weight = Math.clamp(contribution.weight(), 0f, 1f);
        if (weight <= 0f) {
            return;
        }
        for (YsmModelPart part : contribution.parts()) {
            if (weight >= 1f) {
                applyControllerPose(part, contribution.pose());
            } else {
                blendControllerPose(part, contribution.pose(), weight);
            }
        }
    }

    private static void blendControllerPose(YsmModelPart part, YsmBonePose pose, float weight) {
        float[] position = pose.position();
        if (position != null) {
            FiguraVec3 current = part.animPosRaw();
            part.animPosRaw().set(blend(current.x, position[0], weight),
                    blend(current.y, position[1], weight), blend(current.z, position[2], weight));
        }
        float[] rotation = pose.rotation();
        if (rotation != null) {
            FiguraVec3 current = part.animRotRaw();
            part.animRotRaw().set(blend(current.x, rotation[0], weight),
                    blend(current.y, rotation[1], weight), blend(current.z, rotation[2], weight));
        }
        float[] scale = pose.scale();
        if (scale != null) {
            FiguraVec3 current = part.animScaleRaw();
            part.animScaleRaw().set(blend(current.x, scale[0], weight),
                    blend(current.y, scale[1], weight), blend(current.z, scale[2], weight));
        }
    }

    private static float blend(double from, float to, float weight) {
        return (float) (from + (to - from) * weight);
    }

    private void applyControllerLayer(String controllerName, YsmControllerPoseLayer layer,
                                      Map<String, Map<String, YsmBonePose>> controllerPoses,
                                      boolean firstPerson) {
        boolean declaredController = controllers.keySet().stream()
                .anyMatch(name -> normalizeControllerName(name).equals(controllerName));
        if (declaredController && usesBuiltinController(controllerName)
                && !functionEventControllers.contains(controllerName)) {
            return;
        }
        Map<String, YsmBonePose> pose = layer.compose(controllerPoses.getOrDefault(controllerName, Map.of()),
                bone -> boneRestRotation(bone, firstPerson),
                bone -> currentBonePose(bone, firstPerson));
        for (var bone : pose.entrySet()) {
            for (YsmModelPart part : runtime.getAnimationParts(bone.getKey(), firstPerson)) {
                applyControllerPose(part, bone.getValue(), declaredController);
            }
        }
    }

    /** Geometry rest rotation (degrees) used as the reference's nlerp offset; null when the bone is unknown. */
    private float[] boneRestRotation(String bone, boolean firstPerson) {
        if (runtime == null) {
            return null;
        }
        List<YsmModelPart> parts = runtime.getAnimationParts(bone, firstPerson);
        if (parts.isEmpty()) {
            return null;
        }
        FiguraVec3 rest = parts.getFirst().getOriginRot();
        return new float[]{(float) rest.x, (float) rest.y, (float) rest.z};
    }

    /**
     * The bone's pose before this controller layer applies. Controller transitions use it as the
     * start value for channels the outgoing snapshot never wrote, so a scale-0 hide stays hidden
     * instead of blending up from identity.
     */
    private YsmBonePose currentBonePose(String bone, boolean firstPerson) {
        if (runtime == null) {
            return null;
        }
        List<YsmModelPart> parts = runtime.getAnimationParts(bone, firstPerson);
        if (parts.isEmpty()) {
            return null;
        }
        YsmModelPart part = parts.getFirst();
        // A base-hidden bone keeps identity scale while visible=false. Its effective transition
        // start is a zero scale, otherwise an incoming scale-0 hide would blend up from 1.
        float[] scale = part.visibleRaw() ? vector(part.animScaleRaw()) : new float[]{0f, 0f, 0f};
        return new YsmBonePose(vector(part.animPosRaw()), vector(part.animRotRaw()), scale);
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
            var window = YsmAnimationEventWindow.window(animation.clip, animation.previousTime,
                    animation.eventWindowEnd, animation.loop, animation.initialEventsDispatched);
            for (YsmAnimationEvent event : window.beforeWrap()) {
                notifyAnimationEvent(animation, event.type(), event.params().getOrDefault("marker", ""), event.time());
                handleEvent(event, evaluator, animation.controllerName);
            }
            if (window.wrapped()) {
                // Sparkle SoundKeyFrameExecutor.reset: a loop wrap stops the clip's sounds.
                stopPlaybackSounds(animation);
            }
            for (YsmAnimationEvent event : window.afterWrap()) {
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
            // Sparkle wraps only once the running time is strictly past the end.
            if (animation.time >= 0f && animation.time <= animation.clip.length) {
                return;
            }
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
        if (!hasNativeHandAnimation("right") && !hasHandLocator(false)
                && usesBuiltinArmPose(false, mainLeft, usingItem, usedHand, entity.swinging, swingingHand)) {
            applyNativeArmPose(false, itemForArm(entity, false, mainLeft),
                    usingItem && isHandForArm(usedHand, false, mainLeft),
                    isHandForArm(swingingHand, false, mainLeft), swingAmount, firstPerson);
        }
        if (!hasNativeHandAnimation("left") && !hasHandLocator(true)
                && usesBuiltinArmPose(true, mainLeft, usingItem, usedHand, entity.swinging, swingingHand)) {
            applyNativeArmPose(true, itemForArm(entity, true, mainLeft),
                    usingItem && isHandForArm(usedHand, true, mainLeft),
                    isHandForArm(swingingHand, true, mainLeft), swingAmount, firstPerson);
        }
    }

    private boolean hasNativeHandAnimation(String slot) {
        return nativeHandAnimations.containsKey(slot) || nativeHoldAnimations.containsKey(slot);
    }

    private boolean usesBuiltinArmPose(boolean left, boolean mainLeft, boolean using, InteractionHand usedHand,
                                       boolean swinging, InteractionHand swingHand) {
        if (using && isHandForArm(usedHand, left, mainLeft)) {
            return usesBuiltinController("player.use");
        }
        if (swinging && isHandForArm(swingHand, left, mainLeft)) {
            return usesBuiltinController("player.swing");
        }
        return usesBuiltinController(left == mainLeft ? "player.hold_mainhand" : "player.hold_offhand");
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
        boolean keepHoldWhileUsing = true;
        if (usingItem && usesBuiltinController("player.use")) {
            boolean left = handSlotIsLeft(usedHand, mainArm);
            useSlot = left ? "left" : "right";
            ItemStack item = entity.getItemInHand(usedHand);
            String[] names = useAnimationNames(entity, left, item);
            String selected = selectHandAnimation("player.use." + usedHand.name().toLowerCase(Locale.ROOT).replace("_", ""),
                    item, names);
            keepHoldWhileUsing = selected == null;
            syncNativeSlotAnimation(nativeHandAnimations, useSlot, selected, YsmAnimationClip.LoopMode.LOOP, USE_STAGE);
        }

        // The use slot follows the swing slot in YSM. A swing pulse must not replace
        // the drawing pose of the same hand while the item is still being used.
        InteractionHand swingingHand = hands.swingingHand(entity, localOwner);
        boolean anySwinging = hands.anySwinging(entity, localOwner);
        boolean swingUsesActiveHand = usingItem && swingingHand == usedHand;
        boolean newSwingPulse = localOwner && hands.swingPulseActive(entity, true)
                && hands.swingSequence() != lastSwingPulseSequence;
        lastSwingPulseSequence = hands.swingSequence();
        if ((anySwinging && !wasSwinging || newSwingPulse) && !swingUsesActiveHand
                && usesBuiltinController("player.swing")) {
            boolean left = handSlotIsLeft(swingingHand, mainArm);
            ItemStack item = entity.getItemInHand(swingingHand);
            String[] names = swingAnimationNames(swingingHand, item);
            playNativeHandOnce(left ? "left" : "right", SWING_STAGE, item, names);
            sampleKineticSwingAnimation(left ? "left" : "right", entity, item);
        }
        String swingSlot = anySwinging && !swingUsesActiveHand
                ? (handSlotIsLeft(swingingHand, mainArm) ? "left" : "right")
                : null;
        for (String slot : new String[]{"left", "right"}) {
            if (!slot.equals(useSlot) && !slot.equals(swingSlot)) {
                syncNativeHandAnimation(slot, false, USE_STAGE);
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
        String source = hand == InteractionHand.MAIN_HAND ? "player.hold_mainhand" : "player.hold_offhand";
        if (!usesBuiltinController(source)) {
            String previous = nativeHoldAnimations.remove(left ? "left" : "right");
            if (previous != null) {
                fadeOut(previous, 0f);
            }
            return;
        }
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
        int poseOrder = hand == InteractionHand.MAIN_HAND ? HOLD_MAINHAND_STAGE : HOLD_OFFHAND_STAGE;
        if (item.isEmpty()) {
            syncNativeHoldAnimation(slot, poseOrder, item, prefix + ":empty");
            return;
        }
        if (item.is(Items.CROSSBOW) && CrossbowItem.isCharged(item)) {
            syncNativeHoldAnimation(slot, poseOrder, item, prefix + ":charged_crossbow", prefix);
            return;
        }
        if (hand == InteractionHand.MAIN_HAND && item.is(Items.FISHING_ROD)
                && entity instanceof Player player && player.fishing != null && !player.fishing.isRemoved()) {
            syncNativeHoldAnimation(slot, poseOrder, item, prefix + ":fishing", prefix);
            return;
        }
        if (isLanceWeapon(item)) {
            List<String> names = new ArrayList<>();
            if (entity.isPassenger())
                names.add("lance_riding_idle");
            names.add("lance_stand");
            names.add(prefix + ":lance");
            names.addAll(Arrays.asList(conditionalAnimationNames(prefix, item, false)));
            syncNativeHoldAnimation(slot, poseOrder, item, names.toArray(String[]::new));
            return;
        }
        syncNativeHoldAnimation(slot, poseOrder, item, conditionalAnimationNames(prefix, item, false));
    }

    private String[] useAnimationNames(LivingEntity entity, boolean left, ItemStack item) {
        String prefix = left ? "use_offhand" : "use_mainhand";
        List<String> names = new ArrayList<>();
        if (isLanceWeapon(item) && item.getUseAnimation() == UseAnim.SPEAR) {
            if (entity.isPassenger()) {
                names.add("lance_riding_charge");
            } else if (entity.isFallFlying()) {
                names.add("lance_fall_flying_charge");
            }
            names.add("lance_charge");
            names.add(prefix + ":lance");
            // SPEAR charge must not borrow legacy TRIDENT or short generic use poses.
            // Missing charge resources leave this hand's authored hold in control.
            return names.toArray(String[]::new);
        }
        names.addAll(Arrays.asList(conditionalAnimationNames(prefix, item, true)));
        names.add(prefix);
        return names.toArray(String[]::new);
    }

    private void sampleKineticSwingAnimation(String slot, LivingEntity entity, ItemStack item) {
        if (!isLanceWeapon(item))
            return;
        String name = nativeHandAnimations.get(slot);
        PlayingAnimation animation = name == null ? null : activeAnimations.get(name);
        if (animation != null && animation.clip.length > 0f)
            animation.sampledTime = Math.clamp(entity.getAttackAnim(0f), 0f, 1f) * animation.clip.length;
    }

    private static boolean isLanceWeapon(ItemStack item) {
        return top.aihmc.omnimorph.model.ysm.YsmHandItemType.matches(item, "lance");
    }

    private String[] swingAnimationNames(InteractionHand hand, ItemStack item) {
        String prefix = hand == InteractionHand.MAIN_HAND ? "swing" : "swing_offhand";
        List<String> names = new ArrayList<>();
        if (hand == InteractionHand.MAIN_HAND && isLanceWeapon(item)) {
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
            ResourceLocation tagId = identifier(normalized.substring(start.length()));
            if (tagId != null && item.is(TagKey.create(Registries.ITEM, tagId)))
                names.add(animation);
        }
    }

    private static ResourceLocation identifier(String value) {
        try {
            return ResourceLocation.parse(value);
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
                || item.getUseAnimation() == UseAnim.SPEAR;
    }

    private static List<String> itemTypes(ItemStack item, boolean use) {
        List<String> result = new ArrayList<>();
        if (item.is(Items.TRIDENT)) {
            result.add(use ? "trident" : "spear");
            if (use)
                result.add("spear");
        } else if (item.getUseAnimation() == UseAnim.SPEAR) {
            result.add(use ? "lance" : "spear");
            if (use) {
                result.add("spear");
            }
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
    private void syncNativeHandAnimation(String slot, boolean active, int poseOrder, String... names) {
        String selected = active ? firstHandAnimation(names) : null;
        syncNativeSlotAnimation(nativeHandAnimations, slot, selected, YsmAnimationClip.LoopMode.LOOP, poseOrder);
    }

    /** Holds: LOOP, except a declared *:lance hold, which the reference holds on its last frame. */
    private void syncNativeHoldAnimation(String slot, int poseOrder, ItemStack item, String... names) {
        String action = poseOrder == HOLD_MAINHAND_STAGE ? "player.hold_mainhand" : "player.hold_offhand";
        String selected = selectHandAnimation(action, item, names);
        YsmAnimationClip.LoopMode predicateLoop = selected != null && selected.endsWith(":lance")
                ? YsmAnimationClip.LoopMode.HOLD_ON_LAST_FRAME
                : YsmAnimationClip.LoopMode.LOOP;
        syncNativeSlotAnimation(nativeHoldAnimations, slot, selected, predicateLoop, poseOrder);
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
                                         YsmAnimationClip.LoopMode predicateLoop, int poseOrder) {
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
            PlayingAnimation playing = play(selected, nativeHandLoopMode(selected, clip, predicateLoop), 1f, 0.1f);
            playing.builtinSource = owners == nativeHoldAnimations
                    ? "player." + (selected.startsWith("hold_offhand") ? "hold_offhand" : "hold_mainhand")
                    : "player.use";
            playing.poseOrder = poseOrder;
            owners.put(slot, selected);
        }
    }

    private void playNativeHandOnce(String slot, int poseOrder, ItemStack item, String... names) {
        String selected = selectHandAnimation("player.swing." + slot, item, names);
        if (selected == null)
            return;
        String previous = nativeHandAnimations.put(slot, selected);
        if (previous != null && !previous.equals(selected))
            fadeOut(previous, 0.05f);
        PlayingAnimation playing = play(selected, YsmAnimationClip.LoopMode.ONCE, 1f, 0f);
        playing.builtinSource = "player.swing";
        playing.poseOrder = poseOrder;
    }

    private String firstHandAnimation(String... names) {
        for (String name : names) {
            String normalized = resolveAnimationName(name);
            YsmAnimationClip clip = clips.get(normalized);
            if (!isEmptyClip(clip)) {
                return normalized;
            }
            // Candidate specificity precedes source: an authored generic use must not
            // hide a missing item's specific default draw. Same-name author still wins.
            if (nativeStateMachineEnabled && isNativeAnimationEnabled(normalized)
                    && !isEmptyClip(defaultClips.get(normalized))) {
                return normalized;
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
        if (item.getUseAnimation() == UseAnim.SPEAR)
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
            beginControllerPoseTransition(controller, YsmBlendTransition.linear(0.1f));
            retireControllerAnimation(controller, previous, 0f);
        }
        if (selected == null) {
            subEntityAnimations.remove(controller);
        } else {
            subEntityAnimations.put(controller, selected);
            playControllerAnimation(controller, selected, null, 1f, 0f, evaluatorForEvents());
        }
    }

    /**
     * Reference AnimationManager: the played base action comes from the same resolver that backs
     * the {@code ctrl.*} Molang states, so a query and the native animation cannot disagree.
     */
    /** Base action resolved on the last native update; NONE before the first update. */
    public YsmPlayerActionState.State actionState() {
        return lastActionState;
    }

    /** Native movement clip selected on the last update; empty when none applies. */
    public String nativeAnimation() {
        return lastNativeState;
    }

    /** Per arm ("left"/"right"): the selected hold clip, the use/swing action clip and whether the hold is paused. */
    public Map<String, Map<String, Object>> handActions() {
        Map<String, Map<String, Object>> result = new LinkedHashMap<>();
        for (String side : List.of("left", "right")) {
            Map<String, Object> hand = new LinkedHashMap<>();
            String hold = nativeHoldAnimations.get(side);
            PlayingAnimation holdAnimation = hold == null ? null : activeAnimations.get(hold);
            hand.put("hold", hold);
            hand.put("action", nativeHandAnimations.get(side));
            hand.put("paused", holdAnimation != null && holdAnimation.paused);
            result.put(side, hand);
        }
        return result;
    }

    private void updateNativeStateMachine(LivingEntityRenderState state, LivingEntity entity) {
        if (!usesBuiltinController("player.main")) {
            lastActionState = YsmPlayerActionState.State.NONE;
            lastNativeState = "";
            activeAnimations.values().removeIf(animation -> {
                if (!animation.isNative) {
                    return false;
                }
                stopPlaybackSounds(animation);
                return true;
            });
            return;
        }
        if (state == null) return;

        Avatar.MolangContext context = runtime == null || runtime.owner() == null
                ? null : runtime.owner().getMolangContext();
        // The render state limb amount is the reference's limb-swing fallback; the movement
        // context already folds it in once updateFrame captured the frame.
        float groundSpeed = Math.max(finite(context == null ? 0f : context.ground_speed),
                finite(readFloat(state, "walkAnimationSpeed", 0f)));
        float verticalSpeed = finite(context == null ? 0f : context.vertical_speed);

        YsmPlayerActionState.Snapshot snapshot = entity != null
                ? YsmPlayerActionState.snapshot(entity, groundSpeed, verticalSpeed)
                : renderStateActionState(state, groundSpeed, verticalSpeed);
        lastActionState = YsmPlayerActionState.resolve(snapshot);
        String idealState = nativeStateName(lastActionState);
        lastNativeState = idealState;
        if (runtime != null && shouldInterruptActions(idealState))
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
                anim.builtinSource = "player.main";
                activeAnimations.put(idealState, anim);
            }
            anim.targetWeight = 1f;
        }
    }

    /**
     * Render-state fallback for callers without a live entity. A render state carries no death,
     * riptide, climb or hurt flags, so those stay false.
     */
    private YsmPlayerActionState.Snapshot renderStateActionState(LivingEntityRenderState state,
                                                                 float groundSpeed, float verticalSpeed) {
        boolean swimming = readFloat(state, "swimAmount", 0f) > 0.1f
                || readBoolean(state, "isSwimming", readBoolean(state, "swimming", false));
        return new YsmPlayerActionState.Snapshot(
                readBoolean(state, "isPassenger", false),
                false,
                false,
                readBoolean(state, "isSleeping", false),
                swimming,
                swimming,
                false,
                readBoolean(state, "isFallFlying", false),
                false,
                false,
                true,
                readBoolean(state, "isCrouching",
                        readBoolean(state, "isSneaking", readBoolean(state, "crouching", false))),
                readBoolean(state, "isSprinting", false),
                false,
                groundSpeed,
                verticalSpeed);
    }

    private static float finite(float value) {
        return Float.isFinite(value) ? value : 0f;
    }

    /**
     * The reference plays exactly the resolved state's clip. The extra names after the first keep
     * OmniMorph's previous behaviour for models that do not author that clip.
     */
    private String nativeStateName(YsmPlayerActionState.State state) {
        return switch (state) {
            case NONE -> "";
            case RIDE -> firstEnabled("sit", "ride");
            case DEATH -> firstEnabled("death");
            case RIPTIDE -> firstEnabled("riptide");
            case SLEEP -> firstEnabled("sleep");
            case SWIM -> firstEnabled("swim", "swim_stand");
            case CLIMB -> firstEnabled("climb");
            case CLIMBING -> firstEnabled("climbing");
            case LADDER_UP -> firstEnabled("ladder_up");
            case LADDER_STILLNESS -> firstEnabled("ladder_stillness");
            case LADDER_DOWN -> firstEnabled("ladder_down");
            case ELYTRA_FLY -> firstEnabled("elytra_fly", "fly");
            case FLY -> firstEnabled("fly");
            case SWIM_STAND -> firstEnabled("swim_stand", "swim");
            case ATTACKED -> firstEnabled("attacked");
            case JUMP -> firstEnabled("jump", "fall", "fly");
            case SNEAK -> firstEnabled("sneak", "sneaking");
            case SNEAKING -> firstEnabled("sneaking", "sneak");
            case RUN -> firstEnabled("run", "walk");
            case WALK -> firstEnabled("walk", "run");
            case IDLE -> firstEnabled("idle");
        };
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

    private boolean isHandFallbackName(String name) {
        String normalized = normalizeName(name);
        if (isConditionalHandName(normalized)) {
            return true;
        }
        return switch (simpleName(normalized)) {
            case "hold_mainhand", "hold_offhand", "use_mainhand", "use_offhand",
                    "swing", "swing_hand", "swing_offhand", "attack_empty" -> true;
            default -> normalized.startsWith("lance_");
        };
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
        // Preserve exact authored identity before trying compatibility aliases.
        return normalized.toLowerCase(Locale.US);
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
