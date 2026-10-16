package org.figuramc.figura.model.ysm;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.resources.Identifier;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import org.figuramc.figura.math.vector.FiguraVec3;
import org.figuramc.figura.model.rendering.EntityRenderMode;

public class YsmRenderer {
    private final YsmModelRuntime runtime;
    private Identifier culledTranslucentTexture;
    private RenderType culledTranslucentType;
    private YsmPreviewRenderTypes previewTypes;

    public YsmRenderer(YsmModelRuntime runtime) {
        this.runtime = runtime;
    }

    public boolean render(PoseStack stack, MultiBufferSource bufferSource, int light) {
        return render(stack, bufferSource, light, YsmPartMask.forPass(YsmRenderPass.PLAYER_BODY));
    }

    public boolean render(PoseStack stack, MultiBufferSource bufferSource, int light, YsmPartMask mask) {
        return render(stack, bufferSource, light, mask, null);
    }

    public boolean render(PoseStack stack, MultiBufferSource bufferSource, int light, YsmPartMask mask, YsmRenderPose pose) {
        if (runtime.geometry().roots.isEmpty())
            return false;

        runtime.texture().uploadIfDirty(false, false);
        RenderType renderType = renderType(pose);
        VertexConsumer vertices = bufferSource.getBuffer(renderType);

        updateTrackedPivots(false, pose);
        stack.pushPose();
        applyModelScale(stack, pose);
        runtime.updateWorldMatrices(stack, pose);
        for (YsmGeometry.Bone root : runtime.geometry().roots)
            renderBone(root, stack, vertices, light, mask, false, pose);
        stack.popPose();
        return true;
    }

    /** Native item/model-part features use order zero, including special-rendered hand items. */
    public int bodySubmitOrder() {
        return runtime.isRenderLayersFirst() ? 1 : -1;
    }

    public boolean renderWithAttachments(PoseStack stack, MultiBufferSource bufferSource, int light,
                                         YsmRenderPose pose) {
        boolean rendered = false;
        if (runtime.isRenderLayersFirst()) {
            rendered |= renderAttachments(stack, bufferSource, light, pose);
        }
        rendered |= render(stack, bufferSource, light, YsmPartMask.forPass(YsmRenderPass.PLAYER_BODY), pose);
        if (!runtime.isRenderLayersFirst()) {
            rendered |= renderAttachments(stack, bufferSource, light, pose);
        }
        return rendered;
    }

    public boolean renderSubEntity(PoseStack stack, MultiBufferSource bufferSource, int light, YsmSubEntity entity) {
        if (entity == null || entity.geometry().roots.isEmpty())
            return false;

        entity.texture().uploadIfDirty(false, false);
        RenderType renderType = entity.isTextureTranslucent()
                ? RenderTypes.entityTranslucent(entity.texture().getLocation())
                : RenderTypes.entityCutout(entity.texture().getLocation());
        VertexConsumer vertices = bufferSource.getBuffer(renderType);

        stack.pushPose();
        stack.scale(0.9375f, 0.9375f, 0.9375f);
        for (YsmGeometry.Bone root : entity.geometry().roots)
            renderStaticBone(root, stack, vertices, light);
        stack.popPose();
        return true;
    }

    public boolean renderAttachments(PoseStack stack, MultiBufferSource bufferSource, int light) {
        return renderAttachments(stack, bufferSource, light, null);
    }

    public boolean renderAttachments(PoseStack stack, MultiBufferSource bufferSource, int light, YsmRenderPose pose) {
        boolean rendered = false;
        rendered |= renderAttachmentType(stack, bufferSource, light, YsmAttachmentType.BACKPACK, pose);
        rendered |= renderAttachmentType(stack, bufferSource, light, YsmAttachmentType.BLADE, pose);
        rendered |= renderAttachmentType(stack, bufferSource, light, YsmAttachmentType.SHEATH, pose);
        rendered |= renderAttachmentType(stack, bufferSource, light, YsmAttachmentType.ELYTRA, pose);
        return rendered;
    }

    public boolean renderAttachmentType(PoseStack stack, MultiBufferSource bufferSource, int light, YsmAttachmentType type) {
        return renderAttachmentType(stack, bufferSource, light, type, null);
    }

    private boolean renderAttachmentType(PoseStack stack, MultiBufferSource bufferSource, int light,
                                          YsmAttachmentType type, YsmRenderPose pose) {
        boolean rendered = false;
        for (YsmAttachmentPoint point : runtime.attachmentPoints()) {
            if (point.type() == type)
                rendered |= renderAttachment(stack, bufferSource, light, point, pose);
        }
        return rendered;
    }

    public boolean renderAttachment(PoseStack stack, MultiBufferSource bufferSource, int light, String name) {
        return renderAttachment(stack, bufferSource, light, runtime.getAttachmentPoint(name));
    }

    public boolean renderAttachment(PoseStack stack, MultiBufferSource bufferSource, int light, YsmAttachmentPoint point) {
        return renderAttachment(stack, bufferSource, light, point, null);
    }

