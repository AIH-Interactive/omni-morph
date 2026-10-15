package org.figuramc.figura.model.ysm;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.LivingEntity;
import org.figuramc.figura.avatar.Avatar;

/** Formal hand queries share native per-hand state and owner-restricted device pulses. */
public final class YsmHandQuery {
    private YsmHandQuery() {
    }

    private static boolean localOwner(Avatar.MolangContext context) {
        Minecraft client = Minecraft.getInstance();
        return context.owner != null && context.owner.isHost && client != null
                && YsmInputState.ownsInput(true, context.entity, client.player)
                && !YsmParticleRuntime.isPreview(context.owner.renderMode);
    }

    public static boolean using(Avatar.MolangContext context) {
        var entity = (LivingEntity) context.entity;
        var hands = YsmInputState.device().hands();
        boolean local = localOwner(context);
        return hands.using(entity, hands.usedHand(entity, local), local);
    }

    public static boolean swinging(Avatar.MolangContext context) {
        return YsmInputState.device().hands().anySwinging((LivingEntity) context.entity, localOwner(context));
    }

    public static float swingTicks(Avatar.MolangContext context) {
        return YsmInputState.device().hands().swingTime(
                (LivingEntity) context.entity, context.ysm_partial_tick, localOwner(context));
    }

    public static float attackProgress(Avatar.MolangContext context) {
        return YsmInputState.device().hands().attackProgress(
                (LivingEntity) context.entity, context.ysm_partial_tick, localOwner(context));
    }

    public static int useTicks(Avatar.MolangContext context) {
        return YsmInputState.device().hands().useTime((LivingEntity) context.entity, localOwner(context));
    }

    public static int remainingUseTicks(Avatar.MolangContext context) {
        return YsmInputState.device().hands().remainingUseTime((LivingEntity) context.entity, localOwner(context));
    }

    public static void capture(Avatar.MolangContext context) {
        if (!(context.entity instanceof LivingEntity)) {
            context.is_using_item = context.is_swinging = context.swing_time = context.attack_time = 0f;
            return;
        }
        context.is_using_item = using(context) ? 1f : 0f;
        context.is_swinging = swinging(context) ? 1f : 0f;
        context.swing_time = swingTicks(context) / 20f;
        context.attack_time = attackProgress(context);
    }
}
