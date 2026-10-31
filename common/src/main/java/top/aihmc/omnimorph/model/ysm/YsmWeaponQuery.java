package top.aihmc.omnimorph.model.ysm;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.item.Items;
import org.figuramc.figura.avatar.Avatar;

/** Fabric weapon queries use main-hand native state and owner-restricted input pulses. */
public final class YsmWeaponQuery {
    public static final java.util.List<String> NAMES = java.util.List.of(
            "weapon_type", "weapon_is_trident", "weapon_is_lance", "weapon_is_spear", "weapon_is_mace",
            "weapon_attacking", "weapon_using", "weapon_riding", "weapon_fall_flying", "weapon_speed",
            "weapon_attack_ticks", "weapon_use_ticks", "trident_holding", "trident_using", "trident_throwing",
            "trident_riptide", "trident_attack", "trident_use_ticks", "trident_attack_ticks", "lance_holding",
            "lance_using", "lance_charging", "lance_jabbing", "lance_lunging", "lance_riding", "lance_riding_charge",
            "lance_fall_flying", "lance_use_ticks", "lance_attack_ticks", "lance_speed", "lance_charge_progress",
            "mace_holding", "mace_falling", "mace_can_smash", "mace_smashing", "mace_wind_bursting",
            "mace_attacking", "mace_riding", "mace_fall_flying", "mace_fall_distance", "mace_vertical_speed",
            "mace_attack_ticks", "mace_smash_progress");

    private YsmWeaponQuery() {
    }

    private static int kind(ItemStack stack) {
        if (stack.isEmpty()) {
            return 0;
        }
        if (stack.is(Items.TRIDENT) || tagged(stack, "tridents")) {
            return 1;
        }
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        if (stack.getUseAnimation() == UseAnim.SPEAR && id.getNamespace().equals("minecraft")
                && id.getPath().endsWith("_spear")) {
            return 4;
        }
        if (tagged(stack, "pike")) {
            return 2;
        }
        return stack.is(Items.MACE) || tagged(stack, "mace") ? 3 : 0;
    }

    private static boolean tagged(ItemStack stack, String tag) {
        return stack.is(TagKey.create(Registries.ITEM, ResourceLocation.fromNamespaceAndPath("sparkle_morpher", tag)));
    }

    public static Object evaluate(String name, Avatar.MolangContext context, LivingEntity entity) {
        ItemStack stack = entity.getMainHandItem();
        int kind = kind(stack);
        boolean trident = kind == 1;
        boolean lance = kind == 2 || kind == 4;
        boolean mace = kind == 3;
        var hands = YsmInputState.device().hands();
        boolean local = YsmHandQuery.ownsDeviceInput(context.owner, entity);
        boolean attacking = kind != 0 && !entity.isSleeping()
                && hands.swinging(entity, InteractionHand.MAIN_HAND, local);
        boolean using = (trident || lance) && hands.using(entity, InteractionHand.MAIN_HAND, local)
                && stack.getUseAnimation() == UseAnim.SPEAR;
        float partialTick = context.ysm_partial_tick;
        float attackTicks = attacking ? hands.swingTime(entity, partialTick, local) : 0f;
        float useTicks = using ? hands.useTime(entity, partialTick, local) : 0f;
        var movement = entity.getDeltaMovement();
        float speed = kind == 0 ? 0f : (float) Math.sqrt(movement.x * movement.x + movement.z * movement.z);
        float verticalSpeed = mace ? (float) movement.y : 0f;
        boolean riding = entity.isPassenger();
        boolean flying = entity.isFallFlying();
        boolean lunging = lance && speed >= .35f && (using || attacking || riding || flying);
        float fallDistance = mace ? (float) entity.fallDistance : 0f;
        boolean falling = mace && !entity.onGround() && verticalSpeed < -.08f && fallDistance > 0f;
        boolean canSmash = falling && fallDistance >= 1.5f;
        return switch (name) {
            case "weapon_type" -> kind;
            case "weapon_is_trident", "trident_holding" -> trident;
            case "weapon_is_lance" -> kind == 2;
            case "weapon_is_spear" -> kind == 4;
            case "weapon_is_mace", "mace_holding" -> mace;
            case "weapon_attacking" -> attacking || lunging;
            case "weapon_using" -> using;
            case "weapon_riding" -> (lance || mace) && riding;
            case "weapon_fall_flying" -> (lance || mace) && flying;
            case "weapon_speed" -> speed;
            case "weapon_attack_ticks" -> attackTicks;
            case "weapon_use_ticks" -> useTicks;
            case "trident_using", "trident_throwing" -> trident && using;
            case "trident_riptide" -> trident && entity.isAutoSpinAttack();
            case "trident_attack" -> trident && attacking;
            case "trident_use_ticks" -> trident ? useTicks : 0f;
            case "trident_attack_ticks" -> trident ? attackTicks : 0f;
            case "lance_holding" -> lance;
            case "lance_using", "lance_charging" -> lance && using;
            case "lance_jabbing" -> lance && attacking && !lunging;
            case "lance_lunging" -> lunging;
            case "lance_riding" -> lance && riding;
            case "lance_riding_charge" -> lance && riding && using;
            case "lance_fall_flying" -> lance && flying;
            case "lance_use_ticks" -> lance ? useTicks : 0f;
            case "lance_attack_ticks" -> lance ? attackTicks : 0f;
            case "lance_speed" -> lance ? speed : 0f;
            case "lance_charge_progress" -> lance && using ? chargeProgress(stack, entity, useTicks) : 0f;
            case "mace_falling" -> falling;
            case "mace_can_smash" -> canSmash;
            case "mace_smashing" -> mace && attacking && canSmash;
            case "mace_wind_bursting" -> mace && windImpulseActive(entity);
            case "mace_attacking" -> mace && attacking;
            case "mace_riding" -> mace && riding;
            case "mace_fall_flying" -> mace && flying;
            case "mace_fall_distance" -> fallDistance;
            case "mace_vertical_speed" -> verticalSpeed;
            case "mace_attack_ticks" -> mace ? attackTicks : 0f;
            case "mace_smash_progress" -> canSmash ? Math.min(1f, fallDistance / 3f) : 0f;
            default -> throw new IllegalArgumentException("Unknown weapon query: " + name);
        };
    }

    /** MC 1.21.1 tracks wind-charge impulses on players only; the grace timer is widened by the AW. */
    private static boolean windImpulseActive(LivingEntity entity) {
        return entity instanceof Player player
                && (player.currentImpulseContextResetGraceTime > 0
                || player.isIgnoringFallDamageFromCurrentImpulse());
    }

    private static float chargeProgress(ItemStack stack, LivingEntity entity, float useTicks) {
        int duration = stack.getUseDuration(entity);
        return duration <= 0 ? 0f : Math.min(1f, useTicks / duration);
    }
}
