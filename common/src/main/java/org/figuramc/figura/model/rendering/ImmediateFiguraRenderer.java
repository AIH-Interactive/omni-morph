package org.figuramc.figura.model.rendering;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.datafixers.util.Pair;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.debug.DebugScreenEntries;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LightLayer;
import org.figuramc.figura.FiguraMod;
import org.figuramc.figura.avatar.Avatar;
import org.figuramc.figura.config.Configs;
import org.figuramc.figura.lua.api.ClientAPI;
import org.figuramc.figura.math.matrix.FiguraMat3;
import org.figuramc.figura.math.matrix.FiguraMat4;
import org.figuramc.figura.math.vector.FiguraVec3;
import org.figuramc.figura.math.vector.FiguraVec4;
import org.figuramc.figura.model.*;
import org.figuramc.figura.model.rendering.texture.FiguraRenderTypes;
import org.figuramc.figura.model.rendering.texture.FiguraTexture;
import org.figuramc.figura.model.rendering.texture.FiguraTextureSet;
import org.figuramc.figura.model.rendertasks.RenderTask;
import org.figuramc.figura.utils.ColorUtils;
import org.figuramc.figura.utils.ui.UIHelper;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.function.Consumer;

public class ImmediateFiguraRenderer extends FiguraRenderer {

    protected final PartCustomization.PartCustomizationStack customizationStack = new PartCustomization.PartCustomizationStack();

    public static final FiguraMat4 CAMERA_POS_TO_WORLD_MATRIX = FiguraMat4.of();

    private static final PartCustomization pivotOffsetter = new PartCustomization();
    protected static final VertexBuffer VERTEX_BUFFER = new VertexBuffer();

    public ImmediateFiguraRenderer(Avatar avatar) {
        super(avatar);

        // Vertex data, read model parts
        root = FiguraModelPartReader.read(avatar, avatar.nbt.getCompoundOrEmpty("models"), textureSets, false);

        sortParts();
    }

    public void checkEmpty() {
        if (!customizationStack.isEmpty())
            throw new IllegalStateException("Customization stack not empty!");
    }

    @Override
    public int render() {
        return commonRender(1.5d);
    }

    @Override
    public int renderSpecialParts() {
        return commonRender(0);
    }

    @Override
    public void updateMatrices() {
        // flag rendering state
        this.isRendering = true;

        // setup root customizations
        PartCustomization customization = setupRootCustomization(1.5d);

        // Push transform
        customizationStack.push(customization);

        // world matrices
        CAMERA_POS_TO_WORLD_MATRIX.set(FiguraRenderer.worldToCameraPosMatrix().invert());

        // calculate each part matrices
        calculatePartMatrices(root);

        // finish rendering
        customizationStack.pop();
        checkEmpty();

        this.isRendering = false;
    }

    public int getComplexity() {
        // complexity
        int prev = avatar.complexity.remaining;
        int[] remainingComplexity = new int[] {prev};

        // explore all model parts
        if (root.customization.visible) {
            if (currentFilterScheme.parentType.isSeparate) {
                List<FiguraModelPart> parts = separatedParts.get(currentFilterScheme.parentType);
                if (parts != null) {
                    for (FiguraModelPart part : parts) {
                        if (currentFilterScheme.parentType == ParentType.Item && part != itemToRender)
                            continue;

                        getPartComplexity(part, remainingComplexity, currentFilterScheme.initialValue);
                    }
                }
            } else {
                getPartComplexity(root, remainingComplexity, currentFilterScheme.initialValue);
            }
        }
        return prev - Math.max(remainingComplexity[0], 0);
    }

