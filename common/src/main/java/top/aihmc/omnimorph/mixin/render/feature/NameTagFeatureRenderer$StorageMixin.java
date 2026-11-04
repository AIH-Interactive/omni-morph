package top.aihmc.omnimorph.mixin.render.feature;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.SubmitNodeCollection;
import net.minecraft.client.renderer.feature.NameTagFeatureRenderer;
import net.minecraft.client.renderer.feature.TextFeatureRenderer;
import net.minecraft.client.renderer.feature.phase.SimpleFeatureRenderPhase;
import net.minecraft.client.renderer.feature.phase.TranslucentFeatureRenderPhase;
import net.minecraft.client.renderer.feature.submit.SubmitNode;
import net.minecraft.client.renderer.feature.submit.TranslucentSubmit;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.Vec3;
import org.figuramc.figura.FiguraMod;
import org.figuramc.figura.avatar.AvatarManager;
import org.figuramc.figura.config.Configs;
import org.figuramc.figura.lua.api.nameplate.EntityNameplateCustomization;
import org.figuramc.figura.permissions.Permissions;
import org.figuramc.figura.utils.TextUtils;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import top.aihmc.omnimorph.ducks.CameraRenderStateExtension;

import java.util.ArrayList;
import java.util.List;

/** Keeps existing nameplate customization on the native collection and text phases. */
@Mixin(SubmitNodeCollection.class)
public abstract class NameTagFeatureRenderer$StorageMixin {
    @Shadow @Final public SimpleFeatureRenderPhase texts;

    @Unique private EntityNameplateCustomization figura$custom;
    @Unique private List<Component> figura$textList;
    @Unique private boolean figura$isRenderingName;

    @WrapMethod(method = "submitNameTag")
    private void figura$withNameplate(PoseStack stack, Vec3 attachment, int offset, Component name,
                                      boolean seeThrough, int light, CameraRenderState camera,
                                      Operation<Void> original) {
        var context = (CameraRenderStateExtension) camera;
        var avatar = context.figura$getAvatar();
        boolean renderingName = context.figura$isRenderingNameTag();
        context.figura$setAvatar(null);
        context.figura$setRenderingNameTag(false);

        var previousCustom = figura$custom;
        var previousLines = figura$textList;
        boolean previousRenderingName = figura$isRenderingName;
        figura$custom = avatar == null || avatar.luaRuntime == null
                || Configs.ENTITY_NAMEPLATE.value <= 0 || AvatarManager.panic
                || avatar.permissions.get(Permissions.NAMEPLATE_EDIT) != 1
                ? null : avatar.luaRuntime.nameplate.ENTITY;
        figura$isRenderingName = renderingName;
        figura$textList = figura$custom == null ? null : TextUtils.splitText(name, "\n");
        try {
            original.call(stack, attachment, offset, name, seeThrough, light, camera);
        } finally {
            figura$custom = previousCustom;
            figura$textList = previousLines;
            figura$isRenderingName = previousRenderingName;
        }
    }

    @WrapOperation(method = "submitNameTag", at = @At(value = "INVOKE",
            target = "Lcom/mojang/blaze3d/vertex/PoseStack;translate(DDD)V"))
    private void figura$modifyPivot(PoseStack stack, double x, double y, double z, Operation<Void> original) {
        if (figura$custom != null) {
            FiguraMod.pushProfiler("pivot");
        }
        var pivot = figura$custom == null ? null : figura$custom.getPivot();
        original.call(stack, pivot == null ? x : pivot.x, pivot == null ? y : pivot.y, pivot == null ? z : pivot.z);
    }

    @Inject(method = "submitNameTag", at = @At(value = "INVOKE",
            target = "Lcom/mojang/blaze3d/vertex/PoseStack;mulPose(Lorg/joml/Quaternionfc;)V", shift = At.Shift.AFTER))
    private void figura$modifyPosition(PoseStack stack, Vec3 attachment, int offset, Component name,
                                       boolean seeThrough, int light, CameraRenderState camera, CallbackInfo ci) {
        if (figura$custom != null) {
            FiguraMod.popPushProfiler("position");
        }
        var position = figura$custom == null ? null : figura$custom.getPos();
        if (position != null) {
            stack.translate(position.x, position.y, position.z);
        }
    }

