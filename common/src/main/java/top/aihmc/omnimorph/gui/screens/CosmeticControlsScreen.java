package top.aihmc.omnimorph.gui.screens;

import net.minecraft.network.chat.Component;
import top.aihmc.omnimorph.avatar.Avatar;
import top.aihmc.omnimorph.avatar.control.AvatarControlDefinition;
import top.aihmc.omnimorph.avatar.control.AvatarControlType;
import top.aihmc.omnimorph.gui.widgets.library.LibraryDropdown;
import top.aihmc.omnimorph.model.library.LibraryPreviewSession;
import top.aihmc.omnimorph.model.outfit.OutfitComposition;
import top.aihmc.omnimorph.model.outfit.OutfitDraft;
import top.aihmc.omnimorph.utils.FiguraText;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.UUID;

/** Reuses the avatar control widgets while retaining the draft preview and each callback's real owner. */
public final class CosmeticControlsScreen extends AvatarControlsScreen {
    private final WardrobeScreen wardrobe;
    private final LibraryPreviewSession session;
    private final OutfitComposition composition;
    private final OutfitDraft draft;
    private final UUID account;
    private OutfitComposition.ControlGroup group;
    private List<OutfitComposition.ControlGroup> groups;
    private long definitions;
    private boolean returning;
    private final CosmeticAnimationScreen wheelParent;

    public CosmeticControlsScreen(WardrobeScreen wardrobe, LibraryPreviewSession session,
                                  OutfitComposition composition, OutfitDraft draft, UUID account) {
        this(wardrobe, session, composition, draft, account, null,
                composition.controlGroups().getFirst().avatar(), "root");
    }

    CosmeticControlsScreen(WardrobeScreen wardrobe, LibraryPreviewSession session,
                           OutfitComposition composition, OutfitDraft draft, UUID account,
                           CosmeticAnimationScreen wheelParent, Avatar owner, String page) {
        super(wheelParent == null ? wardrobe : wheelParent, owner, page);
        this.wardrobe = wardrobe;
        this.session = session;
        this.composition = composition;
        this.draft = draft;
        this.account = account;
        this.wheelParent = wheelParent;
        groups = composition.controlGroups();
        group = groups.stream().filter(value -> value.avatar() == owner).findFirst().orElse(null);
        definitions = group == null ? owner.controls.definitionRevision() : composition.controlGroupsRevision();
    }

    @Override
    public Component getTitle() {
        return FiguraText.of(group == null ? "gui.controls.title" : "gui.wardrobe.cosmetic_settings");
    }

    @Override
    protected void buildAdditionalControls() {
        if (group == null) {
            return;
        }
        List<OutfitComposition.ControlGroup> choices = groups;
        int selectorWidth = Math.min(360, Math.max(1, width - 40));
        addRenderableWidget(new LibraryDropdown((width - selectorWidth) / 2, 4, selectorWidth,
                () -> choices.stream().map(wardrobe::cosmeticControlName).toList(),
                () -> choices.indexOf(group), selected -> {
            var next = choices.get(selected);
            if (wardrobe.cosmeticControlsActive(composition, draft, account, next)) {
                group = next;
                selectControlAvatar(next.avatar());
            }
        }, 6));
    }

    @Override
    protected boolean controlsActive() {
        if (returning) {
            return false;
        }
        if (group == null) {
            return wheelParent != null && wheelParent.acceptsControls(avatar);
        }
        return wardrobe.cosmeticControlsActive(composition, draft, account, group);
    }

    @Override
    protected void controlsChanged() {
        if (group != null && controlsActive()) {
            wardrobe.saveCosmeticControls(group);
        }
    }

    @Override
    protected void validateControlChange(AvatarControlDefinition control, Object value) {
        if (avatar.cosmeticContext == null) {
            return;
        }
        avatar.cosmeticContext.validateControl(control.id(), value);
        if (control.persistent()) {
            Object normalized = value;
            if ((control.type() == AvatarControlType.SLIDER || control.type() == AvatarControlType.NUMBER)
                    && value instanceof Number number) {
                normalized = Math.max(control.min(), Math.min(control.max(), number.doubleValue()));
            }
            var values = new LinkedHashMap<>(avatar.cosmeticContext.controls());
            values.put(control.id(), normalized);
            draft.validateControls(group.instance().instanceId(), values);
        }
    }

    @Override
    protected boolean includeWheelEditor() {
        // Wheel aggregation is a separate stage-four entry; these widgets edit only cosmetic controls.
        return false;
    }

    @Override
    protected LibraryPreviewSession controlPreview() {
        return session;
    }

    @Override
    public void tick() {
        super.tick();
        session.tick();
        if (!controlsActive()) {
            onClose();
            return;
        }
        long current = group == null ? avatar.controls.definitionRevision() : composition.controlGroupsRevision();
        if (current != definitions) {
            definitions = current;
            groups = composition.controlGroups();
            rebuild();
        }
    }

    @Override
    public void onClose() {
        returning = true;
        if (wheelParent == null) {
            wardrobe.closeCosmeticControls(true);
        } else {
            wheelParent.controlsReturned();
        }
        super.onClose();
    }

    @Override
    public void removed() {
        super.removed();
        if (!returning && !session.isExpanded()) {
            previewAbandoned();
        }
    }

    void previewAbandoned() {
        if (wheelParent == null) {
            wardrobe.closeCosmeticControls(false);
        } else {
            wheelParent.previewAbandoned();
        }
    }
}
