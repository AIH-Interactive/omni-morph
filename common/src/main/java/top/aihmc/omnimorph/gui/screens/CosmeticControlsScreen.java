package top.aihmc.omnimorph.gui.screens;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import top.aihmc.omnimorph.avatar.Avatar;
import top.aihmc.omnimorph.avatar.control.AvatarControlDefinition;
import top.aihmc.omnimorph.avatar.control.AvatarControlType;
import top.aihmc.omnimorph.gui.widgets.Button;
import top.aihmc.omnimorph.gui.widgets.ScrollBarWidget;
import top.aihmc.omnimorph.model.library.LibraryPreviewSession;
import top.aihmc.omnimorph.model.outfit.OutfitComposition;
import top.aihmc.omnimorph.model.outfit.OutfitConfig;
import top.aihmc.omnimorph.model.outfit.OutfitDraft;
import top.aihmc.omnimorph.utils.FiguraText;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Reuses the avatar control widgets while retaining the draft preview and each callback's real owner.
 * One tab per cosmetic resource lists every instance of that resource, separated by its ordinal.
 */
public final class CosmeticControlsScreen extends AvatarControlsScreen {
    private static final int MAX_TAB_WIDTH = 160;
    private static final int MIN_TAB_WIDTH = 72;
    private static final int TAB_ROW_Y = 4;
    private static final int TAB_ROW_MARGIN = 20;

    private final WardrobeScreen wardrobe;
    private final LibraryPreviewSession session;
    private final OutfitComposition composition;
    private final OutfitDraft draft;
    private final UUID account;
    /** The selected tab, or {@code null} for an owner opened from the wheel without a control group. */
    private OutfitConfig.ResourceRef resource;
    private List<OutfitComposition.ControlGroup> groups;
    private long definitions;
    private boolean returning;
    private final CosmeticAnimationScreen wheelParent;
    private final List<Button> tabButtons = new ArrayList<>();
    private ScrollBarWidget tabScrollbar;
    private int tabScroll;
    private int tabOverflow;
    private int tabRowStart;

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
        var group = groupOf(owner);
        resource = group == null ? null : group.instance().resource();
        definitions = resource == null ? owner.controls.definitionRevision() : composition.controlGroupsRevision();
    }

    /** Groups instances into one tab per cosmetic resource, keeping the composition's instance order. */
    static Map<OutfitConfig.ResourceRef, List<OutfitComposition.ControlGroup>> resourceTabs(
            List<OutfitComposition.ControlGroup> groups) {
        var tabs = new LinkedHashMap<OutfitConfig.ResourceRef, List<OutfitComposition.ControlGroup>>();
        for (var group : groups) {
            tabs.computeIfAbsent(group.instance().resource(), ignored -> new ArrayList<>()).add(group);
        }
        return tabs;
    }

    @Override
    public Component getTitle() {
        return FiguraText.of(resource == null ? "gui.controls.title" : "gui.wardrobe.cosmetic_settings");
    }

    @Override
    protected List<ControlSection> controlSections() {
        if (resource == null) {
            return super.controlSections();
        }
        var sections = new ArrayList<ControlSection>();
        for (var group : resourceTabs(groups).getOrDefault(resource, List.of())) {
            Component header = Component.literal("#" + group.instance().displayOrdinal());
            sections.add(new ControlSection(group.avatar(), header));
        }
        return sections;
    }

    @Override
    protected boolean framedLayout() {
        return resource != null;
    }

    @Override
    protected void buildAdditionalControls() {
        tabButtons.clear();
        tabScrollbar = null;
        if (resource == null) {
            return;
        }
        var tabs = resourceTabs(groups);
        int left = TAB_ROW_MARGIN;
        int right = width - TAB_ROW_MARGIN;
        int available = Math.max(1, right - left);
        int tabWidth = Math.max(MIN_TAB_WIDTH, Math.min(MAX_TAB_WIDTH, available / tabs.size()));
        int rowWidth = tabWidth * tabs.size() - 4;
        tabOverflow = Math.max(0, rowWidth - available);
        tabRowStart = tabOverflow > 0 ? left : left + (available - rowWidth) / 2;
        tabScroll = Math.clamp(tabScroll, 0, tabOverflow);
        int selected = List.copyOf(tabs.keySet()).indexOf(resource) * tabWidth;
        if (selected < tabScroll) {
            tabScroll = selected;
        } else if (selected + tabWidth - 4 > tabScroll + available) {
            tabScroll = Math.min(tabOverflow, selected + tabWidth - 4 - available);
        }
        for (var entry : tabs.entrySet()) {
            var target = entry.getKey();
            var members = entry.getValue();
            Button tab = new Button(left, TAB_ROW_Y, tabWidth - 4, 20, wardrobe.cosmeticResourceName(target), null,
                    button -> selectTab(target, members)) {
                @Override
                public boolean isMouseOver(double mouseX, double mouseY) {
                    return mouseX >= left && mouseX < right && super.isMouseOver(mouseX, mouseY);
                }

                @Override
                public void extractContents(GuiGraphicsExtractor gui, int mouseX, int mouseY, float delta) {
                    gui.enableScissor(left, TAB_ROW_Y, right, TAB_ROW_Y + 20);
                    super.extractContents(gui, mouseX, mouseY, delta);
                    gui.disableScissor();
                }
            };
            tab.setActive(!target.equals(resource));
            tabButtons.add(tab);
            addRenderableWidget(tab);
        }
        updateTabPositions();
        if (tabOverflow > 0) {
            tabScrollbar = new ScrollBarWidget(left, TAB_ROW_Y + 24, available, 10,
                    (double) tabScroll / tabOverflow, false);
            tabScrollbar.setAction(bar -> {
                tabScroll = (int) Math.round(bar.getScrollProgress() * tabOverflow);
                updateTabPositions();
            });
            addRenderableWidget(tabScrollbar);
        }
    }

    private void updateTabPositions() {
        int x = tabRowStart - tabScroll;
        for (Button tab : tabButtons) {
            tab.setX(x);
            x += tab.getWidth() + 4;
        }
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double amount, double horizontalAmount) {
        if (tabScrollbar != null && mouseY < TAB_ROW_Y + 36) {
            tabScrollbar.setScrollProgress(tabScrollbar.getScrollProgress()
                    - (amount + horizontalAmount) * MAX_TAB_WIDTH / 2d / tabOverflow);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, amount, horizontalAmount);
    }

    private void selectTab(OutfitConfig.ResourceRef target, List<OutfitComposition.ControlGroup> members) {
        for (var member : members) {
            if (wardrobe.cosmeticControlsActive(composition, draft, account, member)) {
                resource = target;
                selectControlAvatar(member.avatar());
                return;
            }
        }
    }

    /** The control group of {@code owner}; control callbacks bind {@link #avatar} to the row's owner. */
    private OutfitComposition.ControlGroup groupOf(Avatar owner) {
        for (var group : groups) {
            if (group.avatar() == owner) {
                return group;
            }
        }
        return null;
    }

    @Override
    protected boolean controlsActive() {
        if (returning) {
            return false;
        }
        if (resource == null) {
            return wheelParent != null && wheelParent.acceptsControls(avatar);
        }
        var group = groupOf(avatar);
        return group != null && wardrobe.cosmeticControlsActive(composition, draft, account, group);
    }

    @Override
    protected void controlsChanged() {
        var group = groupOf(avatar);
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
            draft.validateControls(groupOf(avatar).instance().instanceId(), values);
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
        long current = resource == null ? avatar.controls.definitionRevision() : composition.controlGroupsRevision();
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
