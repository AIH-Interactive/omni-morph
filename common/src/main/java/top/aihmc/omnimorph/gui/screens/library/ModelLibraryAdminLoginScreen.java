package top.aihmc.omnimorph.gui.screens.library;

import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import top.aihmc.omnimorph.backend2.library.LibraryAdminSession;
import top.aihmc.omnimorph.backend2.library.LibraryClient;
import top.aihmc.omnimorph.gui.screens.ModelLibraryScreen;
import top.aihmc.omnimorph.gui.widgets.Button;
import top.aihmc.omnimorph.gui.widgets.SwitchButton;
import top.aihmc.omnimorph.gui.widgets.TextField;
import top.aihmc.omnimorph.gui.widgets.library.LibraryPasswordField;
import top.aihmc.omnimorph.gui.widgets.library.LibraryCheckBox;
import top.aihmc.omnimorph.utils.FiguraIdentifier;
import top.aihmc.omnimorph.utils.FiguraText;
import top.aihmc.omnimorph.utils.ui.UIHelper;

import java.util.UUID;

public final class ModelLibraryAdminLoginScreen extends LibraryFormScreen {
    private final LibraryClient client;
    private final LibraryAdminSession admin;
    private final Runnable authenticated;
    private LibraryClient.Request<?> pending;
    private LibraryClient.Request<?> permissionCheck;
    private LibraryPasswordField password;
    private TextField name;
    private SwitchButton remember;
    private Button login;
    private boolean closed;
    private long cooldownUntil;
    private Component message = Component.empty();

    public ModelLibraryAdminLoginScreen(Screen parent, LibraryClient client, LibraryAdminSession admin,
                                        Runnable authenticated) {
        super(parent, FiguraText.of("gui.library.admin_login"));
        this.client = client;
        this.admin = admin;
        this.authenticated = authenticated;
    }

    @Override
    public Class<? extends Screen> getSelectedPanel() {
        return ModelLibraryScreen.class;
    }

    @Override
    protected void init() {
        if (password != null) {
            password.clear();
        }
        if (!initForm()) {
            return;
        }
        int dialogWidth = Math.min(440, width - 16);
        int dialogHeight = 168;
        int x = (width - dialogWidth) / 2;
        int y = Math.max(30, (height - dialogHeight) / 2);
        int sidebar = Math.max(76, dialogWidth / 3);
        int inputX = x + sidebar + 42;
        int inputWidth = dialogWidth - sidebar - 50;
        addRenderableOnly((gui, mouseX, mouseY, delta) -> {
            UIHelper.blitSliced(gui, x, y, dialogWidth, dialogHeight, UIHelper.OUTLINE_FILL);
            gui.fill(x + sidebar, y + 1, x + sidebar + 1, y + dialogHeight - 1, 0xff3c4b57);
            gui.blit(RenderPipelines.GUI_TEXTURED, new FiguraIdentifier("textures/gui/panels.png"),
                    x + 12, y + 32, 60, 0, 20, 20, 140, 20);
            UIHelper.renderScrollingText(gui, FiguraText.of("gui.library.admin_login"), x + 8, y + 64,
                    sidebar - 16, 0xffeeeeee);
            UIHelper.renderScrollingText(gui, FiguraText.of("gui.library.login_name"), x + sidebar + 6,
                    y + 36, 32, 0xffbac6d8);
            UIHelper.renderScrollingText(gui, FiguraText.of("gui.library.password"), x + sidebar + 6,
                    y + 64, 32, 0xffbac6d8);
        });
        name = addRenderableWidget(new TextField(inputX, y + 30, inputWidth, 20, TextField.HintType.NAME, ignored -> {}));
        name.getField().setMaxLength(64);
        password = addRenderableWidget(new LibraryPasswordField(inputWidth));
        password.setX(inputX);
        password.setY(y + 58);
        remember = addRenderableWidget(new LibraryCheckBox(x + sidebar + 8, y + 86, dialogWidth - sidebar - 16,
                FiguraText.of("gui.library.remember"), FiguraText.of("gui.library.remember_unavailable"), ignored -> {}));
        remember.setActive(false);
        permissionCheck = admin.rememberAvailable();
        permissionCheck.future().whenComplete((value, failure) -> {
            if (!closed) {
                remember.setActive(failure == null && Boolean.TRUE.equals(value));
                if (failure != null || !Boolean.TRUE.equals(value)) {
                    remember.setToggled(false);
                    message = FiguraText.of("gui.library.remember_unavailable");
                }
            }
        });
        int actionWidth = Math.max(48, (dialogWidth - sidebar - 24) / 2);
        login = addRenderableWidget(new Button(x + dialogWidth - actionWidth - 8, y + dialogHeight - 28, actionWidth, 20,
                FiguraText.of("gui.library.login"), null, ignored -> login()));
        addRenderableWidget(new Button(x + sidebar + 8, y + dialogHeight - 28, actionWidth, 20,
                FiguraText.of("gui.cancel"), null, ignored -> onClose()));
        addRenderableWidget(new Button(x + dialogWidth - 20, y + 2, 18, 18,
                Component.literal("×"), FiguraText.of("gui.cancel"), ignored -> onClose()));
        addRenderableOnly((gui, mouseX, mouseY, delta) -> UIHelper.renderScrollingText(gui, message,
                inputX - 34, password.getY() + 56, dialogWidth - sidebar - 16, 0xffd3e7e1));
    }

    private void login() {
        if (busy() || System.nanoTime() < cooldownUntil || password.getField().getValue().isEmpty()) {
            return;
        }
        var request = admin.login(name.getField().getValue().trim(), password.getField().getValue(),
                remember.isToggled(), UUID.randomUUID());
        password.clear();
        pending = request;
        request.future().whenComplete((value, failure) -> {
            if (closed) {
                return;
            }
            if (failure == null && Boolean.TRUE.equals(value)) {
                minecraft.setScreen(parentScreen);
                authenticated.run();
            } else {
                message = FiguraText.of("gui.library.login_failed");
                cooldownUntil = System.nanoTime() + 3_000_000_000L;
            }
        });
    }

    private boolean busy() {
        return pending != null && !pending.future().isDone();
    }

    @Override
    public void tick() {
        super.tick();
        if (!formReady) {
            return;
        }
        if (!client.current()) {
            onClose();
            return;
        }
        login.setActive(!closed && !busy() && System.nanoTime() >= cooldownUntil);
        password.setEnabled(!busy());
        name.setEnabled(!busy());
    }

    @Override
    public void removed() {
        closed = true;
        if (password != null) {
            password.clear();
        }
        if (pending != null) {
            pending.cancel();
        }
        if (permissionCheck != null) {
            permissionCheck.cancel();
        }
        super.removed();
    }
}
