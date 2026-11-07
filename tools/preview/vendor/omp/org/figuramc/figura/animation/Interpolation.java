package org.figuramc.figura.animation;

import org.joml.Vector3d;
import top.aihmc.omm.preview.FiguraAnimationMath;

public enum Interpolation {

    LINEAR((frames, currentFrame, targetFrame, strength, delta, type, loop) -> {
        Vector3d prev = frames[currentFrame].getTargetB(delta);
        Vector3d next = frames[targetFrame].getTargetA(delta);
        Vector3d result = FiguraAnimationMath.lerp(delta, prev, next);
        return getResult(result, strength, type);
    }),
    CATMULLROM((frames, currentFrame, targetFrame, strength, delta, type, loop) -> {
        Keyframe prev = frames[currentFrame];
        Keyframe next = frames[targetFrame];
        Keyframe prevPrev = frames[Math.max(0, currentFrame - 1)];
        Keyframe nextNext = frames[Math.min(frames.length - 1, targetFrame + 1)];

        if (prevPrev == prev)
            prevPrev = frames[frames.length - 1];
        if (nextNext == next && loop)
            nextNext = frames[0];

        Vector3d p0 = prevPrev.getTargetB(delta);
        Vector3d p1 = prev.getTargetB(delta);
        Vector3d p2 = next.getTargetA(delta);
        Vector3d p3 = nextNext.getTargetA(delta);

        Vector3d result = FiguraAnimationMath.catmullrom(delta, p0, p1, p2, p3);
        return getResult(result, strength, type);
    }),
    BEZIER((frames, currentFrame, targetFrame, strength, delta, type, loop) -> {
        Keyframe prev = frames[currentFrame];
        Keyframe next = frames[targetFrame];

        Vector3d p1Time = prev.getBezierRightTime();
        Vector3d p2Time = next.getBezierLeftTime();

        Vector3d p0 = prev.getTargetB(delta);
        Vector3d p3 = next.getTargetA(delta);
        Vector3d p1 = prev.getBezierRight().add(p0);
        Vector3d p2 = next.getBezierLeft().add(p3);

        Vector3d result = new Vector3d(
                FiguraAnimationMath.bezier(FiguraAnimationMath.bezierFindT(delta, 0, p1Time.x, p2Time.x, 1), p0.x, p1.x, p2.x, p3.x),
                FiguraAnimationMath.bezier(FiguraAnimationMath.bezierFindT(delta, 0, p1Time.y, p2Time.y, 1), p0.y, p1.y, p2.y, p3.y),
                FiguraAnimationMath.bezier(FiguraAnimationMath.bezierFindT(delta, 0, p1Time.z, p2Time.z, 1), p0.z, p1.z, p2.z, p3.z)
        );

        return getResult(result, strength, type);
    }),
    STEP((frames, currentFrame, targetFrame, strength, delta, type, loop) -> getResult(frames[currentFrame].getTargetB(delta), strength, type));

    private final IInterpolation function;

    Interpolation(IInterpolation function) {
        this.function = function;
    }

    private static Vector3d getResult(Vector3d result, float strength, TransformType type) {
        return type == TransformType.SCALE ? result.sub(1, 1, 1).mul(strength).add(1, 1, 1) : result.mul(strength);
    }

    public Vector3d generate(Keyframe[] keyframes, int currentFrame, int targetFrame, float strength, float delta, TransformType type, boolean loop) {
        return this.function.generate(keyframes, currentFrame, targetFrame, strength, delta, type, loop);
    }

    private interface IInterpolation {
        Vector3d generate(Keyframe[] keyframes, int currentFrame, int targetFrame, float strength, float delta, TransformType type, boolean loop);
    }
}
