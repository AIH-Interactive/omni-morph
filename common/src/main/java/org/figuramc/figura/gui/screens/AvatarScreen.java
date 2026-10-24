package org.figuramc.figura.gui.screens;

import net.minecraft.client.gui.screens.Screen;
import net.minecraft.world.entity.LivingEntity;
import org.figuramc.figura.gui.widgets.EntityPreview;
import top.aihmc.omnimorph.model.library.LibraryPreviewSession;
import org.figuramc.figura.utils.FiguraText;

import top.aihmc.omnimorph.gui.screens.CosmeticAnimationScreen;
import top.aihmc.omnimorph.gui.screens.CosmeticControlsScreen;
import top.aihmc.omnimorph.gui.screens.OutfitPresetsScreen;
public class AvatarScreen extends AbstractPanelScreen {

    private final float scale;
    private final float pitch;
    private final float yaw;
    private final LivingEntity entity;
    private final LibraryPreviewSession libraryPreview;
    private final Object selectedBone;
    private boolean returningToParent;

    public AvatarScreen(float scale, float pitch, float yaw, LivingEntity entity, Screen parentScreen) {
        this(scale, pitch, yaw, entity, parentScreen, null);
    }

    public AvatarScreen(float scale, float pitch, float yaw, LivingEntity entity, Screen parentScreen,
                        LibraryPreviewSession libraryPreview) {
        this(scale, pitch, yaw, entity, parentScreen, libraryPreview, null);
    }

    public AvatarScreen(float scale, float pitch, float yaw, LivingEntity entity, Screen parentScreen,
                        LibraryPreviewSession libraryPreview, Object selectedBone) {
        super(parentScreen, FiguraText.of("gui.panels.title.avatar"));
        this.scale = scale;
        this.pitch = pitch;
        this.yaw = yaw;
        this.entity = entity;
        this.libraryPreview = libraryPreview;
        this.selectedBone = selectedBone;
    }

    @Override
    public Class<? extends Screen> getSelectedPanel() {
        return parentScreen.getClass();
    }

    @Override
    protected void init() {
        super.init();
        removeWidget(panels); // no panels :p

        // entity
        EntityPreview widget = new EntityPreview(0, 0, width, height, scale, pitch, yaw, entity, parentScreen);
        widget.setSelectedBone(selectedBone);
        if (libraryPreview != null) {
            widget.setLibraryPreview(libraryPreview);
        }
        widget.setToggled(true);
        addRenderableWidget(widget);
    }

    @Override
    public void tick() {
        if (libraryPreview != null) {
            libraryPreview.tick();
        }
        super.tick();
    }

    @Override
    public void onClose() {
        returningToParent = true;
        if (libraryPreview != null) {
            libraryPreview.setExpanded(false);
        }
        super.onClose();
    }

    @Override
    public void removed() {
        if (libraryPreview != null && !returningToParent) {
            libraryPreview.close();
            if (parentScreen instanceof CosmeticControlsScreen controls) {
                controls.previewAbandoned();
            } else if (parentScreen instanceof CosmeticAnimationScreen animations) {
                animations.previewAbandoned();
            } else if (parentScreen instanceof OutfitPresetsScreen presets) {
                presets.previewAbandoned();
            }
        }
        super.removed();
    }
}
