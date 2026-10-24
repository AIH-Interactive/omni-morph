package top.aihmc.omnimorph.gui.screens;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.figuramc.figura.avatar.Avatar;
import org.figuramc.figura.config.Configs;
import org.figuramc.figura.gui.ActionWheel;
import org.figuramc.figura.gui.widgets.Button;
import org.figuramc.figura.gui.widgets.EntityPreview;
import top.aihmc.omnimorph.model.library.LibraryPreviewSession;
import top.aihmc.omnimorph.model.outfit.OutfitActionWheel;
import top.aihmc.omnimorph.model.outfit.OutfitComposition;
import top.aihmc.omnimorph.model.outfit.OutfitDraft;
import org.figuramc.figura.utils.FiguraText;
import org.figuramc.figura.utils.ui.UIHelper;

import java.util.UUID;

import org.figuramc.figura.gui.screens.AbstractPanelScreen;
import org.figuramc.figura.gui.screens.WardrobeScreen;
/** A private draft wheel: native artwork and owner callbacks, without activating the equipped wheel. */
public final class CosmeticAnimationScreen extends Screen {
    private final WardrobeScreen wardrobe;
    private final LibraryPreviewSession session;
    private final OutfitComposition composition;
    private final OutfitDraft draft;
    private final UUID account;
    private final OutfitActionWheel wheel;
    private int heldInput = -1;
    private boolean returning;
    private boolean retainingControls;
    private Component failure = Component.empty();

    public CosmeticAnimationScreen(WardrobeScreen wardrobe, LibraryPreviewSession session,
                                   OutfitComposition composition, OutfitDraft draft, UUID account) {
        super(FiguraText.of("gui.wardrobe.cosmetic_animations"));
        this.wardrobe = wardrobe;
        this.session = session;
        this.composition = composition;
        this.draft = draft;
        this.account = account;
        wheel = composition.actionWheel();
    }

    private boolean active() {
        return wardrobe.cosmeticAnimationsActive(composition, draft, account);
    }

    private int modelWidth() {
        return Math.clamp(width / 3, 96, 240);
    }

    private int centerX() {
        return modelWidth() + (width - modelWidth()) / 2;
    }

    private int centerY() {
        return height / 2;
    }

    @Override
    protected void init() {
        super.init();
        int previewHeight = Math.max(24, height - 88);
        var preview = new EntityPreview(8, 40, modelWidth() - 16, previewHeight,
                Math.min(modelWidth() * .35f, previewHeight * .35f), 0, 0, session.entity(), this);
        preview.setLibraryPreview(session);
        addRenderableWidget(preview);
        addRenderableWidget(new Button(width / 2 - 70, height - 28, 140, 20,
                Component.translatable("gui.done"), null, button -> onClose()));
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor gui, int mouseX, int mouseY, float delta) {
        float speed = Configs.BACKGROUND_SCROLL_SPEED.tempValue * .125f;
        for (var background : AbstractPanelScreen.BACKGROUNDS) {
            UIHelper.renderAnimatedBackground(gui, background, 0, 0, width, height, 64, 64, speed, delta);
            speed *= 2;
        }
        gui.nextStratum();
        super.extractRenderState(gui, mouseX, mouseY, delta);
        gui.text(font, getTitle(), 12, 12, 0xffffffff);
        if (active()) {
            ActionWheel.renderPreview(gui, wheel.page(session.outfitBase()), centerX(), centerY(), mouseX, mouseY);
        }
        UIHelper.renderScrollingText(gui, failure.getString().isEmpty()
                ? FiguraText.of("gui.wardrobe.cosmetic_animations_help") : failure,
                12, height - 42, Math.max(1, width - 24), 0xffd3e7e1);
    }

    private int selected(double mouseX, double mouseY) {
        return ActionWheel.previewSelection(wheel.page(session.outfitBase()), centerX(), centerY(), mouseX, mouseY);
    }

    private void execute(int index, boolean left, int input) {
        if (!active() || index < 0 || index >= 8) {
            return;
        }
        Avatar owner = wheel.selectedOwner(session.outfitBase());
        if (!ownerActive(owner)) {
            release();
            return;
        }
        if (left) {
            if (heldInput != input) {
                release();
            }
            heldInput = input;
        }
        wheel.execute(session.outfitBase(), index, left);
        save(owner);
        if (minecraft.screen != this) {
            release();
        }
    }

    private void save(Avatar owner) {
        try {
            wardrobe.saveAnimationControls(composition, draft, account, owner);
            failure = Component.empty();
        } catch (IllegalArgumentException invalid) {
            failure = FiguraText.of("gui.library.failure", "OUTFIT_DRAFT_LIMIT_OR_BINDING");
        }
    }

    private boolean ownerActive(Avatar owner) {
        return wardrobe.animationOwnerActive(composition, draft, account, owner);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (super.mouseClicked(event, doubleClick)) {
            return true;
        }
        if (event.button() == 0 || event.button() == 1) {
            execute(selected(event.x(), event.y()), event.button() == 0, event.button());
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        if (heldInput == event.button()) {
            release();
        }
        return super.mouseReleased(event);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (event.key() >= 49 && event.key() <= 56) {
            if (heldInput != event.key()) {
                execute(event.key() - 49, true, event.key());
            }
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public boolean keyReleased(KeyEvent event) {
        if (heldInput == event.key()) {
            release();
            return true;
        }
        return super.keyReleased(event);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double amount, double horizontalAmount) {
        if (mouseX < modelWidth() && super.mouseScrolled(mouseX, mouseY, amount, horizontalAmount)) {
            return true;
        }
        if (active()) {
            Avatar owner = wheel.selectedOwner(session.outfitBase());
            if (!ownerActive(owner)) {
                release();
                return true;
            }
            wheel.scroll(session.outfitBase(), selected(mouseX, mouseY), amount + horizontalAmount);
            save(owner);
        }
        return true;
    }

    boolean openControls(Avatar owner, String page) {
        if (!acceptsControls(owner)) {
            return false;
        }
        release();
        retainingControls = true;
        minecraft.setScreen(new CosmeticControlsScreen(wardrobe, session, composition, draft, account,
                this, owner, page));
        return true;
    }

    boolean acceptsControls(Avatar owner) {
        return owner != null && ownerActive(owner) && (owner == session.outfitBase()
                || composition.controlGroups().stream().anyMatch(group -> group.avatar() == owner));
    }

    void controlsReturned() {
        retainingControls = false;
    }

    private void release() {
        heldInput = -1;
        wheel.release();
    }

    @Override
    public void tick() {
        super.tick();
        session.tick();
        if (!active() || !wheel.available() || !ownerActive(wheel.selectedOwner(session.outfitBase()))) {
            onClose();
            return;
        }
        save(wheel.selectedOwner(session.outfitBase()));
    }

    @Override
    public void onClose() {
        returning = true;
        release();
        wardrobe.closeCosmeticAnimations(true);
        minecraft.setScreen(wardrobe);
    }

    @Override
    public void removed() {
        release();
        super.removed();
        if (!returning && !retainingControls && !session.isExpanded()) {
            wardrobe.closeCosmeticAnimations(false);
        }
    }

    public void previewAbandoned() {
        wardrobe.closeCosmeticAnimations(false);
    }
}