    protected int commonRender(double vertOffset) {
        // flag rendering state
        this.isRendering = true;

        // iris fix
        int irisConfig = UIHelper.paperdoll || !ClientAPI.hasShaderPackMod() ? 0 : Configs.IRIS_COMPATIBILITY_FIX.value;
        doIrisEmissiveFix = (irisConfig >= 2 && ClientAPI.hasShaderPack()) || (avatar.renderMode != EntityRenderMode.RENDER && avatar.renderMode != EntityRenderMode.WORLD);
        offsetRenderLayers = irisConfig >= 1;

        // custom textures
        for (FiguraTextureSet set : textureSets)
            set.uploadIfNeeded();
        for (FiguraTexture texture : customTextures.values())
            texture.uploadIfDirty(false, false);

        // Set shouldRenderPivots
        int config = Configs.RENDER_DEBUG_PARTS_PIVOT.value;
        if (!Minecraft.getInstance().debugEntries.isCurrentlyEnabled(DebugScreenEntries.ENTITY_HITBOXES) || (!avatar.isHost && config < 2))
            shouldRenderPivots = 0;
        else
            shouldRenderPivots = config;

        // world matrices
        if (allowMatrixUpdate) {
            CAMERA_POS_TO_WORLD_MATRIX.set(FiguraRenderer.worldToCameraPosMatrix().invert());
        }

        // complexity
        int prev = avatar.complexity.remaining;
        int[] remainingComplexity = new int[] {prev};

        // render all model parts
        if (root.customization.visible) {
            if (currentFilterScheme.parentType.isSeparate) {
                List<FiguraModelPart> parts = separatedParts.get(currentFilterScheme.parentType);
                if (parts != null) {
                    boolean renderLayer = !currentFilterScheme.parentType.isRenderLayer;
                    if (renderLayer) {
                        PartCustomization customization = setupRootCustomization(vertOffset);
                        customizationStack.push(customization); // push root
                        customizationStack.push(root.customization); // push "models"
                    }

                    for (FiguraModelPart part : parts) {
                        if (currentFilterScheme.parentType == ParentType.Item && part != itemToRender)
                            continue;

                        boolean saved = part.savedCustomization != null;
                        if (saved) {
                            customizationStack.push(part.savedCustomization);
                            part.savedCustomization = null;
                        }

                        renderPart(part, remainingComplexity, currentFilterScheme.initialValue);

                        if (saved) customizationStack.pop();
                    }

                    if (renderLayer) {
                        customizationStack.pop(); // pop "models"
                        customizationStack.pop(); // pop root
                    }
                }
            } else {
                PartCustomization customization = setupRootCustomization(vertOffset);
                customizationStack.push(customization);
                renderPart(root, remainingComplexity, currentFilterScheme.initialValue);
                customizationStack.pop();
            }

            // push vertices to vertex consumer
            FiguraMod.pushProfiler("draw");
            FiguraMod.pushProfiler("primary");
            VERTEX_BUFFER.consume(true, bufferSource);
            FiguraMod.popPushProfiler("secondary");
            VERTEX_BUFFER.consume(false, bufferSource);
            FiguraMod.popProfiler(2);

            // finish rendering
            checkEmpty();
        }

        this.isRendering = false;
        if (this.dirty)
            clean();

        return prev - Math.max(remainingComplexity[0], 0);
    }

    protected PartCustomization setupRootCustomization(double vertOffset) {
        PartCustomization customization = new PartCustomization();

        customization.setPrimaryRenderType(FiguraRenderTypes.TRANSLUCENT);
        customization.setSecondaryRenderType(FiguraRenderTypes.EMISSIVE);

        double s = 1.0 / 16;
        customization.positionMatrix.scale(s, s, s);
        customization.positionMatrix.rotateZ(180);
        customization.positionMatrix.translate(0, vertOffset, 0);
        customization.normalMatrix.rotateZ(180);

        customization.positionMatrix.multiply(posMat);
        customization.normalMatrix.multiply(normalMat);

        customization.light = light;
        customization.alpha = alpha;
        customization.overlay = overlay;

        customization.primaryTexture = new TextureCustomization(FiguraTextureSet.OverrideType.PRIMARY, null);
        customization.secondaryTexture = new TextureCustomization(FiguraTextureSet.OverrideType.SECONDARY, null);

        return customization;
    }

