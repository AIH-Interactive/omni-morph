package top.aihmc.omnimorph.gui.screens;

import net.minecraft.client.gui.screens.Screen;
import top.aihmc.omnimorph.FiguraMod;
import top.aihmc.omnimorph.gui.FiguraToast;
import top.aihmc.omnimorph.gui.widgets.Button;
import top.aihmc.omnimorph.gui.widgets.lists.AvatarWizardList;
import top.aihmc.omnimorph.utils.FiguraText;
import top.aihmc.omnimorph.utils.ui.UIHelper;
import top.aihmc.omnimorph.wizards.AvatarWizard;

public class AvatarWizardScreen extends AbstractPanelScreen {

    private final Screen sourcePanel;

    private final AvatarWizard wizard;
    private Button build;
    private Button cancel;
    private boolean building;

    public AvatarWizardScreen(AbstractPanelScreen parentScreen) {
        this(parentScreen, new AvatarWizard());
    }

    public AvatarWizardScreen(AbstractPanelScreen parentScreen, AvatarWizard wizard) {
        super(parentScreen.parentScreen, FiguraText.of("gui.panels.title.avatar_wizard"));
        sourcePanel = parentScreen;
        this.wizard = wizard;
    }

    @Override
    public Class<? extends Screen> getSelectedPanel() {
        return sourcePanel.getClass();
    }

    @Override
    protected void init() {
        super.init();

        // -- bottom buttons -- // 

        // cancel
        cancel = this.addRenderableWidget(new Button(width / 2 - 122, height - 24, 120, 20,
                FiguraText.of("gui.cancel"), null, button -> onClose()));

        // done
        addRenderableWidget(build = new Button(width / 2 + 4, height - 24, 120, 20,
                FiguraText.of("gui.create"), null, button -> startBuild()));

        // -- wizard -- // 

        int width = Math.min(this.width - 8, 420) / 2;
        this.addRenderableWidget(new AvatarWizardList((this.width - width) / 2, formTop(), width,
                height - formTop() - 48, wizard));
        addRenderableOnly((gui, mouseX, mouseY, delta) -> UIHelper.renderScrollingText(gui,
                FiguraText.of("gui.avatar_wizard.target_directory", wizard.targetDirectory().toString()),
                8, height - 42, this.width - 16, 0xffd3e7e1));
    }

    protected int formTop() {
        return 28;
    }

    private void startBuild() {
        if (building || !wizard.canBuild()) {
            return;
        }
        building = true;
        build.setActive(false);
        cancel.setActive(false);
        wizard.buildAsync().whenComplete((ignored, error) -> minecraft.execute(() -> {
            building = false;
            if (error != null) {
                Throwable cause = error;
                while (cause instanceof java.util.concurrent.CompletionException && cause.getCause() != null) {
                    cause = cause.getCause();
                }
                String reason = cause.getMessage() == null ? cause.getClass().getSimpleName() : cause.getMessage();
                FiguraToast.sendToast(FiguraText.of("toast.avatar_wizard.error"),
                        net.minecraft.network.chat.Component.literal(reason), FiguraToast.ToastType.ERROR);
                FiguraMod.LOGGER.error("Avatar creation failed", error);
                // Preserve the wizard and every entered field so the user can correct/retry it.
                return;
            }
            FiguraToast.sendToast(FiguraText.of("toast.avatar_wizard.success"));
            if (minecraft.screen == this) {
                onClose();
            }
        }));
    }

    @Override
    public void onClose() {
        if (!building) {
            this.minecraft.setScreen(sourcePanel);
        }
    }

    @Override
    public void tick() {
        super.tick();
        build.setActive(!building && wizard.canBuild());
        cancel.setActive(!building);
    }
}
