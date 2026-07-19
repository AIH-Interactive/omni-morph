package org.figuramc.figura.model.ysm.animation;

import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.item.ItemStack;
import org.figuramc.figura.avatar.Avatar;
import org.figuramc.figura.molang.parser.ast.Expression;
import org.figuramc.figura.molang.runtime.ExpressionEvaluator;
import org.figuramc.figura.molang.storage.StringPool;
import org.figuramc.figura.lua.api.particle.ParticleAPI;
import org.figuramc.figura.lua.api.sound.SoundAPI;
import org.figuramc.figura.math.vector.FiguraVec3;
import org.figuramc.figura.model.ysm.YsmBoneRole;
import org.figuramc.figura.model.ysm.YsmModelPart;
import org.figuramc.figura.model.ysm.YsmModelRuntime;
import org.figuramc.figura.model.ysm.controller.YsmAnimationController;
import org.figuramc.figura.model.ysm.controller.YsmControllerAnimationRef;
import org.figuramc.figura.model.ysm.controller.YsmControllerRuntime;
import org.figuramc.figura.model.ysm.controller.YsmControllerState;

import java.util.*;

public class YsmAnimationPlayer {
    private final YsmModelRuntime runtime;
    private final Map<String, YsmAnimationClip> clips = new HashMap<>();
    private final Map<String, YsmAnimationController> controllers = new LinkedHashMap<>();
    private final YsmControllerRuntime controllerRuntime;
    private final Map<String, PlayingAnimation> activeAnimations = new LinkedHashMap<>();
    private final Map<String, String> controllerFunctionAnimations = new HashMap<>();
    private final Set<String> disabledNativeAnimations = new HashSet<>();
    private final Set<String> baseHiddenBones = new HashSet<>();
    private float lastAgeInTicks = Float.NaN;
    private boolean controllerInitialHiddenResolved;
    private LivingEntity currentEntity;

    public static class PlayingAnimation {
        public final YsmAnimationClip clip;
        public float time;
        public float previousTime;
        public float speed = 1f;
        public float weight = 1f;
        public boolean loop;
        public YsmAnimationClip.LoopMode loopMode;
        public final boolean isNative;
        public boolean initialEventsDispatched;

        // For smooth transitions in native state machine
        public float targetWeight = 0f;
        public float fadeSpeed = 0f;

        public PlayingAnimation(YsmAnimationClip clip, boolean isNative) {
            this.clip = clip;
            this.isNative = isNative;
            this.loop = clip.loop;
            this.loopMode = clip.loopMode;
            this.targetWeight = isNative ? 0f : 1f;
            this.weight = isNative ? 0f : 1f;
        }
    }

    private boolean nativeStateMachineEnabled = true;

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

    public YsmControllerRuntime controllerRuntime() {
        return controllerRuntime;
    }

    public void runFunctionEventsForController(YsmAnimationController controller, ExpressionEvaluator<?> evaluator) {
        if (controller != null)
            runtime.functions().runControllerSlotEvents(controller.name(), controller.slot(), evaluator);
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
        String normalized = resolveAnimationName(name);
        YsmAnimationClip clip = clips.get(normalized);
        if (clip == null) return null;

        boolean created = !activeAnimations.containsKey(normalized);
        PlayingAnimation playing = activeAnimations.computeIfAbsent(normalized, k -> new PlayingAnimation(clip, false));
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
        if (created)
            dispatchInitialEvents(playing, evaluatorForEvents());
        return playing;
    }

    public PlayingAnimation playControllerFunctionAnimation(String controllerName, String animationName, YsmAnimationClip.LoopMode loopMode, float speed, float fadeSeconds) {
        String controller = normalizeControllerName(controllerName);
        String normalizedAnimation = normalizeName(animationName);
        String previous = controllerFunctionAnimations.get(controller);
        if (previous != null && !previous.equals(normalizedAnimation))
            fadeOut(previous, fadeSeconds);
        PlayingAnimation playing = play(animationName, loopMode, speed, fadeSeconds);
        if (playing != null)
            controllerFunctionAnimations.put(controller, normalizedAnimation);
        return playing;
    }

    public void clearControllerFunctionAnimation(String controllerName, float fadeSeconds) {
        String previous = controllerFunctionAnimations.remove(normalizeControllerName(controllerName));
        if (previous != null)
            fadeOut(previous, fadeSeconds);
    }

