package top.aihmc.omnimorph.model.ysm.controller;

import top.aihmc.omnimorph.model.ysm.animation.YsmBonePose;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Function;

/** Per-controller output and transition snapshot, independent of clip definitions. */
public final class YsmControllerPoseLayer {
    private Map<String, YsmBonePose> lastPose = Map.of();
    private Map<String, YsmBonePose> outgoing = Map.of();
    private YsmBlendTransition curve;
    private float elapsed;

    /**
     * Starts a pose transition. Like the reference, the state's blend_via_shortest_path flag is parsed
     * but does not affect rotation: rotations always blend by quaternion nlerp.
     */
    public void beginTransition(YsmBlendTransition curve) {
        this.outgoing = lastPose;
        this.curve = curve;
        this.elapsed = 0f;
    }

    public void inheritOutput(YsmControllerPoseLayer child) {
        Map<String, YsmBonePose> combined = new LinkedHashMap<>(lastPose);
        child.lastPose.forEach((bone, pose) -> combined.put(bone,
                YsmBonePose.accumulate(combined.get(bone), pose, 1f)));
        lastPose = Map.copyOf(combined);
    }

    public void advance(float deltaSeconds) {
        if (!Float.isFinite(deltaSeconds) || deltaSeconds < 0f) {
            throw new IllegalArgumentException("Invalid controller transition delta");
        }
        if (curve != null) {
            elapsed = Math.min(curve.duration(), elapsed + deltaSeconds);
        }
    }

    public Map<String, YsmBonePose> compose(Map<String, YsmBonePose> incoming) {
        return compose(incoming, bone -> null, bone -> null);
    }

    /** @param restRotation the bone's rest rotation in degrees, or null when unknown */
    public Map<String, YsmBonePose> compose(Map<String, YsmBonePose> incoming,
                                            Function<String, float[]> restRotation) {
        return compose(incoming, restRotation, bone -> null);
    }

    /**
     * @param currentPose the bone's pose before this layer applied, used for channels the outgoing
     *                    snapshot never wrote; null keeps the identity fallback
     */
    public Map<String, YsmBonePose> compose(Map<String, YsmBonePose> incoming,
                                            Function<String, float[]> restRotation,
                                            Function<String, YsmBonePose> currentPose) {
        if (curve == null || elapsed >= curve.duration()) {
            lastPose = Map.copyOf(incoming);
            outgoing = Map.of();
            curve = null;
            return lastPose;
        }
        float progress = curve.progress(elapsed);
        Map<String, YsmBonePose> result = new LinkedHashMap<>();
        YsmBonePose identity = new YsmBonePose(null, null, null);
        // The reference only writes bones the incoming clip animates; anything left from the old
        // clip returns to the layers below immediately instead of fading toward identity.
        for (String bone : incoming.keySet()) {
            YsmBonePose target = incoming.get(bone);
            YsmBonePose previous = outgoing.getOrDefault(bone, identity);
            // A channel this controller did not write before must start from the bone's current
            // pose. Identity scale 1 would blend a scale-0 hide up into view during the transition.
            YsmBonePose from = YsmBonePose.fillMissingChannels(previous, currentPose.apply(bone));
            YsmBonePose blended = YsmBonePose.blend(from, target, progress, restRotation.apply(bone));
            result.put(bone, YsmBonePose.keepWrittenChannels(blended, target));
        }
        lastPose = Map.copyOf(result);
        return lastPose;
    }
}