    protected boolean renderPart(FiguraModelPart part, int[] remainingComplexity, boolean prevPredicate) {
        FiguraMod.pushProfiler(part.name);

        PartCustomization custom = part.customization;

        // test the current filter scheme
        FiguraMod.pushProfiler("predicate");
        Boolean thisPassedPredicate = currentFilterScheme.test(part.parentType, prevPredicate);
        if (thisPassedPredicate == null || (!custom.visible)) {
            if (part.parentType.isRenderLayer)
                part.savedCustomization = customizationStack.peek();
            FiguraMod.popProfiler(2);
            return true;
        }

        // calculate part transforms

        // calculate vanilla parent
        FiguraMod.popPushProfiler("copyVanillaPart");
        part.applyVanillaTransforms(vanillaModelData);
        part.applyExtraTransforms(customizationStack.peek());

        // visibility
        FiguraMod.popPushProfiler("checkVanillaVisible");
        if (!ignoreVanillaVisibility && custom.vanillaVisible != null && !custom.vanillaVisible) {
            FiguraMod.popPushProfiler("removeVanillaTransforms");
            part.resetVanillaTransforms();
            FiguraMod.popProfiler(2);
            return true;
        }

        // pre render function
        if (part.preRender != null) {
            FiguraMod.popPushProfiler("preRenderFunction");
            avatar.run(part.preRender, avatar.render, tickDelta, avatar.renderMode.name(), part);
        }

        // recalculate stuff
        FiguraMod.popPushProfiler("calculatePartMatrices");
        custom.recalculate();

        // void blocked matrices
        // that's right, check only for previous predicate
        FiguraMat4 positionCopy = null;
        FiguraMat3 normalCopy = null;
        boolean voidMatrices = !allowHiddenTransforms && !prevPredicate;
        if (voidMatrices) {
            FiguraMod.popPushProfiler("clearMatrices");
            positionCopy = custom.positionMatrix.copy();
            normalCopy = custom.normalMatrix.copy();
            custom.positionMatrix.reset();
            custom.normalMatrix.reset();
        }

        // push stack
        FiguraMod.popPushProfiler("pushCustomizationStack");
        customizationStack.push(custom);

        // restore variables
        if (voidMatrices) {
            FiguraMod.popPushProfiler("restoreMatrices");
            custom.positionMatrix.set(positionCopy);
            custom.normalMatrix.set(normalCopy);
        }

        if (thisPassedPredicate) {
            // recalculate world matrices
            FiguraMod.popPushProfiler("worldMatrices");
            if (allowMatrixUpdate) {
                FiguraMat4 mat = partToWorldMatrices(custom);
                part.savedPartToWorldMat.set(mat);
            }

            // recalculate light
            FiguraMod.popPushProfiler("calculateLight");
            Level l;
            if (custom.light != null) {
                updateLight = false;
                pivotOffsetter.light = custom.light;
            }
            else if (updateLight && (l = Minecraft.getInstance().level) != null) {
                FiguraVec3 pos = part.savedPartToWorldMat.apply(0d, 0d, 0d);
                int block = l.getBrightness(LightLayer.BLOCK, pos.asBlockPos());
                int sky = l.getBrightness(LightLayer.SKY, pos.asBlockPos());
                customizationStack.peek().light = LightTexture.pack(block, sky);
            }

            if (custom.alpha != null)
                pivotOffsetter.alpha = custom.alpha;
            if (custom.overlay != null)
                pivotOffsetter.overlay = custom.overlay;
        }

        // mid render function
        if (part.midRender != null) {
            FiguraMod.popPushProfiler("midRenderFunction");
            avatar.run(part.midRender, avatar.render, tickDelta, avatar.renderMode.name(), part);
        }

        // render this
        FiguraMod.popPushProfiler("pushVertices");
        boolean breakRender = thisPassedPredicate && !part.pushVerticesImmediate(this, remainingComplexity);

        // render extras
        FiguraMod.popPushProfiler("extras");
        if (!breakRender && thisPassedPredicate) {
            boolean renderPivot = shouldRenderPivots > 0;
            boolean renderTasks = !part.renderTasks.isEmpty();
            boolean renderPivotParts = part.parentType.isPivot && allowPivotParts;

            if (renderPivot || renderTasks || renderPivotParts) {
                // fix pivots
                FiguraMod.pushProfiler("fixMatricesPivot");

                FiguraVec3 pivot = custom.getPivot().copy().add(custom.getOffsetPivot());
                pivotOffsetter.setPos(pivot);
                pivotOffsetter.recalculate();
                customizationStack.push(pivotOffsetter);

                PartCustomization peek = customizationStack.peek();

                // render pivot indicators
                if (renderPivot) {
                    FiguraMod.popPushProfiler("renderPivotCube");
                    renderPivot(part, peek);
                }

                // render tasks
                if (renderTasks) {
                    FiguraMod.popPushProfiler("renderTasks");
                    int light = peek.light;
                    int overlay = peek.overlay;
                    interceptRendersIntoFigura = false;
                    for (RenderTask task : part.renderTasks.values()) {
                        if (!task.shouldRender())
                            continue;
                        int neededComplexity = task.getComplexity();
                        if (neededComplexity > remainingComplexity[0])
                            break;
                        FiguraMod.pushProfiler(task.getName());
                        task.render(customizationStack, bufferSource, light, overlay);
                        remainingComplexity[0] -= neededComplexity;
                        FiguraMod.popProfiler();
                    }
                    interceptRendersIntoFigura = true;
                }

                // render pivot parts
                if (renderPivotParts && part.parentType.isPivot) {
                    FiguraMod.popPushProfiler("savePivotParts");
                    savePivotTransform(part.parentType, peek);
                }

                customizationStack.pop();
                FiguraMod.popProfiler();
            }
        }

        // render children
        FiguraMod.popPushProfiler("children");
        for (FiguraModelPart child : List.copyOf(part.children)) {
            if (!renderPart(child, remainingComplexity, thisPassedPredicate)) {
                breakRender = true;
                break;
            }
        }

        // reset the parent
        FiguraMod.popPushProfiler("removeVanillaTransforms");
        part.resetVanillaTransforms();

        // post render function
        if (part.postRender != null) {
            FiguraMod.popPushProfiler("postRenderFunction");
            avatar.run(part.postRender, avatar.render, tickDelta, avatar.renderMode.name(), part);
        }

        // pop
        customizationStack.pop();
        FiguraMod.popProfiler(2);

        return !breakRender;
    }


