package top.aihmc.omnimorph.model.outfit;

import org.figuramc.figura.model.FiguraModelPart;
import org.figuramc.figura.model.ParentType;
import org.figuramc.figura.model.PartCustomization;
import org.figuramc.figura.math.matrix.FiguraMat4;
import top.aihmc.omnimorph.model.rendering.ImmediateFiguraRenderer;
import top.aihmc.omnimorph.render.acceleration.AccelerationService;
import org.joml.Matrix4f;
import org.luaj.vm2.LuaFunction;

import java.util.List;
import java.util.Map;

/**
 * Private native-part pose for one retained command; geometry and Lua execution remain native.
 *
 * <p>The submit-time pose is copied once into reusable snapshot objects. At draw time each part is compared
 * against its snapshot without copying; only when something changed in between is the newer pose captured,
 * the submit-time pose borrowed for the draw, and the newer pose restored afterwards.
 */
public final class FiguraRenderPose {
    /** Snapshot objects returned by a released pose for the next pose of the same base to reuse. */
    public static final class Spare {
        private PartState[] states;
    }

    private static final class PartState {
        private FiguraModelPart part;
        private final PartCustomization pose = new PartCustomization();
        private ParentType parentType;
        private boolean animated;
        private int animationOverride;
        private int animationPriority;
        private LuaFunction preRender;
        private LuaFunction midRender;
        private LuaFunction postRender;
        private final PartCustomization saved = new PartCustomization();
        private boolean hasSaved;
        private final PartCustomization player = new PartCustomization();
        private boolean hasPlayer;
        private final FiguraMat4 world = FiguraMat4.of();

        private void capture(FiguraModelPart source) {
            part = source;
            source.customization.copyRenderPoseTo(pose);
            parentType = source.parentType;
            animated = source.animated;
            animationOverride = source.animationOverride;
            animationPriority = source.lastAnimationPriority;
            preRender = source.preRender;
            midRender = source.midRender;
            postRender = source.postRender;
            hasSaved = source.savedCustomization != null;
            if (hasSaved) {
                source.savedCustomization.copyRenderPoseTo(saved);
            }
            hasPlayer = source.playerCustomization != null;
            if (hasPlayer) {
                source.playerCustomization.copyRenderPoseTo(player);
            }
            world.set(source.savedPartToWorldMat);
        }

        /** Compares the snapshot with the part's current state without copying or allocating. */
        private boolean matchesLive() {
            return pose.matchesRenderPose(part.customization) && parentType == part.parentType
                    && animated == part.animated && animationOverride == part.animationOverride
                    && animationPriority == part.lastAnimationPriority && preRender == part.preRender
                    && midRender == part.midRender && postRender == part.postRender
                    && sameOptionalPose(hasSaved, saved, part.savedCustomization)
                    && sameOptionalPose(hasPlayer, player, part.playerCustomization)
                    && PartCustomization.sameMatrix(world, part.savedPartToWorldMat);
        }

        private static boolean sameOptionalPose(boolean present, PartCustomization snapshot, PartCustomization live) {
            return present ? live != null && snapshot.matchesRenderPose(live) : live == null;
        }

        private void apply() {
            pose.copyRenderPoseTo(part.customization);
            part.parentType = parentType;
            part.animated = animated;
            part.animationOverride = animationOverride;
            part.lastAnimationPriority = animationPriority;
            part.preRender = preRender;
            part.midRender = midRender;
            part.postRender = postRender;
            part.savedCustomization = restore(hasSaved, saved, part.savedCustomization);
            part.playerCustomization = restore(hasPlayer, player, part.playerCustomization);
            part.savedPartToWorldMat.set(world);
        }

        /** The part never aliases snapshot objects: its own object is reused or a new one is made. */
        private static PartCustomization restore(boolean present, PartCustomization snapshot, PartCustomization current) {
            if (!present) {
                return null;
            }
            PartCustomization target = current != null ? current : new PartCustomization();
            snapshot.copyRenderPoseTo(target);
            return target;
        }
    }

    private final PartState[] states;
    private final Spare spare;
    private PartState[] live;
    private final AccelerationService.Lease lease;
    private final ImmediateFiguraRenderer renderer;
    private final Map<FiguraModelPart, Matrix4f> overrides;

    public FiguraRenderPose(List<FiguraModelPart> parts, AccelerationService.Lease lease) {
        this(parts, lease, null, Map.of(), null);
    }

    public FiguraRenderPose(List<FiguraModelPart> parts, AccelerationService.Lease lease,
                             ImmediateFiguraRenderer renderer, Map<FiguraModelPart, Matrix4f> overrides) {
        this(parts, lease, renderer, overrides, null);
    }

    /** {@code spare} may supply snapshot objects released by an earlier pose; it receives them back on release. */
    public FiguraRenderPose(List<FiguraModelPart> parts, AccelerationService.Lease lease,
                             ImmediateFiguraRenderer renderer, Map<FiguraModelPart, Matrix4f> overrides, Spare spare) {
        this.spare = spare;
        states = takeStates(spare, parts.size());
        for (int index = 0; index < states.length; index++) {
            states[index].capture(parts.get(index));
        }
        this.lease = lease;
        this.renderer = renderer;
        var copied = new java.util.HashMap<FiguraModelPart, Matrix4f>();
        overrides.forEach((part, matrix) -> copied.put(part, new Matrix4f(matrix)));
        this.overrides = Map.copyOf(copied);
    }

    private static PartState[] takeStates(Spare spare, int count) {
        if (spare != null && spare.states != null && spare.states.length == count) {
            PartState[] reused = spare.states;
            spare.states = null;
            return reused;
        }
        return newStates(count);
    }

    private static PartState[] newStates(int count) {
        PartState[] created = new PartState[count];
        for (int index = 0; index < count; index++) {
            created[index] = new PartState();
        }
        return created;
    }

    /** Test hook: whether two poses use the same snapshot objects (reuse through a {@link Spare}). */
    boolean sharesSnapshotsWith(FiguraRenderPose other) {
        return states == other.states;
    }

    /** Hands the snapshot objects back once this pose will never draw again. */
    public void release() {
        if (spare != null && spare.states == null) {
            spare.states = states;
        }
    }

    public void draw(Runnable nativeDrawing) {
        if (!AccelerationService.INSTANCE.isCurrent(lease)) {
            return;
        }
        boolean unchanged = true;
        for (PartState state : states) {
            if (!state.matchesLive()) {
                unchanged = false;
                break;
            }
        }
        if (!unchanged) {
            // A callback changed parts after submit: keep the newer pose to restore after this draw.
            if (live == null) {
                live = newStates(states.length);
            }
            for (int index = 0; index < states.length; index++) {
                live[index].capture(states[index].part);
            }
            for (PartState state : states) {
                state.apply();
            }
        }
        boolean completed = false;
        try {
            if (renderer == null || overrides.isEmpty()) {
                nativeDrawing.run();
            } else {
                renderer.withOutfitBoneOverrides(overrides, nativeDrawing);
            }
            completed = true;
        } finally {
            // Keep normal native callback edits when this is still the current pose. An older
            // command must instead return the instance to the newer pose it temporarily borrowed.
            if (!unchanged) {
                for (PartState state : live) {
                    state.apply();
                }
            }
        }
    }
}
