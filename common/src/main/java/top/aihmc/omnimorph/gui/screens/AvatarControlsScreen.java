package top.aihmc.omnimorph.gui.screens;

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
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import org.figuramc.figura.FiguraMod;
import org.figuramc.figura.utils.FiguraText;
import org.figuramc.figura.avatar.Avatar;
import top.aihmc.omnimorph.avatar.control.AvatarControlDefinition;
import top.aihmc.omnimorph.avatar.control.AvatarControlType;
import org.figuramc.figura.config.Configs;
import org.figuramc.figura.gui.widgets.Button;
import org.figuramc.figura.gui.widgets.ContextMenu;
import org.figuramc.figura.gui.widgets.EntityPreview;
import org.figuramc.figura.gui.widgets.SliderWidget;
import org.figuramc.figura.gui.widgets.ScrollBarWidget;
import org.figuramc.figura.gui.widgets.SwitchButton;
import org.figuramc.figura.gui.widgets.TextField;
import top.aihmc.omnimorph.lua.api.ysm_actions.YsmActionsAPI;
import top.aihmc.omnimorph.model.library.LibraryPreviewSession;
import top.aihmc.omnimorph.model.ysm.YsmModelRuntime;
import top.aihmc.omnimorph.model.ysm.action.YsmActionDefinition;
import top.aihmc.omnimorph.model.ysm.action.YsmActionWheelLayoutStore;
import org.figuramc.figura.utils.LuaUtils;
import org.figuramc.figura.utils.ui.UIHelper;
import org.joml.Matrix3x2fStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Supplier;

import org.figuramc.figura.gui.screens.AbstractPanelScreen;
public class AvatarControlsScreen extends Screen {
    private static final int ROW_HEIGHT = 26;
    private static final int SECTION_HEADER_HEIGHT = 22;
    private static final int WHEEL_ROW_HEIGHT = 42;
    private static final int HEADER_HEIGHT = 52;
    private static final int TAB_HEIGHT = 28;
    private static final int TAB_SCROLLBAR_SPACE = 14;
    private static final int FOOTER_HEIGHT = 42;
    /** Space reserved above the panel for a subclass's own row (tabs plus their scrollbar) in the framed layout. */
    protected static final int FRAMED_TOP_ROW_HEIGHT = 40;
    /** Gap that keeps the footer buttons below the panel border in the framed layout. */
    private static final int FRAMED_FOOTER_GAP = 12;
    private static final int CONTENT_SCROLLBAR_WIDTH = 10;
    private static final String YSM_WHEEL_PAGE = "__ysm_wheel";

    private final Screen parentScreen;
    protected Avatar avatar;
    private String page;
    private List<ControlSection> sections = List.of();
    private final List<ControlRow> rows = new ArrayList<>();
    private final List<String> pages = new ArrayList<>();
    private final List<ControlSlider> sliders = new ArrayList<>();
    private AvatarControlDefinition focusedKeybind;
    private Avatar focusedKeybindOwner;
    private EntityPreview preview;
    private ContextMenu openDropdown;
    private int scroll;
    private final List<Button> pageTabs = new ArrayList<>();
    private ScrollBarWidget pageTabScrollbar;
    private int pageTabScroll;
    private int pageTabOverflow;
    private String wheelEditorPage = "extra_animation";
    private String wheelPageNameDraft = "";
    private String selectedWheelAction;
    private String focusedWheelKeybindAction;
    private boolean focusedWheelKeybindToggle;
    private final Map<String, ItemStack> wheelItemCache = new HashMap<>();
    private boolean buildingControls;
    private boolean contentScrollDirty;

    /** One owner's controls on the page; a non-null header separates owners that share the page. */
    protected record ControlSection(Avatar owner, Component header) {}

    /** A rendered row: either a section header ({@code control == null}) or one control of {@code owner}. */
    private record ControlRow(Avatar owner, Component header, AvatarControlDefinition control) {}

    public AvatarControlsScreen(Screen parentScreen, Avatar avatar, String page) {
        super(FiguraText.of("gui.controls.title"));
        this.parentScreen = parentScreen;
        this.avatar = avatar;
        this.page = page == null || page.isBlank() ? "root" : page;
    }

