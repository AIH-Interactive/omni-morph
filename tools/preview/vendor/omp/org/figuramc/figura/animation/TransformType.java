package org.figuramc.figura.animation;

import org.joml.Vector3d;
import top.aihmc.omm.preview.PreviewFiguraParts.Part;

public enum TransformType {
    POSITION(Part::animPosition),
    ROTATION(Part::animRotation),
    GLOBAL_ROT(Part::globalAnimRot),
    SCALE(Part::animScale);

    private final ITransform function;

    TransformType(ITransform function) {
        this.function = function;
    }

    public void apply(Part part, Vector3d vec, boolean merge) {
        this.function.apply(part, vec, merge);
    }

    private interface ITransform {
        void apply(Part part, Vector3d vec, boolean merge);
    }
}
