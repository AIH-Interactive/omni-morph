package org.figuramc.figura.gui.screens;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Renderable;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.figuramc.figura.config.Configs;
import org.figuramc.figura.gui.widgets.ContextMenu;
import org.figuramc.figura.gui.widgets.EntityPreview;
import org.figuramc.figura.gui.widgets.FiguraRemovable;
import org.figuramc.figura.gui.widgets.FiguraTickable;
import top.aihmc.omnimorph.gui.screens.NavigationScreens;
import top.aihmc.omnimorph.gui.widgets.navigation.NavigationLayout;
import top.aihmc.omnimorph.gui.widgets.navigation.NavigationRoute;
import top.aihmc.omnimorph.gui.widgets.navigation.SideNavigationWidget;
import org.figuramc.figura.lua.api.ClientAPI;
import org.figuramc.figura.mixin.gui.ScreenAccessor;
import org.figuramc.figura.utils.FiguraIdentifier;
import org.figuramc.figura.utils.ui.UIHelper;

import java.util.List;

public abstract class AbstractPanelScreen extends Screen {

    public static final List<ResourceLocation> BACKGROUNDS = List.of(
            FiguraIdentifier.of("textures/gui/background/background_0.png"),
            FiguraIdentifier.of("textures/gui/background/background_1.png"),
            FiguraIdentifier.of("textures/gui/background/background_2.png")
    );

    /** Horizontal margin that keeps centred content right of the navigation rail. */
    protected static final int SIDE_MARGIN = NavigationLayout.RAIL_WIDTH + 2;

    // variables
    protected final Screen parentScreen;
    public SideNavigationWidget panels;

    // overlays
    public ContextMenu contextMenu;
    public Component tooltip;

    // stuff :3
    private static final String EGG = "ĉĉĈĈćĆćĆBAā";
    private String egg = EGG;

    protected AbstractPanelScreen(Screen parentScreen, Component title) {
        super(title);
        this.parentScreen = parentScreen;
    }

    public Class<? extends Screen> getSelectedPanel() {
        return this.getClass();
    };

    /** The entry shown as current by the shared navigation; derived from the selected panel by default. */
    protected NavigationRoute navigationRoute() {
        return NavigationScreens.routeOf(getSelectedPanel());
    }

    /**
     * Pages laid out beside the left rail return true. Other pages only show the top bar, since their
     * content still starts at the left edge.
     */
    protected boolean usesSideNavigation() {
        return false;
    }

    /** Opens a navigation destination, inside this page when it owns the destination. */
    public void navigate(NavigationRoute route) {
        if (openNavigationRoute(route)) {
            return;
        }
        this.minecraft.setScreen(NavigationScreens.open(route, parentScreen));
    }

    /** Shows a destination this page owns without replacing the screen; false when it is elsewhere. */
    protected boolean openNavigationRoute(NavigationRoute route) {
        return false;
    }

    @Override
    protected void init() {
        super.init();

        // The navigation is an input listener only: it is drawn after the content, above it.
        panels = new SideNavigationWidget(width, height, navigationRoute(), usesSideNavigation(), this::navigate);
        this.addWidget(panels);

        // clear overlays
        contextMenu = null;
        tooltip = null;
    }

    @Override
    public void tick() {
        for (Renderable renderable : this.renderables()) {
            if (renderable instanceof FiguraTickable tickable)
                tickable.tick();
        }

        renderables().removeIf(r -> r instanceof FiguraRemovable removable && removable.isRemoved());

        super.tick();
    }

    public List<Renderable> renderables() {
        return ((ScreenAccessor) this).getRenderables();
    }

    private void closeLocalPreviews() {
        for (GuiEventListener child : children()) {
            if (child instanceof EntityPreview preview) {
                preview.closeLocalPreview();
            }
        }
    }

    @Override
    protected void clearWidgets() {
        closeLocalPreviews();
        super.clearWidgets();
    }

    @Override
    public void removed() {
        closeLocalPreviews();
        super.removed();
    }

    @Override
    public void render(GuiGraphics gui, int mouseX, int mouseY, float delta) {
        // setup figura framebuffer
        // UIHelper.useFiguraGuiFramebuffer();

        // render background
        this.renderBackground(gui, delta);

        // render contents; the expanded navigation covers them, so they see no pointer beneath it
        boolean navigationAttached = navigationAttached();
        boolean covered = navigationAttached && panels.capturesPointer(mouseX, mouseY);
        super.render(gui, covered ? -1 : mouseX, covered ? -1 : mouseY, delta);

        if (navigationAttached) {
            gui.flush();
            PoseStack pose = gui.pose();
            pose.pushPose();
            try {
                // Above body text and previews (250), below context menus (500).
                pose.translate(0, 0, 400);
                panels.render(gui, mouseX, mouseY, delta);
                gui.flush();
            } finally {
                pose.popPose();
            }
        }

        // render overlays
        this.renderOverlays(gui, mouseX, mouseY, delta);

        // restore vanilla framebuffer
        // UIHelper.useVanillaFramebuffer();
    }

