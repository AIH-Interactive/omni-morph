package top.aihmc.omnimorph.gui.widgets.library;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import top.aihmc.omnimorph.FiguraMod;
import top.aihmc.omnimorph.backend2.library.LibraryModels;
import top.aihmc.omnimorph.gui.widgets.AbstractContainerElement;
import top.aihmc.omnimorph.gui.widgets.Button;
import top.aihmc.omnimorph.model.library.LibraryThumbnailService;
import top.aihmc.omnimorph.utils.FiguraIdentifier;
import top.aihmc.omnimorph.utils.FiguraText;
import top.aihmc.omnimorph.utils.ui.UIHelper;

import java.util.function.Consumer;

/** One visible card. Its thumbnail belongs to the card and never to the wardrobe texture registry. */
public final class ModelCardWidget extends AbstractContainerElement implements AutoCloseable {
    private static final Identifier PLACEHOLDER = new FiguraIdentifier("textures/gui/library_placeholder.png");
    private static final Identifier AUTHORIZATION = new FiguraIdentifier("textures/gui/library_authorization.png");
    private static final Identifier VISIBILITY = new FiguraIdentifier("textures/gui/library_visibility.png");
    private final LibraryModels.Model model;
    private final LibraryThumbnailService.Handle thumbnail;
    private final Consumer<LibraryModels.Model> select;
    private final Button favorite;
    private final Button report;
    private boolean selected;

    public ModelCardWidget(LibraryModels.Model model, LibraryThumbnailService thumbnails,
                           Consumer<LibraryModels.Model> select, Consumer<LibraryModels.Model> favorite) {
        this(model, thumbnails, select, favorite, ignored -> {});
    }

    public ModelCardWidget(LibraryModels.Model model, LibraryThumbnailService thumbnails,
                           Consumer<LibraryModels.Model> select, Consumer<LibraryModels.Model> favorite,
                           Consumer<LibraryModels.Model> report) {
        super(0, 0, 128, ModelLibraryLayout.CARD_HEIGHT);
        this.model = model;
        this.select = select;
        thumbnail = model.imageAvailable() ? thumbnails.acquire(model.modelId()) : null;
        this.favorite = new Button(0, 0, 20, 18, Component.literal(model.favorited() ? "★" : "☆"),
                FiguraText.of("gui.library.favorite"), button -> favorite.accept(model));
        this.favorite.setActive(model.allows("favorite"));
        children.add(this.favorite);
        this.report = new Button(0, 0, 20, 18, Component.literal("!"), FiguraText.of("gui.library.report"),
                button -> report.accept(model));
        children.add(this.report);
    }

