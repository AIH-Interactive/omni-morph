package top.aihmc.omnimorph.model.ysm.controller;

import top.aihmc.omnimorph.model.ysm.animation.YsmBonePose;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
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
        return compose(incoming, bone -> null);
    }

    /** @param restRotation the bone's rest rotation in degrees, or null when unknown */
    public Map<String, YsmBonePose> compose(Map<String, YsmBonePose> incoming,
                                            Function<String, float[]> restRotation) {
        if (curve == null || elapsed >= curve.duration()) {
            lastPose = Map.copyOf(incoming);
            outgoing = Map.of();
            curve = null;
            return lastPose;
        }
        float progress = curve.progress(elapsed);
        var bones = new LinkedHashSet<>(outgoing.keySet());
        bones.addAll(incoming.keySet());
        Map<String, YsmBonePose> result = new LinkedHashMap<>();
        YsmBonePose identity = new YsmBonePose(null, null, null);
        for (String bone : bones) {
            result.put(bone, YsmBonePose.blend(outgoing.getOrDefault(bone, identity),
                    incoming.getOrDefault(bone, identity), progress, restRotation.apply(bone)));
        }
        lastPose = Map.copyOf(result);
        return lastPose;
    }
}