    public static void open(Avatar avatar, String page) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null || avatar == null)
            return;
        if (minecraft.screen instanceof CosmeticAnimationScreen preview && preview.openControls(avatar, page)) {
            return;
        }
        var formal = OutfitControlsScreen.create(minecraft.screen, avatar, page);
        if (formal != null) {
            minecraft.setScreen(formal);
            return;
        }
        if (avatar.cosmeticContext != null && avatar.cosmeticContext.scope()
                != top.aihmc.omnimorph.model.outfit.CosmeticInstanceContext.Scope.DRAFT) {
            return;
        }
        minecraft.setScreen(new AvatarControlsScreen(minecraft.screen, avatar, page));
    }

    @Override
    protected void init() {
        super.init();
        rebuild();
    }

    protected void rebuild() {
        buildingControls = true;
        try {
            buildControls();
        } finally {
            buildingControls = false;
        }
    }

    private void buildControls() {
        sections = List.copyOf(controlSections());
        for (ControlSection section : sections)
            section.owner().controls.hydrateLiveValues(section.owner());
        this.clearWidgets();
        pageTabs.clear();
        pageTabScrollbar = null;
        sliders.clear();
        rows.clear();
        pages.clear();
        openDropdown = null;
        collectPages();
        if (!pages.contains(page))
            page = pages.isEmpty() ? "root" : pages.get(0);

        addPageTabs();
        if (isWheelPage()) {
            addWheelEditorWidgets();
        } else {
            collectRows();
        }

        int controlsWidth = controlsWidth();
        int controlsX = controlsX();
        int y = contentTop() - scroll;
        for (ControlRow row : rows) {
            AvatarControlDefinition control = row.control();
            if (control != null && rowWidgetsVisible(y, rowHeight(row))) {
                Avatar owner = row.owner();
                int widgetX = controlsX + controlsWidth - 238;
                int widgetWidth = 180;
                addControlWidget(widgetX, y + 3, widgetWidth, owner, control);
                if (canReset(control))
                    this.addRenderableWidget(new Button(widgetX + widgetWidth + 8, y + 3, 42, 20, FiguraText.of("gui.controls.reset"), null, button -> {
                        setControlValue(owner, control, control.getDefault());
                        rebuild();
                    }));
            }
            y += rowHeight(row);
        }
        addContentScrollbar();

        if (previewWidth() > 0) {
            preview = new EntityPreview(previewX(), previewY(), previewWidth(), previewHeight(), previewScale(), 0f, 0f, minecraft == null ? null : minecraft.player, this);
            this.addRenderableWidget(preview);
            if (controlPreview() != null) {
                preview.setLibraryPreview(controlPreview());
            }
        }

        int footerWidth = panelWidth();
        Avatar expectedAvatar = avatar;
        Button resetAll = new Button(panelX(), footerY(), footerWidth / 2 - 4, 20, FiguraText.of(isWheelPage() ? "gui.controls.refresh_wheel" : "gui.reset_all"), null, button -> {
            if (!controlsActive() || avatar != expectedAvatar) {
                return;
            }
            if (isWheelPage()) {
                refreshYsmWheel();
                return;
            }
            for (ControlSection section : sections) {
                for (var control : List.copyOf(section.owner().controls.all())) {
                    if (canReset(control)) {
                        setControlValue(section.owner(), control, control.getDefault());
                    }
                }
            }
            rebuild();
        });
        resetAll.setActive(isWheelPage() ? avatar.getYsmRuntime() != null : hasResettableControls());
        this.addRenderableWidget(resetAll);
        this.addRenderableWidget(new Button(panelX() + footerWidth / 2 + 4, footerY(), footerWidth / 2 - 4, 20, Component.translatable("gui.done"), null, button -> onClose()));
        buildAdditionalControls();
    }

    protected void buildAdditionalControls() {
    }

    /**
     * The framed layout reserves a row above the panel for the subclass, keeps the footer below the
     * panel border, shows a vertical scrollbar for long control lists and hides rows outside the content.
     */
    protected boolean framedLayout() {
        return false;
    }

    private int topInset() {
        return framedLayout() ? FRAMED_TOP_ROW_HEIGHT : 0;
    }

    private int headerTop() {
        return HEADER_HEIGHT + topInset();
    }

    private int panelY() {
        return 20 + topInset();
    }

    private int panelHeight() {
        return height - 48 - topInset() - (framedLayout() ? FRAMED_FOOTER_GAP : 0);
    }

    private int footerY() {
        return height - 32;
    }

    private boolean rowWidgetsVisible(int y, int rowHeight) {
        if (!framedLayout())
            return true;
        return y >= contentTop() && y + rowHeight <= height - FOOTER_HEIGHT;
    }

    private boolean rowLabelVisible(int y, int rowHeight) {
        if (framedLayout())
            return rowWidgetsVisible(y, rowHeight);
        return y > contentTop() - rowHeight && y < height - FOOTER_HEIGHT;
    }

    private void addContentScrollbar() {
        int max = maxScroll();
        if (!framedLayout() || isWheelPage() || max <= 0)
            return;
        int top = contentTop();
        int scrollbarHeight = Math.max(20, height - FOOTER_HEIGHT - top);
        ScrollBarWidget scrollbar = new ScrollBarWidget(controlsX() + controlsWidth() - 4, top,
                CONTENT_SCROLLBAR_WIDTH, scrollbarHeight, (double) scroll / max, true);
        scrollbar.setAction(bar -> {
            int next = (int) Math.round(bar.getScrollProgress() * max);
            if (next != scroll) {
                scroll = next;
                contentScrollDirty = true;
            }
        });
        this.addRenderableWidget(scrollbar);
    }

    /** The owners shown together on this page; the plain avatar settings show only {@link #avatar}. */
    protected List<ControlSection> controlSections() {
        return List.of(new ControlSection(avatar, null));
    }

    private void collectRows() {
        for (ControlSection section : sections) {
            List<AvatarControlDefinition> pageControls = new ArrayList<>();
            for (AvatarControlDefinition control : section.owner().controls.all()) {
                if (page.equals(control.page()) && control.targetPage() == null)
                    pageControls.add(control);
            }
            if (pageControls.isEmpty())
                continue;
            if (section.header() != null)
                rows.add(new ControlRow(section.owner(), section.header(), null));
            for (AvatarControlDefinition control : pageControls)
                rows.add(new ControlRow(section.owner(), null, control));
        }
    }

    protected boolean controlsActive() {
        return true;
    }

    protected void controlsChanged() {
    }

    protected void validateControlChange(AvatarControlDefinition control, Object value) {
    }

    protected boolean applyControlValue(AvatarControlDefinition control, Object value) {
        return avatar.controls.setValue(avatar, control.id(), value);
    }

    protected void pressControl(AvatarControlDefinition control) {
        avatar.controls.press(avatar, control.id());
    }

    protected boolean includeWheelEditor() {
        return true;
    }

    protected LibraryPreviewSession controlPreview() {
        return null;
    }

    protected void selectControlAvatar(Avatar selected) {
        avatar = selected;
        page = "root";
        scroll = 0;
        focusedKeybind = null;
        focusedKeybindOwner = null;
        rebuild();
    }

    /**
     * Runs a control callback with {@link #avatar} bound to the control's owner, so the activity,
     * validation, apply and save hooks all address the instance whose row was edited.
     */
    private <T> T asControlOwner(Avatar owner, Supplier<T> action) {
        Avatar selected = avatar;
        avatar = owner;
        try {
            return action.get();
        } finally {
            avatar = selected;
        }
    }

    private boolean setControlValue(Avatar owner, AvatarControlDefinition control, Object value) {
        return asControlOwner(owner, () -> {
            if (!controlsActive() || avatar.controls.get(control.id()) != control
                    || buildingControls && avatar.cosmeticContext != null) {
                return false;
            }
            boolean changed;
            try {
                validateControlChange(control, value);
                changed = applyControlValue(control, value);
            } catch (IllegalArgumentException invalid) {
                if (avatar.cosmeticContext == null) {
                    throw invalid;
                }
                return false;
            }
            if (changed) {
                controlsChanged();
            }
            return changed;
        });
    }

    @Override
    public void onClose() {
        minecraft.setScreen(parentScreen);
    }

    private void addControlWidget(int x, int y, int width, Avatar owner, AvatarControlDefinition control) {
        AvatarControlType type = control.type();
        switch (type) {
            case TOGGLE -> addToggle(x, y, width, owner, control);
            case SLIDER -> addSlider(x, y + 5, width, owner, control);
            case ENUM -> addEnum(x, y, width, owner, control);
            case COLOR -> addTextInput(x, y, width, owner, control, TextField.HintType.HEX_COLOR, true);
            case TEXT -> addTextInput(x, y, width, owner, control, TextField.HintType.ANY, false);
            case NUMBER -> addTextInput(x, y, width, owner, control, TextField.HintType.FLOAT, false);
            case KEYBIND -> addKeybind(x, y, width, owner, control);
            case BUTTON -> this.addRenderableWidget(new Button(x, y, width, 20, FiguraText.of(control.targetPage() == null ? "gui.controls.run" : "gui.controls.open"), null, button -> {
                if (control.targetPage() != null) {
                    page = control.targetPage();
                    scroll = 0;
                    rebuild();
                } else {
                    asControlOwner(owner, () -> {
                        if (controlsActive() && avatar.controls.get(control.id()) == control) {
                            pressControl(control);
                            controlsChanged();
                        }
                        return null;
                    });
                }
            }));
            default -> {
            }
        }
    }

    private void addToggle(int x, int y, int width, Avatar owner, AvatarControlDefinition control) {
        this.addRenderableWidget(new ControlSwitch(x + Math.max(0, width - 92), y, 92, 20, owner, control));
    }

    private void addSlider(int x, int y, int width, Avatar owner, AvatarControlDefinition control) {
        ControlSlider slider = new ControlSlider(x, y, width, 20, owner, control);
        sliders.add(slider);
        this.addRenderableWidget(slider);
    }

    private void addEnum(int x, int y, int width, Avatar owner, AvatarControlDefinition control) {
        this.addRenderableWidget(new ControlDropdown(x, y, width, owner, control));
    }

    private void addTextInput(int x, int y, int width, Avatar owner, AvatarControlDefinition control, TextField.HintType hint, boolean colorPreview) {
        TextField field = new TextField(x, y, width, 20, hint, ignored -> {});
        field.getField().setValue(valueText(control));
        field.getField().setResponder(text -> {
            boolean accepted;
            if (control.type() == AvatarControlType.NUMBER) {
                try {
                    accepted = setControlValue(owner, control, Double.parseDouble(text));
                } catch (NumberFormatException ignored) {
                    accepted = false;
                }
            } else {
                accepted = setControlValue(owner, control, text);
            }
            field.setBorderColour(accepted ? 0xffffffff : 0xffff5555);
        });
        if (colorPreview)
            field.setBorderColour(parseColor(valueText(control), 0xFFFFFFFF));
        this.addRenderableWidget(field);
    }

    private void addKeybind(int x, int y, int width, Avatar owner, AvatarControlDefinition control) {
        this.addRenderableWidget(new Button(x, y, width, 20, Component.literal(keybindText(control)), null, button -> {
            focusedKeybind = control;
            focusedKeybindOwner = owner;
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
        for (ControlSection section : sections) {
            for (AvatarControlDefinition control : section.owner().controls.all()) {
                if (canReset(control))
                    return true;
            }
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
                focusedKeybindOwner = null;
                rebuild();
                return true;
            }
            setControlValue(focusedKeybindOwner, focusedKeybind, "key." + keyEvent.key());
            focusedKeybind = null;
            focusedKeybindOwner = null;
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
            setControlValue(focusedKeybindOwner, focusedKeybind, "mouse." + event.button());
            focusedKeybind = null;
            focusedKeybindOwner = null;
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
    public boolean mouseReleased(MouseButtonEvent event) {
        boolean handled = super.mouseReleased(event);
        rebuildDirtyContentScroll();
        return handled;
    }

    /** Dragging the content scrollbar only records the offset; the rows are rebuilt once the drag ends. */
    private void rebuildDirtyContentScroll() {
        if (!contentScrollDirty)
            return;
        contentScrollDirty = false;
        rebuild();
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double amount, double horizontalAmount) {
        if (pageTabScrollbar != null && mouseX >= controlsX() + 8
                && mouseX < controlsX() + controlsWidth() - 8
                && mouseY >= headerTop() && mouseY < contentTop()) {
            pageTabScrollbar.setScrollProgress(pageTabScrollbar.getScrollProgress()
                    - (amount + horizontalAmount) * 72d / pageTabOverflow);
            return true;
        }
        if (super.mouseScrolled(mouseX, mouseY, amount, horizontalAmount)) {
            rebuildDirtyContentScroll();
            return true;
        }
        int max = maxScroll();
        int next = Mth.clamp(scroll + (int) Math.signum(-amount - horizontalAmount) * scrollStep(), 0, max);
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
        UIHelper.renderOutlineText(gui, minecraft.font, FiguraText.of("gui.controls.page", pageTitle(page)), controlsX + 10, panelY() + 8, 0xFFFFFF, 0);
        if (previewWidth() > 0)
            UIHelper.renderOutlineText(gui, minecraft.font, FiguraText.of("gui.controls.preview"), previewX() + 8, panelY() + 8, 0xFFD0D0D0, 0);
        if (isWheelPage()) {
            renderWheelEditor(gui, controlsX, controlsWidth);
            renderDropdown(gui, mouseX, mouseY, delta);
            return;
        }
        if (rows.isEmpty()) {
            UIHelper.renderOutlineText(gui, minecraft.font, FiguraText.of("gui.controls.empty"), controlsX + 10, contentTop() + 8, 0xAAAAAA, 0);
            return;
        }

        int y = contentTop() - scroll;
        for (ControlRow row : rows) {
            AvatarControlDefinition control = row.control();
            if (control == null) {
                if (rowLabelVisible(y, SECTION_HEADER_HEIGHT))
                    renderSectionHeader(gui, row.header(), controlsX + 10, y);
                y += rowHeight(row);
                continue;
            }
            if (rowLabelVisible(y, ROW_HEIGHT)) {
                renderControlLabel(gui, control, controlsX + 10, y);
                String description = control.getDescription();
                if (description != null && !description.isBlank() && mouseX >= controlsX
                        && mouseX < controlsX + controlsWidth && mouseY >= y && mouseY < y + rowHeight(row)) {
                    gui.setTooltipForNextFrame(minecraft.font, Component.literal(description), mouseX, mouseY);
                }
                if (control.type() == AvatarControlType.COLOR)
                    renderColorPreview(gui, controlsX + controlsWidth - 214, y + 5, valueText(control));
            }
            y += rowHeight(row);
        }
        renderDropdown(gui, mouseX, mouseY, delta);
    }

    private void renderDropdown(GuiGraphicsExtractor gui, int mouseX, int mouseY, float delta) {
        if (openDropdown == null || !openDropdown.isVisible())
            return;
        gui.nextStratum();
        openDropdown.extractRenderState(gui, mouseX, mouseY, delta);
    }

    private void renderSectionHeader(GuiGraphicsExtractor gui, Component header, int x, int y) {
        UIHelper.renderOutlineText(gui, minecraft.font, header, x, y + 6, 0xFFFFD27F, 0);
        int lineX = x + minecraft.font.width(header) + 8;
        gui.fill(lineX, y + 10, x + controlsWidth() - 20, y + 11, 0x66FFFFFF);
    }

    private void renderControlLabel(GuiGraphicsExtractor gui, AvatarControlDefinition control, int x, int y) {
        if (control.type() == AvatarControlType.SEPARATOR) {
            gui.fill(x, y + 12, x + controlsWidth() - 20, y + 13, 0x66FFFFFF);
            return;
        }
        int titleColor = control.type() == AvatarControlType.LABEL ? 0xFFD0D0D0 : 0xFFFFFFFF;
        UIHelper.renderOutlineText(gui, minecraft.font, Component.literal(control.getTitle()), x, y + 7, titleColor, 0);
        Component meta = FiguraText.of("gui.controls.type." + control.getType());
        gui.text(minecraft.font, meta, x + Math.min(190, controlsWidth() / 2), y + 7, 0xFF888888);
    }

    private void renderColorPreview(GuiGraphicsExtractor gui, int x, int y, String value) {
        int color = parseColor(value, 0xFF000000);
        UIHelper.blitSliced(gui, x, y, 20, 20, UIHelper.OUTLINE);
        gui.fill(x + 2, y + 2, x + 18, y + 18, color);
    }

    private void renderPanel(GuiGraphicsExtractor gui) {
        int x = panelX();
        int y = panelY();
        int w = panelWidth();
        int h = panelHeight();
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

    private int rowHeight(ControlRow row) {
        if (row.control() == null)
            return SECTION_HEADER_HEIGHT;
        return row.control().type() == AvatarControlType.SEPARATOR ? 18 : ROW_HEIGHT;
    }

    private int maxScroll() {
        if (isWheelPage())
            return Math.max(0, wheelActions().size() * WHEEL_ROW_HEIGHT - Math.max(0, height - wheelEditorListTop() - FOOTER_HEIGHT));
        int content = 0;
        for (ControlRow row : rows)
            content += rowHeight(row);
        return Math.max(0, content - Math.max(0, height - contentTop() - FOOTER_HEIGHT));
    }

    private int scrollStep() {
        return isWheelPage() ? WHEEL_ROW_HEIGHT : ROW_HEIGHT;
    }

    private int contentTop() {
        return headerTop() + (pages.size() > 1 ? TAB_HEIGHT : 0)
                + (pageTabScrollbar == null ? 0 : TAB_SCROLLBAR_SPACE);
    }

    private void collectPages() {
        addPage("root");
        for (ControlSection section : sections) {
            for (AvatarControlDefinition control : section.owner().controls.all())
                addPage(control.page());
        }
        if (includeWheelEditor() && avatar.getYsmRuntime() != null && !wheelActions().isEmpty())
            addPage(YSM_WHEEL_PAGE);
    }

    private void addPage(String page) {
        if (page != null && !page.isBlank() && !pages.contains(page))
            pages.add(page);
    }

    private void addPageTabs() {
        if (pages.size() <= 1) {
            pageTabScroll = 0;
            pageTabOverflow = 0;
            return;
        }
        int left = controlsX() + 8;
        int right = controlsX() + controlsWidth() - 8;
        int top = headerTop();
        int available = Math.max(1, right - left);
        int tabWidth = Math.max(72, Math.min(132, available / pages.size()));
        pageTabOverflow = Math.max(0, pages.size() * tabWidth - 4 - available);
        pageTabScroll = Mth.clamp(pageTabScroll, 0, pageTabOverflow);
        int selected = Math.max(0, pages.indexOf(page)) * tabWidth;
        if (selected < pageTabScroll) {
            pageTabScroll = selected;
        } else if (selected + tabWidth - 4 > pageTabScroll + available) {
            pageTabScroll = Math.min(pageTabOverflow, selected + tabWidth - 4 - available);
        }
        for (String target : pages) {
            Button tab = new Button(left, top, tabWidth - 4, 20,
                    Component.literal(pageTitle(target)), null, button -> {
                        page = target;
                        scroll = 0;
                        rebuild();
                    }) {
                @Override
                public boolean isMouseOver(double mouseX, double mouseY) {
                    return mouseX >= left && mouseX < right && super.isMouseOver(mouseX, mouseY);
                }

                @Override
                public void extractContents(GuiGraphicsExtractor gui, int mouseX, int mouseY, float delta) {
                    gui.enableScissor(left, top, right, top + 20);
                    super.extractContents(gui, mouseX, mouseY, delta);
                    gui.disableScissor();
                }
            };
            tab.setActive(!target.equals(page));
            pageTabs.add(tab);
            this.addRenderableWidget(tab);
        }
        updatePageTabPositions();
        if (pageTabOverflow > 0) {
            pageTabScrollbar = new ScrollBarWidget(left, top + 24, available, 10,
                    (double) pageTabScroll / pageTabOverflow, false);
            pageTabScrollbar.setAction(bar -> {
                pageTabScroll = (int) Math.round(bar.getScrollProgress() * pageTabOverflow);
                updatePageTabPositions();
            });
            this.addRenderableWidget(pageTabScrollbar);
        }
    }

    private void updatePageTabPositions() {
        int x = controlsX() + 8 - pageTabScroll;
        for (Button tab : pageTabs) {
            tab.setX(x);
            x += tab.getWidth() + 4;
        }
    }

    private String pageTitle(String id) {
        if (YSM_WHEEL_PAGE.equals(id))
            return FiguraText.of("gui.controls.wheel").getString();
        if ("root".equals(id))
            return FiguraText.of("gui.controls.settings").getString();
        for (ControlSection section : sections) {
            for (AvatarControlDefinition control : section.owner().controls.all()) {
                if (id.equals(control.targetPage()))
                    return control.getTitle();
                if (id.equals(control.page()) && control.type() == AvatarControlType.LABEL)
                    return control.getTitle();
            }
        }
        return id;
    }

    private boolean isWheelPage() {
        return includeWheelEditor() && YSM_WHEEL_PAGE.equals(page);
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
        this.addRenderableWidget(new Button(x, overviewY, 68, 20, FiguraText.of("gui.controls.auto_fill"), null, ignored -> autoFillWheelEditorPage(runtime)));
        x += 72;
        this.addRenderableWidget(new Button(x, overviewY, 66, 20, FiguraText.of("gui.controls.compact"), null, ignored -> compactWheelEditorPage(runtime)));
        x += 70;
        this.addRenderableWidget(new Button(x, overviewY, 82, 20, FiguraText.of("gui.controls.clear_page"), null, ignored -> clearWheelEditorPage(runtime)));
        x += 86;
        this.addRenderableWidget(new Button(x, overviewY, 70, 20, FiguraText.of("gui.controls.stop_all"), null, ignored -> {
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
        this.addRenderableWidget(new Button(editX, editY, 46, 20, FiguraText.of("gui.controls.add"), null, ignored -> addWheelEditorPage(runtime)));
        editX += 50;
        Button renamePage = new Button(editX, editY, 64, 20, FiguraText.of("gui.controls.rename"), null, ignored -> renameWheelEditorPage(runtime));
        renamePage.setActive(!"extra_animation".equals(wheelEditorPage));
        this.addRenderableWidget(renamePage);
        editX += 68;
        Button deletePage = new Button(editX, editY, 58, 20, FiguraText.of("gui.controls.delete"), null, ignored -> deleteWheelEditorPage(runtime));
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
        Button bindButton = new Button(controlsX + 10, selectedY, 52, 20, FiguraText.of(focusedWheelKeybindAction != null && !focusedWheelKeybindToggle ? "gui.controls.press" : "gui.controls.bind"), null, ignored -> beginWheelKeybind(selectedAction, false));
        bindButton.setActive(selectedAction != null);
        this.addRenderableWidget(bindButton);
        Button toggleBindButton = new Button(controlsX + 66, selectedY, 76, 20, FiguraText.of(focusedWheelKeybindAction != null && focusedWheelKeybindToggle ? "gui.controls.press" : "gui.controls.toggle_bind"), null, ignored -> beginWheelKeybind(selectedAction, true));
        toggleBindButton.setActive(selectedAction != null);
        this.addRenderableWidget(toggleBindButton);
        Button unbindButton = new Button(controlsX + 146, selectedY, 58, 20, FiguraText.of("gui.controls.unbind"), null, ignored -> unbindWheelActionKeybinds(selectedAction));
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
        int listTop = wheelEditorListTop();
        int listBottom = height - FOOTER_HEIGHT;
        int y = listTop - scroll;
        for (YsmActionDefinition action : wheelActions()) {
            if (isWheelRowVisible(y, listTop, listBottom)) {
                int buttonY = y + 6;
                Button putButton = new Button(putButtonX, buttonY, 42, 20, FiguraText.of("gui.controls.put"), null, ignored -> assignToWheelEditorPage(runtime, action));
                putButton.setActive(nextAvailableWheelSlot(runtime, wheelEditorPage, currentWheelSlot(runtime, action), action) > 0);
                this.addRenderableWidget(putButton);
                this.addRenderableWidget(new Button(selectButtonX, buttonY, 56, 20, FiguraText.of("gui.controls.select"), null, ignored -> {
                    selectedWheelAction = action.getId();
                    rebuild();
                }));
                this.addRenderableWidget(new Button(runButtonX, buttonY, 38, 20, FiguraText.of("gui.controls.run"), null, ignored -> {
                    selectedWheelAction = action.getId();
                    runtime.actions().trigger(action.getId());
                    rebuild();
                }));
                Button stopButton = new Button(stopButtonX, buttonY, 38, 20, FiguraText.of("gui.controls.stop"), null, ignored -> {
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
                this.addRenderableWidget(new Button(slotX + buttonWidth * 8 + 6, buttonY, clearWidth, 20, FiguraText.of("gui.controls.clear"), null, ignored -> {
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
            UIHelper.renderOutlineText(gui, minecraft.font, FiguraText.of("gui.controls.no_model"), controlsX + 10, contentTop() + 8, 0xAAAAAA, 0);
            return;
        }
        List<YsmActionDefinition> actions = wheelActions();
        if (actions.isEmpty()) {
            UIHelper.renderOutlineText(gui, minecraft.font, FiguraText.of("gui.controls.no_actions"), controlsX + 10, contentTop() + 8, 0xAAAAAA, 0);
            return;
        }
        normalizeWheelEditorPage();
        int innerX = controlsX + 10;
        int innerWidth = controlsWidth - 20;
        YsmActionDefinition selectedAction = selectedWheelAction(runtime);
        String selectedText = FiguraText.of("gui.controls.selected", selectedAction == null
                ? FiguraText.of("gui.controls.none").getString()
                : selectedAction.getTitle() + "  " + wheelActionKeybindLabel(selectedAction)).getString();
        UIHelper.renderOutlineText(gui, minecraft.font, Component.literal(selectedText), innerX + 204, contentTop() + 116, selectedAction == null ? 0xFF777777 : 0xFF66AAFF, 0);
        if (previewWidth() > 0 && selectedAction != null) {
            String previewText = runtime.actions().isActive(selectedAction.getId())
                    ? FiguraText.of("gui.controls.active_time",
                            numberText(runtime.actions().time(selectedAction.getId()))).getString()
                    : FiguraText.of("gui.controls.idle").getString();
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

        int listTop = wheelEditorListTop();
        int listBottom = height - FOOTER_HEIGHT;
        int y = listTop - scroll;
        int rightX = controlsX + controlsWidth - 10;
        int buttonWidth = Math.max(20, Math.min(30, (controlsWidth - 420) / 10));
        int clearWidth = 58;
        int slotX = rightX - clearWidth - 6 - buttonWidth * 8;
        int pageButtonWidth = Math.max(86, Math.min(116, controlsWidth / 10));
        int putButtonX = slotX - pageButtonWidth - 8 - 46 - 46 - 50;
        int titleX = controlsX + 126;
        int metaRight = Math.max(titleX + 80, putButtonX - 10);
        for (YsmActionDefinition action : actions) {
            if (isWheelRowVisible(y, listTop, listBottom)) {
                renderActionItem(gui, action, controlsX + 10, y + 10);
                int titleColor = action.getId().equals(selectedWheelAction) ? 0xFF66AAFF : 0xFFFFFFFF;
                UIHelper.renderOutlineText(gui, minecraft.font, Component.literal(action.getTitle()), titleX, y + 5, titleColor, 0);
                YsmActionWheelLayoutStore.Entry entry = YsmActionWheelLayoutStore.get(runtime.getModelKey(), action.getId());
                String pageText = entry == null ? defaultWheelPage(action) : entry.page();
                int slot = entry == null ? 0 : entry.slot();
                YsmActionDefinition owner = slot > 0 ? wheelSlotOwner(runtime, pageText, slot, action) : null;
                String ownerText = owner == null ? ""
                        : " " + FiguraText.of("gui.controls.occupied", owner.getTitle()).getString();
                String activeText = runtime.actions().isActive(action.getId())
                        ? " " + FiguraText.of("gui.controls.active_time",
                                numberText(runtime.actions().time(action.getId()))).getString() : "";
                Component meta = Component.literal(pageText + " / "
                        + (slot > 0 ? slot : FiguraText.of("gui.controls.auto").getString()) + activeText + ownerText);
                UIHelper.renderScrollingText(gui, meta, titleX, y + 23, Math.max(40, metaRight - titleX), 0xFF888888);
            }
            y += WHEEL_ROW_HEIGHT;
        }
    }

    private boolean isWheelRowVisible(int y, int listTop, int listBottom) {
        return y >= listTop && y + WHEEL_ROW_HEIGHT <= listBottom;
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
        return FiguraText.of("gui.controls.binding", press, toggle).getString();
    }

    private String wheelKeybindText(String name) {
        if (avatar.luaRuntime == null)
            return FiguraText.of("gui.controls.none").getString();
        for (var binding : avatar.luaRuntime.keybinds.keyBindings) {
            if (name.equals(binding.getName()))
                return binding.getTranslatedKeyMessage().getString();
        }
        return FiguraText.of("gui.controls.none").getString();
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
        if (control != null && control.type() == AvatarControlType.ENUM) {
            String option = optionText(control, value);
            if (option != null)
                return option;
        }
        return value == null ? "" : value.toString();
    }

    private static String optionText(AvatarControlDefinition control, Object value) {
        String raw = rawOptionText(control, value);
        return raw == null ? null : control.optionLabel(raw);
    }

    private static String rawOptionText(AvatarControlDefinition control, Object value) {
        if (control == null || value == null)
            return null;
        String text = value.toString();
        if (control.options().contains(text))
            return text;
        Integer index = optionIndex(value);
        if (index == null || index < 0 || index >= control.options().size())
            return null;
        return control.options().get(index);
    }

    private static Integer optionIndex(Object value) {
        if (value instanceof Number number)
            return Math.round(number.floatValue());
        if (value instanceof String string) {
            try {
                return Math.round(Float.parseFloat(string));
            } catch (NumberFormatException ignored) {
            }
        }
        return null;
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
        return value.isBlank() ? FiguraText.of("gui.controls.unbound").getString() : value;
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
        for (ResourceLocation background : AbstractPanelScreen.BACKGROUNDS) {
            UIHelper.renderAnimatedBackground(gui, background, 0, 0, this.width, this.height, 64, 64, speed, delta);
            speed /= 0.5f;
        }
        gui.nextStratum();
    }

    private class ControlSlider extends SliderWidget {
        private final Avatar owner;
        private final AvatarControlDefinition control;
        private double lastValue = Double.NaN;

        private ControlSlider(int x, int y, int width, int height, Avatar owner, AvatarControlDefinition control) {
            super(x, y, width, height, progress(control), steps(control), steps(control) <= 21);
            this.owner = owner;
            this.control = control;
            lastValue = currentValue();
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
            double sliderValue = currentValue();
            if (control.getValue() instanceof Number number) {
                double external = Math.max(control.min(), Math.min(control.max(), number.doubleValue()));
                if (!Double.isNaN(lastValue) && Math.abs(external - lastValue) > 0.0001d
                        && Math.abs(external - sliderValue) > 0.0001d) {
                    // A cloud/remote state or another UI wrote the control; move this slider to it.
                    setScrollProgress(progressFor(external));
                    lastValue = external;
                    return;
                }
            }
            if (!control.hasStoredValue() && Double.isNaN(lastValue)) {
                lastValue = sliderValue;
                return;
            }
            if (Double.isNaN(lastValue) || Math.abs(lastValue - sliderValue) > 0.0001d) {
                lastValue = sliderValue;
                setControlValue(owner, control, sliderValue);
            }
        }

        private double progressFor(double number) {
            double range = control.max() - control.min();
            return range <= 0d ? 0d : Math.clamp((number - control.min()) / range, 0d, 1d);
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
        private final Avatar owner;
        private final AvatarControlDefinition control;

        private ControlSwitch(int x, int y, int width, int height, Avatar owner, AvatarControlDefinition control) {
            super(x, y, width, height, Boolean.TRUE.equals(control.getValue()) ? SwitchButton.ON : SwitchButton.OFF, Boolean.TRUE.equals(control.getValue()));
            this.owner = owner;
            this.control = control;
            setUnderline(false);
        }

        @Override
        public void onPress(InputWithModifiers inputWithModifiers) {
            boolean next = !isToggled();
            if (!setControlValue(owner, control, next)) {
                return;
            }
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

        private ControlDropdown(int x, int y, int width, Avatar owner, AvatarControlDefinition control) {
            super(x, y, width, 20, Component.literal(valueText(control)), null, button -> ((ControlDropdown) button).toggleDropdown());
            this.control = control;
            this.context = new ContextMenu(this, width);
            for (String option : control.options()) {
                this.context.addAction(Component.literal(control.optionLabel(option)), null, button -> {
                    if (!setControlValue(owner, control, option)) {
                        return;
                    }
                    this.setMessage(Component.literal(control.optionLabel(option)));
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
            String selected = rawOptionText(control, control.getValue());
            for (int i = 0; i < options.size() && i < entries.size(); i++) {
                Component text = Component.literal(control.optionLabel(options.get(i)));
                if (options.get(i).equals(selected))
                    text = Component.empty().setStyle(FiguraMod.getAccentColor()).withStyle(ChatFormatting.UNDERLINE).append(text);
                entries.get(i).setMessage(text);
            }
        }
    }
}
