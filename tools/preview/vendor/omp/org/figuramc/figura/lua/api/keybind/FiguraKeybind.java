package org.figuramc.figura.lua.api.keybind;

import top.aihmc.omm.preview.PreviewKeys;
import top.aihmc.omm.preview.PreviewLua;
import org.figuramc.figura.lua.LuaNotNil;
import org.figuramc.figura.lua.LuaWhitelist;
import org.figuramc.figura.lua.docs.LuaFieldDoc;
import org.figuramc.figura.lua.docs.LuaMethodDoc;
import org.figuramc.figura.lua.docs.LuaMethodOverload;
import org.figuramc.figura.lua.docs.LuaTypeDoc;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaFunction;
import org.luaj.vm2.Varargs;

import java.util.List;

@LuaWhitelist
@LuaTypeDoc(
        name = "Keybind",
        value = "keybind"
)
public class FiguraKeybind {

    private final PreviewLua owner;
    private final String name;
    private final PreviewKeys.Key defaultKey;

    private PreviewKeys.Key key;
    private boolean isDown, override;
    private boolean enabled = true;
    private boolean gui;

    @LuaWhitelist
    @LuaFieldDoc("keybind.press")
    public LuaFunction press;

    @LuaWhitelist
    @LuaFieldDoc("keybind.release")
    public LuaFunction release;

    public FiguraKeybind(PreviewLua owner, String name, PreviewKeys.Key key) {
        this.owner = owner;
        this.name = name;
        this.defaultKey = key;
        this.key = key;
    }

    public void resetDefaultKey() {
        this.key = this.defaultKey;
    }

    public boolean setDown(boolean pressed, int modifiers) {
        // events
        if (isDown != pressed) {
            Varargs result = null;

            if (pressed) {
                if (press != null)
                    result = owner.run(press, modifiers, this);
            } else if (release != null) {
                result = owner.run(release, modifiers, this);
            }

            override = result != null && result.arg(1).isboolean() && result.checkboolean(1);
        }

        this.isDown = pressed;
        return override;
    }

    public void setKey(PreviewKeys.Key key) {
        this.key = key;
    }

    public String getTranslatedKeyMessage() {
        return this.key.getDisplayName();
    }

    // -- static -- //

    public static PreviewKeys.Key parseStringKey(String key) {
        try {
            return PreviewKeys.parse(key);
        } catch (Exception passed) {
            throw new LuaError("Invalid key: " + key);
        }
    }

    public static boolean set(List<FiguraKeybind> bindings, PreviewKeys.Key key, boolean pressed, int modifiers) {
        boolean overrided = false;
        for (FiguraKeybind keybind : List.copyOf(bindings)) {
            if (keybind.key.equals(key) && keybind.enabled && (keybind.gui || !keybind.owner.keybindGui()))
                overrided = keybind.setDown(pressed, modifiers) || overrided;
        }
        return overrided;
    }

    public static void releaseAll(List<FiguraKeybind> bindings) {
        for (FiguraKeybind keybind : bindings)
            keybind.setDown(false, -1);
    }

    public static boolean overridesKey(List<FiguraKeybind> bindings, PreviewKeys.Key key) {
        for (FiguraKeybind binding : bindings)
            if (binding.key.equals(key) && binding.enabled && binding.override)
                return true;
        return false;
    }


    // -- lua -- //


    @LuaWhitelist
    @LuaMethodDoc(
            overloads = @LuaMethodOverload(
                    argumentTypes = LuaFunction.class,
                    argumentNames = "function"
            ),
            aliases = "onPress",
            value = "keybind.set_on_press"
    )
    public FiguraKeybind setOnPress(LuaFunction function) {
        this.press = function;
        return this;
    }

    @LuaWhitelist
    public FiguraKeybind onPress(LuaFunction function) {
        return setOnPress(function);
    }

    @LuaWhitelist
    @LuaMethodDoc(
            overloads = @LuaMethodOverload(
                    argumentTypes = LuaFunction.class,
                    argumentNames = "function"
            ),
            aliases = "onRelease",
            value = "keybind.set_on_release"
    )
    public FiguraKeybind setOnRelease(LuaFunction function) {
        this.release = function;
        return this;
    }

    @LuaWhitelist
    public FiguraKeybind onRelease(LuaFunction function) {
        return setOnRelease(function);
    }

    @LuaWhitelist
    @LuaMethodDoc(
            overloads = @LuaMethodOverload(
                    argumentTypes = String.class,
                    argumentNames = "key"
            ),
            aliases = "key",
            value = "keybind.set_key"
    )
    public FiguraKeybind setKey(@LuaNotNil String key) {
        this.key = parseStringKey(key);
        return this;
    }

    @LuaWhitelist
    public FiguraKeybind key(@LuaNotNil String key) {
        return setKey(key);
    }

    @LuaWhitelist
    @LuaMethodDoc("keybind.is_default")
    public boolean isDefault() {
        return this.key.equals(this.defaultKey);
    }

    @LuaWhitelist
    @LuaMethodDoc("keybind.get_key")
    public String getKey() {
        return this.key.getName();
    }

    @LuaWhitelist
    @LuaMethodDoc("keybind.get_key_name")
    public String getKeyName() {
        return this.key.getDisplayName();
    }

    @LuaWhitelist
    @LuaMethodDoc("keybind.get_name")
    public String getName() {
        return this.name;
    }

    @LuaWhitelist
    @LuaMethodDoc("keybind.get_id")
    public int getID() {
        return this.key.getValue();
    }

    @LuaWhitelist
    @LuaMethodDoc("keybind.is_pressed")
    public boolean isPressed() {
        return (this.gui || !owner.keybindGui()) && this.isDown;
    }

    @LuaWhitelist
    @LuaMethodDoc("keybind.is_enabled")
    public boolean isEnabled() {
        return this.enabled;
    }

    @LuaWhitelist
    @LuaMethodDoc(
            overloads = @LuaMethodOverload(
                    argumentTypes = Boolean.class,
                    argumentNames = "bool"
            ),
            aliases = "enabled",
            value = "keybind.set_enabled"
    )
    public FiguraKeybind setEnabled(boolean bool) {
        this.enabled = bool;
        return this;
    }

    @LuaWhitelist
    public FiguraKeybind enabled(boolean bool) {
        return setEnabled(bool);
    }

    @LuaWhitelist
    @LuaMethodDoc("keybind.is_gui_enabled")
    public boolean isGuiEnabled() {
        return this.gui;
    }

    @LuaWhitelist
    @LuaMethodDoc(
            overloads = @LuaMethodOverload(
                    argumentTypes = Boolean.class,
                    argumentNames = "bool"
            ),
            aliases = "gui",
            value = "keybind.set_gui"
    )
    public FiguraKeybind setGUI(boolean bool) {
        this.gui = bool;
        return this;
    }

    @LuaWhitelist
    public FiguraKeybind gui(boolean bool) {
        return setGUI(bool);
    }

    @LuaWhitelist
    public Object __index(String arg) {
        if (arg == null) return null;
        return switch (arg) {
            case "press" -> press;
            case "release" -> release;
            default -> null;
        };
    }

    @LuaWhitelist
    public void __newindex(@LuaNotNil String key, LuaFunction value) {
        switch (key) {
            case "press" -> press = value;
            case "release" -> release = value;
            default -> throw new LuaError("Cannot assign value on key \"" + key + "\"");
        }
    }

    @Override
    public String toString() {
        return this.name + " (" + key.getName() + ") (Keybind)";
    }
}
