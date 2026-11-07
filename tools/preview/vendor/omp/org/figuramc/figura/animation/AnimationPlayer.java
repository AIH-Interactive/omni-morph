package org.figuramc.figura.animation;

import top.aihmc.omm.preview.FiguraAnimationMath;

import org.joml.Vector3d;
import top.aihmc.omm.preview.PreviewFiguraParts.Part;

import java.util.List;
import java.util.Map;

public class AnimationPlayer {

    public static int tick(Animation anim, int limit) {
        if (anim.playState == Animation.PlayState.STOPPED)
            return limit;


        if (anim.playState != Animation.PlayState.PAUSED)
            anim.tick();

        for (Map.Entry<Part, List<Animation.AnimationChannel>> entry : anim.animationParts) {
            Part part = entry.getKey();

            if (part.lastAnimationPriority > anim.priority)
                continue;


            boolean merge = part.lastAnimationPriority == anim.priority;
            part.lastAnimationPriority = anim.priority;
            part.animated = true;

            for (Animation.AnimationChannel channel : entry.getValue()) {
                if (limit <= 0) {
                    return limit;
                }

                TransformType type = channel.type();

                Keyframe[] keyframes = channel.keyframes();
                if (keyframes.length == 0) {
                    return limit;
                }

                int currentIndex = Math.max(0, FiguraAnimationMath.binarySearch(0, keyframes.length, index -> anim.frameTime <= keyframes[index].getTime()) - 1);
                int nextIndex = Math.min(keyframes.length - 1, currentIndex + 1);

                Keyframe current = keyframes[currentIndex];
                Keyframe next = keyframes[nextIndex];

                float delta;
                if (current == next) {
                    delta = 0;
                } else {
                    float timeDiff = anim.frameTime - current.getTime();
                    delta = Math.min(Math.max(timeDiff / (next.getTime() - current.getTime()), 0), 1);
                }

                Interpolation interpolation = next.getInterpolation() == Interpolation.BEZIER ? Interpolation.BEZIER : current.getInterpolation();
                Vector3d transform = interpolation.generate(keyframes, currentIndex, nextIndex, anim.blend, delta, type, anim.loop == Animation.LoopMode.LOOP);
                type.apply(part, transform, merge);

                switch (type) {
                    case ROTATION, GLOBAL_ROT -> {
                        if (anim.getOverrideRot())
                            part.animationOverride |= 1;
                        else if (!merge) {
                            part.animationOverride = part.animationOverride & 6;
                        }
                    }
                    case POSITION -> {
                        if (anim.getOverridePos())
                            part.animationOverride |= 2;
                        else if (!merge) {
                            part.animationOverride = part.animationOverride & 5;
                        }
                    }
                    case SCALE -> {
                        if (anim.getOverrideScale())
                            part.animationOverride |= 4;
                        else if (!merge) {
                            part.animationOverride = part.animationOverride & 3;
                        }
                    }
                }

                limit--;
            }

        }

        return limit;
    }

    public static void clear(Animation anim) {
        Vector3d zero = new Vector3d();
        for (Map.Entry<Part, List<Animation.AnimationChannel>> entry : anim.animationParts) {
            Part part = entry.getKey();
            if (!part.animated)
                continue;

            part.animPosition(zero, false);
            part.animRotation(zero, false);
            part.animScale(new Vector3d(1, 1, 1), false);
            part.lastAnimationPriority = Integer.MIN_VALUE;
            part.animated = false;
            part.animationOverride = 0;
        }
    }
}
