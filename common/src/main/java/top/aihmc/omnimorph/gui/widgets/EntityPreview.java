package top.aihmc.omnimorph.gui.widgets;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import top.aihmc.omnimorph.FiguraMod;
import top.aihmc.omnimorph.avatar.Avatar;
import top.aihmc.omnimorph.avatar.AvatarManager;
import net.minecraft.nbt.CompoundTag;
import top.aihmc.omnimorph.gui.screens.AvatarScreen;
import top.aihmc.omnimorph.gui.screens.AvatarControlsScreen;
import top.aihmc.omnimorph.gui.screens.WardrobeScreen;
import top.aihmc.omnimorph.model.rendering.EntityRenderMode;
import top.aihmc.omnimorph.model.library.LibraryPreviewSession;
import top.aihmc.omnimorph.utils.FiguraIdentifier;
import top.aihmc.omnimorph.utils.FiguraText;
import top.aihmc.omnimorph.utils.ui.UIHelper;
import org.joml.Matrix3x2fStack;
import org.joml.Vector3f;

public class EntityPreview extends AbstractContainerElement {

    public static final Identifier UNKNOWN = new FiguraIdentifier("textures/gui/unknown_entity.png");
    public static final Identifier OVERLAY = new FiguraIdentifier("textures/gui/entity_overlay.png");

    // properties
    private LivingEntity entity;
    private LibraryPreviewSession libraryPreview;
    private LibraryPreviewSession localPreview;
    private LivingEntity sourceEntity;
    private Avatar sourceAvatar;
    private CompoundTag sourceNbt;
    private final boolean liveEntityPreview;
    private final float pitch, yaw, scale;
    private SwitchButton button;

    // transformation data

    // rot
    private boolean isRotating = false;
    private float anchorX = 0f, anchorY = 0f;
    private float anchorAngleX = 0f, anchorAngleY = 0f;
    private float angleX, angleY;

    // scale
    private float scaledValue = 0f, scaledPrecise = 0f;
    private static final float SCALE_FACTOR = 1.1F;

    // pos
    private boolean isDragging = false;
    private int modelX, modelY;
    private float dragDeltaX, dragDeltaY;
    private float dragAnchorX, dragAnchorY;

    public EntityPreview(int x, int y, int width, int height, float scale, float pitch, float yaw, LivingEntity entity, Screen parentScreen) {
        super(x, y, width, height);

        this.scale = scale;
        this.pitch = pitch;
        this.yaw = yaw;
        this.entity = entity;
        this.sourceEntity = entity;
        // Wardrobe and model controls show the live model, including immediate control edits.
        // AvatarScreen retains the originating parent so expansion uses the same mechanism.
        this.liveEntityPreview = parentScreen instanceof WardrobeScreen
                || parentScreen instanceof AvatarControlsScreen;

        modelX = width / 2;
        modelY = height / 2;
        angleX = pitch;
        angleY = yaw;

        // button
        children.add(button = new SwitchButton(
                x + 4, y + 4, 16, 16,
                0, 0, 16,
                new FiguraIdentifier("textures/gui/expand.png"),
                48, 32,
                FiguraText.of("gui.expand"),
                bx -> {
                    if (button.isToggled()) {
                        if (libraryPreview != null) {
                            libraryPreview.setExpanded(true);
                        }
                        Minecraft.getInstance().setScreen(new AvatarScreen(scale, pitch, yaw,
                                localPreview == null ? this.entity : sourceEntity, parentScreen, libraryPreview));
                    } else {
                        if (libraryPreview != null && Minecraft.getInstance().screen instanceof AvatarScreen avatarScreen) {
                            avatarScreen.onClose();
                        } else {
                            Minecraft.getInstance().setScreen(parentScreen);
                        }
                    }
                }));
    }

