package top.aihmc.omnimorph.model.outfit;

import org.figuramc.figura.permissions.Permissions;
import top.aihmc.omnimorph.model.rendering.CameraFov;
import top.aihmc.omnimorph.model.ysm.YsmMaterialNativeSmokeTest;

import java.nio.file.Path;

public final class CameraFovSmokeTest {
    public static void main(String[] args) throws Exception {
        YsmMaterialNativeSmokeTest.replayFiguraAcceleration(Path.of(args[0]), avatar -> {
            avatar.permissions.insert(Permissions.VANILLA_MODEL_EDIT, 1, "fov test");
            avatar.loadScript("camera_fov_test", "renderer:setFOV(0.5)").call();
            equal(CameraFov.apply(80, avatar, true), 40, "Lua multiplier reaches computed camera FOV");
            equal(CameraFov.apply(100, avatar, true), 50, "vanilla sprint/effect calculation remains the input");
            equal(CameraFov.apply(80, avatar, false), 80, "remote or preview avatars cannot change the local camera");
            avatar.loadScript("camera_fov_reset", "renderer:setFOV(nil)").call();
            equal(CameraFov.apply(96, avatar, true), 96, "nil restores the current vanilla calculation");
            equal(CameraFov.apply(72, null, true), 72, "unloading the avatar restores vanilla");
            avatar.luaRuntime.renderer.setFOV(2f);
            avatar.permissions.insert(Permissions.VANILLA_MODEL_EDIT, 0, "fov test");
            equal(CameraFov.apply(70, avatar, true), 70, "existing camera-edit permission still applies");
        });
        System.out.println("PASS camera FOV: actual Lua setter, multiplier, reset and owner/permission isolation; game pending");
    }

    private static void equal(float actual, float expected, String message) {
        if (actual != expected) {
            throw new AssertionError(message + ": expected " + expected + ", got " + actual);
        }
    }
}
