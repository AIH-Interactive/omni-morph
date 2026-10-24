package top.aihmc.omnimorph.model.outfit;

import org.joml.Matrix4f;
import org.figuramc.figura.avatar.Avatar;
import org.figuramc.figura.model.FiguraModelPart;
import top.aihmc.omnimorph.model.rendering.ImmediateFiguraRenderer;
import top.aihmc.omnimorph.model.ysm.YsmModelPart;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;

/** Validated, instance-bound render offsets. Author channels and source geometry remain untouched. */
final class CosmeticBoneOverrides {
    private final Map<FiguraModelPart, Matrix4f> figura;
    private final Map<YsmModelPart, Matrix4f> ysm;

    private CosmeticBoneOverrides(Map<FiguraModelPart, Matrix4f> figura, Map<YsmModelPart, Matrix4f> ysm) {
        this.figura = Map.copyOf(figura);
        this.ysm = Map.copyOf(ysm);
    }

    Map<FiguraModelPart, Matrix4f> figura() {
        return figura;
    }

    Map<YsmModelPart, Matrix4f> ysm() {
        return ysm;
    }

    static CosmeticBoneOverrides bind(Avatar avatar, OutfitConfig.Instance instance, CosmeticMetadata metadata,
                                      boolean authorized, SkeletonIndex.Cache indexes, OutfitLimits limits) {
        var figura = new HashMap<FiguraModelPart, Matrix4f>();
        var ysm = new HashMap<YsmModelPart, Matrix4f>();
        if (metadata != null) {
            OutfitConfig.require(metadata.editableBones().size() <= limits.maxResourceNodes());
            for (var bone : metadata.editableBones()) {
                OutfitConfig.require(bone.path().size() <= limits.maxDepth());
            }
        }
        if (instance.boneOverrides().isEmpty()) {
            return new CosmeticBoneOverrides(figura, ysm);
        }
        if (!authorized || metadata == null || !metadata.hasBones() || !metadata.editBones()) {
            throw new IllegalArgumentException("COSMETIC_BONE_EDIT_FORBIDDEN");
        }
        var editable = new HashSet<>(metadata.editableBones());
        var ysmBinding = avatar.isYsmNative()
                ? new YsmSkeletonAdapter(avatar.getYsmRuntime(), instance.resource(), instance.instanceId(), indexes, limits)
                        .binding() : null;
        var figuraBinding = !avatar.isYsmNative() && avatar.renderer instanceof ImmediateFiguraRenderer renderer
                ? new FiguraSkeletonAdapter(renderer, instance.resource(), instance.instanceId(), indexes, limits)
                        .binding() : null;
        for (var override : instance.boneOverrides()) {
            var relative = new OutfitConfig.BoneRef(null, override.bone().path(), override.bone().skeletonHash());
            if (!editable.contains(relative)) {
                throw new IllegalArgumentException("COSMETIC_BONE_EDIT_OUTSIDE_DECLARATION");
            }
            if (ysmBinding != null) {
                var part = ysmBinding.resolve(override.bone()).orElseThrow(
                        () -> new IllegalArgumentException("COSMETIC_BONE_EDIT_STALE"));
                ysm.put(part, override.transform().modelMatrix());
            } else if (figuraBinding != null) {
                var part = figuraBinding.resolve(override.bone()).orElseThrow(
                        () -> new IllegalArgumentException("COSMETIC_BONE_EDIT_STALE"));
                figura.put(part, override.transform().modelMatrix());
            } else {
                throw new IllegalArgumentException("COSMETIC_BONE_EDIT_UNSUPPORTED");
            }
        }
        return new CosmeticBoneOverrides(figura, ysm);
    }
}
