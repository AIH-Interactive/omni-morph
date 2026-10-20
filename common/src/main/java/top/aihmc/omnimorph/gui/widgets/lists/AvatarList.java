package top.aihmc.omnimorph.gui.widgets.lists;

import net.minecraft.util.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.util.Mth;
import net.minecraft.network.chat.Component;
import top.aihmc.omnimorph.avatar.AvatarManager;
import top.aihmc.omnimorph.avatar.local.LocalAvatarFetcher;
import top.aihmc.omnimorph.avatar.local.AvatarTypeFilter;
import top.aihmc.omnimorph.gui.screens.AbstractPanelScreen;
import top.aihmc.omnimorph.gui.screens.AvatarWizardScreen;
import top.aihmc.omnimorph.gui.widgets.Button;
import top.aihmc.omnimorph.gui.widgets.SearchBar;
import top.aihmc.omnimorph.gui.widgets.library.LibraryDropdown;
import top.aihmc.omnimorph.gui.widgets.avatar.AbstractAvatarWidget;
import top.aihmc.omnimorph.gui.widgets.avatar.AvatarFolderWidget;
import top.aihmc.omnimorph.gui.widgets.avatar.AvatarWidget;
import top.aihmc.omnimorph.utils.FiguraIdentifier;
import top.aihmc.omnimorph.utils.FiguraText;
import top.aihmc.omnimorph.utils.ui.UIHelper;

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
    private SearchBar search;
    private Button formatButton;
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
    private void updateHeader() {
        int actionsHeight = actionsVisible ? 24 : 0;
        contentTop = 25 + actionsHeight + (filtersExpanded ? 24 : 0);
        formatButton.setVisible(filtersExpanded);
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
