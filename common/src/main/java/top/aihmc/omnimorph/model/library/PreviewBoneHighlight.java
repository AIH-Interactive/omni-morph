package top.aihmc.omnimorph.model.library;

import com.mojang.blaze3d.pipeline.DepthStencilState;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.CompareOp;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.resources.Identifier;
import top.aihmc.omnimorph.avatar.Avatar;
import top.aihmc.omnimorph.model.FiguraModelPart;
import top.aihmc.omnimorph.model.PartCustomization;
import top.aihmc.omnimorph.model.rendering.ImmediateFiguraRenderer;
import top.aihmc.omnimorph.model.ysm.YsmGeometry;
import top.aihmc.omnimorph.model.ysm.YsmModelPart;
import top.aihmc.omnimorph.utils.FiguraIdentifier;
import top.aihmc.omnimorph.utils.ui.UIHelper;

import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Optional;

/** One GUI frame's read-only selection. World rendering never consumes these overlays. */
public final class PreviewBoneHighlight {
    private static final Map<Avatar, Object> FRAMES = new IdentityHashMap<>();

    private PreviewBoneHighlight() {}

    public static void capture(Avatar avatar, Object part) {
        if (avatar != null && part != null) {
            FRAMES.put(avatar, part);
        }
    }

    public static boolean selected(Avatar avatar, Object part) {
        return part != null && UIHelper.paperdoll && FRAMES.get(avatar) == part;
    }

    /** Keep the selected subtree intact without changing authored visibility or alpha. */
    public static float opacity(Avatar avatar, Object part) {
        Object selected = UIHelper.paperdoll ? FRAMES.get(avatar) : null;
        if (selected == null || part == null) {
            return 1f;
        }
        for (Object ancestor = part; ancestor != null;) {
            if (ancestor == selected) {
                return 1f;
            }
            ancestor = switch (ancestor) {
                case FiguraModelPart figura -> figura.parent;
                case YsmModelPart ysm -> ysm.getParent();
                default -> null;
            };
        }
        return 0.3f;
    }

    public static void finishFrame() {
        FRAMES.clear();
    }

    public static RenderType translucent(RenderType original, Identifier texture) {
        if (original.hasBlending() || texture == null) {
            return original;
        }
        return original.pipeline().isCull()
                ? RenderTypes.entityTranslucentCullItemTarget(texture) : RenderTypes.entityTranslucent(texture);
    }

    public static void figura(FiguraModelPart part, PartCustomization transform, MultiBufferSource source) {
        var bounds = new Bounds();
        for (var vertices : part.vertices.values()) {
            for (var vertex : vertices) {
                bounds.add(vertex.x, vertex.y, vertex.z);
            }
        }
        if (bounds.empty()) {
            var pivot = part.customization.getPivot().copy().add(part.customization.getOffsetPivot());
            bounds.add(pivot.x - 1, pivot.y - 1, pivot.z - 1);
            bounds.add(pivot.x + 1, pivot.y + 1, pivot.z + 1);
        }
        bounds.draw(transform.copyIntoGlobalPoseStack().last(), source);
    }

    public static void ysm(YsmGeometry.Bone bone, PoseStack stack, MultiBufferSource source) {
        var bounds = new Bounds();
        for (var cube : bone.cubes) {
            for (var quad : cube.quads) {
                var positions = quad.positions();
                for (int index = 0; index < positions.length; index += 3) {
                    bounds.add(positions[index], positions[index + 1], positions[index + 2]);
                }
            }
        }
        if (bounds.empty()) {
            double x = bone.pivot[0] / 16d;
            double y = bone.pivot[1] / 16d;
            double z = bone.pivot[2] / 16d;
            bounds.add(x - 0.0625, y - 0.0625, z - 0.0625);
            bounds.add(x + 0.0625, y + 0.0625, z + 0.0625);
        }
        bounds.draw(stack.last(), source);
    }

    private static final class Bounds {
        private double x0 = Double.POSITIVE_INFINITY;
        private double y0 = Double.POSITIVE_INFINITY;
        private double z0 = Double.POSITIVE_INFINITY;
        private double x1 = Double.NEGATIVE_INFINITY;
        private double y1 = Double.NEGATIVE_INFINITY;
        private double z1 = Double.NEGATIVE_INFINITY;

        void add(double x, double y, double z) {
            if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)) {
                return;
            }
            x0 = Math.min(x0, x);
            y0 = Math.min(y0, y);
            z0 = Math.min(z0, z);
            x1 = Math.max(x1, x);
            y1 = Math.max(y1, y);
            z1 = Math.max(z1, z);
        }

        boolean empty() {
            return x0 == Double.POSITIVE_INFINITY;
        }

        void draw(PoseStack.Pose pose, MultiBufferSource source) {
            ImmediateFiguraRenderer.renderLineBox(pose, source.getBuffer(Lines.TYPE),
                    x0, y0, z0, x1, y1, z1, 1f, 0.75f, 0.1f, 1f);
        }
    }

    private static final class Lines {
        private static final RenderType TYPE = create();

        private static RenderType create() {
            var source = RenderPipelines.LINES;
            var snippet = new RenderPipeline.Snippet(Optional.of(source.getVertexShader()),
                    Optional.of(source.getFragmentShader()), Optional.of(source.getShaderDefines()),
                    Optional.of(source.getSamplers()), Optional.of(source.getUniforms()),
                    Optional.of(source.getColorTargetState()), Optional.ofNullable(source.getDepthStencilState()),
                    Optional.of(source.getPolygonMode()), Optional.of(source.isCull()),
                    Optional.of(source.getVertexFormat()), Optional.of(source.getVertexFormatMode()));
            var pipeline = RenderPipelines.register(RenderPipeline.builder(snippet)
                    .withLocation(new FiguraIdentifier("pipeline/wardrobe_bone_selection"))
                    .withDepthStencilState(new DepthStencilState(CompareOp.ALWAYS_PASS, false))
                    .build());
            return new RenderType("wardrobe_bone_selection", RenderSetup.builder(pipeline).createRenderSetup());
        }
    }
}
