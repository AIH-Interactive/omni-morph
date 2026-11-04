package org.figuramc.figura.lua.api.keybind;

/** Public Lua key identifiers must retain their pre-SDL meaning without creating a client. */
public final class LegacyInputSmokeTest {
    public static void main(String[] args) {
        var keybind = new FiguraKeybind(null, "letter", FiguraKeybind.parseStringKey("key.keyboard.a"));
        require(keybind.getID() == 65, "Lua key A remains GLFW identifier 65, got " + keybind.getID());
        require(keybind.getKey().equals("key.keyboard.a"), "named key configuration remains stable");
        System.out.println("PASS legacy Lua key identifiers");
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
