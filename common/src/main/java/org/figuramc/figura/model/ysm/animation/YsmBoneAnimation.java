package org.figuramc.figura.model.ysm.animation;

public class YsmBoneAnimation {
    public final String boneName;
    public YsmAnimationChannel position;
    public YsmAnimationChannel rotation;
    public YsmAnimationChannel scale;

    public YsmBoneAnimation(String boneName) {
        this.boneName = boneName;
    }
}
