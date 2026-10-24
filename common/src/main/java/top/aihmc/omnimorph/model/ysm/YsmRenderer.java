package top.aihmc.omnimorph.model.ysm;

import top.aihmc.omnimorph.model.library.PreviewBoneHighlight;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.world.entity.Pose;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.resources.Identifier;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import org.figuramc.figura.math.vector.FiguraVec3;
import org.figuramc.figura.model.rendering.EntityRenderMode;
import top.aihmc.omnimorph.render.acceleration.AccelerationService;
import top.aihmc.omnimorph.render.acceleration.DrawBatch;
import top.aihmc.omnimorph.render.acceleration.DrawSemantics;
import top.aihmc.omnimorph.render.acceleration.MeshHandle;
import top.aihmc.omnimorph.render.acceleration.PoseSnapshot;
import top.aihmc.omnimorph.render.acceleration.GpuSubmission;

import java.util.List;

public class YsmRenderer {
    private final YsmModelRuntime runtime;
    private final YsmAccelerationMeshes accelerationMeshes = new YsmAccelerationMeshes();
    private Identifier culledTranslucentTexture;
    private RenderType culledTranslucentType;
    private YsmPreviewRenderTypes previewTypes;

    private record DrawTarget(MultiBufferSource source, RenderType material, Identifier texture,
                              String channel, int order, float alpha) {
        DrawTarget(MultiBufferSource source, RenderType material, Identifier texture, String channel, int order) {
            this(source, material, texture, channel, order, 1f);
        }

        VertexConsumer consumer() {
            return source.getBuffer(material);
        }
    }

    public YsmRenderer(YsmModelRuntime runtime) {
        this.runtime = runtime;
    }

    /** Entity root transform shared by the body, held items and attached outfit frame. */
    public static void applyEntityRotation(PoseStack stack, LivingEntityRenderState state, float bodyYaw) {
        if (state.hasPose(Pose.SLEEPING)) {
            if (state.bedOrientation != null) {
                // Vanilla performs this before setupRotations; our HEAD interception skips it.
                float offset = state.eyeHeight - 0.1f;
                stack.translate(-state.bedOrientation.getStepX() * offset, 0f,
                        -state.bedOrientation.getStepZ() * offset);
            }
            float angle = state.bedOrientation == null ? bodyYaw : switch (state.bedOrientation) {
                case SOUTH -> 90f;
                case WEST -> 0f;
                case NORTH -> 270f;
                case EAST -> 180f;
                default -> bodyYaw;
            };
            stack.mulPose(Axis.YP.rotationDegrees(angle));
            stack.mulPose(Axis.ZP.rotationDegrees(90f));
            stack.mulPose(Axis.YP.rotationDegrees(270f));
        } else {
            stack.mulPose(Axis.YP.rotationDegrees(180f - bodyYaw));
        }
    }

    void clearAccelerationMeshes() {
        accelerationMeshes.clear();
    }

    public boolean render(PoseStack stack, MultiBufferSource bufferSource, int light) {
        return render(stack, bufferSource, light, YsmPartMask.forPass(YsmRenderPass.PLAYER_BODY));
    }

    public boolean render(PoseStack stack, MultiBufferSource bufferSource, int light, YsmPartMask mask) {
        return render(stack, bufferSource, light, mask, null);
    }

    public boolean render(PoseStack stack, MultiBufferSource bufferSource, int light, YsmPartMask mask, YsmRenderPose pose) {
        if ((pose != null && !pose.isCurrent()) || !runtime.owner().accelerationOwner().isCurrent()) {
            return false;
        }
        runtime.recordComplexity(0);
        if (runtime.geometry().roots.isEmpty())
            return false;

        runtime.texture().uploadIfDirty(false, false);
        RenderType renderType = renderType(pose);
        DrawTarget vertices = new DrawTarget(bufferSource, renderType,
                runtime.texture().getLocation(), mask == YsmPartMask.WORLD_ATTACHMENTS ? "attachments" : "body",
                bodySubmitOrder());

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
        runtime.recordComplexity(0);
        if (entity == null || entity.geometry().roots.isEmpty())
            return false;

        entity.texture().uploadIfDirty(false, false);
        RenderType renderType = entity.isTextureTranslucent()
                ? RenderTypes.entityTranslucent(entity.texture().getLocation())
                : RenderTypes.entityCutout(entity.texture().getLocation());
        DrawTarget vertices = new DrawTarget(bufferSource, renderType,
                entity.texture().getLocation(), "sub-entity", bodySubmitOrder());

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
        // Attachment points describe item anchors, not an exhaustive list of geometry roots.
        // Traverse the authored hierarchy so every branch keeps its ancestors and draws once.
        return render(stack, bufferSource, light, YsmPartMask.WORLD_ATTACHMENTS, pose);
    }

