package org.figuramc.figura.model.ysm;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import org.figuramc.figura.math.vector.FiguraVec3;

public class YsmRenderer {
    private final YsmModelRuntime runtime;

    public YsmRenderer(YsmModelRuntime runtime) {
        this.runtime = runtime;
    }

    public boolean render(PoseStack stack, MultiBufferSource bufferSource, int light) {
        return render(stack, bufferSource, light, YsmPartMask.forPass(YsmRenderPass.PLAYER_BODY));
    }

    public boolean render(PoseStack stack, MultiBufferSource bufferSource, int light, YsmPartMask mask) {
        if (runtime.geometry().roots.isEmpty())
            return false;

        runtime.texture().uploadIfDirty(false, false);
        RenderType renderType = RenderTypes.entityCutout(runtime.texture().getLocation());
        VertexConsumer vertices = bufferSource.getBuffer(renderType);

        stack.pushPose();
        stack.scale(0.9375f * runtime.widthScale(), 0.9375f * runtime.heightScale(), 0.9375f * runtime.widthScale());
        runtime.updateWorldMatrices(stack);
        for (YsmGeometry.Bone root : runtime.geometry().roots)
            renderBone(root, stack, vertices, light, mask);
        stack.popPose();
        return true;
    }

    public boolean renderAttachments(PoseStack stack, MultiBufferSource bufferSource, int light) {
        boolean rendered = false;
        rendered |= renderAttachmentType(stack, bufferSource, light, YsmAttachmentType.BACKPACK);
        rendered |= renderAttachmentType(stack, bufferSource, light, YsmAttachmentType.BLADE);
        rendered |= renderAttachmentType(stack, bufferSource, light, YsmAttachmentType.SHEATH);
        rendered |= renderAttachmentType(stack, bufferSource, light, YsmAttachmentType.ELYTRA);
        return rendered;
    }

    public boolean renderAttachmentType(PoseStack stack, MultiBufferSource bufferSource, int light, YsmAttachmentType type) {
        boolean rendered = false;
        for (YsmAttachmentPoint point : runtime.attachmentPoints()) {
            if (point.type() == type)
                rendered |= renderAttachment(stack, bufferSource, light, point);
        }
        return rendered;
    }

    public boolean renderAttachment(PoseStack stack, MultiBufferSource bufferSource, int light, String name) {
        return renderAttachment(stack, bufferSource, light, runtime.getAttachmentPoint(name));
    }

    public boolean renderAttachment(PoseStack stack, MultiBufferSource bufferSource, int light, YsmAttachmentPoint point) {
        if (point == null)
            return false;
        YsmGeometry.Bone bone = findAttachmentBone(point.boneName());
        if (bone == null)
            return false;

        runtime.texture().uploadIfDirty(false, false);
        RenderType renderType = RenderTypes.entityCutout(runtime.texture().getLocation());
        VertexConsumer vertices = bufferSource.getBuffer(renderType);

        stack.pushPose();
        stack.scale(0.9375f * runtime.widthScale(), 0.9375f * runtime.heightScale(), 0.9375f * runtime.widthScale());
        renderBone(bone, stack, vertices, light, null);
        stack.popPose();
        return true;
    }

    private YsmGeometry.Bone findAttachmentBone(String name) {
        if (name == null || name.isBlank())
            return null;
        YsmGeometry.Bone exact = runtime.geometry().bones.get(name);
        if (exact != null)
            return exact;
        for (YsmGeometry.Bone bone : runtime.geometry().bones.values()) {
            if (bone.name.equalsIgnoreCase(name))
                return bone;
        }
        return null;
    }

    public boolean renderFirstPersonArm(PoseStack stack, MultiBufferSource bufferSource, int light, boolean left) {
        YsmGeometry armGeo = runtime.getArmGeometry();
        if (armGeo == null) {
            armGeo = runtime.geometry();
        }

        runtime.texture().uploadIfDirty(false, false);
        RenderType renderType = RenderTypes.entityCutout(runtime.texture().getLocation());
        VertexConsumer vertices = bufferSource.getBuffer(renderType);

        YsmPartMask mask = YsmPartMask.forPass(YsmRenderPass.FIRST_PERSON_ARM);

        stack.pushPose();
        boolean useArmParts = runtime.hasArmModel();
        for (YsmGeometry.Bone root : armGeo.roots) {
            if (shouldRenderArmBone(root, left, useArmParts))
                renderBone(root, stack, vertices, light, mask, useArmParts);
        }
        stack.popPose();
        return true;
    }