    private boolean renderAttachment(PoseStack stack, MultiBufferSource bufferSource, int light,
                                      YsmAttachmentPoint point, YsmRenderPose pose) {
        if (point == null)
            return false;
        YsmGeometry.Bone bone = findAttachmentBone(point.boneName());
        if (bone == null)
            return false;
        if (!(pose == null ? runtime.isBoneVisibleInHierarchy(bone.name)
                : pose.visibleInHierarchy(bone.name, runtime.geometry())))
            return false;

        runtime.texture().uploadIfDirty(false, false);
        RenderType renderType = renderType(pose);
        VertexConsumer vertices = bufferSource.getBuffer(renderType);

        updateTrackedPivots(false, pose);
        stack.pushPose();
        applyModelScale(stack, pose);
        renderBone(bone, stack, vertices, light, null, false, pose);
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
        return renderFirstPersonArm(stack, bufferSource, light, left, null);
    }

    public boolean renderFirstPersonArm(PoseStack stack, MultiBufferSource bufferSource, int light,
                                         boolean left, YsmRenderPose pose) {
        YsmGeometry armGeo = runtime.getArmGeometry();
        if (armGeo == null) {
            armGeo = runtime.geometry();
        }

        runtime.texture().uploadIfDirty(false, false);
        RenderType renderType = renderType(pose);
        VertexConsumer vertices = bufferSource.getBuffer(renderType);

        YsmPartMask mask = YsmPartMask.forPass(YsmRenderPass.FIRST_PERSON_ARM);

        updateTrackedPivots(true, pose);
        stack.pushPose();
        boolean useArmParts = runtime.hasArmModel();
        for (YsmGeometry.Bone root : armGeo.roots) {
            if (shouldRenderArmBone(root, left, useArmParts))
                renderBone(root, stack, vertices, light, mask, useArmParts, pose);
        }
        stack.popPose();
        return true;
    }

    private boolean shouldRenderArmBone(YsmGeometry.Bone bone, boolean left, boolean useArmParts) {
        YsmBoneRole role = useArmParts ? runtime.armRoleOf(bone.name) : runtime.roleOf(bone.name);
        return YsmBoneMapper.isFirstPersonArmCandidate(bone.name, role, left);
    }

    private RenderType renderType() {
        return renderType(null);
    }

    private RenderType renderType(YsmRenderPose pose) {
        Identifier texture = runtime.texture().getLocation();
        boolean unlit = pose == null
                ? runtime.isGuiNoLighting() && runtime.owner().renderMode == EntityRenderMode.FIGURA_GUI
                : pose.unlit();
        if (unlit) {
            if (previewTypes == null) {
                previewTypes = new YsmPreviewRenderTypes();
            }
            return previewTypes.get(texture, runtime.isAllCutout(), runtime.isTextureTranslucent());
        }
        if (!runtime.isAllCutout()) {
            return runtime.isTextureTranslucent()
                    ? RenderTypes.entityTranslucent(texture) : RenderTypes.entityCutout(texture);
        }
        if (!runtime.isTextureTranslucent()) {
            return RenderTypes.entityCutoutCull(texture);
        }
        if (!texture.equals(culledTranslucentTexture)) {
            // all_cutout forces back-face culling, while retaining alpha blending and the world target.
            // The vanilla translucent-cull helper uses the item target, so it cannot serve this pass.
            RenderType base = RenderTypes.entityTranslucent(texture);
            culledTranslucentType = new RenderType("ysm_entity_translucent_cull",
                    RenderSetup.builder(RenderPipelines.ENTITY_TRANSLUCENT_CULL)
                            .withTexture("Sampler0", texture)
                            .setOutputTarget(base.outputTarget())
                            .useLightmap()
                            .useOverlay()
                            .affectsCrumbling()
                            .sortOnUpload()
                            .setOutline(RenderSetup.OutlineProperty.AFFECTS_OUTLINE)
                            .createRenderSetup());
            culledTranslucentTexture = texture;
        }
        return culledTranslucentType;
    }

    private void renderBone(YsmGeometry.Bone bone, PoseStack stack, VertexConsumer vertices, int light, YsmPartMask mask) {
        renderBone(bone, stack, vertices, light, mask, false);
    }

    private void renderBone(YsmGeometry.Bone bone, PoseStack stack, VertexConsumer vertices, int light, YsmPartMask mask, boolean useArmParts) {
        renderBone(bone, stack, vertices, light, mask, useArmParts, null);
    }

