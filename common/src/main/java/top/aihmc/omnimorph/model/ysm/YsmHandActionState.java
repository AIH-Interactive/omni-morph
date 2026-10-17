package top.aihmc.omnimorph.model.ysm;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUseAnimation;
import net.minecraft.world.item.Items;

/** Local interaction pulses plus native, per-hand queries. Pulses never belong to an observer. */
public final class YsmHandActionState {
    private LivingEntity player;
    private boolean ready;
    private boolean attackDown;
    private int rawAttackDedupTicks;
    private int swingTicks;
    private int swingAge;
    private InteractionHand swingHand = InteractionHand.MAIN_HAND;
    private int useTicks;
    private int useAge;
    private InteractionHand useHand = InteractionHand.MAIN_HAND;
    private Item useItem;

    public void prepare(LivingEntity player, boolean ready) {
        if (!ready || this.player != player) {
            swingTicks = 0;
            swingAge = 0;
            useTicks = 0;
            useAge = 0;
            rawAttackDedupTicks = 0;
            useItem = null;
            attackDown = false;
        }
        // Focus suppresses input pulses, not the identity used by native local/observer predicates.
        this.player = player;
        this.ready = ready && player != null;
    }

    public void tick(boolean attackDown) {
        if (!ready) {
            return;
        }
        boolean rawAttack = rawAttackDedupTicks > 0;
        if (rawAttackDedupTicks > 0) {
            rawAttackDedupTicks--;
        }
        if (attackDown && !this.attackDown && !rawAttack && !offhandShield(player)) {
            recordSwing(InteractionHand.MAIN_HAND);
        }
        this.attackDown = attackDown;
        if (useTicks > 0) {
            if (player.isUsingItem() || !validUse(player, useHand)) {
                clearUse();
            } else {
                useAge++;
                if (--useTicks == 0) {
                    clearUse();
                }
            }
        }
        if (swingTicks > 0) {
            swingTicks--;
            swingAge++;
        }
    }

    public void mouseEvent(int button, int action) {
        if (!ready) {
            return;
        }
        if (button == 0 && action == 1 && !offhandShield(player)) {
            rawAttackDedupTicks = 2;
            recordSwing(InteractionHand.MAIN_HAND);
        } else if (button == 1 && action == 0) {
            clearUse();
        } else if (button == 1 && action == 1) {
            ItemStack offhand = player.getOffhandItem();
            InteractionHand hand = isShield(offhand) || (player.getMainHandItem().isEmpty()
                    && !offhand.isEmpty() && !offhand.is(Items.TOTEM_OF_UNDYING))
                    ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND;
            ItemStack stack = player.getItemInHand(hand);
            if (stack.isEmpty() || stack.getUseAnimation() == ItemUseAnimation.NONE) {
                clearUse();
            } else {
                useHand = hand;
                useItem = stack.getItem();
                useTicks = 2;
                useAge = 0;
            }
        }
    }

    public boolean using(LivingEntity entity, InteractionHand hand, boolean localOwner) {
        if (entity == null || entity.isSleeping()) {
            return false;
        }
        return (entity.isUsingItem() && entity.getUsedItemHand() == hand)
                || (localOwner && entity == player && ready && validUse(entity, hand));
    }

    public boolean swinging(LivingEntity entity, InteractionHand hand, boolean localOwner) {
        if (entity == null) {
            return false;
        }
        boolean pulse = localOwner && entity == player && ready && swingTicks > 0 && swingHand == hand;
        // The reference ctrl matcher permits a local-model pulse when its native predicate rejects the state.
        if (entity.isSleeping()) {
            return pulse;
        }
        if (entity == player && hand == InteractionHand.MAIN_HAND && offhandShield(player)) {
            return pulse;
        }
        if (entity.swinging && entity.swingingArm == hand) {
            return true;
        }
        if (entity != player && hand == InteractionHand.MAIN_HAND && entity.getAttackAnim(0f) > 0f) {
            return true;
        }
        return pulse;
    }

    private void recordSwing(InteractionHand hand) {
        swingHand = hand;
        swingTicks = 10;
        swingAge = 0;
    }

    public boolean anySwinging(LivingEntity entity, boolean localOwner) {
        return localPulse(entity, localOwner) || swinging(entity, InteractionHand.MAIN_HAND, false)
                || swinging(entity, InteractionHand.OFF_HAND, false);
    }

    public float swingTime(LivingEntity entity, float partialTick, boolean localOwner) {
        if (localPulse(entity, localOwner)) {
            return Math.max(1f, swingAge + partialTick);
        }
        if (entity.isSleeping() || (entity == player && offhandShield(entity))) {
            return 0f;
        }
        return entity.swinging ? Math.max(0f, entity.swingTime + partialTick) : 0f;
    }

    public float attackProgress(LivingEntity entity, float partialTick, boolean localOwner) {
        if (localPulse(entity, localOwner) && swingHand == InteractionHand.MAIN_HAND) {
            return Math.min(1f, swingTime(entity, partialTick, true) / 6f);
        }
        if (entity.isSleeping() || (entity == player && offhandShield(entity))) {
            return 0f;
        }
        float progress = entity.getAttackAnim(partialTick);
        return progress > 0f ? progress : 0f;
    }

    public InteractionHand usedHand(LivingEntity entity, boolean localOwner) {
        if (entity.isUsingItem()) {
            return entity.getUsedItemHand();
        }
        return localOwner && entity == player && ready && validUse(entity, useHand)
                ? useHand : InteractionHand.MAIN_HAND;
    }

    public int useTime(LivingEntity entity, boolean localOwner) {
        if (entity.isUsingItem()) {
            return entity.getTicksUsingItem();
        }
        return localOwner && entity == player && ready && !entity.isSleeping() && validUse(entity, useHand)
                ? Math.max(1, useAge) : 0;
    }

    public int remainingUseTime(LivingEntity entity, boolean localOwner) {
        if (entity.isUsingItem()) {
            return entity.getUseItemRemainingTicks();
        }
        return using(entity, usedHand(entity, localOwner), localOwner)
                ? Math.max(1, 6 - useTime(entity, localOwner)) : 0;
    }

    private boolean localPulse(LivingEntity entity, boolean localOwner) {
        return localOwner && ready && entity == player && swingTicks > 0;
    }

    private boolean validUse(LivingEntity entity, InteractionHand hand) {
        if (useTicks <= 0 || hand != useHand || useItem == null) {
            return false;
        }
        ItemStack stack = entity.getItemInHand(hand);
        return !stack.isEmpty() && stack.getItem() == useItem;
    }

    private void clearUse() {
        useTicks = 0;
        useAge = 0;
        useItem = null;
    }

    private static boolean offhandShield(LivingEntity entity) {
        return entity.isUsingItem() && entity.getUsedItemHand() == InteractionHand.OFF_HAND
                && isShield(entity.getUseItem());
    }

    private static boolean isShield(ItemStack stack) {
        return !stack.isEmpty() && (stack.is(Items.SHIELD)
                || stack.is(TagKey.create(Registries.ITEM, Identifier.parse("sparkle_morpher:shields"))));
    }
}
