package top.aihmc.omnimorph.model.ysm;

import net.minecraft.client.Minecraft;
import top.aihmc.omnimorph.molang.runtime.ExecutionContext;
import top.aihmc.omnimorph.molang.runtime.Function;

import java.util.Arrays;

/** Client device state; access is restricted to the local model owner by the binding. */
public final class YsmInputState {
    private static final YsmInputState DEVICE = new YsmInputState();

    private final boolean[] keys = new boolean[349];
    private final boolean[] buttons = new boolean[8];
    private boolean ready;
    private final YsmHandActionState hands = new YsmHandActionState();

    public YsmHandActionState hands() {
        return hands;
    }

    public static YsmInputState device() {
        return DEVICE;
    }

    public void updateReadiness(Minecraft client) {
        setReady(client != null && client.player != null && client.level != null
                && client.getOverlay() == null && client.screen == null
                && client.mouseHandler.isMouseGrabbed() && client.isWindowActive());
        hands.prepare(client == null ? null : client.player, ready);
    }

    public void tick(Minecraft client) {
        updateReadiness(client);
        if (ready) {
            hands.tick(client.options.keyAttack.isDown());
        }
    }

    public void setReady(boolean ready) {
        this.ready = ready;
        if (!ready) {
            hands.prepare(null, false);
            Arrays.fill(keys, false);
            Arrays.fill(buttons, false);
        }
    }

    public void keyEvent(int code, int action) {
        if (ready && code >= 32 && code <= 348) {
            if (action == 1) {
                keys[code] = true;
            } else if (action == 0) {
                keys[code] = false;
            }
        }
    }

    public void mouseEvent(int button, int action) {
        if (ready && button >= 0 && button <= 7) {
            hands.mouseEvent(button, action);
            if (action == 1) {
                buttons[button] = true;
            } else if (action == 0) {
                buttons[button] = false;
            }
        }
    }

    public static boolean ownsInput(boolean host, Object modelEntity, Object localPlayer) {
        return host && modelEntity != null && modelEntity == localPlayer;
    }

    public boolean keyboard(boolean owner, ExecutionContext<?> context, Function.ArgumentCollection arguments) {
        if (!owner || !ready) {
            return false;
        }
        for (int index = 0; index < arguments.size(); index++) {
            int code = arguments.getAsInt(context, index);
            if (code >= 32 && code <= 348 && keys[code]) {
                return true;
            }
        }
        return false;
    }

    public boolean mouse(boolean owner, ExecutionContext<?> context, Function.ArgumentCollection arguments) {
        if (!owner || !ready || arguments.size() != 1) {
            return false;
        }
        int button = arguments.getAsInt(context, 0);
        return button >= 0 && button <= 7 && buttons[button];
    }
}
