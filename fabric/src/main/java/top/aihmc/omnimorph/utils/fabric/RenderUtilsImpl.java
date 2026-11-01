package top.aihmc.omnimorph.utils.fabric;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterial;

public class RenderUtilsImpl {
    public static ResourceLocation getArmorResource(Entity entity, ItemStack stack, ArmorItem item,
                                                    EquipmentSlot slot, boolean inner, ArmorMaterial.Layer layer) {
        return layer.texture(inner);
    }
}
