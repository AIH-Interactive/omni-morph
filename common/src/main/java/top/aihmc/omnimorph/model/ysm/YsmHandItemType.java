package top.aihmc.omnimorph.model.ysm;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.item.Items;

import java.util.Locale;

/** Reference hand-matcher classification; item identifiers are never substring classifiers. */
public final class YsmHandItemType {
    private YsmHandItemType() {
    }

    public static boolean matches(ItemStack stack, String expected) {
        if (stack == null || stack.isEmpty() || expected == null || expected.isEmpty()) {
            return false;
        }
        Item item = stack.getItem();
        if (item == Items.TRIDENT || compatTag(stack, "tridents")) {
            return expected.equals("spear") || expected.equals("trident");
        }
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(item);
        if (stack.getUseAnimation() == UseAnim.SPEAR && id != null
                && id.getNamespace().equals("minecraft") && id.getPath().endsWith("_spear")) {
            return expected.equals("lance");
        }
        if (compatTag(stack, "pike")) {
            return expected.equals("lance");
        }
        if (item == Items.MACE || compatTag(stack, "mace")) {
            return expected.equals("mace");
        }
        String type = nonWeaponType(stack);
        return expected.equals(type) || (type.equals("shovel") && expected.equals("spade"))
                || expected.equals(stack.getUseAnimation().name().toLowerCase(Locale.ROOT));
    }

    private static String nonWeaponType(ItemStack stack) {
        Item item = stack.getItem();
        // Optional SlashBlade/maid adapters are unavailable, as in the reference Fabric adapter.
        if (stack.is(ItemTags.SWORDS) || compatTag(stack, "swords")) {
            return "sword";
        }
        if (stack.is(ItemTags.AXES) || compatTag(stack, "axes")) {
            return "axe";
        }
        if (stack.is(ItemTags.PICKAXES) || compatTag(stack, "pickaxes")) {
            return "pickaxe";
        }
        if (stack.is(ItemTags.SHOVELS) || compatTag(stack, "shovels")) {
            return "shovel";
        }
        if (stack.is(ItemTags.HOES) || compatTag(stack, "hoes")) {
            return "hoe";
        }
        if (item == Items.SHIELD || compatTag(stack, "shields")) {
            return "shield";
        }
        if (item == Items.CROSSBOW || compatTag(stack, "crossbows")) {
            return "crossbow";
        }
        if (item == Items.BOW || compatTag(stack, "bows")) {
            return "bow";
        }
        if (item == Items.FISHING_ROD || compatTag(stack, "fishing_rods")) {
            return "fishing_rod";
        }
        if (item == Items.SPLASH_POTION || item == Items.LINGERING_POTION || compatTag(stack, "throwable_potion")) {
            return "throwable_potion";
        }
        return "";
    }

    private static boolean compatTag(ItemStack stack, String name) {
        // This namespace comes from the current reference ItemTagsConstants, not this mod's ID.
        return stack.is(TagKey.create(Registries.ITEM, ResourceLocation.fromNamespaceAndPath("sparkle_morpher", name)));
    }
}