    protected boolean getPartComplexity(FiguraModelPart part, int[] remainingComplexity, boolean prevPredicate) {
        PartCustomization custom = part.customization;

        // test the current filter scheme
        Boolean thisPassedPredicate = currentFilterScheme.test(part.parentType, prevPredicate);
        if (thisPassedPredicate == null || (!custom.visible)) {
            return true;
        }

        // visibility
        if (!ignoreVanillaVisibility && custom.vanillaVisible != null && !custom.vanillaVisible) {
            return true;
        }

        // calculate this part's complexity
        FiguraMod.popPushProfiler("pushVertices");
        boolean breakRender = thisPassedPredicate && !part.calculateComplexity(remainingComplexity);

        // calculate extras
        if (!breakRender && thisPassedPredicate) {
            boolean renderTasks = !part.renderTasks.isEmpty();
            // add tasks
            if (renderTasks) {

                for (RenderTask task : part.renderTasks.values()) {
                    if (!task.shouldRender())
                        continue;
                    int neededComplexity = task.getComplexity();
                    if (neededComplexity > remainingComplexity[0])
                        break;
                    FiguraMod.pushProfiler(task.getName());
                    remainingComplexity[0] -= neededComplexity;
                    FiguraMod.popProfiler();
                }
            }
        }

        // calculate children
        for (FiguraModelPart child : List.copyOf(part.children)) {
            if (!getPartComplexity(child, remainingComplexity, thisPassedPredicate)) {
                breakRender = true;
                break;
            }
        }
        return !breakRender;
    }

    protected void renderPivot(FiguraModelPart part, PartCustomization customization) {
        boolean group = part.customization.partType == PartCustomization.PartType.GROUP;
        FiguraVec3 color = group ? ColorUtils.Colors.FIGURA_BLUE.vec : ColorUtils.Colors.AWESOME_BLUE.vec;
        double boxSize = group ? 1 / 16d : 1 / 32d;
        boxSize /= Math.max(Math.cbrt(part.savedPartToWorldMat.det()), 0.02);

        PoseStack stack = customization.copyIntoGlobalPoseStack();

        renderLineBox(stack.last(), bufferSource.getBuffer(RenderTypes.LINES),
                -boxSize, -boxSize, -boxSize,
                boxSize, boxSize, boxSize,
                (float) color.x, (float) color.y, (float) color.z, 1f);
    }

