package top.aihmc.omnimorph.model.rendering;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.resources.model.cuboid.ItemTransform;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.figuramc.figura.avatar.Avatar;
import org.figuramc.figura.config.Configs;
import org.figuramc.figura.lua.api.world.ItemStackAPI;
import org.figuramc.figura.math.vector.FiguraVec3;

/** Routes first-person item submissions within the hand renderer's scoped invocation. */
public final class FirstPersonItemRender {
    private static Avatar currentAvatar;
    private static boolean eventActive;

    private FirstPersonItemRender() {
    }

    public static void withAvatar(Avatar avatar, Runnable drawing) {
        Avatar previous = currentAvatar;
        currentAvatar = avatar;
        try {
            drawing.run();
        } finally {
            currentAvatar = previous;
        }
    }

    public static boolean submit(ItemStack item, ItemDisplayContext context, ItemTransform transform,
                                 PoseStack pose, SubmitNodeCollector collector, int light, int overlay) {
        Avatar avatar = currentAvatar;
        if (!Configs.FIRST_PERSON_ITEM_REPLACEMENT.value
                || avatar == null || eventActive || !avatar.renderOwner().isCurrent()
                || context != ItemDisplayContext.FIRST_PERSON_LEFT_HAND
                && context != ItemDisplayContext.FIRST_PERSON_RIGHT_HAND) {
            return false;
        }
        eventActive = true;
        try {
            if (avatar.isYsmNative()) {
                return avatar.getYsmRuntime().renderer().submitFirstPersonItem(
                        item, context.leftHand(), pose, collector, light);
            }
            return avatar.itemRenderEvent(ItemStackAPI.verify(item), context.name(),
                    FiguraVec3.fromVec3f(transform.translation()),
                    FiguraVec3.of(transform.rotation().z(), transform.rotation().y(), transform.rotation().x()),
                    FiguraVec3.fromVec3f(transform.scale()), context.leftHand(), pose, collector, light, overlay);
        } finally {
            eventActive = false;
        }
    }
}
