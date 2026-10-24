package top.aihmc.omnimorph.gui.widgets.library;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.PreeditEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.figuramc.figura.gui.widgets.AbstractContainerElement;
import org.figuramc.figura.gui.widgets.Button;
import org.figuramc.figura.gui.widgets.TextField;
import org.figuramc.figura.utils.FiguraText;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/** Editable tag chips with native Figura fields/buttons; pending input remains part of the draft. */
public final class LibraryTagField extends AbstractContainerElement {
    private final List<String> tags = new ArrayList<>();
    private final List<Button> chips = new ArrayList<>();
    private final TextField input;
    private final Button add;
    private final Consumer<String> changed;
    private boolean enabled = true;
    private int chipScroll;

    private int maximumScroll() {
        return Math.max(0, chips.stream().mapToInt(button -> button.getWidth() + 3).sum() - getWidth());
    }

    public LibraryTagField(int width, String value, Consumer<String> changed) {
        super(0, 0, width, 44);
        this.changed = changed;
        for (String tag : value.split(",")) {
            if (!tag.isBlank()) {
                tags.add(tag.strip());
            }
        }
        input = new TextField(0, 0, Math.max(20, width - 24), 20, TextField.HintType.ANY, ignored -> publish());
        input.getField().setMaxLength(4096);
        add = new Button(0, 0, 20, 20, Component.literal("+"), FiguraText.of("gui.library.add_tag"), ignored -> commit());
        rebuildChips();
    }

    private void publish() {
        String pending = input == null ? "" : input.getField().getValue();
        changed.accept(String.join(",", tags) + (tags.isEmpty() ? "" : ",") + pending);
    }

    private void commit() {
        for (String value : input.getField().getValue().split(",")) {
            String tag = value.strip();
            if (!tag.isEmpty() && tags.stream().noneMatch(existing -> existing.equalsIgnoreCase(tag))) {
                tags.add(tag);
            }
        }
        input.getField().setValue("");
        rebuildChips();
        publish();
    }

    private void rebuildChips() {
        chips.clear();
        for (String tag : tags) {
            int width = Math.min(getWidth(), Minecraft.getInstance().font.width(tag) + 20);
            var chip = new Button(0, 0, width, 18, Component.literal(tag + " ×"), Component.literal(tag), ignored -> {
                tags.remove(tag);
                rebuildChips();
                publish();
            });
            chip.setActive(enabled);
            chips.add(chip);
        }
        children.clear();
        children.add(input);
        children.add(add);
        children.addAll(chips);
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
        input.setEnabled(enabled);
        add.setActive(enabled);
        chips.forEach(button -> button.setActive(enabled));
        if (!enabled) {
            setFocused(false);
        }
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (!enabled) {
            return false;
        }
        if (input.isFocused() && (event.key() == 257 || event.key() == 335)) {
            commit();
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (!enabled || !isMouseOver(event.x(), event.y())) {
            return false;
        }
        boolean handled = super.mouseClicked(event, doubleClick);
        if (handled && (event.y() < getY() + 20 || add.isMouseOver(event.x(), event.y()))) {
            setFocused(input);
            input.setFocused(true);
        }
        return handled;
    }

    @Override
    public boolean charTyped(CharacterEvent event) {
        return enabled && super.charTyped(event);
    }

    @Override
    public boolean preeditUpdated(PreeditEvent event) {
        return enabled && super.preeditUpdated(event);
    }

    @Override
    public boolean mouseScrolled(double x, double y, double horizontal, double vertical) {
        if (isMouseOver(x, y) && y < getY() + 20 && maximumScroll() > 0) {
            chipScroll = Math.clamp(chipScroll - (int) (vertical * 24 + horizontal * 24), 0, maximumScroll());
            return true;
        }
        return super.mouseScrolled(x, y, horizontal, vertical);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor gui, int mouseX, int mouseY, float delta) {
        input.setX(getX());
        input.setY(getY() + 22);
        add.setX(getX() + getWidth() - 20);
        add.setY(getY() + 22);
        chipScroll = Math.clamp(chipScroll, 0, maximumScroll());
        int x = getX() - chipScroll;
        for (Button chip : chips) {
            chip.setX(x);
            chip.setY(getY());
            x += chip.getWidth() + 3;
        }
        gui.enableScissor(getX(), getY(), getX() + getWidth(), getY() + getHeight());
        try {
            super.extractRenderState(gui, mouseX, mouseY, delta);
        } finally {
            gui.disableScissor();
        }
    }
}