    @Override
    public void tick() {
        super.tick();
        if (libraryPreview != null || liveEntityPreview || sourceEntity == null) {
            return;
        }
        Avatar current = AvatarManager.getAvatar(sourceEntity);
        CompoundTag nbt = current == null ? null : current.nbt;
        if (localPreview == null) {
            localPreview = new LibraryPreviewSession();
            sourceAvatar = current;
            sourceNbt = nbt;
            localPreview.selectDraft(nbt == null ? new CompoundTag() : nbt);
        }
        if (current != sourceAvatar || nbt != sourceNbt) {
            sourceAvatar = current;
            sourceNbt = nbt;
            if (nbt == null) {
                localPreview.selectDraft(new CompoundTag());
            } else {
                localPreview.selectDraft(nbt);
            }
        }
        localPreview.tick();
    }

    /** Only widget-owned local copies are retired here; library screens own their sessions. */
    public void closeLocalPreview() {
        if (localPreview != null) {
            localPreview.close();
            localPreview = null;
        }
        sourceAvatar = null;
        sourceNbt = null;
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor gui, int mouseX, int mouseY, float delta) {
        if (!this.isVisible())
            return;

        if (libraryPreview != null) {
            libraryPreview.beforeExtract(delta);
            entity = libraryPreview.entity();
        } else if (liveEntityPreview) {
            entity = sourceEntity;
        } else if (localPreview != null) {
            localPreview.beforeExtract(delta);
            entity = localPreview.entity();
            synchronizeLocalAppearance();
        } else {
            // Wait for the detached copy instead of briefly displaying the live animation.
            entity = null;
        }

        int x = getX();
        int y = getY();
        int width = getWidth();
        int height = getHeight();

        if (!button.isToggled()) {
            // border
            UIHelper.blitSliced(gui, x, y, width, height, UIHelper.OUTLINE_FILL);
            // overlay
            UIHelper.blit(gui, x + 1, y + 1, width - 2, height, OVERLAY);
        }

        // scissors
        gui.enableScissor(x + 1, y + 1, x + width - 1, y + height - 1);

        // render entity
        Matrix3x2fStack pose = gui.pose();
        if (entity != null) {
            pose.pushMatrix();
            scaledValue = Mth.lerp((float) (1f - Math.pow(0.5f, delta)), scaledValue, scaledPrecise);
            boolean independent = libraryPreview != null && libraryPreview.submitIndependentFigura(gui,
                    x + modelX, y + modelY, scale + scaledValue, angleX, angleY, delta,
                    x, y, x + width, y + height);
            if (!independent) {
                Runnable draw = () -> UIHelper.drawEntity(x + modelX, y + modelY, scale + scaledValue,
                        angleX, angleY, entity, gui, new Vector3f(), EntityRenderMode.FIGURA_GUI,
                        x, y, x + width, y + height);
                if (libraryPreview != null) {
                    libraryPreview.withPlayerContext(draw);
                } else if (localPreview != null) {
                    localPreview.withPlayerContext(draw);
                } else {
                    draw.run();
                }
            }
            pose.popMatrix();
        } else {
            // draw
            int s = Math.min(width, height) * 2 / 3;
            UIHelper.enableBlend();
            gui.blit(RenderPipelines.GUI_TEXTURED, UNKNOWN, x + (width - s) / 2, y + (height - s) / 2, 0f, 64 * ((int) (FiguraMod.ticks / 3f) % 8), s, s,64, 64, 64, 512);
        }

        gui.disableScissor();

        super.extractRenderState(gui, mouseX, mouseY, delta);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent mouseButtonEvent, boolean bl) {
        double mouseX = mouseButtonEvent.x();
        double mouseY = mouseButtonEvent.y();
        int button = mouseButtonEvent.button();

        if (!this.isVisible() || !this.isMouseOver(mouseX, mouseY))
            return false;

        if (super.mouseClicked(mouseButtonEvent, bl))
            return true;

        switch (button) {
            // left click - rotate
            case 0 -> {
                // set anchor rotation

                // get starter mouse pos
                anchorX = (float) mouseX;
                anchorY = (float) mouseY;

                // get starter rotation angles
                anchorAngleX = angleX;
                anchorAngleY = angleY;

                isRotating = true;
                return true;
            }

            // right click - move
            case 1 -> {
                // get starter mouse pos
                dragDeltaX = (float) mouseX;
                dragDeltaY = (float) mouseY;

                // also get start node pos
                dragAnchorX = modelX;
                dragAnchorY = modelY;

                isDragging = true;
                return true;
            }

            // middle click - reset pos
            case 2 -> {
                isRotating = false;
                isDragging = false;
                anchorX = 0f;
                anchorY = 0f;
                anchorAngleX = 0f;
                anchorAngleY = 0f;
                angleX = pitch;
                angleY = yaw;
                scaledValue = 0f;
                scaledPrecise = 0f;
                modelX = getWidth() / 2;
                modelY = getHeight() / 2;
                return true;
            }
        }

        return false;
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent mouseButtonEvent) {
        int button = mouseButtonEvent.button();
        // left click - stop rotating
        if (button == 0) {
            isRotating = false;
            return true;
        }

        // right click - stop dragging
        else if (button == 1) {
            isDragging = false;
            return true;
        }

        return super.mouseReleased(mouseButtonEvent);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent mouseButtonEvent, double deltaX, double deltaY) {
        double mouseX = mouseButtonEvent.x();
        double mouseY = mouseButtonEvent.y();
        // left click - rotate
        if (isRotating) {
            // get starter rotation angle then get hot much is moved and divided by a slow factor
            // as of 1.21.6 gui scale is an int, this must be a float or else it may be Nan and that is a big nono
            angleX = (float) (anchorAngleX + (anchorY - mouseY) / (3d / Minecraft.getInstance().getWindow().getGuiScale()));
            angleY = (float) (anchorAngleY - (anchorX - mouseX) / (3d / Minecraft.getInstance().getWindow().getGuiScale()));

            // cap to 360, so we don't get extremely high unnecessary rotation values
            if (angleX >= 360 || angleX <= -360) {
                anchorY = (float) mouseY;
                anchorAngleX = 0;
                angleX = 0;
            }
            if (angleY >= 360 || angleY <= -360) {
                anchorX = (float) mouseX;
                anchorAngleY = 0;
                angleY = 0;
            }

            return true;
        }

        // right click - move
        else if (isDragging) {
            // get how much it should move
            // get actual pos of the mouse, then subtract starter X,Y
            float x = (float) (mouseX - dragDeltaX);
            float y = (float) (mouseY - dragDeltaY);

            // move it
            modelX = (int) (dragAnchorX + x);
            modelY = (int) (dragAnchorY + y);

            return true;
        }

        return super.mouseDragged(mouseButtonEvent, deltaX, deltaY);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double amount, double d) {
        if (!this.isVisible())
            return false;

        if (super.mouseScrolled(mouseX, mouseY, amount, d))
            return true;

        // scroll - scale

        // set scale direction
        float scaleDir = ((amount+d) > 0) ? SCALE_FACTOR : 1 / SCALE_FACTOR;

        // determine scale
        scaledPrecise = ((scale + scaledPrecise) * scaleDir) - scale;

        return true;
    }

    public void setEntity(LivingEntity entity) {
        if (sourceEntity == entity) {
            return;
        }
        closeLocalPreview();
        sourceEntity = entity;
        this.entity = entity;
    }

    private void synchronizeLocalAppearance() {
        Avatar detached = entity == null ? null : AvatarManager.getAvatar(entity);
        if (detached == null || sourceAvatar == null) {
            return;
        }
        for (var control : detached.controls.all()) {
            var source = sourceAvatar.controls.get(control.id());
            if (source != null && !java.util.Objects.equals(control.getValue(), source.getValue())) {
                control.setValue(source.getValue());
            }
        }
        var target = detached.getYsmRuntime();
        var source = sourceAvatar.getYsmRuntime();
        if (target != null && source != null && !target.textureId().equals(source.textureId())) {
            target.setTexture(source.textureId());
        }
    }

    public void setLibraryPreview(LibraryPreviewSession preview) {
        closeLocalPreview();
        libraryPreview = preview;
        entity = preview == null ? null : preview.entity();
    }

    public void setToggled(boolean toggled) {
        this.button.setToggled(toggled);
        this.button.setTooltip(toggled ? FiguraText.of("gui.minimise") : FiguraText.of("gui.expand"));
    }
}