    public static void renderLineBox(PoseStack.Pose pose, VertexConsumer vertices, double x1, double y1, double z1, double x2, double y2, double z2, float r, float g, float b, float a) {
        float f = (float)x1;
        float h = (float)y1;
        float i = (float)z1;
        float j = (float)x2;
        float k = (float)y2;
        float l = (float)z2;
        vertices.addVertex(pose, f, h, i).setColor(r, g, b, a).setNormal(pose, 1.0F, 0.0F, 0.0F);
        vertices.addVertex(pose, j, h, i).setColor(r, g, b, a).setNormal(pose, 1.0F, 0.0F, 0.0F);
        vertices.addVertex(pose, f, h, i).setColor(r, g, b, a).setNormal(pose, 0.0F, 1.0F, 0.0F);
        vertices.addVertex(pose, f, k, i).setColor(r, g, b, a).setNormal(pose, 0.0F, 1.0F, 0.0F);
        vertices.addVertex(pose, f, h, i).setColor(r, g, b, a).setNormal(pose, 0.0F, 0.0F, 1.0F);
        vertices.addVertex(pose, f, h, l).setColor(r, g, b, a).setNormal(pose, 0.0F, 0.0F, 1.0F);
        vertices.addVertex(pose, j, h, i).setColor(r, g, b, a).setNormal(pose, 0.0F, 1.0F, 0.0F);
        vertices.addVertex(pose, j, k, i).setColor(r, g, b, a).setNormal(pose, 0.0F, 1.0F, 0.0F);
        vertices.addVertex(pose, j, k, i).setColor(r, g, b, a).setNormal(pose, -1.0F, 0.0F, 0.0F);
        vertices.addVertex(pose, f, k, i).setColor(r, g, b, a).setNormal(pose, -1.0F, 0.0F, 0.0F);
        vertices.addVertex(pose, f, k, i).setColor(r, g, b, a).setNormal(pose, 0.0F, 0.0F, 1.0F);
        vertices.addVertex(pose, f, k, l).setColor(r, g, b, a).setNormal(pose, 0.0F, 0.0F, 1.0F);
        vertices.addVertex(pose, f, k, l).setColor(r, g, b, a).setNormal(pose, 0.0F, -1.0F, 0.0F);
        vertices.addVertex(pose, f, h, l).setColor(r, g, b, a).setNormal(pose, 0.0F, -1.0F, 0.0F);
        vertices.addVertex(pose, f, h, l).setColor(r, g, b, a).setNormal(pose, 1.0F, 0.0F, 0.0F);
        vertices.addVertex(pose, j, h, l).setColor(r, g, b, a).setNormal(pose, 1.0F, 0.0F, 0.0F);
        vertices.addVertex(pose, j, h, l).setColor(r, g, b, a).setNormal(pose, 0.0F, 0.0F, -1.0F);
        vertices.addVertex(pose, j, h, i).setColor(r, g, b, a).setNormal(pose, 0.0F, 0.0F, -1.0F);
        vertices.addVertex(pose, f, k, l).setColor(r, g, b, a).setNormal(pose, 1.0F, 0.0F, 0.0F);
        vertices.addVertex(pose, j, k, l).setColor(r, g, b, a).setNormal(pose, 1.0F, 0.0F, 0.0F);
        vertices.addVertex(pose, j, h, l).setColor(r, g, b, a).setNormal(pose, 0.0F, 1.0F, 0.0F);
        vertices.addVertex(pose, j, k, l).setColor(r, g, b, a).setNormal(pose, 0.0F, 1.0F, 0.0F);
        vertices.addVertex(pose, j, k, i).setColor(r, g, b, a).setNormal(pose, 0.0F, 0.0F, 1.0F);
        vertices.addVertex(pose, j, k, l).setColor(r, g, b, a).setNormal(pose, 0.0F, 0.0F, 1.0F);
    }

    protected void savePivotTransform(ParentType parentType, PartCustomization customization) {
        FiguraMat4 currentPosMat = customization.getPositionMatrix();
        FiguraMat3 currentNormalMat = customization.getNormalMatrix();
        ConcurrentLinkedQueue<Pair<FiguraMat4, FiguraMat3>> queue = pivotCustomizations.computeIfAbsent(parentType, p -> new ConcurrentLinkedQueue<>());
        queue.add(new Pair<>(currentPosMat, currentNormalMat)); // These are COPIES, so ok to add
    }

    protected FiguraMat4 partToWorldMatrices(PartCustomization cust) {
        FiguraMat4 customizePeek = customizationStack.peek().positionMatrix.copy();
        // Translate by the inverse matrix of the camera position, as of 1.20.5 it is no longer dependent on camera rot.
        customizePeek.multiply(CAMERA_POS_TO_WORLD_MATRIX);
        FiguraVec3 piv = cust.getPivot();

        FiguraMat4 translation = FiguraMat4.of();
        translation.translate(piv);
        customizePeek.rightMultiply(translation);

        return customizePeek;
    }

    protected void calculatePartMatrices(FiguraModelPart part) {
        FiguraMod.pushProfiler(part.name);

        PartCustomization custom = part.customization;

        // Store old visibility, but overwrite it in case we only want to render certain parts
        FiguraMod.pushProfiler("predicate");
        Boolean thisPassedPredicate = currentFilterScheme.test(part.parentType, true);
        if (thisPassedPredicate == null) {
            FiguraMod.popProfiler(2);
            return;
        }

        // calculate part transforms

        // calculate vanilla parent
        FiguraMod.popPushProfiler("copyVanillaPart");
        part.applyVanillaTransforms(vanillaModelData);
        part.applyExtraTransforms(customizationStack.peek());

        // push customization stack
        FiguraMod.popPushProfiler("calculatePartMatrices");
        custom.recalculate();
        FiguraMod.popPushProfiler("applyOnStack");
        customizationStack.push(custom);

        // render extras
        if (thisPassedPredicate) {
            // part to world matrices
            FiguraMod.popPushProfiler("worldMatrices");
            FiguraMat4 mat = partToWorldMatrices(custom);
            part.savedPartToWorldMat.set(mat);
        }

        // render children
        FiguraMod.popPushProfiler("children");
        for (FiguraModelPart child : part.children)
            calculatePartMatrices(child);

        // reset the parent
        part.resetVanillaTransforms();

        // pop
        customizationStack.pop();
        FiguraMod.popProfiler(2);
    }

