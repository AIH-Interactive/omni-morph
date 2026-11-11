package top.aihmc.omnimorph.gui.widgets.library;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.figuramc.figura.FiguraMod;
import org.figuramc.figura.gui.widgets.AbstractContainerElement;
import org.figuramc.figura.gui.widgets.ContextMenu;
import org.figuramc.figura.utils.FiguraIdentifier;
import org.figuramc.figura.utils.FiguraText;
import org.figuramc.figura.utils.ui.UIHelper;
import top.aihmc.omnimorph.backend2.library.LibraryModels;
import top.aihmc.omnimorph.gui.widgets.ModelResourceBadges;
import top.aihmc.omnimorph.model.library.LibraryThumbnailService;

import java.util.function.Consumer;

/**
 * One visible catalog card. Its thumbnail belongs to the card and never to the wardrobe texture registry.
 * The static helpers draw the fixed card face shared with the wardrobe collection (Penpot 54): a square
 * cover with the type badge and status icons, and a name row with a three-dot menu.
 */
public final class ModelCardWidget extends AbstractContainerElement implements AutoCloseable {
    private static final ResourceLocation PLACEHOLDER = FiguraIdentifier.of("textures/gui/library_placeholder.png");
    private static final ResourceLocation AUTHORIZATION = FiguraIdentifier.of("textures/gui/library_authorization.png");
    private static final ResourceLocation VISIBILITY = FiguraIdentifier.of("textures/gui/library_visibility.png");
    private static final int MENU_WIDTH = 14;
    private static final int MENU_HEIGHT = 12;
    private static final int STATUS_STEP = 15;

    private final LibraryModels.Model model;
    private final LibraryThumbnailService.Handle thumbnail;
    private final Consumer<LibraryModels.Model> select;
    private final Consumer<LibraryModels.Model> favorite;
    private final Consumer<LibraryModels.Model> report;
    private boolean selected;

    public ModelCardWidget(LibraryModels.Model model, LibraryThumbnailService thumbnails,
                           Consumer<LibraryModels.Model> select, Consumer<LibraryModels.Model> favorite,
                           Consumer<LibraryModels.Model> report) {
        super(0, 0, ModelLibraryLayout.CARD_WIDTH, ModelLibraryLayout.CARD_HEIGHT);
        this.model = model;
        this.select = select;
        this.favorite = favorite;
        this.report = report;
        thumbnail = model.imageAvailable() ? thumbnails.acquire(model.modelId()) : null;
    }

    public void selected(boolean value) {
        selected = value;
    }

    @Override
    public void render(GuiGraphics gui, int mouseX, int mouseY, float delta) {
        ResourceLocation texture = thumbnail == null ? null : thumbnail.capture();
        drawFace(gui, getX(), getY(), texture, model.modelType(), model, Component.literal(model.displayName()),
                selected, mouseX, mouseY);
        if (isMouseOver(mouseX, mouseY) && !isOnMenu(getX(), getY(), mouseX, mouseY)) {
            var tooltip = FiguraText.of("gui.library.card_tooltip", model.internalName(),
                    String.join("\n", model.authors()), model.uploader().name(), model.uploader().uuid().toString());
            tooltip.append("\n").append(ModelResourceBadges.typeTooltip(model.modelType()));
            tooltip.append("\n").append(statusTooltip(model));
            tooltip.append("\n").append(FiguraText.of("gui.library.counts", model.downloads(), model.favorites()));
            if (thumbnail != null && thumbnail.ready().isCompletedExceptionally()) {
                tooltip.append("\n").append(FiguraText.of("gui.library.image_retry"));
            }
            UIHelper.setTooltip(tooltip);
        }
    }

