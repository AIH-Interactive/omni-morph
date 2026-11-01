package top.aihmc.omnimorph.gui;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.Vec3;

import net.minecraft.world.entity.player.Player;
import top.aihmc.omnimorph.render.EntityFrame;
import top.aihmc.omnimorph.avatar.AvatarLoadProgress;
import org.figuramc.figura.avatar.AvatarManager;
import org.figuramc.figura.utils.FiguraText;

/** Draws loading labels with native name-tag geometry without touching model ownership. */
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

    public static void render(EntityFrame frame, PoseStack poses, MultiBufferSource buffers, int light) {
        if (!(frame.entity() instanceof Player player) || player.isInvisible() || AvatarManager.panic) {
            return;
        }
        var client = Minecraft.getInstance();
        if (client.level == null || client.getEntityRenderDispatcher().distanceToSqr(player) > 4096) {
            return;
        }
        var progress = AvatarManager.getLoadingProgress(player.getUUID());
        if (progress == null) {
            return;
        }
        // Keep the existing 0.45-block gap above the native label, including entity-specific offsets.
        Vec3 position = new Vec3(0, player.getNameTagOffsetY() + 0.45, 0);
        Component text = label(progress,
                top.aihmc.omnimorph.model.ysm.animation.YsmRenderFrameClock.currentFrame());
        Font font = client.font;
        int background = (int) (client.options.getBackgroundOpacity(0.25f) * 255f) << 24;
        float x = -font.width(text) / 2f;
        poses.pushPose();
        try {
            poses.translate(position.x, position.y, position.z);
            poses.mulPose(client.getEntityRenderDispatcher().cameraOrientation());
            poses.scale(0.025f, -0.025f, 0.025f);
            font.drawInBatch(text, x, 0, 0x20ffffff, false, poses.last().pose(), buffers,
                    Font.DisplayMode.SEE_THROUGH, background, light);
            font.drawInBatch(text, x, 0, -1, false, poses.last().pose(), buffers,
                    Font.DisplayMode.NORMAL, 0, light);
        } finally {
            poses.popPose();
        }
    }
}
