package top.aihmc.omnimorph.model.rendering;

import org.figuramc.figura.avatar.Avatar;
import org.figuramc.figura.utils.RenderUtils;

/** Applies the current local avatar's multiplier to the camera's computed world FOV. */
public final class CameraFov {
    private CameraFov() {
    }

    public static float apply(float vanillaFov, Avatar avatar, boolean localPlayer) {
        if (!localPlayer || !RenderUtils.vanillaModelAndScript(avatar)
                || !avatar.loaded || avatar.scriptError || !avatar.renderOwner().isCurrent()) {
            return vanillaFov;
        }
        Float multiplier = avatar.luaRuntime.renderer.fov;
        return multiplier == null ? vanillaFov : vanillaFov * multiplier;
    }
}