    /**
     * Draws the fixed card face at its top-left corner. {@code model} is null for local entries, which
     * have no status icons and no report or favorite menu.
     */
    public static void drawFace(GuiGraphics gui, int x, int y, ResourceLocation cover, String format,
                                LibraryModels.Model model, Component name, boolean selected,
                                int mouseX, int mouseY) {
        int width = ModelLibraryLayout.CARD_WIDTH;
        int height = ModelLibraryLayout.CARD_HEIGHT;
        int coverSize = ModelLibraryLayout.COVER_SIZE;
        gui.fill(x, y, x + width, y + height, selected ? 0xc0335566 : 0xb51a202c);
        // Full normalized texture, with dimensions independent of the source image's pixel size.
        gui.blit(cover == null ? PLACEHOLDER : cover, x, y, 0, 0,
                coverSize, coverSize, coverSize, coverSize);
        int outline = selected ? FiguraMod.getAccentColor().getColor().getValue() : 0x4b5569;
        UIHelper.fillOutline(gui, x, y, width, height, UIHelper.adjustColor(outline));
        gui.fill(x + 1, y + coverSize, x + width - 1, y + coverSize + 1, 0xff27364a);

        Font font = Minecraft.getInstance().font;
        ModelResourceBadges.drawType(gui, x + 4, y + 4, format, 14);
        Component typeName = typeName(format);
        if (typeName != null) {
            gui.drawString(font, typeName, x + 21, y + 7, 0xffcbd5e1, false);
        }
        if (model != null) {
            drawStatusIcons(gui, x + 5, y + coverSize - 16, model);
        }

        int menuX = x + width - MENU_WIDTH - 3;
        int menuY = y + coverSize + (ModelLibraryLayout.NAME_ROW_HEIGHT - MENU_HEIGHT) / 2;
        int nameWidth = (model == null ? x + width : menuX) - x - 8;
        gui.drawString(font, ellipsize(font, name, nameWidth), x + 5, y + coverSize + 5, 0xffeeeeee, false);
        if (model == null) {
            return;
        }
        boolean menuHovered = isOnMenu(x, y, mouseX, mouseY);
        gui.fill(menuX, menuY, menuX + MENU_WIDTH, menuY + MENU_HEIGHT, menuHovered ? 0xff2a3b55 : 0xff1c2a3e);
        for (int dot = 0; dot < 3; dot++) {
            int dotY = menuY + 2 + dot * 3;
            gui.fill(menuX + 6, dotY, menuX + 8, dotY + 2, 0xffcbd5e1);
        }
        if (menuHovered) {
            UIHelper.setTooltip(FiguraText.of("gui.library.card_menu.more"));
        }
    }

    /** True when the pointer is on the three-dot menu button of a card drawn at {@code x, y}. */
    public static boolean isOnMenu(int x, int y, double mouseX, double mouseY) {
        int menuX = x + ModelLibraryLayout.CARD_WIDTH - MENU_WIDTH - 3;
        int menuY = y + ModelLibraryLayout.COVER_SIZE + (ModelLibraryLayout.NAME_ROW_HEIGHT - MENU_HEIGHT) / 2;
        return mouseX >= menuX && mouseX < menuX + MENU_WIDTH && mouseY >= menuY && mouseY < menuY + MENU_HEIGHT;
    }

    /**
     * Opens the "report / favorite" text menu below the three-dot button of a card drawn at {@code x, y}.
     * The actions keep their existing permission checks in the owners of the callbacks.
     */
    public static void openMenu(int x, int y, LibraryModels.Model model, Consumer<LibraryModels.Model> report,
                                Consumer<LibraryModels.Model> favorite) {
        var menu = new ContextMenu();
        menu.addAction(FiguraText.of("gui.library.card_menu.report"), FiguraText.of("gui.library.report"),
                button -> report.accept(model));
        menu.addAction(FiguraText.of("gui.library.card_menu.favorite"),
                FiguraText.of(model.favorited() ? "gui.library.unfavorite" : "gui.library.favorite"), button -> {
                    if (model.allows("favorite")) {
                        favorite.accept(model);
                    }
                });
        menu.setX(x + ModelLibraryLayout.CARD_WIDTH - MENU_WIDTH - 3);
        menu.setY(y + ModelLibraryLayout.CARD_HEIGHT);
        menu.setVisible(true);
        UIHelper.setContext(menu);
    }

