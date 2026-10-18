package top.aihmc.omnimorph.model.library;

/** Small standing breathing motion for previews whose rigs have no vanilla limb bindings. */
public final class PreviewIdlePose {
    private PreviewIdlePose() {
    }

    public static float breathingScale(float age) {
        float time = Float.isFinite(age) ? age : 0f;
        return 1f + (float) Math.sin(time * 0.09f) * 0.006f;
    }
}
