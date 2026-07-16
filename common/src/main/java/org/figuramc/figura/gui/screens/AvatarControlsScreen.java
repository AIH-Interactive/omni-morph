package org.figuramc.figura.gui.screens;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.InputWithModifiers;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import org.figuramc.figura.FiguraMod;
import org.figuramc.figura.avatar.Avatar;
import org.figuramc.figura.avatar.control.AvatarControlDefinition;
import org.figuramc.figura.avatar.control.AvatarControlType;
import org.figuramc.figura.config.Configs;
import org.figuramc.figura.gui.widgets.Button;
import org.figuramc.figura.gui.widgets.ContextMenu;
import org.figuramc.figura.gui.widgets.EntityPreview;
import org.figuramc.figura.gui.widgets.SliderWidget;
import org.figuramc.figura.gui.widgets.SwitchButton;
import org.figuramc.figura.gui.widgets.TextField;
import org.figuramc.figura.lua.api.ysm_actions.YsmActionsAPI;
import org.figuramc.figura.model.ysm.YsmModelRuntime;
import org.figuramc.figura.model.ysm.action.YsmActionDefinition;
import org.figuramc.figura.model.ysm.action.YsmActionWheelLayoutStore;
import org.figuramc.figura.utils.LuaUtils;
import org.figuramc.figura.utils.ui.UIHelper;
import org.joml.Matrix3x2fStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class AvatarControlsScreen extends Screen {
    private static final int ROW_HEIGHT = 26;
    private static final int WHEEL_ROW_HEIGHT = 42;
    private static final int HEADER_HEIGHT = 52;
    private static final int TAB_HEIGHT = 28;
    private static final int FOOTER_HEIGHT = 42;
    private static final String YSM_WHEEL_PAGE = "__ysm_wheel";

    private final Screen parentScreen;
    private final Avatar avatar;
    private String page;
    private final List<AvatarControlDefinition> controls = new ArrayList<>();
    private final List<String> pages = new ArrayList<>();
    private final List<ControlSlider> sliders = new ArrayList<>();
    private AvatarControlDefinition focusedKeybind;
    private EntityPreview preview;
    private ContextMenu openDropdown;
    private int scroll;
    private String wheelEditorPage = "extra_animation";
    private String wheelPageNameDraft = "";
    private String selectedWheelAction;
    private String focusedWheelKeybindAction;
    private boolean focusedWheelKeybindToggle;
    private final Map<String, ItemStack> wheelItemCache = new HashMap<>();

    public AvatarControlsScreen(Screen parentScreen, Avatar avatar, String page) {
        super(Component.literal("Avatar Controls"));
        this.parentScreen = parentScreen;
        this.avatar = avatar;
        this.page = page == null || page.isBlank() ? "root" : page;
    }

    public static void open(Avatar avatar, String page) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null || avatar == null)
            return;
        minecraft.setScreen(new AvatarControlsScreen(minecraft.screen, avatar, page));
    }

    @Override
    protected void init() {
        super.init();
        rebuild();
    }

    private void rebuild() {
        this.clearWidgets();
        sliders.clear();
        controls.clear();
        pages.clear();
        openDropdown = null;
        collectPages();
        if (!pages.contains(page))
            page = pages.isEmpty() ? "root" : pages.get(0);

        addPageTabs();
        if (isWheelPage()) {
            addWheelEditorWidgets();
        } else {
            for (AvatarControlDefinition control : avatar.controls.all()) {
                if (page.equals(control.page()) && control.targetPage() == null)
                    controls.add(control);
            }
        }

        int controlsWidth = controlsWidth();
        int controlsX = controlsX();
        int y = contentTop() - scroll;
        for (AvatarControlDefinition control : controls) {
            int widgetX = controlsX + controlsWidth - 238;
            int widgetWidth = 180;
            addControlWidget(widgetX, y + 3, widgetWidth, control);
            if (canReset(control))
                this.addRenderableWidget(new Button(widgetX + widgetWidth + 8, y + 3, 42, 20, Component.literal("Reset"), null, button -> {
                    avatar.controls.reset(avatar, control.id());
                    rebuild();
                }));
            y += rowHeight(control);
        }

        if (previewWidth() > 0) {
            preview = new EntityPreview(previewX(), previewY(), previewWidth(), previewHeight(), previewScale(), 0f, 180f, minecraft == null ? null : minecraft.player, this);
            this.addRenderableWidget(preview);
        }

        int footerWidth = panelWidth();
        Button resetAll = new Button(panelX(), height - 32, footerWidth / 2 - 4, 20, Component.literal(isWheelPage() ? "Refresh Wheel" : "Reset All"), null, button -> {
            if (isWheelPage()) {
                refreshYsmWheel();
                return;
            }
            avatar.controls.resetAll(avatar);
            rebuild();
        });
        resetAll.setActive(isWheelPage() ? avatar.getYsmRuntime() != null : hasResettableControls());
        this.addRenderableWidget(resetAll);
        this.addRenderableWidget(new Button(panelX() + footerWidth / 2 + 4, height - 32, footerWidth / 2 - 4, 20, Component.translatable("gui.done"), null, button -> onClose()));
    }

    @Override
    public void onClose() {
        minecraft.setScreen(parentScreen);
    }

    private void addControlWidget(int x, int y, int width, AvatarControlDefinition control) {
        AvatarControlType type = control.type();
        switch (type) {
            case TOGGLE -> addToggle(x, y, width, control);
            case SLIDER -> addSlider(x, y + 5, width, control);
            case ENUM -> addEnum(x, y, width, control);
            case COLOR -> addTextInput(x, y, width, control, TextField.HintType.HEX_COLOR, true);
            case TEXT -> addTextInput(x, y, width, control, TextField.HintType.ANY, false);
            case NUMBER -> addTextInput(x, y, width, control, TextField.HintType.FLOAT, false);
            case KEYBIND -> addKeybind(x, y, width, control);
            case BUTTON -> this.addRenderableWidget(new Button(x, y, width, 20, Component.literal(control.targetPage() == null ? "Run" : "Open"), null, button -> {
                if (control.targetPage() != null) {
                    page = control.targetPage();
                    scroll = 0;
                    rebuild();
                } else {
                    avatar.controls.press(avatar, control.id());
                }
            }));
            default -> {
            }
        }
    }

    private void addToggle(int x, int y, int width, AvatarControlDefinition control) {
        this.addRenderableWidget(new ControlSwitch(x + Math.max(0, width - 92), y, 92, 20, control));
    }

    private void addSlider(int x, int y, int width, AvatarControlDefinition control) {
        ControlSlider slider = new ControlSlider(x, y, width, 20, control);
        sliders.add(slider);
        this.addRenderableWidget(slider);
    }

    private void addEnum(int x, int y, int width, AvatarControlDefinition control) {
        this.addRenderableWidget(new ControlDropdown(x, y, width, control));
    }

    private void addTextInput(int x, int y, int width, AvatarControlDefinition control, TextField.HintType hint, boolean colorPreview) {
        TextField field = new TextField(x, y, width, 20, hint, text -> {
            if (control.type() == AvatarControlType.NUMBER) {
                try {
                    avatar.controls.setValue(avatar, control.id(), Double.parseDouble(text));
                } catch (NumberFormatException ignored) {
                }
            } else {
                avatar.controls.setValue(avatar, control.id(), text);
            }
        });
        field.getField().setValue(valueText(control));
        if (colorPreview)
            field.setBorderColour(parseColor(valueText(control), 0xFFFFFFFF));
        this.addRenderableWidget(field);
    }

    private void addKeybind(int x, int y, int width, AvatarControlDefinition control) {
        this.addRenderableWidget(new Button(x, y, width, 20, Component.literal(keybindText(control)), null, button -> {
            focusedKeybind = control;
            button.setMessage(Component.literal("> press key <"));
        }));
    }

    private boolean canReset(AvatarControlDefinition control) {
        return switch (control.type()) {
            case LABEL, SEPARATOR, BUTTON -> false;
            default -> control.hasStoredValue();
        };
    }

    private boolean hasResettableControls() {
        for (AvatarControlDefinition control : avatar.controls.all()) {
            if (canReset(control))
                return true;
        }
        return false;
    }

    @Override
    public boolean keyPressed(KeyEvent keyEvent) {
        if (focusedWheelKeybindAction != null) {
            bindFocusedWheelKeybind(keyEvent.key() == 256 ? InputConstants.UNKNOWN : InputConstants.getKey(keyEvent));
            return true;
        }
        if (focusedKeybind != null) {
            if (keyEvent.key() == 256) {
                focusedKeybind = null;
                rebuild();
                return true;
            }
            avatar.controls.setValue(avatar, focusedKeybind.id(), "key." + keyEvent.key());
            focusedKeybind = null;
            rebuild();
            return true;
        }
        return super.keyPressed(keyEvent);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (openDropdown != null && openDropdown.isVisible()) {
            if (openDropdown.mouseClicked(event, doubleClick))
                return true;
            openDropdown.setVisible(false);
            openDropdown = null;
        }
        if (focusedKeybind != null && event.button() != 0) {
            avatar.controls.setValue(avatar, focusedKeybind.id(), "mouse." + event.button());
            focusedKeybind = null;
            rebuild();
            return true;
        }
        if (focusedWheelKeybindAction != null && event.button() != 0) {
            bindFocusedWheelKeybind(InputConstants.Type.MOUSE.getOrCreate(event.button()));
            return true;
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double amount, double horizontalAmount) {
        if (super.mouseScrolled(mouseX, mouseY, amount, horizontalAmount))
            return true;
        int max = maxScroll();
        int next = Mth.clamp(scroll + (int) Math.signum(-amount - horizontalAmount) * ROW_HEIGHT, 0, max);
        if (next == scroll)
            return false;
        scroll = next;
        rebuild();
        return true;
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor gui, int mouseX, int mouseY, float delta) {
        renderFiguraBackground(gui, delta);
        renderPanel(gui);
        super.extractRenderState(gui, mouseX, mouseY, delta);
        syncSliders();
        if (preview != null && minecraft != null)
            preview.setEntity(minecraft.player);

        int controlsX = controlsX();
        int controlsWidth = controlsWidth();
        UIHelper.renderOutlineText(gui, minecraft.font, Component.literal("Avatar Controls: " + page), controlsX + 10, 28, 0xFFFFFF, 0);
        if (previewWidth() > 0)
            UIHelper.renderOutlineText(gui, minecraft.font, Component.literal("Preview"), previewX() + 8, 28, 0xFFD0D0D0, 0);
        if (isWheelPage()) {
            renderWheelEditor(gui, controlsX, controlsWidth);
            renderDropdown(gui, mouseX, mouseY, delta);
            return;
        }
        if (controls.isEmpty()) {
            UIHelper.renderOutlineText(gui, minecraft.font, Component.literal("No controls on this page"), controlsX + 10, contentTop() + 8, 0xAAAAAA, 0);
            return;
        }

        int y = contentTop() - scroll;
        for (AvatarControlDefinition control : controls) {
            if (y > contentTop() - ROW_HEIGHT && y < height - FOOTER_HEIGHT) {
                renderControlLabel(gui, control, controlsX + 10, y);
                if (control.type() == AvatarControlType.COLOR)
                    renderColorPreview(gui, controlsX + controlsWidth - 214, y + 5, valueText(control));
            }
            y += rowHeight(control);
        }
        renderDropdown(gui, mouseX, mouseY, delta);
    }

    private void renderDropdown(GuiGraphicsExtractor gui, int mouseX, int mouseY, float delta) {
        if (openDropdown == null || !openDropdown.isVisible())
            return;
        gui.nextStratum();
        openDropdown.extractRenderState(gui, mouseX, mouseY, delta);
    }

    private void renderControlLabel(GuiGraphicsExtractor gui, AvatarControlDefinition control, int x, int y) {
        if (control.type() == AvatarControlType.SEPARATOR) {
            gui.fill(x, y + 12, x + controlsWidth() - 20, y + 13, 0x66FFFFFF);
            return;
        }
        int titleColor = control.type() == AvatarControlType.LABEL ? 0xFFD0D0D0 : 0xFFFFFFFF;
        UIHelper.renderOutlineText(gui, minecraft.font, Component.literal(control.getTitle()), x, y + 7, titleColor, 0);
        Component meta = Component.literal(control.getType());
        gui.text(minecraft.font, meta, x + Math.min(190, controlsWidth() / 2), y + 7, 0xFF888888);
    }

    private void renderColorPreview(GuiGraphicsExtractor gui, int x, int y, String value) {
        int color = parseColor(value, 0xFF000000);
        UIHelper.blitSliced(gui, x, y, 20, 20, UIHelper.OUTLINE);
        gui.fill(x + 2, y + 2, x + 18, y + 18, color);
    }

    private void renderPanel(GuiGraphicsExtractor gui) {
        int x = panelX();
        int y = 20;
        int w = panelWidth();
        int h = height - 48;
        UIHelper.blitSliced(gui, x, y, w, h, UIHelper.OUTLINE_FILL);
        if (previewWidth() > 0)
            gui.fill(previewX() - 8, y + 1, previewX() - 7, y + h - 1, 0x66777777);
    }

    private void syncSliders() {
        for (ControlSlider slider : sliders)
            slider.syncValue();
    }

    private int panelWidth() {
        return Math.min(960, width - 40);
    }

    private int panelX() {
        return width / 2 - panelWidth() / 2;
    }

    private int controlsX() {
        return panelX();
    }

    private int controlsWidth() {
        int preview = previewWidth();
        return preview <= 0 ? panelWidth() : Math.max(360, panelWidth() - preview - 16);
    }

    private int previewWidth() {
        if (width < 620)
            return 0;
        return Math.max(180, Math.min(280, panelWidth() / 3));
    }

    private int previewX() {
        return panelX() + controlsWidth() + 16;
    }

    private int previewY() {
        return contentTop();
    }

    private int previewHeight() {
        return Math.max(120, height - contentTop() - FOOTER_HEIGHT);
    }

    private float previewScale() {
        return Math.max(28f, Math.min(54f, previewHeight() / 4.2f));
    }

    private int rowHeight(AvatarControlDefinition control) {
        return control.type() == AvatarControlType.SEPARATOR ? 18 : ROW_HEIGHT;
    }

    private int maxScroll() {
        if (isWheelPage())
            return Math.max(0, wheelEditorListTop() - contentTop() + wheelActions().size() * WHEEL_ROW_HEIGHT - Math.max(0, height - contentTop() - FOOTER_HEIGHT));
        int content = 0;
        for (AvatarControlDefinition control : controls)
            content += rowHeight(control);
        return Math.max(0, content - Math.max(0, height - contentTop() - FOOTER_HEIGHT));
    }

    private int contentTop() {
        return HEADER_HEIGHT + (pages.size() > 1 ? TAB_HEIGHT : 0);
    }

    private void collectPages() {
        addPage("root");
        for (AvatarControlDefinition control : avatar.controls.all())
            addPage(control.page());
        if (avatar.getYsmRuntime() != null && !wheelActions().isEmpty())
            addPage(YSM_WHEEL_PAGE);
    }

    private void addPage(String page) {
        if (page != null && !page.isBlank() && !pages.contains(page))
            pages.add(page);
    }

    private void addPageTabs() {
        if (pages.size() <= 1)
            return;
        int x = controlsX() + 8;
        int y = HEADER_HEIGHT;
        int available = controlsWidth() - 16;
        int tabWidth = Math.max(72, Math.min(132, available / pages.size()));
        for (String target : pages) {
            if (x + tabWidth > controlsX() + controlsWidth() - 8)
                break;
            Button tab = new Button(x, y, tabWidth - 4, 20, Component.literal(pageTitle(target)), null, button -> {
                page = target;
                scroll = 0;
                rebuild();
            });
            tab.setActive(!target.equals(page));
            this.addRenderableWidget(tab);
            x += tabWidth;
        }
    }

    private String pageTitle(String id) {
        if (YSM_WHEEL_PAGE.equals(id))
            return "Wheel";
        if ("root".equals(id))
            return "Settings";
        for (AvatarControlDefinition control : avatar.controls.all()) {
            if (id.equals(control.targetPage()))
                return control.getTitle();
            if (id.equals(control.page()) && control.type() == AvatarControlType.LABEL)
                return control.getTitle();
        }
        return id;
    }

    private boolean isWheelPage() {
        return YSM_WHEEL_PAGE.equals(page);
    }

    private List<YsmActionDefinition> wheelActions() {
        YsmModelRuntime runtime = avatar.getYsmRuntime();
        if (runtime == null)
            return List.of();
        List<YsmActionDefinition> result = new ArrayList<>();
        for (YsmActionDefinition action : runtime.actions().all()) {
            if (isWheelAction(action))
                result.add(action);
        }
        return result;
    }

    private void addWheelEditorWidgets() {
        YsmModelRuntime runtime = avatar.getYsmRuntime();
        if (runtime == null)
            return;
        int controlsX = controlsX();
        int controlsWidth = controlsWidth();
        normalizeWheelEditorPage();
        int overviewY = contentTop() + 8;
        int innerX = controlsX + 10;
        int innerWidth = controlsWidth - 20;
        int x = innerX;
        this.addRenderableWidget(new Button(x, overviewY, 28, 20, Component.literal("<"), null, ignored -> cycleWheelEditorPage(-1)));
        x += 32;
        int pageCycleWidth = Math.min(180, Math.max(112, innerWidth / 5));
        this.addRenderableWidget(new Button(x, overviewY, pageCycleWidth, 20, Component.literal(shortPageName(wheelEditorPage)), null, ignored -> cycleWheelEditorPage(1)));
        x += pageCycleWidth + 4;
        this.addRenderableWidget(new Button(x, overviewY, 28, 20, Component.literal(">"), null, ignored -> cycleWheelEditorPage(1)));
        x += 38;
        this.addRenderableWidget(new Button(x, overviewY, 68, 20, Component.literal("Auto Fill"), null, ignored -> autoFillWheelEditorPage(runtime)));
        x += 72;
        this.addRenderableWidget(new Button(x, overviewY, 66, 20, Component.literal("Compact"), null, ignored -> compactWheelEditorPage(runtime)));
        x += 70;
        this.addRenderableWidget(new Button(x, overviewY, 82, 20, Component.literal("Clear Page"), null, ignored -> clearWheelEditorPage(runtime)));
        x += 86;
        this.addRenderableWidget(new Button(x, overviewY, 70, 20, Component.literal("Stop All"), null, ignored -> {
            runtime.actions().stopAll();
            rebuild();
        }));
        int editY = overviewY + 26;
        int editX = innerX;
        int pageNameWidth = Math.max(120, Math.min(240, innerWidth - 180));
        TextField pageName = new TextField(editX, editY, pageNameWidth, 20, TextField.HintType.ANY, value -> wheelPageNameDraft = value);
        pageName.getField().setValue(wheelPageNameDraft.isBlank() ? wheelEditorPage : wheelPageNameDraft);
        this.addRenderableWidget(pageName);
        editX += pageNameWidth + 8;
        this.addRenderableWidget(new Button(editX, editY, 46, 20, Component.literal("Add"), null, ignored -> addWheelEditorPage(runtime)));
        editX += 50;
        Button renamePage = new Button(editX, editY, 64, 20, Component.literal("Rename"), null, ignored -> renameWheelEditorPage(runtime));
        renamePage.setActive(!"extra_animation".equals(wheelEditorPage));
        this.addRenderableWidget(renamePage);
        editX += 68;
        Button deletePage = new Button(editX, editY, 58, 20, Component.literal("Delete"), null, ignored -> deleteWheelEditorPage(runtime));
        deletePage.setActive(!"extra_animation".equals(wheelEditorPage));
        this.addRenderableWidget(deletePage);
        int overviewSlotY = overviewY + 58;
        int overviewSlotWidth = Math.max(36, Math.min(88, innerWidth / 8));
        int overviewSlotX = innerX;
        for (int slot = 1; slot <= 8; slot++) {
            int targetSlot = slot;
            YsmActionDefinition owner = wheelSlotOwner(runtime, wheelEditorPage, slot, null);
            String label = owner == null || itemForAction(owner).isEmpty() ? owner == null ? Integer.toString(slot) : shortActionName(owner.getTitle()) : "";
            Button button = new Button(overviewSlotX + (slot - 1) * overviewSlotWidth, overviewSlotY, overviewSlotWidth - 3, 20, Component.literal(label), null, ignored -> {
                if (owner != null) {
                    selectedWheelAction = owner.getId();
                    runtime.actions().trigger(owner.getId());
                    rebuild();
                }
            });
            button.setActive(owner != null);
            this.addRenderableWidget(button);
            int actionX = overviewSlotX + (slot - 1) * overviewSlotWidth;
            int actionWidth = Math.max(14, (overviewSlotWidth - 5) / 3);
            Button moveLeft = new Button(actionX, overviewSlotY + 23, actionWidth, 16, Component.literal("<"), null, ignored -> moveWheelEditorSlot(runtime, targetSlot, -1));
            moveLeft.setActive(owner != null);
            this.addRenderableWidget(moveLeft);
            Button clearSlot = new Button(actionX + actionWidth + 1, overviewSlotY + 23, actionWidth, 16, Component.literal("x"), null, ignored -> clearWheelEditorSlot(runtime, targetSlot));
            clearSlot.setActive(owner != null);
            this.addRenderableWidget(clearSlot);
            Button moveRight = new Button(actionX + (actionWidth + 1) * 2, overviewSlotY + 23, actionWidth, 16, Component.literal(">"), null, ignored -> moveWheelEditorSlot(runtime, targetSlot, 1));
            moveRight.setActive(owner != null);
            this.addRenderableWidget(moveRight);
        }

        YsmActionDefinition selectedAction = selectedWheelAction(runtime);
        int selectedY = overviewY + 104;
        Button bindButton = new Button(controlsX + 10, selectedY, 52, 20, Component.literal(focusedWheelKeybindAction != null && !focusedWheelKeybindToggle ? "Press" : "Bind"), null, ignored -> beginWheelKeybind(selectedAction, false));
        bindButton.setActive(selectedAction != null);
        this.addRenderableWidget(bindButton);
        Button toggleBindButton = new Button(controlsX + 66, selectedY, 76, 20, Component.literal(focusedWheelKeybindAction != null && focusedWheelKeybindToggle ? "Press" : "Toggle Bind"), null, ignored -> beginWheelKeybind(selectedAction, true));
        toggleBindButton.setActive(selectedAction != null);
        this.addRenderableWidget(toggleBindButton);
        Button unbindButton = new Button(controlsX + 146, selectedY, 58, 20, Component.literal("Unbind"), null, ignored -> unbindWheelActionKeybinds(selectedAction));
        unbindButton.setActive(selectedAction != null);
        this.addRenderableWidget(unbindButton);

        int rightX = controlsX + controlsWidth - 10;
        int buttonWidth = Math.max(20, Math.min(30, (controlsWidth - 420) / 10));
        int clearWidth = 58;
        int slotX = rightX - clearWidth - 6 - buttonWidth * 8;
        int pageButtonWidth = Math.max(86, Math.min(116, controlsWidth / 10));
        int pageButtonX = slotX - pageButtonWidth - 8;
        int runButtonX = pageButtonX - 46;
        int stopButtonX = runButtonX - 46;
        int putButtonX = stopButtonX - 50;
        int selectButtonX = controlsX + 64;
        int y = wheelEditorListTop() - scroll;
        for (YsmActionDefinition action : wheelActions()) {
            if (y > contentTop() - WHEEL_ROW_HEIGHT && y < height - FOOTER_HEIGHT) {
                int buttonY = y + 6;
                Button putButton = new Button(putButtonX, buttonY, 42, 20, Component.literal("Put"), null, ignored -> assignToWheelEditorPage(runtime, action));
                putButton.setActive(nextAvailableWheelSlot(runtime, wheelEditorPage, currentWheelSlot(runtime, action), action) > 0);
                this.addRenderableWidget(putButton);
                this.addRenderableWidget(new Button(selectButtonX, buttonY, 56, 20, Component.literal("Select"), null, ignored -> {
                    selectedWheelAction = action.getId();
                    rebuild();
                }));
                this.addRenderableWidget(new Button(runButtonX, buttonY, 38, 20, Component.literal("Run"), null, ignored -> {
                    selectedWheelAction = action.getId();
                    runtime.actions().trigger(action.getId());
                    rebuild();
                }));
                Button stopButton = new Button(stopButtonX, buttonY, 38, 20, Component.literal("Stop"), null, ignored -> {
                    selectedWheelAction = action.getId();
                    runtime.actions().stop(action.getId());
                    rebuild();
                });
                stopButton.setActive(runtime.actions().isActive(action.getId()));
                this.addRenderableWidget(stopButton);
                this.addRenderableWidget(new Button(pageButtonX, buttonY, pageButtonWidth, 20, Component.literal(shortPageName(currentWheelPage(runtime, action))), null, ignored -> {
                    cycleWheelPage(runtime, action);
                }));
                for (int slot = 1; slot <= 8; slot++) {
                    final int targetSlot = slot;
                    String currentPage = currentWheelPage(runtime, action);
                    int currentSlot = currentWheelSlot(runtime, action);
                    YsmActionDefinition owner = wheelSlotOwner(runtime, currentPage, slot, action);
                    boolean selected = currentSlot == slot;
                    Component label = Component.literal(selected ? "*" : owner == null ? Integer.toString(slot) : "!");
                    Button button = new Button(slotX + (slot - 1) * buttonWidth, buttonY, buttonWidth - 2, 20, label, null, ignored -> {
                        setWheelSlot(runtime, action, targetSlot);
                    });
                    button.setActive(!selected && owner == null);
                    this.addRenderableWidget(button);
                }
                this.addRenderableWidget(new Button(slotX + buttonWidth * 8 + 6, buttonY, clearWidth, 20, Component.literal("Clear"), null, ignored -> {
                    YsmActionWheelLayoutStore.set(runtime.getModelKey(), action.getId(), null, -1);
                    refreshYsmWheel();
                    rebuild();
                }));
            }
            y += WHEEL_ROW_HEIGHT;
        }
    }

    private void renderWheelEditor(GuiGraphicsExtractor gui, int controlsX, int controlsWidth) {
        YsmModelRuntime runtime = avatar.getYsmRuntime();
        if (runtime == null) {
            UIHelper.renderOutlineText(gui, minecraft.font, Component.literal("No YSM model loaded"), controlsX + 10, contentTop() + 8, 0xAAAAAA, 0);
            return;
        }
        List<YsmActionDefinition> actions = wheelActions();
        if (actions.isEmpty()) {
            UIHelper.renderOutlineText(gui, minecraft.font, Component.literal("No YSM wheel actions"), controlsX + 10, contentTop() + 8, 0xAAAAAA, 0);
            return;
        }
        normalizeWheelEditorPage();
        int innerX = controlsX + 10;
        int innerWidth = controlsWidth - 20;
        YsmActionDefinition selectedAction = selectedWheelAction(runtime);
        String selectedText = selectedAction == null ? "Selected: none" : "Selected: " + selectedAction.getTitle() + "  " + wheelActionKeybindLabel(selectedAction);
        UIHelper.renderOutlineText(gui, minecraft.font, Component.literal(selectedText), innerX + 204, contentTop() + 116, selectedAction == null ? 0xFF777777 : 0xFF66AAFF, 0);
        if (previewWidth() > 0 && selectedAction != null) {
            String previewText = runtime.actions().isActive(selectedAction.getId()) ? "Active " + numberText(runtime.actions().time(selectedAction.getId())) + "s" : "Idle";
            UIHelper.renderOutlineText(gui, minecraft.font, Component.literal(selectedAction.getTitle() + " / " + previewText), previewX() + 8, previewY() + previewHeight() - 18, 0xFFAAAAAA, 0);
        }
        int overviewY = contentTop() + 66;
        int overviewSlotWidth = Math.max(36, Math.min(88, innerWidth / 8));
        for (int slot = 1; slot <= 8; slot++) {
            YsmActionDefinition owner = wheelSlotOwner(runtime, wheelEditorPage, slot, null);
            int x = innerX + (slot - 1) * overviewSlotWidth;
            int color = owner == null ? 0xFF555555 : 0xFF66AAFF;
            UIHelper.renderOutlineText(gui, minecraft.font, Component.literal(Integer.toString(slot)), x + 4, overviewY + 3, color, 0);
            if (owner != null)
                renderActionItem(gui, owner, x + overviewSlotWidth / 2 - 8, overviewY - 23);
        }

        int y = wheelEditorListTop() - scroll;
        int rightX = controlsX + controlsWidth - 10;
        int buttonWidth = Math.max(20, Math.min(30, (controlsWidth - 420) / 10));
        int clearWidth = 58;
        int slotX = rightX - clearWidth - 6 - buttonWidth * 8;
        int pageButtonWidth = Math.max(86, Math.min(116, controlsWidth / 10));
        int putButtonX = slotX - pageButtonWidth - 8 - 46 - 46 - 50;
        int titleX = controlsX + 126;
        int metaRight = Math.max(titleX + 80, putButtonX - 10);
        for (YsmActionDefinition action : actions) {
            if (y > contentTop() - WHEEL_ROW_HEIGHT && y < height - FOOTER_HEIGHT) {
                renderActionItem(gui, action, controlsX + 10, y + 10);
                int titleColor = action.getId().equals(selectedWheelAction) ? 0xFF66AAFF : 0xFFFFFFFF;
                UIHelper.renderOutlineText(gui, minecraft.font, Component.literal(action.getTitle()), titleX, y + 5, titleColor, 0);
                YsmActionWheelLayoutStore.Entry entry = YsmActionWheelLayoutStore.get(runtime.getModelKey(), action.getId());
                String pageText = entry == null ? defaultWheelPage(action) : entry.page();
                int slot = entry == null ? 0 : entry.slot();
                YsmActionDefinition owner = slot > 0 ? wheelSlotOwner(runtime, pageText, slot, action) : null;
                String ownerText = owner == null ? "" : " occupied: " + owner.getTitle();
                String activeText = runtime.actions().isActive(action.getId()) ? " active " + numberText(runtime.actions().time(action.getId())) + "s" : "";
                Component meta = Component.literal(pageText + (slot > 0 ? " / " + slot : " / auto") + activeText + ownerText);
                UIHelper.renderScrollingText(gui, meta, titleX, y + 23, Math.max(40, metaRight - titleX), 0xFF888888);
            }
            y += WHEEL_ROW_HEIGHT;
        }
    }

    private void setWheelSlot(YsmModelRuntime runtime, YsmActionDefinition action, int slot) {
        YsmActionWheelLayoutStore.set(runtime.getModelKey(), action.getId(), currentWheelPage(runtime, action), slot);
        refreshYsmWheel();
        rebuild();
    }

    private int currentWheelSlot(YsmModelRuntime runtime, YsmActionDefinition action) {
        YsmActionWheelLayoutStore.Entry entry = YsmActionWheelLayoutStore.get(runtime.getModelKey(), action.getId());
        return entry == null ? -1 : entry.slot();
    }

    private String currentWheelPage(YsmModelRuntime runtime, YsmActionDefinition action) {
        YsmActionWheelLayoutStore.Entry entry = YsmActionWheelLayoutStore.get(runtime.getModelKey(), action.getId());
        return entry == null || entry.page() == null || entry.page().isBlank() ? defaultWheelPage(action) : entry.page();
    }

    private void cycleWheelPage(YsmModelRuntime runtime, YsmActionDefinition action) {
        List<String> pages = wheelPages();
        if (pages.isEmpty())
            return;
        String current = currentWheelPage(runtime, action);
        int index = pages.indexOf(current);
        String next = pages.get((index + 1 + pages.size()) % pages.size());
        int slot = nextAvailableWheelSlot(runtime, next, currentWheelSlot(runtime, action), action);
        YsmActionWheelLayoutStore.set(runtime.getModelKey(), action.getId(), next, slot);
        refreshYsmWheel();
        rebuild();
    }

    private void assignToWheelEditorPage(YsmModelRuntime runtime, YsmActionDefinition action) {
        normalizeWheelEditorPage();
        int slot = nextAvailableWheelSlot(runtime, wheelEditorPage, currentWheelSlot(runtime, action), action);
        if (slot < 1)
            return;
        YsmActionWheelLayoutStore.set(runtime.getModelKey(), action.getId(), wheelEditorPage, slot);
        refreshYsmWheel();
        rebuild();
    }

    private void autoFillWheelEditorPage(YsmModelRuntime runtime) {
        normalizeWheelEditorPage();
        for (YsmActionDefinition action : wheelActions()) {
            if (currentWheelSlot(runtime, action) > 0)
                continue;
            int slot = nextAvailableWheelSlot(runtime, wheelEditorPage, -1, action);
            if (slot < 1)
                break;
            YsmActionWheelLayoutStore.set(runtime.getModelKey(), action.getId(), wheelEditorPage, slot);
        }
        refreshYsmWheel();
        rebuild();
    }

    private int nextAvailableWheelSlot(YsmModelRuntime runtime, String page, int preferredSlot, YsmActionDefinition currentAction) {
        if (preferredSlot > 0 && wheelSlotOwner(runtime, page, preferredSlot, currentAction) == null)
            return preferredSlot;
        for (int slot = 1; slot <= 8; slot++) {
            if (wheelSlotOwner(runtime, page, slot, currentAction) == null)
                return slot;
        }
        return -1;
    }

    private YsmActionDefinition wheelSlotOwner(YsmModelRuntime runtime, String page, int slot, YsmActionDefinition currentAction) {
        if (runtime == null || page == null || page.isBlank() || slot < 1)
            return null;
        for (YsmActionDefinition action : wheelActions()) {
            if (action == currentAction)
                continue;
            YsmActionWheelLayoutStore.Entry entry = YsmActionWheelLayoutStore.get(runtime.getModelKey(), action.getId());
            if (entry != null && slot == entry.slot() && page.equals(entry.page()))
                return action;
        }
        return null;
    }

    private YsmActionDefinition selectedWheelAction(YsmModelRuntime runtime) {
        if (runtime == null || selectedWheelAction == null || selectedWheelAction.isBlank())
            return null;
        YsmActionDefinition action = runtime.actions().get(selectedWheelAction);
        if (!isWheelAction(action)) {
            selectedWheelAction = null;
            return null;
        }
        return action;
    }

    private void beginWheelKeybind(YsmActionDefinition action, boolean toggle) {
        if (action == null)
            return;
        selectedWheelAction = action.getId();
        focusedWheelKeybindAction = action.getId();
        focusedWheelKeybindToggle = toggle;
        rebuild();
    }

    private void bindFocusedWheelKeybind(InputConstants.Key key) {
        YsmModelRuntime runtime = avatar.getYsmRuntime();
        if (runtime == null || focusedWheelKeybindAction == null)
            return;
        YsmActionDefinition action = runtime.actions().get(focusedWheelKeybindAction);
        if (action == null) {
            focusedWheelKeybindAction = null;
            rebuild();
            return;
        }
        YsmActionsAPI api = new YsmActionsAPI(avatar);
        String name = wheelActionKeybindName(action, focusedWheelKeybindToggle);
        String keyName = key == null ? InputConstants.UNKNOWN.getName() : key.getName();
        if (focusedWheelKeybindToggle)
            api.bindToggleAction(name, action.getId(), keyName, false);
        else
            api.bindAction(name, action.getId(), keyName, false);
        selectedWheelAction = action.getId();
        focusedWheelKeybindAction = null;
        rebuild();
    }

    private void unbindWheelActionKeybinds(YsmActionDefinition action) {
        if (action == null)
            return;
        YsmActionsAPI api = new YsmActionsAPI(avatar);
        api.unbindWheelKeybind(wheelActionKeybindName(action, false));
        api.unbindWheelKeybind(wheelActionKeybindName(action, true));
        focusedWheelKeybindAction = null;
        selectedWheelAction = action.getId();
        rebuild();
    }

    private String wheelActionKeybindLabel(YsmActionDefinition action) {
        String press = wheelKeybindText(wheelActionKeybindName(action, false));
        String toggle = wheelKeybindText(wheelActionKeybindName(action, true));
        return "Bind: " + press + " / Toggle: " + toggle;
    }

    private String wheelKeybindText(String name) {
        if (avatar.luaRuntime == null)
            return "none";
        for (var binding : avatar.luaRuntime.keybinds.keyBindings) {
            if (name.equals(binding.getName()))
                return binding.getTranslatedKeyMessage().getString();
        }
        return "none";
    }

    private static String wheelActionKeybindName(YsmActionDefinition action, boolean toggle) {
        return toggle ? "YSM Toggle Action " + action.getId() : "YSM Action " + action.getId();
    }

    private void renderActionItem(GuiGraphicsExtractor gui, YsmActionDefinition action, int x, int y) {
        ItemStack stack = itemForAction(action);
        if (!stack.isEmpty())
            gui.item(stack, x, y);
    }

    private ItemStack itemForAction(YsmActionDefinition action) {
        if (action == null)
            return ItemStack.EMPTY;
        String item = action.getItem();
        if (item == null || item.isBlank())
            item = action.getAnimation() != null && action.getAnimation().startsWith("#") ? "minecraft:comparator" : "minecraft:armor_stand";
        String itemId = item;
        return wheelItemCache.computeIfAbsent(itemId, ignored -> parseActionItem(itemId));
    }

    private ItemStack parseActionItem(String item) {
        try {
            return LuaUtils.parseItemStack("ysm action item", item);
        } catch (RuntimeException ignored) {
            return ItemStack.EMPTY;
        }
    }

    private int wheelEditorListTop() {
        return contentTop() + 152;
    }

    private void normalizeWheelEditorPage() {
        List<String> pages = wheelPages();
        if (pages.isEmpty()) {
            wheelEditorPage = "extra_animation";
        } else if (!pages.contains(wheelEditorPage)) {
            wheelEditorPage = pages.get(0);
        }
    }

    private void cycleWheelEditorPage(int direction) {
        List<String> pages = wheelPages();
        if (pages.isEmpty())
            return;
        int index = pages.indexOf(wheelEditorPage);
        if (index < 0)
            index = 0;
        wheelEditorPage = pages.get((index + direction + pages.size()) % pages.size());
        rebuild();
    }

    private void addWheelEditorPage(YsmModelRuntime runtime) {
        String page = sanitizeWheelPageName(wheelPageNameDraft);
        if (page.isBlank())
            page = nextWheelPageName();
        YsmActionWheelLayoutStore.addPage(runtime.getModelKey(), page);
        wheelEditorPage = page;
        wheelPageNameDraft = page;
        refreshYsmWheel();
        rebuild();
    }

    private void renameWheelEditorPage(YsmModelRuntime runtime) {
        if ("extra_animation".equals(wheelEditorPage))
            return;
        String page = sanitizeWheelPageName(wheelPageNameDraft);
        if (page.isBlank() || page.equals(wheelEditorPage))
            return;
        YsmActionWheelLayoutStore.renamePage(runtime.getModelKey(), wheelEditorPage, page);
        wheelEditorPage = page;
        wheelPageNameDraft = page;
        refreshYsmWheel();
        rebuild();
    }

    private void deleteWheelEditorPage(YsmModelRuntime runtime) {
        if ("extra_animation".equals(wheelEditorPage))
            return;
        YsmActionWheelLayoutStore.removePage(runtime.getModelKey(), wheelEditorPage);
        wheelEditorPage = "extra_animation";
        wheelPageNameDraft = wheelEditorPage;
        refreshYsmWheel();
        rebuild();
    }

    private void clearWheelEditorPage(YsmModelRuntime runtime) {
        normalizeWheelEditorPage();
        YsmActionWheelLayoutStore.clearPage(runtime.getModelKey(), wheelEditorPage);
        refreshYsmWheel();
        rebuild();
    }

    private void compactWheelEditorPage(YsmModelRuntime runtime) {
        normalizeWheelEditorPage();
        YsmActionWheelLayoutStore.compactPage(runtime.getModelKey(), wheelEditorPage);
        refreshYsmWheel();
        rebuild();
    }

    private void clearWheelEditorSlot(YsmModelRuntime runtime, int slot) {
        normalizeWheelEditorPage();
        YsmActionWheelLayoutStore.clearSlot(runtime.getModelKey(), wheelEditorPage, slot);
        refreshYsmWheel();
        rebuild();
    }

    private void moveWheelEditorSlot(YsmModelRuntime runtime, int slot, int direction) {
        normalizeWheelEditorPage();
        int targetSlot = ((slot - 1 + direction + 8) % 8) + 1;
        YsmActionWheelLayoutStore.swapSlots(runtime.getModelKey(), wheelEditorPage, slot, targetSlot);
        refreshYsmWheel();
        rebuild();
    }

    private List<String> wheelPages() {
        LinkedHashSet<String> result = new LinkedHashSet<>();
        result.add("extra_animation");
        YsmModelRuntime runtime = avatar.getYsmRuntime();
        if (runtime != null)
            result.addAll(YsmActionWheelLayoutStore.pages(runtime.getModelKey()));
        for (YsmActionDefinition action : wheelActions()) {
            result.add(defaultWheelPage(action));
            if (runtime != null) {
                YsmActionWheelLayoutStore.Entry entry = YsmActionWheelLayoutStore.get(runtime.getModelKey(), action.getId());
                if (entry != null && entry.page() != null && !entry.page().isBlank())
                    result.add(entry.page());
            }
        }
        return new ArrayList<>(result);
    }

    private String nextWheelPageName() {
        List<String> pages = wheelPages();
        for (int index = 1; index < 100; index++) {
            String page = "page_" + index;
            if (!pages.contains(page))
                return page;
        }
        return "page";
    }

    private String sanitizeWheelPageName(String value) {
        if (value == null)
            return "";
        String page = value.trim().toLowerCase(Locale.US).replace('-', '_').replace(' ', '_');
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < page.length(); i++) {
            char c = page.charAt(i);
            if ((c >= 'a' && c <= 'z') || (c >= '0' && c <= '9') || c == '_' || c == '.')
                builder.append(c);
        }
        return builder.toString();
    }

    private String shortPageName(String page) {
        if (page == null || page.isBlank())
            return "extra";
        String value = page.startsWith("extra_animation") ? page.substring("extra_animation".length()) : page;
        value = value.replace('_', ' ').trim();
        if (value.isBlank())
            value = "extra";
        return value.length() <= 10 ? value : value.substring(0, 9) + ".";
    }

    private String shortActionName(String title) {
        if (title == null || title.isBlank())
            return "?";
        return title.length() <= 8 ? title : title.substring(0, 7) + ".";
    }

    private void refreshYsmWheel() {
        YsmModelRuntime runtime = avatar.getYsmRuntime();
        if (runtime != null && avatar.luaRuntime != null) {
            avatar.luaRuntime.action_wheel.setPage(null);
            runtime.installDefaultActionWheel(avatar.luaRuntime.action_wheel);
        }
    }

    private static boolean isWheelAction(YsmActionDefinition action) {
        if (action == null)
            return false;
        return normalize(action.getId()).contains("extra_animation")
                || normalize(action.getPage()).contains("extra_animation")
                || normalize(action.getAnimation()).contains("extra_animation");
    }

    private static String defaultWheelPage(YsmActionDefinition action) {
        String page = action.getPage();
        return page == null || page.isBlank() || "root".equals(normalize(page)) ? "extra_animation" : page;
    }

    private static String normalize(String value) {
        return value == null ? "" : value.toLowerCase(Locale.US).replace('-', '_').replace(' ', '_');
    }

    private String valueText(AvatarControlDefinition control) {
        Object value = control.getValue();
        return value == null ? "" : value.toString();
    }

    private static String numberText(double value) {
        if (Math.abs(value - Math.rint(value)) < 0.0001d)
            return Long.toString(Math.round(value));
        String text = String.format(Locale.ROOT, "%.3f", value);
        while (text.endsWith("0"))
            text = text.substring(0, text.length() - 1);
        return text.endsWith(".") ? text.substring(0, text.length() - 1) : text;
    }

    private String keybindText(AvatarControlDefinition control) {
        String value = valueText(control);
        return value.isBlank() ? "Unbound" : value;
    }

    private int parseColor(String value, int fallback) {
        if (value == null)
            return fallback;
        String hex = value.trim();
        if (hex.startsWith("#"))
            hex = hex.substring(1);
        try {
            return 0xFF000000 | Integer.parseUnsignedInt(hex, 16);
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private void renderFiguraBackground(GuiGraphicsExtractor gui, float delta) {
        float speed = Configs.BACKGROUND_SCROLL_SPEED.tempValue * 0.125f;
        for (Identifier background : AbstractPanelScreen.BACKGROUNDS) {
            UIHelper.renderAnimatedBackground(gui, background, 0, 0, this.width, this.height, 64, 64, speed, delta);
            speed /= 0.5f;
        }
        gui.nextStratum();
    }

    private class ControlSlider extends SliderWidget {
        private final AvatarControlDefinition control;
        private double lastValue = Double.NaN;

        private ControlSlider(int x, int y, int width, int height, AvatarControlDefinition control) {
            super(x, y, width, height, progress(control), steps(control), steps(control) <= 21);
            this.control = control;
        }

        @Override
        public void extractWidgetRenderState(GuiGraphicsExtractor gui, int mouseX, int mouseY, float delta) {
            Matrix3x2fStack pose = gui.pose();
            Font font = Minecraft.getInstance().font;

            pose.pushMatrix();
            pose.translate(0f, font.lineHeight);
            super.extractWidgetRenderState(gui, mouseX, mouseY, delta);
            pose.popMatrix();

            Component value = Component.literal(numberText(currentValue()));
            int valueX = getX() + getWidth() - font.width(value) - 1;
            gui.text(font, value.copy().setStyle(FiguraMod.getAccentColor()), valueX, getY(), UIHelper.adjustColor(0xFFFFFF));
        }

        private void syncValue() {
            double value = currentValue();
            if (!control.hasStoredValue() && Double.isNaN(lastValue)) {
                lastValue = value;
                return;
            }
            if (Double.isNaN(lastValue) || Math.abs(lastValue - value) > 0.0001d) {
                lastValue = value;
                avatar.controls.setValue(avatar, control.id(), value);
            }
        }

        private double currentValue() {
            double value = control.min() + getScrollProgress() * (control.max() - control.min());
            double step = control.step();
            if (step > 0d)
                value = Math.round(value / step) * step;
            return Math.max(control.min(), Math.min(control.max(), value));
        }
    }

    private class ControlSwitch extends SwitchButton {
        private final AvatarControlDefinition control;

        private ControlSwitch(int x, int y, int width, int height, AvatarControlDefinition control) {
            super(x, y, width, height, Boolean.TRUE.equals(control.getValue()) ? SwitchButton.ON : SwitchButton.OFF, Boolean.TRUE.equals(control.getValue()));
            this.control = control;
            setUnderline(false);
        }

        @Override
        public void onPress(InputWithModifiers inputWithModifiers) {
            boolean next = !isToggled();
            avatar.controls.setValue(avatar, control.id(), next);
            setMessage(next ? SwitchButton.ON : SwitchButton.OFF);
            super.onPress(inputWithModifiers);
        }
    }

    private static double progress(AvatarControlDefinition control) {
        Object value = control.getValue();
        double number = value instanceof Number n ? n.doubleValue() : control.min();
        double range = control.max() - control.min();
        return range <= 0d ? 0d : (number - control.min()) / range;
    }

    private static int steps(AvatarControlDefinition control) {
        double range = control.max() - control.min();
        double step = control.step();
        if (range <= 0d || step <= 0d)
            return 2;
        return Math.max(2, Math.min(101, (int) Math.round(range / step) + 1));
    }

    private class ControlDropdown extends Button {
        private final AvatarControlDefinition control;
        private final ContextMenu context;

        private ControlDropdown(int x, int y, int width, AvatarControlDefinition control) {
            super(x, y, width, 20, Component.literal(valueText(control)), null, button -> ((ControlDropdown) button).toggleDropdown());
            this.control = control;
            this.context = new ContextMenu(this, width);
            for (String option : control.options()) {
                this.context.addAction(Component.literal(option), null, button -> {
                    avatar.controls.setValue(avatar, control.id(), option);
                    this.setMessage(Component.literal(option));
                    this.context.setVisible(false);
                    openDropdown = null;
                });
            }
            updateContextText();
        }

        private void toggleDropdown() {
            if (control.options().isEmpty())
                return;
            boolean visible = openDropdown != context || !context.isVisible();
            if (openDropdown != null)
                openDropdown.setVisible(false);
            openDropdown = visible ? context : null;
            context.setVisible(visible);
            if (visible) {
                updateContextText();
                context.setX(getX() + getWidth() / 2 - context.getWidth() / 2);
                context.setY(getY() + getHeight());
            }
        }

        @Override
        protected void renderText(GuiGraphicsExtractor gui, float delta) {
            Font font = Minecraft.getInstance().font;
            Component arrow = context.isVisible() ? UIHelper.DOWN_ARROW : UIHelper.UP_ARROW;
            int arrowWidth = font.width(arrow);
            Component message = Component.literal(valueText(control));
            int color = UIHelper.adjustColor(getTextColor());
            UIHelper.renderCenteredScrollingText(gui, message, getX() + 1, getY(), getWidth() - arrowWidth - 6, getHeight(), color);
            gui.text(font, arrow, getX() + getWidth() - arrowWidth - 3, (int) (getY() + getHeight() / 2f - font.lineHeight / 2f), color);
        }

        @Override
        public void setHovered(boolean hovered) {
            if (!hovered && openDropdown == context && context.isVisible())
                hovered = true;
            super.setHovered(hovered);
        }

        private void updateContextText() {
            List<? extends net.minecraft.client.gui.components.AbstractWidget> entries = context.getEntries();
            List<String> options = control.options();
            Object value = control.getValue();
            String selected = value == null ? "" : value.toString();
            for (int i = 0; i < options.size() && i < entries.size(); i++) {
                Component text = Component.literal(options.get(i));
                if (options.get(i).equals(selected))
                    text = Component.empty().setStyle(FiguraMod.getAccentColor()).withStyle(ChatFormatting.UNDERLINE).append(text);
                entries.get(i).setMessage(text);
            }
        }
    }
}
