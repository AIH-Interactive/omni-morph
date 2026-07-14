package org.figuramc.figura.lua.api.avatar_controls;

import org.figuramc.figura.avatar.Avatar;
import org.figuramc.figura.avatar.control.AvatarControlDefinition;
import org.figuramc.figura.avatar.control.AvatarControlType;
import org.figuramc.figura.gui.screens.AvatarControlsScreen;
import org.figuramc.figura.lua.LuaNotNil;
import org.figuramc.figura.lua.LuaWhitelist;
import org.figuramc.figura.lua.docs.LuaMethodDoc;
import org.figuramc.figura.lua.docs.LuaMethodOverload;
import org.figuramc.figura.lua.docs.LuaTypeDoc;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaFunction;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@LuaWhitelist
@LuaTypeDoc(
        name = "AvatarControlsAPI",
        value = "avatar_controls"
)
public class AvatarControlsAPI {
    private final Avatar owner;

    public AvatarControlsAPI(Avatar owner) {
        this.owner = owner;
    }

    @LuaWhitelist
    @LuaMethodDoc("avatar_controls.get_controls")
    public Map<String, AvatarControlDefinition> getControls() {
        return owner.controls.asMap();
    }

    @LuaWhitelist
    public Map<String, AvatarControlDefinition> get_controls() {
        return getControls();
    }

    @LuaWhitelist
    @LuaMethodDoc(
            overloads = @LuaMethodOverload(
                    argumentTypes = String.class,
                    argumentNames = "id"
            ),
            value = "avatar_controls.get_control"
    )
    public AvatarControlDefinition getControl(String id) {
        return owner.controls.get(id);
    }

    @LuaWhitelist
    public AvatarControlDefinition get_control(String id) {
        return getControl(id);
    }

    @LuaWhitelist
    @LuaMethodDoc("avatar_controls.get_types")
    public List<String> getTypes() {
        List<String> types = new ArrayList<>();
        for (AvatarControlType type : AvatarControlType.values())
            types.add(type.name().toLowerCase(Locale.ROOT));
        return types;
    }

    @LuaWhitelist
    public List<String> get_types() {
        return getTypes();
    }

    @LuaWhitelist
    @LuaMethodDoc(
            overloads = @LuaMethodOverload(
                    argumentTypes = {String.class, String.class},
                    argumentNames = {"id", "type"}
            ),
            aliases = "of",
            value = "avatar_controls.new_control"
    )
    public AvatarControlDefinition newControl(@LuaNotNil String id, @LuaNotNil String type) {
        AvatarControlDefinition control = register(id, parseType(type));
        if (isPageType(type))
            control.setTargetPage(id);
        return control;
    }

    @LuaWhitelist
    public AvatarControlDefinition new_control(@LuaNotNil String id, @LuaNotNil String type) {
        return newControl(id, type);
    }

    @LuaWhitelist
    public AvatarControlDefinition of(@LuaNotNil String id, @LuaNotNil String type) {
        return newControl(id, type);
    }

    @LuaWhitelist
    @LuaMethodDoc("avatar_controls.new_toggle")
    public AvatarControlDefinition newToggle(@LuaNotNil String id) {
        return register(id, AvatarControlType.TOGGLE).setDefault(false);
    }

    @LuaWhitelist
    public AvatarControlDefinition new_toggle(@LuaNotNil String id) {
        return newToggle(id);
    }

    @LuaWhitelist
    public AvatarControlDefinition newCheckbox(@LuaNotNil String id) {
        return newToggle(id);
    }

    @LuaWhitelist
    public AvatarControlDefinition new_checkbox(@LuaNotNil String id) {
        return newToggle(id);
    }

    @LuaWhitelist
    public AvatarControlDefinition newSwitch(@LuaNotNil String id) {
        return newToggle(id);
    }

    @LuaWhitelist
    public AvatarControlDefinition new_switch(@LuaNotNil String id) {
        return newToggle(id);
    }

    @LuaWhitelist
    @LuaMethodDoc("avatar_controls.new_slider")
    public AvatarControlDefinition newSlider(@LuaNotNil String id) {
        return register(id, AvatarControlType.SLIDER).setDefault(0d);
    }

    @LuaWhitelist
    public AvatarControlDefinition new_slider(@LuaNotNil String id) {
        return newSlider(id);
    }

    @LuaWhitelist
    public AvatarControlDefinition newRange(@LuaNotNil String id) {
        return newSlider(id);
    }