    public void pushFaces(int faceCount, int[] remainingComplexity, FiguraTextureSet textureSet, List<Vertex> vertices, FiguraModelPart part) {
        // Handle cases that we can quickly
        if (faceCount == 0 || vertices.isEmpty())
            return;

        PartCustomization customization = customizationStack.peek();

        VertexData primary = getTexture(customization, textureSet, true);
        VertexData secondary = getTexture(customization, textureSet, false);

        if (primary.renderType == null && secondary.renderType == null) {
            remainingComplexity[0] += faceCount;
            return;
        }

        // Collect bone animation delta matrices for skinning
        FiguraMat4[] boneDeltas = null;
        if (part.skinBoneNames != null && part.skinBoneNames.length > 0 && part.parent != null) {
            boneDeltas = collectBoneSkinningDeltas(part);
        }

        if (primary.renderType != null)
            pushToBuffer(faceCount, primary, customization, textureSet, vertices, part, boneDeltas);
        if (secondary.renderType != null)
            pushToBuffer(faceCount, secondary, customization, textureSet, vertices, part, boneDeltas);
    }

    /**
     * Collects animation delta matrices from bone parts in the hierarchy.
     * Supports nested bone hierarchies: a child bone's delta is composed with
     * its ancestor bones' deltas so that the accumulated delta correctly
     * represents the full animation chain from root to that bone.
     *
     * The delta matrix represents ONLY the animation change (rotation/position/scale around pivot),
     * WITHOUT the bone's rest pose transform.
     *
     * For a nested bone B (child of A), the combined delta is:
     *   delta_B * delta_A  (in standard matrix notation)
     * which means V' = delta_B * (delta_A * V).
     * This is accumulated via left-multiply (multiply()) from root to leaf.
     *
     * Delta formula: T(-pivot) * S(animScale) * R_ZYX(animRot) * T(pivot + animPos)
     */
    private FiguraMat4[] collectBoneSkinningDeltas(FiguraModelPart skinnedPart) {
        String[] boneNames = skinnedPart.skinBoneNames;
        FiguraMat4[] deltas = new FiguraMat4[boneNames.length];

        if (skinnedPart.parent != null) {
            for (FiguraModelPart sibling : skinnedPart.parent.children) {
                if (sibling == skinnedPart)
                    continue;
                // Recursively search this subtree for bones, accumulating ancestor deltas
                searchBoneHierarchy(sibling, boneNames, deltas, FiguraMat4.of());
            }
        }

        // Fall back to identity for missing bones (no deformation)
        for (int i = 0; i < boneNames.length; i++) {
            if (deltas[i] == null) {
                deltas[i] = FiguraMat4.of();
            }
        }

        return deltas;
    }

    /**
     * Recursively searches a part's subtree for bones that match skinBoneNames.
     * For each bone found, computes the combined world-space animation delta
     * by accumulating local deltas from the root of the search down to that bone.
     *
     * Accumulation uses left-multiply (multiply() = other * this):
     *   accum.multiply(localDelta) → accum = localDelta * parentAccum
     * This ensures V' = localDelta_leaf * ... * localDelta_root * V,
     * i.e. root animation applied first, then each child's animation.
     *
     * @param part        The current part to check (and recurse into its children)
     * @param boneNames   The array of bone names to match against
     * @param deltas      Output array; deltas[i] is set when boneNames[i] matches
     * @param parentAccum The accumulated delta from all ancestors above this part
     */
    private void searchBoneHierarchy(FiguraModelPart part, String[] boneNames, FiguraMat4[] deltas, FiguraMat4 parentAccum) {
        // Compute this part's local animation delta
        FiguraMat4 localDelta = buildBoneAnimDelta(part.customization);

        // Accumulate: combined = localDelta * parentAccum
        // multiply(other) = other * this, so combined.multiply(localDelta) = localDelta * parentAccum
        FiguraMat4 combined = parentAccum.copy();
        combined.multiply(localDelta);
        // combined is now: localDelta * parentAccum (standard notation)

        // Check if this part matches any bone name
        for (int i = 0; i < boneNames.length; i++) {
            if (deltas[i] == null && boneNames[i].equals(part.name)) {
                deltas[i] = combined.copy();
            }
        }

        // Recurse into children, passing down the accumulated delta
        for (FiguraModelPart child : part.children) {
            searchBoneHierarchy(child, boneNames, deltas, combined);
        }
    }

