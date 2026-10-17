package top.aihmc.omnimorph.gui;

import com.mojang.blaze3d.platform.Window;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import top.aihmc.omnimorph.avatar.Avatar;
import top.aihmc.omnimorph.avatar.AvatarManager;
import top.aihmc.omnimorph.config.Configs;
import top.aihmc.omnimorph.model.rendering.EntityRenderMode;
import top.aihmc.omnimorph.utils.ui.UIHelper;
import org.joml.Vector3f;

public class PaperDoll {

    private static Long lastActivityTime = 0L;

    public static void render(GuiGraphicsExtractor gui, boolean force) {
        Minecraft minecraft = Minecraft.getInstance();
        LivingEntity entity = minecraft.getCameraEntity() instanceof LivingEntity e ? e : null;
        Avatar avatar;

        if ((!Configs.HAS_PAPERDOLL.value && !force) ||
                entity == null ||
                !Minecraft.renderNames() ||
                minecraft.debugEntries.isOverlayVisible() ||
                (Configs.FIRST_PERSON_PAPERDOLL.value && !minecraft.options.getCameraType().isFirstPerson() && !force) ||
                entity.isSleeping())
            return;

        // check if it should stay always on
        if (!Configs.PAPERDOLL_ALWAYS_ON.value && !force && (avatar = AvatarManager.getAvatar(entity)) != null && avatar.luaRuntime != null && !avatar.luaRuntime.renderer.forcePaperdoll) {
            // if action - reset activity time and enable can draw
            if (entity.isSprinting() ||
                    entity.isCrouching() ||
                    entity.isAutoSpinAttack() ||
                    entity.isVisuallySwimming() ||
                    entity.isFallFlying() ||
                    entity.isBlocking() ||
                    entity.onClimbable() ||
                    (entity instanceof Player p && p.getAbilities().flying))
                lastActivityTime = System.currentTimeMillis();

            // if activity time is greater than duration - return
            else if (System.currentTimeMillis() - lastActivityTime > 1000L)
                return;
        }

        // draw
        Window window = minecraft.getWindow();
        float screenWidth = window.getWidth();
        float screenHeight = window.getHeight();
        float guiScale = (float) window.getGuiScale();

        float scale = Configs.PAPERDOLL_SCALE.tempValue;
        float x = scale * 25f;
        float y = scale * 45f;
        x += (Configs.PAPERDOLL_X.tempValue / 100f) * screenWidth / guiScale;
        y += (Configs.PAPERDOLL_Y.tempValue / 100f) * screenHeight / guiScale;
        float x2 = x *2;
        float y2 = y *2;
        UIHelper.drawEntity(
                x, y,
                scale * 30f,
                Configs.PAPERDOLL_PITCH.tempValue, Configs.PAPERDOLL_YAW.tempValue,
                entity, gui, new Vector3f(), EntityRenderMode.PAPERDOLL,0, 0, (int) x2, (int) y2
        );
    }
}
