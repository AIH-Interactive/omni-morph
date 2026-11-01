package top.aihmc.omnimorph.utils.neoforge;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterial;

public class RenderUtilsImpl {
    public static ResourceLocation getArmorResource(Entity entity, ItemStack stack, ArmorItem item,
                                                    EquipmentSlot slot, boolean inner, ArmorMaterial.Layer layer) {
        return net.neoforged.neoforge.client.ClientHooks.getArmorTexture(entity, stack, layer, inner, slot);
    }
}