    public YsmAnimationClip.LoopMode loopModeFor(String animationName, YsmAnimationClip.LoopMode fallback) {
        YsmAnimationClip clip = clips.get(normalizeName(animationName));
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
        PlayingAnimation animation = activeAnimations.get(resolveAnimationName(name));
        if (animation == null)
            return;
        if (fadeSeconds <= 0f) {
            stop(name);
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
        currentEntity = entity;
        float deltaTime = getDeltaTime(state);

        // 1. Run Native State Machine if enabled
        if (nativeStateMachineEnabled) {
            updateNativeStateMachine(state, entity);
        } else {
            // Smoothly fade out any running native animations
            for (PlayingAnimation anim : activeAnimations.values()) {
                if (anim.isNative) {
                    anim.targetWeight = 0f;
                }
            }
        }

        // 2. Advance time and update weights for active animations
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

            // Advance time
            anim.previousTime = anim.time;
            anim.time += deltaTime * anim.speed;
            if (anim.clip.length > 0)
                advanceAnimationTime(anim);
            if (anim.loopMode == YsmAnimationClip.LoopMode.ONCE && anim.clip.length > 0f && anim.time >= anim.clip.length) {
                anim.targetWeight = 0f;
                if (anim.fadeSpeed <= 0f)
                    anim.fadeSpeed = 5f;
            }
            dispatchEvents(anim, evaluatorForEvents());
        }

        Avatar.MolangContext molangContext = runtime.owner().getMolangContext();
        ExpressionEvaluator<?> evaluator = molangContext != null ? ExpressionEvaluator.evaluator(molangContext) : ExpressionEvaluator.evaluator(runtime.owner());
        if (!controllerInitialHiddenResolved) {
            collectControllerBaseHiddenBones(evaluator);
            collectControllerInitialHiddenBones(evaluator);
            controllerInitialHiddenResolved = true;
        }
        runtime.functions().beginControllerFrame();
        controllerRuntime.update(evaluator, deltaTime);
        runtime.functions().runUnboundControllerEvents(evaluator);

        // 3. Reset and apply animations to parts
        for (YsmModelPart part : runtime.uniqueParts()) {
            part.resetAnimPose();
            part.setVisible(part.defaultVisibleRaw());
        }
        applyBaseHiddenDefaults();

        for (PlayingAnimation anim : activeAnimations.values()) {
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
            for (YsmBoneAnimation boneAnim : anim.clip.boneAnimations.values()) {
                List<YsmModelPart> targetParts = runtime.getAnimationParts(boneAnim.boneName);
                if (targetParts.isEmpty()) continue;

                float[] posVal = boneAnim.position == null ? null : evaluateChannel(boneAnim.position, t, evaluator, new float[]{0f, 0f, 0f});
                float[] rotVal = boneAnim.rotation == null ? null : evaluateChannel(boneAnim.rotation, t, evaluator, new float[]{0f, 0f, 0f});
                float[] scaleVal = boneAnim.scale == null ? null : evaluateChannel(boneAnim.scale, t, evaluator, new float[]{1f, 1f, 1f});

                for (YsmModelPart part : targetParts) {
                    if (posVal != null)
                        part.addAnimPos(posVal[0] * anim.weight, posVal[1] * anim.weight, posVal[2] * anim.weight);
                    if (rotVal != null)
                        part.addAnimRot(rotVal[0] * anim.weight, rotVal[1] * anim.weight, rotVal[2] * anim.weight);
                    if (scaleVal != null) {
                        boolean baseHidden = isBaseHiddenBone(boneAnim.boneName);
                        float scaleMagnitude = scaleMagnitude(scaleVal);
                        if (baseHidden && baseAnimation && scaleMagnitude < 0.0001f)
                            continue;
                        if (scaleMagnitude < 0.0001f) {
                            part.setVisible(false);
                            part.mulAnimScale(
                                    1.0 + (scaleVal[0] - 1.0) * anim.weight,
                                    1.0 + (scaleVal[1] - 1.0) * anim.weight,
                                    1.0 + (scaleVal[2] - 1.0) * anim.weight
                            );
                            continue;
                        }
                        if (baseAnimation && part.defaultVisibleRaw()) {
                            part.setVisible(true);
                        }
                        if (!baseAnimation && scaleMagnitude > 0.0001f)
                            part.setVisible(true);
                        // Scale lerps with weight: final_scale = 1 + (scale_val - 1) * weight
                        double sx = 1.0 + (scaleVal[0] - 1.0) * anim.weight;
                        double sy = 1.0 + (scaleVal[1] - 1.0) * anim.weight;
                        double sz = 1.0 + (scaleVal[2] - 1.0) * anim.weight;
                        part.mulAnimScale(sx, sy, sz);
                    }
                }
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

        applyNativeHandPose(state, entity);

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
        float from = animation.previousTime;
        float to = animation.time;
        for (YsmAnimationEvent event : animation.clip.events) {
            boolean crossed = from <= to
                    ? event.time() > from && event.time() <= to
                    : event.time() > from || event.time() <= to;
            if (!crossed && from == 0f && to > 0f && event.time() == 0f)
                crossed = true;
            if (crossed && animation.initialEventsDispatched && from == 0f && to > 0f && event.time() == 0f)
                continue;
            if (crossed)
                handleEvent(event, evaluator);
        }
    }

    private void dispatchInitialEvents(PlayingAnimation animation, ExpressionEvaluator<?> evaluator) {
        if (animation == null || animation.initialEventsDispatched || animation.clip.events.isEmpty() || evaluator == null)
            return;
        for (YsmAnimationEvent event : animation.clip.events) {
            if (event.time() == 0f)
                handleEvent(event, evaluator);
        }
        animation.initialEventsDispatched = true;
    }

    private void advanceAnimationTime(PlayingAnimation animation) {
        if (animation.loopMode == YsmAnimationClip.LoopMode.LOOP || animation.loop) {
            animation.time = animation.time % animation.clip.length;
            return;
        }
        animation.time = Math.min(animation.time, animation.clip.length);
    }

    private void handleEvent(YsmAnimationEvent event, ExpressionEvaluator<?> evaluator) {
        if ("sound".equals(event.type())) {
            playEventSound(event);
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
            } catch (Exception ignored) {
            }
        }
    }

    private void playEventSound(YsmAnimationEvent event) {
        String id = event.value();
        if (id == null || id.isBlank() || currentEntity == null)
            return;
        try {
            Map<String, String> params = event.params();
            double x = currentEntity.getX() + param(params, "offset_x", "offsetX", "x");
            double y = currentEntity.getY() + param(params, "offset_y", "offsetY", "y");
            double z = currentEntity.getZ() + param(params, "offset_z", "offsetZ", "z");
            double volume = param(params, 1d, "volume", "vol");
            double pitch = param(params, 1d, "pitch");
            boolean loop = boolParam(params, "loop", "looping");
            FiguraVec3 pos = FiguraVec3.of(x, y, z);
            new SoundAPI(runtime.owner()).playSound(id, pos, volume, pitch, loop, null, false);
        } catch (Exception ignored) {
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
            return scaleMagnitude(evaluateStatic(channel.staticValue, channel.staticExpressions, evaluator, new float[]{1f, 1f, 1f})) < 0.0001f;
        for (YsmKeyframe keyframe : channel.keyframes) {
            if (scaleMagnitude(evaluateStatic(keyframe.value, keyframe.expressions, evaluator, new float[]{1f, 1f, 1f})) >= 0.0001f)
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

    private void applyNativeHandPose(LivingEntityRenderState state, LivingEntity entity) {
        if (entity == null || entity.getPose() == Pose.SLEEPING)
            return;

        HumanoidArm mainArm = entity.getMainArm();
        boolean mainLeft = mainArm == HumanoidArm.LEFT;
        boolean usingItem = entity.isUsingItem();
        InteractionHand usedHand = usingItem ? entity.getUsedItemHand() : null;
        InteractionHand swingingHand = entity.swinging ? entity.swingingArm : InteractionHand.MAIN_HAND;
        float attackProgress = Math.max(readFloat(state, "attackAnim", 0f), entity.getAttackAnim(0f));
        float swingAmount = attackProgress > 0f ? (float) Math.sin(Math.sqrt(attackProgress) * Math.PI) : 0f;

        applyNativeArmPose(false, itemForArm(entity, false, mainLeft), usingItem && isHandForArm(usedHand, false, mainLeft), isHandForArm(swingingHand, false, mainLeft), swingAmount);
        applyNativeArmPose(true, itemForArm(entity, true, mainLeft), usingItem && isHandForArm(usedHand, true, mainLeft), isHandForArm(swingingHand, true, mainLeft), swingAmount);
    }

    private void applyNativeArmPose(boolean left, ItemStack item, boolean using, boolean swinging, float swingAmount) {
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
        for (YsmModelPart part : runtime.uniqueParts()) {
            if (runtime.roleOf(part.getName()) == role || runtime.armRoleOf(part.getName()) == role)
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

    private float[] evaluateChannel(YsmAnimationChannel channel, float time, ExpressionEvaluator<?> evaluator, float[] defaultValue) {
        if (channel.keyframes.isEmpty()) {
            return evaluateStatic(channel.staticValue, channel.staticExpressions, evaluator, defaultValue);
        }

        List<YsmKeyframe> kfs = channel.keyframes;
        // If time before first keyframe
        if (time <= kfs.get(0).time) {
            YsmKeyframe first = kfs.get(0);
            return evaluateStatic(first.value, first.expressions, evaluator, defaultValue);
        }
        // If time after last keyframe
        if (time >= kfs.get(kfs.size() - 1).time) {
            YsmKeyframe last = kfs.get(kfs.size() - 1);
            return evaluateStatic(last.value, last.expressions, evaluator, defaultValue);
        }

        // Find correct interval
        YsmKeyframe k1 = kfs.get(0);
        YsmKeyframe k2 = null;
        for (int i = 1; i < kfs.size(); i++) {
            YsmKeyframe curr = kfs.get(i);
            if (time < curr.time) {
                k1 = kfs.get(i - 1);
                k2 = curr;
                break;
            }
        }

        if (k2 == null) {
            return evaluateStatic(k1.value, k1.expressions, evaluator, defaultValue);
        }

        float[] v1 = evaluateStatic(k1.value, k1.expressions, evaluator, defaultValue);
        float[] v2 = evaluateStatic(k2.value, k2.expressions, evaluator, defaultValue);

        float ratio = (time - k1.time) / (k2.time - k1.time);
        if (ratio < 0) ratio = 0;
        if (ratio > 1) ratio = 1;

        if ("step".equalsIgnoreCase(k1.interpolation))
            return v1;
        if ("catmullrom".equalsIgnoreCase(k1.interpolation)) {
            float[] p0 = evaluateStatic(kfs.get(Math.max(0, kfs.indexOf(k1) - 1)).value, kfs.get(Math.max(0, kfs.indexOf(k1) - 1)).expressions, evaluator, defaultValue);
            float[] p3 = evaluateStatic(kfs.get(Math.min(kfs.size() - 1, kfs.indexOf(k2) + 1)).value, kfs.get(Math.min(kfs.size() - 1, kfs.indexOf(k2) + 1)).expressions, evaluator, defaultValue);
            return YsmEasing.catmullRomVec(p0, v1, v2, p3, ratio);
        }
        ratio = YsmEasing.apply(k1.interpolation, ratio);
        float[] res = new float[3];
        res[0] = v1[0] + (v2[0] - v1[0]) * ratio;
        res[1] = v1[1] + (v2[1] - v1[1]) * ratio;
        res[2] = v1[2] + (v2[2] - v1[2]) * ratio;
        return res;
    }

    private float[] evaluateStatic(float[] staticVal, Expression[] staticExprs, ExpressionEvaluator<?> evaluator, float[] defaultValue) {
        float[] result = new float[3];
        System.arraycopy(staticVal != null ? staticVal : defaultValue, 0, result, 0, 3);
        if (staticExprs != null && evaluator != null) {
            for (int i = 0; i < 3; i++) {
                if (staticExprs[i] != null) {
                    try {
                        result[i] = evaluator.evalAsFloat(staticExprs[i]);
                    } catch (Exception ignored) {}
                }
            }
        }
        return result;
    }

    private float getDeltaTime(LivingEntityRenderState state) {
        float age = Float.NaN;
        if (state != null) {
            try {
                age = state.ageInTicks;
            } catch (Throwable ignored) {
            }
        }
        float deltaTicks = Float.isNaN(lastAgeInTicks) || Float.isNaN(age) ? 1f : age - lastAgeInTicks;
        lastAgeInTicks = age;
        if (deltaTicks <= 0f || deltaTicks > 5f)
            deltaTicks = 1f;
        return deltaTicks / 20f;
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

    private String resolveAnimationName(String name) {
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
