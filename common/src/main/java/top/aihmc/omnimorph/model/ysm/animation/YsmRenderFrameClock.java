package top.aihmc.omnimorph.model.ysm.animation;

/** Render-loop identity shared by world, first-person and GUI passes within one frame. */
public final class YsmRenderFrameClock {
    private static long frame;

    private YsmRenderFrameClock() {
    }

    public static void beginFrame() {
        frame++;
    }

    public static long currentFrame() {
        return frame;
    }
}