    @WrapOperation(method = "submitNameTag", at = @At(value = "INVOKE",
            target = "Lcom/mojang/blaze3d/vertex/PoseStack;scale(FFF)V"))
    private void figura$modifyScale(PoseStack stack, float x, float y, float z, Operation<Void> original) {
        if (figura$custom != null) {
            FiguraMod.popPushProfiler("scale");
        }
        var scale = figura$custom == null ? null : figura$custom.getScale();
        original.call(stack, scale == null ? x : x * (float) scale.x,
                scale == null ? y : y * (float) scale.y, scale == null ? z : z * (float) scale.z);
    }

    @WrapOperation(method = "submitNameTag", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/feature/phase/TranslucentFeatureRenderPhase;submit(Lnet/minecraft/client/renderer/feature/submit/TranslucentSubmit;)V"))
    private void figura$drawWithColor(TranslucentFeatureRenderPhase phase, TranslucentSubmit node,
                                      Operation<Void> original) {
        if (figura$custom == null) {
            original.call(phase, node);
            return;
        }
        var submit = (NameTagFeatureRenderer.Submit) node;
        int light = figura$custom.light == null ? submit.lightCoords() : figura$custom.light;
        int background = figura$custom.background == null ? submit.backgroundColor() : figura$custom.background;
        for (var line : figura$layout(submit)) {
            original.call(phase, new NameTagFeatureRenderer.Submit(line.pose(), line.x(), line.y(), line.text(),
                    light, line.color(), background, line.displayMode()));
        }
    }

    @WrapOperation(method = "submitNameTag", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/feature/phase/SimpleFeatureRenderPhase;submit(Lnet/minecraft/client/renderer/feature/submit/SubmitNode;)V",
            ordinal = 0))
    private void figura$drawWithOutline(SimpleFeatureRenderPhase phase, SubmitNode node, Operation<Void> original) {
        if (figura$custom == null) {
            original.call(phase, node);
            return;
        }
        var submit = (NameTagFeatureRenderer.Submit) node;
        var lines = figura$layout(submit);
        if (figura$custom.outline) {
            int outlineColor = figura$custom.outlineColor == null ? 0x202020 : figura$custom.outlineColor;
            for (var line : lines) {
                texts.submit(new TextFeatureRenderer.Submit(new Matrix4f(line.pose()), line.x(), line.y(),
                        line.text().getVisualOrderText(), false, Font.DisplayMode.NORMAL,
                        line.lightCoords(), line.color(), 0, outlineColor));
            }
            original.call(phase, new NameTagFeatureRenderer.Submit(submit.pose(), submit.x(), submit.y(),
                    Component.empty(), submit.lightCoords(), submit.color(), submit.backgroundColor(), submit.displayMode()));
        } else {
            for (var line : lines) {
                original.call(phase, line);
            }
        }
    }

    @Unique
    private List<NameTagFeatureRenderer.Submit> figura$layout(NameTagFeatureRenderer.Submit submit) {
        if (!figura$isRenderingName) {
            return List.of(submit);
        }
        Font font = Minecraft.getInstance().font;
        var result = new ArrayList<NameTagFeatureRenderer.Submit>();
        boolean deadmau = submit.text().getString().equals("deadmau5");
        for (int index = 0; index < figura$textList.size(); index++) {
            Component text = figura$textList.get(index);
            if (text.getString().isEmpty()) {
                continue;
            }
            int line = index - figura$textList.size() + 1;
            float x = -font.width(text) / 2f;
            float y = (deadmau ? -10f : 0f) + (font.lineHeight + 1) * line;
            result.add(new NameTagFeatureRenderer.Submit(submit.pose(), x, y, text, submit.lightCoords(),
                    submit.color(), submit.backgroundColor(), submit.displayMode()));
        }
        return result;
    }
}
