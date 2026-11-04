package top.aihmc.omnimorph.gui;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.MouseButtonInfo;
import org.figuramc.figura.gui.widgets.EntityPreview;
import org.figuramc.figura.gui.widgets.PanelSelectorWidget;
import sun.misc.Unsafe;

/** Exercises native scancodes against the actual panel shortcut routing without a client. */
public final class NativeGuiInputSmokeTest {
    public static void main(String[] args) throws Exception {
        var unsafeField = Unsafe.class.getDeclaredField("theUnsafe");
        unsafeField.setAccessible(true);
        var unsafe = (Unsafe) unsafeField.get(null);
        var selector = (PanelSelectorWidget) unsafe.allocateInstance(PanelSelectorWidget.class);
        var nextPanel = PanelSelectorWidget.class.getDeclaredMethod("getNextPanel", int.class);
        nextPanel.setAccessible(true);
        for (int index = 0; index < 9; index++) {
            int actual = (int) nextPanel.invoke(selector, InputConstants.KEY_1 + index);
            if (actual != index) {
                throw new AssertionError("Native number shortcut " + (index + 1) + " selected " + actual);
            }
        }
        if ((int) nextPanel.invoke(selector, InputConstants.KEY_0) != -1) {
            throw new AssertionError("Zero must not select a panel");
        }
        // The fixture supplies hit testing; mouse routing and state transitions remain production code.
        var preview = new EntityPreview(0, 0, 100, 100, 1, 0, 0, null, null) {
            @Override
            public boolean isMouseOver(double x, double y) {
                return x >= 0 && x < 100 && y >= 0 && y < 100;
            }
        };
        checkMouse(preview, InputConstants.MOUSE_BUTTON_LEFT, "isRotating");
        checkMouse(preview, InputConstants.MOUSE_BUTTON_RIGHT, "isDragging");
        if (!preview.mouseClicked(mouse(InputConstants.MOUSE_BUTTON_MIDDLE), false)) {
            throw new AssertionError("Native middle click must reset the preview");
        }
        System.out.println("PASS native GUI panel shortcuts and preview mouse routing");
    }

    private static MouseButtonEvent mouse(int button) {
        return new MouseButtonEvent(50, 50, new MouseButtonInfo(button, 0));
    }

    private static void checkMouse(EntityPreview preview, int button, String state) throws Exception {
        var field = EntityPreview.class.getDeclaredField(state);
        field.setAccessible(true);
        if (!preview.mouseClicked(mouse(button), false) || !field.getBoolean(preview)) {
            throw new AssertionError("Native button " + button + " must start " + state);
        }
        if (!preview.mouseReleased(mouse(button)) || field.getBoolean(preview)) {
            throw new AssertionError("Native button " + button + " must stop " + state);
        }
    }
}
