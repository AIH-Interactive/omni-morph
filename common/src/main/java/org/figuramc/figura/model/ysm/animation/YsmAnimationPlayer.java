package org.figuramc.figura.model.ysm.animation;

import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Pose;
import org.figuramc.figura.avatar.Avatar;
import org.figuramc.figura.molang.parser.ast.Expression;
import org.figuramc.figura.molang.runtime.ExpressionEvaluator;
import org.figuramc.figura.model.ysm.YsmModelPart;
import org.figuramc.figura.model.ysm.YsmModelRuntime;

import java.util.*;

public class YsmAnimationPlayer {
    private final YsmModelRuntime runtime;
    private final Map<String, YsmAnimationClip> clips = new HashMap<>();
    private final Map<String, PlayingAnimation> activeAnimations = new LinkedHashMap<>();
    private final Set<String> disabledNativeAnimations = new HashSet<>();
    private float lastAgeInTicks = Float.NaN;

    public static class PlayingAnimation {
        public final YsmAnimationClip clip;
        public float time;
        public float speed = 1f;
        public float weight = 1f;
        public boolean loop;
        public final boolean isNative;

        // For smooth transitions in native state machine
        public float targetWeight = 0f;

        public PlayingAnimation(YsmAnimationClip clip, boolean isNative) {
            this.clip = clip;
            this.isNative = isNative;
            this.loop = clip.loop;
            this.targetWeight = isNative ? 0f : 1f;
            this.weight = isNative ? 0f : 1f;
        }
    }

    private boolean nativeStateMachineEnabled = true;

    public YsmAnimationPlayer(YsmModelRuntime runtime) {
        this.runtime = runtime;
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

    public Map<String, PlayingAnimation> getActiveAnimations() {
        return activeAnimations;
    }

    public PlayingAnimation play(String name, boolean loop, float speed) {
        String normalized = normalizeName(name);
        YsmAnimationClip clip = clips.get(normalized);
        if (clip == null) return null;

        PlayingAnimation playing = activeAnimations.computeIfAbsent(normalized, k -> new PlayingAnimation(clip, false));
        playing.loop = loop;
        playing.speed = speed;
        playing.targetWeight = 1f;
        playing.weight = 1f;
        return playing;
    }

    public void stop(String name) {
        activeAnimations.remove(normalizeName(name));
    }

    public void update(LivingEntityRenderState state, LivingEntity entity) {
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
            if (anim.isNative) {
                if (anim.weight < anim.targetWeight) {
                    anim.weight = Math.min(anim.targetWeight, anim.weight + deltaTime * 5f); // 0.2s transition
                } else if (anim.weight > anim.targetWeight) {
                    anim.weight = Math.max(anim.targetWeight, anim.weight - deltaTime * 5f);
                }

                // If native animation faded out, remove it to save CPU
                if (anim.weight <= 0f && anim.targetWeight <= 0f) {
                    it.remove();
                    continue;
                }
            }

            // Advance time
            anim.time += deltaTime * anim.speed;
            if (anim.clip.length > 0) {
                if (anim.loop) {
                    anim.time = anim.time % anim.clip.length;
                } else {
                    anim.time = Math.min(anim.time, anim.clip.length);
                }
            }
        }

        Avatar.MolangContext molangContext = runtime.owner().getMolangContext();

        // 3. Reset and apply animations to parts
        for (YsmModelPart part : runtime.uniqueParts()) {
            part.resetAnimPose();
        }

        ExpressionEvaluator<?> evaluator = molangContext != null ? ExpressionEvaluator.evaluator(molangContext) : ExpressionEvaluator.evaluator(runtime.owner());

        for (PlayingAnimation anim : activeAnimations.values()) {
            if (anim.weight <= 0f) continue;

            float t = anim.time;
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
                        // Scale lerps with weight: final_scale = 1 + (scale_val - 1) * weight
                        double sx = 1.0 + (scaleVal[0] - 1.0) * anim.weight;
                        double sy = 1.0 + (scaleVal[1] - 1.0) * anim.weight;
                        double sz = 1.0 + (scaleVal[2] - 1.0) * anim.weight;
                        part.mulAnimScale(sx, sy, sz);
                    }
                }
            }
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

        // Linear interpolation
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
        return normalized.toLowerCase(Locale.US);
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
}