    @LuaWhitelist
    public AvatarControlDefinition new_range(@LuaNotNil String id) {
        return newSlider(id);
    }

    @LuaWhitelist
    @LuaMethodDoc("avatar_controls.new_enum")
    public AvatarControlDefinition newEnum(@LuaNotNil String id) {
        return register(id, AvatarControlType.ENUM);
    }

    @LuaWhitelist
    public AvatarControlDefinition new_enum(@LuaNotNil String id) {
        return newEnum(id);
    }

    @LuaWhitelist
    public AvatarControlDefinition newDropdown(@LuaNotNil String id) {
        return newEnum(id);
    }

    @LuaWhitelist
    public AvatarControlDefinition new_dropdown(@LuaNotNil String id) {
        return newEnum(id);
    }

    @LuaWhitelist
    public AvatarControlDefinition newSelect(@LuaNotNil String id) {
        return newEnum(id);
    }

    @LuaWhitelist
    public AvatarControlDefinition new_select(@LuaNotNil String id) {
        return newEnum(id);
    }

    @LuaWhitelist
    public AvatarControlDefinition newRadio(@LuaNotNil String id) {
        return newEnum(id);
    }

    @LuaWhitelist
    public AvatarControlDefinition new_radio(@LuaNotNil String id) {
        return newEnum(id);
    }

    @LuaWhitelist
    @LuaMethodDoc("avatar_controls.new_color")
    public AvatarControlDefinition newColor(@LuaNotNil String id) {
        return register(id, AvatarControlType.COLOR);
    }

    @LuaWhitelist
    public AvatarControlDefinition new_color(@LuaNotNil String id) {
        return newColor(id);
    }

    @LuaWhitelist
    @LuaMethodDoc("avatar_controls.new_text")
    public AvatarControlDefinition newText(@LuaNotNil String id) {
        return register(id, AvatarControlType.TEXT).setDefault("");
    }

    @LuaWhitelist
    public AvatarControlDefinition new_text(@LuaNotNil String id) {
        return newText(id);
    }

    @LuaWhitelist
    public AvatarControlDefinition newInput(@LuaNotNil String id) {
        return newText(id);
    }

    @LuaWhitelist
    public AvatarControlDefinition new_input(@LuaNotNil String id) {
        return newText(id);
    }

    @LuaWhitelist
    @LuaMethodDoc("avatar_controls.new_number")
    public AvatarControlDefinition newNumber(@LuaNotNil String id) {
        return register(id, AvatarControlType.NUMBER).setDefault(0d);
    }

    @LuaWhitelist
    public AvatarControlDefinition new_number(@LuaNotNil String id) {
        return newNumber(id);
    }

    @LuaWhitelist
    @LuaMethodDoc("avatar_controls.new_keybind")
    public AvatarControlDefinition newKeybind(@LuaNotNil String id) {
        return register(id, AvatarControlType.KEYBIND);
    }

    @LuaWhitelist
    public AvatarControlDefinition new_keybind(@LuaNotNil String id) {
        return newKeybind(id);
    }

    @LuaWhitelist
    @LuaMethodDoc("avatar_controls.new_button")
    public AvatarControlDefinition newButton(@LuaNotNil String id) {
        return register(id, AvatarControlType.BUTTON);
    }

    @LuaWhitelist
    public AvatarControlDefinition new_button(@LuaNotNil String id) {
        return newButton(id);
    }

    @LuaWhitelist
    public AvatarControlDefinition newAction(@LuaNotNil String id) {
        return newButton(id);
    }

    @LuaWhitelist
    public AvatarControlDefinition new_action(@LuaNotNil String id) {
        return newButton(id);
    }

    @LuaWhitelist
    @LuaMethodDoc("avatar_controls.new_page")
    public AvatarControlDefinition newPage(@LuaNotNil String id) {
        return newButton(id).setTargetPage(id);
    }

    @LuaWhitelist
    public AvatarControlDefinition new_page(@LuaNotNil String id) {
        return newPage(id);
    }

    @LuaWhitelist
    public AvatarControlDefinition newTab(@LuaNotNil String id) {
        return newPage(id);
    }

    @LuaWhitelist
    public AvatarControlDefinition new_tab(@LuaNotNil String id) {
        return newPage(id);
    }

    @LuaWhitelist
    @LuaMethodDoc("avatar_controls.new_label")
    public AvatarControlDefinition newLabel(@LuaNotNil String id) {
        return register(id, AvatarControlType.LABEL);
    }