    private void renderBone(YsmGeometry.Bone bone, PoseStack stack, VertexConsumer vertices, int light,
                             YsmPartMask mask, boolean useArmParts, YsmRenderPose pose) {
        YsmModelPart part = pose == null
                ? (useArmParts ? runtime.getArmPart(bone.name) : runtime.getPart(bone.name)) : pose.part(bone.name);
        YsmBoneRole role = useArmParts ? runtime.armRoleOf(bone.name) : runtime.roleOf(bone.name);
        if (part != null && (!part.visibleRaw() || part.hiddenScaleRaw()))
            return;
        boolean renderSelf = mask == null || mask.allows(role);

        stack.pushPose();
        applyBoneTransform(bone, part, stack);
        if (renderSelf) {
            for (YsmGeometry.Cube cube : bone.cubes)
                renderCube(cube, stack, vertices, light);
        }
        for (YsmGeometry.Bone child : bone.children)
            renderBone(child, stack, vertices, light, mask, useArmParts, pose);
        stack.popPose();
    }

    private void renderStaticBone(YsmGeometry.Bone bone, PoseStack stack, VertexConsumer vertices, int light) {
        if (!bone.visible)
            return;
        stack.pushPose();
        applyBoneTransform(bone, null, stack);
        for (YsmGeometry.Cube cube : bone.cubes)
            renderCube(cube, stack, vertices, light);
        for (YsmGeometry.Bone child : bone.children)
            renderStaticBone(child, stack, vertices, light);
        stack.popPose();
    }

    private void applyModelScale(PoseStack stack, YsmRenderPose pose) {
        if (pose == null) {
            runtime.applyModelScale(stack);
        } else {
            pose.applyModelScale(stack);
        }
    }

    private void applyBoneTransform(YsmGeometry.Bone bone, YsmModelPart part, PoseStack stack) {
        applyBoneTransform(bone, part, stack, false);
    }

    private void applyBoneTransform(YsmGeometry.Bone bone, YsmModelPart part, PoseStack stack, boolean stopAtPivot) {
        double px = bone.pivot[0] / 16d;
        double py = bone.pivot[1] / 16d;
        double pz = bone.pivot[2] / 16d;
        stack.translate(px, py, pz);
        if (part != null) {
            FiguraVec3 animPos = part.animPosRaw();
            FiguraVec3 animRot = part.animRotRaw();
            FiguraVec3 animScale = part.animScaleRaw();
            stack.translate(-animPos.x / 16d, animPos.y / 16d, animPos.z / 16d);
            rotateBone(stack, bone.rotation[0] - (float) animRot.x,
                    bone.rotation[1] - (float) animRot.y, bone.rotation[2] + (float) animRot.z);
            stack.scale((float) animScale.x, (float) animScale.y, (float) animScale.z);

            FiguraVec3 pos = part.posRaw();
            FiguraVec3 rot = part.rotRaw();
            FiguraVec3 scale = part.scaleRaw();
            stack.translate(-pos.x / 16d, pos.y / 16d, pos.z / 16d);
            rotateBone(stack, (float) -rot.x, (float) -rot.y, (float) rot.z);
            stack.scale((float) scale.x, (float) scale.y, (float) scale.z);
        } else {
            rotateBone(stack, bone.rotation[0], bone.rotation[1], bone.rotation[2]);
        }
        if (!stopAtPivot) {
            stack.translate(-px, -py, -pz);
        }
    }

    private void updateTrackedPivots(boolean firstPerson, YsmRenderPose pose) {
        if (!runtime.boneQueries().hasTrackedPivots(firstPerson)) {
            return;
        }
        boolean armParts = firstPerson && runtime.hasArmModel();
        YsmGeometry geometry = armParts ? runtime.getArmGeometry() : runtime.geometry();
        PoseStack local = new PoseStack();
        for (YsmGeometry.Bone root : geometry.roots) {
            updateTrackedPivot(root, local, firstPerson, armParts, pose);
        }
    }

    private void updateTrackedPivot(YsmGeometry.Bone bone, PoseStack local, boolean firstPerson,
                                    boolean armParts, YsmRenderPose pose) {
        YsmModelPart original = armParts ? runtime.getArmPart(bone.name) : runtime.getPart(bone.name);
        YsmModelPart part = pose == null ? original : pose.part(bone.name);
        if (part == null || !part.visibleRaw()
                || part.animScaleRaw().x * part.scaleRaw().x == 0d
                || part.animScaleRaw().y * part.scaleRaw().y == 0d
                || part.animScaleRaw().z * part.scaleRaw().z == 0d) {
            return;
        }
        local.pushPose();
        applyBoneTransform(bone, part, local, true);
        var matrix = local.last().pose();
        runtime.boneQueries().recordPivot(original, firstPerson,
                -matrix.m30() * 16f, matrix.m31() * 16f, matrix.m32() * 16f);
        local.translate(-bone.pivot[0] / 16d, -bone.pivot[1] / 16d, -bone.pivot[2] / 16d);
        for (YsmGeometry.Bone child : bone.children) {
            updateTrackedPivot(child, local, firstPerson, armParts, pose);
        }
        local.popPose();
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
        for (YsmGeometry.Quad quad : cube.quads) {
            if (!quad.visible())
                continue;
            quad(vertices, stack, quad, light);
        }
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