    /**
     * Builds an animation delta matrix from a bone's customization.
     * This is the transform from the bone's rest pose to its animated pose,
     * applied around the bone's pivot point.
     *
     * Resulting transform: V' = R(S(V - pivot)) + pivot + animPos
     * (rotate and scale around pivot, then translate by animPos)
     */
    private static FiguraMat4 buildBoneAnimDelta(PartCustomization boneCust) {
        FiguraVec3 pivot = boneCust.getPivot();
        FiguraVec3 animRot = boneCust.getAnimRot();
        FiguraVec3 animPos = boneCust.getAnimPos();
        FiguraVec3 animScale = boneCust.getAnimScale();

        FiguraMat4 delta = FiguraMat4.of();

        // Step 1: Translate so pivot is at origin (rotation/scaling center)
        delta.translate(-pivot.x, -pivot.y, -pivot.z);

        // Step 2: Apply animation scale
        delta.scale(animScale.x, animScale.y, animScale.z);

        // Step 3: Apply animation rotation (ZYX order, same as armature bones/groups)
        if (animRot.x != 0 || animRot.y != 0 || animRot.z != 0) {
            delta.rotateZYX(animRot.x, animRot.y, animRot.z);
        }

        // Step 4: Translate back to pivot, then add animation position offset
        delta.translate(
                pivot.x + animPos.x,
                pivot.y + animPos.y,
                pivot.z + animPos.z
        );

        return delta;
    }

    private VertexData getTexture(PartCustomization customization, FiguraTextureSet textureSet, boolean primary) {
        FiguraRenderTypes types = primary ? customization.getPrimaryRenderType() : customization.getSecondaryRenderType();
        TextureCustomization texture = primary ? customization.primaryTexture : customization.secondaryTexture;
        VertexData ret = new VertexData();

        if (types == FiguraRenderTypes.NONE)
            return ret;

        // get texture
        Identifier id = textureSet.getOverrideTexture(avatar.owner, texture);

        // color
        ret.color = primary ? customization.color : customization.color2;

        // primary
        ret.primary = primary;

        // get render type
        if (id != null) {
            if (translucent) {
                ret.renderType = RenderTypes.itemEntityTranslucentCull(id);
                return ret;
            }
            if (glowing) {
                ret.renderType = RenderTypes.outline(id);
                return ret;
            }
        }

        if (types == null)
            return ret;

        if (offsetRenderLayers && !primary && types.isOffset())
            ret.vertexOffset = FiguraMod.VERTEX_OFFSET;

        // Switch to cutout with fullbright if the iris emissive fix is enabled
        if (doIrisEmissiveFix && types == FiguraRenderTypes.EMISSIVE) {
            ret.fullBright = true;
            ret.renderType = FiguraRenderTypes.TRANSLUCENT_CULL.get(id);
        } else {
            ret.renderType = types.get(id);
        }

        return ret;
    }