    @LuaWhitelist
    public AvatarControlDefinition new_label(@LuaNotNil String id) {
        return newLabel(id);
    }

    @LuaWhitelist
    @LuaMethodDoc("avatar_controls.new_separator")
    public AvatarControlDefinition newSeparator(@LuaNotNil String id) {
        return register(id, AvatarControlType.SEPARATOR);
    }

    @LuaWhitelist
    public AvatarControlDefinition new_separator(@LuaNotNil String id) {
        return newSeparator(id);
    }

    @LuaWhitelist
    @LuaMethodDoc("avatar_controls.get")
    public Object get(@LuaNotNil String id) {
        return owner.controls.getValue(id);
    }

    @LuaWhitelist
    @LuaMethodDoc("avatar_controls.set")
    public AvatarControlsAPI set(@LuaNotNil String id, Object value) {
        owner.controls.setValue(owner, id, value);
        return this;
    }

    @LuaWhitelist
    @LuaMethodDoc("avatar_controls.press")
    public AvatarControlsAPI press(@LuaNotNil String id) {
        owner.controls.press(owner, id);
        return this;
    }

    @LuaWhitelist
    @LuaMethodDoc(
            overloads = @LuaMethodOverload(
                    argumentTypes = LuaFunction.class,
                    argumentNames = "function"
            ),
            aliases = "onChange",
            value = "avatar_controls.set_on_change"
    )
    public AvatarControlsAPI setOnChange(LuaFunction function) {
        owner.controls.setOnChange(function);
        return this;
    }

    @LuaWhitelist
    public AvatarControlsAPI set_on_change(LuaFunction function) {
        return setOnChange(function);
    }

    @LuaWhitelist
    public AvatarControlsAPI onChange(LuaFunction function) {
        return setOnChange(function);
    }

    @LuaWhitelist
    public AvatarControlsAPI on_change(LuaFunction function) {
        return setOnChange(function);
    }

    @LuaWhitelist
    @LuaMethodDoc("avatar_controls.remove")
    public AvatarControlsAPI remove(@LuaNotNil String id) {
        owner.controls.remove(id);
        return this;
    }

    @LuaWhitelist
    @LuaMethodDoc("avatar_controls.clear")
    public AvatarControlsAPI clear() {
        owner.controls.clear();
        return this;
    }

    @LuaWhitelist
    @LuaMethodDoc("avatar_controls.open_page")
    public AvatarControlsAPI openPage(String page) {
        AvatarControlsScreen.open(owner, page);
        return this;
    }

    @LuaWhitelist
    public AvatarControlsAPI open_page(String page) {
        return openPage(page);
    }

    private AvatarControlDefinition register(String id, AvatarControlType type) {
        AvatarControlDefinition control = new AvatarControlDefinition(id, type);
        switch (type) {
            case TOGGLE -> control.setDefault(false);
            case SLIDER, NUMBER -> control.setDefault(0d);
            case TEXT -> control.setDefault("");
            default -> {
            }
        }
        return owner.controls.register(control);
    }

    private static AvatarControlType parseType(String type) {
        String normalized = type == null ? "" : type.trim().toUpperCase(Locale.ROOT).replace('-', '_');
        try {
            return switch (normalized) {
                case "BOOL", "BOOLEAN", "CHECKBOX", "SWITCH" -> AvatarControlType.TOGGLE;
                case "RANGE" -> AvatarControlType.SLIDER;
                case "DROPDOWN", "SELECT", "RADIO" -> AvatarControlType.ENUM;
                case "STRING", "INPUT", "FIELD" -> AvatarControlType.TEXT;
                case "INT", "INTEGER", "FLOAT", "DOUBLE" -> AvatarControlType.NUMBER;
                case "ACTION" -> AvatarControlType.BUTTON;
                case "TAB", "PAGE" -> AvatarControlType.BUTTON;
                default -> AvatarControlType.valueOf(normalized);
            };
        } catch (IllegalArgumentException exception) {
            throw new LuaError("Invalid avatar control type: " + type);
        }
    }

    private static boolean isPageType(String type) {
        String normalized = type == null ? "" : type.trim().toUpperCase(Locale.ROOT).replace('-', '_');
        return "TAB".equals(normalized) || "PAGE".equals(normalized);
    }

    @Override
    public String toString() {
        return "AvatarControlsAPI";
    }
}
