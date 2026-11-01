package top.aihmc.omnimorph.utils.forge;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.client.ForgeHooksClient;

import java.util.Locale;

public class RenderUtilsImpl {
    public static ResourceLocation getArmorResource(Entity entity, ItemStack stack, ArmorItem item,
                                                    EquipmentSlot slot, boolean inner, String type) {
        ResourceLocation material = new ResourceLocation(item.getMaterial().getName());
        String texture = String.format(Locale.ROOT, "%s:textures/models/armor/%s_layer_%d%s.png",
                material.getNamespace(), material.getPath(), inner ? 2 : 1,
                type == null ? "" : "_" + type);
        return new ResourceLocation(ForgeHooksClient.getArmorTexture(entity, stack, texture, slot, type));
    }
}
