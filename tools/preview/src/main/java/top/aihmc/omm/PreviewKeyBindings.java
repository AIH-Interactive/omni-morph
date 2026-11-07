package top.aihmc.omm;

import java.util.ArrayList;
import java.util.List;

/** Keeps the MOD's original key mappings in the preview host instead of Minecraft options. */
public final class PreviewKeyBindings {
    private static final List<Object> MAPPINGS = new ArrayList<>();

    private PreviewKeyBindings() {
    }

    public static Object register(Object mapping) {
        MAPPINGS.add(mapping);
        return mapping;
    }

    public static List<Object> mappings() {
        return List.copyOf(MAPPINGS);
    }
}
