package top.aihmc.omnimorph.model.outfit;

import org.joml.Matrix4f;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Reproduces the Alpha.7 NeoForge crash (INVALID_OUTFIT_CONFIGURATION from SkeletonPose.fromNative)
 * where a first-person arm renderer such as TaCZ hands over a pose that cannot be inverted.
 */
public final class FirstPersonArmPoseSmokeTest {
    private record Part(String name, List<Part> children) {
    }

    public static void main(String[] args) {
        var root = new Part("models", List.of());
        var resource = new OutfitConfig.ResourceRef(OutfitConfig.Source.LOCAL, UUID.randomUUID(), null,
                null, null, "8".repeat(64), OutfitConfig.Format.FIGURA);
        var binding = new SkeletonIndex.Cache().bind(resource, null, List.of(root), Part::name, Part::children,
                OutfitLimits.defaults());
        Map<Part, Matrix4f> captured = Map.of(root, new Matrix4f().translation(0, 1, 0));

        // A regular hand pose converts and captures normally.
        var handPose = new Matrix4f().translation(0.5f, -0.6f, -0.7f).rotateY(0.4f);
        var regular = SkeletonPose.nativeToEntity(handPose);
        require(regular.isPresent(), "a regular hand pose has an inverse");
        require(regular.get().equals(new Matrix4f(handPose).invert(), 1e-5f), "the inverse is exact");
        require(SkeletonPose.fromNative(binding, captured, regular.get(), SkeletonPose.NativeUnits.MODEL_UNITS)
                .capturedCount() == 1, "a regular arm pose is captured");

        // A collapsed arm pose (scale 0) has no inverse; inverting it anyway reproduces the crash.
        var collapsed = new Matrix4f().translation(0.5f, -0.6f, -0.7f).scale(0);
        boolean crashed = false;
        try {
            SkeletonPose.fromNative(binding, captured, new Matrix4f(collapsed).invert(),
                    SkeletonPose.NativeUnits.MODEL_UNITS);
        } catch (IllegalArgumentException failure) {
            crashed = failure.getMessage().equals("INVALID_OUTFIT_CONFIGURATION");
        }
        require(crashed, "the raw inverse of a collapsed pose fails the finite-matrix check, as in the crash log");
        require(SkeletonPose.nativeToEntity(collapsed).isEmpty(), "a collapsed pose is reported as not invertible");
        require(SkeletonPose.nativeToEntity(new Matrix4f().scale(1e-30f)).isEmpty(),
                "a nearly collapsed pose whose inverse overflows is not invertible");
        var broken = new Matrix4f().m30(Float.NaN);
        require(SkeletonPose.nativeToEntity(broken).isEmpty(), "a pose with NaN is not invertible");
        System.out.println("PASS first-person arm pose: collapsed or broken native poses no longer reach the capture");
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
