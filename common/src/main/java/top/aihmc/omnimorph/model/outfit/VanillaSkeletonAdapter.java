package top.aihmc.omnimorph.model.outfit;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.player.PlayerModel;
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

    public VanillaSkeletonAdapter(PlayerModel model, OutfitLimits limits) {
        this(model, limits, null);
    }

    public VanillaSkeletonAdapter(PlayerModel model, OutfitLimits limits, SkeletonIndex.Cache cache) {
        Part root = new Part("Root", model.root(), List.of(
                leaf("Body", model.body), leaf("Head", model.head),
                leaf("LeftArm", model.leftArm), leaf("RightArm", model.rightArm),
                leaf("LeftLeg", model.leftLeg), leaf("RightLeg", model.rightLeg)));
        binding = cache == null ? SkeletonIndex.capture(null, List.of(root), Part::name, Part::children, limits)
                : cache.bind(null, null, List.of(root), Part::name, Part::children, limits);
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
            part.nativePart().translateAndRotate(stack);
            matrices.put(part, new Matrix4f(stack.last().pose()));
        }
        return SkeletonPose.fromNative(binding, matrices, nativeToEntity, SkeletonPose.NativeUnits.BLOCKS);
    }
}
