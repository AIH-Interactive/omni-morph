package top.aihmc.omnimorph.gui;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.Vec3;
import top.aihmc.omnimorph.avatar.AvatarLoadProgress;
import org.figuramc.figura.avatar.AvatarManager;
import org.figuramc.figura.utils.FiguraText;

/** Uses the native name-tag submission so labels follow the player without touching model ownership. */
public final class AvatarLoadingRenderer {
    private static final int SEGMENTS = 12;

    private AvatarLoadingRenderer() {
    }

    public static Component label(AvatarLoadProgress.Snapshot progress, long frame) {
        double fraction = progress.fraction();
        int completed = fraction < 0 ? -1 : (int) Math.floor(fraction * SEGMENTS);
        int moving = (int) Math.floorMod(frame / 3, SEGMENTS - 2);
        var label = FiguraText.of("loading." + progress.stage().name().toLowerCase(java.util.Locale.ROOT))
                .withStyle(ChatFormatting.WHITE).append(" ");
        for (int segment = 0; segment < SEGMENTS; segment++) {
            boolean filled = completed >= 0 ? segment < completed : segment >= moving && segment < moving + 3;
            label.append(Component.literal("▰").withStyle(filled ? ChatFormatting.AQUA : ChatFormatting.DARK_GRAY));
        }
        if (fraction >= 0) {
            label.append(Component.literal(" " + (int) Math.floor(fraction * 100) + "%").withStyle(ChatFormatting.WHITE));
        }
        return label;
    }

    public static void submit(LivingEntityRenderState state, PoseStack poses,
                              SubmitNodeCollector collector, CameraRenderState camera) {
        if (!(state instanceof AvatarRenderState player) || state.isInvisible
                || state.distanceToCameraSq > 4096 || AvatarManager.panic) {
            return;
        }
        var client = Minecraft.getInstance();
        if (client.level == null) {
            return;
        }
        var entity = client.level.getEntity(player.id);
        var progress = entity == null ? null : AvatarManager.getLoadingProgress(entity.getUUID());
        if (progress == null) {
            return;
        }
        submitProgress(progress, state, poses, collector, camera);
    }

    private static void submitProgress(AvatarLoadProgress.Snapshot progress, LivingEntityRenderState state,
                                       PoseStack poses, SubmitNodeCollector collector, CameraRenderState camera) {
        Vec3 attachment = state.nameTagAttachment == null
                ? new Vec3(0, state.boundingBoxHeight + 0.6, 0) : state.nameTagAttachment.add(0, 0.45, 0);
        collector.submitNameTag(poses, attachment, 0, label(progress,
                        top.aihmc.omnimorph.model.ysm.animation.YsmRenderFrameClock.currentFrame()),
                true, state.lightCoords, state.distanceToCameraSq, camera);
    }
}
