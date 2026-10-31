package top.aihmc.omnimorph.model.ysm;

import net.minecraft.world.entity.EntityType;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.Parrot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.FishingHook;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.ThrowableItemProjectile;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import top.aihmc.omnimorph.mixin.AbstractArrowAccessor;
import top.aihmc.omnimorph.mixin.FishingHookAccessor;
import top.aihmc.omnimorph.mixin.ThrowableItemProjectileAccessor;

import java.util.Locale;
import java.util.Optional;

/**
 * Entity-side values for the remaining reference {@code ysm.*} names (D015). Each method follows
 * the matching Sparkle YSMBinding registration; the binding layer applies the entity-type gate.
 */
public final class YsmReferenceQuery {
    /** Fabric ForgeAttributes return no attribute, so the reference always reports these defaults. */
    public static final double BLOCK_REACH = 4.5d;
    public static final double ENTITY_REACH = 3.0d;
    public static final double SWIM_SPEED = 1.0d;
    public static final double ENTITY_GRAVITY = 0.08d;
    public static final double STEP_HEIGHT_ADDITION = 0.0d;
    public static final double NAMETAG_DISTANCE = 64.0d;

    private YsmReferenceQuery() {
    }

    /** LadderFacing: horizontal facing of the last climbable block, otherwise 0. */
    public static int ladderFacing(LivingEntity entity) {
        Optional<BlockPos> climbable = entity.getLastClimbablePos();
        if (climbable.isEmpty() || entity.level() == null) {
            return 0;
        }
        Optional<Direction> facing = entity.level().getBlockState(climbable.get())
                .getOptionalValue(HorizontalDirectionalBlock.FACING);
        return facing.map(Direction::get2DDataValue).orElse(0);
    }

    public static boolean chargedCrossbow(LivingEntity entity, InteractionHand hand) {
        ItemStack stack = entity.getItemInHand(hand);
        return stack.is(Items.CROSSBOW) && CrossbowItem.isCharged(stack);
    }

    /** Reference item_is_charged reads the off hand only. */
    public static float offhandItemCharged(Player player) {
        ItemStack stack = player.getOffhandItem();
        return stack.getItem() instanceof CrossbowItem && CrossbowItem.isCharged(stack) ? 1f : 0f;
    }

    /** Touhou Little Maid sitting is a compat branch the reference only reaches with that mod installed. */
    public static boolean fishing(LivingEntity entity) {
        return entity instanceof Player player && player.fishing != null;
    }

    public static String shoulderParrotVariant(Player player, boolean left) {
        Parrot.Variant variant = shoulderParrot(player, left).orElse(null);
        return variant != null ? variant.name().toLowerCase(Locale.ENGLISH) : "empty";
    }

    public static boolean hasShoulderParrot(Player player, boolean left) {
        return shoulderParrot(player, left).isPresent();
    }

    /** MC 1.21.1 keeps shoulder riders as entity NBT; this mirrors ParrotOnShoulderLayer's parrot check. */
    private static Optional<Parrot.Variant> shoulderParrot(Player player, boolean left) {
        CompoundTag shoulder = left ? player.getShoulderEntityLeft() : player.getShoulderEntityRight();
        boolean parrot = EntityType.byString(shoulder.getString("id"))
                .filter(type -> type == EntityType.PARROT)
                .isPresent();
        return parrot ? Optional.of(Parrot.Variant.byId(shoulder.getInt("Variant"))) : Optional.empty();
    }

    /** Block or entity registry id under the client crosshair; empty when nothing is targeted. */
    public static String hitTargetId(LocalPlayer player) {
        Minecraft client = Minecraft.getInstance();
        HitResult hit = client == null ? null : client.hitResult;
        if (hit instanceof BlockHitResult block) {
            if (block.getType() == HitResult.Type.MISS || client.level == null) {
                return "";
            }
            ResourceLocation key = BuiltInRegistries.BLOCK.getKey(client.level.getBlockState(block.getBlockPos()).getBlock());
            return key == null ? "" : key.toString();
        }
        if (hit instanceof EntityHitResult entityHit) {
            ResourceLocation key = BuiltInRegistries.ENTITY_TYPE.getKey(entityHit.getEntity().getType());
            return key == null ? "" : key.toString();
        }
        return "";
    }

    /** The reference switch has no returning branch, so hit_target_type is always empty. */
    public static String hitTargetType(LocalPlayer player) {
        return "";
    }

    public static String hookedIn(FishingHook hook) {
        Entity hooked = ((FishingHookAccessor) hook).figura$getHookedIn();
        ResourceLocation key = hooked == null ? null : BuiltInRegistries.ENTITY_TYPE.getKey(hooked.getType());
        return key == null ? "" : key.toString();
    }

    public static boolean biting(FishingHook hook) {
        return ((FishingHookAccessor) hook).figura$isBiting();
    }

    public static boolean inGround(AbstractArrow arrow) {
        return ((AbstractArrowAccessor) arrow).figura$isInGround();
    }

    public static int inGroundTime(AbstractArrow arrow) {
        return ((AbstractArrowAccessor) arrow).figura$getInGroundTime();
    }

    public static String throwableItem(ThrowableItemProjectile projectile) {
        ResourceLocation key = BuiltInRegistries.ITEM.getKey(((ThrowableItemProjectileAccessor) projectile).figura$getDefaultItem());
        return key == null ? "" : key.toString();
    }

    /** MovementQuery.getDeltaMovementLength without the reference's synthetic-walk tracker. */
    public static float deltaMovementLength(Entity entity) {
        Vec3 delta = entity.getDeltaMovement();
        if (delta != null && Double.isFinite(delta.lengthSqr()) && delta.lengthSqr() > 0d) {
            return (float) delta.length();
        }
        double dx = entity.getX() - entity.xo;
        double dy = entity.getY() - entity.yo;
        double dz = entity.getZ() - entity.zo;
        double length = Math.sqrt(dx * dx + dy * dy + dz * dz);
        return Double.isFinite(length) ? (float) length : 0f;
    }

    /** tcos0: cos(life * 103.2 degrees) * min(1, moveDist * 4) * 20. */
    public static float tcos0(Entity entity, float lifeSeconds) {
        float speed = entity == null ? 0f : entity.moveDist;
        return (float) (Math.cos(lifeSeconds * 103.2d * Math.PI / 180.0d) * Math.min(1.0f, speed * 4.0f) * 20.0f);
    }

    /** get_equipped_item_name: item path in the main hand, or the off hand for "off_hand"; "empty" otherwise. */
    public static String equippedItemName(LivingEntity entity, String hand) {
        ItemStack stack = "off_hand".equals(hand) ? entity.getOffhandItem() : entity.getMainHandItem();
        if (stack.isEmpty()) {
            return "empty";
        }
        return BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath();
    }
}