    public void selected(boolean value) {
        selected = value;
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor gui, int mouseX, int mouseY, float delta) {
        int x = getX();
        int y = getY();
        gui.fill(x, y, x + getWidth(), y + getHeight(), selected ? 0xc0335566 : 0xb51a202c);
        int outline = selected ? FiguraMod.getAccentColor().getColor().getValue() : 0x4b5569;
        UIHelper.fillOutline(gui, x, y, getWidth(), getHeight(), UIHelper.adjustColor(outline));
        Identifier texture = thumbnail == null ? null : thumbnail.capture();
        if (texture == null) {
            texture = PLACEHOLDER;
        }
        // Full normalized texture, with dimensions independent of the source image's pixel size.
        int imageSize = Math.max(1, Math.min(getHeight() - 8, (getWidth() - 16) / 2));
        int informationX = x + imageSize + 10;
        int informationWidth = Math.max(1, getWidth() - imageSize - 16);
        gui.blit(RenderPipelines.GUI_TEXTURED, texture, x + 4, y + 4, 0, 0,
                imageSize, imageSize, imageSize, imageSize);
        String badge = switch (model.modelType()) {
            case "YSM_ENCRYPTED" -> "YSM •";
            case "YSM_PLAIN" -> "YSM";
            default -> "Figura";
        };
        int badgeWidth = Math.min(imageSize, Minecraft.getInstance().font.width(badge) + 6);
        gui.fill(x + 4, y + 4, x + 4 + badgeWidth, y + 17, 0xd0101820);
        UIHelper.renderScrollingText(gui, Component.literal(badge), x + 7, y + 7,
                Math.max(1, badgeWidth - 6), 0xffeeeeee);
        int statusX = x + 5;
        int statusY = y + imageSize - 10;
        if (model.requiresAuthorization()) {
            drawStatusIcon(gui, AUTHORIZATION, statusX, statusY, 1);
            statusX += 15;
        }
        if (model.visibility().equals("PRIVATE")) {
            drawStatusIcon(gui, VISIBILITY, statusX, statusY, 0);
            statusX += 15;
        }
        if (model.moderationStatus().equals("BLOCKED")) {
            gui.fill(statusX - 1, statusY - 1, statusX + 13, statusY + 13, 0xd0101820);
            LibraryRowList.drawIcon(gui, LibraryRowList.Icon.BLOCK, statusX + 1, statusY + 1, 0xffeeeeee, 1);
        }
        UIHelper.renderScrollingText(gui, LibraryLabels.category(model.categoryId()), informationX, y + 20,
                informationWidth, 0xffbac6d8);
        favorite.setX(x + getWidth() - 24);
        favorite.setY(y + getHeight() - 20);
        favorite.extractRenderState(gui, mouseX, mouseY, delta);
        report.setX(x + getWidth() - 48);
        report.setY(y + getHeight() - 20);
        report.extractRenderState(gui, mouseX, mouseY, delta);
        UIHelper.renderScrollingText(gui, Component.literal(model.displayName()), informationX, y + 8,
                informationWidth, 0xffeeeeee);
        Component author = model.authors().isEmpty() ? FiguraText.of("gui.library.unknown_author")
                : Component.literal(model.authors().getFirst()).append(model.authors().size() > 1
                ? FiguraText.of("gui.library.more_authors") : Component.empty());
        UIHelper.renderScrollingText(gui, FiguraText.of("gui.library.card_authors", author),
                informationX, y + 34, informationWidth, 0xffbac6d8);
        UIHelper.renderScrollingText(gui, FiguraText.of("gui.library.card_uploader", model.uploader().name()), informationX, y + 46,
                informationWidth, 0xffbac6d8);
        Component counts = FiguraText.of("gui.library.counts", model.downloads(), model.favorites());
        if (model.requiresAuthorization() && !model.allows("equip")) {
            counts = counts.copy().append(" • ").append(FiguraText.of("gui.library.locked"));
        }
        UIHelper.renderScrollingText(gui, counts, informationX, y + 58, informationWidth, 0xffa9bbc9);
        if (isMouseOver(mouseX, mouseY) && !favorite.isMouseOver(mouseX, mouseY) && !report.isMouseOver(mouseX, mouseY)) {
            var tooltip = FiguraText.of("gui.library.card_tooltip", model.internalName(),
                    String.join("\n", model.authors()), model.uploader().name(), model.uploader().uuid().toString());
            tooltip.append("\n").append(FiguraText.of("gui.library.filter.authorization",
                    FiguraText.of(model.requiresAuthorization() ? "gui.yes" : "gui.no")));
            tooltip.append("\n").append(FiguraText.of("gui.library.card_visibility",
                    FiguraText.of(model.visibility().equals("PRIVATE") ? "gui.library.private" : "gui.library.public")));
            if (model.moderationStatus().equals("BLOCKED")) {
                tooltip.append("\n").append(LibraryLabels.status(model.moderationStatus()));
            }
            if (thumbnail != null && thumbnail.ready().isCompletedExceptionally()) {
                tooltip.append("\n").append(FiguraText.of("gui.library.image_retry"));
            }
            UIHelper.setTooltip(tooltip);
        }
    }

    private static void drawStatusIcon(GuiGraphicsExtractor gui, Identifier texture, int x, int y, int variant) {
        gui.fill(x - 1, y - 1, x + 13, y + 13, 0xd0101820);
        gui.blit(RenderPipelines.GUI_TEXTURED, texture, x, y, 24, variant * 24,
                12, 12, 24, 24, 72, 48);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (!isMouseOver(event.x(), event.y())) {
            return false;
        }
        if (event.button() == 1) {
            Minecraft.getInstance().keyboardHandler.setClipboard(model.uploader().uuid().toString());
            return true;
        }
        if (report.isMouseOver(event.x(), event.y())) {
            return report.mouseClicked(event, doubleClick);
        }
        if (favorite.isMouseOver(event.x(), event.y()) && favorite.isActive()) {
            return favorite.mouseClicked(event, doubleClick);
        }
        if (event.button() == 0) {
            select.accept(model);
            return true;
        }
        return false;
    }

    @Override
    public void close() {
        if (thumbnail != null) {
            thumbnail.close();
        }
    }
}
