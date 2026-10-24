package org.figuramc.figura.gui.widgets.lists;

import net.minecraft.util.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.util.Mth;
import net.minecraft.network.chat.Component;
import org.figuramc.figura.avatar.AvatarManager;
import org.figuramc.figura.avatar.local.LocalAvatarFetcher;
import top.aihmc.omnimorph.avatar.local.AvatarTypeFilter;
import org.figuramc.figura.gui.screens.AbstractPanelScreen;
import org.figuramc.figura.gui.screens.AvatarWizardScreen;
import org.figuramc.figura.gui.widgets.Button;
import org.figuramc.figura.gui.widgets.SearchBar;
import top.aihmc.omnimorph.gui.widgets.library.LibraryDropdown;
import org.figuramc.figura.gui.widgets.avatar.AbstractAvatarWidget;
import org.figuramc.figura.gui.widgets.avatar.AvatarFolderWidget;
import org.figuramc.figura.gui.widgets.avatar.AvatarWidget;
import org.figuramc.figura.utils.FiguraIdentifier;
import org.figuramc.figura.utils.FiguraText;
import org.figuramc.figura.utils.ui.UIHelper;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Predicate;

public class AvatarList extends AbstractList {

    // -- Variables -- // 
    private final HashMap<Path, AbstractAvatarWidget> avatars = new HashMap<>();
    private final ArrayList<AbstractAvatarWidget> avatarList = new ArrayList<>();

    private int totalHeight = 0;
    private String filter = "";
    private AvatarTypeFilter typeFilter = AvatarTypeFilter.ALL;
    private final Consumer<LocalAvatarFetcher.AvatarPath> selection;
    private final Predicate<LocalAvatarFetcher.AvatarPath> included;
    private Path previewSelection;
    private boolean sourceBadge;
    private Consumer<LocalAvatarFetcher.AvatarPath> outfitRemoval;
    private SearchBar search;
    private Button formatButton;
    private List<LibraryDropdown> resourceFilters = List.of();
    private Consumer<Boolean> filtersChanged = ignored -> {};
    private boolean filtersExpanded;
    private int contentTop = 49;
    private boolean actionsVisible = true;
    private Button newAvatarButton;
    private Button unselectButton;
    private Button folderButton;

    public static Path selectedEntry;

    // -- Constructors -- // 

    public AvatarList(int x, int y, int width, int height, AbstractPanelScreen parentScreen) {
        this(x, y, width, height, parentScreen, null, ignored -> true);
    }

    public AvatarList(int x, int y, int width, int height, AbstractPanelScreen parentScreen,
                      Consumer<LocalAvatarFetcher.AvatarPath> selection,
                      Predicate<LocalAvatarFetcher.AvatarPath> included) {
        super(x, y, width, height);
        this.selection = selection;
        this.included = included;

        // search bar
        children.add(search = new SearchBar(x + 4, y + 4, width - 32, 20, s -> {
            if (!filter.equals(s))
                scrollBar.setScrollProgress(0f);
            filter = s;
        }));
        children.add(new Button(x + width - 24, y + 4, 20, 20, Component.literal("+"),
                FiguraText.of("gui.library.filters"), button -> {
            filtersExpanded = !filtersExpanded;
            updateHeader();
            filtersChanged.accept(filtersExpanded);
        }));

        // new avatar
        children.add(newAvatarButton = new Button(
                x + width / 2 - 46, y + 28,
                20, 20, 0, 0, 20,
                new FiguraIdentifier("textures/gui/new_avatar.png"),
                60, 20,
                FiguraText.of("gui.wardrobe.new_avatar.tooltip"),
                button -> Minecraft.getInstance().setScreen(new AvatarWizardScreen(parentScreen)))
        );

        // unselect
        children.add(unselectButton = new Button(
                x + width / 2 - 10, y + 28,
                20, 20, 0, 0, 20,
                new FiguraIdentifier("textures/gui/unselect.png"),
                60, 20,
                FiguraText.of("gui.wardrobe.unselect.tooltip"),
                button -> {
                    selectAvatar(null);
                })
        );

        // root folder
        children.add(folderButton = new Button(
                x + width / 2 + 26, y + 28,
                20, 20, 0, 0, 20,
                new FiguraIdentifier("textures/gui/folder.png"),
                60, 20,
                FiguraText.of("gui.wardrobe.folder.tooltip"),
                button -> Util.getPlatform().openUri(LocalAvatarFetcher.getLocalAvatarDirectory().toUri()))
        );

        children.add(formatButton = new LibraryDropdown(x + 4, y + 52, Math.min(128, width - 8),
                () -> java.util.Arrays.stream(AvatarTypeFilter.values())
                        .<Component>map(type -> FiguraText.of(type.translationKey())).toList(),
                () -> typeFilter.ordinal(), index -> {
                    typeFilter = AvatarTypeFilter.values()[index];
                    scrollBar.setScrollProgress(0f);
                    loadContents();
                }));

        updateHeader();

        // initial load
        loadContents();
        scrollToSelected();
    }