    public boolean renderAttachmentType(PoseStack stack, MultiBufferSource bufferSource, int light, YsmAttachmentType type) {
        return renderAttachmentType(stack, bufferSource, light, type, null);
    }

    private boolean renderAttachmentType(PoseStack stack, MultiBufferSource bufferSource, int light,
                                          YsmAttachmentType type, YsmRenderPose pose) {
        YsmBoneRole role = switch (type) {
            case BACKPACK -> YsmBoneRole.BACKPACK;
            case BLADE -> YsmBoneRole.BLADE;
            case SHEATH -> YsmBoneRole.SHEATH;
            case ELYTRA -> YsmBoneRole.ELYTRA;
            default -> null;
        };
        if (role != null) {
            return render(stack, bufferSource, light,
                    new YsmPartMask(java.util.EnumSet.of(role)), pose);
        }
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
        if ((pose != null && !pose.isCurrent()) || !runtime.owner().accelerationOwner().isCurrent()) {
            return false;
        }
        runtime.recordComplexity(0);
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
        DrawTarget vertices = new DrawTarget(bufferSource, renderType,
                runtime.texture().getLocation(), "attachment/" + point.boneName(), bodySubmitOrder());

        updateTrackedPivots(false, pose);
        stack.pushPose();
        applyModelScale(stack, pose);
        var ancestors = new java.util.ArrayList<YsmGeometry.Bone>();
        for (var parent = findAttachmentBone(bone.parentName); parent != null;
             parent = findAttachmentBone(parent.parentName)) {
            ancestors.addFirst(parent);
        }
        for (var parent : ancestors) {
            applyBoneTransform(parent, pose == null ? runtime.getPart(parent.name) : pose.part(parent.name), stack);
        }
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
        if ((pose != null && !pose.isCurrent()) || !runtime.owner().accelerationOwner().isCurrent()) {
            return false;
        }
        runtime.recordComplexity(0);
        YsmGeometry armGeo = runtime.getArmGeometry();
        if (armGeo == null) {
            armGeo = runtime.geometry();
        }

        runtime.texture().uploadIfDirty(false, false);
        RenderType renderType = renderType(pose);
        DrawTarget vertices = new DrawTarget(bufferSource, renderType,
                runtime.texture().getLocation(), left ? "left-arm" : "right-arm", bodySubmitOrder());

        YsmPartMask mask = YsmPartMask.forPass(YsmRenderPass.FIRST_PERSON_ARM);

        updateTrackedPivots(true, pose);
        stack.pushPose();
        boolean useArmParts = runtime.hasArmModel();
        for (YsmGeometry.Bone root : armGeo.roots) {
            renderBone(root, stack, vertices, light, mask, useArmParts, pose, left);
        }
        stack.popPose();
        return true;
    }

    private boolean shouldRenderArmBone(YsmGeometry.Bone bone, boolean left, boolean useArmParts) {
        YsmBoneRole role = useArmParts ? runtime.armRoleOf(bone.name) : runtime.roleOf(bone.name);
        return YsmBoneMapper.isFirstPersonArmCandidate(bone.name, role, left);
    }

    private YsmBoneRole worldGeometryRole(YsmGeometry.Bone bone, YsmBoneRole role) {
        // Ordinary descendants belong to their enclosing equipment branch. Otherwise a
        // child is drawn in the body pass and again when its attachment is submitted.
        for (var current = bone; current != null; current = findAttachmentBone(current.parentName)) {
            YsmBoneRole ancestorRole = runtime.roleOf(current.name);
            if (YsmPartMask.WORLD_ATTACHMENTS.allows(ancestorRole)) {
                role = ancestorRole;
            }
        }
        return role;
    }

