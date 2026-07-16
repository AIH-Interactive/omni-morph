package org.figuramc.figura.model.ysm.animation;

import java.util.HashMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class YsmAnimationClip {
    public final String name;
    public final float length;
    public final boolean loop;
    public final LoopMode loopMode;
    public final Map<String, YsmBoneAnimation> boneAnimations = new HashMap<>();
    public final List<YsmAnimationEvent> events = new ArrayList<>();

    public YsmAnimationClip(String name, float length, boolean loop) {
        this(name, length, loop ? LoopMode.LOOP : LoopMode.ONCE);
    }

    public YsmAnimationClip(String name, float length, LoopMode loopMode) {
        this.name = name;
        this.length = length;
        this.loopMode = loopMode == null ? LoopMode.ONCE : loopMode;
        this.loop = this.loopMode == LoopMode.LOOP;
    }

    public enum LoopMode {
        ONCE,
        LOOP,
        HOLD_ON_LAST_FRAME
    }
}
