package top.aihmc.omnimorph.model.ysm.animation;

import top.aihmc.omnimorph.molang.parser.ast.Expression;
import top.aihmc.omnimorph.molang.runtime.ExpressionEvaluator;
import top.aihmc.omnimorph.molang.runtime.MolangExecutionBudget;
import org.figuramc.figura.avatar.Avatar;

import java.util.List;

/** Pure channel sampling shared by live poses and transition snapshots. */
public final class YsmAnimationSampler {
    private YsmAnimationSampler() {
    }

    public static YsmBonePose sampleBone(YsmBoneAnimation bone, float time, ExpressionEvaluator<?> evaluator) {
        Avatar.MolangContext context = evaluator != null && evaluator.entity() instanceof Avatar.MolangContext value ? value : null;
        try (var boneScope = context == null ? null : context.filters.beginBone(bone.boneName)) {
            return new YsmBonePose(
                    bone.position == null ? null : sampleChannel(bone.position, time, evaluator, new float[3]),
                    bone.rotation == null ? null : sampleChannel(bone.rotation, time, evaluator, new float[3]),
                    bone.scale == null ? null : sampleChannel(bone.scale, time, evaluator, new float[]{1f, 1f, 1f}));
        }
    }

    public static float[] sampleChannel(YsmAnimationChannel channel, float time, ExpressionEvaluator<?> evaluator, float[] defaultValue) {
        if (channel.keyframes.isEmpty()) {
            return sampleStatic(channel.staticValue, channel.staticExpressions, evaluator, defaultValue);
        }

        List<YsmKeyframe> kfs = channel.keyframes;
        // If time before first keyframe
        if (time <= kfs.get(0).time) {
            YsmKeyframe first = kfs.get(0);
            if (time < first.time) {
                return samplePre(first, evaluator, defaultValue);
            }
            return samplePost(first, evaluator, defaultValue);
        }
        // If time after last keyframe
        if (time >= kfs.get(kfs.size() - 1).time) {
            YsmKeyframe last = kfs.get(kfs.size() - 1);
            return samplePost(last, evaluator, defaultValue);
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
            return samplePost(k1, evaluator, defaultValue);
        }

        float[] v1 = samplePost(k1, evaluator, defaultValue);

        float ratio = (time - k1.time) / (k2.time - k1.time);
        if (ratio < 0) {
            ratio = 0;
        }
        if (ratio > 1) {
            ratio = 1;
        }

        if ("step".equalsIgnoreCase(k1.interpolation)) {
            return v1;
        }
        if ("catmullrom".equalsIgnoreCase(k2.interpolation)) {
            int k1Index = kfs.indexOf(k1);
            int k2Index = kfs.indexOf(k2);
            float[] p0 = samplePost(kfs.get(Math.max(0, k1Index - 1)), evaluator, defaultValue);
            float[] p3 = samplePre(kfs.get(Math.min(kfs.size() - 1, k2Index + 1)), evaluator, defaultValue);
            float[] v2 = samplePre(k2, evaluator, defaultValue);
            return YsmEasing.catmullRomVec(p0, v1, v2, p3, ratio);
        }
        ratio = YsmEasing.apply(k1.interpolation, ratio);
        float[] v2 = samplePre(k2, evaluator, defaultValue);
        float[] res = new float[3];
        res[0] = v1[0] + (v2[0] - v1[0]) * ratio;
        res[1] = v1[1] + (v2[1] - v1[1]) * ratio;
        res[2] = v1[2] + (v2[2] - v1[2]) * ratio;
        return res;
    }

    public static float[] samplePost(YsmKeyframe keyframe, ExpressionEvaluator<?> evaluator, float[] defaultValue) {
        return sampleStatic(keyframe.value, keyframe.expressions, evaluator, defaultValue);
    }

    public static float[] samplePre(YsmKeyframe keyframe, ExpressionEvaluator<?> evaluator, float[] defaultValue) {
        return sampleStatic(keyframe.preValue, keyframe.preExpressions, evaluator, defaultValue);
    }

    public static float[] sampleStatic(float[] staticVal, Expression[] staticExprs, ExpressionEvaluator<?> evaluator, float[] defaultValue) {
        float[] result = new float[3];
        System.arraycopy(staticVal != null ? staticVal : defaultValue, 0, result, 0, 3);
        if (staticExprs != null && evaluator != null) {
            for (int i = 0; i < 3; i++) {
                if (staticExprs[i] != null) {
                    try {
                        result[i] = evaluator.evalAsFloat(staticExprs[i]);
                    } catch (MolangExecutionBudget.Exceeded exhausted) {
                        throw exhausted;
                    } catch (Exception ignored) {
                        // Preserve the sampled numeric fallback when expression evaluation fails.
                    }
                }
            }
        }
        return result;
    }

}
