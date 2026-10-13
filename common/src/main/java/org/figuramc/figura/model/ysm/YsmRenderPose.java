package org.figuramc.figura.model.ysm;

import com.mojang.blaze3d.vertex.PoseStack;
import org.figuramc.figura.math.vector.FiguraVec3;
import org.joml.Matrix4f;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/** Immutable submission-time pose, independent of later animation passes and Lua part edits. */
public final class YsmRenderPose {
    private final Map<String, PartPose> parts;
    private final Matrix4f modelScale;

    private record Vector(double x, double y, double z) {
        private static Vector capture(FiguraVec3 value) {
            return new Vector(value.x, value.y, value.z);
        }
    }

    private record PartPose(String name, Vector position, Vector rotation, Vector scale,
                            Vector animationPosition, Vector animationRotation, Vector animationScale,
                            boolean visible) {
        private static PartPose capture(YsmModelPart part) {
            return new PartPose(part.getName(), Vector.capture(part.posRaw()), Vector.capture(part.rotRaw()),
                    Vector.capture(part.scaleRaw()), Vector.capture(part.animPosRaw()), Vector.capture(part.animRotRaw()),
                    Vector.capture(part.animScaleRaw()), part.visibleRaw());
        }

        private YsmModelPart detachedPart() {
            YsmModelPart part = new YsmModelPart(name, null, new float[3], new float[3]);
            part.setPos(position.x(), position.y(), position.z());
            part.setRot(rotation.x(), rotation.y(), rotation.z());
            part.setScale(scale.x(), scale.y(), scale.z());
            part.addAnimPos(animationPosition.x(), animationPosition.y(), animationPosition.z());
            part.addAnimRot(animationRotation.x(), animationRotation.y(), animationRotation.z());
            part.mulAnimScale(animationScale.x(), animationScale.y(), animationScale.z());
            part.setVisible(visible);
            return part;
        }
    }

    public static YsmRenderPose capture(Iterable<YsmModelPart> source, Matrix4f modelScale) {
        Map<String, PartPose> parts = new LinkedHashMap<>();
        for (YsmModelPart part : source) {
            PartPose pose = PartPose.capture(part);
            parts.put(part.getName(), pose);
            parts.putIfAbsent(part.getName().toLowerCase(Locale.US), pose);
        }
        return new YsmRenderPose(parts, modelScale);
    }

    private YsmRenderPose(Map<String, PartPose> parts, Matrix4f modelScale) {
        this.parts = Map.copyOf(parts);
        this.modelScale = new Matrix4f(modelScale);
    }

    YsmModelPart part(String name) {
        PartPose pose = parts.get(name);
        if (pose == null && name != null) {
            pose = parts.get(name.toLowerCase(Locale.US));
        }
        return pose == null ? null : pose.detachedPart();
    }

    void applyModelScale(PoseStack stack) {
        stack.mulPose(new Matrix4f(modelScale));
    }

    boolean visibleInHierarchy(String name, YsmGeometry geometry) {
        YsmGeometry.Bone bone = geometry.bones.get(name);
        while (bone != null) {
            YsmModelPart part = part(bone.name);
            if (part != null && (!part.visibleRaw() || part.hiddenScaleRaw())) {
                return false;
            }
            bone = geometry.bones.get(bone.parentName);
        }
        return true;
    }
}
