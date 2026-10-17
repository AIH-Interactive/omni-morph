package top.aihmc.omnimorph.gui.screens;

import net.minecraft.client.gui.screens.Screen;
import net.minecraft.world.entity.LivingEntity;
import top.aihmc.omnimorph.gui.widgets.EntityPreview;
import top.aihmc.omnimorph.model.library.LibraryPreviewSession;
import top.aihmc.omnimorph.utils.FiguraText;

public class AvatarScreen extends AbstractPanelScreen {

    private final float scale;
    private final float pitch;
    private final float yaw;
    private final LivingEntity entity;
    private final LibraryPreviewSession libraryPreview;
    private boolean returningToParent;

    public AvatarScreen(float scale, float pitch, float yaw, LivingEntity entity, Screen parentScreen) {
        this(scale, pitch, yaw, entity, parentScreen, null);
    }

    public AvatarScreen(float scale, float pitch, float yaw, LivingEntity entity, Screen parentScreen,
                        LibraryPreviewSession libraryPreview) {
        super(parentScreen, FiguraText.of("gui.panels.title.avatar"));
        this.scale = scale;
        this.pitch = pitch;
        this.yaw = yaw;
        this.entity = entity;
        this.libraryPreview = libraryPreview;
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
        }
        super.removed();
    }
}
