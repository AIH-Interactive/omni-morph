package top.aihmc.omnimorph.model.outfit;

import org.joml.Matrix4f;
import org.joml.Matrix4fc;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.IntPredicate;

/** Detached frame matrices in entity-root model units; structural definitions remain shared. */
public final class SkeletonPose {
    public enum NativeUnits {
        MODEL_UNITS(16),
        BLOCKS(1);

        private final float unitsPerBlock;

        NativeUnits(float unitsPerBlock) {
            this.unitsPerBlock = unitsPerBlock;
        }
    }

    private final SkeletonIndex definition;
    private final UUID instanceId;
    private final List<Matrix4f> matrices;
    private final BitSet available;

    private SkeletonPose(SkeletonIndex definition, UUID instanceId, List<Matrix4f> matrices) {
        this(definition, instanceId, matrices, null);
    }

    private SkeletonPose(SkeletonIndex definition, UUID instanceId, List<Matrix4f> matrices, BitSet available) {
        this.definition = definition;
        this.instanceId = instanceId;
        this.matrices = List.copyOf(matrices);
        this.available = available;
    }

    /** nativeToEntity maps the native render/root block space into entity-root block space. */
    public static <T> SkeletonPose fromNative(SkeletonIndex.Binding<T> binding, Map<T, Matrix4f> nativeMatrices,
                                              Matrix4fc nativeToEntity, NativeUnits inputUnits) {
        return fromNative(binding, nativeMatrices, nativeToEntity, inputUnits, true);
    }

    public static <T> SkeletonPose fromNativeSubset(SkeletonIndex.Binding<T> binding, Map<T, Matrix4f> nativeMatrices,
                                                    Matrix4fc nativeToEntity, NativeUnits inputUnits) {
        return fromNative(binding, nativeMatrices, nativeToEntity, inputUnits, false);
    }

    private static <T> SkeletonPose fromNative(SkeletonIndex.Binding<T> binding, Map<T, Matrix4f> nativeMatrices,
                                               Matrix4fc nativeToEntity, NativeUnits inputUnits, boolean complete) {
        Matrix4f conversion = new Matrix4f().scaling(16).mul(nativeToEntity);
        var matrices = new ArrayList<Matrix4f>(binding.parts().size());
        BitSet available = new BitSet(binding.parts().size());
        for (T part : binding.parts()) {
            Matrix4f nativeMatrix = nativeMatrices.get(part);
            if (nativeMatrix == null) {
                OutfitConfig.require(!complete);
                matrices.add(new Matrix4f());
                continue;
            }
            available.set(matrices.size());
            Matrix4f matrix = new Matrix4f(conversion).mul(nativeMatrix).scale(inputUnits.unitsPerBlock / 16);
            for (float value : matrix.get(new float[16])) {
                OutfitConfig.require(Float.isFinite(value));
            }
            matrices.add(matrix);
        }
        return new SkeletonPose(binding.definition(), binding.instanceId(), matrices, complete ? null : available);
    }

    public SkeletonIndex definition() {
        return definition;
    }

    /** Keep stable body references while exposing only bones represented by this native pass. */
    public SkeletonPose restrict(IntPredicate included) {
        BitSet selected = new BitSet(matrices.size());
        for (int index = 0; index < matrices.size(); index++) {
            if ((available == null || available.get(index)) && included.test(index)) {
                selected.set(index);
            }
        }
        return new SkeletonPose(definition, instanceId, matrices, selected);
    }

    public Optional<Matrix4f> matrix(OutfitConfig.BoneRef bone) {
        if (!Objects.equals(instanceId, bone.instanceId())) {
            return Optional.empty();
        }
        return definition.resolve(bone)
                .filter(index -> available == null || available.get(index))
                .map(index -> new Matrix4f(matrices.get(index)));
    }

    public boolean defines(OutfitConfig.BoneRef bone) {
        return Objects.equals(instanceId, bone.instanceId()) && definition.resolve(bone).isPresent();
    }
}
