package top.aihmc.omnimorph.gui.screens;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.*;
import top.aihmc.omnimorph.FiguraMod;
import top.aihmc.omnimorph.avatar.Avatar;
import top.aihmc.omnimorph.avatar.AvatarManager;
import top.aihmc.omnimorph.avatar.local.LocalAvatarFetcher;
import top.aihmc.omnimorph.backend2.NetworkStuff;
import top.aihmc.omnimorph.config.Configs;
import top.aihmc.omnimorph.gui.FiguraToast;
import top.aihmc.omnimorph.gui.widgets.*;
import top.aihmc.omnimorph.gui.widgets.lists.AvatarList;
import top.aihmc.omnimorph.utils.FiguraIdentifier;
import top.aihmc.omnimorph.utils.FiguraText;
import top.aihmc.omnimorph.utils.IOUtils;
import top.aihmc.omnimorph.utils.TextUtils;
import top.aihmc.omnimorph.utils.ui.UIHelper;

import java.net.URI;
import java.nio.file.Path;
import java.util.List;

public class WardrobeScreen extends AbstractPanelScreen {
    private static final Component DEBUG_MOTD_FALLBACK = Component.literal("No motd could be loaded.\n\n")
            .append("Lorem ipsum dolor sit amet, consectetur adipiscing elit, sed do eiusmod tempor incididunt ut labore et dolore magna aliqua. Ut enim ad minim veniam, quis nostrud exercitation ullamco laboris nisi ut aliquip ex ea commodo consequat. Duis aute irure dolor in reprehenderit in voluptate velit esse cillum dolore eu fugiat nulla pariatur. Excepteur sint occaecat cupidatat non proident, sunt in culpa qui officia deserunt mollit anim id est laborum.\n")
                    .withStyle(ChatFormatting.GRAY)
            .append(Component.literal("(This is some text you can hover)\n")
                    .withStyle(Style.EMPTY.withColor(0xFFF311A0).withHoverEvent(new HoverEvent.ShowText(Component.literal("hi chat")))))
            .append(Component.literal("(This is some text you can click on)\n")
                    .withStyle(Style.EMPTY.withClickEvent(new ClickEvent.OpenUrl(URI.create("https://github.com/FiguraMC/Figura")))))
            .append(Component.literal("(This is only visible in debug mode)")
                    .withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));

    private Label panic;

    private Button upload, delete, back;

    public WardrobeScreen(Screen parentScreen) {
        super(parentScreen, FiguraText.of("gui.panels.title.wardrobe"));
    }

    private AvatarInfoWidget infoWidget;
    private BackendMotdWidget motdWidget;

    @Override
    protected void init() {
        super.init();

        // screen
        Minecraft minecraft = Minecraft.getInstance();
        int middle = width / 2;
        int panels = getPanels();

        int modelBgSize = Math.min(width - panels * 2 - 16, height - 96);
        panels = Math.max((width - modelBgSize) / 2 - 8, panels);

        // -- left -- // 

        AvatarList avatarList = new AvatarList(4, 28, panels, height - 32, this);
        addRenderableWidget(avatarList);

        // -- middle -- // 

        // model
        int entitySize = 11 * modelBgSize / 29;
        int entityX = middle - modelBgSize / 2;
        int entityY = this.height / 2 - modelBgSize / 2;

        EntityPreview entity = new EntityPreview(entityX, entityY, modelBgSize, modelBgSize, entitySize, -15f, 30f, minecraft.player, this);
        addRenderableWidget(entity);

        int buttX = entity.getX() + entity.getWidth() / 2;
        int buttY = entity.getY() + entity.getHeight() + 4;

        // upload
        addRenderableWidget(upload = new Button(buttX - 48, buttY, 24, 24, 0, 0, 24, new FiguraIdentifier("textures/gui/upload.png"), 72, 24, FiguraText.of("gui.wardrobe.upload.tooltip"), button -> {
            Avatar avatar = AvatarManager.getAvatarForPlayer(FiguraMod.getLocalPlayerUUID());
            NetworkStuff.uploadAvatar(avatar);
        }));
        upload.setActive(false);

        // reload
        addRenderableWidget(new Button(buttX - 12, buttY, 24, 24, 0, 0, 24, new FiguraIdentifier("textures/gui/reload.png"), 72, 24, FiguraText.of("gui.wardrobe.reload.tooltip"), button -> {
            AvatarManager.reloadCloudAvatar();
        }));

        // delete
        addRenderableWidget(delete = new Button(buttX + 24, buttY, 24, 24, 0, 0, 24, new FiguraIdentifier("textures/gui/delete.png"), 72, 24, FiguraText.of("gui.wardrobe.delete.tooltip"), button ->
                NetworkStuff.deleteAvatar(null))
        );
        delete.setActive(false);

        addRenderableOnly((gui, mouseX, mouseY, delta) -> renderUploadProgress(gui,
                entity.getX() + 8, entity.getY() + entity.getHeight() - 26, entity.getWidth() - 16));

        StatusWidget statusWidget = new StatusWidget(entity.getX() + entity.getWidth() - 64, 0, 64);
        statusWidget.setY(entity.getY() - statusWidget.getHeight() - 4);
        addRenderableOnly(statusWidget);

        addRenderableOnly(new LoadingErrorWidget(statusWidget.getX() - 18, statusWidget.getY(), 14));

        // -- bottom -- // 

        // version
        MutableComponent versionText = FiguraText.of().append(" " + FiguraMod.VERSION.noBuildString()).withStyle(ChatFormatting.ITALIC);
        int versionStatus = NetworkStuff.latestVersion != null ? NetworkStuff.latestVersion.compareTo(FiguraMod.VERSION) : 0;
        boolean oldVersion = versionStatus > 0;
        if (oldVersion) {
            versionText
                    .append(" ")
                    .append(Component.literal("=")
                            .withStyle(Style.EMPTY
                                    .withFont(new FontDescription.Resource(UIHelper.UI_FONT))
                                    .withItalic(false)
                                    .applyLegacyFormat(ChatFormatting.WHITE)
                            ))
                    .withStyle(Style.EMPTY
                            .applyFormat(ChatFormatting.AQUA)
                            .withHoverEvent(new HoverEvent.ShowText(
                                    FiguraText.of("gui.new_version.tooltip", Component.literal(NetworkStuff.latestVersion.toString()).withStyle(ChatFormatting.GREEN))
                            ))
                            .withClickEvent(new TextUtils.FiguraClickEvent(UIHelper.openURL(
                                FiguraMod.Links.Releases.url
                                ))
                            )
                    );
        } else if (versionStatus < 0) {
            versionText.withStyle(Style.EMPTY.withHoverEvent(new HoverEvent.ShowText(
                    FiguraText.of("gui.old_version.tooltip", Component.literal(NetworkStuff.latestVersion.toString()).withStyle(ChatFormatting.LIGHT_PURPLE))
            )));
        }

        Label version = new Label(versionText, middle, this.height - 4, TextUtils.Alignment.CENTER);
        addRenderableWidget(version);
        if (!oldVersion) version.setAlpha(0x33);
        version.setY(version.getRawY() - version.getHeight());

        int rightSide = Math.min(panels, 134);

        // back
        back = new Button(width - rightSide - 4, height - 24, rightSide, 20, FiguraText.of("gui.done"), null, bx -> onClose());
        addRenderableWidget(back);

        // -- right side -- //

        rightSide = panels / 2 + 52;

        // avatar settings
        Button avatarSettings;
        addRenderableWidget(avatarSettings = new Button(
                this.width - rightSide, 28, 24, 24,
                0, 0, 24,
                new FiguraIdentifier("textures/gui/avatar_settings.png"),
                72, 24,
                FiguraText.of("gui.avatar_settings.tooltip").append("\n").append(FiguraText.of("gui.not_available_yet").withStyle(ChatFormatting.RED)),
                bx -> {}
        ));
        avatarSettings.setActive(false);

        // sounds
        Button sounds = new Button(this.width - rightSide + 36, 28, 24, 24, 0, 0, 24, new FiguraIdentifier("textures/gui/sound.png"), 72, 24, FiguraText.of("gui.wardrobe.sound.tooltip"),
                button -> Minecraft.getInstance().setScreen(new SoundScreen(this))
        );
        addRenderableWidget(sounds);

        // keybinds
        Button keybinds = new Button(this.width - rightSide + 72, 28, 24, 24, 0, 0, 24, new FiguraIdentifier("textures/gui/keybind.png"), 72, 24, FiguraText.of("gui.wardrobe.keybind.tooltip"),
                button -> Minecraft.getInstance().setScreen(new KeybindScreen(this))
        );
        addRenderableWidget(keybinds);

        // avatar metadata
        addRenderableOnly(infoWidget = new AvatarInfoWidget(this.width - panels - 4, 56, panels, back.getY() - 60));

        // backend MOTD
        if (motdWidget != null) {
            removeWidget(motdWidget);
            motdWidget = null;
        }
        updateMotdWidget();

        // panic warning - always added last, on top
        addRenderableWidget(panic = new Label(
                FiguraText.of("gui.panic", Configs.PANIC_BUTTON.keyBind.getTranslatedKeyMessage()).withStyle(ChatFormatting.YELLOW),
                middle, version.getRawY(), TextUtils.Alignment.CENTER, 0)
        );
        panic.setY(panic.getRawY() - panic.getHeight());
        panic.setVisible(false);
    }

    private int getPanels() {
        return Math.min(width / 3, 256) - 8;
    }

    private void updateMotdWidget() {
        int panels = getPanels();

        int infoBottom = infoWidget.getY() + infoWidget.getHeight();

        int width = panels - 8;
        int height = back.getY() - infoBottom - 16;
        int x = this.width - panels;
        int y = infoBottom + 8;

        infoWidget.tick();
        if (motdWidget == null) {
            Component motd = NetworkStuff.motd == null ? DEBUG_MOTD_FALLBACK : NetworkStuff.motd;
            if (!FiguraMod.debugModeEnabled() && motd == DEBUG_MOTD_FALLBACK) {
                return;
            }
            motdWidget = addRenderableWidget(new BackendMotdWidget(x, y, width, height, motd, font));
        }  else {
            motdWidget.setPosition(x, y);
            motdWidget.setWidth(width);
            motdWidget.setHeight(height);
            Component motd = NetworkStuff.motd == null ? DEBUG_MOTD_FALLBACK : NetworkStuff.motd;
            if (!FiguraMod.debugModeEnabled() && motd == DEBUG_MOTD_FALLBACK) {
                return;
            }
            motdWidget.setMessage(motd);
        }

        motdWidget.visible = motdWidget.getHeight() > 48;
    }

    @Override
    public void tick() {
        // children tick
        super.tick();

        // panic visible
        panic.setVisible(AvatarManager.panic);

        // backend buttons
        Avatar avatar;
        upload.setActive(NetworkStuff.canUpload() && !AvatarManager.localUploaded && (avatar = AvatarManager.getAvatarForPlayer(FiguraMod.getLocalPlayerUUID())) != null && avatar.nbt != null && avatar.loaded);
        delete.setActive(NetworkStuff.isConnected() && AvatarManager.localUploaded && NetworkStuff.getUploadProgress() == null);

        updateMotdWidget();
    }

    @Override
    public void removed() {
        super.removed();
        LocalAvatarFetcher.save();
    }

    private void renderUploadProgress(GuiGraphicsExtractor gui, int x, int y, int width) {
        var progress = NetworkStuff.getUploadProgress();
        if (progress == null || width < 16) {
            return;
        }
        double fraction = progress.fraction();
        var text = FiguraText.of("loading." + progress.stage().name().toLowerCase(java.util.Locale.ROOT));
        if (fraction >= 0) {
            text.append(" " + (int) Math.floor(fraction * 100) + "%");
        }
        gui.fill(x, y, x + width, y + 22, 0xD0101010);
        gui.centeredText(font, TextUtils.trimToWidthEllipsis(font, text, width - 4, TextUtils.ELLIPSIS),
                x + width / 2, y + 2, 0xFFFFFFFF);
        gui.fill(x + 2, y + 15, x + width - 2, y + 20, 0xFF404040);
        int span = width - 4;
        int filled = fraction >= 0 ? (int) Math.floor(span * fraction) : Math.max(1, span / 4);
        int offset = fraction >= 0 || Configs.REDUCED_MOTION.value ? 0
                : Math.floorMod(FiguraMod.ticks * 2, span - filled + 1);
        gui.fill(x + 2 + offset, y + 15, x + 2 + offset + filled, y + 20, 0xFF55FFFF);
    }

    @Override
    public void onFilesDrop(List<Path> paths) {
        super.onFilesDrop(paths);

        StringBuilder packs = new StringBuilder();
        for (int i = 0; i < paths.size(); i++) {
            if (i > 0)
                packs.append("\n");
            packs.append(IOUtils.getFileNameOrEmpty(paths.get(i)));
        }

        this.minecraft.setScreen(new FiguraConfirmScreen(confirmed -> {
            if (confirmed) {
                try {
                    LocalAvatarFetcher.loadExternal(paths);
                    FiguraToast.sendToast(FiguraText.of("toast.wardrobe_copy.success", paths.size()));
                } catch (Exception e) {
                    FiguraToast.sendToast(FiguraText.of("toast.wardrobe_copy.error"), FiguraToast.ToastType.ERROR);
                    FiguraMod.LOGGER.error("Failed to copy files", e);
                }
            }
            this.minecraft.setScreen(this);
        }, FiguraText.of("gui.wardrobe.drop_files"), packs.toString(), this));
    }
}
