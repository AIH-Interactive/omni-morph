package org.figuramc.figura.mixin.render.layers;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.armortrim.ArmorTrim;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Intrinsic;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(HumanoidArmorLayer.class)
public interface HumanoidArmorLayerAccessor<T extends LivingEntity, M extends HumanoidModel<T>, A extends HumanoidModel<T>> {
    @Intrinsic
    @Invoker("renderModel")
    void renderModel(PoseStack matrices, MultiBufferSource vertexConsumers, int light, A model, int packedColor, @Nullable ResourceLocation overlay);

    @Intrinsic
    @Invoker("renderTrim")
    void renderTrim(Holder<ArmorMaterial> holder, PoseStack matrices, MultiBufferSource vertexConsumers, int light, ArmorTrim permutation, A model, boolean bl);

    @Intrinsic
    @Invoker("renderGlint")
    void renderGlint(PoseStack matrices, MultiBufferSource vertexConsumers, int light, A model);

    @Intrinsic
    @Invoker("usesInnerModel")
    boolean usesInnerModel(EquipmentSlot armorSlot);
}