    private RenderType renderType() {
        return renderType(null);
    }

    private RenderType renderType(YsmRenderPose pose) {
        return renderType(pose, false);
    }

    private RenderType renderType(YsmRenderPose pose, boolean forceTranslucent) {
        Identifier texture = runtime.texture().getLocation();
        boolean translucent = forceTranslucent || runtime.isTextureTranslucent();
        boolean unlit = pose == null
                ? runtime.isGuiNoLighting() && runtime.owner().renderMode == EntityRenderMode.FIGURA_GUI
                : pose.unlit();
        if (unlit) {
            if (previewTypes == null) {
                previewTypes = new YsmPreviewRenderTypes();
            }
            return previewTypes.get(texture, runtime.isAllCutout(), translucent);
        }
        if (!runtime.isAllCutout()) {
            return translucent
                    ? RenderTypes.entityTranslucent(texture) : RenderTypes.entityCutout(texture);
        }
        if (!translucent) {
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

    private void renderBone(YsmGeometry.Bone bone, PoseStack stack, DrawTarget vertices, int light, YsmPartMask mask) {
        renderBone(bone, stack, vertices, light, mask, false);
    }

    private void renderBone(YsmGeometry.Bone bone, PoseStack stack, DrawTarget vertices, int light, YsmPartMask mask, boolean useArmParts) {
        renderBone(bone, stack, vertices, light, mask, useArmParts, null);
    }

    private void renderBone(YsmGeometry.Bone bone, PoseStack stack, DrawTarget vertices, int light,
                             YsmPartMask mask, boolean useArmParts, YsmRenderPose pose) {
        renderBone(bone, stack, vertices, light, mask, useArmParts, pose, null);
    }

    private void renderBone(YsmGeometry.Bone bone, PoseStack stack, DrawTarget vertices, int light,
                            YsmPartMask mask, boolean useArmParts, YsmRenderPose pose, Boolean leftArm) {
        if (leftArm != null && !shouldRenderArmBone(bone, leftArm, useArmParts)) {
            return;
        }
        YsmModelPart part = pose == null
                ? (useArmParts ? runtime.getArmPart(bone.name) : runtime.getPart(bone.name)) : pose.part(bone.name);
        YsmBoneRole role = useArmParts ? runtime.armRoleOf(bone.name) : runtime.roleOf(bone.name);
        if (!useArmParts && mask != YsmPartMask.FIRST_PERSON_ARM) {
            role = worldGeometryRole(bone, role);
        }
        if (part != null && (!part.visibleRaw() || part.hiddenScaleRaw()))
            return;
        boolean renderSelf = mask == null || mask.allows(role);

        stack.pushPose();
        applyBoneTransform(bone, part, stack);
        if (renderSelf) {
            var selectionSource = runtime.selectionSource();
            var selectedPart = selectionSource.getPart(bone.name);
            float opacity = PreviewBoneHighlight.opacity(selectionSource.owner(), selectedPart);
            DrawTarget target = opacity < 1f
                    ? new DrawTarget(vertices.source(), renderType(pose, true), vertices.texture(),
                            vertices.channel(), vertices.order(), opacity) : vertices;
            renderGeometry(bone.cubes, stack, target, light);
            if (PreviewBoneHighlight.selected(selectionSource.owner(), selectedPart)) {
                PreviewBoneHighlight.ysm(bone, stack, vertices.source());
            }
        }
        for (YsmGeometry.Bone child : bone.children)
            renderBone(child, stack, vertices, light, mask, useArmParts, pose, leftArm);
        stack.popPose();
    }

    private void renderStaticBone(YsmGeometry.Bone bone, PoseStack stack, DrawTarget vertices, int light) {
        if (!bone.visible)
            return;
        stack.pushPose();
        applyBoneTransform(bone, null, stack);
        renderGeometry(bone.cubes, stack, vertices, light);
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
            part.applyOutfitTransform(stack);
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

    private void renderCube(YsmGeometry.Cube cube, PoseStack stack, DrawTarget vertices, int light) {
        for (YsmGeometry.Quad quad : cube.quads) {
            if (!quad.visible() || !runtime.consumeOutfitFaces(1))
                continue;
            quad(vertices, stack, quad, light);
        }
    }

    private void quad(DrawTarget vertices, PoseStack stack, YsmGeometry.Quad quad, int light) {
        if (!runtime.owner().accelerationOwner().isCurrent()) {
            return;
        }
        float[] positions = quad.positions();
        float[] uvs = quad.uvs();
        float[] normal = quad.normal();
        VertexConsumer consumer = vertices.consumer();
        for (int i = 0; i < 4; i++) {
            vertex(consumer, stack, positions[i * 3], positions[i * 3 + 1], positions[i * 3 + 2],
                    uvs[i * 2], uvs[i * 2 + 1], normal[0], normal[1], normal[2], light, vertices.alpha());
        }
    }

    private void renderGeometry(List<YsmGeometry.Cube> cubes, PoseStack stack, DrawTarget target, int light) {
        AccelerationService acceleration = AccelerationService.INSTANCE;
        if (!acceleration.accelerated()) {
            accelerationMeshes.clear();
            for (YsmGeometry.Cube cube : cubes) {
                renderCube(cube, stack, target, light);
            }
            return;
        }
        int visibleFaces = 0;
        for (YsmGeometry.Cube cube : cubes) {
            for (YsmGeometry.Quad face : cube.quads) {
                if (face.visible()) {
                    visibleFaces++;
                }
            }
        }
        if (visibleFaces == 0) {
            return;
        }
        if (!runtime.consumeOutfitFaces(visibleFaces)) {
            // A mesh is indivisible here; submit only the permitted prefix through native quads.
            for (YsmGeometry.Cube cube : cubes) {
                renderCube(cube, stack, target, light);
            }
            return;
        }
        PoseSnapshot pose = new PoseSnapshot(stack.last());
        for (MeshHandle mesh : accelerationMeshes.get(cubes)) {
            submitGeometry(target, mesh, pose, light, mesh.faceCount());
        }
    }

    private void submitGeometry(DrawTarget target, MeshHandle mesh, PoseSnapshot pose, int light, int faces) {
        AccelerationService acceleration = AccelerationService.INSTANCE;
        var lease = acceleration.capture(runtime.owner().accelerationOwner(), String.valueOf(runtime.owner().renderMode));
        DrawSemantics semantics = new DrawSemantics(target.material(), target.texture(),
                DrawSemantics.Layer.PRIMARY, target.channel(), target.order(), faces, light,
                OverlayTexture.NO_OVERLAY, 1, 1, 1, target.alpha(), 0, true);
        DrawBatch batch = new DrawBatch(lease, mesh, pose, semantics);
        double[][] output = {null};
        GpuSubmission.JavaEmitter fallback = (consumer, firstVertex, vertexCount) -> {
            if (!acceleration.isCurrent(lease)) {
                return;
            }
            if (output[0] == null) {
                output[0] = acceleration.transform(lease, token -> batch);
            }
            double[] transformed = output[0];
            if (transformed == null) {
                return;
            }
            for (int vertex = firstVertex; vertex < firstVertex + vertexCount; vertex++) {
                int offset = vertex * MeshHandle.STRIDE;
                consumer.addVertex((float) transformed[offset], (float) transformed[offset + 1], (float) transformed[offset + 2])
                        .setColor(semantics.red(), semantics.green(), semantics.blue(), semantics.alpha())
                        .setUv((float) transformed[offset + 7], (float) transformed[offset + 8])
                        .setOverlay(semantics.overlay()).setLight(semantics.light())
                        .setNormal((float) transformed[offset + 4], (float) transformed[offset + 5], (float) transformed[offset + 6]);
            }
        };
        if (!GpuSubmission.enqueue(target.source(), batch, fallback)) {
            fallback.emit(target.consumer(), 0, mesh.vertexCount());
        }
    }

    private void vertex(VertexConsumer vertices, PoseStack stack, float x, float y, float z,
                        float u, float v, float nx, float ny, float nz, int light, float alpha) {
        vertices.addVertex(stack.last(), x, y, z)
                .setColor(1f, 1f, 1f, alpha)
                .setUv(u, v)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(light)
                .setNormal(stack.last(), nx, ny, nz);
    }
}
