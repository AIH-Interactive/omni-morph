package org.figuramc.figura.model.ysm.animation;

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
import org.figuramc.figura.mixin.AbstractArrowAccessor;
import org.figuramc.figura.molang.parser.ast.Expression;
import org.figuramc.figura.molang.runtime.ExpressionEvaluator;
import org.figuramc.figura.molang.runtime.MolangExecutionBudget;
import org.figuramc.figura.FiguraMod;
import org.figuramc.figura.molang.storage.StringPool;
import org.figuramc.figura.lua.api.particle.ParticleAPI;
import org.figuramc.figura.lua.api.sound.LuaSound;
import org.figuramc.figura.math.vector.FiguraVec3;
import org.figuramc.figura.model.ysm.YsmBoneRole;
import org.figuramc.figura.model.ysm.YsmModelPart;
import org.figuramc.figura.model.ysm.YsmModelRuntime;
import org.figuramc.figura.model.ysm.YsmParticleRuntime;
import org.figuramc.figura.model.ysm.YsmMolangFunctionRuntime;
import org.figuramc.figura.model.ysm.YsmFilterRuntime;
import org.figuramc.figura.model.ysm.controller.YsmAnimationController;
import org.figuramc.figura.model.ysm.controller.YsmControllerAnimationRef;
import org.figuramc.figura.model.ysm.controller.YsmControllerRuntime;
import org.figuramc.figura.model.ysm.controller.YsmControllerState;
import org.figuramc.figura.model.ysm.controller.YsmControllerSlot;
import org.figuramc.figura.model.ysm.controller.YsmControllerSlotBinder;
import org.figuramc.figura.model.ysm.controller.YsmControllerPoseLayer;
import org.figuramc.figura.model.ysm.controller.YsmBlendTransition;

import java.util.*;

public class YsmAnimationPlayer {
    private final YsmModelRuntime runtime;
    private final Map<String, YsmAnimationClip> clips = new HashMap<>();
    private final Map<String, YsmAnimationController> controllers = new LinkedHashMap<>();
    private final YsmControllerRuntime controllerRuntime;
    private final Map<String, PlayingAnimation> activeAnimations = new LinkedHashMap<>();
    private final Map<String, String> controllerFunctionAnimations = new HashMap<>();
    private final Map<String, Float> controllerFunctionTransitionLengths = new HashMap<>();
    private final Set<String> controllerFunctionReloads = new HashSet<>();
    private final Map<String, YsmControllerPoseLayer> controllerPoseLayers = new LinkedHashMap<>();
    private final Map<String, YsmControllerPoseLayer> armControllerPoseLayers = new LinkedHashMap<>();
    private final Map<String, String> nativeHandAnimations = new HashMap<>();
    private final Set<String> disabledNativeAnimations = new HashSet<>();
    private final Set<String> baseHiddenBones = new HashSet<>();
    private final YsmAnimationFrameClock frameClock = new YsmAnimationFrameClock();
    private final YsmControllerSoundBank controllerSounds = new YsmControllerSoundBank();
    private boolean controllerInitialHiddenResolved;
    private Entity currentEntity;
    private final Map<String, String> subEntityAnimations = new LinkedHashMap<>();
    private final Set<String> functionEventControllers = new HashSet<>();
    private boolean wasSwinging;
    private long lastBudgetFailureFrame = Long.MIN_VALUE;

    public static class PlayingAnimation {
        public final YsmAnimationClip clip;
        public long playbackGeneration;
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

        // For smooth transitions in native state machine
        public float targetWeight = 0f;
        public float fadeSpeed = 0f;
        public float sampledTime = Float.NaN;
        private boolean retired;
        private String controllerName;

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
        controllerSounds.close();
        currentEntity = null;
    }

    public void resetControllerAnimations(String controllerName) {
        String controller = normalizeControllerName(controllerName);
        activeAnimations.values().removeIf(animation -> controller.equals(animation.controllerName));
        controllerFunctionAnimations.remove(controller);
        controllerFunctionReloads.remove(controller);
        controllerPoseLayers.remove(controller);
        armControllerPoseLayers.remove(controller);
    }

