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

/** Private native-part pose for one retained command; geometry and Lua execution remain native. */
public final class FiguraRenderPose {
    private record PartState(FiguraModelPart part, PartCustomization pose, ParentType parentType,
                             boolean animated, int animationOverride, int animationPriority,
                             LuaFunction preRender, LuaFunction midRender, LuaFunction postRender,
                             PartCustomization saved, PartCustomization player, FiguraMat4 world) {
        private static PartState capture(FiguraModelPart part) {
            return new PartState(part, copy(part.customization), part.parentType, part.animated,
                    part.animationOverride, part.lastAnimationPriority, part.preRender, part.midRender,
                    part.postRender, copy(part.savedCustomization), copy(part.playerCustomization),
                    part.savedPartToWorldMat.copy());
        }

        private static PartCustomization copy(PartCustomization source) {
            if (source == null) {
                return null;
            }
            PartCustomization result = new PartCustomization();
            source.copyRenderPoseTo(result);
            return result;
        }

        private boolean matches(PartState other) {
            return part == other.part && pose.matchesRenderPose(other.pose) && parentType == other.parentType
                    && animated == other.animated && animationOverride == other.animationOverride
                    && animationPriority == other.animationPriority && preRender == other.preRender
                    && midRender == other.midRender && postRender == other.postRender
                    && sameOptionalPose(saved, other.saved) && sameOptionalPose(player, other.player)
                    && world.toMatrix4f().equals(other.world.toMatrix4f());
        }

        private static boolean sameOptionalPose(PartCustomization first, PartCustomization second) {
            return first == null ? second == null : second != null && first.matchesRenderPose(second);
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
            part.savedCustomization = copy(saved);
            part.playerCustomization = copy(player);
            part.savedPartToWorldMat.set(world);
        }
    }

    private final List<PartState> states;
    private final AccelerationService.Lease lease;
    private final ImmediateFiguraRenderer renderer;
    private final Map<FiguraModelPart, Matrix4f> overrides;

    public FiguraRenderPose(List<FiguraModelPart> parts, AccelerationService.Lease lease) {
        this(parts, lease, null, Map.of());
    }

    public FiguraRenderPose(List<FiguraModelPart> parts, AccelerationService.Lease lease,
                             ImmediateFiguraRenderer renderer, Map<FiguraModelPart, Matrix4f> overrides) {
        states = parts.stream().map(PartState::capture).toList();
        this.lease = lease;
        this.renderer = renderer;
        var copied = new java.util.HashMap<FiguraModelPart, Matrix4f>();
        overrides.forEach((part, matrix) -> copied.put(part, new Matrix4f(matrix)));
        this.overrides = Map.copyOf(copied);
    }

    public void draw(Runnable nativeDrawing) {
        if (!AccelerationService.INSTANCE.isCurrent(lease)) {
            return;
        }
        List<PartState> live = states.stream().map(state -> PartState.capture(state.part())).toList();
        boolean unchanged = true;
        for (int index = 0; index < states.size(); index++) {
            unchanged &= states.get(index).matches(live.get(index));
        }
        states.forEach(PartState::apply);
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
            if (!unchanged || !completed) {
                live.forEach(PartState::apply);
            }
        }
    }
}