    // -- Functions -- // 
    public void setResourceFilters(LibraryDropdown source, LibraryDropdown mode, boolean expanded,
                                   Consumer<Boolean> changed) {
        children.removeAll(resourceFilters);
        resourceFilters = List.of(source, mode);
        children.remove(formatButton);
        children.addAll(resourceFilters);
        children.add(formatButton);
        filtersExpanded = expanded;
        filtersChanged = changed;
        updateHeader();
    }

    private void updateHeader() {
        int actionsHeight = actionsVisible ? 24 : 0;
        contentTop = 25 + actionsHeight + (filtersExpanded ? 24 : 0);
        formatButton.setVisible(filtersExpanded);
        int selectorWidth = Math.min(100, (getWidth() - 16) / 3);
        for (int index = 0; index < resourceFilters.size(); index++) {
            var selector = resourceFilters.get(index);
            selector.setVisible(filtersExpanded);
            selector.setX(getX() + 4 + index * (selectorWidth + 4));
            selector.setY(getY() + 28 + actionsHeight);
            selector.setWidth(selectorWidth);
        }
        int selectorsWidth = resourceFilters.isEmpty() ? 0 : 2 * (selectorWidth + 4);
        formatButton.setX(getX() + 4 + selectorsWidth);
        formatButton.setWidth(Math.min(128, getWidth() - 8 - selectorsWidth));
        formatButton.setY(getY() + 28 + actionsHeight);
        scrollBar.setY(getY() + contentTop - 1);
        scrollBar.setHeight(Math.max(16, getHeight() - contentTop - 3));
        updateScissors(1, contentTop, -2, -contentTop - 1);
    }

    public void setActionsVisible(boolean visible) {
        actionsVisible = visible;
        newAvatarButton.setVisible(visible);
        unselectButton.setVisible(visible);
        folderButton.setVisible(visible);
        updateHeader();
    }

    @Override
    public void tick() {
        // update list
        loadContents();
        super.tick();
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor gui, int mouseX, int mouseY, float delta) {
        int x = getX();
        int y = getY();
        int width = getWidth();
        int height = getHeight();

        // background and scissors
        UIHelper.blitSliced(gui, x, y, width, height, UIHelper.OUTLINE_FILL);
        enableScissors(gui);

        // scrollbar
        totalHeight = 2;
        for (AbstractAvatarWidget avatar : avatarList)
            totalHeight += avatar.getHeight() + 2;
        int entryHeight = avatarList.isEmpty() ? 0 : totalHeight / avatarList.size();

        scrollBar.setVisible(totalHeight > height - contentTop);
        scrollBar.setScrollRatio(entryHeight, totalHeight - (height - contentTop));

        // render list
        int xOffset = scrollBar.isVisible() ? 4 : 11;
        int yOffset = scrollBar.isVisible()
                ? (int) -(Mth.lerp(scrollBar.getScrollProgress(), -contentTop, totalHeight - height)) : contentTop;
        boolean hidden = false;

        for (AbstractAvatarWidget avatar : avatarList) {
            if (hidden) continue;

            avatar.setX(x + xOffset);
            avatar.setY(y + yOffset);

            if (avatar.getY() + avatar.getHeight() > y + scissorsY)
                avatar.extractRenderState(gui, mouseX, mouseY, delta);

            yOffset += avatar.getHeight() + 2;
            if (yOffset > height)
                hidden = true;
        }

        // reset scissor
        gui.disableScissor();

        // render children
        super.extractRenderState(gui, mouseX, mouseY, delta);

        // loading badge
        if (!LocalAvatarFetcher.isLoaded())
            UIHelper.renderLoading(gui, x + width / 2, y + height / 2);
    }

