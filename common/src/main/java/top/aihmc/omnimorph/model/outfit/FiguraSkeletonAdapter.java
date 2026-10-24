package top.aihmc.omnimorph.model.outfit;

import org.joml.Matrix4fc;
import org.figuramc.figura.model.FiguraModelPart;
import top.aihmc.omnimorph.model.rendering.ImmediateFiguraRenderer;
import org.figuramc.figura.model.rendering.PartFilterScheme;

import java.util.List;
import java.util.UUID;

/** Captures native draw-time pivots instead of consulting last frame's saved world matrices. */
public final class FiguraSkeletonAdapter {
    private final ImmediateFiguraRenderer renderer;
    private final SkeletonIndex.Binding<FiguraModelPart> binding;
    private final OutfitLimits limits;

    public FiguraSkeletonAdapter(ImmediateFiguraRenderer renderer, OutfitConfig.ResourceRef resource, UUID instanceId,
                                 SkeletonIndex.Cache cache, OutfitLimits limits) {
        this.renderer = renderer;
        this.limits = limits;
        // Figura's unnamed synthetic container has the stable native Lua handle "models".
        binding = cache.bind(resource, instanceId, List.of(renderer.root),
                part -> part == renderer.root && part.name.isEmpty() ? "models" : part.name,
                part -> part.children, limits);
    }

    public SkeletonIndex.Binding<FiguraModelPart> binding() {
        return binding;
    }

    public SkeletonPose capture(Matrix4fc nativeToEntity, Runnable nativeDrawing) {
        var matrices = renderer.capturePartPivots(nativeDrawing, limits.maxResourceNodes(), limits.maxDepth());
        return SkeletonPose.fromNative(binding, matrices, nativeToEntity, SkeletonPose.NativeUnits.MODEL_UNITS);
    }

    public SkeletonPose captureArm(Matrix4fc nativeToEntity, boolean left, Runnable nativeDrawing) {
        var captured = renderer.capturePartPose(nativeDrawing, limits.maxResourceNodes(), limits.maxDepth());
        SkeletonPose pose = SkeletonPose.fromNative(binding, captured.matrices(), nativeToEntity,
                SkeletonPose.NativeUnits.MODEL_UNITS);
        PartFilterScheme filter = left ? PartFilterScheme.LEFT_ARM : PartFilterScheme.RIGHT_ARM;
        Boolean[] accepted = new Boolean[binding.parts().size()];
        for (int index = 0; index < accepted.length; index++) {
            int parent = binding.definition().nodes().get(index).parent();
            // Null means the native filter pruned this entire branch; do not unbox it.
            Boolean inherited = parent < 0 ? Boolean.valueOf(filter.initialValue) : accepted[parent];
            accepted[index] = inherited == null ? null
                    : filter.test(captured.parentTypes().get(binding.parts().get(index)), inherited);
        }
        return pose.restrict(index -> Boolean.TRUE.equals(accepted[index]));
    }
}
