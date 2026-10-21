package top.aihmc.omnimorph.model.outfit;

import org.joml.Matrix4fc;
import top.aihmc.omnimorph.model.ysm.YsmModelPart;
import top.aihmc.omnimorph.model.ysm.YsmModelRuntime;
import top.aihmc.omnimorph.model.ysm.YsmRenderPose;

import java.util.UUID;

/** Reuses native YSM transform order and the exact submission-time animation pose. */
public final class YsmSkeletonAdapter {
    private final YsmModelRuntime runtime;
    private final SkeletonIndex.Binding<YsmModelPart> binding;

    public YsmSkeletonAdapter(YsmModelRuntime runtime, OutfitConfig.ResourceRef resource, UUID instanceId,
                              SkeletonIndex.Cache cache, OutfitLimits limits) {
        this.runtime = runtime;
        this.binding = cache.bind(resource, instanceId, runtime.rootParts(),
                YsmModelPart::getName, YsmModelPart::getChildren, limits);
    }

    public SkeletonIndex.Binding<YsmModelPart> binding() {
        return binding;
    }

    public SkeletonPose capture(Matrix4fc nativeToEntity, YsmRenderPose pose) {
        return SkeletonPose.fromNative(binding, runtime.captureBonePivots(pose),
                nativeToEntity, SkeletonPose.NativeUnits.BLOCKS);
    }

    public SkeletonPose captureArm(Matrix4fc nativeToEntity, boolean left, YsmRenderPose pose) {
        return SkeletonPose.fromNativeSubset(binding, runtime.captureFirstPersonPivots(left, pose),
                nativeToEntity, SkeletonPose.NativeUnits.BLOCKS);
    }
}
