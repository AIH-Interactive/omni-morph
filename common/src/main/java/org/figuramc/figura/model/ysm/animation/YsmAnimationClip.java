package org.figuramc.figura.model.ysm.animation;

import java.util.HashMap;
import java.util.Map;

public class YsmAnimationClip {
    public final String name;
    public final float length;
    public final boolean loop;
    public final Map<String, YsmBoneAnimation> boneAnimations = new HashMap<>();

    public YsmAnimationClip(String name, float length, boolean loop) {
        this.name = name;
        this.length = length;
        this.loop = loop;
    }
}
