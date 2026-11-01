package org.figuramc.figura.utils.ui;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FastColor;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraft.Util;
import net.minecraft.world.entity.LivingEntity;
import org.figuramc.figura.FiguraMod;
import org.figuramc.figura.avatar.Avatar;
import org.figuramc.figura.avatar.AvatarManager;
import org.figuramc.figura.avatar.Badges;
import org.figuramc.figura.config.Configs;
import org.figuramc.figura.ducks.EntityRenderDispatcherAccessor;
import org.figuramc.figura.gui.screens.AbstractPanelScreen;
import org.figuramc.figura.gui.screens.FiguraConfirmScreen;
import org.figuramc.figura.gui.widgets.ContextMenu;
import org.figuramc.figura.gui.widgets.FiguraWidget;
import org.figuramc.figura.math.vector.FiguraVec4;
import org.figuramc.figura.model.rendering.EntityRenderMode;
import top.aihmc.omnimorph.render.EntityFrame;
import top.aihmc.omnimorph.model.outfit.OutfitRenderBinding;
import top.aihmc.omnimorph.model.library.LibraryPreviewBinding;
import org.figuramc.figura.utils.FiguraIdentifier;
import org.figuramc.figura.utils.RenderUtils;
import org.figuramc.figura.utils.TextUtils;
import org.joml.*;

import java.lang.Math;
import java.util.List;
import java.util.function.Consumer;

public final class UIHelper {

    private UIHelper() {}

    // -- Variables -- // 

    public static final ResourceLocation OUTLINE_FILL = FiguraIdentifier.of("textures/gui/outline_fill.png");
    public static final ResourceLocation OUTLINE = FiguraIdentifier.of("textures/gui/outline.png");
    public static final ResourceLocation TOOLTIP = FiguraIdentifier.of("textures/gui/tooltip.png");
    public static final ResourceLocation UI_FONT = FiguraIdentifier.of("ui");
    public static final ResourceLocation SPECIAL_FONT = FiguraIdentifier.of("special");

    public static final Component UP_ARROW = Component.literal("^").withStyle(Style.EMPTY.withFont(UIHelper.UI_FONT));
    public static final Component DOWN_ARROW = Component.literal("V").withStyle(Style.EMPTY.withFont(UIHelper.UI_FONT));

    // Used for GUI rendering
    //private static final CustomFramebuffer FIGURA_FRAMEBUFFER = new CustomFramebuffer();
    private static int previousFBO = -1;
    public static boolean paperdoll = false;
    public static float fireRot = 0f;
    public static float dollScale = 1f;

    // -- Functions -- // 

/*    public static void useFiguraGuiFramebuffer() {
        previousFBO = GL30.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);

        int width = Minecraft.getInstance().getWindow().getWidth();
        int height = Minecraft.getInstance().getWindow().getHeight();
        FIGURA_FRAMEBUFFER.setSize(width, height);

        // Enable stencil buffer during this phase of rendering
        GL30.glEnable(GL30.GL_STENCIL_TEST);
        GlStateManager._stencilMask(0xFF);
        // Bind custom GUI framebuffer to be used for rendering
        GlStateManager._glBindFramebuffer(GL30.GL_FRAMEBUFFER, FIGURA_FRAMEBUFFER.getFbo());

        // Clear GUI framebuffer
        GlStateManager._clearStencil(0);
        GlStateManager._clearColor(0f, 0f, 0f, 1f);
        GlStateManager._clearDepth(1);
        GlStateManager._clear(GL11.GL_COLOR_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT | GL30.GL_STENCIL_BUFFER_BIT);

        Matrix4f mf = RenderSystem.getProjectionMatrix();
        Minecraft.getInstance().getMainRenderTarget().blitToScreen(width, height);
        RenderSystem.setProjectionMatrix(mf, ProjectionType.ORTHOGRAPHIC);
    }

    public static void useVanillaFramebuffer() {
        // Reset state before we go back to normal rendering
        GlStateManager._enableDepthTest();
        // Set a sensible default for stencil buffer operations
        GlStateManager._stencilFunc(GL11.GL_EQUAL, 0, 0xFF);
        GL30.glDisable(GL30.GL_STENCIL_TEST);

        // Bind vanilla framebuffer again
        GlStateManager._glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, previousFBO);

        RenderSystem.disableBlend();
        // Draw GUI framebuffer -> vanilla framebuffer
        int windowWidth = Minecraft.getInstance().getWindow().getWidth();
        int windowHeight = Minecraft.getInstance().getWindow().getHeight();

        Matrix4f mf = RenderSystem.getProjectionMatrix();
        FIGURA_FRAMEBUFFER.drawToScreen(windowWidth, windowHeight);
        RenderSystem.setProjectionMatrix(mf, ProjectionType.ORTHOGRAPHIC);
        RenderSystem.enableBlend();
    }*/

