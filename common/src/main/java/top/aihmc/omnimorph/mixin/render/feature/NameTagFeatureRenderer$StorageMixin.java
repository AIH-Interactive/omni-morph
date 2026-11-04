package top.aihmc.omnimorph.mixin.render.feature;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.SubmitNodeCollection;
import net.minecraft.client.renderer.feature.TextFeatureRenderer;
import net.minecraft.client.renderer.feature.phase.TranslucentFeatureRenderPhase;
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
            target = "Lcom/mojang/blaze3d/vertex/PoseStack;rotate(Lorg/joml/Quaternionfc;)V", shift = At.Shift.AFTER))
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
    private void figura$drawSeeThrough(TranslucentFeatureRenderPhase phase, TranslucentSubmit node,
                                       Operation<Void> original) {
        if (figura$custom == null) {
            original.call(phase, node);
            return;
        }
        for (var line : figura$layout((TextFeatureRenderer.Submit) node)) {
            original.call(phase, line);
        }
    }

    @WrapOperation(method = "submitNameTag", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/SubmitNodeCollection;submitNameTagPart(Lnet/minecraft/client/renderer/feature/TextFeatureRenderer$Submit;)V"))
    private void figura$drawNameTag(SubmitNodeCollection collection, TextFeatureRenderer.Submit node,
                                    Operation<Void> original) {
        if (figura$custom == null) {
            original.call(collection, node);
            return;
        }
        for (var line : figura$layout(node)) {
            original.call(collection, line);
        }
    }

    @Unique
    private List<TextFeatureRenderer.Submit> figura$layout(TextFeatureRenderer.Submit submit) {
        var content = (TextFeatureRenderer.Content.Text) submit.content();
        int light = figura$custom.light == null ? submit.lightCoords() : figura$custom.light;
        int background = figura$custom.background == null ? content.backgroundColor() : figura$custom.background;
        int outline = figura$custom.outline && submit.displayMode() != Font.DisplayMode.SEE_THROUGH
                ? (figura$custom.outlineColor == null ? 0x202020 : figura$custom.outlineColor) : 0;
        if (!figura$isRenderingName) {
            return List.of(new TextFeatureRenderer.Submit(submit.pose(), submit.displayMode(), light,
                    new TextFeatureRenderer.Content.Text(content.x(), content.y(), content.string(),
                            content.dropShadow(), content.color(), background, outline)));
        }
        Font font = Minecraft.getInstance().font;
        var result = new ArrayList<TextFeatureRenderer.Submit>();
        for (int index = 0; index < figura$textList.size(); index++) {
            Component text = figura$textList.get(index);
            if (text.getString().isEmpty()) {
                continue;
            }
            int line = index - figura$textList.size() + 1;
            float x = -font.width(text) / 2f;
            float y = content.y() + (font.lineHeight + 1) * line;
            result.add(new TextFeatureRenderer.Submit(submit.pose(), submit.displayMode(), light,
                    new TextFeatureRenderer.Content.Text(x, y, text.getVisualOrderText(),
                            content.dropShadow(), content.color(), background, outline)));
        }
        return result;
    }
}