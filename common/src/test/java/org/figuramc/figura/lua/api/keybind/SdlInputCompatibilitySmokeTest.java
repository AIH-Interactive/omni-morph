package org.figuramc.figura.lua.api.keybind;

import com.mojang.blaze3d.platform.InputConstants;
import org.figuramc.figura.utils.LegacyInput;

/** Public Lua key IDs remain GLFW-compatible while native matching uses SDL keys. */
public final class SdlInputCompatibilitySmokeTest {
    public static void main(String[] args) {
        checkKey("key.keyboard.a", 65, InputConstants.KEY_A);
        checkKey("key.keyboard.z", 90, InputConstants.KEY_Z);
        checkKey("key.keyboard.0", 48, InputConstants.KEY_0);
        checkKey("key.keyboard.escape", 256, InputConstants.KEY_ESCAPE);
        checkKey("key.keyboard.f8", 297, InputConstants.KEY_F8);
        checkKey("key.keyboard.right.control", 345, InputConstants.KEY_RCONTROL);
        checkKey("key.mouse.left", 0, InputConstants.MOUSE_BUTTON_LEFT);
        checkKey("key.mouse.right", 1, InputConstants.MOUSE_BUTTON_RIGHT);
        checkKey("key.mouse.middle", 2, InputConstants.MOUSE_BUTTON_MIDDLE);
        checkKey("key.mouse.8", 7, InputConstants.MOUSE_BUTTON_8);
        checkKey("key.keyboard.keypad.decimal", 330, 99);
        checkKey("key.keyboard.menu", 348, 101);
        require(LegacyInput.key(118) == -1, "SDL Menu must not alias GLFW Application");
        require(LegacyInput.key(0) == -1, "unknown native key stays unknown");
        require(LegacyInput.action(-1) == 2, "SDL repeat becomes Lua repeat");
        require(LegacyInput.action(1) == 1 && LegacyInput.action(0) == 0, "press/release survive");
        require(LegacyInput.modifiers(0x0001) == 1 && LegacyInput.modifiers(0x0002) == 1,
                "both shift sides collapse to public shift");
        require(LegacyInput.modifiers(0x0040) == 2 && LegacyInput.modifiers(0x0080) == 2,
                "both control sides collapse to public control");
        require(LegacyInput.modifiers(0x0100) == 4 && LegacyInput.modifiers(0x0200) == 4,
                "both alt sides collapse to public alt");
        require(LegacyInput.modifiers(0x0400) == 8 && LegacyInput.modifiers(0x0800) == 8,
                "both super sides collapse to public super");
        require(LegacyInput.modifiers(0x3000) == 48, "lock flags retain caps/num meaning");
        boolean rejected = false;
        try {
            FiguraKeybind.parseStringKey("key.keyboard.f25");
        } catch (org.luaj.vm2.LuaError expected) {
            rejected = true;
        }
        require(rejected, "unsupported F25 must not fabricate a native key");
        System.out.println("PASS SDL input compatibility: public key IDs and native matching");
    }

    private static void checkKey(String name, int legacyId, int nativeId) {
        var key = FiguraKeybind.parseStringKey(name);
        var binding = new FiguraKeybind(null, "fixture", key);
        require(binding.getID() == legacyId, name + " public ID: expected " + legacyId + ", got " + binding.getID());
        require(key.getValue() == nativeId, name + " must retain its native key for matching");
        require(binding.getKey().equals(name), name + " serialized name must survive");
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
