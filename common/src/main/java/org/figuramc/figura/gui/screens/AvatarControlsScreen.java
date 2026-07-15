package org.figuramc.figura.gui.screens;

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
import org.figuramc.figura.utils.ui.UIHelper;
import org.joml.Matrix3x2fStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class AvatarControlsScreen extends Screen {
    private static final int ROW_HEIGHT = 26;
    private static final int HEADER_HEIGHT = 52;
    private static final int TAB_HEIGHT = 28;
    private static final int FOOTER_HEIGHT = 42;

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
        for (AvatarControlDefinition control : avatar.controls.all()) {
            if (page.equals(control.page()) && control.targetPage() == null)
                controls.add(control);
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
        Button resetAll = new Button(panelX(), height - 32, footerWidth / 2 - 4, 20, Component.literal("Reset All"), null, button -> {
            avatar.controls.resetAll(avatar);
            rebuild();
        });
        resetAll.setActive(hasResettableControls());
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
