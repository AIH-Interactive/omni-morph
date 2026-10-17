package top.aihmc.omnimorph.render.acceleration;

import java.util.Objects;

/** Owned geometry, pose and draw semantics. Backend support remains operation-specific. */
public record DrawBatch(AccelerationService.Lease lease, MeshHandle mesh, PoseSnapshot pose,
                        DrawSemantics semantics) {
    public DrawBatch(AccelerationService.Lease lease, MeshHandle mesh, PoseSnapshot pose) {
        this(lease, mesh, pose, DrawSemantics.transformOnly());
    }

    public DrawBatch {
        Objects.requireNonNull(lease);
        Objects.requireNonNull(mesh);
        Objects.requireNonNull(pose);
        Objects.requireNonNull(semantics);
    }
}
