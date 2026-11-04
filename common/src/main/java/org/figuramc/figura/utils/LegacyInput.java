package org.figuramc.figura.utils;

import com.mojang.blaze3d.platform.InputConstants;

/** Converts SDL device values at the public Lua/Molang compatibility boundary. */
public final class LegacyInput {
    private LegacyInput() {
    }

    public static int key(int scancode) {
        if (scancode >= 4 && scancode <= 29) {
            return scancode + 61;
        }
        if (scancode >= 30 && scancode <= 38) {
            return scancode + 19;
        }
        if (scancode >= 58 && scancode <= 69) {
            return scancode + 232;
        }
        if (scancode >= 104 && scancode <= 115) {
            return scancode + 198;
        }
        if (scancode >= 89 && scancode <= 97) {
            return scancode + 232;
        }
        return switch (scancode) {
            case 39 -> 48;
            case 40 -> 257;
            case 41 -> 256;
            case 42 -> 259;
            case 43 -> 258;
            case 44 -> 32;
            case 45 -> 45;
            case 46 -> 61;
            case 47 -> 91;
            case 48 -> 93;
            case 49 -> 92;
            case 50 -> 162;
            case 51 -> 59;
            case 52 -> 39;
            case 53 -> 96;
            case 54 -> 44;
            case 55 -> 46;
            case 56 -> 47;
            case 57 -> 280;
            case 70 -> 283;
            case 71 -> 281;
            case 72 -> 284;
            case 73 -> 260;
            case 74 -> 268;
            case 75 -> 266;
            case 76 -> 261;
            case 77 -> 269;
            case 78 -> 267;
            case 79 -> 262;
            case 80 -> 263;
            case 81 -> 264;
            case 82 -> 265;
            case 83 -> 282;
            case 84 -> 331;
            case 85 -> 332;
            case 86 -> 333;
            case 87 -> 334;
            case 88 -> 335;
            case 98 -> 320;
            case 99 -> 330;
            case 100 -> 161;
            case 101 -> 348;
            case 103 -> 336;
            case 224 -> 341;
            case 225 -> 340;
            case 226 -> 342;
            case 227 -> 343;
            case 228 -> 345;
            case 229 -> 344;
            case 230 -> 346;
            case 231 -> 347;
            default -> -1;
        };
    }

    public static int mouse(int button) {
        return switch (button) {
            case 1 -> 0;
            case 2 -> 2;
            case 3 -> 1;
            default -> button >= 4 && button <= 8 ? button - 1 : -1;
        };
    }

    public static int action(int action) {
        return action == -1 ? 2 : action;
    }

    public static int modifiers(int modifiers) {
        int legacy = 0;
        if ((modifiers & 0x0003) != 0) {
            legacy |= 1;
        }
        if ((modifiers & 0x00c0) != 0) {
            legacy |= 2;
        }
        if ((modifiers & 0x0300) != 0) {
            legacy |= 4;
        }
        if ((modifiers & 0x0c00) != 0) {
            legacy |= 8;
        }
        if ((modifiers & 0x2000) != 0) {
            legacy |= 16;
        }
        if ((modifiers & 0x1000) != 0) {
            legacy |= 32;
        }
        return legacy;
    }

    public static InputConstants.Key parseKey(String name) {
        return InputConstants.getKey(switch (name) {
            case "key.keyboard.keypad.decimal" -> "key.keyboard.keypad.period";
            case "key.keyboard.menu" -> "key.keyboard.application";
            default -> name;
        });
    }

    public static String name(InputConstants.Key key) {
        return switch (key.getName()) {
            case "key.keyboard.keypad.period" -> "key.keyboard.keypad.decimal";
            case "key.keyboard.application" -> "key.keyboard.menu";
            default -> key.getName();
        };
    }

    public static int id(InputConstants.Key key) {
        return key.getType() == InputConstants.Type.MOUSE ? mouse(key.getValue()) : key(key.getValue());
    }
}