    public static void drawEntity(float x, float y, float scale, float pitch, float yaw,
                                  LivingEntity entity, GuiGraphics gui, Vector3f offset, EntityRenderMode renderMode) {
        drawEntity(x, y, scale, pitch, yaw, entity, gui, offset, renderMode,
                0, 0, gui.guiWidth(), gui.guiHeight());
    }

    public static void drawEntity(float x, float y, float scale, float pitch, float yaw,
                                  LivingEntity entity, GuiGraphics gui, Vector3f offset, EntityRenderMode renderMode,
                                  int x1, int y1, int x2, int y2) {
        gui.flush();
        EntityRenderDispatcher dispatcher = Minecraft.getInstance().getEntityRenderDispatcher();
        PoseStack pose = gui.pose();
        float headX = entity.getXRot();
        float headY = entity.yHeadRot;
        boolean invisible = entity.isInvisible();
        boolean renderHitboxes = dispatcher.shouldRenderHitBoxes();
        boolean renderShadow = ((EntityRenderDispatcherAccessor) dispatcher).figura$shouldRenderShadow();
        Quaternionf cameraOrientation = new Quaternionf(dispatcher.cameraOrientation());
        boolean previousPaperdoll = paperdoll;
        float previousFireRot = fireRot;
        float previousDollScale = dollScale;
        EntityFrame frame = new EntityFrame(entity, 1f);
        OutfitRenderBinding.capture(entity, frame, 1f, false);
        Avatar avatar = AvatarManager.getAvatar(frame);
        EntityRenderMode previousRenderMode = avatar == null ? null : avatar.renderMode;

        gui.enableScissor(x1, y1, x2, y2);
        pose.pushPose();
        try {
            float bodyY = entity.yBodyRot; // not truly a backup
            if (entity.getVehicle() instanceof LivingEntity l) {
                bodyY = l.yBodyRot;
            }

            // setup rendering properties
            float xRot, yRot;
            double xPos = 0d;
            double yPos = 0d;

            switch (renderMode) {
                case PAPERDOLL -> {
                    // rotations
                    xRot = pitch;
                    yRot = yaw + bodyY + 180;

                    // positions
                    yPos--;

                    if (entity.isFallFlying())
                        xPos += Mth.triangleWave((float) Math.toRadians(270), Mth.TWO_PI);

                    if (entity.isAutoSpinAttack() || entity.isVisuallySwimming() || entity.isFallFlying()) {
                        yPos++;
                        entity.setXRot(0f);
                    }

                    // lightning
                    Lighting.setupForEntityInInventory();

                    // invisibility
                    if (Configs.PAPERDOLL_INVISIBLE.value)
                        entity.setInvisible(false);
                }
                case FIGURA_GUI -> {
                    // rotations
                    xRot = pitch;
                    yRot = yaw + bodyY + 180;

                    if (!Configs.PREVIEW_HEAD_ROTATION.value) {
                        entity.setXRot(0f);
                        entity.yHeadRot = bodyY;
                    }

                    // positions
                    yPos--;

                    // set up lighting
                    useFiguraLighting();
                    // 1.20.5 invered the z for lights

                    // invisibility
                    entity.setInvisible(false);
                }
                default -> {
                    // rotations
                    xRot = pitch;
                    yRot = yaw + bodyY + 180;

                    entity.setXRot(-xRot);
                    entity.yHeadRot = -yaw + bodyY;

                    // lightning
                    Lighting.setupForEntityInInventory();
                }
            }

            if (RenderUtils.vanillaModelAndScript(avatar) && !avatar.luaRuntime.renderer.getRootRotationAllowed()) {
                yRot = yaw;
            }
            Quaternionf viewRotation = Axis.XP.rotationDegrees(xRot).mul(Axis.YP.rotationDegrees(yRot));
            Quaternionf modelRotation = Axis.ZP.rotationDegrees(180f).mul(viewRotation);
            pose.translate(x, y, 250);
            pose.scale(scale, scale, -scale);
            pose.mulPose(modelRotation);
            pose.translate(offset.x, offset.y, offset.z);

            dispatcher.setRenderHitBoxes(false);
            dispatcher.setRenderShadow(false);
            dispatcher.overrideCameraOrientation(viewRotation.conjugate(new Quaternionf()));
            paperdoll = renderMode != EntityRenderMode.MINECRAFT_GUI;
            fireRot = -yRot;
            dollScale = scale;
            if (avatar != null) {
                avatar.renderMode = renderMode;
            }
            EntityFrame.prepare(frame);
            double drawX = xPos;
            double drawY = yPos;
            RenderSystem.runAsFancy(() -> dispatcher.render(entity, drawX, drawY, 0, 0f, 1f,
                    pose, gui.bufferSource(), LightTexture.FULL_BRIGHT));
            if (renderMode == EntityRenderMode.FIGURA_GUI) {
                Matrix4f particlePose = new Matrix4f(pose.last().pose()).translate((float) drawX, (float) drawY, 0);
                LibraryPreviewBinding.particles(frame, modelRotation, 1f).draw(particlePose, gui.bufferSource());
            }
            gui.flush();
        } finally {
            EntityFrame.clearPrepared(frame);
            if (EntityFrame.current() == frame) {
                EntityFrame.end(frame);
            }
            entity.setXRot(headX);
            entity.yHeadRot = headY;
            entity.setInvisible(invisible);
            if (avatar != null) {
                avatar.renderMode = previousRenderMode;
            }
            paperdoll = previousPaperdoll;
            fireRot = previousFireRot;
            dollScale = previousDollScale;
            dispatcher.setRenderHitBoxes(renderHitboxes);
            dispatcher.setRenderShadow(renderShadow);
            dispatcher.overrideCameraOrientation(cameraOrientation);
            pose.popPose();
            gui.disableScissor();
            Lighting.setupFor3DItems();
        }
    }

