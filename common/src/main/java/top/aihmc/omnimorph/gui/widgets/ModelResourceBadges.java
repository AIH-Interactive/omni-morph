package top.aihmc.omnimorph.gui.widgets;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import top.aihmc.omnimorph.utils.FiguraIdentifier;
import top.aihmc.omnimorph.utils.FiguraText;

/** Source is independent of model format and the catalog's public/private visibility. */
public final class ModelResourceBadges {
    private static final Identifier LOCAL = new FiguraIdentifier("textures/gui/wardrobe_source_local.png");
    private static final Identifier CLOUD = new FiguraIdentifier("textures/gui/wardrobe_source_cloud.png");
    private static final Identifier FIGURA = new FiguraIdentifier("textures/gui/wardrobe_type_figura.png");
    private static final Identifier YSM = new FiguraIdentifier("textures/gui/wardrobe_type_ysm.png");

    private ModelResourceBadges() {}

    public static void draw(GuiGraphicsExtractor gui, int x, int y, boolean cloud) {
        gui.fill(x, y, x + 10, y + 10, 0xe0101820);
        gui.blit(RenderPipelines.GUI_TEXTURED, cloud ? CLOUD : LOCAL, x, y, 24, 0,
                10, 10, 24, 24, 72, 24);
    }

    public static Component tooltip(Component title, boolean cloud, String format) {
        return title.copy().append("\n").append(FiguraText.of("gui.wardrobe.resource_source",
                FiguraText.of(cloud ? "gui.wardrobe.cloud" : "gui.wardrobe.local")))
                .append("\n").append(typeTooltip(format));
    }

    public static Component typeTooltip(String format) {
        String type = switch (format) {
            case "FIGURA" -> "gui.wardrobe.filter.figura";
            case "YSM_PLAIN" -> "gui.wardrobe.filter.ysm_plain";
            case "YSM_ENCRYPTED" -> "gui.wardrobe.filter.ysm_encrypted";
            default -> "gui.wardrobe.unavailable";
        };
        return FiguraText.of("gui.wardrobe.resource_type", FiguraText.of(type));
    }

    public static void drawType(GuiGraphicsExtractor gui, int x, int y, String format) {
        drawType(gui, x, y, format, 10);
    }

    public static void drawType(GuiGraphicsExtractor gui, int x, int y, String format, int size) {
        boolean figura = format.equals("FIGURA");
        if (!figura && !format.startsWith("YSM_")) {
            return;
        }
        int cell = figura ? 20 : 24;
        gui.fill(x, y, x + size, y + size, 0xe0101820);
        gui.blit(RenderPipelines.GUI_TEXTURED, figura ? FIGURA : YSM, x, y, cell, 0,
                size, size, cell, cell, cell * 3, cell);
    }
}
