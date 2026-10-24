package top.aihmc.omnimorph.gui.screens;

import net.minecraft.network.chat.Component;
import top.aihmc.omnimorph.gui.widgets.library.LibraryDropdown;
import top.aihmc.omnimorph.model.outfit.OutfitConfig;
import org.figuramc.figura.utils.FiguraText;
import top.aihmc.omnimorph.wizards.CosmeticTemplate;
import top.aihmc.omnimorph.wizards.CosmeticWizard;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.figuramc.figura.gui.screens.AbstractPanelScreen;
import org.figuramc.figura.gui.screens.AvatarWizardScreen;
public final class CosmeticWizardScreen extends AvatarWizardScreen {
    private final CosmeticWizard cosmetic;

    public CosmeticWizardScreen(AbstractPanelScreen parent) {
        this(parent, new CosmeticWizard());
    }

    public CosmeticWizardScreen(AbstractPanelScreen parent, CosmeticWizard cosmetic) {
        super(parent, cosmetic);
        this.cosmetic = cosmetic;
    }

    @Override
    public Component getTitle() {
        return FiguraText.of("gui.wardrobe.create_cosmetic");
    }

    @Override
    protected int formTop() {
        return 76;
    }

    @Override
    protected void init() {
        super.init();
        int width = Math.min(this.width - 8, 420) / 2;
        int x = (this.width - width) / 2;
        addRenderableWidget(new LibraryDropdown(x, 28, width,
                () -> Arrays.stream(CosmeticTemplate.values())
                        .map(value -> (Component) FiguraText.of("gui.cosmetic_wizard.category." + value.category())).toList(),
                () -> cosmetic.template().ordinal(), option -> {
                    cosmetic.template(CosmeticTemplate.values()[option]);
                    rebuildWidgets();
                }));
        addRenderableWidget(new LibraryDropdown(x, 52, width,
                () -> anchors().stream().map(value -> (Component) Component.literal(value)).toList(),
                () -> anchors().indexOf(cosmetic.anchor()), option -> {
                    cosmetic.anchor(anchors().get(option));
                    rebuildWidgets();
                }));
    }

    private List<String> anchors() {
        var anchors = new ArrayList<>(OutfitConfig.STANDARD_ANCHORS.stream().sorted().toList());
        anchors.add("tailtip");
        if (!anchors.contains(cosmetic.anchor())) {
            anchors.add(cosmetic.anchor());
        }
        return List.copyOf(anchors);
    }
}