    public Map<String, PlayingAnimation> getActiveAnimations() {
        return activeAnimations;
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
        for (Map.Entry<String, YsmAnimationClip> entry : clips.entrySet()) {
            String normalized = normalizeName(entry.getKey());
            if (!isBaseAnimation(normalized, entry.getValue()))
                continue;
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
        return playInSlot(resolveAnimationName(name), name, loopMode, speed, fadeSeconds, null);
    }

    private PlayingAnimation playInSlot(String slot, String name, YsmAnimationClip.LoopMode loopMode,
                                         float speed, float fadeSeconds, String controllerName) {
        return playInSlot(slot, name, loopMode, speed, fadeSeconds, controllerName, null);
    }

    private PlayingAnimation playInSlot(String slot, String name, YsmAnimationClip.LoopMode loopMode,
                                         float speed, float fadeSeconds, String controllerName,
                                         ExpressionEvaluator<?> initialEvaluator) {
        String normalized = resolveAnimationName(name);
        YsmAnimationClip clip = clips.get(normalized);
        if (clip == null) return null;

        boolean created = !activeAnimations.containsKey(slot);
        PlayingAnimation playing = activeAnimations.computeIfAbsent(slot, k -> new PlayingAnimation(clip, false));
        playing.playbackGeneration++;
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
        activeAnimations.remove(normalized);
        controllerFunctionAnimations.values().removeIf(normalized::equals);
    }

    public void fadeOut(String name, float fadeSeconds) {
        fadeOutSlot(resolveAnimationName(name), fadeSeconds);
    }

    private void fadeOutSlot(String slot, float fadeSeconds) {
        PlayingAnimation animation = activeAnimations.get(slot);
        if (animation == null)
            return;
        if (fadeSeconds <= 0f) {
            activeAnimations.remove(slot);
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
        } catch (MolangExecutionBudget.Exceeded exhausted) {
            runtime.particles().discardPending();
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
            org.figuramc.figura.model.ysm.YsmHandQuery.capture(renderContext);
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
            dispatchEvents(anim, evaluator);
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
        for (PlayingAnimation anim : activeAnimations.values()) {
            if (anim.retired) {
                continue;
            }
            if (!firstPerson && isFirstPersonController(anim.controllerName)) {
                continue;
            }
            if (anim.weight <= 0f) continue;

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
                for (YsmBoneAnimation boneAnim : anim.clip.boneAnimations.values()) {
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

                    for (YsmModelPart part : targetParts) {
                        if (posVal != null)
                            part.addAnimPos(posVal[0] * effectiveWeight, posVal[1] * effectiveWeight, posVal[2] * effectiveWeight);
                        if (rotVal != null)
                            part.addAnimRot(rotVal[0] * effectiveWeight, rotVal[1] * effectiveWeight, rotVal[2] * effectiveWeight);
                        if (scaleVal != null) {
                            boolean baseHidden = isBaseHiddenBone(boneAnim.boneName);
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

        Map<String, YsmControllerPoseLayer> poseLayers = firstPerson ? armControllerPoseLayers : controllerPoseLayers;
        for (var entry : poseLayers.entrySet()) {
            if (!firstPerson && isFirstPersonController(entry.getKey())) {
                continue;
            }
            Map<String, YsmBonePose> pose = entry.getValue().compose(controllerPoses.getOrDefault(entry.getKey(), Map.of()));
            for (var bone : pose.entrySet()) {
                for (YsmModelPart part : runtime.getAnimationParts(bone.getKey(), firstPerson)) {
                    applyControllerPose(part, bone.getValue());
                }
            }
        }
    }

    private boolean isFirstPersonController(String controllerName) {
        return controllerName != null && YsmControllerSlot.fromName(controllerName) == YsmControllerSlot.FIRST_PERSON_ARM;
    }

    private static void applyControllerPose(YsmModelPart part, YsmBonePose pose) {
        float[] position = pose.position();
        float[] rotation = pose.rotation();
        float[] scale = pose.scale();
        if (position != null) {
            part.addAnimPos(position[0], position[1], position[2]);
        }
        if (rotation != null) {
            part.addAnimRot(rotation[0], rotation[1], rotation[2]);
        }
        if (scale != null) {
            float magnitude = Math.max(Math.abs(scale[0]), Math.max(Math.abs(scale[1]), Math.abs(scale[2])));
            part.setVisible(magnitude >= 0.0001f);
            part.mulAnimScale(scale[0], scale[1], scale[2]);
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

    private void playOwnedSound(String controllerName, YsmAnimationEvent event) {
        if (runtime.isPreviewRuntime()) {
            return;
        }
        LuaSound sound = playEventSound(event);
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
        if (!nativeHandAnimations.containsKey("right") && !hasHandLocator(false)) {
            applyNativeArmPose(false, itemForArm(entity, false, mainLeft),
                    usingItem && isHandForArm(usedHand, false, mainLeft),
                    isHandForArm(swingingHand, false, mainLeft), swingAmount, firstPerson);
        }
        if (!nativeHandAnimations.containsKey("left") && !hasHandLocator(true)) {
            applyNativeArmPose(true, itemForArm(entity, true, mainLeft),
                    usingItem && isHandForArm(usedHand, true, mainLeft),
                    isHandForArm(swingingHand, true, mainLeft), swingAmount, firstPerson);
        }
    }

    private boolean hasHandLocator(boolean left) {
        // Fallback hand bones are registered as attachment points too. They
        // still require the vanilla-like pose when the model has no authored
        // locator; only an actual YSM locator suppresses that fallback.
        var point = runtime.getHandAttachmentPoint(left);
        return point != null && point.locator() != null;
    }

    private void updateNativeHandAnimations(LivingEntity entity) {
        if (entity == null || entity.getPose() == Pose.SLEEPING) {
            clearNativeHandAnimations();
            wasSwinging = false;
            return;
        }

        HumanoidArm mainArm = entity.getMainArm();
        if (entity.isUsingItem()) {
            boolean left = entity.getUsedItemHand() == InteractionHand.MAIN_HAND
                    ? mainArm == HumanoidArm.LEFT
                    : mainArm != HumanoidArm.LEFT;
            syncNativeHandAnimation(left ? "left" : "right", true,
                    useAnimationNames(entity, left, entity.getUseItem()));
            sampleKineticChargeAnimation(left ? "left" : "right", entity, entity.getUseItem());
            syncNativeHandAnimation(left ? "right" : "left", false);
        } else {
            syncHoldingAnimation(entity, InteractionHand.MAIN_HAND, mainArm == HumanoidArm.LEFT);
            syncHoldingAnimation(entity, InteractionHand.OFF_HAND, mainArm != HumanoidArm.LEFT);
        }

        if (entity.swinging && !wasSwinging) {
            boolean left = entity.swingingArm == InteractionHand.MAIN_HAND
                    ? mainArm == HumanoidArm.LEFT
                    : mainArm != HumanoidArm.LEFT;
            ItemStack item = entity.getItemInHand(entity.swingingArm);
            String[] names = swingAnimationNames(entity.swingingArm, item);
            playNativeHandOnce(left ? "left" : "right", names);
            sampleKineticSwingAnimation(left ? "left" : "right", entity, item);
        }
        wasSwinging = entity.swinging;
    }

    private void syncHoldingAnimation(LivingEntity entity, InteractionHand hand, boolean left) {
        if (entity.swinging && entity.swingingArm == hand)
            return;
        ItemStack item = entity.getItemInHand(hand);
        String slot = left ? "left" : "right";
        String prefix = hand == InteractionHand.MAIN_HAND ? "hold_mainhand" : "hold_offhand";
        if (item.isEmpty()) {
            syncNativeHandAnimation(slot, true, prefix + ":empty");
            return;
        }
        if (item.is(Items.CROSSBOW) && CrossbowItem.isCharged(item)) {
            syncNativeHandAnimation(slot, true, prefix + ":charged_crossbow", prefix);
            return;
        }
        if (hand == InteractionHand.MAIN_HAND && item.is(Items.FISHING_ROD)
                && entity instanceof Player player && player.fishing != null && !player.fishing.isRemoved()) {
            syncNativeHandAnimation(slot, true, prefix + ":fishing", prefix);
            return;
        }
        if (isKineticSpear(item)) {
            List<String> names = new ArrayList<>();
            if (entity.isPassenger())
                names.add("lance_riding_idle");
            names.add("lance_stand");
            names.add(prefix + ":lance");
            names.addAll(Arrays.asList(conditionalAnimationNames(prefix, item, false)));
            syncNativeHandAnimation(slot, true, names.toArray(String[]::new));
            return;
        }
        syncNativeHandAnimation(slot, true, conditionalAnimationNames(prefix, item, false));
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
        // Sparkle falls back to the generic use clip only after all declared
        // id/tag/use-animation conditions have been considered.
        names.add(prefix);
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
            if (use)
                result.add("spear");
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
        if (!result.contains(useAnimation) && !useAnimation.equals("none"))
            result.add(useAnimation);
        return result;
    }

    private void syncNativeHandAnimation(String slot, boolean active, String... names) {
        String previous = nativeHandAnimations.get(slot);
        String selected = active ? firstHandAnimation(names) : null;
        if (Objects.equals(previous, selected))
            return;
        if (previous != null)
            fadeOut(previous, 0.1f);
        if (selected == null) {
            nativeHandAnimations.remove(slot);
        } else {
            YsmAnimationClip clip = clips.get(selected);
            play(selected, clip == null ? YsmAnimationClip.LoopMode.ONCE : clip.loopMode, 1f, 0.1f);
            nativeHandAnimations.put(slot, selected);
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
        for (String name : names) {
            String normalized = resolveAnimationName(name);
            YsmAnimationClip clip = clips.get(normalized);
            if (!isEmptyClip(clip))
                return normalized;
        }
        return null;
    }

    private void clearNativeHandAnimations() {
        for (String animation : nativeHandAnimations.values())
            fadeOut(animation, 0.1f);
        nativeHandAnimations.clear();
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
                anim = new PlayingAnimation(clips.get(idealState), true);
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
            return firstEnabled("walk");
        return firstEnabled("idle");
    }

    private static boolean shouldInterruptActions(String state) {
        return state != null && !state.isBlank() && !"idle".equals(state);
    }

    private String firstEnabled(String... names) {
        for (String name : names) {
            String normalized = normalizeName(name);
            if (clips.containsKey(normalized) && !disabledNativeAnimations.contains(normalized))
                return normalized;
        }
        return "";
    }

    private boolean readBoolean(LivingEntityRenderState state, String name, boolean fallback) {
        try {
            java.lang.reflect.Field field = state.getClass().getField(name);
            return field.getBoolean(state);
        } catch (Throwable ignored) {
            return fallback;
        }
    }

    private float readFloat(LivingEntityRenderState state, String name, float fallback) {
        try {
            java.lang.reflect.Field field = state.getClass().getField(name);
            return field.getFloat(state);
        } catch (Throwable ignored) {
            return fallback;
        }
    }

    private String normalizeName(String name) {
        if (name == null)
            return "";
        String normalized = name;
        if (normalized.startsWith("animation."))
            normalized = normalized.substring("animation.".length());
        int colon = normalized.indexOf(':');
        if (colon >= 0 && colon + 1 < normalized.length())
            normalized = normalized.substring(colon + 1);
        normalized = stripReferenceSuffix(normalized);
        return normalized.toLowerCase(Locale.US);
    }

    private String literalAnimationName(String name) {
        if (name == null)
            return "";
        String normalized = name;
        if (normalized.startsWith("animation."))
            normalized = normalized.substring("animation.".length());
        return stripReferenceSuffix(normalized).toLowerCase(Locale.US);
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
