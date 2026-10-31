package top.aihmc.omnimorph.model.outfit;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.PlayerModel;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;

import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/** A vanilla skin supplies real PlayerModel pivots without requiring an empty Avatar. */
public final class VanillaSkeletonAdapter {
    public record Part(String name, ModelPart nativePart, List<Part> children) {
        public Part {
            children = List.copyOf(children);
        }
    }

    private final SkeletonIndex.Binding<Part> binding;

    public VanillaSkeletonAdapter(PlayerModel<?> model, OutfitLimits limits) {
        this(model, limits, null);
    }

    public VanillaSkeletonAdapter(PlayerModel<?> model, OutfitLimits limits, SkeletonIndex.Cache cache) {
        Part root = root(model);
        binding = cache == null ? SkeletonIndex.capture(null, List.of(root), Part::name, Part::children, limits)
                : cache.bind(null, null, List.of(root), Part::name, Part::children, limits);
    }

    /** Same native structural contract for an editor that has not extracted a player pose yet. */
    public static SkeletonIndex.Binding<Part> describe(OutfitLimits limits) {
        return SkeletonIndex.capture(null, List.of(root(null)), Part::name, Part::children, limits);
    }

    private static Part root(PlayerModel<?> model) {
        // MC 1.21.1 player models have no root part; limbs sit directly in model space.
        return new Part("Root", null, List.of(
                leaf("Body", model == null ? null : model.body),
                leaf("Head", model == null ? null : model.head),
                leaf("LeftArm", model == null ? null : model.leftArm),
                leaf("RightArm", model == null ? null : model.rightArm),
                leaf("LeftLeg", model == null ? null : model.leftLeg),
                leaf("RightLeg", model == null ? null : model.rightLeg)));
    }

    private static Part leaf(String name, ModelPart part) {
        return new Part(name, part, List.of());
    }

    public SkeletonIndex.Binding<Part> binding() {
        return binding;
    }

    public SkeletonPose captureArm(Matrix4fc nativeToEntity, boolean left) {
        String arm = left ? "LeftArm" : "RightArm";
        return capture(nativeToEntity).restrict(index -> binding.parts().get(index).name().equals(arm));
    }

    /** Capture after PlayerModel.setupAnim, with the native body transform relative to the entity root. */
    public SkeletonPose capture(Matrix4fc nativeToEntity) {
        Map<Part, Matrix4f> matrices = new IdentityHashMap<>();
        PoseStack stack = new PoseStack();
        for (int index = 0; index < binding.parts().size(); index++) {
            Part part = binding.parts().get(index);
            int parent = binding.definition().nodes().get(index).parent();
            if (parent >= 0) {
                stack.last().pose().set(matrices.get(binding.parts().get(parent)));
            }
            // Parts without a native counterpart (the 1.21.1 root) contribute an identity transform.
            if (part.nativePart() != null) {
                part.nativePart().translateAndRotate(stack);
            }
            matrices.put(part, new Matrix4f(stack.last().pose()));
        }
        return SkeletonPose.fromNative(binding, matrices, nativeToEntity, SkeletonPose.NativeUnits.BLOCKS);
    }
}
