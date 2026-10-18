package top.aihmc.omnimorph.gui.screens.library;

import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import top.aihmc.omnimorph.backend2.library.LibraryAdminSession;
import top.aihmc.omnimorph.backend2.library.LibraryClient;
import top.aihmc.omnimorph.gui.screens.ModelLibraryScreen;
import top.aihmc.omnimorph.gui.widgets.Button;
import top.aihmc.omnimorph.gui.widgets.SwitchButton;
import top.aihmc.omnimorph.gui.widgets.TextField;
import top.aihmc.omnimorph.gui.widgets.library.LibraryFormBody;
import top.aihmc.omnimorph.gui.widgets.library.LibraryPasswordField;
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
        int formWidth = Math.max(100, Math.min(320, width - 16));
        var form = addRenderableWidget(new LibraryFormBody((width - formWidth) / 2, 28, formWidth,
                Math.max(40, height - 84)));
        name = form.row(FiguraText.of("gui.library.login_name"),
                new TextField(0, 0, formWidth - 22, 20, TextField.HintType.NAME, ignored -> {}));
        name.getField().setMaxLength(64);
        password = form.row(FiguraText.of("gui.library.password"), new LibraryPasswordField(formWidth - 22));
        remember = form.row(FiguraText.of("gui.library.remember"), new SwitchButton(0, 0, formWidth - 22, 20,
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
        login = addRenderableWidget(new Button((width - formWidth) / 2, height - 48, formWidth, 20,
                FiguraText.of("gui.library.login"), null, ignored -> login()));
        addRenderableWidget(new Button((width - formWidth) / 2, height - 24, formWidth, 20,
                FiguraText.of("gui.cancel"), null, ignored -> onClose()));
        addRenderableOnly((gui, x, y, delta) -> UIHelper.renderScrollingText(gui, message,
                (width - formWidth) / 2, height - 62, formWidth, 0xffd3e7e1));
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
