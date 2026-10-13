package org.figuramc.figura.model.ysm.controller;

import org.figuramc.figura.model.ysm.animation.YsmBonePose;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;

/** Per-controller output and transition snapshot, independent of clip definitions. */
public final class YsmControllerPoseLayer {
    private Map<String, YsmBonePose> lastPose = Map.of();
    private Map<String, YsmBonePose> outgoing = Map.of();
    private YsmBlendTransition curve;
    private float elapsed;
    private boolean shortestRotation;

    public void beginTransition(YsmBlendTransition curve, boolean shortestRotation) {
        this.outgoing = lastPose;
        this.curve = curve;
        this.elapsed = 0f;
        this.shortestRotation = shortestRotation;
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
                    incoming.getOrDefault(bone, identity), progress, shortestRotation));
        }
        lastPose = Map.copyOf(result);
        return lastPose;
    }
}