    public static void enableBlend() {
        GlStateManager._enableBlend();
        GlStateManager._blendFuncSeparate(770, 771, 1, 0);
    }

    public static void blit(GuiGraphics gui, int x, int y, int width, int height, ResourceLocation texture) {
        gui.blit(texture, x, y, width, height, 0f, 0f, 1, 1, 1, 1);
    }

    public static void renderAnimatedBackground(GuiGraphics gui, ResourceLocation texture, float x, float y, float width, float height, float textureWidth, float textureHeight, double speed, float delta) {
        if (speed != 0) {
            double d = (FiguraMod.ticks + delta) * speed;
            x -= d % textureWidth;
            y -= d % textureHeight;
        }

        width += textureWidth;
        height += textureHeight;

        if (speed < 0) {
            x -= textureWidth;
            y -= textureHeight;
        }

        renderBackgroundTexture(gui, texture, x, y, width, height, textureWidth, textureHeight);
    }

    public static void renderBackgroundTexture(GuiGraphics gui, ResourceLocation texture, float x, float y, float width, float height, float textureWidth, float textureHeight) {
        prepareTexture(gui, texture);
        BufferBuilder buffer = Tesselator.getInstance().getBuilder();
        buffer.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX);
        float u1 = width / textureWidth;
        float v1 = height / textureHeight;
        quad(buffer, gui.pose().last().pose(), x, y, width, height, -999f, 0f, u1, 0f, v1);
        BufferUploader.drawWithShader(buffer.end());

    }

    public static void fillRounded(GuiGraphics gui, int x, int y, int width, int height, int color) {
        gui.fill(x + 1, y, x + width - 1, y + 1, color);
        gui.fill(x, y + 1, x + width, y + height - 1, color);
        gui.fill(x + 1, y + height - 1, x + width - 1, y + height, color);
    }

    public static void fillOutline(GuiGraphics gui, int x, int y, int width, int height, int color) {
        gui.fill(x + 1, y, x + width - 1, y + 1, color);
        gui.fill(x, y + 1, x + 1, y + height - 1, color);
        gui.fill(x + width - 1, y + 1, x + width, y + height - 1, color);
        gui.fill(x + 1, y + height - 1, x + width - 1, y + height, color);
    }

    public static void blitSliced(GuiGraphics gui, int x, int y, int width, int height, ResourceLocation texture) {
        blitSliced(gui, x, y, width, height, 0f, 0f, 15, 15, 15, 15, texture);
    }

    public static void blitSliced(GuiGraphics gui, int x, int y, int width, int height, float u, float v, int regionWidth, int regionHeight, int textureWidth, int textureHeight, ResourceLocation texture) {

        prepareTexture(gui, texture);
        Matrix4f pose = gui.pose().last().pose();
        BufferBuilder buffer = Tesselator.getInstance().getBuilder();
        buffer.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX);

        float rWidthThird = regionWidth / 3f;
        float rHeightThird = regionHeight / 3f;

        // top left
        quad(buffer, pose, x, y, rWidthThird, rHeightThird, u, v, rWidthThird, rHeightThird, textureWidth, textureHeight);
        // top middle
        quad(buffer, pose, x + rWidthThird, y, width - rWidthThird * 2, rHeightThird, u + rWidthThird, v, rWidthThird, rHeightThird, textureWidth, textureHeight);
        // top right
        quad(buffer, pose, x + width - rWidthThird, y, rWidthThird, rHeightThird, u + rWidthThird * 2, v, rWidthThird, rHeightThird, textureWidth, textureHeight);

        // middle left
        quad(buffer, pose, x, y + rHeightThird, rWidthThird, height - rHeightThird * 2, u, v + rHeightThird, rWidthThird, rHeightThird, textureWidth, textureHeight);
        // middle middle
        quad(buffer, pose, x + rWidthThird, y + rHeightThird, width - rWidthThird * 2, height - rHeightThird * 2, u + rWidthThird, v + rHeightThird, rWidthThird, rHeightThird, textureWidth, textureHeight);
        // middle right
        quad(buffer, pose, x + width - rWidthThird, y + rHeightThird, rWidthThird, height - rHeightThird * 2, u + rWidthThird * 2, v + rHeightThird, rWidthThird, rHeightThird, textureWidth, textureHeight);

        // bottom left
        quad(buffer, pose, x, y + height - rHeightThird, rWidthThird, rHeightThird, u, v + rHeightThird * 2, rWidthThird, rHeightThird, textureWidth, textureHeight);
        // bottom middle
        quad(buffer, pose, x + rWidthThird, y + height - rHeightThird, width - rWidthThird * 2, rHeightThird, u + rWidthThird, v + rHeightThird * 2, rWidthThird, rHeightThird, textureWidth, textureHeight);
        // bottom right
        quad(buffer, pose, x + width - rWidthThird, y + height - rHeightThird, rWidthThird, rHeightThird, u + rWidthThird * 2, v + rHeightThird * 2, rWidthThird, rHeightThird, textureWidth, textureHeight);

        BufferUploader.drawWithShader(buffer.end());
    }

    public static void renderHalfTexture(GuiGraphics gui, int x, int y, int width, int height, int textureWidth, ResourceLocation texture) {
        renderHalfTexture(gui, x, y, width, height, 0f, 0f, textureWidth, 1, textureWidth, 1, texture);
    }

    public static void renderHalfTexture(GuiGraphics gui, int x, int y, int width, int height, float u, float v, int regionWidth, int regionHeight, int textureWidth, int textureHeight, ResourceLocation texture) {
        enableBlend();

        // left
        int w = width / 2;
        gui.blit(texture, x, y, w, height, u, v, w, regionHeight, textureWidth, textureHeight);

        // right
        x += w;
        if (width % 2 == 1) w++;
        gui.blit(texture, x, y, w, height, u + regionWidth - w, v, w, regionHeight, textureWidth, textureHeight);
    }

    public static void renderSprite(GuiGraphics gui, int x, int y, int z, int width, int height, TextureAtlasSprite sprite) {
        gui.blit(x, y, z, width, height, sprite);
    }

    private static void prepareTexture(GuiGraphics gui, ResourceLocation texture) {
        gui.flush();
        enableBlend();
        RenderSystem.setShader(GameRenderer::getPositionTexShader);
        RenderSystem.setShaderTexture(0, texture);
    }

    private static void quad(BufferBuilder buffer, Matrix4f pose, float x, float y, float width, float height,
                             float u, float v, float regionWidth, float regionHeight, int textureWidth, int textureHeight) {
        float u0 = u / textureWidth;
        float v0 = v / textureHeight;
        float u1 = (u + regionWidth) / textureWidth;
        float v1 = (v + regionHeight) / textureHeight;
        quad(buffer, pose, x, y, width, height, 0f, u0, u1, v0, v1);
    }

    private static void quad(BufferBuilder buffer, Matrix4f pose, float x, float y, float width, float height,
                             float z, float u0, float u1, float v0, float v1) {
        float x1 = x + width;
        float y1 = y + height;
        buffer.vertex(pose, x, y1, z).uv(u0, v1).endVertex();
        buffer.vertex(pose, x1, y1, z).uv(u1, v1).endVertex();
        buffer.vertex(pose, x1, y, z).uv(u1, v0).endVertex();
        buffer.vertex(pose, x, y, z).uv(u0, v0).endVertex();
    }

    public static void renderWithoutScissors(GuiGraphics gui, Consumer<GuiGraphics> toRun) {
        gui.flush();
        // Keep the logical stack so disabling the temporary entry restores its parent.
        gui.enableScissor(0, 0, 1, 1);
        RenderSystem.disableScissor();
        try {
            toRun.accept(gui);
            gui.flush();
        } finally {
            gui.disableScissor();
        }
    }

    public static void highlight(GuiGraphics gui, FiguraWidget widget, Component text) {
        // screen
        int screenW, screenH;
        if (Minecraft.getInstance().screen instanceof AbstractPanelScreen panel) {
            screenW = panel.width;
            screenH = panel.height;
        } else {
            return;
        }

        // draw

        int x = widget.getX();
        int y = widget.getY();
        int width = widget.getWidth();
        int height = widget.getHeight();
        int color = 0xDD000000;

        // left
        gui.fill(0, 0, x, y + height, color);
        // right
        gui.fill(x + width, y, screenW, screenH, color);
        // up
        gui.fill(x, 0, screenW, y, color);
        // down
        gui.fill(0, y + height, x + width, screenH, color);

        // outline
        fillOutline(gui, Math.max(x - 1, 0), Math.max(y - 1, 0), Math.min(width + 2, screenW), Math.min(height + 2, screenH), 0xFFFFFFFF);

        // text

        if (text == null)
            return;

        int bottomDistance = screenH - (y + height);
        int rightDistance = screenW - (x + width);
        int verArea = y * screenW - bottomDistance * screenW;
        int horArea = x * screenH - rightDistance * screenH;
        FiguraVec4 square = new FiguraVec4();

        if (Math.abs(verArea) > Math.abs(horArea)) {
            if (verArea >= 0) {
                square.set(0, 0, screenW, y);
            } else {
                square.set(0, y + height, screenW, bottomDistance);
            }
        } else {
            if (horArea >= 0) {
                square.set(0, 0, x, screenH);
            } else {
                square.set(x + width, 0, rightDistance, screenH);
            }
        }

        // fill(stack, (int) square.x, (int) square.y, (int) (square.x + square.z), (int) (square.y + square.w), 0xFFFF72AD);
        // renderTooltip(stack, text, 0, 0, false);
    }

    // widget.isMouseOver() returns false if the widget is disabled or invisible
    public static boolean isMouseOver(int x, int y, int width, int height, double mouseX, double mouseY) {
        return isMouseOver(x, y, width, height, mouseX, mouseY, false);
    }

    public static boolean isMouseOver(int x, int y, int width, int height, double mouseX, double mouseY, boolean force) {
        ContextMenu context = force ? null : getContext();
        return (context == null || !context.isVisible()) && mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
    }

    public static void renderOutlineText(GuiGraphics gui, Font textRenderer, Component text, int x, int y, int color, int outline) {
        color = adjustColor(color);
        outline = adjustColor(outline);
        gui.flush();
        // The native path draws both the glyph-aware outline and the foreground.
        textRenderer.drawInBatch8xOutline(text.getVisualOrderText(), x, y, color, outline,
                gui.pose().last().pose(), gui.bufferSource(), LightTexture.FULL_BRIGHT);
        gui.flush();
    }

    public static void renderTooltip(GuiGraphics gui, Component tooltip, int mouseX, int mouseY, boolean background) {
        Minecraft minecraft = Minecraft.getInstance();

        // window
        int screenX = minecraft.getWindow().getGuiScaledWidth();
        int screenY = minecraft.getWindow().getGuiScaledHeight();

        boolean reduced = Configs.REDUCED_MOTION.value;

        // calculate pos
        int x = reduced ? 0 : mouseX;
        int y = reduced ? screenY : mouseY - 12;

        // prepare text
        Font font = minecraft.font;
        List<FormattedCharSequence> text = TextUtils.wrapTooltip(tooltip, font, x, screenX, 12);
        int height = font.lineHeight * text.size();

        // clamp position to bounds
        x += 12;
        y = Math.min(Math.max(y, 0), screenY - height);
        int width = TextUtils.getWidth(text, font);
        if (x + width > screenX)
            x = Math.max(x - width - 24, 0);

        if (reduced) {
            x += (screenX - width) / 2;
            if (background)
                y -= 4;
        }

        // Draw above screen contents and context menus.
        gui.flush();
        PoseStack pose = gui.pose();
        pose.pushPose();
        try {
            pose.translate(0, 0, 999);
            if (background) {
                blitSliced(gui, x - 4, y - 4, width + 8, height + 8, TOOLTIP);
            }
            for (int i = 0; i < text.size(); i++) {
                FormattedCharSequence charSequence = text.get(i);
                gui.drawString(font, charSequence, x, y + font.lineHeight * i, UIHelper.adjustColor(0xFFFFFF));
            }
            gui.flush();
        } finally {
            pose.popPose();
        }
    }

    public static void renderScrollingText(GuiGraphics gui, Component text, int x, int y, int width, int color) {
        Font font = Minecraft.getInstance().font;
        int textWidth = font.width(text);
        int textX = x;

        color = adjustColor(color);
        // the text fit :D
        if (textWidth <= width) {
            gui.drawString(font, text, textX, y, color);
            return;
        }

        // oh, no it doesn't fit
        textX += getTextScrollingOffset(textWidth, width, false);

        // draw text
        enableTextScissor(gui, x, y, width, font.lineHeight);
        try {
            gui.drawString(font, text, textX, y, color);
        } finally {
            gui.disableScissor();
        }
    }

    public static void renderCenteredScrollingText(GuiGraphics gui, Component text, int x, int y, int width, int height, int color) {
        Font font = Minecraft.getInstance().font;
        int textWidth = font.width(text);
        int textX = x + width / 2;
        int textY = y + height / 2 - font.lineHeight / 2;

        color = adjustColor(color);
        // the text fit :D
        if (textWidth <= width) {
            gui.drawCenteredString(font, text, textX, textY, color);
            return;
        }

        // oh, no it doesn't fit
        textX += getTextScrollingOffset(textWidth, width, true);

        // draw text
        enableTextScissor(gui, x, y, width, height);
        try {
            gui.drawCenteredString(font, text, textX, textY, color);
        } finally {
            gui.disableScissor();
        }
    }

    private static void enableTextScissor(GuiGraphics gui, int x, int y, int width, int height) {
        // Native 1.21.1 scissors use screen coordinates even when text is rotated or scaled.
        Matrix4f pose = gui.pose().last().pose();
        Vector3f minimum = new Vector3f(Float.POSITIVE_INFINITY);
        Vector3f maximum = new Vector3f(Float.NEGATIVE_INFINITY);
        Vector3f[] corners = {
                new Vector3f(x, y, 0), new Vector3f(x + width, y, 0),
                new Vector3f(x, y + height, 0), new Vector3f(x + width, y + height, 0)
        };
        for (Vector3f corner : corners) {
            pose.transformPosition(corner);
            minimum.min(corner);
            maximum.max(corner);
        }
        gui.enableScissor((int) Math.floor(minimum.x), (int) Math.floor(minimum.y),
                (int) Math.ceil(maximum.x), (int) Math.ceil(maximum.y));
    }

    private static int getTextScrollingOffset(int textWidth, int width, boolean centered) {
        float speed = Configs.TEXT_SCROLL_SPEED.tempValue;
        int scrollLen = textWidth - width;
        int startingOffset = (int) Math.ceil(scrollLen / 2d);
        int stopDelay = (int) (Configs.TEXT_SCROLL_DELAY.tempValue * speed);
        int time = scrollLen + stopDelay;
        int totalTime = time * 2;
        int ticks = (int) (FiguraMod.ticks * speed);
        int currentTime = ticks % time;
        int dir = (ticks % totalTime) > time - 1 ? 1 : -1;

        int clamp = Math.min(Math.max(currentTime - stopDelay, 0), scrollLen);
        return (startingOffset - clamp) * dir - (centered ? 0 : startingOffset);
    }

    public static int adjustColor(int argbColor) {
        return (argbColor & -67108864) == 0 ? (argbColor | 0xff000000) : argbColor;
    }

    public static Runnable openURL(String url) {
        Minecraft minecraft = Minecraft.getInstance();
        return () -> minecraft.setScreen(new FiguraConfirmScreen.FiguraConfirmLinkScreen((bl) -> {
            if (bl) Util.getPlatform().openUri(url);
        }, url, minecraft.screen));
    }

    public static void renderLoading(GuiGraphics gui, int x, int y) {
        Component text = Component.literal(Integer.toHexString(Math.abs(FiguraMod.ticks) % 16)).withStyle(Style.EMPTY.withFont(Badges.FONT));
        Font font = Minecraft.getInstance().font;
        gui.drawString(font, text, x - font.width(text) / 2, y - font.lineHeight / 2, UIHelper.adjustColor(-1), false);
    }

    public static void setContext(ContextMenu context) {
        if (Minecraft.getInstance().screen instanceof AbstractPanelScreen panelScreen)
            panelScreen.contextMenu = context;
    }

    public static ContextMenu getContext() {
        if (Minecraft.getInstance().screen instanceof AbstractPanelScreen panelScreen)
            return panelScreen.contextMenu;
        return null;
    }

    public static void setTooltip(Component text) {
        if (Minecraft.getInstance().screen instanceof AbstractPanelScreen panelScreen)
            panelScreen.tooltip = text;
    }

    public static void setTooltip(Style style) {
        if (style == null || style.getHoverEvent() == null)
            return;

        Component text = style.getHoverEvent() != null ? style.getHoverEvent().getValue(HoverEvent.Action.SHOW_TEXT) : null;
        if (text != null)
            setTooltip(text);
    }

    public static void useFiguraLighting() {
        Lighting.setupForFlatItems();
        RenderSystem.setShaderLights(new Vector3f(-0.2f, -1f, 1f).normalize(),
                new Vector3f(-0.2f, 0.4f, 0.3f).normalize());
    }

}