    private boolean shouldRenderArmBone(YsmGeometry.Bone bone, boolean left, boolean useArmParts) {
        YsmBoneRole role = useArmParts ? runtime.armRoleOf(bone.name) : runtime.roleOf(bone.name);
        if (role == YsmBoneRole.LEFT_HAND)
            return left;
        if (role == YsmBoneRole.RIGHT_HAND)
            return !left;
        if (role == YsmBoneRole.UNKNOWN || role == YsmBoneRole.FIRST_PERSON_ARM || role == YsmBoneRole.BODY)
            return true;
        String lower = bone.name.toLowerCase(java.util.Locale.US);
        if (lower.contains("left"))
            return left;
        if (lower.contains("right"))
            return !left;
        return role == YsmBoneRole.BODY || role == YsmBoneRole.UNKNOWN;
    }

    private void renderBone(YsmGeometry.Bone bone, PoseStack stack, VertexConsumer vertices, int light, YsmPartMask mask) {
        renderBone(bone, stack, vertices, light, mask, false);
    }

    private void renderBone(YsmGeometry.Bone bone, PoseStack stack, VertexConsumer vertices, int light, YsmPartMask mask, boolean useArmParts) {
        YsmModelPart part = useArmParts ? runtime.getArmPart(bone.name) : runtime.getPart(bone.name);
        YsmBoneRole role = useArmParts ? runtime.armRoleOf(bone.name) : runtime.roleOf(bone.name);
        boolean renderSelf = (part == null || part.visibleRaw()) && (mask == null || mask.allows(role));

        stack.pushPose();
        applyBoneTransform(bone, part, stack);
        if (renderSelf) {
            for (YsmGeometry.Cube cube : bone.cubes)
                renderCube(cube, stack, vertices, light);
        }
        for (YsmGeometry.Bone child : bone.children)
            renderBone(child, stack, vertices, light, mask, useArmParts);
        stack.popPose();
    }

    private void applyBoneTransform(YsmGeometry.Bone bone, YsmModelPart part, PoseStack stack) {
        double px = bone.pivot[0] / 16d;
        double py = bone.pivot[1] / 16d;
        double pz = bone.pivot[2] / 16d;
        stack.translate(px, py, pz);
        rotateBone(stack, bone.rotation[0], bone.rotation[1], bone.rotation[2]);
        if (part != null) {
            FiguraVec3 animPos = part.animPosRaw();
            FiguraVec3 animRot = part.animRotRaw();
            FiguraVec3 animScale = part.animScaleRaw();
            stack.translate(-animPos.x / 16d, animPos.y / 16d, animPos.z / 16d);
            rotateBone(stack, (float) -animRot.x, (float) -animRot.y, (float) animRot.z);
            stack.scale((float) animScale.x, (float) animScale.y, (float) animScale.z);

            FiguraVec3 pos = part.posRaw();
            FiguraVec3 rot = part.rotRaw();
            FiguraVec3 scale = part.scaleRaw();
            stack.translate(-pos.x / 16d, pos.y / 16d, pos.z / 16d);
            rotateBone(stack, (float) -rot.x, (float) -rot.y, (float) rot.z);
            stack.scale((float) scale.x, (float) scale.y, (float) scale.z);
        }
        stack.translate(-px, -py, -pz);
    }

    private void rotateBone(PoseStack stack, float x, float y, float z) {
        if (z != 0f)
            stack.mulPose(Axis.ZP.rotationDegrees(z));
        if (y != 0f)
            stack.mulPose(Axis.YP.rotationDegrees(y));
        if (x != 0f)
            stack.mulPose(Axis.XP.rotationDegrees(x));
    }

    private void renderCube(YsmGeometry.Cube cube, PoseStack stack, VertexConsumer vertices, int light) {
        for (YsmGeometry.Quad quad : cube.quads)
            quad(vertices, stack, quad, light);
    }

    private void quad(VertexConsumer vertices, PoseStack stack, YsmGeometry.Quad quad, int light) {
        float[] positions = quad.positions();
        float[] uvs = quad.uvs();
        float[] normal = quad.normal();
        for (int i = 0; i < 4; i++) {
            int pos = i * 3;
            int uv = i * 2;
            vertex(vertices, stack, positions[pos], positions[pos + 1], positions[pos + 2], uvs[uv], uvs[uv + 1], normal[0], normal[1], normal[2], light);
        }
    }

    private void vertex(VertexConsumer vertices, PoseStack stack, float x, float y, float z, float u, float v, float nx, float ny, float nz, int light) {
        vertices.addVertex(stack.last(), x, y, z)
                .setColor(1f, 1f, 1f, 1f)
                .setUv(u, v)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(light)
                .setNormal(stack.last(), nx, ny, nz);
    }
}