    private void loadContents() {
        LocalAvatarFetcher.reloadAvatars();

        // Load avatars // 
        HashSet<Path> missingPaths = new HashSet<>(avatars.keySet());
        for (LocalAvatarFetcher.AvatarPath avatar : List.copyOf(LocalAvatarFetcher.ALL_AVATARS)) {
            Path path = avatar.getTheActualPathForThis();

            // filter
            if (!matches(avatar, filter))
                continue;

            // update current
            AbstractAvatarWidget widget = avatars.get(path);
            if (widget != null)
                widget.update(avatar, filter);

            // do not remove if passed
            missingPaths.remove(path);

            // add to the avatar list
            avatars.computeIfAbsent(path, p -> {
                int width = this.getWidth() - 22;
                AbstractAvatarWidget entry = avatar instanceof LocalAvatarFetcher.FolderPath folder ? new AvatarFolderWidget(0, width, folder, this) : new AvatarWidget(0, width, avatar, this);

                avatarList.add(entry);
                children.add(entry);

                return entry;
            });
        }

        // remove missing avatars
        for (Path missingPath : missingPaths) {
            AbstractAvatarWidget obj = avatars.remove(missingPath);
            avatarList.remove(obj);
            children.remove(obj);
        }

        // sort lists
        avatarList.sort(AbstractAvatarWidget::compareTo);
        children.sort((children1, children2) -> {
            if (children1 instanceof AbstractAvatarWidget avatar1 && children2 instanceof AbstractAvatarWidget avatar2)
                return avatar1.compareTo(avatar2);
            return 0;
        });
    }

    public boolean matches(LocalAvatarFetcher.AvatarPath avatar, String query) {
        if (avatar instanceof LocalAvatarFetcher.FolderPath folder) {
            return folder.getChildren().stream().anyMatch(child -> matches(child, query));
        }
        return included.test(avatar) && typeFilter.matches(avatar, query);
    }

    public void selectAvatar(LocalAvatarFetcher.AvatarPath avatar) {
        if (selection != null) {
            previewSelection = avatar == null ? null : avatar.getTheActualPathForThis();
            selection.accept(avatar);
        } else {
            AvatarManager.loadLocalAvatar(avatar == null ? null : avatar.getPath());
            selectedEntry = avatar == null ? null : avatar.getTheActualPathForThis();
        }
    }

    public boolean isSelected(LocalAvatarFetcher.AvatarPath avatar) {
        return avatar.getTheActualPathForThis().equals(selection == null ? selectedEntry : previewSelection);
    }

    public void showSourceBadge(boolean visible) {
        sourceBadge = visible;
        for (var widget : avatarList) {
            if (widget instanceof AvatarWidget model) {
                model.updateRowHeight();
            }
        }
    }

    public void setOutfitRemoval(Consumer<LocalAvatarFetcher.AvatarPath> remove) {
        outfitRemoval = remove;
    }

    public boolean canRemoveFromOutfit() {
        return outfitRemoval != null;
    }

    public void removeFromOutfit(LocalAvatarFetcher.AvatarPath avatar) {
        if (outfitRemoval != null) {
            outfitRemoval.accept(avatar);
        }
    }

    public boolean showsSourceBadge() {
        return sourceBadge;
    }

    public record View(String search, AvatarTypeFilter type, double scroll, boolean filtersExpanded) {
        public View(String search, AvatarTypeFilter type, double scroll) {
            this(search, type, scroll, false);
        }
    }

    public View view() {
        return new View(filter, typeFilter, scrollBar.getScrollProgress(), filtersExpanded);
    }

    public void restore(View view, Path selected) {
        previewSelection = selected;
        if (view != null) {
            typeFilter = view.type();
            filtersExpanded = view.filtersExpanded();
            updateHeader();
            search.getField().setValue(view.search());
            loadContents();
            scrollBar.setScrollProgressNoAnim(view.scroll());
        }
    }

    public void updateScroll() {
        // store old scroll pos
        double pastScroll = (totalHeight - getHeight()) * scrollBar.getScrollProgress();

        // get new height
        totalHeight = 2;
        for (AbstractAvatarWidget avatar : avatarList)
            totalHeight += avatar.getHeight() + 2;

        // set new scroll percentage
        scrollBar.setScrollProgress(pastScroll / (totalHeight - getHeight()));
    }

    public void scrollToSelected() {
        double y = 0;

        // get height
        totalHeight = 0;
        for (AbstractAvatarWidget avatar : avatarList) {
            if (avatar.isOf(selectedEntry))
                y = totalHeight;
            else
                totalHeight += avatar.getHeight() + 2;
        }

        // set scroll
        scrollBar.setScrollProgressNoAnim(totalHeight == 0 ? 0 : y / totalHeight);
    }

    @Override
    public List<? extends GuiEventListener> contents() {
        return avatarList;
    }
}