    private static final FiguraVec4 pos = FiguraVec4.of();
    private static final FiguraVec3 normal = FiguraVec3.of();
    private static final FiguraVec3 uv = FiguraVec3.of(0, 0, 1);
    private void pushToBuffer(int faceCount, VertexData vertexData, PartCustomization customization, FiguraTextureSet textureSet, List<Vertex> vertices,
                               FiguraModelPart part, @Nullable FiguraMat4[] boneDeltas) {
        int vertCount = faceCount * 4;

        FiguraVec3 uvFixer = FiguraVec3.of();
        uvFixer.set(textureSet.getWidth(), textureSet.getHeight(), 1); // Dividing by this makes uv 0 to 1

        int overlay = customization.overlay;
        int light = vertexData.fullBright ? LightTexture.FULL_BRIGHT : customization.light;

        // Determine if we should use skinning
        boolean useSkinning = boneDeltas != null && boneDeltas.length > 0
                && part.skinBoneIndices != null && part.skinBoneWeights != null;

        VERTEX_BUFFER.getBufferFor(vertexData.renderType, vertexData.primary, vertexConsumer -> {
            for (int i = 0; i < vertCount; i++) {
                Vertex vertex = vertices.get(i);

                pos.set(vertex.x, vertex.y, vertex.z, 1);

                // Step 1: Apply bone skinning DELTA in local space (before mesh transform)
                // The delta rotates/translates around the bone's pivot WITHOUT the bone's rest position
                if (useSkinning && vertex.origIdx >= 0 && vertex.origIdx < part.skinBoneIndices.length && part.skinBoneIndices[vertex.origIdx] != null) {
                    applySkinningDelta(pos, vertex.origIdx, part, boneDeltas);
                }

                // Step 2: Apply mesh's own transform (parent chain + mesh local) for ALL vertices
                pos.transform(customization.positionMatrix);

                pos.add(pos.normalized().scale(vertexData.vertexOffset));
                normal.set(vertex.nx, vertex.ny, vertex.nz);
                normal.transform(customization.normalMatrix);
                uv.set(vertex.u, vertex.v, 1);
                uv.divide(uvFixer);
                uv.transform(customization.uvMatrix);

                vertexConsumer
                        .addVertex((float) pos.x, (float) pos.y, (float) pos.z)
                        .setColor((float) vertexData.color.x, (float) vertexData.color.y, (float) vertexData.color.z, customization.alpha)
                        .setUv((float) uv.x, (float) uv.y)
                        .setOverlay(overlay)
                        .setLight(light)
                        .setNormal((float) normal.x, (float) normal.y, (float) normal.z);
            }
        });
    }

    /// Thread-local reuse for skinning calculation
    private static final FiguraVec4 skinTempPos = FiguraVec4.of();

    /**
     * Applies per-vertex bone skinning delta to the given position in local space.
     * Blends multiple bone delta matrices weighted by the vertex's bone weights.
     * The delta matrices represent ONLY the animation change (rotation around pivot),
     * so the blended result is a deformed local-space position.
     */
    private void applySkinningDelta(FiguraVec4 pos, int vertexIndex, FiguraModelPart part, FiguraMat4[] boneDeltas) {
        int[] boneIndices = part.skinBoneIndices[vertexIndex];
        float[] boneWeights = part.skinBoneWeights[vertexIndex];

        if (boneIndices == null || boneWeights == null || boneIndices.length == 0) {
            return;
        }

        float totalWeight = 0f;
        float sx = 0f, sy = 0f, sz = 0f;

        for (int bi = 0; bi < boneIndices.length; bi++) {
            int boneIdx = boneIndices[bi];
            float weight = boneWeights[bi];

            if (weight <= 0f || boneIdx < 0 || boneIdx >= boneDeltas.length)
                continue;

            // Transform vertex by this bone's animation delta
            skinTempPos.set(pos.x, pos.y, pos.z, 1);
            skinTempPos.transform(boneDeltas[boneIdx]);

            sx += (float) skinTempPos.x * weight;
            sy += (float) skinTempPos.y * weight;
            sz += (float) skinTempPos.z * weight;
            totalWeight += weight;
        }

        if (totalWeight > 0f) {
            float invTotal = 1f / totalWeight;
            pos.x = sx * invTotal;
            pos.y = sy * invTotal;
            pos.z = sz * invTotal;
        }
    }

    private static class VertexData {
        public RenderType renderType;
        public boolean fullBright;
        public float vertexOffset;
        public FiguraVec3 color;
        public boolean primary;
    }

    private static class VertexBuffer {
        private final HashMap<RenderType, List<Consumer<VertexConsumer>>> primaryBuffers = new LinkedHashMap<>();
        private final HashMap<RenderType, List<Consumer<VertexConsumer>>> secondaryBuffers = new LinkedHashMap<>();

        public void getBufferFor(RenderType renderType, boolean primary, Consumer<VertexConsumer> consumer) {
            HashMap<RenderType, List<Consumer<VertexConsumer>>> buffer = primary ? primaryBuffers : secondaryBuffers;
            List<Consumer<VertexConsumer>> list = buffer.computeIfAbsent(renderType, renderType1 -> new ArrayList<>());
            list.add(consumer);
        }

        public void consume(boolean primary, MultiBufferSource bufferSource) {
            HashMap<RenderType, List<Consumer<VertexConsumer>>> map = primary ? primaryBuffers : secondaryBuffers;
            for (Map.Entry<RenderType, List<Consumer<VertexConsumer>>> entry : map.entrySet()) {
                VertexConsumer vertexConsumer = bufferSource.getBuffer(entry.getKey());
                List<Consumer<VertexConsumer>> consumers = entry.getValue();
                for (Consumer<VertexConsumer> consumer : consumers)
                    consumer.accept(vertexConsumer);
            }
            map.clear();
        }
    }
}
