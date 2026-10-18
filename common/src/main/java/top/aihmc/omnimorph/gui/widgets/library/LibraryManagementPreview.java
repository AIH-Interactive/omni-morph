package top.aihmc.omnimorph.gui.widgets.library;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import top.aihmc.omnimorph.FiguraMod;
import top.aihmc.omnimorph.backend2.library.LibraryClient;
import top.aihmc.omnimorph.gui.widgets.AbstractContainerElement;
import top.aihmc.omnimorph.gui.widgets.EntityPreview;
import top.aihmc.omnimorph.model.library.LibraryPreviewSession;
import top.aihmc.omnimorph.model.library.LibraryTransferService;
import top.aihmc.omnimorph.model.library.LibraryWardrobePreviewService;
import top.aihmc.omnimorph.utils.FiguraText;
import top.aihmc.omnimorph.utils.ui.UIHelper;

import java.util.Locale;
import java.util.UUID;
import java.util.function.Supplier;

/** Shared selection-bound 3D panel for management pages, independent from player equipment. */
public final class LibraryManagementPreview extends AbstractContainerElement implements AutoCloseable {
    private final LibraryPreviewSession session;
    private final LibraryTransferService transfers;
    private final LibraryWardrobePreviewService wardrobe;
    private Component title = FiguraText.of("gui.library.select_model");

    public LibraryManagementPreview(LibraryClient client, Supplier<String> administrator) {
        super(0, 0, 1, 1);
        session = new LibraryPreviewSession(client);
        transfers = new LibraryTransferService(client, FiguraMod.getCacheDirectory(), administrator);
        wardrobe = new LibraryWardrobePreviewService(client, administrator);
    }

    public LibraryManagementPreview place(Screen parent, int x, int y, int width, int height) {
        setX(x);
        setY(y);
        setWidth(width);
        setHeight(height);
        children.clear();
        var entity = new EntityPreview(x, y + 20, width, Math.max(24, height - 36),
                Math.max(12, width / 3), -15, 30, session.entity(), parent);
        entity.setLibraryPreview(session);
        children.add(entity);
        return this;
    }

    public void model(UUID model, Component title) {
        this.title = title;
        session.select(model, transfers);
    }

    public void wardrobe(UUID owner, String asset, long revision, long equipmentRevision, Component title) {
        this.title = title;
        session.selectResource(wardrobe.current(owner, asset, revision, equipmentRevision));
    }

    public void blockedWardrobe(UUID operation, Component title) {
        this.title = title;
        session.selectResource(wardrobe.blocked(operation));
    }

    public void recycledWardrobe(UUID owner, UUID operation, Component title) {
        this.title = title;
        session.selectResource(wardrobe.recycled(owner, operation));
    }

    public void clear() {
        title = FiguraText.of("gui.library.select_model");
        session.clear();
    }

    public boolean isExpanded() {
        return session.isExpanded();
    }

    @Override
    public void tick() {
        session.tick();
        super.tick();
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor gui, int mouseX, int mouseY, float delta) {
        UIHelper.renderScrollingText(gui, title, getX(), getY() + 4, getWidth(), 0xffeeeeee);
        Component state = FiguraText.of("gui.library.preview." + session.status().stage().name().toLowerCase(Locale.ROOT));
        UIHelper.renderScrollingText(gui, state, getX(), getY() + getHeight() - 10, getWidth(), 0xffbac6d8);
        super.extractRenderState(gui, mouseX, mouseY, delta);
    }

    @Override
    public void close() {
        session.close();
    }
}
