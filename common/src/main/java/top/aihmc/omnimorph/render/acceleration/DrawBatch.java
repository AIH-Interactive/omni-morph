package top.aihmc.omnimorph.render.acceleration;

import top.aihmc.omnimorph.render.RenderLifetime;

import java.util.Objects;

/** Owned geometry, pose and draw semantics. Backend support remains operation-specific. */
public record DrawBatch(RenderLifetime.Lease lease, MeshHandle mesh, PoseSnapshot pose,
                        DrawSemantics semantics) {
    public DrawBatch(RenderLifetime.Lease lease, MeshHandle mesh, PoseSnapshot pose) {
        this(lease, mesh, pose, DrawSemantics.transformOnly());
    }

    public DrawBatch {
        Objects.requireNonNull(lease);
        Objects.requireNonNull(mesh);
        Objects.requireNonNull(pose);
        Objects.requireNonNull(semantics);
    }
}