    private static Component typeName(String format) {
        if ("FIGURA".equals(format)) {
            return Component.literal("Figura");
        }
        if (format != null && format.startsWith("YSM_")) {
            return Component.literal("YSM");
        }
        return null;
    }

    /** Long names end with an ellipsis instead of pushing the menu button; the tooltip has the full name. */
    private static Component ellipsize(Font font, Component name, int width) {
        String text = name.getString();
        if (font.width(text) <= width) {
            return name;
        }
        String ellipsis = "…";
        String kept = font.plainSubstrByWidth(text, Math.max(0, width - font.width(ellipsis)));
        return Component.literal(kept + ellipsis);
    }

    /**
     * Status icons only, in design order: blocked, requires authorization, then private or public.
     * Their meaning is described by the card tooltip.
     */
    public static void drawStatusIcons(GuiGraphics gui, int x, int y, LibraryModels.Model model) {
        int statusX = x;
        if (model.moderationStatus().equals("BLOCKED")) {
            gui.fill(statusX - 1, y - 1, statusX + 13, y + 13, 0xd0101820);
            LibraryRowList.drawIcon(gui, LibraryRowList.Icon.BLOCK, statusX + 1, y + 1, 0xffeeeeee, 1);
            statusX += STATUS_STEP;
        }
        if (model.requiresAuthorization()) {
            drawStatusIcon(gui, AUTHORIZATION, statusX, y, 1);
            statusX += STATUS_STEP;
        }
        boolean isPrivate = model.visibility().equals("PRIVATE");
        drawStatusIcon(gui, VISIBILITY, statusX, y, isPrivate ? 0 : 1);
    }

    /** Text for the status icons, shown through the card tooltip. */
    public static Component statusTooltip(LibraryModels.Model model) {
        var text = FiguraText.of("gui.library.card_visibility",
                FiguraText.of(model.visibility().equals("PRIVATE") ? "gui.library.private" : "gui.library.public"));
        text.append("\n").append(FiguraText.of("gui.library.filter.authorization",
                FiguraText.of(model.requiresAuthorization() ? "gui.yes" : "gui.no")));
        if (model.moderationStatus().equals("BLOCKED")) {
            text.append("\n").append(LibraryLabels.status(model.moderationStatus()));
        }
        return text;
    }

    /**
     * Wardrobe preview visibility indicator, reusing the card visibility icon: the open eye when the
     * equipment is shown to others, the crossed-out eye when hidden, dimmed when unknown.
     */
    public static void drawPreviewVisibility(GuiGraphics gui, int x, int y, Boolean showing) {
        drawStatusIcon(gui, VISIBILITY, x + 1, y + 1, showing == null || showing ? 1 : 0);
        if (showing == null) {
            gui.fill(x, y, x + 14, y + 14, 0x99101820);
        }
    }

    private static void drawStatusIcon(GuiGraphics gui, ResourceLocation texture, int x, int y, int variant) {
        gui.fill(x - 1, y - 1, x + 13, y + 13, 0xd0101820);
        gui.blit(texture, x, y, 12, 12, 24f, variant * 24f, 24, 24, 72, 48);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (!isMouseOver(mouseX, mouseY)) {
            return false;
        }
        if (button == 1) {
            Minecraft.getInstance().keyboardHandler.setClipboard(model.uploader().uuid().toString());
            return true;
        }
        if (button != 0) {
            return false;
        }
        if (isOnMenu(getX(), getY(), mouseX, mouseY)) {
            openMenu(getX(), getY(), model, report, favorite);
        } else {
            select.accept(model);
        }
        return true;
    }

    @Override
    public void close() {
        if (thumbnail != null) {
            thumbnail.close();
        }
    }
}
