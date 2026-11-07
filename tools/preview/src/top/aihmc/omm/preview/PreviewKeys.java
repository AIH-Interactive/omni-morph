package top.aihmc.omm.preview;

import java.lang.reflect.Field;
import java.util.Locale;
import org.luaj.vm2.LuaError;
import org.lwjgl.glfw.GLFW;

/** Minecraft key identifiers mapped to the actual GLFW input backend. */
public final class PreviewKeys {
    public enum Type { KEYBOARD, MOUSE, SCANCODE }
    public record Key(String name, int value, Type type) {
        public String getName() {
            return name;
        }
        public int getValue() {
            return value;
        }
        public String getDisplayName() {
            return name;
        }
        @Override
        public boolean equals(Object value) {
            return value instanceof Key other && this.value == other.value && type == other.type;
        }
        @Override
        public int hashCode() {
            return 31 * type.hashCode() + value;
        }
    }

    private PreviewKeys() {}

    public static Key parse(String name) {
        try {
            if (name.startsWith("scancode.")) {
                int value = Integer.parseInt(name.substring("scancode.".length()));
                if (value < 0)
                    throw new IllegalArgumentException();
                return new Key(name, value, Type.SCANCODE);
            }
            if (name.startsWith("key.mouse.")) {
                String button = name.substring("key.mouse.".length());
                int code = switch (button) {
                    case "left" -> 0;
                    case "right" -> 1;
                    case "middle" -> 2;
                    default -> Integer.parseInt(button) - 1;
                };
                if (code < 0 || code > GLFW.GLFW_MOUSE_BUTTON_LAST)
                    throw new IllegalArgumentException();
                return new Key(name, code, Type.MOUSE);
            }
            if (!name.startsWith("key.keyboard."))
                throw new IllegalArgumentException();
            String token = name.substring("key.keyboard.".length()).toUpperCase(Locale.ROOT).replace('.', '_');
            if (token.startsWith("KEYPAD_"))
                token = "KP_" + token.substring("KEYPAD_".length());
            int code = GLFW.class.getField("GLFW_KEY_" + token).getInt(null);
            return new Key(name, code, Type.KEYBOARD);
        } catch (ReflectiveOperationException | IllegalArgumentException error) {
            throw new LuaError("Invalid key: " + name);
        }
    }

    static String keyboardName(int code, int scancode) {
        if (code == GLFW.GLFW_KEY_UNKNOWN)
            return "scancode." + scancode;
        for (Field field : GLFW.class.getFields()) {
            if (field.getName().startsWith("GLFW_KEY_") && !field.getName().equals("GLFW_KEY_LAST")) {
                try {
                    if (field.getInt(null) == code) {
                        String token = field.getName().substring("GLFW_KEY_".length()).toLowerCase(Locale.ROOT);
                        if (token.startsWith("kp_"))
                            token = "keypad_" + token.substring(3);
                        return "key.keyboard." + token.replace('_', '.');
                    }
                } catch (IllegalAccessException error) {
                    throw new IllegalStateException(error);
                }
            }
        }
        throw new IllegalArgumentException("Unknown GLFW keyboard code: " + code);
    }

    static String mouseName(int button) {
        return "key.mouse." + switch (button) {
            case 0 -> "left";
            case 1 -> "right";
            case 2 -> "middle";
            default -> Integer.toString(button + 1);
        };
    }
}
