package top.aihmc.omnimorph.gui.widgets.library;

import net.minecraft.network.chat.Component;
import top.aihmc.omnimorph.utils.FiguraText;
import top.aihmc.omnimorph.utils.FileSizeFormatter;

/** Only built-in values are translation keys; custom category names stay literal. */
public final class LibraryLabels {
    private LibraryLabels() {
    }

    public static String fileSize(long bytes) {
        return FileSizeFormatter.format(bytes, FiguraText.of("gui.library.bytes").getString());
    }

    public static Component category(String id) {
        return switch (id) {
            case "full_model", "decorative_parts" -> FiguraText.of("gui.library.value." + id);
            default -> Component.literal(id);
        };
    }

    public static Component status(String value) {
        return switch (value) {
            case "ACTIVE" -> FiguraText.of("gui.library.value.active");
            case "BLOCKED" -> FiguraText.of("gui.library.value.blocked");
            case "DELETED" -> FiguraText.of("gui.library.value.deleted");
            default -> Component.literal(value);
        };
    }
}