    /** The navigation is active unless the page removed it or draws it as a plain renderable. */
    private boolean navigationAttached() {
        return panels != null && children().contains(panels) && !renderables().contains(panels);
    }

    public void renderBackground(GuiGraphics gui, float delta) {
        // render
        float speed = Configs.BACKGROUND_SCROLL_SPEED.tempValue * 0.125f;
        for (ResourceLocation background : BACKGROUNDS) {
            UIHelper.renderAnimatedBackground(gui, background, 0, 0, this.width, this.height, 64, 64, speed, delta);
            speed /= 0.5;
        }
        gui.flush();
    }

    public void renderOverlays(GuiGraphics gui, int mouseX, int mouseY, float delta) {
        // fps
        if (Configs.GUI_FPS.value)
            gui.drawString(Minecraft.getInstance().font, ClientAPI.getFPS() + " fps", 1, 1, UIHelper.adjustColor(0xFFFFFF));

        // render context
        if (contextMenu != null && contextMenu.isVisible()) {
            // translate the stack here because of nested contexts
            gui.flush();
            PoseStack pose = gui.pose();
            pose.pushPose();
            try {
                pose.translate(0, 0, 500);
                contextMenu.render(gui, mouseX, mouseY, delta);
                gui.flush();
            } finally {
                pose.popPose();
            }
        }

        // render tooltip
        if (tooltip != null)
            UIHelper.renderTooltip(gui, tooltip, mouseX, mouseY, true);

        tooltip = null;
    }

    @Override
    public void onClose() {
        this.minecraft.setScreen(parentScreen);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        // context menu first
        if (this.contextMenuClick(mouseX, mouseY, button))
            return true;

        // The rail and the expanded tree take every click above the content.
        if (navigationAttached() && panels.capturesPointer(mouseX, mouseY)) {
            panels.mouseClicked(mouseX, mouseY, button);
            setFocused(null);
            return true;
        }

        GuiEventListener widget = null;

        // update children focused
        for (GuiEventListener children : List.copyOf(this.children())) {
            boolean clicked = children.mouseClicked(mouseX, mouseY, button);
            children.setFocused(clicked);
            if (clicked) widget = children;
        }

        // set this focused
        if (getFocused() != widget)
            setFocused(widget);

        if (widget != null) {
            if (button == 0) this.setDragging(true);
            return true;
        }

        return false;
    }

    public boolean contextMenuClick(double mouseX, double mouseY, int button) {
        ContextMenu clickedMenu = contextMenu;
        // attempt to run context first
        if (clickedMenu != null && clickedMenu.isVisible()) {
            // attempt to click on the context menu
            boolean clicked = clickedMenu.mouseClicked(mouseX, mouseY, button);

            // then try to click on the category container and suppress it
            // let the category handle the context menu visibility
            if (!clicked && clickedMenu.parent != null && clickedMenu.parent.mouseClicked(mouseX, mouseY, button))
                return true;

            // otherwise, remove visibility and suppress the click only if we clicked on the context
            clickedMenu.setVisible(false);
            return clicked;
        }

        // no interaction was made
        return false;
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double d, double e) {
        // yeet mouse 0 and isDragging check
        return this.getFocused() != null && this.getFocused().mouseDragged(mouseX, mouseY, button, d, e);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        // better check for mouse released when outside element boundaries
        boolean bool = this.getFocused() != null && this.getFocused().mouseReleased(mouseX, mouseY, button);

        // remove focused when clicking
        if (bool) setFocused(null);

        this.setDragging(false);
        return bool;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double amount, double d) {
        // hide previous context
        if (contextMenu != null)
            contextMenu.setVisible(false);

        if (navigationAttached() && panels.capturesPointer(mouseX, mouseY))
            return panels.mouseScrolled(mouseX, mouseY, amount, d);

        // fix scrolling targeting only one child
        boolean ret = false;
        for (GuiEventListener child : this.children()) {
            if (child.isMouseOver(mouseX, mouseY))
                ret = ret || child.mouseScrolled(mouseX, mouseY, amount, d);
        }
        return ret;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        egg += (char) keyCode;
        egg = egg.substring(1);
        if (EGG.equals(egg)) {
            Minecraft.getInstance().setScreen(new GameScreen(this));
            return true;
        }

        if (children().contains(panels) && panels.cycleTab(keyCode))
            return true;

        if (keyCode == 256 && contextMenu != null && contextMenu.isVisible()) {
            contextMenu.setVisible(false);
            return true;
        }

        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    // No blur in our screens!
    @Override
    protected void renderBlurredBackground(float delta) {

    }
}
