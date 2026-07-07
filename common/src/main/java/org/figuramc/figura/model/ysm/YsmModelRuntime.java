package org.figuramc.figura.model.ysm;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.entity.LivingEntity;
import org.figuramc.figura.avatar.Avatar;
import org.figuramc.figura.math.vector.FiguraVec3;
import org.figuramc.figura.model.rendering.texture.FiguraTexture;
import org.figuramc.figura.model.ysm.animation.YsmAnimationClip;
import org.figuramc.figura.model.ysm.animation.YsmAnimationParser;
import org.figuramc.figura.model.ysm.animation.YsmAnimationPlayer;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class YsmModelRuntime implements AutoCloseable {
    private final Avatar owner;
    private final YsmGeometry geometry;
    private final FiguraTexture texture;
    private final Map<String, YsmModelPart> parts = new LinkedHashMap<>();
    private final YsmRenderer renderer;
    private final YsmAnimationPlayer animationPlayer;
    private final String textureId;

    private YsmModelRuntime(Avatar owner, YsmGeometry geometry, FiguraTexture texture, String textureId) {
        this.owner = owner;
        this.geometry = geometry;
        this.texture = texture;
        this.textureId = textureId;
        buildParts();
        this.renderer = new YsmRenderer(this);
        this.animationPlayer = new YsmAnimationPlayer(this);
    }

    public static YsmModelRuntime fromNbt(Avatar owner, CompoundTag tag) throws java.io.IOException {
        String mainJson = new String(tag.getByteArray("main_model").orElse(new byte[0]), StandardCharsets.UTF_8);
        if (mainJson.isBlank())
            throw new java.io.IOException("YSM main model is empty");
        YsmGeometry geometry = YsmGeometryParser.parse(mainJson);
        byte[] textureBytes = tag.getByteArray("texture").orElse(new byte[0]);
        FiguraTexture texture = new FiguraTexture(owner, tag.getStringOr("texture_id", "ysm_texture"), textureBytes.length == 0 ? onePixelPng() : textureBytes);
        YsmModelRuntime runtime = new YsmModelRuntime(owner, geometry, texture, tag.getStringOr("texture_id", ""));
        runtime.animationPlayer.registerAnimations(readAnimations(tag));
        return runtime;
    }

    private static Map<String, YsmAnimationClip> readAnimations(CompoundTag tag) {
        Map<String, YsmAnimationClip> result = new HashMap<>();
        for (Tag animationTag : tag.getListOrEmpty("animations")) {
            if (!(animationTag instanceof CompoundTag animation))
                continue;
            String json = new String(animation.getByteArray("data").orElse(new byte[0]), StandardCharsets.UTF_8);
            if (json.isBlank())
                continue;
            result.putAll(YsmAnimationParser.parse(json));
        }
        return result;
    }

    private void buildParts() {
        for (YsmGeometry.Bone bone : geometry.bones.values())
            buildPart(bone);
    }

    private YsmModelPart buildPart(YsmGeometry.Bone bone) {
        YsmModelPart existing = parts.get(bone.name);
        if (existing != null)
            return existing;

        YsmGeometry.Bone parentBone = bone.parentName == null || bone.parentName.isBlank() ? null : geometry.bones.get(bone.parentName);
        YsmModelPart parent = parentBone == null ? null : buildPart(parentBone);
        YsmModelPart part = new YsmModelPart(bone.name, parent, bone.pivot, bone.rotation);
        if (YsmBoneClassifier.isNonBodyBranch(bone))
            part.setVisible(false);
        parts.put(bone.name, part);
        parts.putIfAbsent(bone.name.toLowerCase(java.util.Locale.US), part);
        return part;
    }

    public Avatar owner() {
        return owner;
    }

    public YsmGeometry geometry() {
        return geometry;
    }

    public FiguraTexture texture() {
        return texture;
    }

    public String textureId() {
        return textureId;
    }

    public YsmRenderer renderer() {
        return renderer;
    }

    public YsmAnimationPlayer animations() {
        return animationPlayer;
    }

    public YsmModelPart getPart(String name) {
        if (name == null)
            return null;
        YsmModelPart part = parts.get(name);
        return part != null ? part : parts.get(name.toLowerCase(java.util.Locale.US));
    }

    public Map<String, YsmModelPart> parts() {
        return parts;
    }

    public Iterable<YsmModelPart> uniqueParts() {
        return new LinkedHashSet<>(parts.values());
    }

    public void updateAnimations(LivingEntityRenderState state, LivingEntity entity) {
        animationPlayer.update(state, entity);
    }

    public boolean applyHandItemTransform(PoseStack stack, boolean left) {
        YsmGeometry.Bone bone = findHandBone(left);
        stack.scale(0.9375f, 0.9375f, 0.9375f);
        if (bone == null) {
            stack.translate(left ? -0.42d : 0.42d, 0.78d, 0d);
            stack.mulPose(Axis.XP.rotationDegrees(-90f));
        } else {
            boolean locator = bone.name.toLowerCase(java.util.Locale.US).contains("locator");
            applyBoneChain(stack, bone, locator);
            stack.translate(0d, -0.0625d, -0.1d);
            stack.mulPose(Axis.XP.rotationDegrees(-90f));
        }
        return bone != null;
    }

    @Override
    public void close() {
        texture.close();
    }

    private YsmGeometry.Bone findHandBone(boolean left) {
        String side = left ? "Left" : "Right";
        for (String name : List.of(side + "HandLocator", side + "Item", side + "Hand", side + "Palm", side + "Wrist", side + "ForeArm", side + "LowerArm", side + "Arm")) {
            YsmGeometry.Bone bone = getBoneIgnoreCase(name);
            if (bone != null)
                return bone;
        }
        return null;
    }

    private YsmGeometry.Bone getBoneIgnoreCase(String name) {
        YsmGeometry.Bone exact = geometry.bones.get(name);
        if (exact != null)
            return exact;
        for (YsmGeometry.Bone bone : geometry.bones.values()) {
            if (bone.name.equalsIgnoreCase(name))
                return bone;
        }
        return null;
    }

    private void applyBoneChain(PoseStack stack, YsmGeometry.Bone bone) {
        applyBoneChain(stack, bone, false);
    }

    private void applyBoneChain(PoseStack stack, YsmGeometry.Bone bone, boolean locatorMode) {
        List<YsmGeometry.Bone> chain = new ArrayList<>();
        for (YsmGeometry.Bone current = bone; current != null; current = current.parentName == null ? null : geometry.bones.get(current.parentName))
            chain.add(0, current);
        for (int i = 0; i < chain.size(); i++)
            applyBoneTransform(stack, chain.get(i), locatorMode && i == chain.size() - 1);
    }

    private void applyBoneTransform(PoseStack stack, YsmGeometry.Bone bone) {
        applyBoneTransform(stack, bone, false);
    }

    private void applyBoneTransform(PoseStack stack, YsmGeometry.Bone bone, boolean stopAtPivot) {
        YsmModelPart part = getPart(bone.name);
        double px = bone.pivot[0] / 16d;
        double py = bone.pivot[1] / 16d;
        double pz = bone.pivot[2] / 16d;
        stack.translate(px, py, pz);
        rotate(stack, bone.rotation[0], bone.rotation[1], bone.rotation[2]);
        if (part != null) {
            FiguraVec3 animPos = part.animPosRaw();
            FiguraVec3 animRot = part.animRotRaw();
            FiguraVec3 animScale = part.animScaleRaw();
            stack.translate(-animPos.x / 16d, animPos.y / 16d, animPos.z / 16d);
            rotate(stack, (float) -animRot.x, (float) -animRot.y, (float) animRot.z);
            stack.scale((float) animScale.x, (float) animScale.y, (float) animScale.z);

            FiguraVec3 pos = part.posRaw();
            FiguraVec3 rot = part.rotRaw();
            FiguraVec3 scale = part.scaleRaw();
            stack.translate(-pos.x / 16d, pos.y / 16d, pos.z / 16d);
            rotate(stack, (float) -rot.x, (float) -rot.y, (float) rot.z);
            stack.scale((float) scale.x, (float) scale.y, (float) scale.z);
        }
        if (!stopAtPivot)
            stack.translate(-px, -py, -pz);
    }

    private void rotate(PoseStack stack, float x, float y, float z) {
        if (z != 0f)
            stack.mulPose(Axis.ZP.rotationDegrees(z));
        if (y != 0f)
            stack.mulPose(Axis.YP.rotationDegrees(y));
        if (x != 0f)
            stack.mulPose(Axis.XP.rotationDegrees(x));
    }

    private static byte[] onePixelPng() {
        return java.util.Base64.getDecoder().decode("iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMB/atX7pQAAAAASUVORK5CYII=");
    }
}
